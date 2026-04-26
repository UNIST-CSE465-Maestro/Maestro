package com.maestro.app.data.service

import com.maestro.app.data.model.LlmRequestBuilder
import com.maestro.app.data.remote.ClaudeClient
import com.maestro.app.data.remote.LlmClient
import com.maestro.app.data.remote.OpenAiClient
import com.maestro.app.domain.model.ChatMessage
import com.maestro.app.domain.model.LlmProvider
import com.maestro.app.domain.repository.SettingsRepository
import com.maestro.app.domain.service.LlmService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

class LlmServiceImpl(
    private val geminiClient: LlmClient,
    private val openAiClient: OpenAiClient,
    private val claudeClient: ClaudeClient,
    private val settingsRepository: SettingsRepository
) : LlmService {

    private val modelCache =
        mutableMapOf<LlmProvider, List<String>>()

    override fun stream(
        messages: List<ChatMessage>,
        systemPrompt: String?,
        images: List<ByteArray>
    ): Flow<String> = flow {
        val provider = getProvider()
        when (provider) {
            LlmProvider.GEMINI -> {
                val apiKey = requireGeminiKey()
                val model = getModel(
                    LlmRequestBuilder.DEFAULT_MODEL
                )
                val body = LlmRequestBuilder.build(
                    messages = messages,
                    systemPrompt = systemPrompt,
                    images = images
                )
                geminiClient.stream(
                    apiKey,
                    body,
                    model
                ).collect { emit(it) }
            }
            LlmProvider.OPENAI -> {
                val apiKey = requireOpenAiKey()
                val model = getModel(
                    OpenAiClient.DEFAULT_MODEL
                )
                openAiClient.stream(
                    apiKey,
                    messages,
                    systemPrompt,
                    images,
                    model
                ).collect { emit(it) }
            }
            LlmProvider.CLAUDE -> {
                val apiKey = requireClaudeKey()
                val model = getModel(
                    ClaudeClient.DEFAULT_MODEL
                )
                claudeClient.stream(
                    apiKey,
                    messages,
                    systemPrompt,
                    images,
                    model
                ).collect { emit(it) }
            }
        }
    }

    override suspend fun complete(
        messages: List<ChatMessage>,
        systemPrompt: String?,
        images: List<ByteArray>
    ): String {
        val provider = getProvider()
        return when (provider) {
            LlmProvider.GEMINI -> {
                val apiKey = requireGeminiKey()
                val model = getModel(
                    LlmRequestBuilder.DEFAULT_MODEL
                )
                val body = LlmRequestBuilder.build(
                    messages = messages,
                    systemPrompt = systemPrompt,
                    images = images
                )
                geminiClient.complete(
                    apiKey,
                    body,
                    model
                )
            }
            LlmProvider.OPENAI -> {
                throw UnsupportedOperationException(
                    "OpenAI complete not implemented"
                )
            }
            LlmProvider.CLAUDE -> {
                throw UnsupportedOperationException(
                    "Claude complete not implemented"
                )
            }
        }
    }

    override suspend fun validateApiKey(apiKey: String): Boolean {
        val provider = getProvider()
        return when (provider) {
            LlmProvider.GEMINI ->
                geminiClient.validateKey(apiKey)
            LlmProvider.OPENAI ->
                openAiClient.validateKey(apiKey)
            LlmProvider.CLAUDE ->
                claudeClient.validateKey(apiKey)
        }
    }

    override suspend fun fetchModels(): List<String> {
        val provider = getProvider()
        modelCache[provider]?.let { return it }
        val models = when (provider) {
            LlmProvider.GEMINI -> {
                val key = requireGeminiKey()
                geminiClient.fetchModels(key)
            }
            LlmProvider.OPENAI -> {
                val key = requireOpenAiKey()
                openAiClient.fetchModels(key)
            }
            LlmProvider.CLAUDE ->
                claudeClient.fetchModels()
        }
        if (models.isNotEmpty()) {
            modelCache[provider] = models
        }
        return models
    }

    override suspend fun warmUp(): Result<Unit> {
        return try {
            when (getProvider()) {
                LlmProvider.GEMINI ->
                    geminiClient.warmUp(requireGeminiKey())
                LlmProvider.OPENAI ->
                    openAiClient.warmUp(requireOpenAiKey())
                LlmProvider.CLAUDE ->
                    claudeClient.warmUp(requireClaudeKey())
            }
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private suspend fun getProvider(): LlmProvider {
        val name = settingsRepository
            .getLlmProvider().first()
        return try {
            LlmProvider.valueOf(
                name?.uppercase() ?: "GEMINI"
            )
        } catch (_: Throwable) {
            LlmProvider.GEMINI
        }
    }

    private suspend fun getModel(default: String): String {
        return settingsRepository.getLlmModel()
            .first() ?: default
    }

    private suspend fun requireGeminiKey(): String {
        return settingsRepository.getGeminiApiKey()
            .first()
            ?: throw IllegalStateException(
                "Gemini API 키가 설정되지 않았습니다"
            )
    }

    private suspend fun requireOpenAiKey(): String {
        return settingsRepository.getOpenAiApiKey()
            .first()
            ?: throw IllegalStateException(
                "OpenAI API 키가 설정되지 않았습니다"
            )
    }

    private suspend fun requireClaudeKey(): String {
        return settingsRepository.getClaudeApiKey()
            .first()
            ?: throw IllegalStateException(
                "Claude API 키가 설정되지 않았습니다"
            )
    }
}
