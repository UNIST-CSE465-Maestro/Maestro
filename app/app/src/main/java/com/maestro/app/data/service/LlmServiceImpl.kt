package com.maestro.app.data.service

import com.maestro.app.data.model.AnthropicRequestBuilder
import com.maestro.app.data.remote.AnthropicSseClient
import com.maestro.app.domain.model.ChatMessage
import com.maestro.app.domain.repository.SettingsRepository
import com.maestro.app.domain.service.LlmService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

class LlmServiceImpl(
    private val sseClient: AnthropicSseClient,
    private val settingsRepository: SettingsRepository
) : LlmService {

    override fun stream(
        messages: List<ChatMessage>,
        systemPrompt: String?,
        images: List<ByteArray>
    ): Flow<String> = flow {
        val apiKey = requireApiKey()
        val body = AnthropicRequestBuilder.build(
            messages = messages,
            systemPrompt = systemPrompt,
            images = images,
            stream = true
        )
        sseClient.stream(apiKey, body).collect { emit(it) }
    }

    override suspend fun complete(
        messages: List<ChatMessage>,
        systemPrompt: String?,
        images: List<ByteArray>
    ): String {
        val apiKey = requireApiKey()
        val body = AnthropicRequestBuilder.build(
            messages = messages,
            systemPrompt = systemPrompt,
            images = images,
            stream = false
        )
        return sseClient.complete(apiKey, body)
    }

    override suspend fun validateApiKey(apiKey: String): Boolean = sseClient.validateKey(apiKey)

    private suspend fun requireApiKey(): String {
        return settingsRepository.getApiKey().first()
            ?: throw IllegalStateException(
                "API key not configured"
            )
    }
}
