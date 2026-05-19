package com.maestro.app.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maestro.app.data.local.PdfTextIndex
import com.maestro.app.data.local.PdfTextIndexPage
import com.maestro.app.data.local.PdfTextIndexWord
import com.maestro.app.domain.model.CropCapturePhase
import com.maestro.app.domain.model.CropCapturePayload
import com.maestro.app.domain.model.DrawingTool
import com.maestro.app.domain.model.InkStroke
import com.maestro.app.domain.model.LassoPhase
import com.maestro.app.domain.model.PdfSearchMatch
import com.maestro.app.domain.model.SelectedTextQuizPayload
import com.maestro.app.domain.model.StrokePoint
import com.maestro.app.ui.config.UxConfig
import com.maestro.app.ui.drawing.DrawingState
import com.maestro.app.ui.theme.MaestroOnSurface
import com.maestro.app.ui.theme.MaestroPrimary
import com.maestro.app.ui.theme.MaestroSurfaceContainer
import com.maestro.app.ui.theme.MaestroSurfaceContainerHigh
import com.maestro.app.ui.theme.MaestroSurfaceContainerLow
import com.maestro.app.ui.theme.MaestroSurfaceContainerLowest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

/**
 * Serializable data classes for clipboard stroke JSON
 * interchange.
 */
@Serializable
private data class ClipboardPoint(
    val x: Double,
    val y: Double,
    val p: Double
)

@Serializable
private data class ClipboardStroke(
    val pts: List<ClipboardPoint>,
    val color: Int,
    val width: Double
)

private val clipboardJson = Json {
    ignoreUnknownKeys = true
}

private val SearchHighlightFill = Color(0xFFFFD54F)
    .copy(alpha = 0.42f)
private val SearchHighlightStroke = Color(0xFFFFA000)
    .copy(alpha = 0.72f)
private val ActiveSearchHighlightFill = Color(0xFFFFF176)
    .copy(alpha = 0.68f)
private val ActiveSearchHighlightStroke = MaestroPrimary
    .copy(alpha = 0.88f)
private val TextSelectionFill = Color(0xFFFFE082)
    .copy(alpha = 0.58f)
private val TextSelectionStroke = MaestroPrimary
    .copy(alpha = 0.85f)
private const val TextSelectionVisualYOffsetRatio = 0.28f

private data class TextSelectionState(
    val pageIndex: Int,
    val startWordIndex: Int,
    val endWordIndex: Int,
    val menuOffset: Offset,
    val isDragging: Boolean = false
)

