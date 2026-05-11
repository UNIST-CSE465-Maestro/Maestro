package com.maestro.app.ui.viewer

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.maestro.app.ui.theme.MaestroOnSurface
import com.maestro.app.ui.theme.MaestroOnSurfaceVariant
import com.maestro.app.ui.theme.MaestroOutline
import com.maestro.app.ui.theme.MaestroPrimary
import com.maestro.app.ui.theme.MaestroSurfaceContainer
import com.maestro.app.ui.theme.MaestroSurfaceContainerHigh
import com.maestro.app.ui.theme.MaestroSurfaceContainerLowest
import com.maestro.app.ui.theme.Slate100
import com.maestro.app.ui.theme.Slate200
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
    openTabs: List<OpenPdfTab> = emptyList(),
    activeTabId: String = viewModel.pdfId,
    initialFirstVisiblePageIndex: Int = 0,
    initialFirstVisiblePageScrollOffset: Int = 0,
    onSelectTab: (OpenPdfTab) -> Unit = {},
    onCloseTab: (OpenPdfTab) -> Unit = {},
    onOpenNewTab: () -> Unit = {},
    onScrollPositionChanged: (pageIndex: Int, scrollOffset: Int) -> Unit = { _, _ -> },
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
    val pendingQuizCrop by viewModel
        .pendingQuizCrop.collectAsState()
    val documentContent by viewModel
        .documentContent.collectAsState()
    val documentJsonContent by viewModel
        .documentJsonContent.collectAsState()
    val searchQuery by viewModel
        .searchQuery.collectAsState()
    val searchMatches by viewModel
        .searchMatches.collectAsState()
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
    var activeSearchMatchIndex by remember {
        mutableIntStateOf(-1)
    }
    var searchNavigationRequest by remember {
        mutableIntStateOf(0)
    }

    LaunchedEffect(searchQuery, searchMatches) {
        if (searchQuery.isBlank() || searchMatches.isEmpty()) {
            activeSearchMatchIndex = -1
        } else {
            activeSearchMatchIndex = 0
            searchNavigationRequest++
        }
    }

    fun goToNextSearchMatch() {
        if (searchQuery.isBlank() || searchMatches.isEmpty()) {
            return
        }
        activeSearchMatchIndex =
            if (activeSearchMatchIndex < 0) {
                0
            } else {
                (activeSearchMatchIndex + 1) %
                    searchMatches.size
            }
        searchNavigationRequest++
    }

    fun goToPreviousSearchMatch() {
        if (searchQuery.isBlank() || searchMatches.isEmpty()) {
            return
        }
        activeSearchMatchIndex =
            if (activeSearchMatchIndex < 0) {
                searchMatches.lastIndex
            } else {
                (
                    activeSearchMatchIndex - 1 +
                        searchMatches.size
                    ) % searchMatches.size
            }
        searchNavigationRequest++
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
            searchQuery = searchQuery,
            searchResultCount = searchMatches.size,
            activeSearchResultIndex =
            activeSearchMatchIndex,
            onSearchQueryChange = {
                viewModel.setSearchQuery(it)
            },
            onSearchPrevious = {
                goToPreviousSearchMatch()
            },
            onSearchNext = {
                goToNextSearchMatch()
            },
            onQuiz = {
                viewModel.extractAndQuiz()
            },
            onToggleSidebar = {
                viewModel.toggleChatSidebar()
            }
        )

        if (openTabs.size > 1) {
            PdfViewerTabStrip(
                tabs = openTabs,
                activeTabId = activeTabId,
                onSelectTab = onSelectTab,
                onCloseTab = onCloseTab,
                onOpenNewTab = onOpenNewTab
            )
        }

        Row(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.weight(1f)) {
                CanvasSection(
                    pdfUri = safeUri,
                    pageCount = safePageCount,
                    drawingState = drawingState,
                    modifier = Modifier.fillMaxSize(),
                    viewportKey = activeTabId,
                    initialFirstVisiblePageIndex =
                    initialFirstVisiblePageIndex,
                    initialFirstVisiblePageScrollOffset =
                    initialFirstVisiblePageScrollOffset,
                    searchMatches = searchMatches,
                    activeSearchMatch = searchMatches
                        .getOrNull(activeSearchMatchIndex),
                    searchNavigationRequest =
                    searchNavigationRequest,
                    onScrollPositionChanged =
                    onScrollPositionChanged,
                    onCropLlm = { payload ->
                        viewModel.sendSelectionToLlm(
                            payload.imageBytes,
                            "이 영역에 대해 설명해주세요"
                        )
                    },
                    onCropQuiz = { payload ->
                        viewModel.sendSelectionToQuiz(payload)
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
                documentJsonContent = documentJsonContent,
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
                pendingQuizCrop = pendingQuizCrop,
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
                        choiceExplanations,
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
                        choiceExplanations = choiceExplanations,
                        sourceSentence = sourceSentence
                    )
                },
                onQuizHistoryDeleted = { recordId ->
                    viewModel.deleteQuizResponse(recordId)
                },
                onPendingConsumed = {
                    viewModel.consumePendingLlm()
                },
                onPendingQuizCropConsumed = {
                    viewModel.consumePendingQuizCrop()
                }
            )
        }
    }
}

@Composable
private fun PdfViewerTabStrip(
    tabs: List<OpenPdfTab>,
    activeTabId: String,
    onSelectTab: (OpenPdfTab) -> Unit,
    onCloseTab: (OpenPdfTab) -> Unit,
    onOpenNewTab: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(Slate100)
            .border(1.dp, Slate200),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(
                items = tabs,
                key = { it.documentId }
            ) { tab ->
                PdfViewerTab(
                    tab = tab,
                    selected = tab.documentId == activeTabId,
                    onSelect = {
                        onSelectTab(tab)
                    },
                    onClose = {
                        onCloseTab(tab)
                    }
                )
            }
        }

        IconButton(
            onClick = onOpenNewTab,
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "새 PDF 탭 열기",
                tint = MaestroPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun PdfViewerTab(
    tab: OpenPdfTab,
    selected: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val background = if (selected) {
        MaestroSurfaceContainerLowest
    } else {
        MaestroSurfaceContainer
    }
    val border = if (selected) {
        MaestroPrimary
    } else {
        Slate200
    }
    Row(
        modifier = Modifier
            .widthIn(min = 148.dp, max = 238.dp)
            .height(32.dp)
            .background(
                color = background,
                shape = RoundedCornerShape(6.dp)
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = border,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onSelect)
            .padding(start = 9.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Description,
            contentDescription = null,
            tint = if (selected) {
                MaestroPrimary
            } else {
                MaestroOutline
            },
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            tab.title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp,
            fontWeight = if (selected) {
                FontWeight.SemiBold
            } else {
                FontWeight.Medium
            },
            color = if (selected) {
                MaestroOnSurface
            } else {
                MaestroOnSurfaceVariant
            }
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(26.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "탭 닫기",
                tint = Slate500,
                modifier = Modifier.size(15.dp)
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
