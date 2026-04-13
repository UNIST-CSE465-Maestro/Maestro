package com.maestro.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.maestro.app.domain.model.InkStroke
import com.maestro.app.domain.model.StrokePoint
import com.maestro.app.domain.repository.AnnotationRepository
import com.maestro.app.domain.repository.AnnotationRepository.ImageOverlayData
import com.maestro.app.ui.drawing.DrawingState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class StrokeDto(
    val color: Int,
    val width: Double,
    val pts: List<PointDto>
)

@Serializable
private data class PointDto(
    val x: Double,
    val y: Double,
    val p: Double
)

@Serializable
private data class ImageDto(
    val file: String,
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double
)

@Serializable
private data class PageAnnotations(
    val strokes: Map<String, List<StrokeDto>> = emptyMap(),
    val refWidths: Map<String, Double> = emptyMap(),
    val images: Map<String, List<ImageDto>> = emptyMap()
)

class AnnotationRepositoryImpl(
    context: Context
) : AnnotationRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }
    private val dir =
        File(context.filesDir, "annotations").also { it.mkdirs() }
    private val imgDir =
        File(context.filesDir, "annotations/images").also { it.mkdirs() }

    override suspend fun loadStrokes(documentId: String, pageIndex: Int): List<InkStroke> =
        withContext(Dispatchers.IO) {
            val annotations = loadAnnotations(documentId)
                ?: return@withContext emptyList()
            val dtos = annotations.strokes[pageIndex.toString()]
                ?: return@withContext emptyList()
            dtos.map { dto ->
                InkStroke(
                    points = dto.pts.map { p ->
                        StrokePoint(
                            p.x.toFloat(),
                            p.y.toFloat(),
                            p.p.toFloat()
                        )
                    },
                    color = Color(dto.color),
                    baseWidth = dto.width.toFloat()
                )
            }
        }

    override suspend fun saveStrokes(
        documentId: String,
        pageIndex: Int,
        strokes: List<InkStroke>,
        refWidth: Float
    ) = withContext(Dispatchers.IO) {
        val existing = loadAnnotations(documentId)
            ?: PageAnnotations()
        val strokeDtos = strokes.map { stroke ->
            StrokeDto(
                color = stroke.color.toArgb(),
                width = stroke.baseWidth.toDouble(),
                pts = stroke.points.map { pt ->
                    PointDto(
                        pt.x.toDouble(),
                        pt.y.toDouble(),
                        pt.pressure.toDouble()
                    )
                }
            )
        }
        val newStrokes = existing.strokes.toMutableMap()
        newStrokes[pageIndex.toString()] = strokeDtos
        val newRefWidths = existing.refWidths.toMutableMap()
        if (refWidth > 0f) {
            newRefWidths[pageIndex.toString()] = refWidth.toDouble()
        }
        val updated = existing.copy(
            strokes = newStrokes,
            refWidths = newRefWidths
        )
        saveAnnotations(documentId, updated)
    }

    override suspend fun loadImageOverlays(
        documentId: String,
        pageIndex: Int
    ): List<ImageOverlayData> = withContext(Dispatchers.IO) {
        val annotations = loadAnnotations(documentId)
            ?: return@withContext emptyList()
        val dtos = annotations.images[pageIndex.toString()]
            ?: return@withContext emptyList()
        dtos.mapNotNull { dto ->
            val imgFile = File(imgDir, dto.file)
            if (!imgFile.exists()) return@mapNotNull null
            val bmp = BitmapFactory.decodeFile(imgFile.absolutePath)
                ?: return@mapNotNull null
            ImageOverlayData(
                bitmap = bmp,
                x = dto.x.toFloat(),
                y = dto.y.toFloat(),
                width = dto.w.toFloat(),
                height = dto.h.toFloat()
            )
        }
    }

    override suspend fun saveImageOverlays(
        documentId: String,
        pageIndex: Int,
        overlays: List<ImageOverlayData>
    ) = withContext(Dispatchers.IO) {
        val existing = loadAnnotations(documentId)
            ?: PageAnnotations()
        val imgDtos = overlays.mapIndexed { i, img ->
            val imgFile = File(
                imgDir,
                "${documentId}_p${pageIndex}_i$i.png"
            )
            imgFile.outputStream().use {
                img.bitmap.compress(Bitmap.CompressFormat.PNG, 90, it)
            }
            ImageDto(
                file = imgFile.name,
                x = img.x.toDouble(),
                y = img.y.toDouble(),
                w = img.width.toDouble(),
                h = img.height.toDouble()
            )
        }
        val newImages = existing.images.toMutableMap()
        newImages[pageIndex.toString()] = imgDtos
        val updated = existing.copy(images = newImages)
        saveAnnotations(documentId, updated)
    }

    /**
     * Bulk save from DrawingState — used by ViewerViewModel
     * for the debounced auto-save.
     */
    fun saveAll(documentId: String, state: DrawingState) {
        val strokes = mutableMapOf<String, List<StrokeDto>>()
        val refWidths = mutableMapOf<String, Double>()
        val images = mutableMapOf<String, List<ImageDto>>()

        state.getAllPageIndices().forEach { pageIndex ->
            val strokeDtos = state.strokesForPage(pageIndex).map { s ->
                StrokeDto(
                    color = s.color.toArgb(),
                    width = s.baseWidth.toDouble(),
                    pts = s.points.map { pt ->
                        PointDto(
                            pt.x.toDouble(),
                            pt.y.toDouble(),
                            pt.pressure.toDouble()
                        )
                    }
                )
            }
            strokes[pageIndex.toString()] = strokeDtos
            val rw = state.getPageRefWidth(pageIndex)
            if (rw > 0f) {
                refWidths[pageIndex.toString()] = rw.toDouble()
            }
        }

        state.getAllImagePageIndices().forEach { pageIndex ->
            val imgDtos = state.imagesForPage(pageIndex)
                .mapIndexed { i, img ->
                    val imgFile = File(
                        imgDir,
                        "${documentId}_p${pageIndex}_i$i.png"
                    )
                    imgFile.outputStream().use {
                        img.bitmap.compress(
                            Bitmap.CompressFormat.PNG,
                            90,
                            it
                        )
                    }
                    ImageDto(
                        file = imgFile.name,
                        x = img.x.toDouble(),
                        y = img.y.toDouble(),
                        w = img.width.toDouble(),
                        h = img.height.toDouble()
                    )
                }
            images[pageIndex.toString()] = imgDtos
        }

        saveAnnotations(
            documentId,
            PageAnnotations(strokes, refWidths, images)
        )
    }

    /**
     * Bulk load into DrawingState — used by ViewerViewModel.
     */
    fun loadAll(documentId: String, state: DrawingState) {
        val annotations = loadAnnotations(documentId) ?: return

        annotations.strokes.forEach { (key, dtos) ->
            val pageIndex = key.toIntOrNull() ?: return@forEach
            val inkStrokes = dtos.map { dto ->
                InkStroke(
                    points = dto.pts.map { p ->
                        StrokePoint(
                            p.x.toFloat(),
                            p.y.toFloat(),
                            p.p.toFloat()
                        )
                    },
                    color = Color(dto.color),
                    baseWidth = dto.width.toFloat()
                )
            }
            state.loadPageStrokes(pageIndex, inkStrokes)
            val rw = annotations.refWidths[key]?.toFloat() ?: 0f
            if (rw > 0f) state.setPageRefWidthIfMissing(pageIndex, rw)
        }

        annotations.images.forEach { (key, dtos) ->
            val pageIndex = key.toIntOrNull() ?: return@forEach
            dtos.forEach { dto ->
                val imgFile = File(imgDir, dto.file)
                if (!imgFile.exists()) return@forEach
                val bmp = BitmapFactory.decodeFile(
                    imgFile.absolutePath
                ) ?: return@forEach
                state.addImage(
                    pageIndex,
                    DrawingState.ImageOverlay(
                        bmp,
                        dto.x.toFloat(),
                        dto.y.toFloat(),
                        dto.w.toFloat(),
                        dto.h.toFloat()
                    )
                )
            }
        }
    }

    private fun loadAnnotations(documentId: String): PageAnnotations? {
        val file = File(dir, "$documentId.json")
        if (!file.exists()) return null
        return try {
            json.decodeFromString<PageAnnotations>(file.readText())
        } catch (_: Throwable) {
            null
        }
    }

    private fun saveAnnotations(documentId: String, annotations: PageAnnotations) {
        File(dir, "$documentId.json")
            .writeText(json.encodeToString(annotations))
    }
}
