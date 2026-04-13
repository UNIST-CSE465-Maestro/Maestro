package com.maestro.app.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getApiKey(): Flow<String?>
    suspend fun setApiKey(key: String)
    suspend fun clearApiKey()
    suspend fun isApiKeySet(): Boolean

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
