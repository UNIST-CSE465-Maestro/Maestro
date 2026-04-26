package com.maestro.app.ui.viewer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maestro.app.data.local.ExtractionProgressStore
import com.maestro.app.data.local.QuizResponseLocalDataSource
import com.maestro.app.data.local.QuizResponseRecord
import com.maestro.app.data.local.StudyEventLocalDataSource
import com.maestro.app.data.local.StudyEventType
import com.maestro.app.data.remote.MaterialAnalyzerClient
import com.maestro.app.data.remote.MaterialAnalyzerHash
import com.maestro.app.data.repository.AnnotationRepositoryImpl
import com.maestro.app.domain.repository.DocumentRepository
import com.maestro.app.domain.repository.SettingsRepository
import com.maestro.app.ui.components.StudySidebarMode
import com.maestro.app.ui.config.UxConfig
import com.maestro.app.ui.drawing.DrawingState
import java.io.File
import java.security.MessageDigest
import java.util.Locale
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
    private val quizResponses: QuizResponseLocalDataSource,
    extractionProgressStore: ExtractionProgressStore,
    private val appContext: Context,
    val pdfId: String,
    val pageCount: Int,
    val pdfUri: Uri?
) : ViewModel() {

    val drawingState = DrawingState()

    private val _sidebarVisible = MutableStateFlow(false)
    val sidebarVisible = _sidebarVisible.asStateFlow()

    private val _sidebarMode =
        MutableStateFlow(StudySidebarMode.CHAT)
    val sidebarMode = _sidebarMode.asStateFlow()

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

    private val _quizMastery =
        MutableStateFlow(0.35f)
    val quizMastery = _quizMastery.asStateFlow()

    private val _quizHistory =
        MutableStateFlow<List<QuizResponseRecord>>(emptyList())
    val quizHistory = _quizHistory.asStateFlow()

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
        loadQuizMastery()
        loadQuizHistory()
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

    fun toggleChatSidebar() {
        if (
            _sidebarVisible.value &&
            _sidebarMode.value == StudySidebarMode.CHAT
        ) {
            _sidebarVisible.value = false
        } else {
            _sidebarMode.value = StudySidebarMode.CHAT
            _sidebarVisible.value = true
        }
    }

    fun setSidebarMode(mode: StudySidebarMode) {
        _sidebarMode.value = mode
    }

    fun collapseSidebar() {
        _sidebarVisible.value = false
    }

    fun sendSelectionToLlm(bitmap: ByteArray, prompt: String) {
        _sidebarVisible.value = true
        _sidebarMode.value = StudySidebarMode.CHAT
        _pendingLlmImage.value = bitmap
        _pendingLlmPrompt.value = prompt
    }

    fun consumePendingLlm() {
        _pendingLlmImage.value = null
        _pendingLlmPrompt.value = null
    }

    fun extractAndQuiz() {
        _sidebarMode.value = StudySidebarMode.QUIZ
        _sidebarVisible.value = true
    }

    fun recordQuizRequested(
        conceptId: String,
        bloomLevel: Int
    ) {
        studyEvents.append(
            type = StudyEventType.QUIZ_REQUESTED,
            documentId = pdfId,
            pageIndex = _currentPage.value,
            conceptIds = listOf(conceptId),
            promptLength = _documentContent.value?.length,
            metadata = mapOf(
                "bloomLevel" to bloomLevel.toString()
            )
        )
    }

    fun recordQuizAnswered(
        conceptId: String,
        bloomLevel: Int,
        isCorrect: Boolean,
        responseTimeMs: Long?,
        question: String,
        choices: Map<String, String>,
        selectedAnswer: String,
        correctAnswer: String,
        explanation: String,
        sourceSentence: String
    ) {
        val hash = hashQuestion(question)
        quizResponses.append(
            QuizResponseRecord(
                conceptId = conceptId,
                bloomLevel = bloomLevel,
                isCorrect = isCorrect,
                responseTimeMs = responseTimeMs,
                questionHash = hash,
                sourceDocId = pdfId,
                question = question,
                choices = choices,
                selectedAnswer = selectedAnswer,
                correctAnswer = correctAnswer,
                explanation = explanation,
                sourceSentence = sourceSentence
            )
        )
        studyEvents.append(
            type = StudyEventType.QUIZ_ANSWERED,
            documentId = pdfId,
            pageIndex = _currentPage.value,
            conceptIds = listOf(conceptId),
            correctness = isCorrect,
            metadata = mapOf(
                "bloomLevel" to bloomLevel.toString(),
                "responseTimeMs" to (
                    responseTimeMs?.toString() ?: ""
                    ),
                "questionHash" to hash
            )
        )
        loadQuizMastery()
        loadQuizHistory()
    }

    fun deleteQuizResponse(recordId: String) {
        quizResponses.delete(recordId)
        loadQuizMastery()
        loadQuizHistory()
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

    private fun loadQuizMastery() {
        viewModelScope.launch(Dispatchers.IO) {
            val answered = studyEvents.listEvents()
                .filter {
                    it.documentId == pdfId &&
                        it.type == StudyEventType.QUIZ_ANSWERED &&
                        it.correctness != null
                }
            _quizMastery.value = if (answered.isEmpty()) {
                0.35f
            } else {
                answered.count { it.correctness == true }
                    .toFloat() / answered.size.toFloat()
            }.coerceIn(0f, 1f)
        }
    }

    private fun loadQuizHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _quizHistory.value = quizResponses.listResponses()
                .filter { it.sourceDocId == pdfId }
                .sortedByDescending { it.answeredAt }
        }
    }

    private fun hashQuestion(question: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(
            question.trim()
                .lowercase(Locale.US)
                .toByteArray()
        )
        return bytes.joinToString("") {
            "%02x".format(it)
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
