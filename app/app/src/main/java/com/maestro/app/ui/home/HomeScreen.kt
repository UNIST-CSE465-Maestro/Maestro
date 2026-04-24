package com.maestro.app.ui.home

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maestro.app.domain.model.ExtractionStatus
import com.maestro.app.domain.model.Folder
import com.maestro.app.domain.model.PdfDocument
import com.maestro.app.ui.config.UxConfig
import com.maestro.app.ui.theme.*

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenPdf: (PdfDocument) -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val documents by viewModel.documents.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val currentFolderId by viewModel.currentFolderId.collectAsState()
    val selectedDocIds by viewModel.selectedDocIds.collectAsState()
    val isMultiSelect by viewModel.isMultiSelectMode.collectAsState()

    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var crashLog by remember { mutableStateOf<String?>(null) }
    var debugLog by remember { mutableStateOf<String?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    // PDF picker launcher
    val extractingDocIds by viewModel.extractingDocIds
        .collectAsState()
    val pendingImportUri by viewModel.pendingImportUri
        .collectAsState()
    val extractionError by viewModel.extractionError
        .collectAsState()

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.stagePdfImport(it) }
    }

    // Long-press context menu state
    var contextPdf by remember { mutableStateOf<PdfDocument?>(null) }
    var contextFolder by remember { mutableStateOf<Folder?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showMergeOrderDialog by remember { mutableStateOf(false) }

    // Drag-and-drop state
    data class DragItem(val id: String, val isFolder: Boolean, val label: String)
    var dragItem by remember { mutableStateOf<DragItem?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var hasDragMoved by remember { mutableStateOf(false) }
    val folderBoundsMap = remember { mutableStateMapOf<String, Rect>() }
    var rootOffset by remember { mutableStateOf(Offset.Zero) }

    // Hovered folder for visual highlight (recomputes on each recomposition)
    val hoveredFolderId = if (dragItem != null && hasDragMoved) {
        folderBoundsMap.entries.firstOrNull { (id, bounds) ->
            bounds.contains(dragPosition) && id != dragItem?.id
        }?.key
    } else {
        null
    }

    fun clearDrag() {
        dragItem = null
        hasDragMoved = false
    }

    /** Compute drop target from current state — safe to call inside pointerInput lambdas */
    fun findDropTarget(excludeSelfId: String, isFolderDrag: Boolean): String? {
        val pos = dragPosition
        return folderBoundsMap.entries.firstOrNull { (id, bounds) ->
            if (id == excludeSelfId) return@firstOrNull false
            if (!bounds.contains(pos)) return@firstOrNull false
            // Prevent circular folder moves
            if (isFolderDrag) {
                var cur: String? = id
                while (cur != null) {
                    if (cur == excludeSelfId) return@firstOrNull false
                    cur = folders.find { it.id == cur }?.parentId
                }
            }
            true
        }?.key
    }

    // Clear stale folder bounds when navigating to a different folder
    LaunchedEffect(currentFolderId) {
        folderBoundsMap.clear()
    }

    LaunchedEffect(Unit) {
        try {
            val logFile = java.io.File(context.filesDir, "crash_log.txt")
            if (logFile.exists()) {
                crashLog = logFile.readText()
            }
        } catch (_: Throwable) {}
        try {
            val dbgFile = java.io.File(context.filesDir, "maestro/debug.log")
            if (dbgFile.exists()) {
                debugLog = dbgFile.readText()
            }
        } catch (_: Throwable) {}
    }

    // Crash log dialog
    if (crashLog != null) {
        AlertDialog(
            onDismissRequest = {
                try {
                    java.io.File(context.filesDir, "crash_log.txt").delete()
                } catch (
                    _: Throwable
                ) {}
                crashLog = null
            },
            title = { Text("크래시 로그", fontWeight = FontWeight.Bold) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.heightIn(max = UxConfig.Home.DIALOG_LOG_MAX_HEIGHT)
                ) {
                    item {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(
                                crashLog ?: "",
                                fontSize = UxConfig.Home.DIALOG_LOG_FONT_SIZE,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                lineHeight = UxConfig.Home.DIALOG_LOG_LINE_HEIGHT
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        java.io.File(context.filesDir, "crash_log.txt").delete()
                    } catch (
                        _: Throwable
                    ) {}
                    crashLog = null
                }) { Text("확인") }
            }
        )
    }

    // Debug log dialog
    if (debugLog != null) {
        AlertDialog(
            onDismissRequest = {
                try {
                    java.io.File(context.filesDir, "maestro/debug.log").delete()
                } catch (
                    _: Throwable
                ) {}
                debugLog = null
            },
            title = { Text("디버그 로그", fontWeight = FontWeight.Bold) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.heightIn(max = UxConfig.Home.DIALOG_LOG_MAX_HEIGHT)
                ) {
                    item {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(
                                debugLog ?: "",
                                fontSize = UxConfig.Home.DIALOG_LOG_FONT_SIZE,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                lineHeight = UxConfig.Home.DIALOG_LOG_LINE_HEIGHT
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        java.io.File(context.filesDir, "maestro/debug.log").delete()
                    } catch (
                        _: Throwable
                    ) {}
                    debugLog = null
                }) { Text("확인 (로그 삭제)") }
            }
        )
    }

    // Rename dialog
    if (showRenameDialog) {
        val currentName = contextPdf?.displayName ?: contextFolder?.name ?: ""
        RenameDialog(
            currentName = currentName,
            onDismiss = {
                showRenameDialog = false
                contextPdf = null
                contextFolder = null
            },
            onRename = { newName ->
                contextPdf?.let { viewModel.renameDocument(it.id, newName) }
                contextFolder?.let { viewModel.renameFolder(it.id, newName) }
                showRenameDialog = false
                contextPdf = null
                contextFolder = null
            }
        )
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        val label = contextPdf?.displayName
            ?: contextFolder?.name ?: ""
        val isNonEmptyFolder = contextFolder != null &&
            viewModel.isFolderNonEmpty(
                contextFolder!!.id
            )
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirm = false
                contextPdf = null
                contextFolder = null
            },
            title = {
                Text(
                    "삭제",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (isNonEmptyFolder) {
                    Text(
                        "\"$label\" 폴더 안에 파일 또는 " +
                            "하위 폴더가 있습니다.\n" +
                            "폴더와 내용을 모두 " +
                            "삭제하시겠습니까?"
                    )
                } else {
                    Text(
                        "\"$label\"을(를) " +
                            "삭제하시겠습니까?"
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    contextPdf?.let {
                        viewModel.deleteDocument(it.id)
                    }
                    contextFolder?.let {
                        viewModel.deleteFolder(it.id)
                    }
                    showDeleteConfirm = false
                    contextPdf = null
                    contextFolder = null
                }) {
                    Text(
                        "삭제",
                        color = MaestroError,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    contextPdf = null
                    contextFolder = null
                }) { Text("취소") }
            }
        )
    }

    // Move picker dialog
    if (showMoveDialog) {
        val movingPdfId = contextPdf?.id
        val movingFolderId = contextFolder?.id
        val movingLabel = contextPdf?.displayName ?: contextFolder?.name ?: ""
        MovePickerDialog(
            allFolders = folders,
            excludeFolderId = movingFolderId,
            itemLabel = movingLabel,
            onDismiss = {
                showMoveDialog = false
                contextPdf = null
                contextFolder = null
            },
            onConfirm = { targetFolderId ->
                if (movingPdfId != null) viewModel.moveDocument(movingPdfId, targetFolderId)
                if (movingFolderId != null) viewModel.moveFolder(movingFolderId, targetFolderId)
                showMoveDialog = false
                contextPdf = null
                contextFolder = null
            }
        )
    }

    // Create folder dialog
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onCreate = { name ->
                viewModel.createFolder(name)
                showCreateFolderDialog = false
            }
        )
    }

    if (showMergeOrderDialog) {
        MergeOrderDialog(
            docs = documents.filter {
                it.id in selectedDocIds
            },
            onDismiss = { showMergeOrderDialog = false },
            onConfirm = { orderedIds ->
                showMergeOrderDialog = false
                viewModel.mergeOrdered(orderedIds)
            }
        )
    }

    // Extraction mode selection dialog
    if (pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = {
                viewModel.cancelPdfImport()
            },
            title = {
                Text(
                    "텍스트 추출 방식",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "PDF에서 텍스트를 추출할 방식을 선택하세요.\n" +
                        "AI 추출은 더 정확하지만 시간이 더 걸립니다."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImportUri?.let {
                            viewModel.importAndExtract(
                                it,
                                "ai"
                            )
                        }
                    }
                ) {
                    Text(
                        "AI 추출",
                        fontWeight = FontWeight.Bold,
                        color = MaestroPrimary
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingImportUri?.let {
                            viewModel.importAndExtract(
                                it,
                                "standard"
                            )
                        }
                    }
                ) {
                    Text(
                        "일반 추출",
                        color = Slate500
                    )
                }
            }
        )
    }

    // Extraction error dialog
    if (extractionError != null) {
        AlertDialog(
            onDismissRequest = {
                viewModel.clearExtractionError()
            },
            title = {
                Text(
                    "추출 실패",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(extractionError ?: "")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearExtractionError()
                    }
                ) { Text("확인") }
            }
        )
    }

    // Filter items for current folder
    val currentFolders = folders.filter { it.parentId == currentFolderId }
    val currentDocs = documents.filter { it.folderId == currentFolderId }
    val currentFolderName = folders.find { it.id == currentFolderId }?.name

    Box(
        modifier = Modifier.fillMaxSize().background(MaestroBackground)
            .onGloballyPositioned { rootOffset = it.positionInWindow() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HomeTopBar(
                currentFolderName = currentFolderName,
                onBack = if (currentFolderId != null) {
                    {
                        val parent = folders.find { it.id == currentFolderId }?.parentId
                        viewModel.navigateFolder(parent)
                    }
                } else {
                    null
                },
                onOpenSettings = onOpenSettings
            )

            if (currentFolders.isEmpty() && currentDocs.isEmpty()) {
                EmptyLibrary(
                    isInFolder = currentFolderId != null,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = UxConfig.Home.GRID_MIN_SIZE),
                    modifier = Modifier.weight(
                        1f
                    ).padding(horizontal = UxConfig.Home.GRID_PADDING_H),
                    userScrollEnabled = dragItem == null,
                    contentPadding = PaddingValues(
                        top = UxConfig.Home.GRID_PADDING_TOP,
                        bottom = UxConfig.Home.GRID_PADDING_BOTTOM
                    ),
                    horizontalArrangement = Arrangement.spacedBy(UxConfig.Home.GRID_SPACING_H),
                    verticalArrangement = Arrangement.spacedBy(UxConfig.Home.GRID_SPACING_V)
                ) {
                    items(currentFolders, key = { "folder_${it.id}" }) { folder ->
                        var itemPos by remember { mutableStateOf(Offset.Zero) }
                        var itemWidth by remember { mutableStateOf(0) }
                        Box {
                            FolderGridItem(
                                folder = folder,
                                isDropTarget = hoveredFolderId == folder.id,
                                modifier = Modifier
                                    .onGloballyPositioned {
                                        folderBoundsMap[folder.id] = it.boundsInWindow()
                                        itemPos = it.positionInWindow()
                                        itemWidth = it.size.width
                                    }
                                    .pointerInput(folder.id) {
                                        val handler = android.os.Handler(
                                            android.os.Looper.getMainLooper()
                                        )
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            val startPos = itemPos + down.position
                                            var moved = false
                                            var longPressed = false
                                            val runnable = Runnable {
                                                longPressed = true
                                                haptic.performHapticFeedback(
                                                    HapticFeedbackType.LongPress
                                                )
                                                contextFolder = folder
                                                contextPdf = null
                                                dragItem = DragItem(
                                                    folder.id, true, folder.name
                                                )
                                                dragPosition = startPos
                                                hasDragMoved = false
                                            }
                                            handler.postDelayed(
                                                runnable,
                                                UxConfig.Gesture.LONG_PRESS_HOME_MS
                                            )
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull() ?: break
                                                if (change.changedToUp()) {
                                                    handler.removeCallbacks(runnable)
                                                    if (longPressed && moved) {
                                                        val target = findDropTarget(folder.id, true)
                                                        if (target != null) {
                                                            viewModel.moveFolder(
                                                                folder.id,
                                                                target
                                                            )
                                                        }
                                                        contextFolder = null
                                                    } else if (!longPressed && !moved) {
                                                        viewModel.navigateFolder(folder.id)
                                                    }
                                                    clearDrag()
                                                    break
                                                }
                                                if (longPressed) {
                                                    val drag = change.positionChange()
                                                    val moved2 = drag.getDistance() >
                                                        UxConfig.Gesture.DRAG_THRESHOLD_PX
                                                    if (moved2) {
                                                        if (!moved) contextFolder = null
                                                        moved = true
                                                        hasDragMoved = true
                                                    }
                                                    dragPosition += drag
                                                    change.consume()
                                                }
                                            }
                                        }
                                    }
                            )
                            DropdownMenu(
                                expanded = contextFolder?.id == folder.id,
                                onDismissRequest = { contextFolder = null },
                                offset = DpOffset(with(density) { itemWidth.toDp() }, 0.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("이름 변경") },
                                    onClick = { showRenameDialog = true },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = null,
                                            tint = MaestroOnSurfaceVariant
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("이동") },
                                    onClick = { showMoveDialog = true },
                                    leadingIcon = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.DriveFileMove,
                                            contentDescription = null,
                                            tint = MaestroOnSurfaceVariant
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("삭제", color = MaestroError) },
                                    onClick = { showDeleteConfirm = true },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaestroError
                                        )
                                    }
                                )
                            }
                        }
                    }
                    items(currentDocs, key = { it.id }) { doc ->
                        var itemPos by remember { mutableStateOf(Offset.Zero) }
                        var docItemWidth by remember { mutableStateOf(0) }
                        Box {
                            PdfGridItem(
                                doc = doc,
                                isSelected = doc.id in selectedDocIds,
                                isMultiSelectMode = isMultiSelect,
                                isExtracting = doc.id in extractingDocIds,
                                modifier = Modifier
                                    .onGloballyPositioned {
                                        itemPos = it.positionInWindow()
                                        docItemWidth = it.size.width
                                    }
                                    .pointerInput(doc.id) {
                                        val handler = android.os.Handler(
                                            android.os.Looper.getMainLooper()
                                        )
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            val startPos = itemPos + down.position
                                            var moved = false
                                            var longPressed = false
                                            val runnable = Runnable {
                                                longPressed = true
                                                haptic.performHapticFeedback(
                                                    HapticFeedbackType.LongPress
                                                )
                                                contextPdf = doc
                                                contextFolder = null
                                                dragItem = DragItem(
                                                    doc.id, false, doc.displayName
                                                )
                                                dragPosition = startPos
                                                hasDragMoved = false
                                            }
                                            handler.postDelayed(
                                                runnable,
                                                UxConfig.Gesture.LONG_PRESS_HOME_MS
                                            )
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull() ?: break
                                                if (change.changedToUp()) {
                                                    handler.removeCallbacks(runnable)
                                                    if (longPressed && moved) {
                                                        val target = findDropTarget(doc.id, false)
                                                        if (target != null) {
                                                            viewModel.moveDocument(
                                                                doc.id,
                                                                target
                                                            )
                                                        }
                                                        contextPdf = null
                                                    } else if (!longPressed && !moved) {
                                                        if (isMultiSelect) {
                                                            viewModel.toggleSelect(doc.id)
                                                        } else {
                                                            onOpenPdf(doc)
                                                        }
                                                    }
                                                    clearDrag()
                                                    break
                                                }
                                                if (longPressed) {
                                                    val drag = change.positionChange()
                                                    val moved2 = drag.getDistance() >
                                                        UxConfig.Gesture.DRAG_THRESHOLD_PX
                                                    if (moved2) {
                                                        if (!moved) contextPdf = null
                                                        moved = true
                                                        hasDragMoved = true
                                                    }
                                                    dragPosition += drag
                                                    change.consume()
                                                }
                                            }
                                        }
                                    }
                            )
                            DropdownMenu(
                                expanded = contextPdf?.id == doc.id,
                                onDismissRequest = { contextPdf = null },
                                offset = DpOffset(
                                    with(density) { docItemWidth.toDp() },
                                    0.dp
                                )
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (doc.isPinned) {
                                                "고정 해제"
                                            } else {
                                                "고정"
                                            }
                                        )
                                    },
                                    onClick = {
                                        viewModel.togglePin(doc.id)
                                        contextPdf = null
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.PushPin,
                                            contentDescription = null,
                                            tint = MaestroOnSurfaceVariant
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("선택") },
                                    onClick = {
                                        viewModel.toggleSelect(doc.id)
                                        contextPdf = null
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaestroPrimary
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("이름 변경") },
                                    onClick = {
                                        showRenameDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = null,
                                            tint = MaestroOnSurfaceVariant
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("복사") },
                                    onClick = {
                                        contextPdf?.let {
                                            viewModel.duplicateDocument(
                                                it.id
                                            )
                                        }
                                        contextPdf = null
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            tint = MaestroOnSurfaceVariant
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("이동") },
                                    onClick = {
                                        showMoveDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.DriveFileMove,
                                            contentDescription = null,
                                            tint = MaestroOnSurfaceVariant
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "삭제",
                                            color = MaestroError
                                        )
                                    },
                                    onClick = {
                                        showDeleteConfirm = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaestroError
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating drag indicator
        if (dragItem != null && hasDragMoved) {
            val localX = dragPosition.x - rootOffset.x
            val localY = dragPosition.y - rootOffset.y
            Box(
                modifier = Modifier
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            (localX - 60).toInt(),
                            (localY - 30).toInt()
                        )
                    }
                    .shadow(
                        UxConfig.Home.DRAG_SHADOW,
                        RoundedCornerShape(UxConfig.Home.DRAG_CORNER)
                    )
                    .background(Color.White, RoundedCornerShape(UxConfig.Home.DRAG_CORNER))
                    .padding(
                        horizontal = UxConfig.Home.DRAG_PADDING_H,
                        vertical = UxConfig.Home.DRAG_PADDING_V
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(UxConfig.Home.DRAG_ICON_SPACING)
                ) {
                    Icon(
                        if (dragItem!!.isFolder) {
                            Icons.Default.Folder
                        } else {
                            Icons.Default.PictureAsPdf
                        },
                        contentDescription = null,
                        modifier = Modifier.size(UxConfig.Home.DRAG_ICON_SIZE),
                        tint = if (dragItem!!.isFolder) MaestroPrimary else Slate500
                    )
                    Text(
                        dragItem!!.label,
                        fontSize = UxConfig.Home.DRAG_TEXT_SIZE,
                        fontWeight = FontWeight.Medium,
                        color = MaestroOnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // FAB
        var showFabMenu by remember { mutableStateOf(false) }
        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(UxConfig.Home.FAB_PADDING)) {
            FloatingActionButton(
                onClick = { showFabMenu = true },
                shape = CircleShape,
                containerColor = MaestroPrimary,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = UxConfig.Home.FAB_ELEVATION
                )
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "추가",
                    modifier = Modifier.size(UxConfig.Home.FAB_ICON_SIZE)
                )
            }

            DropdownMenu(
                expanded = showFabMenu,
                onDismissRequest = { showFabMenu = false },
                modifier = Modifier.background(
                    Color.White,
                    RoundedCornerShape(UxConfig.Home.FAB_MENU_CORNER)
                )
            ) {
                DropdownMenuItem(
                    text = { Text("PDF 파일 업로드", fontSize = UxConfig.Home.FAB_MENU_FONT_SIZE) },
                    onClick = {
                        showFabMenu = false
                        pdfPickerLauncher.launch(arrayOf("application/pdf"))
                    },
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Filled.InsertDriveFile,
                            contentDescription = null,
                            tint = MaestroPrimary
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("새 폴더 만들기", fontSize = UxConfig.Home.FAB_MENU_FONT_SIZE) },
                    onClick = {
                        showFabMenu = false
                        showCreateFolderDialog = true
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.CreateNewFolder,
                            contentDescription = null,
                            tint = MaestroPrimary
                        )
                    }
                )
            }
        }

        // Multi-select bottom bar
        if (isMultiSelect) {
            val count = selectedDocIds.size
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(UxConfig.Home.MERGE_BAR_MARGIN)
                    .fillMaxWidth()
                    .height(UxConfig.Home.MERGE_BAR_HEIGHT)
                    .shadow(
                        UxConfig.Home.ITEM_SHADOW,
                        RoundedCornerShape(
                            UxConfig.Home.MERGE_BAR_CORNER
                        )
                    )
                    .background(
                        Color.White,
                        RoundedCornerShape(
                            UxConfig.Home.MERGE_BAR_CORNER
                        )
                    )
                    .padding(
                        horizontal =
                        UxConfig.Home.MERGE_BAR_PADDING_H
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement =
                Arrangement.SpaceBetween
            ) {
                Text(
                    "${count}개 선택됨",
                    fontSize = UxConfig.Home.MERGE_FONT_SIZE,
                    fontWeight = FontWeight.Medium,
                    color = MaestroOnSurface
                )
                Row(
                    horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            viewModel.clearSelection()
                        }
                    ) {
                        Text(
                            "취소",
                            fontSize =
                            UxConfig.Home.MERGE_FONT_SIZE,
                            color = Slate500
                        )
                    }
                    Button(
                        onClick = {
                            showMergeOrderDialog = true
                        },
                        enabled = count >= 2,
                        shape = RoundedCornerShape(
                            UxConfig.Home.MERGE_BUTTON_CORNER
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaestroPrimary
                        ),
                        contentPadding = PaddingValues(
                            horizontal =
                            UxConfig.Home
                                .MERGE_BUTTON_PADDING_H,
                            vertical =
                            UxConfig.Home
                                .MERGE_BUTTON_PADDING_V
                        )
                    ) {
                        @Suppress("DEPRECATION")
                        Icon(
                            Icons.Default.MergeType,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "병합",
                            fontSize =
                            UxConfig.Home.MERGE_FONT_SIZE,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ── Dialogs ─────────────────────────────────────────

@Composable
private fun RenameDialog(currentName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이름 변경", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(UxConfig.Home.DIALOG_TEXT_FIELD_CORNER)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onRename(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("변경", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun CreateFolderDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 폴더", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("폴더 이름") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(UxConfig.Home.DIALOG_TEXT_FIELD_CORNER)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("만들기", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

// ── Top bar ─────────────────────────────────────────

@Composable
private fun HomeTopBar(
    currentFolderName: String?,
    onBack: (() -> Unit)?,
    onOpenSettings: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(
            UxConfig.Home.TOP_BAR_HEIGHT
        ).background(Slate50).padding(horizontal = UxConfig.Home.TOP_BAR_PADDING_H),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로",
                    tint = Maestro900
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = currentFolderName ?: "Maestro",
                fontSize = UxConfig.Home.FOLDER_NAME_FONT_SIZE,
                fontWeight = FontWeight.Bold,
                color = Maestro900,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(Modifier.width(12.dp))
            Text(
                "Maestro",
                fontSize = UxConfig.Home.LOGO_FONT_SIZE,
                fontWeight = FontWeight.Black,
                color = Maestro900,
                letterSpacing = UxConfig.Home.LOGO_LETTER_SPACING
            )
            Spacer(Modifier.weight(1f))
        }
        Text("PDF Studio", fontSize = UxConfig.Home.SUBTITLE_FONT_SIZE, color = Slate500)
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onOpenSettings) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "설정",
                tint = Slate500
            )
        }
    }
}

