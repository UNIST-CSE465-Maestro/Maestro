package com.maestro.app.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maestro.app.data.local.LocalProfile
import com.maestro.app.data.local.ProfileLocalDataSource
import com.maestro.app.domain.model.KnowledgeDashboard
import com.maestro.app.domain.repository.KnowledgeRepository
import com.maestro.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoading: Boolean = true,
    val profile: LocalProfile = LocalProfile(),
    val username: String = "",
    val dashboard: KnowledgeDashboard = KnowledgeDashboard(),
    val errorMessage: String? = null
)

class ProfileViewModel(
    private val profileDataSource: ProfileLocalDataSource,
    private val settingsRepository: SettingsRepository,
    private val knowledgeRepository: KnowledgeRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> =
        _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            profileDataSource.profile.collect { profile ->
                _uiState.value = _uiState.value.copy(
                    profile = profile
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null
            )
            try {
                val username = settingsRepository
                    .getUsername()
                    .firstOrNull()
                    .orEmpty()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    username = username,
                    profile = profileDataSource.getProfile(),
                    dashboard = knowledgeRepository.loadDashboard()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }

    fun updateDisplayName(name: String) {
        viewModelScope.launch {
            profileDataSource.setDisplayName(name)
        }
    }

    fun updateAvatar(uri: Uri) {
        viewModelScope.launch {
            profileDataSource.saveAvatar(uri)
        }
    }
}
