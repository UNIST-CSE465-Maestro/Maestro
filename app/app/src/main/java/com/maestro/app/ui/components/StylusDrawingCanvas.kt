package com.maestro.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.maestro.app.domain.model.DrawingTool
import com.maestro.app.domain.model.InkStroke
import com.maestro.app.domain.model.LassoPhase
import com.maestro.app.domain.model.StrokePoint
import com.maestro.app.ui.config.UxConfig
import com.maestro.app.ui.drawing.DrawingState
import com.maestro.app.ui.theme.MaestroError
import com.maestro.app.ui.theme.MaestroPrimary
import com.maestro.app.ui.theme.Slate500
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ── Serializable clipboard DTOs ──────────────────

@Serializable
private data class ClipStrokePoint(
    val x: Double,
    val y: Double,
    val p: Double
)

@Serializable
private data class ClipStroke(
    val color: Int,
    val width: Double,
    val pts: List<ClipStrokePoint>
)

// ── Constants ────────────────────────────────────

private val LassoColor = MaestroPrimary.copy(alpha = 0.6f)
private val SelectionBorder = MaestroPrimary.copy(alpha = 0.6f)
private val EraserCircleColor = Color(0xFF666666)
private val BtnDelete = MaestroError
private val BtnCopy = MaestroPrimary

