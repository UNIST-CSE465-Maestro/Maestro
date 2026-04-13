package com.maestro.app.domain.repository

import android.graphics.Bitmap
import com.maestro.app.domain.model.InkStroke

interface AnnotationRepository {
    suspend fun loadStrokes(documentId: String, pageIndex: Int): List<InkStroke>
    suspend fun saveStrokes(
        documentId: String,
        pageIndex: Int,
        strokes: List<InkStroke>,
        refWidth: Float
    )
    suspend fun loadImageOverlays(documentId: String, pageIndex: Int): List<ImageOverlayData>
    suspend fun saveImageOverlays(
        documentId: String,
        pageIndex: Int,
        overlays: List<ImageOverlayData>
    )

    data class ImageOverlayData(
        val bitmap: Bitmap,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    )
}
