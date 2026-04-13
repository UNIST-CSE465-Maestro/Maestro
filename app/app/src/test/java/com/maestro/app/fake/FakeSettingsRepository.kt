package com.maestro.app.fake

import com.maestro.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory SettingsRepository for ViewModel tests.
 */
class FakeSettingsRepository : SettingsRepository {

    private val apiKeyFlow = MutableStateFlow<String?>(null)

    override fun getApiKey(): Flow<String?> = apiKeyFlow

    override suspend fun setApiKey(key: String) {
        apiKeyFlow.value = key
    }

    override suspend fun clearApiKey() {
        apiKeyFlow.value = null
    }

    override suspend fun isApiKeySet(): Boolean = !apiKeyFlow.value.isNullOrBlank()
}