// Button hit areas (in PDF coords, updated during draw)
private var cutButtonRect: Rect? = null
private var copyButtonRect: Rect? = null
private var deleteButtonRect: Rect? = null
private var cropCopyButtonRect: Rect? = null
private var cropCancelButtonRect: Rect? = null
private var imgDeleteRect: Rect? = null
private var imgCopyRect: Rect? = null
private var imgCutRect: Rect? = null
private var imgDragMode = 0 // 0=none, 1=move, 2=resize
private var imgDragStartX = 0f
private var imgDragStartY = 0f
private var imgOriginal: DrawingState.ImageOverlay? = null

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun StylusDrawingCanvas(state: DrawingState, pageIndex: Int, modifier: Modifier = Modifier) {
    val pageStrokes = state.strokesForPage(pageIndex)
    val view = LocalView.current
    val context = LocalContext.current

    // Canvas size and position tracking
    var canvasWidth by remember {
        mutableFloatStateOf(0f)
    }
    var canvasHeight by remember {
        mutableFloatStateOf(0f)
    }
    var canvasScreenX by remember {
        mutableFloatStateOf(0f)
    }
    var canvasScreenY by remember {
        mutableFloatStateOf(0f)
    }

    // Pen long-press detection (timer-based)
    var penLongPressHandled by remember {
        mutableStateOf(false)
    }
    // prevent stroke after button tap
    var buttonTapConsumed by remember {
        mutableStateOf(false)
    }
    var penDownX by remember { mutableFloatStateOf(0f) }
    var penDownY by remember { mutableFloatStateOf(0f) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val longPressRunnable = remember {
        mutableStateOf<Runnable?>(null)
    }

    Canvas(
        modifier = modifier
            .onGloballyPositioned { coords ->
                canvasWidth = coords.size.width.toFloat()
                canvasHeight = coords.size.height.toFloat()
                val pos = coords.positionInWindow()
                canvasScreenX = pos.x
                canvasScreenY = pos.y
            }
            .pointerInteropFilter { event ->
                try {
                    val isStylus =
                        event.getToolType(0) ==
                            MotionEvent.TOOL_TYPE_STYLUS
                    val zoom = state.zoomScale

                    // Set reference width on first touch
                    if (canvasWidth > 0f) {
                        state.setPageRefWidthIfMissing(
                            pageIndex,
                            canvasWidth
                        )
                    }
                    val refW = state.getPageRefWidth(pageIndex)
                    // coordScale: current canvas px -> ref space
                    val coordScale =
                        if (refW > 0f && canvasWidth > 0f) {
                            refW / canvasWidth
                        } else {
                            1f
                        }
                    val iz = coordScale / zoom

                    // Handle hover: update button state
                    if (event.actionMasked ==
                        MotionEvent.ACTION_HOVER_MOVE ||
                        event.actionMasked ==
                        MotionEvent.ACTION_HOVER_ENTER
                    ) {
                        if (isStylus) {
                            state.isStylusActive = true
                            state.sPenButtonPressed =
                                (
                                    event.buttonState and
                                        MotionEvent
                                            .BUTTON_STYLUS_PRIMARY
                                    ) != 0
                        }
                        return@pointerInteropFilter false
                    }
                    if (event.actionMasked ==
                        MotionEvent.ACTION_HOVER_EXIT
                    ) {
                        if (isStylus) {
                            state.sPenButtonPressed = false
                        }
                        return@pointerInteropFilter false
                    }

                    // ── Crop mode input handling ──
                    if (state.isCropping &&
                        state.cropPageIndex == pageIndex
                    ) {
                        handleCropInput(
                            event, state, pageIndex,
                            canvasWidth, canvasHeight,
                            canvasScreenX, canvasScreenY,
                            view, context,
                            { buttonTapConsumed = true }
                        )
                        return@pointerInteropFilter true
                    }

                    // ── Selected image: move/resize ──
                    if (state.selectedImage != null &&
                        state.selectedImagePage == pageIndex
                    ) {
                        val consumed = handleSelectedImage(
                            event,
                            state,
                            pageIndex,
                            canvasWidth,
                            isStylus,
                            view,
                            context,
                            { buttonTapConsumed = true }
                        )
                        if (consumed) {
                            return@pointerInteropFilter true
                        }
                    }

                    if (!isStylus) {
                        return@pointerInteropFilter false
                    }

                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            state.isStylusTouching = true
                            penLongPressHandled = false
                            buttonTapConsumed = false
                            penDownX = event.x
                            penDownY = event.y
                            disallowParentIntercept(
                                view,
                                true
                            )
                            // Long-press timer for paste
                            if (state.activeTool ==
                                DrawingTool.PEN &&
                                !state.sPenButtonPressed
                            ) {
                                startLongPress(
                                    handler,
                                    longPressRunnable,
                                    state,
                                    pageIndex,
                                    iz,
                                    canvasWidth,
                                    penDownX,
                                    penDownY,
                                    { penLongPressHandled },
                                    {
                                        penLongPressHandled =
                                            true
                                    }
                                )
                            }
                        }
                        MotionEvent.ACTION_MOVE -> {
                            // Cancel long-press if moved
                            if (!penLongPressHandled &&
                                longPressRunnable.value !=
                                null
                            ) {
                                val dx = event.x - penDownX
                                val dy = event.y - penDownY
                                if (dx * dx + dy * dy >
                                    UxConfig.Gesture.LONG_PRESS_CANCEL_DIST_SQ
                                ) {
                                    longPressRunnable.value
                                        ?.let {
                                            handler
                                                .removeCallbacks(
                                                    it
                                                )
                                        }
                                    longPressRunnable.value =
                                        null
                                }
                            }
                        }
                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL -> {
                            longPressRunnable.value?.let {
                                handler.removeCallbacks(it)
                            }
                            longPressRunnable.value = null
                            state.isStylusTouching = false
                            state.sPenButtonPressed = false
                            state.eraserIndicator = null
                            penLongPressHandled = false
                            disallowParentIntercept(
                                view,
                                false
                            )
                        }
                    }

                    if (penLongPressHandled) {
                        return@pointerInteropFilter true
                    }

                    state.activePageIndex = pageIndex
                    state.isStylusActive = true

                    if (event.actionMasked ==
                        MotionEvent.ACTION_DOWN
                    ) {
                        state.sPenButtonPressed =
                            (
                                event.buttonState and
                                    MotionEvent
                                        .BUTTON_STYLUS_PRIMARY
                                ) != 0
                    }
                    if (!state.sPenButtonPressed &&
                        (
                            event.buttonState and
                                MotionEvent
                                    .BUTTON_STYLUS_PRIMARY
                            ) != 0
                    ) {
                        state.sPenButtonPressed = true
                    }
                    val tool =
                        if (state.sPenButtonPressed) {
                            DrawingTool.ERASER
                        } else {
                            state.activeTool
                        }

                    if (tool == DrawingTool.ERASER &&
                        state.lassoPhase != LassoPhase.IDLE
                    ) {
                        state.clearLasso()
                    }
                    if (tool != DrawingTool.ERASER) {
                        state.eraserIndicator = null
                    }

                    // Skip drawing if button was tapped
                    if (buttonTapConsumed) {
                        if (event.actionMasked ==
                            MotionEvent.ACTION_UP ||
                            event.actionMasked ==
                            MotionEvent.ACTION_CANCEL
                        ) {
                            state.isStylusTouching = false
                            buttonTapConsumed = false
                        }
                        return@pointerInteropFilter true
                    }

                    // Check selection button taps
                    if (tool != DrawingTool.ERASER &&
                        event.actionMasked ==
                        MotionEvent.ACTION_DOWN &&
                        state.lassoPhase ==
                        LassoPhase.SELECTED &&
                        state.selectionPageIndex ==
                        pageIndex
                    ) {
                        val tx = event.x * iz
                        val ty = event.y * iz
                        if (deleteButtonRect?.contains(
                                Offset(tx, ty)
                            ) == true
                        ) {
                            state.deleteSelection()
                            state.isStylusTouching = false
                            buttonTapConsumed = true
                            return@pointerInteropFilter true
                        }
                        if (copyButtonRect?.contains(
                                Offset(tx, ty)
                            ) == true
                        ) {
                            copySelectionToClipboard(
                                context,
                                state
                            )
                            state.isStylusTouching = false
                            buttonTapConsumed = true
                            return@pointerInteropFilter true
                        }
                        if (cutButtonRect?.contains(
                                Offset(tx, ty)
                            ) == true
                        ) {
                            copySelectionToClipboard(
                                context,
                                state
                            )
                            state.deleteSelection()
                            state.isStylusTouching = false
                            buttonTapConsumed = true
                            return@pointerInteropFilter true
                        }
                    }

                    // Pen with lasso selection: drag
                    if (tool == DrawingTool.PEN &&
                        state.lassoPhase !=
                        LassoPhase.IDLE &&
                        state.selectionPageIndex ==
                        pageIndex
                    ) {
                        handleLasso(
                            event,
                            state,
                            pageIndex,
                            iz
                        )
                    } else {
                        when (tool) {
                            DrawingTool.PEN ->
                                handlePen(
                                    event,
                                    state,
                                    iz
                                )
                            DrawingTool.ERASER ->
                                handleEraser(
                                    event,
                                    state,
                                    pageIndex,
                                    iz
                                )
                            DrawingTool.LASSO ->
                                handleLasso(
                                    event,
                                    state,
                                    pageIndex,
                                    iz
                                )
                        }
                    }
                } catch (_: Throwable) { }
                true
            }
    ) {
        try {
            val currentWidth = size.width
            if (currentWidth > 0f &&
                currentWidth != canvasWidth
            ) {
                canvasWidth = currentWidth
            }
            if (canvasWidth > 0f) {
                state.setPageRefWidthIfMissing(
                    pageIndex,
                    canvasWidth
                )
            }
            val refW = state.getPageRefWidth(pageIndex)
            val renderScale =
                if (refW > 0f) currentWidth / refW else 1f
            val dragOff = state.dragOffset

            // Scale strokes from ref coords to canvas px
            scale(
                renderScale,
                renderScale,
                pivot = Offset.Zero
            ) {
                // Draw image overlays
                state.imagesForPage(pageIndex)
                    .forEach { img ->
                        drawImage(
                            image = img.bitmap
                                .asImageBitmap(),
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(
                                img.bitmap.width,
                                img.bitmap.height
                            ),
                            dstOffset = IntOffset(
                                img.x.toInt(),
                                img.y.toInt()
                            ),
                            dstSize = IntSize(
                                img.width.toInt(),
                                img.height.toInt()
                            )
                        )
                    }

                // Draw all strokes
                pageStrokes.forEach { stroke ->
                    val isDragging =
                        state.lassoPhase ==
                            LassoPhase.DRAGGING &&
                            stroke in state.selectedStrokes
                    drawInkStroke(
                        stroke,
                        if (isDragging) {
                            dragOff
                        } else {
                            Offset.Zero
                        }
                    )
                }

                // In-progress pen stroke
                if (state.activeTool ==
                    DrawingTool.PEN &&
                    state.activePageIndex ==
                    pageIndex &&
                    state.currentPoints.isNotEmpty()
                ) {
                    drawInkStroke(
                        InkStroke(
                            state.currentPoints.toList(),
                            state.activeColor,
                            state.activeWidth
                        ),
                        Offset.Zero
                    )
                }

                // Lasso drawing path
                if (state.lassoPhase ==
                    LassoPhase.DRAWING &&
                    state.activePageIndex ==
                    pageIndex &&
                    state.lassoPoints.size > 1
                ) {
                    drawLassoPath(state.lassoPoints)
                }

                // ── Selection bounding box ──
                if ((
                        state.lassoPhase ==
                            LassoPhase.SELECTED ||
                            state.lassoPhase ==
                            LassoPhase.DRAGGING
                        ) &&
                    state.selectionPageIndex ==
                    pageIndex &&
                    state.selectedStrokes.isNotEmpty()
                ) {
                    val bounds =
                        state.getSelectionBounds(dragOff)
                    if (bounds != null) {
                        drawSelectionBox(bounds)
                        drawSelectionButtons(bounds)
                    }
                }

                // ── Eraser circle indicator ──
                if (state.eraserIndicator != null &&
                    state.eraserIndicatorPage == pageIndex
                ) {
                    val pos = state.eraserIndicator!!
                    drawCircle(
                        color = EraserCircleColor
                            .copy(alpha = UxConfig.Drawing.ERASER_FILL_ALPHA),
                        radius = state.eraserWidth,
                        center = pos,
                        style = Fill
                    )
                    drawCircle(
                        color = EraserCircleColor
                            .copy(alpha = UxConfig.Drawing.ERASER_OUTLINE_ALPHA),
                        radius = state.eraserWidth,
                        center = pos,
                        style = Stroke(UxConfig.Drawing.ERASER_OUTLINE_STROKE_WIDTH)
                    )
                }
            } // scale

            // ── Selected image overlay ──
            drawSelectedImageOverlay(
                state,
                pageIndex,
                renderScale
            )

            // ── Crop overlay ──
            drawCropOverlay(
                state,
                pageIndex,
                renderScale
            )
        } catch (_: Throwable) { }
    }
}

