package com.maestro.app.domain.model

data class PdfDocument(
    val id: String,
    val uriString: String,
    val displayName: String,
    val pageCount: Int,
    val folderId: String? = null,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val extractionStatus: ExtractionStatus = ExtractionStatus.NONE,
    val extractionMode: String? = null,
    val isPinned: Boolean = false,
    val bookmarkedPages: Set<Int> = emptySet()
)

enum class ExtractionStatus {
    NONE,
    EXTRACTING,
    DONE,
    FAILED
}
