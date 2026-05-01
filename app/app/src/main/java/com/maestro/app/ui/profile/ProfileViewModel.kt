package com.maestro.app.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maestro.app.data.local.LocalProfile
import com.maestro.app.data.local.ModelArtifactLocalDataSource
import com.maestro.app.data.local.ModelArtifactState
import com.maestro.app.data.local.ModelArtifactType
import com.maestro.app.data.local.MonitoringLogCategory
import com.maestro.app.data.local.MonitoringLogEntry
import com.maestro.app.data.local.MonitoringLogLocalDataSource
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
    val modelArtifacts: List<ModelArtifactState> = emptyList(),
    val monitoringLogs: List<MonitoringLogEntry> = emptyList(),
    val errorMessage: String? = null
) {
    fun logsFor(
        category: MonitoringLogCategory
    ): List<MonitoringLogEntry> =
        monitoringLogs.filter { it.category == category }
}

class ProfileViewModel(
    private val profileDataSource: ProfileLocalDataSource,
    private val settingsRepository: SettingsRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val modelArtifacts: ModelArtifactLocalDataSource,
    private val monitoringLogs: MonitoringLogLocalDataSource
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
        viewModelScope.launch {
            modelArtifacts.states.collect { artifacts ->
                _uiState.value = _uiState.value.copy(
                    modelArtifacts = artifacts
                )
            }
        }
        viewModelScope.launch {
            monitoringLogs.logs.collect { logs ->
                _uiState.value = _uiState.value.copy(
                    monitoringLogs = logs
                )
            }
        }
    }

    fun refresh(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }
            try {
                val username = settingsRepository
                    .getUsername()
                    .firstOrNull()
                    .orEmpty()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    username = username,
                    profile = profileDataSource.getProfile(),
                    dashboard = knowledgeRepository.loadDashboard(),
                    modelArtifacts = modelArtifacts.states.value,
                    monitoringLogs = monitoringLogs.listLogs()
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

    fun uploadModel(type: ModelArtifactType, uri: Uri) {
        viewModelScope.launch {
            try {
                val state = modelArtifacts.saveModel(type, uri)
                monitoringLogs.append(
                    category = MonitoringLogCategory.UX_RELIABILITY,
                    eventType = "model_uploaded",
                    metadata = mapOf(
                        "model_type" to type.name,
                        "file_size_bytes" to state.fileSizeBytes.toString(),
                        "file_path" to state.filePath.orEmpty()
                    )
                )
                refresh()
            } catch (e: Exception) {
                monitoringLogs.append(
                    category = MonitoringLogCategory.UX_RELIABILITY,
                    eventType = "model_upload_failed",
                    metadata = mapOf(
                        "model_type" to type.name,
                        "error" to (e.message ?: e::class.java.name)
                    )
                )
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message
                )
            }
        }
    }

    fun clearMonitoringLogs() {
        viewModelScope.launch {
            monitoringLogs.clear()
            refresh()
        }
    }

    fun deleteMonitoringLogs(ids: Set<String>) {
        viewModelScope.launch {
            monitoringLogs.delete(ids)
            refresh(showLoading = false)
        }
    }
}
