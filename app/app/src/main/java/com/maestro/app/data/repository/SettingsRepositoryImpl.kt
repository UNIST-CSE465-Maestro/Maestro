package com.maestro.app.data.repository

import android.content.Context
import com.maestro.app.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

class SettingsRepositoryImpl(context: Context) : SettingsRepository {

    private val prefs = context.getSharedPreferences("maestro_settings", Context.MODE_PRIVATE)
    private val apiKeyFlow = MutableStateFlow(prefs.getString(KEY_API_KEY, null))

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
        return !prefs.getString(KEY_API_KEY, null).isNullOrBlank()
    }

    companion object {
        private const val KEY_API_KEY = "anthropic_api_key"
    }
}