// ── Empty state ─────────────────────────────────────

@Composable
private fun EmptyLibrary(isInFolder: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            if (isInFolder) Icons.Default.Folder else Icons.Default.PictureAsPdf,
            contentDescription = null,
            modifier = Modifier.size(UxConfig.Home.EMPTY_ICON_SIZE),
            tint = MaestroOutlineVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (isInFolder) "빈 폴더입니다" else "PDF 파일이 없습니다",
            fontSize = UxConfig.Home.EMPTY_TITLE_FONT_SIZE,
            fontWeight = FontWeight.SemiBold,
            color = MaestroOnSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (isInFolder) {
                "오른쪽 아래의 + 버튼을 눌러\nPDF 파일을 추가하세요"
            } else {
                "오른쪽 아래의 + 버튼을 눌러\nPDF 파일이나 폴더를 추가하세요"
            },
            fontSize = UxConfig.Home.EMPTY_DESC_FONT_SIZE,
            color = Slate500,
            textAlign = TextAlign.Center,
            lineHeight = UxConfig.Home.EMPTY_DESC_LINE_HEIGHT
        )
    }
}

// ── Grid items ──────────────────────────────────────

@Composable
private fun FolderGridItem(
    folder: Folder,
    isDropTarget: Boolean = false,
    modifier: Modifier = Modifier
) {
    val borderMod = if (isDropTarget) {
        Modifier.border(
            UxConfig.Home.ITEM_BORDER_DROP_TARGET,
            MaestroPrimary,
            RoundedCornerShape(UxConfig.Home.ITEM_CORNER)
        )
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .then(borderMod)
            .clip(RoundedCornerShape(UxConfig.Home.ITEM_CORNER))
            .shadow(
                if (isDropTarget) {
                    UxConfig.Home.ITEM_SHADOW_DROP_TARGET
                } else {
                    UxConfig.Home.ITEM_SHADOW
                },
                RoundedCornerShape(UxConfig.Home.ITEM_CORNER)
            )
            .background(
                if (isDropTarget) Maestro50 else MaestroSurfaceContainerLowest,
                RoundedCornerShape(UxConfig.Home.ITEM_CORNER)
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(UxConfig.Home.ITEM_ASPECT_RATIO)
                .background(
                    if (isDropTarget) {
                        MaestroPrimary.copy(
                            alpha = UxConfig.Home.DROP_TARGET_BG_ALPHA
                        )
                    } else {
                        Maestro50
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(UxConfig.Home.FOLDER_ICON_SIZE),
                tint = MaestroPrimary.copy(
                    alpha = if (isDropTarget) {
                        UxConfig.Home.FOLDER_ICON_ALPHA_DROP
                    } else {
                        UxConfig.Home.FOLDER_ICON_ALPHA
                    }
                )
            )
        }
        Column(modifier = Modifier.fillMaxWidth().padding(UxConfig.Home.ITEM_PADDING)) {
            Text(
                folder.name,
                fontSize = UxConfig.Home.ITEM_NAME_FONT_SIZE,
                fontWeight = FontWeight.SemiBold,
                color = MaestroOnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = UxConfig.Home.ITEM_NAME_LINE_HEIGHT
            )
            Spacer(Modifier.height(4.dp))
            Text("폴더", fontSize = UxConfig.Home.ITEM_META_FONT_SIZE, color = Slate500)
        }
    }
}

@Composable
private fun PdfGridItem(
    doc: PdfDocument,
    isSelected: Boolean = false,
    isMultiSelectMode: Boolean = false,
    isExtracting: Boolean = false,
    modifier: Modifier = Modifier
) {
    val thumbnail = remember(doc.uriString) {
        var fd: android.os.ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        try {
            val path = Uri.parse(doc.uriString).path ?: return@remember null
            val file = java.io.File(path)
            if (!file.exists()) return@remember null
            fd = android.os.ParcelFileDescriptor.open(
                file, android.os.ParcelFileDescriptor.MODE_READ_ONLY
            )
            renderer = PdfRenderer(fd)
            page = renderer.openPage(0)
            val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        } catch (_: Throwable) {
            null
        } finally {
            try {
                page?.close()
            } catch (_: Throwable) {}
            try {
                renderer?.close()
            } catch (_: Throwable) {}
            try {
                fd?.close()
            } catch (_: Throwable) {}
        }
    }

    val selBorder = if (isSelected) {
        Modifier.border(
            UxConfig.Home.ITEM_BORDER_DROP_TARGET,
            MaestroPrimary,
            RoundedCornerShape(UxConfig.Home.ITEM_CORNER)
        )
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .then(selBorder)
            .clip(
                RoundedCornerShape(UxConfig.Home.ITEM_CORNER)
            )
            .shadow(
                UxConfig.Home.ITEM_SHADOW,
                RoundedCornerShape(UxConfig.Home.ITEM_CORNER)
            )
            .background(
                MaestroSurfaceContainerLowest,
                RoundedCornerShape(UxConfig.Home.ITEM_CORNER)
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(UxConfig.Home.ITEM_ASPECT_RATIO)
                .background(MaestroSurfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = doc.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    modifier = Modifier.size(
                        UxConfig.Home.PDF_PLACEHOLDER_ICON_SIZE
                    ),
                    tint = MaestroOutlineVariant
                )
            }
            if (doc.isPinned) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "고정됨",
                        modifier = Modifier.size(18.dp),
                        tint = MaestroPrimary
                    )
                }
            }
            if (doc.extractionStatus ==
                ExtractionStatus.DONE
            ) {
                Box(
                    modifier = Modifier
                        .align(
                            if (doc.isPinned) {
                                Alignment.BottomStart
                            } else {
                                Alignment.TopStart
                            }
                        )
                        .padding(6.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription =
                        "추출 완료",
                        modifier = Modifier
                            .size(18.dp),
                        tint = Color(0xFF10B981)
                    )
                }
            }
            if (isExtracting) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaestroPrimary
                    )
                }
            }
            if (isMultiSelectMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(UxConfig.Home.CHECKBOX_OFFSET)
                ) {
                    Icon(
                        imageVector = if (isSelected) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.RadioButtonUnchecked
                        },
                        contentDescription = null,
                        modifier = Modifier.size(
                            UxConfig.Home.CHECKBOX_SIZE
                        ),
                        tint = if (isSelected) {
                            MaestroPrimary
                        } else {
                            Slate500
                        }
                    )
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(UxConfig.Home.ITEM_PADDING)) {
            Text(
                doc.displayName,
                fontSize = UxConfig.Home.ITEM_NAME_FONT_SIZE,
                fontWeight = FontWeight.SemiBold,
                color = MaestroOnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = UxConfig.Home.ITEM_NAME_LINE_HEIGHT
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${doc.pageCount}페이지",
                fontSize = UxConfig.Home.ITEM_META_FONT_SIZE,
                color = Slate500
            )
        }
    }
}

