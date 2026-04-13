package com.maestro.app.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maestro.app.ui.components.CanvasSection
import com.maestro.app.ui.components.TopAppBarSection
import com.maestro.app.ui.theme.MaestroBackground
import com.maestro.app.ui.theme.Slate500

@Composable
fun ViewerScreen(viewModel: ViewerViewModel, onBack: () -> Unit) {
    val drawingState = viewModel.drawingState
    val sidebarVisible by viewModel.sidebarVisible.collectAsState()

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
            onToggleSidebar = { viewModel.toggleSidebar() }
        )

        Row(modifier = Modifier.weight(1f)) {
            CanvasSection(
                pdfUri = safeUri,
                pageCount = safePageCount,
                drawingState = drawingState,
                modifier = Modifier.weight(1f)
            )
            // LLM sidebar placeholder — Phase 3
            if (sidebarVisible) {
                Box(
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                        .background(MaestroBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "LLM Sidebar (Phase 3)",
                        color = Slate500
                    )
                }
            }
        }
    }
}
