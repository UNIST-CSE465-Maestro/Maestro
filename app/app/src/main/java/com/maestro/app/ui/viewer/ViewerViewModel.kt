package com.maestro.app.ui.viewer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maestro.app.data.local.ExtractionProgressStore
import com.maestro.app.data.local.StudyEventLocalDataSource
import com.maestro.app.data.local.StudyEventType
import com.maestro.app.data.remote.MaterialAnalyzerClient
import com.maestro.app.data.remote.MaterialAnalyzerHash
import com.maestro.app.data.repository.AnnotationRepositoryImpl
import com.maestro.app.domain.repository.DocumentRepository
import com.maestro.app.domain.repository.SettingsRepository
import com.maestro.app.ui.config.UxConfig
import com.maestro.app.ui.drawing.DrawingState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewerViewModel(
    private val annotationRepo: AnnotationRepositoryImpl,
    private val analyzerClient: MaterialAnalyzerClient,
    private val settingsRepository: SettingsRepository,
    private val documentRepository: DocumentRepository,
    private val studyEvents: StudyEventLocalDataSource,
    extractionProgressStore: ExtractionProgressStore,
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

    private val _documentContent =
        MutableStateFlow<String?>(null)
    val documentContent =
        _documentContent.asStateFlow()

    private val _isPinned = MutableStateFlow(false)
    val isPinned = _isPinned.asStateFlow()

    private val _bookmarkedPages =
        MutableStateFlow<Set<Int>>(emptySet())
    val bookmarkedPages = _bookmarkedPages.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage = _currentPage.asStateFlow()

    val isCurrentPageBookmarked: StateFlow<Boolean> =
        combine(_bookmarkedPages, _currentPage) { pages, page ->
            page in pages
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            false
        )

    val extractionProgress: StateFlow<Int?> =
        extractionProgressStore.progress.map { progress ->
            progress[pdfId]
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            null
        )

    private var lastSavedVersion = 0

    init {
        recordDocumentOpened()
        loadAnnotations()
        loadDocumentContent()
        loadDocumentMeta()
    }

    private fun loadDocumentContent() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _documentContent.value =
                    loadContentMd(pdfId)
            } catch (_: Throwable) {}
        }
    }

    private fun loadAnnotations() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                annotationRepo.loadAll(
                    pdfId,
                    drawingState
                )
            } catch (_: Throwable) {}
            lastSavedVersion =
                drawingState.annotationVersion
        }
    }

    fun saveIfNeeded() {
        val currentVersion =
            drawingState.annotationVersion
        if (currentVersion <= lastSavedVersion) return
        lastSavedVersion = currentVersion
        studyEvents.append(
            type = StudyEventType.ANNOTATION_SAVED,
            documentId = pdfId,
            pageIndex = drawingState.activePageIndex
                .coerceAtLeast(0)
        )
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

    private fun loadDocumentMeta() {
        viewModelScope.launch(Dispatchers.IO) {
            val doc = documentRepository.loadDocuments()
                .find { it.id == pdfId }
            if (doc != null) {
                _bookmarkedPages.value = doc.bookmarkedPages
                _isPinned.value = doc.isPinned
            }
        }
    }

    fun togglePin() {
        viewModelScope.launch {
            val doc = documentRepository.loadDocuments()
                .find { it.id == pdfId } ?: return@launch
            val newPinned = !doc.isPinned
            _isPinned.value = newPinned
            documentRepository.updateDocument(
                doc.copy(isPinned = newPinned)
            )
        }
    }

    fun toggleBookmark(page: Int) {
        viewModelScope.launch {
            val current = _bookmarkedPages.value
            val updated = if (page in current) {
                current - page
            } else {
                current + page
            }
            _bookmarkedPages.value = updated
            val doc = documentRepository.loadDocuments()
                .find { it.id == pdfId } ?: return@launch
            documentRepository.updateDocument(
                doc.copy(bookmarkedPages = updated)
            )
            studyEvents.append(
                type = StudyEventType.BOOKMARK_TOGGLED,
                documentId = pdfId,
                pageIndex = page,
                metadata = mapOf(
                    "bookmarked" to (page in updated).toString()
                )
            )
        }
    }

    fun setCurrentPage(page: Int) {
        _currentPage.value = page
        studyEvents.append(
            type = StudyEventType.PAGE_VIEWED,
            documentId = pdfId,
            pageIndex = page
        )
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
        if (_documentContent.value.isNullOrBlank()) {
            return
        }
        studyEvents.append(
            type = StudyEventType.QUIZ_REQUESTED,
            documentId = pdfId,
            pageIndex = _currentPage.value,
            promptLength = _documentContent.value?.length
        )
        _sidebarVisible.value = true
        _pendingLlmPrompt.value =
            "이 문서의 내용을 바탕으로 " +
            "객관식 퀴즈 5개를 만들어줘. " +
            "각 문제에 4개 선택지(A-D)와 " +
            "정답을 포함해줘." +
            "\n<!--${System.currentTimeMillis()}-->"
    }

    fun recordLlmRequested(prompt: String, hasImage: Boolean) {
        studyEvents.append(
            type = StudyEventType.LLM_REQUESTED,
            documentId = pdfId,
            pageIndex = _currentPage.value,
            promptLength = prompt.length,
            metadata = mapOf(
                "hasImage" to hasImage.toString()
            )
        )
    }

    private fun recordDocumentOpened() {
        studyEvents.append(
            type = StudyEventType.DOCUMENT_OPENED,
            documentId = pdfId
        )
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
