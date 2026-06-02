package com.maestro.app.ui.viewer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maestro.app.data.local.ExtractionProgressStore
import com.maestro.app.data.local.MonitoringLogCategory
import com.maestro.app.data.local.MonitoringLogLocalDataSource
import com.maestro.app.data.local.PdfTextIndex
import com.maestro.app.data.local.PdfTextIndexLocalDataSource
import com.maestro.app.data.local.QuestionRepresentationLocalDataSource
import com.maestro.app.data.local.QuestionRepresentationRecord
import com.maestro.app.data.local.QuizResponseLocalDataSource
import com.maestro.app.data.local.QuizResponseRecord
import com.maestro.app.data.local.StructuredContentSearchExtractor
import com.maestro.app.data.local.StudyEventLocalDataSource
import com.maestro.app.data.local.StudyEventType
import com.maestro.app.data.remote.MaterialAnalyzerClient
import com.maestro.app.data.remote.MaterialAnalyzerHash
import com.maestro.app.data.remote.QuestionEncoderClient
import com.maestro.app.data.repository.AnnotationRepositoryImpl
import com.maestro.app.domain.model.ConceptKnowledge
import com.maestro.app.domain.model.CropCapturePayload
import com.maestro.app.domain.model.GeneratedQuizQuestion
import com.maestro.app.domain.model.PdfSearchMatch
import com.maestro.app.domain.model.SelectedTextQuizPayload
import com.maestro.app.domain.repository.DocumentRepository
import com.maestro.app.domain.repository.KnowledgeRepository
import com.maestro.app.domain.repository.SettingsRepository
import com.maestro.app.domain.service.KnowledgeTracingEngine
import com.maestro.app.ui.components.StudySidebarMode
import com.maestro.app.ui.config.UxConfig
import com.maestro.app.ui.drawing.DrawingState
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val knowledgeRepository: KnowledgeRepository,
    private val studyEvents: StudyEventLocalDataSource,
    private val quizResponses: QuizResponseLocalDataSource,
    private val questionEncoderClient: QuestionEncoderClient,
    private val questionRepresentations: QuestionRepresentationLocalDataSource,
    private val knowledgeEngine: KnowledgeTracingEngine,
    private val monitoringLogs: MonitoringLogLocalDataSource,
    private val pdfTextIndex: PdfTextIndexLocalDataSource,
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

    private val _pendingQuizCrop =
        MutableStateFlow<CropCapturePayload?>(null)
    val pendingQuizCrop =
        _pendingQuizCrop.asStateFlow()

    private val _pendingQuizText =
        MutableStateFlow<SelectedTextQuizPayload?>(null)
    val pendingQuizText =
        _pendingQuizText.asStateFlow()

    private val _documentContent =
        MutableStateFlow<String?>(null)
    val documentContent =
        _documentContent.asStateFlow()

    private val _documentJsonContent =
        MutableStateFlow<String?>(null)
    val documentJsonContent =
        _documentJsonContent.asStateFlow()

    private val _documentTextIndex =
        MutableStateFlow<PdfTextIndex?>(null)
    val documentTextIndex =
        _documentTextIndex.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchMatches =
        MutableStateFlow<List<PdfSearchMatch>>(emptyList())
    val searchMatches = _searchMatches.asStateFlow()

    private val _quizMastery =
        MutableStateFlow(0.35f)
    val quizMastery = _quizMastery.asStateFlow()

    // Model mastery for the most recently answered concept, as (before, after).
    // Concept-specific so the gauge reflects the answered KC itself rather than
    // the document-level average (which can rise on a wrong answer merely
    // because a newly seen concept enters the averaged set).
    private val _quizConceptMastery =
        MutableStateFlow<Pair<Float, Float>?>(null)
    val quizConceptMastery = _quizConceptMastery.asStateFlow()

    // Current model mastery of the concept being quizzed (the QE concept ids of
    // the active question). Drives the header so it matches the gauge instead of
    // the document average. Null until a question has been encoded.
    private val _quizConceptCurrentMastery =
        MutableStateFlow<Float?>(null)
    val quizConceptCurrentMastery = _quizConceptCurrentMastery.asStateFlow()

    private val _quizHistory =
        MutableStateFlow<List<QuizResponseRecord>>(emptyList())
    val quizHistory = _quizHistory.asStateFlow()

    private val _quizEncodeStatus =
        MutableStateFlow<QuizEncodeStatus>(QuizEncodeStatus.Idle)
    val quizEncodeStatus = _quizEncodeStatus.asStateFlow()

    private val _weakConcepts =
        MutableStateFlow<List<ConceptKnowledge>>(emptyList())
    val weakConcepts = _weakConcepts.asStateFlow()

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
    private var searchJob: Job? = null

    init {
        recordDocumentOpened()
        loadAnnotations()
        loadDocumentContent()
        loadDocumentMeta()
        loadQuizMastery()
        loadQuizHistory()
        loadWeakConcepts()
    }

    private fun loadDocumentContent() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _documentContent.value =
                    loadContentMd(pdfId)
                _documentJsonContent.value =
                    loadContentJson(pdfId)
                _documentTextIndex.value =
                    loadOrBuildTextIndex()
                scheduleSearch()
            } catch (_: Throwable) {
            }
        }
    }

    private fun loadAnnotations() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                annotationRepo.loadAll(
                    pdfId,
                    drawingState
                )
            } catch (_: Throwable) {
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
        studyEvents.append(
            type = StudyEventType.ANNOTATION_SAVED,
            documentId = pdfId,
            pageIndex =
            drawingState.activePageIndex
                .coerceAtLeast(0)
        )
        monitoringLogs.append(
            category = MonitoringLogCategory.LEARNING_BEHAVIOR,
            eventType = "annotation_saved",
            documentId = pdfId,
            metadata =
            mapOf(
                "page_index" to
                    drawingState.activePageIndex
                        .coerceAtLeast(0)
                        .toString()
            )
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
        viewModelScope.launch {
            val doc =
                documentRepository.loadDocuments()
                    .find { it.id == pdfId }
            if (doc != null) {
                _bookmarkedPages.value = doc.bookmarkedPages
                _isPinned.value = doc.isPinned
            }
        }
    }

    fun togglePin() {
        viewModelScope.launch {
            val doc =
                documentRepository.loadDocuments()
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
            val updated =
                if (page in current) {
                    current - page
                } else {
                    current + page
                }
            _bookmarkedPages.value = updated
            val doc =
                documentRepository.loadDocuments()
                    .find { it.id == pdfId } ?: return@launch
            documentRepository.updateDocument(
                doc.copy(bookmarkedPages = updated)
            )
            studyEvents.append(
                type = StudyEventType.BOOKMARK_TOGGLED,
                documentId = pdfId,
                pageIndex = page,
                metadata =
                mapOf(
                    "bookmarked" to (page in updated).toString()
                )
            )
            monitoringLogs.append(
                category = MonitoringLogCategory.LEARNING_BEHAVIOR,
                eventType = "bookmark_toggled",
                documentId = pdfId,
                metadata =
                mapOf(
                    "page_index" to page.toString(),
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
        monitoringLogs.append(
            category = MonitoringLogCategory.LEARNING_BEHAVIOR,
            eventType = "page_viewed",
            documentId = pdfId,
            metadata =
            mapOf(
                "page_index" to page.toString()
            )
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

    fun setSearchQuery(query: String) {
        if (_searchQuery.value != query) {
            _searchMatches.value = emptyList()
        }
        _searchQuery.value = query
        scheduleSearch()
    }

    private fun scheduleSearch() {
        searchJob?.cancel()
        val query = _searchQuery.value
        val textIndex = _documentTextIndex.value
        val rawJson = _documentJsonContent.value
        if (query.isBlank()) {
            _searchMatches.value = emptyList()
            return
        }
        searchJob =
            viewModelScope.launch(Dispatchers.Default) {
                val matches =
                    if (textIndex?.hasText == true) {
                        pdfTextIndex.search(textIndex, query)
                    } else {
                        StructuredContentSearchExtractor.search(
                            rawJson = rawJson,
                            query = query
                        )
                    }
                withContext(Dispatchers.Main) {
                    if (_searchQuery.value == query &&
                        _documentTextIndex.value == textIndex &&
                        _documentJsonContent.value == rawJson
                    ) {
                        _searchMatches.value = matches
                    }
                }
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

    fun sendSelectionToQuiz(payload: CropCapturePayload) {
        _sidebarVisible.value = true
        _sidebarMode.value = StudySidebarMode.QUIZ
        _pendingQuizCrop.value = payload
    }

    fun sendTextSelectionToQuiz(payload: SelectedTextQuizPayload) {
        _sidebarVisible.value = true
        _sidebarMode.value = StudySidebarMode.QUIZ
        _pendingQuizText.value = payload
    }

    fun consumePendingLlm() {
        _pendingLlmImage.value = null
        _pendingLlmPrompt.value = null
    }

    fun consumePendingQuizCrop() {
        _pendingQuizCrop.value = null
    }

    fun consumePendingQuizText() {
        _pendingQuizText.value = null
    }

    fun extractAndQuiz() {
        if (
            _sidebarVisible.value &&
            _sidebarMode.value == StudySidebarMode.QUIZ
        ) {
            _sidebarVisible.value = false
        } else {
            _sidebarMode.value = StudySidebarMode.QUIZ
            _sidebarVisible.value = true
        }
    }

    fun recordQuizRequested(conceptId: String, bloomLevel: Int) {
        studyEvents.append(
            type = StudyEventType.QUIZ_REQUESTED,
            documentId = pdfId,
            pageIndex = _currentPage.value,
            conceptIds = listOf(conceptId),
            promptLength = _documentContent.value?.length,
            metadata =
            mapOf(
                "bloomLevel" to bloomLevel.toString(),
                "question_id" to conceptId,
                "concept_id" to conceptId
            )
        )
        monitoringLogs.append(
            category = MonitoringLogCategory.LEARNING_BEHAVIOR,
            eventType = "quiz_generated",
            documentId = pdfId,
            conceptId = conceptId,
            metadata =
            mapOf(
                "bloom_level" to bloomLevel.toString(),
                "mastery_before" to _quizMastery.value.toString(),
                "content_length" to
                    (
                        _documentContent.value?.length ?: 0
                        ).toString()
            )
        )
    }

    /**
     * Sends a freshly generated quiz to the MobileKT Question Encoder server
     * in the format the repo specifies, then stores the returned question
     * representation (embedding + difficulty + concepts) on the tablet.
     *
     * Best-effort: failures are surfaced via [quizEncodeStatus] but never
     * block quiz taking.
     */
    fun encodeAndStoreQuiz(quiz: GeneratedQuizQuestion) {
        if (quiz.question.isBlank() || quiz.choices.isEmpty()) return
        val localHash = hashQuestion(quiz.question)
        _quizEncodeStatus.value = QuizEncodeStatus.Encoding
        viewModelScope.launch {
            try {
                val request =
                    QuestionEncoderClient.requestFromQuiz(
                        quiz = quiz,
                        clientQuestionId = localHash
                    )
                val representation =
                    withContext(Dispatchers.IO) {
                        questionEncoderClient.encode(request)
                    }
                val record =
                    QuestionRepresentationRecord.from(
                        representation = representation,
                        sourceDocId = pdfId,
                        conceptId = quiz.targetConcept,
                        localQuestionHash = localHash,
                        question = quiz.question,
                        bloomLevel = quiz.bloomLevel
                    )
                withContext(Dispatchers.IO) {
                    questionRepresentations.upsert(record)
                }
                // Seed the header with this question's concept mastery so it
                // matches the gauge (per-concept) rather than the document avg.
                val conceptIds = record.conceptIds.toSet()
                if (conceptIds.isNotEmpty()) {
                    _quizConceptCurrentMastery.value =
                        withContext(Dispatchers.Default) {
                            conceptMasteryFor(conceptIds)
                        }
                }
                monitoringLogs.append(
                    category = MonitoringLogCategory.LEARNING_BEHAVIOR,
                    eventType = "quiz_question_encoded",
                    documentId = pdfId,
                    conceptId = quiz.targetConcept,
                    metadata =
                    mapOf(
                        "representation_id" to representation.representationId,
                        "question_hash" to representation.questionHash,
                        "qe_model_version" to representation.qeModelVersion,
                        "concept_ids" to
                            representation.conceptIds
                                .joinToString(","),
                        "difficulty" to representation.difficulty.toString(),
                        "embedding_dim" to representation.embeddingDim.toString()
                    )
                )
                _quizEncodeStatus.value =
                    QuizEncodeStatus.Success(
                        representationId = representation.representationId,
                        conceptKeys = representation.conceptKeys
                    )
            } catch (e: Throwable) {
                monitoringLogs.append(
                    category = MonitoringLogCategory.KT_RUNTIME,
                    eventType = "quiz_question_encode_failed",
                    documentId = pdfId,
                    conceptId = quiz.targetConcept,
                    metadata =
                    mapOf(
                        "error" to (e.message ?: e::class.java.name)
                    )
                )
                _quizEncodeStatus.value =
                    QuizEncodeStatus.Error(
                        e.message ?: "QE 서버 전송에 실패했습니다."
                    )
            }
        }
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
        choiceExplanations: Map<String, String>,
        sourceSentence: String
    ) {
        val hash = hashQuestion(question)
        val masteryBefore = _quizMastery.value
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
                choiceExplanations = choiceExplanations,
                sourceSentence = sourceSentence
            )
        )
        studyEvents.append(
            type = StudyEventType.QUIZ_ANSWERED,
            documentId = pdfId,
            pageIndex = _currentPage.value,
            conceptIds = listOf(conceptId),
            correctness = isCorrect,
            metadata =
            mapOf(
                "bloomLevel" to bloomLevel.toString(),
                "question_id" to conceptId,
                "concept_id" to conceptId,
                "responseTimeMs" to (
                    responseTimeMs?.toString() ?: ""
                    ),
                "questionHash" to hash
            )
        )
        loadQuizHistory()
        updateKnowledgeState(
            question = question,
            isCorrect = isCorrect
        )
        val conceptRecords =
            quizResponses.listResponses()
                .filter {
                    it.sourceDocId == pdfId &&
                        it.conceptId == conceptId
                }
        val masteryAfter =
            if (conceptRecords.isEmpty()) {
                0.35f
            } else {
                conceptRecords.count { it.isCorrect }.toFloat() /
                    conceptRecords.size.toFloat()
            }.coerceIn(0f, 1f)
        monitoringLogs.append(
            category = MonitoringLogCategory.LEARNING_BEHAVIOR,
            eventType = "quiz_answered",
            documentId = pdfId,
            conceptId = conceptId,
            metadata =
            mapOf(
                "bloom_level" to bloomLevel.toString(),
                "is_correct" to isCorrect.toString(),
                "response_time_ms" to (
                    responseTimeMs?.toString() ?: ""
                    ),
                "question_hash" to hash,
                "mastery_before" to masteryBefore.toString(),
                "mastery_after" to masteryAfter.toString()
            )
        )
        // The concept-specific KT evaluation (predicted vs observed) is logged in
        // updateKnowledgeState, where the per-KC before/after TAP mastery is known.
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
            metadata =
            mapOf(
                "hasImage" to hasImage.toString()
            )
        )
        monitoringLogs.append(
            category = MonitoringLogCategory.LEARNING_BEHAVIOR,
            eventType = "llm_requested",
            documentId = pdfId,
            metadata =
            mapOf(
                "page_index" to _currentPage.value.toString(),
                "prompt_length" to prompt.length.toString(),
                "has_image" to hasImage.toString()
            )
        )
    }

    private fun recordDocumentOpened() {
        studyEvents.append(
            type = StudyEventType.DOCUMENT_OPENED,
            documentId = pdfId
        )
        monitoringLogs.append(
            category = MonitoringLogCategory.LEARNING_BEHAVIOR,
            eventType = "document_opened",
            documentId = pdfId,
            metadata =
            mapOf(
                "page_count" to pageCount.toString()
            )
        )
    }

    /**
     * Runs the bundled MIKT+TAP engine after a known answer and refreshes the
     * mastery-driven UI. Knowledge state is derived purely from the model: the
     * stored QE representation (embedding/difficulty/concept_ids) drives the
     * MIKT update, and mastery is read back from the TAP head.
     */
    private fun updateKnowledgeState(question: String, isCorrect: Boolean) {
        viewModelScope.launch(Dispatchers.Default) {
            val hash = hashQuestion(question)
            val rep =
                questionRepresentations.listRecords()
                    .firstOrNull { it.localQuestionHash == hash }
            val usable =
                rep != null &&
                    rep.questionEmbedding.isNotEmpty() &&
                    rep.conceptIds.isNotEmpty() &&
                    knowledgeEngine.isReady()
            if (usable) {
                val targetIds = rep!!.conceptIds.toSet()
                // Read the answered concept's mastery before and after applying
                // the answer, so the gauge can show the real per-KC change.
                val before = conceptMasteryFor(targetIds)
                runCatching {
                    knowledgeEngine.recordAnswer(
                        questionEmbedding = rep.questionEmbedding.toFloatArray(),
                        difficulty = rep.difficulty,
                        conceptIds = rep.conceptIds,
                        correct = isCorrect
                    )
                }
                val after = conceptMasteryFor(targetIds)
                _quizConceptMastery.value =
                    if (before != null && after != null) before to after else null
                // Keep the header in sync with the post-answer concept mastery.
                if (after != null) _quizConceptCurrentMastery.value = after
                // Experiment evaluation: the pre-answer TAP mastery is the model's
                // predicted correctness probability; compare it to what happened.
                if (before != null && after != null) {
                    monitoringLogs.append(
                        category = MonitoringLogCategory.DOMAIN_EVALUATION,
                        eventType = "kt_mastery_update",
                        documentId = pdfId,
                        conceptId = rep.conceptId,
                        metadata =
                        mapOf(
                            "concept_ids" to rep.conceptIds.joinToString(","),
                            "bloom_level" to rep.bloomLevel.toString(),
                            "correct" to isCorrect.toString(),
                            "mastery_before" to before.toString(),
                            "mastery_after" to after.toString(),
                            "mastery_delta" to (after - before).toString(),
                            "predicted_correct_prob" to before.toString(),
                            "prediction_error" to
                                abs(before - if (isCorrect) 1f else 0f).toString()
                        )
                    )
                }
            } else {
                _quizConceptMastery.value = null
            }
            loadQuizMastery()
            loadWeakConcepts()
        }
    }

    /** Average TAP mastery across the given concept ids, or null if unavailable. */
    private fun conceptMasteryFor(ids: Set<Int>): Float? {
        if (ids.isEmpty()) return null
        return knowledgeEngine.masteryByConceptKey().values
            .filter { it.conceptId in ids }
            .map { it.mastery }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.toFloat()
    }

    private fun loadQuizMastery() {
        viewModelScope.launch(Dispatchers.IO) {
            // Mastery comes from the TAP knowledge-tracing engine via the
            // dashboard, not from raw answer-correctness ratios.
            val modelMastery =
                runCatching {
                    knowledgeRepository.loadDashboard()
                        .concepts
                        .filter { pdfId in it.documentIds && it.confidence > 0f }
                        .map { it.mastery }
                        .takeIf { it.isNotEmpty() }
                        ?.average()
                        ?.toFloat()
                }.getOrNull()
            _quizMastery.value = (modelMastery ?: 0.35f).coerceIn(0f, 1f)
        }
    }

    private fun loadQuizHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _quizHistory.value =
                quizResponses.listResponses()
                    .filter { it.sourceDocId == pdfId }
                    .sortedByDescending { it.answeredAt }
        }
    }

    private fun loadWeakConcepts() {
        viewModelScope.launch(Dispatchers.IO) {
            _weakConcepts.value =
                runCatching {
                    knowledgeRepository.loadDashboard()
                        .concepts
                        .filter { pdfId in it.documentIds }
                        .sortedBy { it.mastery }
                        .take(3)
                }.getOrDefault(emptyList())
        }
    }

    private fun hashQuestion(question: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes =
            digest.digest(
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
        val hash =
            MaterialAnalyzerHash.compute(
                appContext,
                uri,
                mode
            )
        val task =
            analyzerClient.upload(
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
            val dir =
                File(
                    appContext.filesDir,
                    "documents/$documentId"
                )
            dir.mkdirs()
            File(dir, "content.md").writeText(text)
        }

    private suspend fun loadContentMd(documentId: String): String? = withContext(Dispatchers.IO) {
        val file =
            File(
                appContext.filesDir,
                "documents/$documentId/content.md"
            )
        val localFile =
            File(
                appContext.filesDir,
                "documents/$documentId/local_mlkit_content.md"
            )
        when {
            file.exists() -> file.readText()
            localFile.exists() -> localFile.readText()
            else -> null
        }
    }

    private suspend fun loadContentJson(documentId: String): String? = withContext(Dispatchers.IO) {
        val file =
            File(
                appContext.filesDir,
                "documents/$documentId/content.json"
            )
        val localFile =
            File(
                appContext.filesDir,
                "documents/$documentId/local_mlkit_content.json"
            )
        when {
            file.exists() -> file.readText()
            localFile.exists() -> localFile.readText()
            else -> null
        }
    }

    private suspend fun loadOrBuildTextIndex(): PdfTextIndex? = withContext(Dispatchers.IO) {
        val existing = pdfTextIndex.loadIndex(pdfId)
        if (existing != null) return@withContext existing
        val doc =
            documentRepository.loadDocuments()
                .find { it.id == pdfId }
                ?: return@withContext null
        val path =
            Uri.parse(doc.uriString).path
                ?: return@withContext null
        pdfTextIndex.ensureIndex(
            documentId = doc.id,
            pdfFile = File(path),
            displayName = doc.displayName
        )
    }
}

/** Status of sending a generated quiz to the Question Encoder server. */
sealed interface QuizEncodeStatus {
    data object Idle : QuizEncodeStatus

    data object Encoding : QuizEncodeStatus

    data class Success(
        val representationId: String,
        val conceptKeys: List<String>
    ) : QuizEncodeStatus

    data class Error(val message: String) : QuizEncodeStatus
}