// ── Crop input handling (extracted for line length) ──

private fun handleCropInput(
    event: MotionEvent,
    state: DrawingState,
    pageIndex: Int,
    canvasWidth: Float,
    canvasHeight: Float,
    canvasScreenX: Float,
    canvasScreenY: Float,
    view: View,
    context: Context,
    onButtonTap: () -> Unit
) {
    val refW = state.getPageRefWidth(pageIndex)
    val rs =
        if (refW > 0f && canvasWidth > 0f) {
            canvasWidth / refW
        } else {
            1f
        }
    val ex = event.x
    val ey = event.y

    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            state.isStylusTouching = true
            disallowParentIntercept(view, true)
            // Check copy/cancel buttons
            if (cropCopyButtonRect?.contains(
                    Offset(ex, ey)
                ) == true
            ) {
                captureAndCopy(
                    context,
                    view,
                    state,
                    canvasWidth,
                    canvasHeight,
                    canvasScreenX,
                    canvasScreenY
                )
                onButtonTap()
                return
            }
            if (cropCancelButtonRect?.contains(
                    Offset(ex, ey)
                ) == true
            ) {
                state.isCropping = false
                state.isStylusTouching = false
                onButtonTap()
                return
            }
            // Find nearest corner to drag
            val tlx = state.cropTopLeft.x * rs
            val tly = state.cropTopLeft.y * rs
            val brx = state.cropBottomRight.x * rs
            val bry = state.cropBottomRight.y * rs
            val corners = listOf(
                0 to Offset(tlx, tly),
                1 to Offset(brx, tly),
                2 to Offset(tlx, bry),
                3 to Offset(brx, bry)
            )
            val nearest = corners.minByOrNull {
                val dx = it.second.x - ex
                val dy = it.second.y - ey
                dx * dx + dy * dy
            }
            val dist = nearest?.let {
                val dx = it.second.x - ex
                val dy = it.second.y - ey
                kotlin.math.sqrt(
                    (dx * dx + dy * dy).toDouble()
                ).toFloat()
            } ?: 999f
            // reuse activePageIndex as corner index
            state.activePageIndex =
                if (dist < UxConfig.Crop.CORNER_DRAG_DISTANCE) nearest!!.first else -1
        }
        MotionEvent.ACTION_MOVE -> {
            val cornerIdx = state.activePageIndex
            if (cornerIdx in 0..3) {
                val maxRefX =
                    if (rs > 0f) {
                        canvasWidth / rs
                    } else {
                        9999f
                    }
                val maxRefY =
                    if (rs > 0f) {
                        canvasHeight / rs
                    } else {
                        9999f
                    }
                val rx = (ex / rs).coerceIn(0f, maxRefX)
                val ry = (ey / rs).coerceIn(0f, maxRefY)
                val minGap = UxConfig.Crop.MIN_CORNER_GAP
                applyCropCornerMove(
                    state,
                    cornerIdx,
                    rx,
                    ry,
                    minGap,
                    maxRefX,
                    maxRefY
                )
            }
        }
        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL -> {
            state.isStylusTouching = false
            disallowParentIntercept(view, false)
        }
    }
}

private fun applyCropCornerMove(
    state: DrawingState,
    cornerIdx: Int,
    rx: Float,
    ry: Float,
    minGap: Float,
    maxRefX: Float,
    maxRefY: Float
) {
    when (cornerIdx) {
        0 -> state.cropTopLeft = Offset(
            rx.coerceIn(
                0f,
                state.cropBottomRight.x - minGap
            ),
            ry.coerceIn(
                0f,
                state.cropBottomRight.y - minGap
            )
        )
        1 -> {
            state.cropBottomRight = Offset(
                rx.coerceIn(
                    state.cropTopLeft.x + minGap,
                    maxRefX
                ),
                state.cropBottomRight.y
            )
            state.cropTopLeft = Offset(
                state.cropTopLeft.x,
                ry.coerceIn(
                    0f,
                    state.cropBottomRight.y - minGap
                )
            )
        }
        2 -> {
            state.cropTopLeft = Offset(
                rx.coerceIn(
                    0f,
                    state.cropBottomRight.x - minGap
                ),
                state.cropTopLeft.y
            )
            state.cropBottomRight = Offset(
                state.cropBottomRight.x,
                ry.coerceIn(
                    state.cropTopLeft.y + minGap,
                    maxRefY
                )
            )
        }
        3 -> state.cropBottomRight = Offset(
            rx.coerceIn(
                state.cropTopLeft.x + minGap,
                maxRefX
            ),
            ry.coerceIn(
                state.cropTopLeft.y + minGap,
                maxRefY
            )
        )
    }
}

