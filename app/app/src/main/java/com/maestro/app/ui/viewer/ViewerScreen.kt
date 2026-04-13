package com.maestro.app.ui.viewer

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.maestro.app.ui.components.CanvasSection
import com.maestro.app.ui.components.LlmSidebar
import com.maestro.app.ui.components.TopAppBarSection
import com.maestro.app.ui.drawing.DrawingState
import com.maestro.app.ui.theme.MaestroBackground
import com.maestro.app.ui.theme.Slate500

@Composable
fun ViewerScreen(
    viewModel: ViewerViewModel,
    llmService: LlmService,
    settingsRepository: SettingsRepository,
    conversationDataSource: ConversationLocalDataSource,
    onBack: () -> Unit
) {
    val drawingState = viewModel.drawingState
    val sidebarVisible by viewModel
        .sidebarVisible.collectAsState()
    val context = LocalContext.current

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
            onBack = onBack,
            onUndo = { drawingState.undo() },
            onRedo = { drawingState.redo() },
            onInsertImage = {
                imagePicker.launch("image/*")
            },
            onQuiz = {
                viewModel.extractAndQuiz()
            },
            onToggleSidebar = {
                viewModel.toggleSidebar()
            }
        )

        Row(modifier = Modifier.weight(1f)) {
            CanvasSection(
                pdfUri = safeUri,
                pageCount = safePageCount,
                drawingState = drawingState,
                modifier = Modifier.weight(1f),
                onLassoLlm = { imageBytes ->
                    viewModel.sendSelectionToLlm(
                        imageBytes,
                        "이 영역에 대해 설명해주세요"
                    )
                }
            )
            LlmSidebar(
                isVisible = sidebarVisible,
                onCollapse = { viewModel.toggleSidebar() },
                llmService = llmService,
                settingsRepository = settingsRepository,
                conversationDataSource = conversationDataSource
            )
        }
    }
}
