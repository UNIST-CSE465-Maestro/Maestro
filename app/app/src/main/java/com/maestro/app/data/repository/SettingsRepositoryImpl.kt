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
    private val apiKeyFlow =
        MutableStateFlow(prefs.getString(KEY_API_KEY, null))
    private val serverUrlFlow =
        MutableStateFlow(prefs.getString(KEY_SERVER_URL, null))
    private val accessTokenFlow =
        MutableStateFlow(prefs.getString(KEY_ACCESS, null))
    private val refreshTokenFlow =
        MutableStateFlow(prefs.getString(KEY_REFRESH, null))
    private val usernameFlow =
        MutableStateFlow(prefs.getString(KEY_USERNAME, null))

    override fun getApiKey(): Flow<String?> = apiKeyFlow

    override suspend fun setApiKey(key: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_API_KEY, key).apply()
        apiKeyFlow.value = key
    }

    override suspend fun clearApiKey() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_API_KEY).apply()
        apiKeyFlow.value = null
    }

    override suspend fun isApiKeySet(): Boolean {
        return !prefs.getString(KEY_API_KEY, null)
            .isNullOrBlank()
    }

    override fun getServerUrl(): Flow<String?> = serverUrlFlow

    override suspend fun setServerUrl(url: String) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_SERVER_URL, url).apply()
        serverUrlFlow.value = url
    }

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

    override suspend fun isLoggedIn(): Boolean {
        return !prefs.getString(KEY_ACCESS, null)
            .isNullOrBlank()
    }

    override fun getUsername(): Flow<String?> = usernameFlow

    override suspend fun setUsername(name: String) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_USERNAME, name).apply()
        usernameFlow.value = name
    }

    companion object {
        private const val KEY_API_KEY =
            "anthropic_api_key"
        private const val KEY_SERVER_URL =
            "server_url"
        private const val KEY_ACCESS =
            "access_token"
        private const val KEY_REFRESH =
            "refresh_token"
        private const val KEY_USERNAME =
            "username"
    }
}