// ── Selected image handling (extracted) ──────────

private fun handleSelectedImage(
    event: MotionEvent,
    state: DrawingState,
    pageIndex: Int,
    canvasWidth: Float,
    isStylus: Boolean,
    view: View,
    context: Context,
    onButtonTap: () -> Unit
): Boolean {
    val refW2 = state.getPageRefWidth(pageIndex)
    val rs2 =
        if (refW2 > 0f && canvasWidth > 0f) {
            canvasWidth / refW2
        } else {
            1f
        }
    val img = state.selectedImage!!
    val ex2 = event.x
    val ey2 = event.y
    // Screen coords of image
    val il = img.x * rs2
    val it2 = img.y * rs2
    val ir = (img.x + img.width) * rs2
    val ib = (img.y + img.height) * rs2
    // Resize handle = bottom-right corner (50px radius)
    val resizeHit =
        (ex2 - ir) * (ex2 - ir) +
            (ey2 - ib) * (ey2 - ib) < UxConfig.ImageOverlay.RESIZE_HANDLE_HIT_DIST_SQ

    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            state.isStylusTouching = true
            disallowParentIntercept(view, true)
            // Check delete/copy/cut buttons
            if (imgDeleteRect?.contains(
                    Offset(ex2, ey2)
                ) == true
            ) {
                state.removeImage(pageIndex, img)
                state.selectedImage = null
                state.isStylusTouching = false
                onButtonTap()
                return true
            }
            if (imgCopyRect?.contains(
                    Offset(ex2, ey2)
                ) == true
            ) {
                copyImageToClipboard(context, img)
                state.isStylusTouching = false
                onButtonTap()
                return true
            }
            if (imgCutRect?.contains(
                    Offset(ex2, ey2)
                ) == true
            ) {
                copyImageToClipboard(context, img)
                state.removeImage(pageIndex, img)
                state.selectedImage = null
                state.isStylusTouching = false
                onButtonTap()
                return true
            }
            if (ex2 in il..ir && ey2 in it2..ib) {
                // Hit inside image -> drag or resize
                imgDragMode =
                    if (resizeHit) 2 else 1
                imgDragStartX = ex2
                imgDragStartY = ey2
                imgOriginal = img
            } else {
                // Tap outside -> deselect
                state.selectedImage = null
                state.isStylusTouching = false
                if (!isStylus) return false
            }
        }
        MotionEvent.ACTION_MOVE -> {
            handleImageDrag(
                event,
                state,
                pageIndex,
                rs2
            )
        }
        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL -> {
            imgDragMode = 0
            imgOriginal = null
            state.isStylusTouching = false
            disallowParentIntercept(view, false)
        }
    }
    return imgDragMode > 0
}

private fun handleImageDrag(event: MotionEvent, state: DrawingState, pageIndex: Int, rs2: Float) {
    val ex2 = event.x
    val ey2 = event.y
    if (imgDragMode == 1 && imgOriginal != null) {
        val dx = (ex2 - imgDragStartX) / rs2
        val dy = (ey2 - imgDragStartY) / rs2
        val moved = imgOriginal!!.copy(
            x = imgOriginal!!.x + dx,
            y = imgOriginal!!.y + dy
        )
        state.replaceImage(
            pageIndex,
            state.selectedImage!!,
            moved
        )
        state.selectedImage = moved
        imgDragStartX = ex2
        imgDragStartY = ey2
        imgOriginal = moved
    } else if (imgDragMode == 2 &&
        imgOriginal != null
    ) {
        val dx = (ex2 - imgDragStartX) / rs2
        val newW = (imgOriginal!!.width + dx)
            .coerceAtLeast(UxConfig.ImageOverlay.MIN_SIZE)
        val ratio =
            imgOriginal!!.bitmap.height.toFloat() /
                imgOriginal!!.bitmap.width
        val newH = newW * ratio
        val resized = imgOriginal!!.copy(
            width = newW,
            height = newH
        )
        state.replaceImage(
            pageIndex,
            state.selectedImage!!,
            resized
        )
        state.selectedImage = resized
        imgDragStartX = ex2
        imgDragStartY = ey2
        imgOriginal = resized
    }
}

// ── Long press helper ────────────────────────────

private fun startLongPress(
    handler: Handler,
    longPressRunnable: androidx.compose.runtime
        .MutableState<Runnable?>,
    state: DrawingState,
    pageIndex: Int,
    iz: Float,
    canvasWidth: Float,
    penDownX: Float,
    penDownY: Float,
    isHandled: () -> Boolean,
    markHandled: () -> Unit
) {
    val capturedIz = iz
    val capturedCanvasWidth = canvasWidth
    longPressRunnable.value?.let {
        handler.removeCallbacks(it)
    }
    val r = Runnable {
        if (!isHandled()) {
            markHandled()
            state.currentPoints.clear()
            if (state.lassoPhase != LassoPhase.IDLE) {
                state.clearLasso()
            }
            // Check if long-press is on an image
            val rw = state.getPageRefWidth(pageIndex)
            val rs =
                if (rw > 0f &&
                    capturedCanvasWidth > 0f
                ) {
                    capturedCanvasWidth / rw
                } else {
                    1f
                }
            val refX = penDownX / rs
            val refY = penDownY / rs
            val hitImg = state.imagesForPage(pageIndex)
                .lastOrNull { img ->
                    refX in img.x..(img.x + img.width) &&
                        refY in img.y..(img.y + img.height)
                }
            if (hitImg != null) {
                state.selectedImage = hitImg
                state.selectedImagePage = pageIndex
            } else {
                state.penPasteRequest = Offset(
                    penDownX * capturedIz,
                    penDownY * capturedIz
                )
                state.penPasteScreenPos = Offset(
                    penDownX, penDownY
                )
                state.penPastePageIndex = pageIndex
            }
        }
    }
    longPressRunnable.value = r
    handler.postDelayed(r, UxConfig.Gesture.LONG_PRESS_CANVAS_MS)
}

// ── Tool handlers ────────────────────────────────