// ── Move Picker Dialog ─────────────────────────────

@Composable
private fun MovePickerDialog(
    allFolders: List<Folder>,
    excludeFolderId: String?,
    itemLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (targetFolderId: String?) -> Unit
) {
    // Collect excluded IDs (the folder itself + all descendants)
    val excludedIds = remember(excludeFolderId, allFolders) {
        if (excludeFolderId == null) {
            emptySet()
        } else {
            val set = mutableSetOf(excludeFolderId)
            fun collectChildren(parentId: String) {
                allFolders.filter { it.parentId == parentId }.forEach {
                    set += it.id
                    collectChildren(it.id)
                }
            }
            collectChildren(excludeFolderId)
            set
        }
    }

    var browseFolderId by remember { mutableStateOf<String?>(null) }

    // Build breadcrumb path
    val breadcrumbs = remember(browseFolderId, allFolders) {
        val path = mutableListOf<Pair<String?, String>>() // id to name
        path += (null to "PDF Studio")
        if (browseFolderId != null) {
            val chain = mutableListOf<Folder>()
            var cur = browseFolderId
            while (cur != null) {
                val f = allFolders.find { it.id == cur } ?: break
                chain += f
                cur = f.parentId
            }
            chain.reversed().forEach { path += (it.id to it.name) }
        }
        path
    }

    val visibleFolders = allFolders.filter {
        it.parentId == browseFolderId && it.id !in excludedIds
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().heightIn(
            min = UxConfig.Home.MOVE_DIALOG_MIN_HEIGHT,
            max = UxConfig.Home.MOVE_DIALOG_MAX_HEIGHT
        ),
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "\"$itemLabel\" 이동",
                        fontWeight = FontWeight.Bold,
                        fontSize = UxConfig.Home.MOVE_DIALOG_TITLE_FONT_SIZE,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onConfirm(browseFolderId) }) {
                        Text("이동", fontWeight = FontWeight.Bold, color = MaestroPrimary)
                    }
                }
                // Breadcrumb
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    breadcrumbs.forEachIndexed { index, (id, name) ->
                        if (index > 0) {
                            Text(
                                " > ",
                                fontSize = UxConfig.Home.MOVE_DIALOG_BREADCRUMB_FONT_SIZE,
                                color = Slate400
                            )
                        }
                        Text(
                            name,
                            fontSize = UxConfig.Home.MOVE_DIALOG_BREADCRUMB_FONT_SIZE,
                            color = if (id == browseFolderId) MaestroPrimary else Slate500,
                            fontWeight = if (id == browseFolderId) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                            modifier = Modifier.clickable { browseFolderId = id },
                            maxLines = 1
                        )
                    }
                }
            }
        },
        text = {
            if (visibleFolders.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(
                        UxConfig.Home.MOVE_DIALOG_EMPTY_HEIGHT
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(UxConfig.Home.MOVE_DIALOG_EMPTY_ICON_SIZE),
                            tint = MaestroOutlineVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "하위 폴더 없음",
                            fontSize = UxConfig.Home.MOVE_DIALOG_EMPTY_TITLE_FONT_SIZE,
                            color = Slate500
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "현재 위치에 이동하려면 '이동' 버튼을 누르세요",
                            fontSize = UxConfig.Home.MOVE_DIALOG_EMPTY_DESC_FONT_SIZE,
                            color = Slate400,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(
                        UxConfig.Home.MOVE_DIALOG_LIST_SPACING
                    )
                ) {
                    items(visibleFolders, key = { it.id }) { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(UxConfig.Home.MOVE_DIALOG_ROW_CORNER))
                                .clickable { browseFolderId = folder.id }
                                .background(
                                    MaestroSurfaceContainer.copy(
                                        alpha = UxConfig.Home.MOVE_DIALOG_ROW_BG_ALPHA
                                    ),
                                    RoundedCornerShape(UxConfig.Home.MOVE_DIALOG_ROW_CORNER)
                                )
                                .padding(
                                    horizontal = UxConfig.Home.MOVE_DIALOG_ROW_PADDING_H,
                                    vertical = UxConfig.Home.MOVE_DIALOG_ROW_PADDING_V
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(
                                UxConfig.Home.MOVE_DIALOG_ROW_SPACING
                            )
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(UxConfig.Home.MOVE_DIALOG_ICON_SIZE),
                                tint = MaestroPrimary.copy(
                                    alpha = UxConfig.Home.MOVE_DIALOG_ICON_ALPHA
                                )
                            )
                            Text(
                                folder.name,
                                fontSize = UxConfig.Home.MOVE_DIALOG_FONT_SIZE,
                                fontWeight = FontWeight.Medium,
                                color = MaestroOnSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(UxConfig.Home.MOVE_DIALOG_CHEVRON_SIZE),
                                tint = Slate400
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

// ── Merge Order Dialog ───────────────────────────────

@Composable
private fun MergeOrderDialog(
    docs: List<PdfDocument>,
    onDismiss: () -> Unit,
    onConfirm: (orderedIds: List<String>) -> Unit
) {
    val orderedDocs = remember(docs) {
        mutableStateListOf(*docs.toTypedArray())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "병합 순서",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                verticalArrangement =
                Arrangement.spacedBy(4.dp)
            ) {
                items(orderedDocs.size) { index ->
                    val doc = orderedDocs[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Slate50,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(
                                horizontal = 12.dp,
                                vertical = 8.dp
                            ),
                        verticalAlignment =
                        Alignment.CenterVertically
                    ) {
                        Text(
                            "${index + 1}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaestroPrimary,
                            modifier = Modifier.width(
                                24.dp
                            )
                        )
                        Text(
                            doc.displayName,
                            fontSize = 14.sp,
                            color = MaestroOnSurface,
                            maxLines = 1,
                            overflow =
                            TextOverflow.Ellipsis,
                            modifier = Modifier.weight(
                                1f
                            )
                        )
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    val item =
                                        orderedDocs
                                            .removeAt(
                                                index
                                            )
                                    orderedDocs.add(
                                        index - 1,
                                        item
                                    )
                                }
                            },
                            enabled = index > 0,
                            modifier = Modifier.size(
                                32.dp
                            )
                        ) {
                            Icon(
                                Icons.Default
                                    .KeyboardArrowUp,
                                contentDescription =
                                "위로",
                                tint = if (index > 0) {
                                    Slate500
                                } else {
                                    Slate200
                                }
                            )
                        }
                        IconButton(
                            onClick = {
                                if (
                                    index <
                                    orderedDocs
                                        .size - 1
                                ) {
                                    val item =
                                        orderedDocs
                                            .removeAt(
                                                index
                                            )
                                    orderedDocs.add(
                                        index + 1,
                                        item
                                    )
                                }
                            },
                            enabled = index <
                                orderedDocs.size - 1,
                            modifier = Modifier.size(
                                32.dp
                            )
                        ) {
                            Icon(
                                Icons.Default
                                    .KeyboardArrowDown,
                                contentDescription =
                                "아래로",
                                tint = if (
                                    index <
                                    orderedDocs
                                        .size - 1
                                ) {
                                    Slate500
                                } else {
                                    Slate200
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        orderedDocs.map { it.id }
                    )
                }
            ) {
                Text(
                    "병합",
                    color = MaestroPrimary,
                    fontWeight = FontWeight.Bold
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