@Composable
fun CanvasSection(
    pdfUri: Uri?,
    pageCount: Int,
    drawingState: DrawingState,
    modifier: Modifier = Modifier,
    viewportKey: Any? = pdfUri,
    initialFirstVisiblePageIndex: Int = 0,
    initialFirstVisiblePageScrollOffset: Int = 0,
    searchMatches: List<PdfSearchMatch> = emptyList(),
    activeSearchMatch: PdfSearchMatch? = null,
    searchNavigationRequest: Int = 0,
    textIndex: PdfTextIndex? = null,
    onScrollPositionChanged: (pageIndex: Int, scrollOffset: Int) -> Unit = { _, _ -> },
    onCropLlm: ((CropCapturePayload) -> Unit)? = null,
    onCropQuiz: ((CropCapturePayload) -> Unit)? = null,
    onTextQuiz: ((SelectedTextQuizPayload) -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val listState = remember(viewportKey) {
        LazyListState(
            initialFirstVisiblePageIndex
                .coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
            initialFirstVisiblePageScrollOffset.coerceAtLeast(0)
        )
    }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(listState, pageCount) {
        snapshotFlow {
            listState.firstVisibleItemIndex to
                listState.firstVisibleItemScrollOffset
        }.distinctUntilChanged()
            .collect { (pageIndex, scrollOffset) ->
                val safePageIndex = pageIndex
                    .coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                drawingState.activePageIndex = safePageIndex
                onScrollPositionChanged(
                    safePageIndex,
                    scrollOffset.coerceAtLeast(0)
                )
            }
    }

    // Paste popup state
    var showPasteMenu by remember { mutableStateOf(false) }
    var pastePageIndex by remember { mutableIntStateOf(0) }
    var pasteTapPosition by remember {
        mutableStateOf(Offset.Zero)
    }
    var pasteMenuOffset by remember {
        mutableStateOf(DpOffset.Zero)
    }
    var textSelection by remember(viewportKey) {
        mutableStateOf<TextSelectionState?>(null)
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(MaestroSurfaceContainerLow)
            .clipToBounds()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    focusManager.clearFocus(force = true)
                    do {
                        val event = awaitPointerEvent()
                        val blocked =
                            drawingState.isCropping ||
                                drawingState.selectedImage != null
                        if (
                            !blocked &&
                            event.changes.size >= 2
                        ) {
                            val zoomChange =
                                event.calculateZoom()
                            val panChange =
                                event.calculatePan()
                            if (
                                zoomChange != 1f ||
                                panChange != Offset.Zero
                            ) {
                                val newZoom =
                                    (
                                        drawingState.zoomScale *
                                            zoomChange
                                        )
                                        .coerceIn(
                                            UxConfig.Canvas.ZOOM_MIN,
                                            UxConfig.Canvas.ZOOM_MAX
                                        )
                                drawingState.zoomScale =
                                    newZoom
                                if (newZoom > 1f) {
                                    panOffset += panChange
                                } else {
                                    panOffset = Offset.Zero
                                }
                                event.changes.forEach {
                                    it.consume()
                                }
                            }
                        } else if (
                            !blocked &&
                            event.changes.size == 1 &&
                            drawingState.zoomScale > 1f
                        ) {
                            val change = event.changes[0]
                            val drag =
                                change.position -
                                    change.previousPosition
                            val absX =
                                kotlin.math.abs(drag.x)
                            val absY =
                                kotlin.math.abs(drag.y)
                            if (
                                absX > absY * UxConfig.Canvas.PAN_THRESHOLD_MULT &&
                                absX > UxConfig.Canvas.PAN_MIN_DISTANCE
                            ) {
                                val maxPanX =
                                    size.width *
                                        (
                                            drawingState
                                                .zoomScale -
                                                1f
                                            ) / 2f
                                panOffset = Offset(
                                    (panOffset.x + drag.x)
                                        .coerceIn(
                                            -maxPanX,
                                            maxPanX
                                        ),
                                    panOffset.y
                                )
                                change.consume()
                            }
                        }
                    } while (
                        event.changes.any { it.pressed }
                    )
                }
            }
    ) {
        // Handle S Pen long-press paste request
        if (drawingState.penPasteRequest != null) {
            pastePageIndex =
                drawingState.penPastePageIndex
            pasteTapPosition =
                drawingState.penPasteRequest!!
            val screenPos =
                drawingState.penPasteScreenPos
                    ?: pasteTapPosition
            pasteMenuOffset = DpOffset(
                (screenPos.x / density.density).dp,
                (screenPos.y / density.density).dp
            )
            showPasteMenu = true
            drawingState.penPasteRequest = null
            drawingState.penPasteScreenPos = null
        }

        if (pdfUri != null && pageCount > 0) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val viewHeight = maxHeight
                val viewWidth = maxWidth
                LaunchedEffect(
                    searchNavigationRequest,
                    activeSearchMatch,
                    pdfUri,
                    pageCount,
                    viewWidth,
                    viewHeight
                ) {
                    val match = activeSearchMatch
                        ?: return@LaunchedEffect
                    if (pageCount <= 0) {
                        return@LaunchedEffect
                    }
                    val pageIndex = match.pageIndex
                        .coerceIn(0, pageCount - 1)
                    val aspectRatio = readPageAspectRatio(
                        context,
                        pdfUri,
                        pageIndex
                    )
                    val viewWidthPx = with(density) {
                        viewWidth.toPx()
                    }
                    val viewHeightPx = with(density) {
                        viewHeight.toPx()
                    }
                    val defaultPadPx = with(density) {
                        UxConfig.Canvas.PAGE_DEFAULT_HORIZONTAL_PADDING
                            .toPx()
                    }
                    val minPadPx = with(density) {
                        UxConfig.Canvas.PAGE_MIN_HORIZONTAL_PADDING
                            .toPx()
                    }
                    val pageHeightAtFullWidth =
                        viewWidthPx / aspectRatio
                    val horizontalPadPx = if (
                        pageHeightAtFullWidth >= viewHeightPx
                    ) {
                        ((viewWidthPx - viewHeightPx * aspectRatio) / 2f)
                            .coerceAtLeast(minPadPx)
                    } else {
                        defaultPadPx
                    }
                    val pageWidthPx = (viewWidthPx -
                        horizontalPadPx * 2f)
                        .coerceAtLeast(1f)
                    val pageHeightPx = pageWidthPx / aspectRatio
                    val matchCenterY =
                        ((match.top + match.bottom) / 2f) /
                            match.pageHeight.coerceAtLeast(1f) *
                            pageHeightPx
                    val scrollOffset = (matchCenterY -
                        viewHeightPx * 0.32f)
                        .coerceAtLeast(0f)
                        .toInt()
                    drawingState.activePageIndex = pageIndex
                    listState.animateScrollToItem(
                        index = pageIndex,
                        scrollOffset = scrollOffset
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX =
                            drawingState.zoomScale,
                            scaleY =
                            drawingState.zoomScale,
                            translationX = panOffset.x,
                            translationY = panOffset.y
                        ),
                    userScrollEnabled =
                    !drawingState.isStylusTouching &&
                        !drawingState.isCropping,
                    contentPadding =
                    PaddingValues(vertical = UxConfig.Canvas.PAGE_VERTICAL_PADDING),
                    verticalArrangement =
                    Arrangement.spacedBy(UxConfig.Canvas.PAGE_VERTICAL_SPACING),
                    horizontalAlignment =
                    Alignment.CenterHorizontally
                ) {
                    items(pageCount) { pageIndex ->
                        val aspectRatio =
                            getPageAspectRatioSync(
                                context,
                                pdfUri,
                                pageIndex
                            )
                        val pageHeightAtFullWidth =
                            viewWidth / aspectRatio
                        val horizontalPad =
                            if (
                                pageHeightAtFullWidth >=
                                viewHeight
                            ) {
                                val targetWidth =
                                    viewHeight *
                                        aspectRatio
                                (
                                    (viewWidth - targetWidth) /
                                        2
                                    )
                                    .coerceAtLeast(UxConfig.Canvas.PAGE_MIN_HORIZONTAL_PADDING)
                            } else {
                                UxConfig.Canvas.PAGE_DEFAULT_HORIZONTAL_PADDING
                            }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal =
                                    horizontalPad
                                )
                                .clipToBounds()
                                .pointerInput(
                                    pageIndex,
                                    textIndex,
                                    onTextQuiz
                                ) {
                                    awaitEachGesture {
                                        val down =
                                            awaitFirstDown(
                                                requireUnconsumed = false
                                            )
                                        val longPress =
                                            awaitLongPressOrCancellation(
                                                down.id
                                            ) ?: return@awaitEachGesture
                                        val blocked =
                                            drawingState.isCropping ||
                                                drawingState.activeTool ==
                                                DrawingTool.CROP_CAPTURE ||
                                                drawingState.selectedImage !=
                                                null
                                        if (blocked) {
                                            return@awaitEachGesture
                                        }
                                        val pageText =
                                            textIndex
                                                ?.pages
                                                ?.firstOrNull {
                                                    it.pageIndex ==
                                                        pageIndex
                                                }
                                        val startWord =
                                            pageText?.let {
                                                hitWordIndexAtPosition(
                                                    page = it,
                                                    position =
                                                    longPress.position,
                                                    pageWidthPx =
                                                    size.width
                                                        .toFloat(),
                                                    pageHeightPx =
                                                    size.height
                                                        .toFloat()
                                                )
                                            }
                                        if (
                                            startWord != null &&
                                            onTextQuiz != null
                                        ) {
                                            showPasteMenu = false
                                            textSelection =
                                                TextSelectionState(
                                                    pageIndex = pageIndex,
                                                    startWordIndex =
                                                    startWord,
                                                    endWordIndex =
                                                    startWord,
                                                    menuOffset =
                                                    longPress.position,
                                                    isDragging = true
                                                )
                                            longPress.consume()
                                            do {
                                                val event =
                                                    awaitPointerEvent()
                                                val change =
                                                    event.changes
                                                        .firstOrNull {
                                                            it.id == down.id
                                                        }
                                                if (change != null) {
                                                    val endWord =
                                                        nearestWordIndexAtPosition(
                                                            page = pageText,
                                                            position =
                                                            change.position,
                                                            pageWidthPx =
                                                            size.width
                                                                .toFloat(),
                                                            pageHeightPx =
                                                            size.height
                                                                .toFloat()
                                                        )
                                                    if (endWord != null) {
                                                        textSelection =
                                                            textSelection
                                                                ?.copy(
                                                                    endWordIndex =
                                                                    endWord,
                                                                    menuOffset =
                                                                    change
                                                                        .position,
                                                                    isDragging =
                                                                    change
                                                                        .pressed
                                                                )
                                                    }
                                                    change.consume()
                                                }
                                            } while (
                                                event.changes.any {
                                                    it.pressed
                                                }
                                            )
                                            textSelection =
                                                textSelection?.copy(
                                                    isDragging = false
                                                )
                                        } else {
                                            onPageLongPress(
                                                drawingState,
                                                pageIndex,
                                                longPress.position,
                                                size.width,
                                                aspectRatio,
                                                density.density
                                            ) { pi, tap, menu ->
                                                textSelection = null
                                                pastePageIndex = pi
                                                pasteTapPosition = tap
                                                pasteMenuOffset = menu
                                                showPasteMenu = true
                                            }
                                        }
                                    }
                                }
                        ) {
                            PdfPageView(
                                uri = pdfUri,
                                pageIndex = pageIndex,
                                modifier =
                                Modifier.fillMaxWidth()
                            )
                            val pageSearchMatches =
                                searchMatches.filter {
                                    it.pageIndex == pageIndex
                                }
                            if (pageSearchMatches.isNotEmpty()) {
                                SearchHighlightOverlay(
                                    matches = pageSearchMatches,
                                    activeMatch = activeSearchMatch
                                        ?.takeIf {
                                            it.pageIndex == pageIndex
                                        },
                                    modifier = Modifier
                                        .matchParentSize()
                                )
                            }
                            val pageText =
                                textIndex?.pages?.firstOrNull {
                                    it.pageIndex == pageIndex
                                }
                            val currentTextSelection =
                                textSelection?.takeIf {
                                    it.pageIndex == pageIndex
                                }
                            if (
                                pageText != null &&
                                currentTextSelection != null
                            ) {
                                TextSelectionOverlay(
                                    page = pageText,
                                    selection = currentTextSelection,
                                    modifier = Modifier
                                        .matchParentSize()
                                )
                            }
                            StylusDrawingCanvas(
                                state = drawingState,
                                pageIndex = pageIndex,
                                modifier =
                                Modifier
                                    .matchParentSize(),
                                onCropLlm =
                                onCropLlm,
                                onCropQuiz =
                                onCropQuiz
                            )

                            if (
                                pageText != null &&
                                currentTextSelection != null &&
                                !currentTextSelection.isDragging &&
                                onTextQuiz != null
                            ) {
                                val selectedText =
                                    selectedTextFor(
                                        pageText,
                                        currentTextSelection
                                    )
                                TextSelectionActionMenu(
                                    selection = currentTextSelection,
                                    selectedText = selectedText,
                                    pageIndex = pageIndex,
                                    onDismiss = {
                                        textSelection = null
                                    },
                                    onQuiz = {
                                        if (selectedText.isNotBlank()) {
                                            onTextQuiz(
                                                SelectedTextQuizPayload(
                                                    text = selectedText,
                                                    pageIndex = pageIndex,
                                                    label =
                                                    textQuizLabel(
                                                        selectedText,
                                                        pageIndex
                                                    )
                                                )
                                            )
                                        }
                                        textSelection = null
                                    }
                                )
                            }

                            // Long-press action menu
                            if (
                                showPasteMenu &&
                                pastePageIndex == pageIndex
                            ) {
                                PageActionMenu(
                                    pasteMenuOffset =
                                    pasteMenuOffset,
                                    aspectRatio =
                                    aspectRatio,
                                    pasteTapPosition =
                                    pasteTapPosition,
                                    drawingState =
                                    drawingState,
                                    pageIndex = pageIndex,
                                    context = context,
                                    onDismiss = {
                                        showPasteMenu =
                                            false
                                    }
                                )
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(UxConfig.Canvas.PAGE_VERTICAL_SPACING))
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(UxConfig.Canvas.PLACEHOLDER_PADDING),
                contentAlignment =
                Alignment.TopCenter
            ) {
                PlaceholderCanvas()
            }
        }

        // Crop capture hint
        if (drawingState.activeTool ==
            DrawingTool.CROP_CAPTURE &&
            drawingState.cropCapturePhase ==
            CropCapturePhase.IDLE
        ) {
            Text(
                "캡처하실 영역을 선택하십시오",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .background(
                        MaestroOnSurface
                            .copy(alpha = 0.7f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(
                        horizontal = 16.dp,
                        vertical = 8.dp
                    ),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaestroSurfaceContainerLowest
            )
        }

        // Zoom indicator
        if (drawingState.zoomScale > UxConfig.Canvas.ZOOM_INDICATOR_THRESHOLD) {
            val pct =
                (drawingState.zoomScale * 100).toInt()
            Text(
                "$pct%",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .background(
                        MaestroOnSurface
                            .copy(alpha = UxConfig.Canvas.ZOOM_INDICATOR_BG_ALPHA),
                        RoundedCornerShape(UxConfig.Canvas.ZOOM_INDICATOR_CORNER)
                    )
                    .padding(
                        horizontal = UxConfig.Canvas.ZOOM_INDICATOR_PADDING_H,
                        vertical = UxConfig.Canvas.ZOOM_INDICATOR_PADDING_V
                    ),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaestroSurfaceContainerLowest
            )
        }
    }
}

