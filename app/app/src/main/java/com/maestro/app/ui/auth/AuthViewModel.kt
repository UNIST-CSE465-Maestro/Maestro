package com.maestro.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maestro.app.data.remote.LoginRequest
import com.maestro.app.data.remote.MaestroServerApi
import com.maestro.app.data.remote.RegisterRequest
import com.maestro.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AuthViewModel(
    private val settingsRepository: SettingsRepository,
    private val serverApi: MaestroServerApi
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> =
        settingsRepository.getAccessToken()
            .map { !it.isNullOrBlank() }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                false
            )

    private val _message =
        MutableStateFlow<String?>(null)
    val message: StateFlow<String?> =
        _message.asStateFlow()

    private val _isLoading =
        MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> =
        _isLoading.asStateFlow()

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _message.value = null
            try {
                val resp = serverApi.login(
                    LoginRequest(username, password)
                )
                if (resp.isSuccessful) {
                    val body = resp.body()!!
                    settingsRepository.setTokens(
                        body.access,
                        body.refresh
                    )
                    settingsRepository.setUsername(
                        username
                    )
                } else {
                    _message.value =
                        "로그인 실패: ${resp.code()}"
                }
            } catch (e: Exception) {
                _message.value =
                    "서버에 연결할 수 없습니다"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun register(username: String, email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _message.value = null
            try {
                val resp = serverApi.register(
                    RegisterRequest(
                        username,
                        email,
                        password
                    )
                )
                if (resp.isSuccessful) {
                    _message.value =
                        "회원가입 성공. 로그인해주세요."
                } else {
                    _message.value =
                        "회원가입 실패: ${resp.code()}"
                }
            } catch (e: Exception) {
                _message.value =
                    "서버에 연결할 수 없습니다"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
