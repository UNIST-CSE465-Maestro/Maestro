package com.maestro.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maestro.app.BuildConfig
import com.maestro.app.data.remote.LoginRequest
import com.maestro.app.data.remote.MaestroServerApi
import com.maestro.app.domain.repository.SettingsRepository
import com.maestro.app.domain.service.LlmService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val llmService: LlmService,
    private val serverApi: MaestroServerApi
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

    private val _openRouterKeySet = MutableStateFlow(false)
    val openRouterKeySet: StateFlow<Boolean> =
        _openRouterKeySet.asStateFlow()

    private val _llmProvider = MutableStateFlow("OPENROUTER")
    val llmProvider: StateFlow<String> =
        _llmProvider.asStateFlow()

    private val _serverTokenSet = MutableStateFlow(false)
    val serverTokenSet: StateFlow<Boolean> =
        _serverTokenSet.asStateFlow()

    private val _serverUsername = MutableStateFlow<String?>(null)
    val serverUsername: StateFlow<String?> =
        _serverUsername.asStateFlow()

    private val _qeServerUrl = MutableStateFlow("")
    val qeServerUrl: StateFlow<String> =
        _qeServerUrl.asStateFlow()

    private val _serverAuthInProgress =
        MutableStateFlow(false)
    val serverAuthInProgress: StateFlow<Boolean> =
        _serverAuthInProgress.asStateFlow()

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
        viewModelScope.launch {
            settingsRepository.getOpenRouterApiKey()
                .collect {
                    _openRouterKeySet.value =
                        !it.isNullOrBlank()
                }
        }
        viewModelScope.launch {
            settingsRepository.getLlmProvider()
                .collect {
                    _llmProvider.value =
                        it?.takeIf { provider ->
                            provider.isNotBlank()
                        } ?: "OPENROUTER"
                }
        }
        viewModelScope.launch {
            settingsRepository.getAccessToken()
                .collect {
                    _serverTokenSet.value =
                        !it.isNullOrBlank() ||
                        BuildConfig
                            .MAESTRO_SERVER_BEARER_TOKEN
                            .isNotBlank()
                }
        }
        viewModelScope.launch {
            settingsRepository.getUsername()
                .collect {
                    _serverUsername.value =
                        it?.takeIf { name ->
                            name.isNotBlank()
                        }
                }
        }
        viewModelScope.launch {
            settingsRepository.getQeServerUrl()
                .collect {
                    _qeServerUrl.value = it.orEmpty()
                }
        }
    }

    fun saveQeServerUrl(url: String) {
        viewModelScope.launch {
            val trimmed = url.trim()
            if (trimmed.isBlank()) {
                _validationResult.value =
                    "QE 서버 주소를 입력해 주세요"
                return@launch
            }
            settingsRepository.setQeServerUrl(trimmed)
            _validationResult.value =
                "OK - QE 서버 주소가 저장되었습니다"
        }
    }

    fun saveAndValidateApiKey(key: String) {
        saveAndValidateGeminiKey(key)
    }

    fun saveAndValidateGeminiKey(key: String) {
        viewModelScope.launch {
            val trimmed = key.trim()
            _isValidating.value = true
            _validationResult.value = null
            try {
                settingsRepository.setLlmProvider(
                    "GEMINI"
                )
                val valid =
                    llmService.validateApiKey(trimmed)
                _validationResult.value =
                    if (valid) {
                        settingsRepository.setGeminiApiKey(trimmed)
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
            _isValidating.value = true
            _validationResult.value = null
            try {
                settingsRepository.setLlmProvider(
                    "OPENAI"
                )
                val valid =
                    llmService.validateApiKey(trimmed)
                _validationResult.value =
                    if (valid) {
                        settingsRepository.setOpenAiApiKey(trimmed)
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
            _isValidating.value = true
            _validationResult.value = null
            try {
                settingsRepository.setLlmProvider(
                    "CLAUDE"
                )
                val valid =
                    llmService.validateApiKey(trimmed)
                _validationResult.value =
                    if (valid) {
                        settingsRepository.setClaudeApiKey(trimmed)
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

    fun saveAndValidateOpenRouterKey(key: String) {
        viewModelScope.launch {
            val trimmed = key.trim()
            _isValidating.value = true
            _validationResult.value = null
            try {
                settingsRepository.setLlmProvider(
                    "OPENROUTER"
                )
                settingsRepository.setLlmModel(
                    "openrouter/free"
                )
                val valid =
                    llmService.validateApiKey(trimmed)
                _validationResult.value =
                    if (valid) {
                        settingsRepository.setOpenRouterApiKey(trimmed)
                        "OK - OpenRouter API 키가 유효합니다"
                    } else {
                        "OpenRouter API 키가 유효하지 않습니다"
                    }
            } catch (_: Exception) {
                _validationResult.value =
                    "서버에 연결할 수 없습니다"
            } finally {
                _isValidating.value = false
            }
        }
    }

    fun activateProvider(provider: String) {
        viewModelScope.launch {
            settingsRepository.setLlmProvider(provider)
            settingsRepository.setLlmModel(
                when (provider) {
                    "OPENAI" -> "gpt-4o-mini"
                    "CLAUDE" -> "claude-sonnet-4-6"
                    "OPENROUTER" -> "openrouter/free"
                    else -> "gemini-2.0-flash"
                }
            )
            _validationResult.value = null
        }
    }

    fun saveServerBearerToken(token: String) {
        viewModelScope.launch {
            val raw = token.trim()
            val normalized =
                if (
                    raw.startsWith("Bearer ", ignoreCase = true)
                ) {
                    raw.drop("Bearer ".length).trim()
                } else {
                    raw
                }
            if (normalized.isBlank()) {
                _validationResult.value =
                    "MinerU 서버 토큰을 입력해 주세요"
                return@launch
            }
            settingsRepository.setTokens(
                access = normalized,
                refresh = ""
            )
            _validationResult.value =
                "OK - MinerU 서버 토큰이 저장되었습니다"
        }
    }

    fun authenticateMineruServer(username: String, password: String) {
        viewModelScope.launch {
            val trimmedUsername = username.trim()
            if (trimmedUsername.isBlank() ||
                password.isBlank()
            ) {
                _validationResult.value =
                    "MinerU 서버 아이디와 비밀번호를 입력해 주세요"
                return@launch
            }

            _serverAuthInProgress.value = true
            _validationResult.value = null
            try {
                val response =
                    serverApi.login(
                        LoginRequest(
                            username = trimmedUsername,
                            password = password
                        )
                    )
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body == null) {
                        _validationResult.value =
                            "MinerU 서버 인증 실패: 응답이 비어 있습니다"
                    } else {
                        settingsRepository.setTokens(
                            body.access,
                            body.refresh
                        )
                        settingsRepository.setUsername(
                            trimmedUsername
                        )
                        _validationResult.value =
                            "OK - MinerU 서버 인증이 완료되었습니다"
                    }
                } else {
                    val error =
                        response.errorBody()
                            ?.string()
                            ?.take(160)
                            .orEmpty()
                    _validationResult.value =
                        "MinerU 서버 인증 실패: HTTP ${response.code()} $error"
                }
            } catch (_: Exception) {
                _validationResult.value =
                    "MinerU 서버에 연결할 수 없습니다"
            } finally {
                _serverAuthInProgress.value = false
            }
        }
    }

    fun clearServerBearerToken() {
        viewModelScope.launch {
            settingsRepository.setTokens(
                access = "",
                refresh = ""
            )
            settingsRepository.setUsername("")
            _validationResult.value = null
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

    fun clearOpenRouterKey() {
        viewModelScope.launch {
            settingsRepository.clearOpenRouterApiKey()
            _validationResult.value = null
        }
    }
}