private fun handlePen(event: MotionEvent, state: DrawingState, iz: Float) {
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            state.currentPoints.clear()
            state.currentPoints += pt(event, iz)
        }
        MotionEvent.ACTION_MOVE -> {
            addHistorical(
                event,
                state.currentPoints,
                iz
            )
            state.currentPoints += pt(event, iz)
        }
        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL -> {
            state.currentPoints += pt(event, iz)
            state.commitStroke()
        }
    }
}

private fun handleEraser(event: MotionEvent, state: DrawingState, pageIndex: Int, iz: Float) {
    val x = event.x * iz
    val y = event.y * iz
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            state.eraserIndicator = Offset(x, y)
            state.eraserIndicatorPage = pageIndex
            state.eraseAt(pageIndex, x, y)
        }
        MotionEvent.ACTION_MOVE -> {
            state.eraserIndicator = Offset(x, y)
            state.eraserIndicatorPage = pageIndex
            for (h in 0 until event.historySize) {
                state.eraseAt(
                    pageIndex,
                    event.getHistoricalX(h) * iz,
                    event.getHistoricalY(h) * iz
                )
            }
            state.eraseAt(pageIndex, x, y)
        }
        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL -> {
            state.eraserIndicator = null
        }
    }
}

private fun handleLasso(event: MotionEvent, state: DrawingState, pageIndex: Int, iz: Float) {
    when (state.lassoPhase) {
        LassoPhase.IDLE -> {
            if (event.actionMasked ==
                MotionEvent.ACTION_DOWN
            ) {
                state.lassoPoints.clear()
                state.lassoPoints += pt(event, iz)
                state.lassoPhase = LassoPhase.DRAWING
            }
        }
        LassoPhase.DRAWING -> {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    addHistorical(
                        event,
                        state.lassoPoints,
                        iz
                    )
                    state.lassoPoints +=
                        pt(event, iz)
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    state.lassoPoints +=
                        pt(event, iz)
                    state.completeLasso(pageIndex)
                }
            }
        }
        LassoPhase.SELECTED -> {
            if (event.actionMasked ==
                MotionEvent.ACTION_DOWN
            ) {
                val tx = event.x * iz
                val ty = event.y * iz
                if (state.isTouchInSelectionBounds(
                        tx,
                        ty
                    )
                ) {
                    state.dragStart = Offset(tx, ty)
                    state.dragOffset = Offset.Zero
                    state.lassoPhase =
                        LassoPhase.DRAGGING
                } else {
                    state.clearLasso()
                    state.lassoPoints +=
                        pt(event, iz)
                    state.lassoPhase =
                        LassoPhase.DRAWING
                }
            }
        }
        LassoPhase.DRAGGING -> {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    state.dragOffset = Offset(
                        event.x * iz -
                            state.dragStart.x,
                        event.y * iz -
                            state.dragStart.y
                    )
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL ->
                    state.commitDrag()
            }
        }
    }
}

// ── Drawing helpers ──────────────────────────────

private fun DrawScope.drawInkStroke(stroke: InkStroke, offset: Offset) {
    val pts = stroke.points
    if (pts.size < 2) {
        if (pts.size == 1) {
            drawCircle(
                stroke.color,
                stroke.baseWidth * pts[0].pressure,
                Offset(
                    pts[0].x + offset.x,
                    pts[0].y + offset.y
                )
            )
        }
        return
    }
    for (i in 1 until pts.size) {
        val prev = pts[i - 1]
        val curr = pts[i]
        val w = stroke.baseWidth *
            (
                UxConfig.Drawing.PRESSURE_BASE + (
                    prev.pressure +
                        curr.pressure
                    ) / 2f * UxConfig.Drawing.PRESSURE_MULT
                )
        val path = Path().apply {
            moveTo(
                prev.x + offset.x,
                prev.y + offset.y
            )
            if (i + 1 < pts.size) {
                val n = pts[i + 1]
                quadraticBezierTo(
                    curr.x + offset.x,
                    curr.y + offset.y,
                    (curr.x + n.x) / 2f + offset.x,
                    (curr.y + n.y) / 2f + offset.y
                )
            } else {
                lineTo(
                    curr.x + offset.x,
                    curr.y + offset.y
                )
            }
        }
        drawPath(
            path,
            stroke.color,
            style = Stroke(
                w,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

private fun DrawScope.drawLassoPath(points: List<StrokePoint>) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            lineTo(points[i].x, points[i].y)
        }
    }
    drawPath(
        path,
        LassoColor,
        style = Stroke(
            UxConfig.Drawing.LASSO_STROKE_WIDTH,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(UxConfig.Drawing.LASSO_DASH, UxConfig.Drawing.LASSO_GAP)
            )
        )
    )
    drawLine(
        LassoColor,
        Offset(points.last().x, points.last().y),
        Offset(points[0].x, points[0].y),
        strokeWidth = UxConfig.Drawing.LASSO_CLOSE_STROKE_WIDTH,
        pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(UxConfig.Drawing.LASSO_CLOSE_DASH, UxConfig.Drawing.LASSO_CLOSE_GAP)
        )
    )
}

private fun DrawScope.drawSelectionBox(bounds: Rect) {
    // Dashed rectangle
    drawRoundRect(
        color = SelectionBorder,
        topLeft = Offset(bounds.left, bounds.top),
        size = Size(bounds.width, bounds.height),
        cornerRadius = CornerRadius(4f, 4f),
        style = Stroke(
            UxConfig.Drawing.SELECTION_STROKE_WIDTH,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(UxConfig.Drawing.SELECTION_DASH, UxConfig.Drawing.SELECTION_GAP)
            )
        )
    )
    // Light fill
    drawRoundRect(
        color = SelectionBorder.copy(alpha = UxConfig.Drawing.SELECTION_FILL_ALPHA),
        topLeft = Offset(bounds.left, bounds.top),
        size = Size(bounds.width, bounds.height),
        cornerRadius = CornerRadius(4f, 4f),
        style = Fill
    )
}

private val BtnCut = Color(0xFFFF8F00) // amber

