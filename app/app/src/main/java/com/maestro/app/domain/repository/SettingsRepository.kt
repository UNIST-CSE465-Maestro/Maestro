package com.maestro.app.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getApiKey(): Flow<String?>
    suspend fun setApiKey(key: String)
    suspend fun clearApiKey()
    suspend fun isApiKeySet(): Boolean
}
