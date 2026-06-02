package com.maestro.app.domain.model

data class PdfSearchMatch(
    val pageIndex: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val pageWidth: Float,
    val pageHeight: Float,
    val matchedText: String
)