@Composable
private fun SearchHighlightOverlay(
    matches: List<PdfSearchMatch>,
    activeMatch: PdfSearchMatch? = null,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        matches.forEach { match ->
            if (match != activeMatch) {
                drawSearchHighlight(
                    match = match,
                    fill = SearchHighlightFill,
                    stroke = SearchHighlightStroke,
                    strokeWidth = 1.2.dp.toPx()
                )
            }
        }
        activeMatch?.let { match ->
            drawSearchHighlight(
                match = match,
                fill = ActiveSearchHighlightFill,
                stroke = ActiveSearchHighlightStroke,
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSearchHighlight(
    match: PdfSearchMatch,
    fill: Color,
    stroke: Color,
    strokeWidth: Float
) {
            val scaleX = size.width /
                match.pageWidth.coerceAtLeast(1f)
            val scaleY = size.height /
                match.pageHeight.coerceAtLeast(1f)
            val left = match.left * scaleX
            val top = match.top * scaleY
            val right = match.right * scaleX
            val bottom = match.bottom * scaleY
            val width = (right - left).coerceAtLeast(4f)
            val height = (bottom - top).coerceAtLeast(8f)
            val corner = 3.dp.toPx()
            drawRoundRect(
                color = fill,
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = CornerRadius(corner, corner)
            )
            drawRoundRect(
                color = stroke,
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = CornerRadius(corner, corner),
                style = Stroke(width = strokeWidth)
            )
}

@Composable
private fun TextSelectionOverlay(
    page: PdfTextIndexPage,
    selection: TextSelectionState,
    modifier: Modifier = Modifier
) {
    val words = selectedWordsFor(page, selection)
    Canvas(modifier = modifier) {
        words.forEach { word ->
            val scaleX = size.width /
                page.width.coerceAtLeast(1f)
            val scaleY = size.height /
                page.height.coerceAtLeast(1f)
            val rect = word.visualRect(page)
            val left = rect.left * scaleX
            val top = rect.top * scaleY
            val right = rect.right * scaleX
            val bottom = rect.bottom * scaleY
            val width = (right - left).coerceAtLeast(4f)
            val height = (bottom - top).coerceAtLeast(8f)
            val corner = 3.dp.toPx()
            drawRoundRect(
                color = TextSelectionFill,
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = CornerRadius(corner, corner)
            )
            drawRoundRect(
                color = TextSelectionStroke,
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = CornerRadius(corner, corner),
                style = Stroke(width = 1.4.dp.toPx())
            )
        }
    }
}

@Composable
private fun TextSelectionActionMenu(
    selection: TextSelectionState,
    selectedText: String,
    pageIndex: Int,
    onDismiss: () -> Unit,
    onQuiz: () -> Unit
) {
    val x = selection.menuOffset.x
        .coerceAtLeast(8f)
        .roundToInt()
    val y = (selection.menuOffset.y - 76f)
        .coerceAtLeast(8f)
        .roundToInt()
    Surface(
        modifier = Modifier
            .offset { IntOffset(x, y) },
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF202124),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 6.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clipToBounds()
                    .clickable(enabled = selectedText.isNotBlank()) {
                        onQuiz()
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Quiz,
                    contentDescription = null,
                    tint = MaestroSurfaceContainerLowest,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "선택 텍스트로 퀴즈 생성",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaestroSurfaceContainerLowest
                )
            }
            Text(
                "p.${pageIndex + 1}",
                fontSize = 11.sp,
                color = MaestroSurfaceContainerLowest
                    .copy(alpha = 0.78f),
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "선택 해제",
                    tint = MaestroSurfaceContainerLowest,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

private data class TextWordRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val centerX: Float
        get() = (left + right) / 2f
    val centerY: Float
        get() = (top + bottom) / 2f
    val height: Float
        get() = (bottom - top).coerceAtLeast(1f)
}

private data class PdfPoint(
    val x: Float,
    val y: Float
)

private fun PdfTextIndexWord.visualRect(
    page: PdfTextIndexPage
): TextWordRect {
    val height = (bottom - top).coerceAtLeast(1f)
    val yOffset = height * TextSelectionVisualYOffsetRatio
    val visualTop = (top - yOffset).coerceIn(0f, page.height)
    val visualBottom = (bottom - yOffset)
        .coerceAtLeast(visualTop + 1f)
        .coerceAtMost(page.height)
    return TextWordRect(
        left = left.coerceIn(0f, page.width),
        top = visualTop,
        right = right.coerceIn(left, page.width),
        bottom = visualBottom
    )
}

private fun toPdfPoint(
    page: PdfTextIndexPage,
    position: Offset,
    pageWidthPx: Float,
    pageHeightPx: Float
): PdfPoint? {
    if (
        pageWidthPx <= 0f ||
        pageHeightPx <= 0f
    ) {
        return null
    }
    return PdfPoint(
        x = position.x / pageWidthPx *
            page.width.coerceAtLeast(1f),
        y = position.y / pageHeightPx *
            page.height.coerceAtLeast(1f)
    )
}

private fun hitWordIndexAtPosition(
    page: PdfTextIndexPage,
    position: Offset,
    pageWidthPx: Float,
    pageHeightPx: Float
): Int? {
    if (
        page.words.isEmpty() ||
        pageWidthPx <= 0f ||
        pageHeightPx <= 0f
    ) {
        return null
    }
    val point = toPdfPoint(
        page,
        position,
        pageWidthPx,
        pageHeightPx
    ) ?: return null
    val averageHeight = page.words
        .map {
            it.visualRect(page).height
        }
        .average()
        .takeIf { !it.isNaN() }
        ?.toFloat()
        ?: 12f
    val xPad = averageHeight * 0.35f
    val yPad = averageHeight * 0.45f
    return page.words
        .withIndex()
        .filter { (_, word) ->
            val rect = word.visualRect(page)
            point.x >= rect.left - xPad &&
                point.x <= rect.right + xPad &&
                point.y >= rect.top - yPad &&
                point.y <= rect.bottom + yPad
        }
        .minByOrNull { (_, word) ->
            val rect = word.visualRect(page)
            val dx = point.x - rect.centerX
            val dy = point.y - rect.centerY
            dx * dx + dy * dy
        }
        ?.index
}

private fun nearestWordIndexAtPosition(
    page: PdfTextIndexPage,
    position: Offset,
    pageWidthPx: Float,
    pageHeightPx: Float
): Int? {
    if (page.words.isEmpty()) return null
    val point = toPdfPoint(
        page,
        position,
        pageWidthPx,
        pageHeightPx
    ) ?: return null
    val rows = page.words
        .withIndex()
        .map { (index, word) ->
            IndexedTextWord(
                index = index,
                word = word,
                rect = word.visualRect(page)
            )
        }
        .groupIntoVisualRows()
    val row = rows.minByOrNull { rowWords ->
        val centerY = rowWords
            .map { it.rect.centerY }
            .average()
            .toFloat()
        kotlin.math.abs(point.y - centerY)
    } ?: return null
    return row.minByOrNull { item ->
        val dx = point.x - item.rect.centerX
        val dy = point.y - item.rect.centerY
        dx * dx + dy * dy * 0.35f
    }?.index
}

private data class IndexedTextWord(
    val index: Int,
    val word: PdfTextIndexWord,
    val rect: TextWordRect
)

private fun List<IndexedTextWord>.groupIntoVisualRows():
    List<List<IndexedTextWord>> {
    if (isEmpty()) return emptyList()
    val rows = mutableListOf<MutableList<IndexedTextWord>>()
    forEach { item ->
        val target = rows.lastOrNull()
        val targetCenter = target
            ?.map { it.rect.centerY }
            ?.average()
            ?.toFloat()
        val tolerance = maxOf(
            item.rect.height,
            target?.map { it.rect.height }
                ?.average()
                ?.toFloat()
                ?: item.rect.height
        ) * 0.65f
        if (
            target == null ||
            targetCenter == null ||
            kotlin.math.abs(item.rect.centerY - targetCenter) >
            tolerance
        ) {
            rows += mutableListOf(item)
        } else {
            target += item
        }
    }
    return rows.map { row ->
        row.sortedBy { it.rect.centerX }
    }
}

private fun selectedWordsFor(
    page: PdfTextIndexPage,
    selection: TextSelectionState
): List<PdfTextIndexWord> {
    if (page.words.isEmpty()) return emptyList()
    val start = minOf(
        selection.startWordIndex,
        selection.endWordIndex
    ).coerceIn(0, page.words.lastIndex)
    val end = maxOf(
        selection.startWordIndex,
        selection.endWordIndex
    ).coerceIn(0, page.words.lastIndex)
    return page.words.subList(start, end + 1)
}

private fun selectedTextFor(
    page: PdfTextIndexPage,
    selection: TextSelectionState
): String {
    val words = selectedWordsFor(page, selection)
    if (words.isEmpty()) return ""
    val builder = StringBuilder()
    var currentLineY: Float? = null
    var currentLineHeight = 1f
    words.forEachIndexed { index, word ->
        val rect = word.visualRect(page)
        val lineY = currentLineY
        val isNewLine =
            lineY != null &&
                kotlin.math.abs(rect.centerY - lineY) >
                maxOf(currentLineHeight, rect.height) * 0.68f
        if (index > 0) {
            builder.append(if (isNewLine) "\n" else " ")
        }
        builder.append(word.text)
        if (lineY == null || isNewLine) {
            currentLineY = rect.centerY
            currentLineHeight = rect.height
        } else {
            currentLineY = (lineY + rect.centerY) / 2f
            currentLineHeight =
                maxOf(currentLineHeight, rect.height)
        }
    }
    return builder.toString().trim()
}

private fun textQuizLabel(
    selectedText: String,
    pageIndex: Int
): String {
    val preview = selectedText
        .replace(Regex("\\s+"), " ")
        .trim()
        .let {
            if (it.length > 42) {
                it.take(42).trimEnd() + "..."
            } else {
                it
            }
        }
    return if (preview.isBlank()) {
        "선택 텍스트 · 페이지 ${pageIndex + 1}"
    } else {
        "선택 텍스트: $preview · 페이지 ${pageIndex + 1}"
    }
}

/**
 * Handle long-press on a page: either select an
 * image overlay or open the paste / crop menu.
 */
private fun onPageLongPress(
    drawingState: DrawingState,
    pageIndex: Int,
    offset: Offset,
    viewWidthPx: Int,
    aspectRatio: Float,
    densityValue: Float,
    showMenu: (Int, Offset, DpOffset) -> Unit
) {
    val refW =
        drawingState.getPageRefWidth(pageIndex)
    val rs =
        if (refW > 0f && viewWidthPx > 0) {
            viewWidthPx.toFloat() / refW
        } else {
            1f
        }
    val refX = offset.x / rs
    val refY = offset.y / rs

    // Check if long-press is on an image
    val hitImg = drawingState
        .imagesForPage(pageIndex)
        .lastOrNull { img ->
            refX in img.x..(img.x + img.width) &&
                refY in img.y..(img.y + img.height)
        }
    if (hitImg != null) {
        drawingState.selectedImage = hitImg
        drawingState.selectedImagePage = pageIndex
    } else {
        val coordScale =
            if (refW > 0f && viewWidthPx > 0) {
                refW / viewWidthPx
            } else {
                1f
            }
        val iz =
            coordScale / drawingState.zoomScale
        val tapPos = Offset(
            offset.x * iz,
            offset.y * iz
        )
        val menuOff = DpOffset(
            (offset.x / densityValue).dp,
            (offset.y / densityValue).dp
        )
        showMenu(pageIndex, tapPos, menuOff)
    }
}

/** Long-press action menu (crop / paste). */
@Composable
private fun PageActionMenu(
    pasteMenuOffset: DpOffset,
    aspectRatio: Float,
    pasteTapPosition: Offset,
    drawingState: DrawingState,
    pageIndex: Int,
    context: Context,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        offset = pasteMenuOffset
    ) {
        DropdownMenuItem(
            text = { Text("화면 캡처") },
            onClick = {
                onDismiss()
                drawingState.isCropping = true
                drawingState.cropPageIndex = pageIndex
                val cx = pasteTapPosition.x
                val cy = pasteTapPosition.y
                val rw = drawingState
                    .getPageRefWidth(pageIndex)
                val maxX =
                    if (rw > 0f) rw else 1000f
                val maxY = maxX / aspectRatio
                drawingState.cropTopLeft = Offset(
                    (cx - UxConfig.Crop.INITIAL_WIDTH).coerceAtLeast(0f),
                    (cy - UxConfig.Crop.INITIAL_HEIGHT).coerceAtLeast(0f)
                )
                drawingState.cropBottomRight = Offset(
                    (cx + UxConfig.Crop.INITIAL_WIDTH).coerceAtMost(maxX),
                    (cy + UxConfig.Crop.INITIAL_HEIGHT).coerceAtMost(maxY)
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Crop,
                    null,
                    tint = MaestroPrimary
                )
            }
        )
        DropdownMenuItem(
            text = { Text("붙여넣기") },
            onClick = {
                onDismiss()
                pasteFromClipboard(
                    context,
                    drawingState,
                    pageIndex,
                    pasteTapPosition
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.ContentPaste,
                    null,
                    tint = MaestroPrimary
                )
            }
        )
    }
}

/**
 * Get page aspect ratio without full render
 * (cached via remember at call site).
 */
@Composable
private fun getPageAspectRatioSync(context: Context, uri: Uri, pageIndex: Int): Float {
    return remember(uri, pageIndex) {
        readPageAspectRatio(context, uri, pageIndex)
    }
}

private fun readPageAspectRatio(
    context: Context,
    uri: Uri,
    pageIndex: Int
): Float {
    return try {
        val fd = if (uri.scheme == "file") {
            val file = java.io.File(
                uri.path ?: return 1f / 1.414f
            )
            if (!file.exists()) {
                return 1f / 1.414f
            }
            android.os.ParcelFileDescriptor.open(
                file,
                android.os.ParcelFileDescriptor
                    .MODE_READ_ONLY
            )
        } else {
            context.contentResolver
                .openFileDescriptor(uri, "r")
                ?: return 1f / 1.414f
        }
        val renderer =
            android.graphics.pdf.PdfRenderer(fd)
        if (pageIndex >= renderer.pageCount) {
            renderer.close()
            fd.close()
            return 1f / 1.414f
        }
        val page = renderer.openPage(pageIndex)
        val r =
            page.width.toFloat() /
                page.height.toFloat()
        page.close()
        renderer.close()
        fd.close()
        r
    } catch (_: Throwable) {
        1f / 1.414f
    }
}

/**
 * Paste strokes or image from clipboard into
 * the page.
 */
private fun pasteFromClipboard(
    context: Context,
    state: DrawingState,
    pageIndex: Int,
    tapPosition: Offset
) {
    try {
        val clipboard = context.getSystemService(
            Context.CLIPBOARD_SERVICE
        ) as ClipboardManager
        val clip =
            clipboard.primaryClip ?: return
        if (clip.itemCount == 0) return
        val item = clip.getItemAt(0) ?: return

        // Try pasting image first
        val imageUri = item.uri
        if (imageUri != null) {
            try {
                val inputStream = context
                    .contentResolver
                    .openInputStream(imageUri)
                val bitmap = android.graphics
                    .BitmapFactory
                    .decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    val imgW =
                        bitmap.width.toFloat()
                    val imgH =
                        bitmap.height.toFloat()
                    val overlay =
                        DrawingState.ImageOverlay(
                            bitmap,
                            tapPosition.x - imgW / 2,
                            tapPosition.y - imgH / 2,
                            imgW,
                            imgH
                        )
                    state.addImage(pageIndex, overlay)
                    state.selectedImage = overlay
                    state.selectedImagePage = pageIndex
                    return
                }
            } catch (_: Throwable) {
                // fall through to stroke paste
            }
        }

        // Try pasting strokes (JSON)
        val text =
            (
                item.text
                    ?: item.coerceToText(context)
                )
                ?.toString() ?: return
        if (!text.trimStart().startsWith("[")) return

        val parsed = clipboardJson
            .decodeFromString<List<ClipboardStroke>>(
                text
            )
        val rawStrokes = parsed.map { s ->
            InkStroke(
                points = s.pts.map { p ->
                    StrokePoint(
                        p.x.toFloat(),
                        p.y.toFloat(),
                        p.p.toFloat()
                    )
                },
                color = Color(s.color),
                baseWidth = s.width.toFloat()
            )
        }
        if (rawStrokes.isEmpty()) return

        // Compute bounding box center
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        rawStrokes.forEach { stroke ->
            stroke.points.forEach { pt ->
                if (pt.x < minX) minX = pt.x
                if (pt.y < minY) minY = pt.y
                if (pt.x > maxX) maxX = pt.x
                if (pt.y > maxY) maxY = pt.y
            }
        }
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f

        // Offset so center aligns with tap
        val dx = tapPosition.x - centerX
        val dy = tapPosition.y - centerY
        val shifted = rawStrokes.map { stroke ->
            stroke.copy(
                points = stroke.points.map { pt ->
                    pt.copy(
                        x = pt.x + dx,
                        y = pt.y + dy
                    )
                }
            )
        }

        // Add strokes and enter lasso-selected state
        state.strokesForPage(pageIndex)
            .addAll(shifted)
        state.annotationVersion++
        state.selectedStrokes.clear()
        state.selectedStrokes.addAll(shifted)
        state.selectionPageIndex = pageIndex
        state.lassoPhase = LassoPhase.SELECTED
        state.dragOffset = Offset.Zero
    } catch (_: Throwable) {
        // silently ignore paste failures
    }
}

@Composable
private fun PlaceholderCanvas() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f / 1.414f)
            .background(
                MaestroSurfaceContainerLowest,
                RoundedCornerShape(2.dp)
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(48.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.75f)
                    .height(32.dp)
                    .background(
                        MaestroSurfaceContainer,
                        RoundedCornerShape(2.dp)
                    )
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.5f)
                    .height(16.dp)
                    .background(
                        MaestroSurfaceContainerHigh,
                        RoundedCornerShape(2.dp)
                    )
            )
            Spacer(Modifier.height(32.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                Arrangement.spacedBy(32.dp)
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(16f / 9f)
                        .background(
                            MaestroSurfaceContainer
                                .copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                )
                Column(
                    Modifier.weight(1f),
                    verticalArrangement =
                    Arrangement
                        .spacedBy(12.dp)
                ) {
                    repeat(4) { i ->
                        val frac = when (i) {
                            2 -> 0.83f
                            3 -> 0.67f
                            else -> 1f
                        }
                        Box(
                            Modifier
                                .fillMaxWidth(frac)
                                .height(12.dp)
                                .background(
                                    MaestroSurfaceContainer,
                                    RoundedCornerShape(
                                        9999.dp
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}
