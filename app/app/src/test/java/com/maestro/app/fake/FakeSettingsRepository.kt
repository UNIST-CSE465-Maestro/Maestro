package com.maestro.app.fake

import com.maestro.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository : SettingsRepository {

    private val geminiKeyFlow = MutableStateFlow<String?>(null)
    private val openAiKeyFlow = MutableStateFlow<String?>(null)
    private val providerFlow = MutableStateFlow<String?>(null)
    private val modelFlow = MutableStateFlow<String?>(null)
    private val serverUrlFlow = MutableStateFlow<String?>(null)
    private val accessTokenFlow = MutableStateFlow<String?>(null)
    private val refreshTokenFlow = MutableStateFlow<String?>(null)
    private val usernameFlow = MutableStateFlow<String?>(null)

    override fun getApiKey(): Flow<String?> = geminiKeyFlow
    override suspend fun setApiKey(key: String) {
        geminiKeyFlow.value = key
    }
    override suspend fun clearApiKey() {
        geminiKeyFlow.value = null
    }
    override suspend fun isApiKeySet(): Boolean = !geminiKeyFlow.value.isNullOrBlank()

    override fun getGeminiApiKey(): Flow<String?> = geminiKeyFlow
    override suspend fun setGeminiApiKey(key: String) {
        geminiKeyFlow.value = key
    }
    override suspend fun clearGeminiApiKey() {
        geminiKeyFlow.value = null
    }

    private val claudeKeyFlow = MutableStateFlow<String?>(null)

    override fun getOpenAiApiKey(): Flow<String?> = openAiKeyFlow
    override suspend fun setOpenAiApiKey(key: String) {
        openAiKeyFlow.value = key
    }
    override suspend fun clearOpenAiApiKey() {
        openAiKeyFlow.value = null
    }

    override fun getClaudeApiKey(): Flow<String?> = claudeKeyFlow
    override suspend fun setClaudeApiKey(key: String) {
        claudeKeyFlow.value = key
    }
    override suspend fun clearClaudeApiKey() {
        claudeKeyFlow.value = null
    }

    override fun getLlmProvider(): Flow<String?> = providerFlow
    override suspend fun setLlmProvider(provider: String) {
        providerFlow.value = provider
    }

    override fun getLlmModel(): Flow<String?> = modelFlow
    override suspend fun setLlmModel(model: String) {
        modelFlow.value = model
    }

    override fun getServerUrl(): Flow<String?> = serverUrlFlow
    override suspend fun setServerUrl(url: String) {
        serverUrlFlow.value = url
    }

    override fun getAccessToken(): Flow<String?> = accessTokenFlow
    override fun getRefreshToken(): Flow<String?> = refreshTokenFlow
    override suspend fun setTokens(access: String, refresh: String) {
        accessTokenFlow.value = access
        refreshTokenFlow.value = refresh
    }
    override suspend fun clearTokens() {
        accessTokenFlow.value = null
        refreshTokenFlow.value = null
        usernameFlow.value = null
    }
    override suspend fun isLoggedIn(): Boolean = !accessTokenFlow.value.isNullOrBlank()

    override fun getUsername(): Flow<String?> = usernameFlow
    override suspend fun setUsername(name: String) {
        usernameFlow.value = name
    }
}
