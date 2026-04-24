package com.maestro.app.domain.service

import com.maestro.app.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface LlmService {
    fun stream(
        messages: List<ChatMessage>,
        systemPrompt: String? = null,
        images: List<ByteArray> = emptyList()
    ): Flow<String>

    suspend fun complete(
        messages: List<ChatMessage>,
        systemPrompt: String? = null,
        images: List<ByteArray> = emptyList()
    ): String

    suspend fun validateApiKey(apiKey: String): Boolean
    suspend fun fetchModels(): List<String>
}
