package com.maestro.app.data.remote

import com.maestro.app.data.model.AnthropicRequestBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OkHttp-based client for Anthropic /v1/messages.
 * Supports both streaming (SSE) and single-shot requests.
 */
class AnthropicSseClient(
    private val httpClient: OkHttpClient,
    private val baseUrl: String = AnthropicRequestBuilder.BASE_URL
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Stream text deltas. Each emission is a text chunk.
     */
    fun stream(apiKey: String, requestBody: JsonObject): Flow<String> = callbackFlow {
        val request = buildRequest(apiKey, requestBody)
        val call = httpClient.newCall(request)

        val response = withContext(Dispatchers.IO) {
            call.execute()
        }

        if (!response.isSuccessful) {
            val err = readErrorDetail(response)
            close(Exception(err))
            return@callbackFlow
        }

        val body = response.body ?: run {
            close(Exception("Empty response body"))
            return@callbackFlow
        }

        withContext(Dispatchers.IO) {
            try {
                body.source().use { source ->
                    val buffer = okio.Buffer()
                    while (!source.exhausted()) {
                        source.read(buffer, 8192)
                        val chunk = buffer.readUtf8()
                        for (line in chunk.lines()) {
                            val data = parseSseLine(line)
                                ?: continue
                            val text = extractTextDelta(data)
                            if (text != null) trySend(text)
                        }
                    }
                }
                channel.close()
            } catch (e: Throwable) {
                channel.close(e)
            }
        }

        awaitClose { call.cancel() }
    }

    /**
     * Single (non-streaming) completion.
     * Returns the full response text.
     */
    suspend fun complete(apiKey: String, requestBody: JsonObject): String =
        withContext(Dispatchers.IO) {
            val request = buildRequest(apiKey, requestBody)
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()
                ?: throw Exception("Empty response")

            if (!response.isSuccessful) {
                throw Exception(parseErrorMessage(body, response.code))
            }

            extractFullText(body)
        }

    /**
     * Validate API key with a minimal request.
     */
    suspend fun validateKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val reqBody = AnthropicRequestBuilder
                .buildValidation(apiKey)
            val request = buildRequest(apiKey, reqBody)
            val response = httpClient.newCall(request).execute()
            response.code == 200
        } catch (_: Throwable) {
            false
        }
    }

    private fun buildRequest(apiKey: String, body: JsonObject): Request = Request.Builder()
        .url("$baseUrl/v1/messages")
        .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
        .addHeader("Content-Type", "application/json")
        .addHeader("x-api-key", apiKey)
        .addHeader(
            "anthropic-version",
            AnthropicRequestBuilder.API_VERSION
        )
        .build()

    private fun parseSseLine(line: String): String? {
        if (!line.startsWith("data: ")) return null
        val data = line.removePrefix("data: ").trim()
        if (data == "[DONE]" || data.isEmpty()) return null
        return data
    }

    private fun extractTextDelta(data: String): String? {
        return try {
            val event = json.parseToJsonElement(data).jsonObject
            if (event["type"]?.jsonPrimitive?.content !=
                "content_block_delta"
            ) {
                return null
            }
            val delta = event["delta"]?.jsonObject ?: return null
            if (delta["type"]?.jsonPrimitive?.content !=
                "text_delta"
            ) {
                return null
            }
            delta["text"]?.jsonPrimitive?.content
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Extract full text from non-streaming response.
     * Response format: { "content": [{"type":"text","text":"..."}] }
     */
    private fun extractFullText(body: String): String {
        val result = json.parseToJsonElement(body).jsonObject
        val content = result["content"] ?: return ""
        val blocks: JsonArray = when (content) {
            is JsonArray -> content
            else -> json.parseToJsonElement(
                content.toString()
            ).jsonArray
        }
        return blocks
            .filter {
                it.jsonObject["type"]
                    ?.jsonPrimitive?.content == "text"
            }
            .joinToString("") {
                it.jsonObject["text"]
                    ?.jsonPrimitive?.content ?: ""
            }
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
        return "API error $code: ${detail ?: body.take(200)}"
    }

    companion object {
        private val JSON_MEDIA_TYPE =
            "application/json; charset=utf-8".toMediaType()
    }
}
