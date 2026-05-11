package com.maestro.app.ui.viewer

data class OpenPdfTab(
    val documentId: String,
    val title: String,
    val pageCount: Int,
    val uriString: String
)

data class PdfTabViewportState(
    val firstVisiblePageIndex: Int = 0,
    val firstVisiblePageScrollOffset: Int = 0
)
