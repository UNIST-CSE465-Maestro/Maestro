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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ClaudeClient(
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
            .url("$baseUrl/v1/messages")
            .post(
                body.toString().toRequestBody(
                    JSON_MEDIA_TYPE
                )
            )
            .addHeader("x-api-key", apiKey)
            .addHeader(
                "anthropic-version",
                API_VERSION
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
                while (response.code == 529 &&
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
                        if (line.startsWith(
                                "event: message_start"
                            ) && !sentGenerating
                        ) {
                            sentGenerating = true
                            receivedFirstToken = true
                            watchdog.cancel()
                            trySend(GENERATING_TOKEN)
                            continue
                        }
                        if (!line.startsWith(
                                "data: "
                            )
                        ) {
                            if (!receivedFirstToken &&
                                line.isNotBlank()
                            ) {
                                receivedFirstToken =
                                    true
                                watchdog.cancel()
                            }
                            continue
                        }
                        if (!receivedFirstToken) {
                            receivedFirstToken = true
                            watchdog.cancel()
                        }
                        val data = line
                            .removePrefix("data: ")
                            .trim()
                        if (data.isEmpty()) continue
                        val text =
                            extractDelta(data)
                        if (text != null) {
                            if (!receivedFirstToken) {
                                receivedFirstToken =
                                    true
                                watchdog.cancel()
                            }
                            trySend(text)
                            tokenCount++
                        }
                        if (isStop(data)) break
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

    fun fetchModels(): List<String> = MODELS

    suspend fun warmUp(apiKey: String) = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", DEFAULT_MODEL)
            put("max_tokens", 1)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", "hi")
                        }
                    )
                }
            )
        }
        val request = Request.Builder()
            .url("$baseUrl/v1/messages")
            .post(
                body.toString().toRequestBody(
                    JSON_MEDIA_TYPE
                )
            )
            .addHeader("x-api-key", apiKey)
            .addHeader(
                "anthropic-version",
                API_VERSION
            )
            .addHeader(
                "Content-Type",
                "application/json"
            )
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception(
                    "Claude 연결 확인 실패: HTTP ${response.code}"
                )
            }
        }
    }

    suspend fun validateKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", DEFAULT_MODEL)
            put("max_tokens", 1)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", "hi")
                        }
                    )
                }
            )
        }
        val request = Request.Builder()
            .url("$baseUrl/v1/messages")
            .post(
                body.toString().toRequestBody(
                    JSON_MEDIA_TYPE
                )
            )
            .addHeader("x-api-key", apiKey)
            .addHeader(
                "anthropic-version",
                API_VERSION
            )
            .addHeader(
                "Content-Type",
                "application/json"
            )
            .build()
        val response =
            httpClient.newCall(request).execute()
        response.isSuccessful
    }

    private fun buildRequestBody(
        messages: List<ChatMessage>,
        systemPrompt: String?,
        images: List<ByteArray>,
        model: String
    ) = buildJsonObject {
        put("model", model)
        put("max_tokens", MAX_TOKENS)
        put("stream", true)
        if (!systemPrompt.isNullOrBlank()) {
            put("system", systemPrompt)
        }
        put(
            "messages",
            buildJsonArray {
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
                            idx == messages
                                .indexOfLast {
                                    it.role ==
                                        ChatMessage
                                            .Role.USER
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
                                                        "image"
                                                    )
                                                    put(
                                                        "source",
                                                        buildJsonObject {
                                                            put(
                                                                "type",
                                                                "base64"
                                                            )
                                                            put(
                                                                "media_type",
                                                                "image/png"
                                                            )
                                                            put(
                                                                "data",
                                                                b64
                                                            )
                                                        }
                                                    )
                                                }
                                            )
                                        }
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
            if (obj["type"]?.jsonPrimitive
                    ?.content !=
                "content_block_delta"
            ) {
                return null
            }
            obj["delta"]?.jsonObject
                ?.get("text")?.jsonPrimitive
                ?.content
        } catch (_: Throwable) {
            null
        }
    }

    private fun isStop(data: String): Boolean {
        return try {
            val obj = json.parseToJsonElement(data)
                .jsonObject
            obj["type"]?.jsonPrimitive
                ?.content == "message_stop"
        } catch (_: Throwable) {
            false
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
            "https://api.anthropic.com"
        const val DEFAULT_MODEL =
            "claude-sonnet-4-6"
        const val API_VERSION = "2023-06-01"
        private const val MAX_TOKENS = 4096
        private val JSON_MEDIA_TYPE =
            "application/json; charset=utf-8"
                .toMediaType()
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 2000L
        const val GENERATING_TOKEN =
            "\u0000GENERATING"
        private const val FIRST_TOKEN_TIMEOUT_MS =
            120_000L
        private val MODELS = listOf(
            "claude-haiku-4-5-20251001",
            "claude-sonnet-4-6",
            "claude-opus-4-6"
        )
    }
}
