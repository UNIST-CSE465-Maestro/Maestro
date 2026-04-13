package com.maestro.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maestro.app.domain.repository.SettingsRepository
import com.maestro.app.domain.service.LlmService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val llmService: LlmService
) : ViewModel() {

    private val _apiKeySet =
        MutableStateFlow(false)
    val apiKeySet: StateFlow<Boolean> =
        _apiKeySet.asStateFlow()

    private val _validationResult =
        MutableStateFlow<String?>(null)
    val validationResult: StateFlow<String?> =
        _validationResult.asStateFlow()

    private val _isValidating =
        MutableStateFlow(false)
    val isValidating: StateFlow<Boolean> =
        _isValidating.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.getApiKey().collect { key ->
                _apiKeySet.value = !key.isNullOrBlank()
            }
        }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            settingsRepository.setApiKey(key)
            _validationResult.value = null
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            settingsRepository.clearApiKey()
            _validationResult.value = null
        }
    }

    fun validateApiKey(key: String) {
        viewModelScope.launch {
            _isValidating.value = true
            _validationResult.value = null
            try {
                val valid =
                    llmService.validateApiKey(key)
                _validationResult.value = if (valid) {
                    "OK - API 키가 유효합니다"
                } else {
                    "API 키가 유효하지 않습니다"
                }
            } catch (e: Exception) {
                _validationResult.value =
                    "오류: ${e.message}"
            } finally {
                _isValidating.value = false
            }
        }
    }
}
