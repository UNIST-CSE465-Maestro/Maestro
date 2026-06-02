package com.maestro.app.domain.model

data class CropCapturePayload(
    val imageBytes: ByteArray,
    val pageIndex: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val pageRefWidth: Float,
    val pageRefHeight: Float
)
