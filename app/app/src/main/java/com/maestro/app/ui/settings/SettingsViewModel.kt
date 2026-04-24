package com.maestro.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maestro.app.domain.repository.SettingsRepository
import com.maestro.app.domain.service.LlmService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val llmService: LlmService
) : ViewModel() {

    private val _geminiKeySet = MutableStateFlow(false)
    val geminiKeySet: StateFlow<Boolean> =
        _geminiKeySet.asStateFlow()

    private val _openAiKeySet = MutableStateFlow(false)
    val openAiKeySet: StateFlow<Boolean> =
        _openAiKeySet.asStateFlow()

    private val _claudeKeySet = MutableStateFlow(false)
    val claudeKeySet: StateFlow<Boolean> =
        _claudeKeySet.asStateFlow()

    // Keep legacy for compatibility
    val apiKeySet: StateFlow<Boolean> = _geminiKeySet

    private val _validationResult =
        MutableStateFlow<String?>(null)
    val validationResult: StateFlow<String?> =
        _validationResult.asStateFlow()

    private val _isValidating =
        MutableStateFlow(false)
    val isValidating: StateFlow<Boolean> =
        _isValidating.asStateFlow()

    val username: StateFlow<String?> =
        settingsRepository.getUsername()
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                null
            )

    init {
        viewModelScope.launch {
            settingsRepository.getGeminiApiKey()
                .collect {
                    _geminiKeySet.value =
                        !it.isNullOrBlank()
                }
        }
        viewModelScope.launch {
            settingsRepository.getOpenAiApiKey()
                .collect {
                    _openAiKeySet.value =
                        !it.isNullOrBlank()
                }
        }
        viewModelScope.launch {
            settingsRepository.getClaudeApiKey()
                .collect {
                    _claudeKeySet.value =
                        !it.isNullOrBlank()
                }
        }
    }

    fun saveAndValidateApiKey(key: String) {
        saveAndValidateGeminiKey(key)
    }

    fun saveAndValidateGeminiKey(key: String) {
        viewModelScope.launch {
            val trimmed = key.trim()
            settingsRepository.setGeminiApiKey(trimmed)
            _isValidating.value = true
            _validationResult.value = null
            try {
                val prev = settingsRepository
                    .getLlmProvider()
                settingsRepository.setLlmProvider(
                    "GEMINI"
                )
                val valid =
                    llmService.validateApiKey(trimmed)
                _validationResult.value = if (valid) {
                    "OK - Gemini API 키가 유효합니다"
                } else {
                    "Gemini API 키가 유효하지 않습니다"
                }
            } catch (_: Exception) {
                _validationResult.value =
                    "서버에 연결할 수 없습니다"
            } finally {
                _isValidating.value = false
            }
        }
    }

    fun saveAndValidateOpenAiKey(key: String) {
        viewModelScope.launch {
            val trimmed = key.trim()
            settingsRepository.setOpenAiApiKey(trimmed)
            _isValidating.value = true
            _validationResult.value = null
            try {
                val prev = settingsRepository
                    .getLlmProvider()
                settingsRepository.setLlmProvider(
                    "OPENAI"
                )
                val valid =
                    llmService.validateApiKey(trimmed)
                _validationResult.value = if (valid) {
                    "OK - OpenAI API 키가 유효합니다"
                } else {
                    "OpenAI API 키가 유효하지 않습니다"
                }
            } catch (_: Exception) {
                _validationResult.value =
                    "서버에 연결할 수 없습니다"
            } finally {
                _isValidating.value = false
            }
        }
    }

    fun saveAndValidateClaudeKey(key: String) {
        viewModelScope.launch {
            val trimmed = key.trim()
            settingsRepository.setClaudeApiKey(trimmed)
            _isValidating.value = true
            _validationResult.value = null
            try {
                settingsRepository.setLlmProvider(
                    "CLAUDE"
                )
                val valid =
                    llmService.validateApiKey(trimmed)
                _validationResult.value = if (valid) {
                    "OK - Claude API 키가 유효합니다"
                } else {
                    "Claude API 키가 유효하지 않습니다"
                }
            } catch (_: Exception) {
                _validationResult.value =
                    "서버에 연결할 수 없습니다"
            } finally {
                _isValidating.value = false
            }
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            settingsRepository.clearGeminiApiKey()
            _validationResult.value = null
        }
    }

    fun clearGeminiKey() {
        viewModelScope.launch {
            settingsRepository.clearGeminiApiKey()
            _validationResult.value = null
        }
    }

    fun clearOpenAiKey() {
        viewModelScope.launch {
            settingsRepository.clearOpenAiApiKey()
            _validationResult.value = null
        }
    }

    fun clearClaudeKey() {
        viewModelScope.launch {
            settingsRepository.clearClaudeApiKey()
            _validationResult.value = null
        }
    }

    fun logout() {
        viewModelScope.launch {
            settingsRepository.clearTokens()
        }
    }
}
