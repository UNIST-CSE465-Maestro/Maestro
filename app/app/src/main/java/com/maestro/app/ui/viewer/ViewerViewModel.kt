package com.maestro.app.ui.viewer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maestro.app.data.remote.MaterialAnalyzerClient
import com.maestro.app.data.remote.MaterialAnalyzerHash
import com.maestro.app.data.repository.AnnotationRepositoryImpl
import com.maestro.app.domain.repository.SettingsRepository
import com.maestro.app.domain.service.QuizService
import com.maestro.app.ui.config.UxConfig
import com.maestro.app.ui.drawing.DrawingState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewerViewModel(
    private val annotationRepo: AnnotationRepositoryImpl,
    private val analyzerClient: MaterialAnalyzerClient,
    private val settingsRepository: SettingsRepository,
    private val quizService: QuizService,
    private val appContext: Context,
    val pdfId: String,
    val pageCount: Int,
    val pdfUri: Uri?
) : ViewModel() {

    val drawingState = DrawingState()

    private val _sidebarVisible = MutableStateFlow(false)
    val sidebarVisible = _sidebarVisible.asStateFlow()

    private val _pendingLlmImage =
        MutableStateFlow<ByteArray?>(null)
    val pendingLlmImage = _pendingLlmImage.asStateFlow()

    private val _pendingLlmPrompt =
        MutableStateFlow<String?>(null)
    val pendingLlmPrompt =
        _pendingLlmPrompt.asStateFlow()

    private val _quizResult =
        MutableStateFlow<String?>(null)
    val quizResult = _quizResult.asStateFlow()

    private val _isExtracting =
        MutableStateFlow(false)
    val isExtracting = _isExtracting.asStateFlow()

    private val _extractionMode =
        MutableStateFlow("standard")
    val extractionMode =
        _extractionMode.asStateFlow()

    private var lastSavedVersion = 0

    init {
        loadAnnotations()
    }

    fun toggleExtractionMode() {
        _extractionMode.value =
            if (_extractionMode.value == "standard") {
                "ai"
            } else {
                "standard"
            }
    }

    private fun loadAnnotations() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                annotationRepo.loadAll(
                    pdfId,
                    drawingState
                )
            }
            lastSavedVersion =
                drawingState.annotationVersion
        }
    }

    fun saveIfNeeded() {
        val currentVersion =
            drawingState.annotationVersion
        if (currentVersion <= lastSavedVersion) return
        lastSavedVersion = currentVersion
        viewModelScope.launch {
            delay(UxConfig.Timing.AUTOSAVE_DEBOUNCE_MS)
            withContext(Dispatchers.IO) {
                annotationRepo.saveAll(
                    pdfId,
                    drawingState
                )
            }
        }
    }

    fun toggleSidebar() {
        _sidebarVisible.value =
            !_sidebarVisible.value
    }

    fun sendSelectionToLlm(bitmap: ByteArray, prompt: String) {
        _sidebarVisible.value = true
        _pendingLlmImage.value = bitmap
        _pendingLlmPrompt.value = prompt
    }

    fun consumePendingLlm() {
        _pendingLlmImage.value = null
        _pendingLlmPrompt.value = null
    }

    fun extractAndQuiz() {
        if (_isExtracting.value) return
        val uri = pdfUri ?: return
        _isExtracting.value = true
        viewModelScope.launch {
            try {
                val mode = _extractionMode.value
                val content = loadOrExtract(uri, mode)
                val quiz =
                    quizService.generateQuiz(content)
                _quizResult.value = quiz
                _sidebarVisible.value = true
                _pendingLlmPrompt.value = quiz
            } catch (_: Throwable) {
                _quizResult.value =
                    "퀴즈 생성에 실패했습니다."
            } finally {
                _isExtracting.value = false
            }
        }
    }

    private suspend fun loadOrExtract(uri: Uri, mode: String): String {
        // Check local cache first
        val cached = loadContentMd(pdfId)
        if (cached != null) return cached

        // Compute hash and upload
        val hash = MaterialAnalyzerHash.compute(
            appContext,
            uri,
            mode
        )
        val task = analyzerClient.upload(
            uri,
            mode,
            hash
        )
        analyzerClient.pollUntilComplete(task.id)
        val content = analyzerClient.getResultMd(task.id)

        // Cache locally
        saveContentMd(pdfId, content)
        return content
    }

    private suspend fun saveContentMd(documentId: String, text: String) =
        withContext(Dispatchers.IO) {
            val dir = File(
                appContext.filesDir,
                "documents/$documentId"
            )
            dir.mkdirs()
            File(dir, "content.md").writeText(text)
        }

    private suspend fun loadContentMd(documentId: String): String? = withContext(Dispatchers.IO) {
        val file = File(
            appContext.filesDir,
            "documents/$documentId/content.md"
        )
        if (file.exists()) file.readText() else null
    }
}
