package com.maestro.app.data.repository

import android.content.Context
import com.maestro.app.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

class SettingsRepositoryImpl(
    context: Context
) : SettingsRepository {

    private val prefs = context.getSharedPreferences(
        "maestro_settings",
        Context.MODE_PRIVATE
    )

    // Flows
    private val geminiKeyFlow =
        MutableStateFlow(
            prefs.getString(KEY_GEMINI_KEY, null)
                ?: prefs.getString(KEY_API_KEY_LEGACY, null)
        )
    private val openAiKeyFlow =
        MutableStateFlow(prefs.getString(KEY_OPENAI_KEY, null))
    private val claudeKeyFlow =
        MutableStateFlow(prefs.getString(KEY_CLAUDE_KEY, null))
    private val providerFlow =
        MutableStateFlow(prefs.getString(KEY_LLM_PROVIDER, null))
    private val modelFlow =
        MutableStateFlow(prefs.getString(KEY_LLM_MODEL, null))
    private val serverUrlFlow =
        MutableStateFlow(prefs.getString(KEY_SERVER_URL, null))
    private val accessTokenFlow =
        MutableStateFlow(prefs.getString(KEY_ACCESS, null))
    private val refreshTokenFlow =
        MutableStateFlow(prefs.getString(KEY_REFRESH, null))
    private val usernameFlow =
        MutableStateFlow(prefs.getString(KEY_USERNAME, null))

    // Legacy — delegates to Gemini key
    override fun getApiKey(): Flow<String?> = geminiKeyFlow
    override suspend fun setApiKey(key: String) = setGeminiApiKey(key)
    override suspend fun clearApiKey() = clearGeminiApiKey()
    override suspend fun isApiKeySet(): Boolean =
        !prefs.getString(KEY_GEMINI_KEY, null).isNullOrBlank() ||
            !prefs.getString(KEY_API_KEY_LEGACY, null).isNullOrBlank()

    // Gemini
    override fun getGeminiApiKey(): Flow<String?> = geminiKeyFlow
    override suspend fun setGeminiApiKey(key: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_GEMINI_KEY, key).apply()
        geminiKeyFlow.value = key
    }
    override suspend fun clearGeminiApiKey() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_GEMINI_KEY).remove(KEY_API_KEY_LEGACY).apply()
        geminiKeyFlow.value = null
    }

    // OpenAI
    override fun getOpenAiApiKey(): Flow<String?> = openAiKeyFlow
    override suspend fun setOpenAiApiKey(key: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_OPENAI_KEY, key).apply()
        openAiKeyFlow.value = key
    }
    override suspend fun clearOpenAiApiKey() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_OPENAI_KEY).apply()
        openAiKeyFlow.value = null
    }

    // Claude
    override fun getClaudeApiKey(): Flow<String?> = claudeKeyFlow
    override suspend fun setClaudeApiKey(key: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_CLAUDE_KEY, key).apply()
        claudeKeyFlow.value = key
    }
    override suspend fun clearClaudeApiKey() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_CLAUDE_KEY).apply()
        claudeKeyFlow.value = null
    }

    // Provider & model
    override fun getLlmProvider(): Flow<String?> = providerFlow
    override suspend fun setLlmProvider(provider: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_LLM_PROVIDER, provider).apply()
        providerFlow.value = provider
    }
    override fun getLlmModel(): Flow<String?> = modelFlow
    override suspend fun setLlmModel(model: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_LLM_MODEL, model).apply()
        modelFlow.value = model
    }

    // Server URL
    override fun getServerUrl(): Flow<String?> = serverUrlFlow
    override suspend fun setServerUrl(url: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
        serverUrlFlow.value = url
    }

    // Auth tokens
    override fun getAccessToken(): Flow<String?> = accessTokenFlow
    override fun getRefreshToken(): Flow<String?> = refreshTokenFlow
    override suspend fun setTokens(access: String, refresh: String) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_ACCESS, access)
            .putString(KEY_REFRESH, refresh)
            .apply()
        accessTokenFlow.value = access
        refreshTokenFlow.value = refresh
    }
    override suspend fun clearTokens() = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_USERNAME)
            .apply()
        accessTokenFlow.value = null
        refreshTokenFlow.value = null
        usernameFlow.value = null
    }
    override suspend fun isLoggedIn(): Boolean = !prefs.getString(KEY_ACCESS, null).isNullOrBlank()

    // Username
    override fun getUsername(): Flow<String?> = usernameFlow
    override suspend fun setUsername(name: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_USERNAME, name).apply()
        usernameFlow.value = name
    }

    companion object {
        private const val KEY_API_KEY_LEGACY = "llm_api_key"
        private const val KEY_GEMINI_KEY = "gemini_api_key"
        private const val KEY_OPENAI_KEY = "openai_api_key"
        private const val KEY_CLAUDE_KEY = "claude_api_key"
        private const val KEY_LLM_PROVIDER = "llm_provider"
        private const val KEY_LLM_MODEL = "llm_model"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_USERNAME = "username"
    }
}
