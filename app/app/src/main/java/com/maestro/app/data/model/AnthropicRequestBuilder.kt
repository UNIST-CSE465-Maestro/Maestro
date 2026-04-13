package com.maestro.app.data.model

import com.maestro.app.domain.model.ChatMessage
import java.util.Base64
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Builds Anthropic /v1/messages request body.
 * Handles both text-only and multimodal (image) messages.
 */
object AnthropicRequestBuilder {

    fun build(
        messages: List<ChatMessage>,
        systemPrompt: String? = null,
        images: List<ByteArray> = emptyList(),
        model: String = DEFAULT_MODEL,
        maxTokens: Int = DEFAULT_MAX_TOKENS,
        stream: Boolean = true
    ): JsonObject = buildJsonObject {
        put("model", model)
        put("max_tokens", maxTokens)
        put("stream", stream)
        if (!systemPrompt.isNullOrBlank()) {
            put("system", systemPrompt)
        }
        put("messages", buildMessagesArray(messages, images))
    }

    fun buildValidation(apiKey: String): JsonObject = buildJsonObject {
        put("model", VALIDATION_MODEL)
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

    private fun buildMessagesArray(
        messages: List<ChatMessage>,
        images: List<ByteArray>
    ): JsonArray = buildJsonArray {
        messages.forEachIndexed { index, msg ->
            add(
                buildJsonObject {
                    put("role", msg.role.name.lowercase())
                    // Attach images to the last user message
                    val isLastUser = msg.role == ChatMessage.Role.USER &&
                        index == messages.indexOfLast {
                            it.role == ChatMessage.Role.USER
                        }
                    if (isLastUser && images.isNotEmpty()) {
                        put(
                            "content",
                            buildMultipartContent(
                                msg.content,
                                images
                            )
                        )
                    } else {
                        put("content", msg.content)
                    }
                }
            )
        }
    }

    private fun buildMultipartContent(text: String, images: List<ByteArray>): JsonArray =
        buildJsonArray {
            images.forEach { imageBytes ->
                add(
                    buildJsonObject {
                        put("type", "image")
                        put(
                            "source",
                            buildJsonObject {
                                put("type", "base64")
                                put("media_type", "image/png")
                                put(
                                    "data",
                                    Base64.getEncoder()
                                        .encodeToString(imageBytes)
                                )
                            }
                        )
                    }
                )
            }
            add(
                buildJsonObject {
                    put("type", "text")
                    put("text", text)
                }
            )
        }

    const val DEFAULT_MODEL = "claude-sonnet-4-20250514"
    const val VALIDATION_MODEL = "claude-haiku-4-5-20251001"
    const val DEFAULT_MAX_TOKENS = 4096
    const val API_VERSION = "2023-06-01"
    const val BASE_URL = "https://api.anthropic.com"
}