private fun DrawScope.drawSelectionButtons(bounds: Rect) {
    val btnSize = UxConfig.Selection.BUTTON_SIZE
    val gap = UxConfig.Selection.BUTTON_GAP
    val btnY = bounds.top - btnSize - UxConfig.Selection.BUTTON_OFFSET_Y

    // Delete button (red) - rightmost
    val delRect = Rect(
        bounds.right - btnSize,
        btnY,
        bounds.right,
        btnY + btnSize
    )
    deleteButtonRect = delRect
    drawRoundRect(
        BtnDelete,
        Offset(delRect.left, delRect.top),
        Size(btnSize, btnSize),
        CornerRadius(UxConfig.Selection.BUTTON_CORNER_RADIUS),
        style = Fill
    )
    val cx = delRect.center.x
    val cy = delRect.center.y
    val s = UxConfig.Selection.DELETE_ICON_SIZE
    drawLine(
        Color.White,
        Offset(cx - s, cy - s),
        Offset(cx + s, cy + s),
        UxConfig.Selection.DELETE_ICON_STROKE,
        StrokeCap.Round
    )
    drawLine(
        Color.White,
        Offset(cx + s, cy - s),
        Offset(cx - s, cy + s),
        UxConfig.Selection.DELETE_ICON_STROKE,
        StrokeCap.Round
    )

    // Copy button (blue) - middle
    val copyRect = Rect(
        delRect.left - btnSize - gap,
        btnY,
        delRect.left - gap,
        btnY + btnSize
    )
    copyButtonRect = copyRect
    drawRoundRect(
        BtnCopy,
        Offset(copyRect.left, copyRect.top),
        Size(btnSize, btnSize),
        CornerRadius(UxConfig.Selection.BUTTON_CORNER_RADIUS),
        style = Fill
    )
    val cx2 = copyRect.center.x
    val cy2 = copyRect.center.y
    val r = UxConfig.Selection.COPY_ICON_SIZE
    drawRoundRect(
        Color.White,
        Offset(cx2 - r + 3, cy2 - r - 1),
        Size(r * UxConfig.Selection.COPY_ICON_SCALE, r * UxConfig.Selection.COPY_ICON_SCALE),
        CornerRadius(3f),
        style = Stroke(UxConfig.Selection.COPY_ICON_STROKE)
    )
    drawRoundRect(
        Color.White,
        Offset(cx2 - r - 1, cy2 - r + 3),
        Size(r * UxConfig.Selection.COPY_ICON_SCALE, r * UxConfig.Selection.COPY_ICON_SCALE),
        CornerRadius(3f),
        style = Stroke(UxConfig.Selection.COPY_ICON_STROKE)
    )

    // Cut button (amber) - leftmost (scissors icon)
    val cutRect = Rect(
        copyRect.left - btnSize - gap,
        btnY,
        copyRect.left - gap,
        btnY + btnSize
    )
    cutButtonRect = cutRect
    drawRoundRect(
        BtnCut,
        Offset(cutRect.left, cutRect.top),
        Size(btnSize, btnSize),
        CornerRadius(UxConfig.Selection.BUTTON_CORNER_RADIUS),
        style = Fill
    )
    val cx3 = cutRect.center.x
    val cy3 = cutRect.center.y
    drawLine(
        Color.White,
        Offset(cx3 - 8f, cy3 - 10f),
        Offset(cx3 + 6f, cy3 + 4f),
        UxConfig.Selection.CUT_STROKE_WIDTH,
        StrokeCap.Round
    )
    drawLine(
        Color.White,
        Offset(cx3 + 8f, cy3 - 10f),
        Offset(cx3 - 6f, cy3 + 4f),
        UxConfig.Selection.CUT_STROKE_WIDTH,
        StrokeCap.Round
    )
    drawCircle(
        Color.White,
        UxConfig.Selection.CUT_CIRCLE_RADIUS,
        Offset(cx3 - 7f, cy3 + 7f),
        style = Stroke(UxConfig.Selection.CUT_CIRCLE_STROKE)
    )
    drawCircle(
        Color.White,
        UxConfig.Selection.CUT_CIRCLE_RADIUS,
        Offset(cx3 + 7f, cy3 + 7f),
        style = Stroke(UxConfig.Selection.CUT_CIRCLE_STROKE)
    )
}

// ── Selected image overlay drawing ──────────────

