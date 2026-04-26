package com.maestro.app.data.remote

import com.maestro.app.data.model.LlmRequestBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Generic LLM client using Gemini REST API.
 * Supports streaming (SSE) and single-shot requests.
 */
class LlmClient(
    private val httpClient: OkHttpClient,
    private val baseUrl: String = LlmRequestBuilder.BASE_URL
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun stream(
        apiKey: String,
        requestBody: JsonObject,
        model: String = LlmRequestBuilder.DEFAULT_MODEL
    ): Flow<String> = callbackFlow {
        val url = "$baseUrl/v1beta/models/$model" +
            ":streamGenerateContent" +
            "?alt=sse&key=$apiKey"
        val request = buildRequest(url, requestBody)
        var activeCall = httpClient.newCall(request)

        var receivedFirstToken = false
        var activeResponse:
            okhttp3.Response? = null

        val watchdog =
            CoroutineScope(Dispatchers.Default)
                .launch {
                    delay(FIRST_TOKEN_TIMEOUT_MS)
                    if (!receivedFirstToken) {
                        try {
                            activeResponse?.close()
                        } catch (_: Throwable) {}
                        activeCall.cancel()
                    }
                }

        withContext(Dispatchers.IO) {
            try {
                var response =
                    activeCall.execute()
                activeResponse = response
                var retries = 0
                while (response.code == 503 &&
                    retries < MAX_RETRIES
                ) {
                    response.close()
                    retries++
                    trySend(RETRY_TOKEN)
                    kotlinx.coroutines.delay(
                        RETRY_DELAY_MS * retries
                    )
                    activeCall =
                        httpClient.newCall(request)
                    response = activeCall.execute()
                    activeResponse = response
                }
                val code = response.code

                if (!response.isSuccessful) {
                    val err = readErrorDetail(response)
                    channel.close(Exception(err))
                    return@withContext
                }

                val body = response.body
                if (body == null) {
                    channel.close(
                        Exception(
                            "HTTP $code: 빈 응답"
                        )
                    )
                    return@withContext
                }

                var tokenCount = 0
                var isThinking = false
                var sentGenerating = false
                val debugLines = mutableListOf<String>()
                body.source().use { source ->
                    while (!source.exhausted()) {
                        val line =
                            source.readUtf8Line()
                                ?: break
                        if (tokenCount == 0 &&
                            debugLines.size < 3 &&
                            line.isNotBlank()
                        ) {
                            debugLines.add(
                                line.take(200)
                            )
                        }
                        val data =
                            parseSseLine(line)
                                ?: continue
                        if (!sentGenerating) {
                            sentGenerating = true
                            receivedFirstToken = true
                            watchdog.cancel()
                            trySend(GENERATING_TOKEN)
                        }
                        val result =
                            parseChunk(data)
                        if (result.thinking &&
                            !isThinking
                        ) {
                            isThinking = true
                            trySend(THINKING_TOKEN)
                        }
                        if (result.text != null) {
                            if (!receivedFirstToken) {
                                receivedFirstToken =
                                    true
                                watchdog.cancel()
                            }
                            if (isThinking) {
                                isThinking = false
                                trySend(
                                    THINKING_DONE_TOKEN
                                )
                            }
                            trySend(result.text)
                            tokenCount++
                        }
                        if (result.done) break
                    }
                }
                if (tokenCount == 0) {
                    val preview = debugLines
                        .joinToString("\n")
                    channel.close(
                        Exception(
                            "HTTP $code 응답 " +
                                "텍스트 없음:\n$preview"
                        )
                    )
                } else {
                    channel.close()
                }
            } catch (e: Throwable) {
                channel.close(e)
            }
        }

        awaitClose {
            watchdog.cancel()
            activeCall.cancel()
        }
    }

    suspend fun complete(
        apiKey: String,
        requestBody: JsonObject,
        model: String = LlmRequestBuilder.DEFAULT_MODEL
    ): String = withContext(Dispatchers.IO) {
        val url = "$baseUrl/v1beta/models/$model" +
            ":generateContent?key=$apiKey"
        val request = buildRequest(url, requestBody)
        val response =
            httpClient.newCall(request).execute()
        val body = response.body?.string()
            ?: throw Exception("Empty response")

        if (!response.isSuccessful) {
            throw Exception(
                parseErrorMessage(body, response.code)
            )
        }

        extractFullText(body)
    }

    suspend fun validateKey(apiKey: String): Boolean {
        return withContext(Dispatchers.IO) {
            val url =
                "$baseUrl/v1beta/models?key=$apiKey"
            val request = Request.Builder()
                .url(url).get().build()
            httpClient.newCall(request)
                .execute().isSuccessful
        }
    }

    suspend fun fetchModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val url =
            "$baseUrl/v1beta/models?key=$apiKey"
        val request = Request.Builder()
            .url(url).get().build()
        val response =
            httpClient.newCall(request).execute()
        if (!response.isSuccessful) return@withContext emptyList()
        val body = response.body?.string()
            ?: return@withContext emptyList()
        try {
            val root =
                json.parseToJsonElement(body)
                    .jsonObject
            val models =
                root["models"]?.jsonArray
                    ?: return@withContext emptyList()
            val candidates = models.mapNotNull { model ->
                val name = model.jsonObject["name"]
                    ?.jsonPrimitive?.content
                    ?: return@mapNotNull null
                val id = name.removePrefix("models/")
                if (!id.startsWith("gemini-")) {
                    return@mapNotNull null
                }
                val methods = model.jsonObject[
                    "supportedGenerationMethods"
                ]?.jsonArray?.map {
                    it.jsonPrimitive.content
                } ?: emptyList()
                if ("generateContent" in methods) {
                    id
                } else {
                    null
                }
            }
            candidates.filter { modelId ->
                probeModel(apiKey, modelId)
            }.sorted()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    suspend fun warmUp(apiKey: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/v1beta/models?key=$apiKey")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception(
                    "Gemini 연결 확인 실패: HTTP ${response.code}"
                )
            }
        }
    }

    private fun probeModel(apiKey: String, modelId: String): Boolean {
        return try {
            val probeBody =
                "{\"contents\":[{\"parts\":" +
                    "[{\"text\":\"hi\"}]}]," +
                    "\"generationConfig\":" +
                    "{\"maxOutputTokens\":1}}"
            val req = Request.Builder()
                .url(
                    "$baseUrl/v1beta/models/" +
                        "$modelId:generateContent" +
                        "?key=$apiKey"
                )
                .post(
                    probeBody.toRequestBody(
                        JSON_MEDIA_TYPE
                    )
                )
                .build()
            val resp =
                httpClient.newCall(req).execute()
            val ok = resp.isSuccessful
            resp.close()
            ok
        } catch (_: Throwable) {
            false
        }
    }

    private fun buildRequest(url: String, body: JsonObject): Request = Request.Builder()
        .url(url)
        .post(
            body.toString()
                .toRequestBody(JSON_MEDIA_TYPE)
        )
        .addHeader("Content-Type", "application/json")
        .build()

    private fun parseSseLine(line: String): String? {
        if (!line.startsWith("data: ")) return null
        val data = line.removePrefix("data: ").trim()
        if (data == "[DONE]" || data.isEmpty()) {
            return null
        }
        return data
    }

    private data class ChunkResult(
        val text: String?,
        val thinking: Boolean,
        val done: Boolean
    )

    /**
     * Parse Gemini SSE chunk.
     * Extract text, detect thinking, detect finish.
     */
    private fun parseChunk(data: String): ChunkResult {
        return try {
            val obj =
                json.parseToJsonElement(data)
                    .jsonObject
            val candidate = obj["candidates"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
            val parts = candidate
                ?.get("content")
                ?.jsonObject
                ?.get("parts")
                ?.jsonArray
            var isThinking = false
            val text = parts?.mapNotNull { part ->
                val partObj = part.jsonObject
                if (partObj["thought"]
                        ?.jsonPrimitive
                        ?.booleanOrNull == true
                ) {
                    isThinking = true
                    null
                } else {
                    partObj["text"]
                        ?.jsonPrimitive?.content
                }
            }?.joinToString("")?.ifEmpty { null }
            val done = candidate
                ?.get("finishReason") != null
            ChunkResult(text, isThinking, done)
        } catch (_: Throwable) {
            ChunkResult(null, false, false)
        }
    }

    private fun extractFullText(body: String): String {
        val result =
            json.parseToJsonElement(body).jsonObject
        val candidates =
            result["candidates"]?.jsonArray
                ?: return ""
        return candidates.mapNotNull { candidate ->
            candidate.jsonObject["content"]
                ?.jsonObject
                ?.get("parts")
                ?.jsonArray
                ?.mapNotNull {
                    it.jsonObject["text"]
                        ?.jsonPrimitive?.content
                }
                ?.joinToString("")
        }.joinToString("")
    }

    private fun readErrorDetail(response: okhttp3.Response): String {
        val body = try {
            response.body?.string() ?: ""
        } catch (_: Throwable) {
            ""
        }
        return parseErrorMessage(body, response.code)
    }

    private fun parseErrorMessage(body: String, code: Int): String {
        val detail = try {
            json.parseToJsonElement(body)
                .jsonObject["error"]
                ?.jsonObject?.get("message")
                ?.jsonPrimitive?.content
        } catch (_: Throwable) {
            null
        }
        return "API error $code: " +
            "${detail ?: body.take(200)}"
    }

    companion object {
        private val JSON_MEDIA_TYPE =
            "application/json; charset=utf-8"
                .toMediaType()
        const val THINKING_TOKEN = "\u0000THINKING"
        const val THINKING_DONE_TOKEN =
            "\u0000THINKING_DONE"
        const val GENERATING_TOKEN =
            "\u0000GENERATING"
        const val RETRY_TOKEN = "\u0000RETRY"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 2000L
        private const val FIRST_TOKEN_TIMEOUT_MS =
            30_000L
    }
}
