package com.maestro.app.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maestro.app.domain.model.CropCapturePhase
import com.maestro.app.domain.model.DrawingTool
import com.maestro.app.domain.model.InkStroke
import com.maestro.app.domain.model.LassoPhase
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

@Composable
fun CanvasSection(
    pdfUri: Uri?,
    pageCount: Int,
    drawingState: DrawingState,
    modifier: Modifier = Modifier,
    onCropLlm: ((ByteArray) -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Paste popup state
    var showPasteMenu by remember { mutableStateOf(false) }
    var pastePageIndex by remember { mutableIntStateOf(0) }
    var pasteTapPosition by remember {
        mutableStateOf(Offset.Zero)
    }
    var pasteMenuOffset by remember {
        mutableStateOf(DpOffset.Zero)
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(MaestroSurfaceContainerLow)
            .clipToBounds()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
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

                LazyColumn(
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
                                .pointerInput(pageIndex) {
                                    detectTapGestures(
                                        onLongPress =
                                        { offset ->
                                            onPageLongPress(
                                                drawingState,
                                                pageIndex,
                                                offset,
                                                size.width,
                                                aspectRatio,
                                                density
                                                    .density
                                            ) {
                                                    pi,
                                                    tap,
                                                    menu
                                                ->
                                                pastePageIndex =
                                                    pi
                                                pasteTapPosition =
                                                    tap
                                                pasteMenuOffset =
                                                    menu
                                                showPasteMenu =
                                                    true
                                            }
                                        }
                                    )
                                }
                        ) {
                            PdfPageView(
                                uri = pdfUri,
                                pageIndex = pageIndex,
                                modifier =
                                Modifier.fillMaxWidth()
                            )
                            StylusDrawingCanvas(
                                state = drawingState,
                                pageIndex = pageIndex,
                                modifier =
                                Modifier
                                    .matchParentSize(),
                                onCropLlm =
                                onCropLlm
                            )

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
        try {
            val fd = if (uri.scheme == "file") {
                val file = java.io.File(
                    uri.path
                        ?: return@remember
                        1f / 1.414f
                )
                if (!file.exists()) {
                    return@remember 1f / 1.414f
                }
                android.os.ParcelFileDescriptor.open(
                    file,
                    android.os.ParcelFileDescriptor
                        .MODE_READ_ONLY
                )
            } else {
                context.contentResolver
                    .openFileDescriptor(uri, "r")
                    ?: return@remember 1f / 1.414f
            }
            val renderer =
                android.graphics.pdf.PdfRenderer(fd)
            if (pageIndex >= renderer.pageCount) {
                renderer.close()
                fd.close()
                return@remember 1f / 1.414f
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
