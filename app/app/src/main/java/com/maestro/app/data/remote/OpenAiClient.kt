package com.maestro.app.data.remote

import com.maestro.app.domain.model.ChatMessage
import java.util.Base64
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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OpenAiClient(
    private val httpClient: OkHttpClient,
    private val baseUrl: String = BASE_URL
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun stream(
        apiKey: String,
        messages: List<ChatMessage>,
        systemPrompt: String?,
        images: List<ByteArray>,
        model: String = DEFAULT_MODEL
    ): Flow<String> = callbackFlow {
        val body = buildRequestBody(
            messages,
            systemPrompt,
            images,
            model
        )
        val request = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .post(
                body.toString().toRequestBody(
                    JSON_MEDIA_TYPE
                )
            )
            .addHeader(
                "Authorization",
                "Bearer $apiKey"
            )
            .addHeader(
                "Content-Type",
                "application/json"
            )
            .build()
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
                var response = activeCall.execute()
                activeResponse = response
                var retries = 0
                while (response.code == 503 &&
                    retries < MAX_RETRIES
                ) {
                    response.close()
                    retries++
                    trySend(LlmClient.RETRY_TOKEN)
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
                    val err = readError(response)
                    channel.close(Exception(err))
                    return@withContext
                }

                val responseBody = response.body
                if (responseBody == null) {
                    channel.close(
                        Exception("HTTP $code: 빈 응답")
                    )
                    return@withContext
                }

                var tokenCount = 0
                var sentGenerating = false
                responseBody.source().use { source ->
                    while (!source.exhausted()) {
                        val line =
                            source.readUtf8Line()
                                ?: break
                        if (!line.startsWith("data: ")) {
                            continue
                        }
                        if (!sentGenerating) {
                            sentGenerating = true
                            receivedFirstToken = true
                            watchdog.cancel()
                            trySend(
                                LlmClient
                                    .GENERATING_TOKEN
                            )
                        }
                        val data = line
                            .removePrefix("data: ")
                            .trim()
                        if (data == "[DONE]") break
                        if (data.isEmpty()) continue
                        val text = extractDelta(data)
                        if (text != null) {
                            trySend(text)
                            tokenCount++
                        }
                    }
                }
                if (tokenCount == 0) {
                    channel.close(
                        Exception(
                            "HTTP $code: 응답 " +
                                "텍스트 없음"
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

    suspend fun fetchModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/v1/models")
            .get()
            .addHeader(
                "Authorization",
                "Bearer $apiKey"
            )
            .build()
        val response =
            httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            return@withContext emptyList()
        }
        val body = response.body?.string()
            ?: return@withContext emptyList()
        try {
            val root = json.parseToJsonElement(body)
                .jsonObject
            val data = root["data"]?.jsonArray
                ?: return@withContext emptyList()
            data.mapNotNull { model ->
                model.jsonObject["id"]
                    ?.jsonPrimitive?.content
            }.filter { id ->
                id.startsWith("gpt-") ||
                    id.startsWith("chatgpt-") ||
                    id.matches(
                        Regex("^o[0-9].*")
                    )
            }.sorted()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    suspend fun validateKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/v1/models")
            .get()
            .addHeader(
                "Authorization",
                "Bearer $apiKey"
            )
            .build()
        httpClient.newCall(request)
            .execute().isSuccessful
    }

    private fun buildRequestBody(
        messages: List<ChatMessage>,
        systemPrompt: String?,
        images: List<ByteArray>,
        model: String
    ): JsonObject = buildJsonObject {
        put("model", model)
        put("stream", true)
        put(
            "messages",
            buildJsonArray {
                if (!systemPrompt.isNullOrBlank()) {
                    add(
                        buildJsonObject {
                            put("role", "system")
                            put(
                                "content",
                                systemPrompt
                            )
                        }
                    )
                }
                messages.forEachIndexed { idx, msg ->
                    val role =
                        if (msg.role ==
                            ChatMessage.Role.USER
                        ) {
                            "user"
                        } else {
                            "assistant"
                        }
                    val isLastUser =
                        msg.role ==
                            ChatMessage.Role.USER &&
                            idx == messages.indexOfLast {
                                it.role ==
                                    ChatMessage.Role.USER
                            }
                    if (isLastUser &&
                        images.isNotEmpty()
                    ) {
                        add(
                            buildJsonObject {
                                put("role", role)
                                put(
                                    "content",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put(
                                                    "type",
                                                    "text"
                                                )
                                                put(
                                                    "text",
                                                    msg.content
                                                )
                                            }
                                        )
                                        images.forEach {
                                                img ->
                                            val b64 =
                                                Base64
                                                    .getEncoder()
                                                    .encodeToString(
                                                        img
                                                    )
                                            add(
                                                buildJsonObject {
                                                    put(
                                                        "type",
                                                        "image_url"
                                                    )
                                                    put(
                                                        "image_url",
                                                        buildJsonObject {
                                                            put(
                                                                "url",
                                                                "data:image/png;base64,$b64"
                                                            )
                                                        }
                                                    )
                                                }
                                            )
                                        }
                                    }
                                )
                            }
                        )
                    } else {
                        add(
                            buildJsonObject {
                                put("role", role)
                                put(
                                    "content",
                                    msg.content
                                )
                            }
                        )
                    }
                }
            }
        )
    }

    private fun extractDelta(data: String): String? {
        return try {
            val obj = json.parseToJsonElement(data)
                .jsonObject
            obj["choices"]?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("delta")?.jsonObject
                ?.get("content")?.jsonPrimitive
                ?.content
        } catch (_: Throwable) {
            null
        }
    }

    private fun readError(response: okhttp3.Response): String {
        val body = try {
            response.body?.string() ?: ""
        } catch (_: Throwable) {
            ""
        }
        val detail = try {
            json.parseToJsonElement(body)
                .jsonObject["error"]
                ?.jsonObject?.get("message")
                ?.jsonPrimitive?.content
        } catch (_: Throwable) {
            null
        }
        return "API error ${response.code}: " +
            "${detail ?: body.take(200)}"
    }

    companion object {
        const val BASE_URL =
            "https://api.openai.com"
        const val DEFAULT_MODEL = "gpt-4o-mini"
        private val JSON_MEDIA_TYPE =
            "application/json; charset=utf-8"
                .toMediaType()
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 2000L
        private const val FIRST_TOKEN_TIMEOUT_MS =
            30_000L
    }
}
