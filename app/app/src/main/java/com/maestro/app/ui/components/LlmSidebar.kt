package com.maestro.app.ui.components

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maestro.app.data.local.ConversationLocalDataSource
import com.maestro.app.data.local.ConversationSummary
import com.maestro.app.data.local.QuizResponseRecord
import com.maestro.app.data.model.LlmRequestBuilder
import com.maestro.app.data.remote.ClaudeClient
import com.maestro.app.data.remote.LlmClient
import com.maestro.app.data.remote.OpenAiClient
import com.maestro.app.domain.model.ChatMessage
import com.maestro.app.domain.model.EngineeringMechanicsConceptCatalog
import com.maestro.app.domain.model.GeneratedQuizQuestion
import com.maestro.app.domain.model.QuizGenerationRequest
import com.maestro.app.domain.model.LlmProvider
import com.maestro.app.domain.repository.SettingsRepository
import com.maestro.app.domain.service.LlmService
import com.maestro.app.domain.service.QuizService
import com.maestro.app.ui.config.UxConfig
import com.maestro.app.ui.viewer.LlmConnectionState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private fun buildSystemPrompt(documentContent: String?): String {
    val base = "You are a helpful AI assistant " +
        "integrated into Maestro, a PDF " +
        "annotation app. Help the user " +
        "understand and work with their documents. " +
        "Always respond in Korean."
    if (documentContent.isNullOrBlank()) return base
    return "$base\n\n" +
        "The user is currently viewing a document. " +
        "Here is the extracted text content:\n\n" +
        documentContent.take(5000)
}

private data class QueuedLlmRequest(
    val text: String,
    val images: List<ByteArray>
)

enum class StudySidebarMode {
    CHAT,
    QUIZ
}

private enum class HistoryPane {
    CHAT,
    QUIZ
}