private fun DrawScope.drawSelectedImageOverlay(
    state: DrawingState,
    pageIndex: Int,
    renderScale: Float
) {
    if (state.selectedImage == null ||
        state.selectedImagePage != pageIndex
    ) {
        return
    }
    val img = state.selectedImage!!
    val il = img.x * renderScale
    val it2 = img.y * renderScale
    val ir = (img.x + img.width) * renderScale
    val ib = (img.y + img.height) * renderScale
    // Selection border
    drawRect(
        MaestroPrimary,
        Offset(il, it2),
        Size(ir - il, ib - it2),
        style = Stroke(
            3f,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(10f, 6f)
            )
        )
    )
    // Resize handle (bottom-right)
    drawCircle(
        Color.White,
        UxConfig.ImageOverlay.RESIZE_HANDLE_RADIUS,
        Offset(ir, ib),
        style = Fill
    )
    drawCircle(
        MaestroPrimary,
        UxConfig.ImageOverlay.RESIZE_HANDLE_RADIUS,
        Offset(ir, ib),
        style = Stroke(2.5f)
    )
    // Buttons above top-right
    val bs = UxConfig.ImageOverlay.BUTTON_SIZE
    val gap = UxConfig.ImageOverlay.BUTTON_GAP
    val by = it2 - bs - UxConfig.ImageOverlay.BUTTON_OFFSET_Y
    val delR = Rect(ir - bs, by, ir, by + bs)
    imgDeleteRect = delR
    drawRoundRect(
        BtnDelete,
        Offset(delR.left, delR.top),
        Size(bs, bs),
        CornerRadius(UxConfig.ImageOverlay.BUTTON_CORNER_RADIUS),
        style = Fill
    )
    val cx = delR.center.x
    val cy = delR.center.y
    val s = UxConfig.ImageOverlay.DELETE_ICON_SIZE
    drawLine(
        Color.White,
        Offset(cx - s, cy - s),
        Offset(cx + s, cy + s),
        UxConfig.ImageOverlay.DELETE_ICON_STROKE,
        StrokeCap.Round
    )
    drawLine(
        Color.White,
        Offset(cx + s, cy - s),
        Offset(cx - s, cy + s),
        UxConfig.ImageOverlay.DELETE_ICON_STROKE,
        StrokeCap.Round
    )

    val cpR = Rect(
        delR.left - bs - gap,
        by,
        delR.left - gap,
        by + bs
    )
    imgCopyRect = cpR
    drawRoundRect(
        BtnCopy,
        Offset(cpR.left, cpR.top),
        Size(bs, bs),
        CornerRadius(UxConfig.ImageOverlay.BUTTON_CORNER_RADIUS),
        style = Fill
    )
    val cx2 = cpR.center.x
    val cy2 = cpR.center.y
    val r = UxConfig.ImageOverlay.COPY_ICON_SIZE
    drawRoundRect(
        Color.White,
        Offset(cx2 - r + 2, cy2 - r - 1),
        Size(r * UxConfig.ImageOverlay.COPY_ICON_SCALE, r * UxConfig.ImageOverlay.COPY_ICON_SCALE),
        CornerRadius(2f),
        style = Stroke(UxConfig.ImageOverlay.COPY_ICON_STROKE)
    )
    drawRoundRect(
        Color.White,
        Offset(cx2 - r - 1, cy2 - r + 2),
        Size(r * UxConfig.ImageOverlay.COPY_ICON_SCALE, r * UxConfig.ImageOverlay.COPY_ICON_SCALE),
        CornerRadius(2f),
        style = Stroke(UxConfig.ImageOverlay.COPY_ICON_STROKE)
    )

    val ctR = Rect(
        cpR.left - bs - gap,
        by,
        cpR.left - gap,
        by + bs
    )
    imgCutRect = ctR
    drawRoundRect(
        BtnCut,
        Offset(ctR.left, ctR.top),
        Size(bs, bs),
        CornerRadius(UxConfig.ImageOverlay.BUTTON_CORNER_RADIUS),
        style = Fill
    )
    val cx3 = ctR.center.x
    val cy3 = ctR.center.y
    drawLine(
        Color.White,
        Offset(cx3 - 8f, cy3 - 10f),
        Offset(cx3 + 6f, cy3 + 4f),
        UxConfig.ImageOverlay.CUT_STROKE_WIDTH,
        StrokeCap.Round
    )
    drawLine(
        Color.White,
        Offset(cx3 + 8f, cy3 - 10f),
        Offset(cx3 - 6f, cy3 + 4f),
        UxConfig.ImageOverlay.CUT_STROKE_WIDTH,
        StrokeCap.Round
    )
    drawCircle(
        Color.White,
        UxConfig.ImageOverlay.CUT_CIRCLE_RADIUS,
        Offset(cx3 - 6f, cy3 + 7f),
        style = Stroke(UxConfig.ImageOverlay.CUT_CIRCLE_STROKE)
    )
    drawCircle(
        Color.White,
        UxConfig.ImageOverlay.CUT_CIRCLE_RADIUS,
        Offset(cx3 + 6f, cy3 + 7f),
        style = Stroke(UxConfig.ImageOverlay.CUT_CIRCLE_STROKE)
    )
}

// ── Crop overlay drawing ─────────────────────────

private fun DrawScope.drawCropOverlay(state: DrawingState, pageIndex: Int, renderScale: Float) {
    if (!state.isCropping ||
        state.cropPageIndex != pageIndex
    ) {
        return
    }
    val tl = Offset(
        state.cropTopLeft.x * renderScale,
        state.cropTopLeft.y * renderScale
    )
    val br = Offset(
        state.cropBottomRight.x * renderScale,
        state.cropBottomRight.y * renderScale
    )
    // Dim outside crop area
    drawRect(
        Color.Black.copy(alpha = UxConfig.Crop.OVERLAY_DIM_ALPHA),
        Offset.Zero,
        Size(size.width, tl.y)
    )
    drawRect(
        Color.Black.copy(alpha = UxConfig.Crop.OVERLAY_DIM_ALPHA),
        Offset(0f, br.y),
        Size(size.width, size.height - br.y)
    )
    drawRect(
        Color.Black.copy(alpha = UxConfig.Crop.OVERLAY_DIM_ALPHA),
        Offset(0f, tl.y),
        Size(tl.x, br.y - tl.y)
    )
    drawRect(
        Color.Black.copy(alpha = UxConfig.Crop.OVERLAY_DIM_ALPHA),
        Offset(br.x, tl.y),
        Size(size.width - br.x, br.y - tl.y)
    )
    // Crop border
    drawRect(
        MaestroPrimary,
        tl,
        Size(br.x - tl.x, br.y - tl.y),
        style = Stroke(UxConfig.Crop.BORDER_STROKE_WIDTH)
    )
    // Corner handles
    val handleR = UxConfig.Crop.CORNER_HANDLE_RADIUS
    listOf(
        tl,
        Offset(br.x, tl.y),
        Offset(tl.x, br.y),
        br
    ).forEach { corner ->
        drawCircle(
            Color.White,
            handleR,
            corner,
            style = Fill
        )
        drawCircle(
            MaestroPrimary,
            handleR,
            corner,
            style = Stroke(UxConfig.Crop.CORNER_HANDLE_STROKE)
        )
    }
    // Copy button above top-right
    val btnSize = UxConfig.Crop.BUTTON_SIZE
    val btnX = br.x - btnSize
    val btnY = tl.y - btnSize - UxConfig.Crop.BUTTON_OFFSET_Y
    cropCopyButtonRect = Rect(
        btnX, btnY, btnX + btnSize, btnY + btnSize
    )
    drawRoundRect(
        MaestroPrimary,
        Offset(btnX, btnY),
        Size(btnSize, btnSize),
        CornerRadius(UxConfig.Crop.BUTTON_CORNER_RADIUS),
        style = Fill
    )
    val cx = btnX + btnSize / 2
    val cy = btnY + btnSize / 2
    val r = UxConfig.Crop.COPY_ICON_SIZE
    drawRoundRect(
        Color.White,
        Offset(cx - r + 2, cy - r - 1),
        Size(r * UxConfig.Crop.COPY_ICON_SCALE, r * UxConfig.Crop.COPY_ICON_SCALE),
        CornerRadius(2f),
        style = Stroke(UxConfig.Crop.COPY_ICON_STROKE)
    )
    drawRoundRect(
        Color.White,
        Offset(cx - r - 1, cy - r + 2),
        Size(r * UxConfig.Crop.COPY_ICON_SCALE, r * UxConfig.Crop.COPY_ICON_SCALE),
        CornerRadius(2f),
        style = Stroke(UxConfig.Crop.COPY_ICON_STROKE)
    )
    // Cancel button
    val cancelX = btnX - btnSize - UxConfig.Crop.CANCEL_OFFSET_X
    cropCancelButtonRect = Rect(
        cancelX, btnY,
        cancelX + btnSize, btnY + btnSize
    )
    drawRoundRect(
        Slate500.copy(alpha = UxConfig.Crop.CANCEL_BG_ALPHA),
        Offset(cancelX, btnY),
        Size(btnSize, btnSize),
        CornerRadius(UxConfig.Crop.BUTTON_CORNER_RADIUS),
        style = Fill
    )
    val cx2 = cancelX + btnSize / 2
    val cy2 = btnY + btnSize / 2
    val s = UxConfig.Crop.CANCEL_ICON_SIZE
    drawLine(
        Color.White,
        Offset(cx2 - s, cy2 - s),
        Offset(cx2 + s, cy2 + s),
        UxConfig.Crop.CANCEL_ICON_STROKE,
        StrokeCap.Round
    )
    drawLine(
        Color.White,
        Offset(cx2 + s, cy2 - s),
        Offset(cx2 - s, cy2 + s),
        UxConfig.Crop.CANCEL_ICON_STROKE,
        StrokeCap.Round
    )
}

