package com.maestro.app.ui.viewer

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maestro.app.data.local.ConversationLocalDataSource
import com.maestro.app.domain.repository.SettingsRepository
import com.maestro.app.domain.service.LlmService
import com.maestro.app.domain.service.QuizService
import com.maestro.app.ui.components.CanvasSection
import com.maestro.app.ui.components.LlmSidebar
import com.maestro.app.ui.components.StudySidebarMode
import com.maestro.app.ui.components.TopAppBarSection
import com.maestro.app.ui.drawing.DrawingState
import com.maestro.app.ui.theme.MaestroBackground
import com.maestro.app.ui.theme.MaestroPrimary
import com.maestro.app.ui.theme.MaestroSurfaceContainerHigh
import com.maestro.app.ui.theme.MaestroSurfaceContainerLowest
import com.maestro.app.ui.theme.Slate500

enum class LlmConnectionState {
    CONNECTING,
    READY,
    FAILED
}

@Composable
fun ViewerScreen(
    viewModel: ViewerViewModel,
    llmService: LlmService,
    quizService: QuizService,
    settingsRepository: SettingsRepository,
    conversationDataSource: ConversationLocalDataSource,
    onBack: () -> Unit
) {
    val drawingState = viewModel.drawingState
    val sidebarVisible by viewModel
        .sidebarVisible.collectAsState()
    val sidebarMode by viewModel
        .sidebarMode.collectAsState()
    val pendingImage by viewModel
        .pendingLlmImage.collectAsState()
    val pendingPrompt by viewModel
        .pendingLlmPrompt.collectAsState()
    val documentContent by viewModel
        .documentContent.collectAsState()
    val quizMastery by viewModel
        .quizMastery.collectAsState()
    val quizHistory by viewModel
        .quizHistory.collectAsState()
    val isPinned by viewModel
        .isPinned.collectAsState()
    val isBookmarked by viewModel
        .isCurrentPageBookmarked.collectAsState()
    val extractionProgress by viewModel
        .extractionProgress.collectAsState()
    val context = LocalContext.current
    var llmConnectionState by remember {
        mutableStateOf(LlmConnectionState.CONNECTING)
    }
    var llmConnectionError by remember {
        mutableStateOf<String?>(null)
    }

    // Warm up the provider connection without loading/probing all models.
    fun retryLlmConnection() {
        llmConnectionState = LlmConnectionState.CONNECTING
        llmConnectionError = null
    }

    LaunchedEffect(llmConnectionState) {
        if (llmConnectionState ==
            LlmConnectionState.CONNECTING
        ) {
            val result = llmService.warmUp()
            if (result.isSuccess) {
                llmConnectionState = LlmConnectionState.READY
            } else {
                llmConnectionState = LlmConnectionState.FAILED
                llmConnectionError = result.exceptionOrNull()
                    ?.message
                    ?: "LLM 서버 연결에 실패했습니다"
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val stream = context.contentResolver
                .openInputStream(uri)
            val bitmap = BitmapFactory
                .decodeStream(stream)
            stream?.close()
            if (bitmap != null) {
                val page = drawingState
                    .activePageIndex
                    .coerceAtLeast(0)
                val imgW = bitmap.width.toFloat()
                val imgH = bitmap.height.toFloat()
                val overlay =
                    DrawingState.ImageOverlay(
                        bitmap,
                        100f,
                        100f,
                        imgW,
                        imgH
                    )
                drawingState.addImage(
                    page,
                    overlay
                )
                drawingState.selectedImage =
                    overlay
                drawingState.selectedImagePage =
                    page
            }
        } catch (_: Throwable) {
            // ignore decode failures
        }
    }

    val safeUri = remember(viewModel.pdfUri) {
        try {
            val uri = viewModel.pdfUri ?: return@remember null
            when (uri.scheme) {
                "file" -> {
                    val path = uri.path
                        ?: return@remember null
                    if (java.io.File(path).exists()) {
                        uri
                    } else {
                        null
                    }
                }
                else -> uri
            }
        } catch (_: Throwable) {
            null
        }
    }

    val safePageCount = if (safeUri != null) {
        viewModel.pageCount.coerceAtLeast(1)
    } else {
        0
    }

    // Track current page for bookmark state
    LaunchedEffect(drawingState.activePageIndex) {
        viewModel.setCurrentPage(
            drawingState.activePageIndex.coerceAtLeast(0)
        )
    }

    // Auto-save on annotation changes
    LaunchedEffect(drawingState.annotationVersion) {
        viewModel.saveIfNeeded()
    }

    if (safeUri == null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaestroBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "PDF를 열 수 없습니다",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "파일이 손상되었거나 지원하지 않는 형식입니다",
                    fontSize = 14.sp,
                    color = Slate500
                )
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = onBack) {
                    Text("돌아가기")
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBarSection(
            drawingState = drawingState,
            isPinned = isPinned,
            isBookmarked = isBookmarked,
            onBack = onBack,
            onUndo = { drawingState.undo() },
            onRedo = { drawingState.redo() },
            onTogglePin = {
                viewModel.togglePin()
            },
            onToggleBookmark = {
                val page = drawingState.activePageIndex
                    .coerceAtLeast(0)
                viewModel.toggleBookmark(page)
            },
            onInsertImage = {
                imagePicker.launch("image/*")
            },
            onQuiz = {
                viewModel.extractAndQuiz()
            },
            onToggleSidebar = {
                viewModel.toggleChatSidebar()
            }
        )

        Row(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.weight(1f)) {
                CanvasSection(
                    pdfUri = safeUri,
                    pageCount = safePageCount,
                    drawingState = drawingState,
                    modifier = Modifier.fillMaxSize(),
                    onCropLlm = { imageBytes ->
                        viewModel.sendSelectionToLlm(
                            imageBytes,
                            "이 영역에 대해 설명해주세요"
                        )
                    }
                )
                extractionProgress?.let { progress ->
                    ExtractionProgressOverlay(
                        progress = progress,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    )
                }
            }
            LlmSidebar(
                isVisible = sidebarVisible,
                onCollapse = { viewModel.collapseSidebar() },
                llmService = llmService,
                quizService = quizService,
                settingsRepository = settingsRepository,
                conversationDataSource = conversationDataSource,
                documentContent = documentContent,
                documentId = viewModel.pdfId,
                pageIndex = drawingState.activePageIndex
                    .coerceAtLeast(0),
                quizMastery = quizMastery,
                quizHistory = quizHistory,
                sidebarMode = sidebarMode,
                onSidebarModeChanged = {
                    viewModel.setSidebarMode(it)
                },
                pendingImage = pendingImage,
                pendingPrompt = pendingPrompt,
                llmConnectionState = llmConnectionState,
                llmConnectionError = llmConnectionError,
                onRetryConnection = {
                    retryLlmConnection()
                },
                onLlmRequested = { prompt, hasImage ->
                    viewModel.recordLlmRequested(
                        prompt,
                        hasImage
                    )
                },
                onQuizRequested = { conceptId, bloomLevel ->
                    viewModel.recordQuizRequested(
                        conceptId,
                        bloomLevel
                    )
                },
                onQuizAnswered = {
                        conceptId,
                        bloomLevel,
                        isCorrect,
                        responseTimeMs,
                        question,
                        choices,
                        selectedAnswer,
                        correctAnswer,
                        explanation,
                        sourceSentence
                    ->
                    viewModel.recordQuizAnswered(
                        conceptId = conceptId,
                        bloomLevel = bloomLevel,
                        isCorrect = isCorrect,
                        responseTimeMs = responseTimeMs,
                        question = question,
                        choices = choices,
                        selectedAnswer = selectedAnswer,
                        correctAnswer = correctAnswer,
                        explanation = explanation,
                        sourceSentence = sourceSentence
                    )
                },
                onQuizHistoryDeleted = { recordId ->
                    viewModel.deleteQuizResponse(recordId)
                },
                onPendingConsumed = {
                    viewModel.consumePendingLlm()
                }
            )
        }
    }
}

@Composable
private fun ExtractionProgressOverlay(
    progress: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                MaestroSurfaceContainerLowest,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CircularProgressIndicator(
            progress = {
                progress.coerceIn(0, 100) / 100f
            },
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaestroPrimary,
            trackColor = MaestroSurfaceContainerHigh
        )
        Text(
            "추출 ${progress.coerceIn(0, 100)}%",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaestroPrimary
        )
    }
}
