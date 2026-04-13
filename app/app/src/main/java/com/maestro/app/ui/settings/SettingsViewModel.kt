package com.maestro.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maestro.app.data.remote.LoginRequest
import com.maestro.app.data.remote.MaestroServerApi
import com.maestro.app.data.remote.RegisterRequest
import com.maestro.app.domain.repository.SettingsRepository
import com.maestro.app.domain.service.LlmService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val llmService: LlmService,
    private val serverApi: MaestroServerApi
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

    val serverUrl: StateFlow<String?> =
        settingsRepository.getServerUrl()
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                null
            )

    val isLoggedIn: StateFlow<Boolean> =
        settingsRepository.getAccessToken()
            .map { !it.isNullOrBlank() }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                false
            )

    val username: StateFlow<String?> =
        settingsRepository.getUsername()
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                null
            )

    private val _serverMessage =
        MutableStateFlow<String?>(null)
    val serverMessage: StateFlow<String?> =
        _serverMessage.asStateFlow()

    private val _isServerLoading =
        MutableStateFlow(false)
    val isServerLoading: StateFlow<Boolean> =
        _isServerLoading.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.getApiKey().collect {
                _apiKeySet.value =
                    !it.isNullOrBlank()
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

    fun saveServerUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.setServerUrl(url)
            _serverMessage.value = "서버 URL 저장됨"
        }
    }

    fun login(loginUsername: String, password: String) {
        viewModelScope.launch {
            _isServerLoading.value = true
            _serverMessage.value = null
            try {
                val resp = serverApi.login(
                    LoginRequest(loginUsername, password)
                )
                if (resp.isSuccessful) {
                    val body = resp.body()!!
                    settingsRepository.setTokens(
                        body.access,
                        body.refresh
                    )
                    settingsRepository.setUsername(
                        loginUsername
                    )
                    _serverMessage.value =
                        "로그인 성공"
                } else {
                    _serverMessage.value =
                        "로그인 실패: ${resp.code()}"
                }
            } catch (e: Exception) {
                _serverMessage.value =
                    "오류: ${e.message}"
            } finally {
                _isServerLoading.value = false
            }
        }
    }

    fun register(regUsername: String, email: String, password: String) {
        viewModelScope.launch {
            _isServerLoading.value = true
            _serverMessage.value = null
            try {
                val resp = serverApi.register(
                    RegisterRequest(
                        regUsername,
                        email,
                        password
                    )
                )
                if (resp.isSuccessful) {
                    _serverMessage.value =
                        "회원가입 성공. 로그인해주세요."
                } else {
                    _serverMessage.value =
                        "회원가입 실패: ${resp.code()}"
                }
            } catch (e: Exception) {
                _serverMessage.value =
                    "오류: ${e.message}"
            } finally {
                _isServerLoading.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            settingsRepository.clearTokens()
            _serverMessage.value = "로그아웃 완료"
        }
    }
}