// ── Clipboard (kotlinx.serialization) ────────────

private val clipJson = Json {
    ignoreUnknownKeys = true
}

private fun copySelectionToClipboard(context: Context, state: DrawingState) {
    if (state.selectedStrokes.isEmpty()) return
    val clipStrokes = state.selectedStrokes.map { stroke ->
        ClipStroke(
            color = stroke.color.toArgb(),
            width = stroke.baseWidth.toDouble(),
            pts = stroke.points.map { pt ->
                ClipStrokePoint(
                    x = pt.x.toDouble(),
                    y = pt.y.toDouble(),
                    p = pt.pressure.toDouble()
                )
            }
        )
    }
    val jsonStr = clipJson.encodeToString(clipStrokes)
    val clip = ClipData.newPlainText(
        "maestro_strokes",
        jsonStr
    )
    val cm = context.getSystemService(
        Context.CLIPBOARD_SERVICE
    ) as ClipboardManager
    cm.setPrimaryClip(clip)
}

// ── Image clipboard ──────────────────────────────

private fun copyImageToClipboard(context: Context, img: DrawingState.ImageOverlay) {
    try {
        val file = java.io.File(
            context.cacheDir,
            "img_copy.png"
        )
        file.outputStream().use {
            img.bitmap.compress(
                android.graphics.Bitmap
                    .CompressFormat.PNG,
                100,
                it
            )
        }
        val uri =
            androidx.core.content.FileProvider
                .getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
        val clipData = android.content.ClipData.newUri(
            context.contentResolver,
            "image",
            uri
        )
        val cm = context.getSystemService(
            Context.CLIPBOARD_SERVICE
        ) as android.content.ClipboardManager
        cm.setPrimaryClip(clipData)
    } catch (_: Throwable) { }
}

// ── Screen capture ───────────────────────────────

private fun captureAndCopy(
    context: Context,
    view: View,
    state: DrawingState,
    cw: Float,
    ch: Float,
    cx: Float,
    cy: Float
) {
    try {
        // 1. Hide crop overlay before capture
        state.isCropping = false

        // 2. Capture entire root view
        val rootView = view.rootView
        val fullBitmap =
            android.graphics.Bitmap.createBitmap(
                rootView.width,
                rootView.height,
                android.graphics.Bitmap
                    .Config.ARGB_8888
            )
        rootView.draw(
            android.graphics.Canvas(fullBitmap)
        )

        // 3. Convert crop ref-coords to screen px
        val refW = state.getPageRefWidth(
            state.cropPageIndex
        )
        val rs =
            if (refW > 0f && cw > 0f) {
                cw / refW
            } else {
                1f
            }

        val left = (
            cx +
                state.cropTopLeft.x * rs
            ).toInt()
            .coerceIn(0, fullBitmap.width - 1)
        val top = (
            cy +
                state.cropTopLeft.y * rs
            ).toInt()
            .coerceIn(0, fullBitmap.height - 1)
        val right = (
            cx +
                state.cropBottomRight.x * rs
            ).toInt()
            .coerceIn(left + 1, fullBitmap.width)
        val bottom = (
            cy +
                state.cropBottomRight.y * rs
            ).toInt()
            .coerceIn(top + 1, fullBitmap.height)

        val cropped =
            android.graphics.Bitmap.createBitmap(
                fullBitmap,
                left,
                top,
                right - left,
                bottom - top
            )

        // 4. Save and copy
        val file = java.io.File(
            context.cacheDir,
            "crop_capture.png"
        )
        file.outputStream().use {
            cropped.compress(
                android.graphics.Bitmap
                    .CompressFormat.PNG,
                100,
                it
            )
        }
        val uri =
            androidx.core.content.FileProvider
                .getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
        val cm = context.getSystemService(
            Context.CLIPBOARD_SERVICE
        ) as android.content.ClipboardManager
        cm.setPrimaryClip(
            android.content.ClipData.newUri(
                context.contentResolver,
                "capture",
                uri
            )
        )

        fullBitmap.recycle()
        state.isStylusTouching = false
    } catch (_: Throwable) {
        state.isCropping = false
        state.isStylusTouching = false
    }
}

// ── Utility ──────────────────────────────────────

private fun disallowParentIntercept(view: View, disallow: Boolean) {
    try {
        var parent = view.parent
        while (parent != null) {
            parent
                .requestDisallowInterceptTouchEvent(
                    disallow
                )
            parent = parent.parent
        }
    } catch (_: Throwable) { }
}

/** Convert MotionEvent to StrokePoint. */
private fun pt(event: MotionEvent, iz: Float): StrokePoint {
    return StrokePoint(
        event.x * iz,
        event.y * iz,
        event.pressure.coerceIn(UxConfig.Drawing.PRESSURE_MIN, UxConfig.Drawing.PRESSURE_MAX),
        true
    )
}

private fun addHistorical(event: MotionEvent, target: MutableList<StrokePoint>, iz: Float) {
    for (h in 0 until event.historySize) {
        target += StrokePoint(
            event.getHistoricalX(h) * iz,
            event.getHistoricalY(h) * iz,
            event.getHistoricalPressure(h)
                .coerceIn(UxConfig.Drawing.PRESSURE_MIN, UxConfig.Drawing.PRESSURE_MAX),
            true
        )
    }
}
