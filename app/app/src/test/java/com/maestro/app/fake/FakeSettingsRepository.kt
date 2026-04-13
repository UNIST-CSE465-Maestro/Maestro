package com.maestro.app.fake

import com.maestro.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository : SettingsRepository {

    private val apiKeyFlow = MutableStateFlow<String?>(null)
    private val serverUrlFlow = MutableStateFlow<String?>(null)
    private val accessTokenFlow = MutableStateFlow<String?>(null)
    private val refreshTokenFlow = MutableStateFlow<String?>(null)
    private val usernameFlow = MutableStateFlow<String?>(null)

    override fun getApiKey(): Flow<String?> = apiKeyFlow
    override suspend fun setApiKey(key: String) {
        apiKeyFlow.value = key
    }
    override suspend fun clearApiKey() {
        apiKeyFlow.value = null
    }
    override suspend fun isApiKeySet(): Boolean = !apiKeyFlow.value.isNullOrBlank()

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