@Composable
fun LlmSidebar(
    isVisible: Boolean,
    onCollapse: () -> Unit,
    llmService: LlmService,
    quizService: QuizService,
    settingsRepository: SettingsRepository,
    conversationDataSource: ConversationLocalDataSource,
    documentContent: String? = null,
    documentId: String,
    pageIndex: Int = 0,
    quizMastery: Float = 0.35f,
    quizHistory: List<QuizResponseRecord> = emptyList(),
    sidebarMode: StudySidebarMode = StudySidebarMode.CHAT,
    onSidebarModeChanged: (StudySidebarMode) -> Unit = {},
    pendingImage: ByteArray? = null,
    pendingPrompt: String? = null,
    llmConnectionState: LlmConnectionState = LlmConnectionState.READY,
    llmConnectionError: String? = null,
    onRetryConnection: () -> Unit = {},
    onLlmRequested: (prompt: String, hasImage: Boolean) -> Unit = { _, _ -> },
    onQuizRequested: (conceptId: String, bloomLevel: Int) -> Unit = { _, _ -> },
    onQuizAnswered: (
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
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _ -> },
    onQuizHistoryDeleted: (String) -> Unit = {},
    onPendingConsumed: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val minWidthPx = with(density) {
        UxConfig.Viewer.SIDEBAR_MIN_WIDTH.toPx()
    }
    val maxWidthPx = with(density) {
        UxConfig.Viewer.SIDEBAR_MAX_WIDTH.toPx()
    }
    val defaultWidthPx = with(density) {
        UxConfig.Viewer.SIDEBAR_DEFAULT_WIDTH.toPx()
    }

    val savedProviderName by settingsRepository
        .getLlmProvider()
        .collectAsState(initial = null)
    val currentProvider = savedProviderName
        ?: LlmProvider.GEMINI.name
    val geminiKey by settingsRepository
        .getGeminiApiKey()
        .collectAsState(initial = null)
    val openAiKey by settingsRepository
        .getOpenAiApiKey()
        .collectAsState(initial = null)
    val claudeKey by settingsRepository
        .getClaudeApiKey()
        .collectAsState(initial = null)
    val hasApiKey = when (currentProvider) {
        LlmProvider.OPENAI.name ->
            !openAiKey.isNullOrBlank()
        LlmProvider.CLAUDE.name ->
            !claudeKey.isNullOrBlank()
        else -> !geminiKey.isNullOrBlank()
    }
    val savedModel by settingsRepository
        .getLlmModel()
        .collectAsState(initial = null)
    var availableModels by remember {
        mutableStateOf<List<String>>(emptyList())
    }
    var modelsLoading by remember {
        mutableStateOf(false)
    }

    fun defaultModelsFor(provider: String): List<String> {
        return when (provider) {
            LlmProvider.OPENAI.name ->
                listOf(OpenAiClient.DEFAULT_MODEL)
            LlmProvider.CLAUDE.name ->
                listOf(ClaudeClient.DEFAULT_MODEL)
            else -> listOf(
                LlmRequestBuilder.DEFAULT_MODEL
            )
        }
    }

    var widthPx by remember {
        mutableStateOf(defaultWidthPx)
    }
    val messages = remember {
        mutableStateListOf<ChatMessage>()
    }
    var currentInput by remember {
        mutableStateOf("")
    }
    var isLoading by remember {
        mutableStateOf(false)
    }
    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }
    var activeJob by remember {
        mutableStateOf<kotlinx.coroutines.Job?>(null)
    }
    var queuedRequest by remember {
        mutableStateOf<QueuedLlmRequest?>(null)
    }

    LaunchedEffect(currentProvider) {
        availableModels = defaultModelsFor(currentProvider)
        modelsLoading = false
    }

    fun loadModelsIfNeeded() {
        if (!hasApiKey || modelsLoading) return
        scope.launch {
            modelsLoading = true
            try {
                val fetched = llmService.fetchModels()
                if (fetched.isNotEmpty()) {
                    availableModels = fetched
                }
            } catch (e: Exception) {
                errorMessage = "모델 목록 실패: " +
                    "${e.message}"
            } finally {
                modelsLoading = false
            }
        }
    }

    var conversationId by remember {
        mutableStateOf<String?>(null)
    }
    var showHistory by remember {
        mutableStateOf(false)
    }
    var historyPane by remember {
        mutableStateOf(HistoryPane.CHAT)
    }
    var historyList by remember {
        mutableStateOf<List<ConversationSummary>>(
            emptyList()
        )
    }
    val listState = rememberLazyListState()

    // Track pending images for next send
    var pendingImages by remember {
        mutableStateOf<List<ByteArray>>(emptyList())
    }

    val quizContent = documentContent.orEmpty()
    val quizConceptName = remember(quizContent) {
        extractQuizConcept(quizContent)
    }
    val quizConceptId = remember(documentId, quizConceptName) {
        stableConceptId(documentId, quizConceptName)
    }
    var selectedBloomLevel by remember(
        documentId,
        quizConceptName,
        quizMastery
    ) {
        mutableStateOf(
            quizService.defaultBloomLevel(quizMastery)
        )
    }
    var currentQuiz by remember(documentId) {
        mutableStateOf<GeneratedQuizQuestion?>(null)
    }
    var selectedQuizChoice by remember(documentId) {
        mutableStateOf<String?>(null)
    }
    var quizAnswered by remember(documentId) {
        mutableStateOf(false)
    }
    var quizLoading by remember(documentId) {
        mutableStateOf(false)
    }
    var quizError by remember(documentId) {
        mutableStateOf<String?>(null)
    }
    var quizStartedAt by remember(documentId) {
        mutableStateOf<Long?>(null)
    }

    fun generateQuizQuestion() {
        if (quizContent.isBlank() ||
            quizLoading ||
            llmConnectionState != LlmConnectionState.READY ||
            !hasApiKey
        ) {
            return
        }
        quizLoading = true
        quizError = null
        selectedQuizChoice = null
        quizAnswered = false
        currentQuiz = null
        onQuizRequested(quizConceptId, selectedBloomLevel)
        scope.launch {
            try {
                currentQuiz = quizService.generateQuestion(
                    QuizGenerationRequest(
                        documentContent = quizContent,
                        conceptName = quizConceptName,
                        mastery = quizMastery,
                        bloomLevel = selectedBloomLevel
                    )
                )
                quizStartedAt = System.currentTimeMillis()
            } catch (e: Exception) {
                quizError = mapQuizError(e)
            } finally {
                quizLoading = false
            }
        }
    }

    LaunchedEffect(conversationId) {
        val id =
            conversationId ?: return@LaunchedEffect
        val loaded =
            conversationDataSource.loadMessages(id)
        messages.clear()
        messages.addAll(loaded)
    }

    val lastMsgContent =
        messages.lastOrNull()?.content?.length ?: 0
    LaunchedEffect(messages.size, lastMsgContent) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(
                messages.size - 1
            )
        }
    }

    // Consume pending crop/quiz prompt
    fun sendMessage(text: String, imgs: List<ByteArray> = emptyList()) {
        activeJob?.cancel()
        isLoading = false
        errorMessage = null
        onLlmRequested(text, imgs.isNotEmpty())
        val userMsg = ChatMessage(
            role = ChatMessage.Role.USER,
            content = text
        )
        messages.add(userMsg)
        activeJob = scope.launch {
            val convId = conversationId
                ?: conversationDataSource
                    .create()
                    .also { conversationId = it }
            conversationDataSource
                .appendMessage(convId, userMsg)
            streamAssistantResponse(
                messages,
                llmService,
                conversationDataSource,
                convId,
                imgs,
                documentContent,
                { errorMessage = it },
                { isLoading = it }
            )
        }
    }

    fun submitMessage(text: String, imgs: List<ByteArray> = emptyList()) {
        when (llmConnectionState) {
            LlmConnectionState.READY -> {
                sendMessage(text, imgs)
            }
            LlmConnectionState.CONNECTING,
            LlmConnectionState.FAILED -> {
                queuedRequest = QueuedLlmRequest(text, imgs)
                errorMessage = null
                if (llmConnectionState ==
                    LlmConnectionState.FAILED
                ) {
                    onRetryConnection()
                }
            }
        }
    }

    LaunchedEffect(llmConnectionState, queuedRequest) {
        val request = queuedRequest
        if (
            llmConnectionState == LlmConnectionState.READY &&
            request != null
        ) {
            queuedRequest = null
            sendMessage(request.text, request.images)
        }
    }

    var lastProcessedPrompt by remember {
        mutableStateOf<String?>(null)
    }
    if (pendingPrompt != null &&
        pendingPrompt != lastProcessedPrompt
    ) {
        lastProcessedPrompt = pendingPrompt
        val imgs = if (pendingImage != null) {
            listOf(pendingImage)
        } else {
            emptyList()
        }
        val text = pendingPrompt
            .replace(
                Regex("\\n<!--\\d+-->$"),
                ""
            )
        onPendingConsumed()
        submitMessage(text, imgs)
    }

    if (!isVisible) return

    val widthDp = with(density) { widthPx.toDp() }

    Row(
        modifier = Modifier
            .width(widthDp)
            .fillMaxHeight()
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(
                    MaterialTheme.colorScheme
                        .outlineVariant
                )
                .pointerInput(Unit) {
                    detectHorizontalDragGestures {
                            _,
                            dx
                        ->
                        widthPx = (widthPx - dx)
                            .coerceIn(
                                minWidthPx,
                                maxWidthPx
                            )
                    }
                }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.surface
                )
        ) {
            SidebarTopBar(
                currentProvider = currentProvider,
                onProviderSelected = { provider ->
                    activeJob?.cancel()
                    activeJob = null
                    conversationId = null
                    messages.clear()
                    errorMessage = null
                    isLoading = false
                    scope.launch {
                        settingsRepository
                            .setLlmProvider(provider)
                        settingsRepository
                            .setLlmModel("")
                    }
                },
                currentModel = savedModel?.ifBlank {
                    null
                } ?: when (currentProvider) {
                    LlmProvider.OPENAI.name ->
                        OpenAiClient.DEFAULT_MODEL
                    LlmProvider.CLAUDE.name ->
                        ClaudeClient.DEFAULT_MODEL
                    else ->
                        LlmRequestBuilder.DEFAULT_MODEL
                },
                availableModels = availableModels,
                modelsLoading = modelsLoading,
                onModelMenuOpened = {
                    loadModelsIfNeeded()
                },
                onModelSelected = { model ->
                    activeJob?.cancel()
                    activeJob = null
                    conversationId = null
                    messages.clear()
                    errorMessage = null
                    isLoading = false
                    scope.launch {
                        settingsRepository
                            .setLlmModel(model)
                    }
                },
                onCollapse = onCollapse,
                onNewConversation = {
                    onSidebarModeChanged(StudySidebarMode.CHAT)
                    activeJob?.cancel()
                    activeJob = null
                    conversationId = null
                    messages.clear()
                    errorMessage = null
                    currentInput = ""
                    pendingImages = emptyList()
                    isLoading = false
                    showHistory = false
                },
                onToggleHistory = {
                    if (showHistory) {
                        onSidebarModeChanged(
                            when (historyPane) {
                                HistoryPane.CHAT -> StudySidebarMode.CHAT
                                HistoryPane.QUIZ -> StudySidebarMode.QUIZ
                            }
                        )
                        showHistory = false
                    } else {
                        historyPane =
                            if (sidebarMode == StudySidebarMode.QUIZ) {
                                HistoryPane.QUIZ
                            } else {
                                HistoryPane.CHAT
                            }
                        historyList =
                            conversationDataSource
                                .listConversations()
                        showHistory = true
                    }
                },
                showHistory = showHistory
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme
                    .outlineVariant
            )

            StudySidebarTabs(
                mode = sidebarMode,
                showHistory = showHistory,
                historyPane = historyPane,
                onModeChanged = { mode ->
                    showHistory = false
                    onSidebarModeChanged(mode)
                },
                onHistoryPaneChanged = {
                    historyPane = it
                }
            )

            if (showHistory) {
                UnifiedHistoryPanel(
                    pane = historyPane,
                    onPaneChanged = {
                        historyPane = it
                    },
                    conversations = historyList,
                    currentConversationId = conversationId,
                    quizHistory = quizHistory,
                    onSelectConversation = { summary ->
                        activeJob?.cancel()
                        activeJob = null
                        conversationId = summary.id
                        isLoading = false
                        errorMessage = null
                        showHistory = false
                        onSidebarModeChanged(StudySidebarMode.CHAT)
                    },
                    onDeleteConversation = { summary ->
                        conversationDataSource
                            .deleteConversation(summary.id)
                        historyList = historyList
                            .filter { it.id != summary.id }
                        if (conversationId == summary.id) {
                            conversationId = null
                            messages.clear()
                        }
                    },
                    onDeleteQuizRecord = { record ->
                        onQuizHistoryDeleted(record.id)
                    },
                    modifier = Modifier.weight(1f)
                )
            } else if (sidebarMode == StudySidebarMode.QUIZ) {
                QuizPanel(
                    quizService = quizService,
                    documentContent = quizContent,
                    pageIndex = pageIndex,
                    conceptName = quizConceptName,
                    conceptId = quizConceptId,
                    mastery = quizMastery,
                    selectedBloomLevel = selectedBloomLevel,
                    onBloomLevelSelected = {
                        selectedBloomLevel = it
                    },
                    quiz = currentQuiz,
                    selectedChoice = selectedQuizChoice,
                    answered = quizAnswered,
                    loading = quizLoading,
                    error = quizError,
                    connectionState = llmConnectionState,
                    connectionError = llmConnectionError,
                    hasApiKey = hasApiKey,
                    onRetryConnection = onRetryConnection,
                    onGenerateQuestion = {
                        generateQuizQuestion()
                    },
                    onAnswerSelected = { choice, current ->
                        if (!quizAnswered) {
                            selectedQuizChoice = choice
                            quizAnswered = true
                            val correct = choice == current.answer
                            val elapsed = quizStartedAt?.let {
                                System.currentTimeMillis() - it
                            }
                            onQuizAnswered(
                                quizConceptId,
                                current.bloomLevel,
                                correct,
                                elapsed,
                                current.question,
                                current.choices,
                                choice,
                                current.answer,
                                current.explanation,
                                current.sourceSentence
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            } else if (!hasApiKey) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment =
                        Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp),
                            tint = MaterialTheme
                                .colorScheme
                                .outline
                        )
                        Spacer(Modifier.height(16.dp))
                        val providerName =
                            when (currentProvider) {
                                LlmProvider.OPENAI
                                    .name -> "OpenAI"
                                LlmProvider.CLAUDE
                                    .name -> "Claude"
                                else -> "Gemini"
                            }
                        Text(
                            "$providerName API 키가 " +
                                "설정되지 않았습니다",
                            fontSize = 15.sp,
                            fontWeight = FontWeight
                                .SemiBold,
                            color = MaterialTheme
                                .colorScheme
                                .onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "설정 화면에서 $providerName" +
                                " API 키를 입력해주세요.",
                            fontSize = 13.sp,
                            color = MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                        )
                    }
                }
            } else {
                if (
                    llmConnectionState != LlmConnectionState.READY ||
                    queuedRequest != null
                ) {
                    LlmConnectionStatusBanner(
                        connectionState = llmConnectionState,
                        errorMessage = llmConnectionError,
                        hasQueuedRequest = queuedRequest != null,
                        onRetryConnection = onRetryConnection
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement
                        .spacedBy(8.dp)
                ) {
                    item {
                        Spacer(Modifier.height(8.dp))
                    }
                    items(messages) { msg ->
                        MessageBubble(message = msg)
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme
                            .colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 4.dp
                        )
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme
                    .outlineVariant
            )

            if (!showHistory && sidebarMode == StudySidebarMode.CHAT) {
                SidebarInput(
                    value = currentInput,
                    onValueChange = {
                        currentInput = it
                    },
                    isLoading = isLoading,
                    pendingImages = pendingImages,
                    onAddImage = { bytes ->
                        pendingImages =
                            pendingImages + bytes
                    },
                    onRemoveImage = { index ->
                        pendingImages = pendingImages
                            .toMutableList()
                            .apply { removeAt(index) }
                    },
                    onSend = {
                        val text = currentInput.trim()
                        if (text.isBlank() &&
                            pendingImages.isEmpty()
                        ) {
                            return@SidebarInput
                        }
                        val msgText = text.ifBlank {
                            "이 이미지를 분석해줘"
                        }
                        val imgs = pendingImages
                        pendingImages = emptyList()
                        currentInput = ""
                        submitMessage(msgText, imgs)
                    }
                )
            }
        }
    }
}

