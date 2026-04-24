package com.maestro.app.data.model

import com.maestro.app.domain.model.ChatMessage
import java.util.Base64
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Builds Gemini-compatible /v1beta/models request bodies.
 */
object LlmRequestBuilder {

    fun build(
        messages: List<ChatMessage>,
        systemPrompt: String? = null,
        images: List<ByteArray> = emptyList(),
        maxTokens: Int = DEFAULT_MAX_TOKENS
    ): JsonObject = buildJsonObject {
        put("contents", buildContentsArray(messages, images))
        if (!systemPrompt.isNullOrBlank()) {
            put(
                "systemInstruction",
                buildJsonObject {
                    put(
                        "parts",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("text", systemPrompt)
                                }
                            )
                        }
                    )
                }
            )
        }
        put(
            "generationConfig",
            buildJsonObject {
                put("maxOutputTokens", maxTokens)
            }
        )
    }

    private fun buildContentsArray(messages: List<ChatMessage>, images: List<ByteArray>) =
        buildJsonArray {
            messages.forEachIndexed { index, msg ->
                add(
                    buildJsonObject {
                        put(
                            "role",
                            if (msg.role == ChatMessage.Role.USER) {
                                "user"
                            } else {
                                "model"
                            }
                        )
                        val isLastUser =
                            msg.role == ChatMessage.Role.USER &&
                                index == messages.indexOfLast {
                                    it.role == ChatMessage.Role.USER
                                }
                        put(
                            "parts",
                            if (isLastUser && images.isNotEmpty()) {
                                buildMultipartParts(msg.content, images)
                            } else {
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("text", msg.content)
                                        }
                                    )
                                }
                            }
                        )
                    }
                )
            }
        }

    private fun buildMultipartParts(text: String, images: List<ByteArray>) = buildJsonArray {
        images.forEach { imageBytes ->
            add(
                buildJsonObject {
                    put(
                        "inlineData",
                        buildJsonObject {
                            put("mimeType", "image/png")
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
                put("text", text)
            }
        )
    }

    const val DEFAULT_MODEL = "gemini-2.0-flash-lite"
    const val DEFAULT_MAX_TOKENS = 4096
    const val BASE_URL =
        "https://generativelanguage.googleapis.com"
}
