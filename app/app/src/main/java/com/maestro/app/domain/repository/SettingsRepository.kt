package com.maestro.app.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    // Legacy — delegates to Gemini key
    fun getApiKey(): Flow<String?>
    suspend fun setApiKey(key: String)
    suspend fun clearApiKey()
    suspend fun isApiKeySet(): Boolean

    // Provider-specific API keys
    fun getGeminiApiKey(): Flow<String?>
    suspend fun setGeminiApiKey(key: String)
    suspend fun clearGeminiApiKey()
    fun getOpenAiApiKey(): Flow<String?>
    suspend fun setOpenAiApiKey(key: String)
    suspend fun clearOpenAiApiKey()
    fun getClaudeApiKey(): Flow<String?>
    suspend fun setClaudeApiKey(key: String)
    suspend fun clearClaudeApiKey()

    // LLM provider & model
    fun getLlmProvider(): Flow<String?>
    suspend fun setLlmProvider(provider: String)
    fun getLlmModel(): Flow<String?>
    suspend fun setLlmModel(model: String)

    fun getServerUrl(): Flow<String?>
    suspend fun setServerUrl(url: String)
    fun getAccessToken(): Flow<String?>
    fun getRefreshToken(): Flow<String?>
    suspend fun setTokens(access: String, refresh: String)
    suspend fun clearTokens()
    suspend fun isLoggedIn(): Boolean
    fun getUsername(): Flow<String?>
    suspend fun setUsername(name: String)
}
