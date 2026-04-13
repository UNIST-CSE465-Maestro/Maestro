package com.maestro.app.ui.viewer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maestro.app.data.repository.AnnotationRepositoryImpl
import com.maestro.app.ui.config.UxConfig
import com.maestro.app.ui.drawing.DrawingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewerViewModel(
    private val annotationRepo: AnnotationRepositoryImpl,
    val pdfId: String,
    val pageCount: Int,
    val pdfUri: Uri?
) : ViewModel() {

    val drawingState = DrawingState()

    private val _sidebarVisible = MutableStateFlow(false)
    val sidebarVisible = _sidebarVisible.asStateFlow()

    private var lastSavedVersion = 0

    init {
        loadAnnotations()
    }

    private fun loadAnnotations() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                annotationRepo.loadAll(pdfId, drawingState)
            }
            lastSavedVersion = drawingState.annotationVersion
        }
    }

    fun saveIfNeeded() {
        val currentVersion = drawingState.annotationVersion
        if (currentVersion <= lastSavedVersion) return
        lastSavedVersion = currentVersion
        viewModelScope.launch {
            delay(UxConfig.Timing.AUTOSAVE_DEBOUNCE_MS)
            withContext(Dispatchers.IO) {
                annotationRepo.saveAll(pdfId, drawingState)
            }
        }
    }

    fun toggleSidebar() {
        _sidebarVisible.value = !_sidebarVisible.value
    }
}
