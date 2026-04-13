package com.maestro.app.ui.drawing

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.maestro.app.domain.model.DrawingTool
import com.maestro.app.domain.model.EraserMode
import com.maestro.app.domain.model.InkStroke
import com.maestro.app.domain.model.LassoPhase
import com.maestro.app.domain.model.StrokePoint
import com.maestro.app.ui.config.UxConfig
import com.maestro.app.ui.theme.MaestroError
import com.maestro.app.ui.theme.MaestroPrimary

val InkColors = listOf(
    MaestroPrimary,
    MaestroError,
    Color(0xFF1B5E20),
    Color(0xFF1A1C1F),
    Color(0xFFE65100)
)
val PenWidths = UxConfig.Drawing.PEN_WIDTHS
val EraserWidths = UxConfig.Drawing.ERASER_WIDTHS

class DrawingState {
    private val pageStrokesMap =
        mutableMapOf<Int, SnapshotStateList<InkStroke>>()
    private val undoStack = mutableStateListOf<Pair<Int, InkStroke>>()
    private val redoStack = mutableStateListOf<Pair<Int, InkStroke>>()

    var activePageIndex by mutableIntStateOf(-1)
    val currentPoints = mutableStateListOf<StrokePoint>()

    var activeTool by mutableStateOf(DrawingTool.PEN)
    var activeColor by mutableStateOf(InkColors[0])
    var activeWidth by mutableFloatStateOf(PenWidths[UxConfig.Drawing.DEFAULT_PEN_WIDTH_INDEX])

    var eraserMode by mutableStateOf(EraserMode.STROKE)
    var eraserWidth by mutableFloatStateOf(
        EraserWidths[UxConfig.Drawing.DEFAULT_ERASER_WIDTH_INDEX]
    )

    var sPenButtonPressed by mutableStateOf(false)
    var isStylusActive by mutableStateOf(false)
    var isStylusTouching by mutableStateOf(false)

    /** Increments on every mutation — used to trigger annotation saves */
    var annotationVersion by mutableIntStateOf(0)

    /** Zoom scale for the PDF viewer (1.0 = normal) */
    var zoomScale by mutableFloatStateOf(UxConfig.Canvas.ZOOM_MIN)

    /** Pen long-press paste request */
    var penPasteRequest by mutableStateOf<Offset?>(null)
    var penPasteScreenPos by mutableStateOf<Offset?>(null)
    var penPastePageIndex by mutableIntStateOf(-1)

    /** Image overlays on pages */
    data class ImageOverlay(
        val bitmap: Bitmap,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    )

    private val pageImages =
        mutableMapOf<Int, MutableList<ImageOverlay>>()

    fun imagesForPage(page: Int): List<ImageOverlay> = pageImages[page] ?: emptyList()

    fun getAllImagePageIndices(): Set<Int> = pageImages.keys

    fun addImage(page: Int, overlay: ImageOverlay) {
        pageImages.getOrPut(page) { mutableListOf() }.add(overlay)
        annotationVersion++
    }

    fun removeImage(page: Int, overlay: ImageOverlay) {
        pageImages[page]?.remove(overlay)
        annotationVersion++
    }

    fun replaceImage(page: Int, old: ImageOverlay, new: ImageOverlay) {
        val list = pageImages[page] ?: return
        val idx = list.indexOf(old)
        if (idx >= 0) list[idx] = new
        annotationVersion++
    }

    /** Selected image overlay state */
    var selectedImage by mutableStateOf<ImageOverlay?>(null)
    var selectedImagePage by mutableIntStateOf(-1)

    /** Screen crop mode */
    var isCropping by mutableStateOf(false)
    var cropPageIndex by mutableIntStateOf(-1)
    var cropTopLeft by mutableStateOf(
        Offset(UxConfig.Crop.INITIAL_TOP_LEFT_X, UxConfig.Crop.INITIAL_TOP_LEFT_Y)
    )
    var cropBottomRight by mutableStateOf(
        Offset(UxConfig.Crop.INITIAL_BOTTOM_RIGHT_X, UxConfig.Crop.INITIAL_BOTTOM_RIGHT_Y)
    )

    /** Eraser position for circle visualization */
    var eraserIndicator by mutableStateOf<Offset?>(null)
    var eraserIndicatorPage by mutableIntStateOf(-1)

    /** Per-page reference canvas width for coordinate normalization */
    private val pageRefWidths = mutableMapOf<Int, Float>()

    fun getPageRefWidth(page: Int): Float = pageRefWidths[page] ?: 0f

    fun setPageRefWidth(page: Int, width: Float) {
        if (page !in pageRefWidths && width > 0f) {
            pageRefWidths[page] = width
        }
    }

    fun setPageRefWidthIfMissing(page: Int, width: Float) {
        if (page !in pageRefWidths && width > 0f) {
            pageRefWidths[page] = width
        }
    }

    // ── Lasso state ──
    var lassoPhase by mutableStateOf(LassoPhase.IDLE)
    val lassoPoints = mutableStateListOf<StrokePoint>()
    val selectedStrokes = mutableStateListOf<InkStroke>()
    var selectionPageIndex by mutableIntStateOf(-1)
    var dragStart by mutableStateOf(Offset.Zero)
    var dragOffset by mutableStateOf(Offset.Zero)

    fun strokesForPage(pageIndex: Int): SnapshotStateList<InkStroke> =
        pageStrokesMap.getOrPut(pageIndex) { mutableStateListOf() }

    fun getAllPageIndices(): Set<Int> = pageStrokesMap.keys

    fun loadPageStrokes(pageIndex: Int, strokes: List<InkStroke>) {
        strokesForPage(pageIndex).apply {
            clear()
            addAll(strokes)
        }
    }