@Composable
private fun StudySidebarTabs(
    mode: StudySidebarMode,
    showHistory: Boolean,
    historyPane: HistoryPane,
    onModeChanged: (StudySidebarMode) -> Unit,
    onHistoryPaneChanged: (HistoryPane) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SidebarTab(
            label = if (showHistory) {
                "Chat History"
            } else {
                "Chat"
            },
            selected = if (showHistory) {
                historyPane == HistoryPane.CHAT
            } else {
                mode == StudySidebarMode.CHAT
            },
            icon = {
                Icon(
                    Icons.Default.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            onClick = {
                if (showHistory) {
                    onHistoryPaneChanged(HistoryPane.CHAT)
                } else {
                    onModeChanged(StudySidebarMode.CHAT)
                }
            },
            modifier = Modifier.weight(1f)
        )
        SidebarTab(
            label = if (showHistory) {
                "Quiz History"
            } else {
                "Quiz"
            },
            selected = if (showHistory) {
                historyPane == HistoryPane.QUIZ
            } else {
                mode == StudySidebarMode.QUIZ
            },
            icon = {
                Icon(
                    Icons.Default.Quiz,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            onClick = {
                if (showHistory) {
                    onHistoryPaneChanged(HistoryPane.QUIZ)
                } else {
                    onModeChanged(StudySidebarMode.QUIZ)
                }
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SidebarTab(
    label: String,
    selected: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides fg
        ) {
            icon()
        }
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = fg
        )
    }
}

@Composable
private fun QuizPanel(
    quizService: QuizService,
    documentContent: String?,
    pageIndex: Int,
    conceptName: String,
    conceptId: String,
    mastery: Float,
    selectedBloomLevel: Int,
    onBloomLevelSelected: (Int) -> Unit,
    quiz: GeneratedQuizQuestion?,
    selectedChoice: String?,
    answered: Boolean,
    loading: Boolean,
    error: String?,
    connectionState: LlmConnectionState,
    connectionError: String?,
    hasApiKey: Boolean,
    onRetryConnection: () -> Unit,
    onGenerateQuestion: () -> Unit,
    onAnswerSelected: (String, GeneratedQuizQuestion) -> Unit,
    modifier: Modifier = Modifier
) {
    val content = documentContent.orEmpty()

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(6.dp))
            QuizHeader(
                conceptName = conceptName,
                conceptId = conceptId,
                mastery = mastery,
                pageIndex = pageIndex,
                selectedBloomLevel = selectedBloomLevel,
                onBloomLevelSelected = {
                    onBloomLevelSelected(it)
                },
                quizService = quizService
            )
        }

        if (!hasApiKey) {
            item {
                QuizMessage(
                    title = "API 키가 필요합니다",
                    body = "설정 화면에서 현재 LLM provider의 API 키를 입력하면 퀴즈를 생성할 수 있습니다."
                )
            }
        } else if (connectionState != LlmConnectionState.READY) {
            item {
                LlmConnectionStatusBanner(
                    connectionState = connectionState,
                    errorMessage = connectionError,
                    hasQueuedRequest = false,
                    onRetryConnection = onRetryConnection
                )
            }
        } else if (content.isBlank()) {
            item {
                QuizMessage(
                    title = "학습 자료가 아직 없습니다",
                    body = "이 PDF의 context.md 추출이 완료되면 문서 내용 기반 퀴즈를 만들 수 있습니다."
                )
            }
        }

        if (loading) {
            item {
                QuizLoadingCard()
            }
        }

        error?.let { message ->
            item {
                QuizMessage(
                    title = "퀴즈 생성 실패",
                    body = message,
                    isError = true
                )
            }
        }

        quiz?.let { currentQuiz ->
            item {
                QuizQuestionCard(
                    quiz = currentQuiz,
                    selectedChoice = selectedChoice,
                    answered = answered,
                    onSelect = { choice ->
                        onAnswerSelected(choice, currentQuiz)
                    }
                )
            }
        }

        item {
            QuizActionButton(
                loading = loading,
                enabled = content.isNotBlank() &&
                    hasApiKey &&
                    connectionState == LlmConnectionState.READY,
                hasQuestion = quiz != null,
                onClick = {
                    onGenerateQuestion()
                }
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun QuizHeader(
    conceptName: String,
    conceptId: String,
    mastery: Float,
    pageIndex: Int,
    selectedBloomLevel: Int,
    onBloomLevelSelected: (Int) -> Unit,
    quizService: QuizService
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Text(
            "Bloom Quiz",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "개념: $conceptName · 페이지 ${pageIndex + 1} · mastery ${((mastery * 100).toInt())}%",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(quizService.bloomLevels()) { bloom ->
                BloomChip(
                    level = bloom.level,
                    selected = bloom.level == selectedBloomLevel,
                    onClick = {
                        onBloomLevelSelected(bloom.level)
                    }
                )
            }
        }
    }
}

@Composable
private fun BloomChip(
    level: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        "L$level",
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun QuizQuestionCard(
    quiz: GeneratedQuizQuestion,
    selectedChoice: String?,
    answered: Boolean,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Text(
            "Level ${quiz.bloomLevel} · ${quiz.targetConcept}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            quiz.question,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 21.sp
        )
        Spacer(Modifier.height(12.dp))
        quiz.choices.toSortedMap().forEach { (key, value) ->
            QuizChoiceRow(
                keyLabel = key,
                text = value,
                selected = selectedChoice == key,
                correct = answered && key == quiz.answer,
                wrong = answered &&
                    selectedChoice == key &&
                    key != quiz.answer,
                enabled = !answered,
                onClick = {
                    onSelect(key)
                }
            )
            Spacer(Modifier.height(8.dp))
        }
        if (answered) {
            QuizExplanation(
                correct = selectedChoice == quiz.answer,
                answer = quiz.answer,
                explanation = quiz.explanation,
                sourceSentence = quiz.sourceSentence
            )
        }
    }
}

@Composable
private fun QuizChoiceRow(
    keyLabel: String,
    text: String,
    selected: Boolean,
    correct: Boolean,
    wrong: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bg = when {
        correct -> Color(0xFFE7F8EF)
        wrong -> Color(0xFFFFEDEA)
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val fg = when {
        correct -> Color(0xFF047857)
        wrong -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                keyLabel,
                color = fg,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            modifier = Modifier.weight(1f),
            color = fg,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        if (correct || wrong) {
            Icon(
                if (correct) {
                    Icons.Default.CheckCircle
                } else {
                    Icons.Default.Cancel
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = fg
            )
        }
    }
}

@Composable
private fun QuizExplanation(
    correct: Boolean,
    answer: String,
    explanation: String,
    sourceSentence: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp)
    ) {
        Text(
            if (correct) {
                "정답입니다"
            } else {
                "정답은 $answer 입니다"
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (correct) {
                Color(0xFF047857)
            } else {
                MaterialTheme.colorScheme.error
            }
        )
        Spacer(Modifier.height(6.dp))
        Text(
            explanation,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (sourceSentence.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "근거 문장",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                sourceSentence,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuizActionButton(
    loading: Boolean,
    enabled: Boolean,
    hasQuestion: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (hasQuestion) "새 문제 생성" else "문제 생성",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun QuizLoadingCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp
        )
        Text(
            "학습 자료 기반 퀴즈를 생성하는 중입니다",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuizMessage(
    title: String,
    body: String,
    isError: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Text(
            title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun extractQuizConcept(content: String): String {
    if (content.isBlank()) {
        return EngineeringMechanicsConceptCatalog
            .concepts
            .first()
            .name
    }
    return EngineeringMechanicsConceptCatalog
        .bestMatch(content)
        .name
}

private fun stableConceptId(documentId: String, conceptName: String): String {
    EngineeringMechanicsConceptCatalog.concepts
        .find { it.name == conceptName }
        ?.let { return it.id }
    val slug = conceptName.lowercase()
        .replace(Regex("[^a-z0-9가-힣]+"), "_")
        .trim('_')
        .take(40)
        .ifBlank { "concept" }
    return "${documentId.take(8)}_$slug"
}

private fun mapQuizError(error: Exception): String {
    val msg = error.message.orEmpty()
    return when {
        msg.contains("API 키", true) ->
            msg
        msg.contains("Unable to resolve host", true) ||
            msg.contains("No address associated", true) ->
            "LLM 서버 주소를 찾을 수 없습니다. 네트워크/DNS 상태를 확인해주세요."
        msg.contains("JSON", true) ||
            msg.contains("파싱", true) ->
            "LLM 응답을 퀴즈 JSON으로 해석하지 못했습니다. 새 문제 생성을 다시 눌러주세요."
        msg.contains("API error 429", true) ->
            "요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요."
        else ->
            "퀴즈를 생성할 수 없습니다: $msg"
    }
}

@Composable
private fun LlmConnectionStatusBanner(
    connectionState: LlmConnectionState,
    errorMessage: String?,
    hasQueuedRequest: Boolean,
    onRetryConnection: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme
            .primaryContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 10.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (connectionState ==
                LlmConnectionState.CONNECTING
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    Icons.Default.Key,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (connectionState) {
                        LlmConnectionState.CONNECTING ->
                            if (hasQueuedRequest) {
                                "LLM 서버와 연결 중입니다. 요청을 보관했고 연결되면 자동으로 전송합니다."
                            } else {
                                "LLM 서버와 연결을 준비하는 중입니다."
                            }
                        LlmConnectionState.FAILED ->
                            if (hasQueuedRequest) {
                                "LLM 서버 연결에 실패했습니다. 요청을 보관했고 재연결되면 자동으로 전송합니다."
                            } else {
                                "LLM 서버 연결에 실패했습니다."
                            }
                        LlmConnectionState.READY ->
                            "LLM 서버 연결이 준비되었습니다."
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme
                        .onPrimaryContainer,
                    lineHeight = 16.sp
                )
                if (
                    connectionState ==
                    LlmConnectionState.FAILED &&
                    !errorMessage.isNullOrBlank()
                ) {
                    Text(
                        text = errorMessage,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        lineHeight = 15.sp
                    )
                }
            }
            if (connectionState == LlmConnectionState.FAILED) {
                Text(
                    "다시 연결",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onRetryConnection() }
                        .padding(
                            horizontal = 8.dp,
                            vertical = 5.dp
                        )
                )
            }
        }
    }
}

private suspend fun streamAssistantResponse(
    messages: MutableList<ChatMessage>,
    llmService: LlmService,
    conversationDataSource: ConversationLocalDataSource,
    convId: String,
    images: List<ByteArray>,
    documentContent: String?,
    onError: (String?) -> Unit,
    onLoading: (Boolean) -> Unit
) {
    onLoading(true)
    var assistantIndex: Int? = null

    fun ensureAssistantMessage(initialContent: String = ""): Int {
        val existing = assistantIndex
        if (existing != null && existing in messages.indices) {
            return existing
        }
        messages.add(
            ChatMessage(
                role = ChatMessage.Role.ASSISTANT,
                content = initialContent
            )
        )
        assistantIndex = messages.lastIndex
        return messages.lastIndex
    }

    fun updateAssistant(content: String) {
        val idx = ensureAssistantMessage(content)
        if (idx in messages.indices) {
            messages[idx] = messages[idx].copy(
                content = content
            )
        }
    }

    try {
        val sb = StringBuilder()
        var thinking = false
        llmService.stream(
            messages = messages.toList(),
            systemPrompt = buildSystemPrompt(
                documentContent
            ),
            images = images
        ).collect { token ->
            when (token) {
                LlmClient.THINKING_TOKEN -> {
                    thinking = true
                    updateAssistant("생각 중...")
                }
                LlmClient.THINKING_DONE_TOKEN -> {
                    thinking = false
                    sb.clear()
                }
                ClaudeClient.GENERATING_TOKEN,
                LlmClient.GENERATING_TOKEN -> {
                    updateAssistant("응답 생성 중...")
                }
                LlmClient.RETRY_TOKEN -> {
                    updateAssistant("서버 과부하, 재시도 중...")
                }
                else -> {
                    if (!thinking) {
                        sb.append(token)
                        updateAssistant(sb.toString())
                    }
                }
            }
        }
        val idx = assistantIndex
        if (idx != null && idx in messages.indices) {
            if (
                messages[idx].content.isBlank() ||
                messages[idx].content == "응답 생성 중..." ||
                messages[idx].content == "생각 중..."
            ) {
                messages.removeAt(idx)
                onError(
                    "이 모델에서 응답을 받지 못했습니다. " +
                        "다른 모델을 선택해주세요."
                )
            } else {
                conversationDataSource.appendMessage(
                    convId,
                    messages[idx]
                )
            }
        }
    } catch (_: kotlin.coroutines.cancellation.CancellationException) {
        val idx = assistantIndex
        if (idx != null && idx in messages.indices) {
            messages.removeAt(idx)
        }
    } catch (e: Exception) {
        val msg = e.message ?: ""
        onError(
            when {
                msg.contains("API key not configured") ->
                    "API 키가 설정되지 않았습니다"
                msg.contains("API error 401") ||
                    msg.contains("API error 403") ->
                    "API 키가 유효하지 않습니다"
                msg.contains("API error 429") ->
                    "요청 한도 초과: $msg"
                msg.contains("Unable to resolve host", true) ||
                    msg.contains("No address associated", true) ->
                    "LLM 서버 주소를 찾을 수 없습니다. " +
                        "네트워크/DNS/VPN 상태를 확인한 뒤 " +
                        "다시 연결을 눌러주세요."
                msg.contains("API error") -> msg
                msg.contains("cancel", true) ||
                    msg.contains("closed") ->
                    null
                else -> "AI 응답을 받을 수 없습니다: $msg"
            }
        )
        val idx = assistantIndex
        if (idx != null && idx in messages.indices) {
            messages.removeAt(idx)
        }
    } finally {
        onLoading(false)
    }
}

@Composable
private fun SidebarTopBar(
    currentProvider: String,
    onProviderSelected: (String) -> Unit,
    currentModel: String,
    availableModels: List<String>,
    modelsLoading: Boolean,
    onModelMenuOpened: () -> Unit,
    onModelSelected: (String) -> Unit,
    onCollapse: () -> Unit,
    onNewConversation: () -> Unit,
    onToggleHistory: () -> Unit,
    showHistory: Boolean
) {
    var showModelMenu by remember {
        mutableStateOf(false)
    }
    var showProviderMenu by remember {
        mutableStateOf(false)
    }
    val providerLabel = when (currentProvider) {
        LlmProvider.OPENAI.name -> "OpenAI"
        LlmProvider.CLAUDE.name -> "Claude"
        else -> "Gemini"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Provider toggle
        Box {
            Text(
                text = providerLabel,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme
                    .onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        MaterialTheme.colorScheme
                            .surfaceVariant
                    )
                    .clickable {
                        showProviderMenu = true
                    }
                    .padding(
                        horizontal = 8.dp,
                        vertical = 4.dp
                    )
            )
            DropdownMenu(
                expanded = showProviderMenu,
                onDismissRequest = {
                    showProviderMenu = false
                }
            ) {
                DropdownMenuItem(
                    text = { Text("Gemini") },
                    onClick = {
                        onProviderSelected(
                            LlmProvider.GEMINI.name
                        )
                        showProviderMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("OpenAI") },
                    onClick = {
                        onProviderSelected(
                            LlmProvider.OPENAI.name
                        )
                        showProviderMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Claude") },
                    onClick = {
                        onProviderSelected(
                            LlmProvider.CLAUDE.name
                        )
                        showProviderMenu = false
                    }
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        // Model selector
        Box(modifier = Modifier.weight(1f)) {
            Text(
                text = currentModel,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text
                    .style.TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme
                    .primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        onModelMenuOpened()
                        showModelMenu = true
                    }
                    .padding(
                        horizontal = 8.dp,
                        vertical = 4.dp
                    )
            )
            DropdownMenu(
                expanded = showModelMenu,
                onDismissRequest = {
                    showModelMenu = false
                }
            ) {
                if (modelsLoading) {
                    Box(
                        modifier = Modifier
                            .padding(16.dp),
                        contentAlignment =
                        Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                } else if (
                    availableModels.isEmpty()
                ) {
                    Text(
                        "모델 목록을 불러올 수 없습니다",
                        modifier = Modifier
                            .padding(16.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                    )
                } else {
                    availableModels.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    model,
                                    fontSize = 13.sp,
                                    fontWeight =
                                    if (model ==
                                        currentModel
                                    ) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight
                                            .Normal
                                    },
                                    color =
                                    if (model ==
                                        currentModel
                                    ) {
                                        MaterialTheme
                                            .colorScheme
                                            .primary
                                    } else {
                                        MaterialTheme
                                            .colorScheme
                                            .onSurface
                                    }
                                )
                            },
                            onClick = {
                                onModelSelected(model)
                                showModelMenu = false
                            }
                        )
                    }
                }
            }
        }
        IconButton(
            onClick = onToggleHistory,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (showHistory) {
                        MaterialTheme.colorScheme
                            .primaryContainer
                    } else {
                        Color.Transparent
                    }
                )
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = "대화 기록",
                tint = if (showHistory) {
                    MaterialTheme.colorScheme
                        .onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
                }
            )
        }
        IconButton(
            onClick = onNewConversation,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "새 대화",
                tint = MaterialTheme.colorScheme
                    .onSurfaceVariant
            )
        }
        IconButton(
            onClick = onCollapse,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "닫기",
                tint = MaterialTheme.colorScheme
                    .onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser =
        message.role == ChatMessage.Role.USER
    val bgColor = if (isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val alignment = if (isUser) {
        Alignment.End
    } else {
        Alignment.Start
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .padding(
                    horizontal = 12.dp,
                    vertical = 8.dp
                )
        ) {
            Text(
                text = message.content,
                color = textColor,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun SidebarInput(
    value: String,
    onValueChange: (String) -> Unit,
    isLoading: Boolean,
    pendingImages: List<ByteArray>,
    onAddImage: (ByteArray) -> Unit,
    onRemoveImage: (Int) -> Unit,
    onSend: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(
        android.content.Context.CLIPBOARD_SERVICE
    ) as android.content.ClipboardManager

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver
                .openInputStream(uri)?.use {
                    onAddImage(it.readBytes())
                }
        } catch (_: Throwable) {}
    }

    val canSend =
        value.isNotBlank() ||
            pendingImages.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        if (pendingImages.isNotEmpty()) {
            LazyRow(
                horizontalArrangement =
                Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                items(pendingImages.size) { idx ->
                    ImageThumbnail(
                        bytes = pendingImages[idx],
                        onRemove = {
                            onRemoveImage(idx)
                        }
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = {
                    imagePicker.launch("image/*")
                },
                enabled = !isLoading,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = "이미지 첨부",
                    tint = if (!isLoading) {
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme
                            .outline
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = {
                    try {
                        val clip = clipboardManager
                            .primaryClip
                        val item =
                            clip?.getItemAt(0)
                        val uri = item?.uri
                        if (uri != null) {
                            context
                                .contentResolver
                                .openInputStream(uri)
                                ?.use {
                                    onAddImage(
                                        it.readBytes()
                                    )
                                }
                        }
                    } catch (_: Throwable) {}
                },
                enabled = !isLoading,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.ContentPaste,
                    contentDescription =
                    "클립보드에서 붙여넣기",
                    tint = if (!isLoading) {
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme
                            .outline
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "메시지를 입력하세요...",
                        fontSize = 14.sp
                    )
                },
                maxLines = 5,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor =
                    MaterialTheme.colorScheme
                        .surfaceVariant,
                    unfocusedContainerColor =
                    MaterialTheme.colorScheme
                        .surfaceVariant,
                    focusedIndicatorColor =
                    Color.Transparent,
                    unfocusedIndicatorColor =
                    Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onSend,
                enabled = canSend
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored
                        .Filled.Send,
                    contentDescription = "전송",
                    tint = if (canSend) {
                        MaterialTheme.colorScheme
                            .primary
                    } else {
                        MaterialTheme.colorScheme
                            .outline
                    }
                )
            }
        }
    }
}

@Composable
private fun ImageThumbnail(bytes: ByteArray, onRemove: () -> Unit) {
    val bitmap = remember(bytes) {
        BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size
        )
    }
    Box(modifier = Modifier.size(56.dp)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "첨부 이미지",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        MaterialTheme.colorScheme
                            .surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme
                        .outline
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(18.dp)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.error
                )
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "제거",
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme
                    .onError
            )
        }
    }
}

@Composable
private fun UnifiedHistoryPanel(
    pane: HistoryPane,
    onPaneChanged: (HistoryPane) -> Unit,
    conversations: List<ConversationSummary>,
    currentConversationId: String?,
    quizHistory: List<QuizResponseRecord>,
    onSelectConversation: (ConversationSummary) -> Unit,
    onDeleteConversation: (ConversationSummary) -> Unit,
    onDeleteQuizRecord: (QuizResponseRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (pane == HistoryPane.CHAT) {
            ConversationHistoryPanel(
                conversations = conversations,
                currentId = currentConversationId,
                onSelect = onSelectConversation,
                onDelete = onDeleteConversation
            )
        } else {
            QuizHistoryPanel(
                records = quizHistory,
                onDelete = onDeleteQuizRecord
            )
        }
    }
}

@Composable
private fun QuizHistoryPanel(
    records: List<QuizResponseRecord>,
    onDelete: (QuizResponseRecord) -> Unit
) {
    var pendingDelete by remember {
        mutableStateOf<QuizResponseRecord?>(null)
    }
    pendingDelete?.let { record ->
        DeleteConfirmDialog(
            title = "퀴즈 기록 삭제",
            message = "이 퀴즈 풀이 기록을 삭제할까요?",
            onConfirm = {
                onDelete(record)
                pendingDelete = null
            },
            onDismiss = {
                pendingDelete = null
            }
        )
    }
    if (records.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "이 문서에서 푼 퀴즈 기록이 없습니다",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme
                    .onSurfaceVariant
            )
        }
        return
    }
    val dayFormat = remember {
        SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
    }
    val timeFormat = remember {
        SimpleDateFormat("HH:mm", Locale.getDefault())
    }
    val grouped = remember(records) {
        records.groupBy {
            dayFormat.format(Date(it.answeredAt))
        }.toSortedMap(compareByDescending { it })
    }
    var selectedDay by remember(records) {
        mutableStateOf(grouped.keys.first())
    }
    val selectedRecords = grouped[selectedDay].orEmpty()
        .sortedByDescending { it.answeredAt }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            items(grouped.keys.toList()) { day ->
                Text(
                    day,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (day == selectedDay) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .clickable { selectedDay = day }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    color = if (day == selectedDay) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(selectedRecords, key = { it.id }) { record ->
                SwipeRevealDeleteContainer(
                    onDelete = {
                        pendingDelete = record
                    }
                ) {
                    QuizHistoryCard(
                        record = record,
                        timeText = timeFormat.format(
                            Date(record.answeredAt)
                        )
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun QuizHistoryCard(
    record: QuizResponseRecord,
    timeText: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                timeText,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "L${record.bloomLevel}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (record.isCorrect) "정답" else "오답",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (record.isCorrect) {
                    Color(0xFF047857)
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            record.question.ifBlank { "저장된 질문 내용이 없습니다" },
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        record.choices.toSortedMap().forEach { (key, value) ->
            val isSelected = key == record.selectedAnswer
            val isAnswer = key == record.correctAnswer
            Text(
                "$key. $value",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = when {
                    isAnswer -> Color(0xFF047857)
                    isSelected && !record.isCorrect ->
                        MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (isSelected || isAnswer) {
                    FontWeight.Bold
                } else {
                    FontWeight.Normal
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (record.isCorrect) {
                "선택한 ${record.selectedAnswer}가 정답입니다."
            } else {
                "선택한 답: ${record.selectedAnswer} · 정답: ${record.correctAnswer}"
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (record.isCorrect) {
                Color(0xFF047857)
            } else {
                MaterialTheme.colorScheme.error
            }
        )
        if (record.explanation.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                record.explanation,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (record.sourceSentence.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "근거: ${record.sourceSentence}",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SwipeRevealDeleteContainer(
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val revealWidth = 86.dp
    val revealWidthPx = with(density) { revealWidth.toPx() }
    var offsetPx by remember {
        mutableStateOf(0f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .width(revealWidth)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.error)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "삭제",
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "삭제",
                        color = MaterialTheme.colorScheme.onError,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset {
                    IntOffset(offsetPx.roundToInt(), 0)
                }
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            offsetPx = if (
                                offsetPx <= -revealWidthPx / 2f
                            ) {
                                -revealWidthPx
                            } else {
                                0f
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            offsetPx = (
                                offsetPx + dragAmount
                            ).coerceIn(
                                -revealWidthPx,
                                0f
                            )
                        }
                    )
                }
        ) {
            content()
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                title,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(message)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    "삭제",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun ConversationHistoryPanel(
    conversations: List<ConversationSummary>,
    currentId: String?,
    onSelect: (ConversationSummary) -> Unit,
    onDelete: (ConversationSummary) -> Unit
) {
    var pendingDelete by remember {
        mutableStateOf<ConversationSummary?>(null)
    }
    pendingDelete?.let { conversation ->
        DeleteConfirmDialog(
            title = "채팅 기록 삭제",
            message = "이 채팅 기록을 삭제할까요?",
            onConfirm = {
                onDelete(conversation)
                pendingDelete = null
            },
            onDismiss = {
                pendingDelete = null
            }
        )
    }
    if (conversations.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "저장된 대화가 없습니다",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme
                    .onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalArrangement =
            Arrangement.spacedBy(2.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            items(
                conversations,
                key = { it.id }
            ) { conv ->
                val isSelected =
                    conv.id == currentId
                SwipeRevealDeleteContainer(
                    onDelete = {
                        pendingDelete = conv
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(8.dp)
                            )
                            .background(
                                if (isSelected) {
                                    MaterialTheme
                                        .colorScheme
                                        .primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                            .clickable {
                                onSelect(conv)
                            }
                            .padding(
                                horizontal = 12.dp,
                                vertical = 10.dp
                            ),
                        verticalAlignment =
                        Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                        ) {
                            Text(
                                conv.title,
                                fontSize = 13.sp,
                                fontWeight =
                                FontWeight.Medium,
                                maxLines = 1,
                                color = MaterialTheme
                                    .colorScheme
                                    .onSurface
                            )
                            val dateStr =
                                java.text.SimpleDateFormat(
                                    "MM/dd HH:mm",
                                    java.util.Locale
                                        .getDefault()
                                ).format(
                                    java.util.Date(
                                        conv.updated
                                    )
                                )
                            Text(
                                dateStr,
                                fontSize = 11.sp,
                                color = MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }
    }
}