    fun commitStroke() {
        if (currentPoints.isEmpty() || activePageIndex < 0) return
        val stroke = InkStroke(
            currentPoints.toList(),
            activeColor,
            activeWidth
        )
        strokesForPage(activePageIndex) += stroke
        undoStack += Pair(activePageIndex, stroke)
        currentPoints.clear()
        redoStack.clear()
        annotationVersion++
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val (page, stroke) = undoStack.removeLast()
        strokesForPage(page).remove(stroke)
        redoStack += Pair(page, stroke)
        annotationVersion++
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val (page, stroke) = redoStack.removeLast()
        strokesForPage(page) += stroke
        undoStack += Pair(page, stroke)
        annotationVersion++
    }

    fun eraseAt(pageIndex: Int, x: Float, y: Float) {
        val strokes = strokesForPage(pageIndex)
        val radius = eraserWidth
        when (eraserMode) {
            EraserMode.STROKE -> {
                val toRemove = strokes.filter { s ->
                    s.points.any { p ->
                        distSq(p.x, p.y, x, y) < radius * radius
                    }
                }
                if (toRemove.isEmpty()) return
                strokes.removeAll(toRemove)
                annotationVersion++
            }
            EraserMode.PARTIAL -> {
                val radiusSq = radius * radius
                val toProcess = strokes.filter { s ->
                    s.points.any { p ->
                        distSq(p.x, p.y, x, y) < radiusSq
                    }
                }
                if (toProcess.isEmpty()) return
                val newStrokes = mutableListOf<InkStroke>()
                toProcess.forEach { stroke ->
                    var seg = mutableListOf<StrokePoint>()
                    for (pt in stroke.points) {
                        if (distSq(pt.x, pt.y, x, y) < radiusSq) {
                            if (seg.size >= 2) {
                                newStrokes += stroke.copy(
                                    points = seg.toList()
                                )
                            }
                            seg = mutableListOf()
                        } else {
                            seg += pt
                        }
                    }
                    if (seg.size >= 2) {
                        newStrokes += stroke.copy(
                            points = seg.toList()
                        )
                    }
                }
                strokes.removeAll(toProcess)
                strokes.addAll(newStrokes)
            }
        }
        annotationVersion++
    }

    // ── Lasso operations ──

    fun completeLasso(pageIndex: Int) {
        if (lassoPoints.size < 3) {
            clearLasso()
            return
        }
        val polygon = lassoPoints.map { Offset(it.x, it.y) }
        val strokes = strokesForPage(pageIndex)
        val inside = strokes.filter { stroke ->
            val insideCount = stroke.points.count { pt ->
                isPointInPolygon(Offset(pt.x, pt.y), polygon)
            }
            insideCount > stroke.points.size / 2
        }
        if (inside.isEmpty()) {
            clearLasso()
            return
        }
        selectedStrokes.clear()
        selectedStrokes.addAll(inside)
        selectionPageIndex = pageIndex
        lassoPhase = LassoPhase.SELECTED
        dragOffset = Offset.Zero
    }

    fun commitDrag() {
        if (selectedStrokes.isEmpty() || dragOffset == Offset.Zero) {
            lassoPhase = LassoPhase.SELECTED
            return
        }
        val strokes = strokesForPage(selectionPageIndex)
        val dx = dragOffset.x
        val dy = dragOffset.y
        val moved = selectedStrokes.map { stroke ->
            stroke.copy(
                points = stroke.points.map { pt ->
                    pt.copy(x = pt.x + dx, y = pt.y + dy)
                }
            )
        }
        strokes.removeAll(selectedStrokes.toSet())
        strokes.addAll(moved)
        selectedStrokes.clear()
        selectedStrokes.addAll(moved)
        dragOffset = Offset.Zero
        lassoPhase = LassoPhase.SELECTED
        annotationVersion++
    }

    fun clearLasso() {
        lassoPoints.clear()
        selectedStrokes.clear()
        selectionPageIndex = -1
        lassoPhase = LassoPhase.IDLE
        dragOffset = Offset.Zero
    }

    fun getSelectionBounds(offset: Offset = Offset.Zero): Rect? {
        if (selectedStrokes.isEmpty()) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        selectedStrokes.forEach { stroke ->
            stroke.points.forEach { pt ->
                val x = pt.x + offset.x
                val y = pt.y + offset.y
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
            }
        }
        val pad = UxConfig.Drawing.SELECTION_BOUNDS_PADDING
        return Rect(minX - pad, minY - pad, maxX + pad, maxY + pad)
    }

    fun isTouchInSelectionBounds(x: Float, y: Float): Boolean {
        val bounds = getSelectionBounds(dragOffset) ?: return false
        return bounds.contains(Offset(x, y))
    }

    fun deleteSelection() {
        if (selectedStrokes.isEmpty()) return
        strokesForPage(selectionPageIndex)
            .removeAll(selectedStrokes.toSet())
        clearLasso()
        annotationVersion++
    }

    fun isTouchOnSelection(x: Float, y: Float): Boolean = isTouchInSelectionBounds(x, y)
}

private fun distSq(x1: Float, y1: Float, x2: Float, y2: Float): Float =
    (x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2)

/** Ray-casting point-in-polygon test */
private fun isPointInPolygon(point: Offset, polygon: List<Offset>): Boolean {
    var inside = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        val pi = polygon[i]
        val pj = polygon[j]
        if ((pi.y > point.y) != (pj.y > point.y) &&
            point.x < (pj.x - pi.x) * (point.y - pi.y) /
            (pj.y - pi.y) + pi.x
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}
