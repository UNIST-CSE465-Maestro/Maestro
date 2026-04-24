package com.maestro.app.data.model

import com.maestro.app.domain.model.ExtractionStatus
import com.maestro.app.domain.model.PdfDocument
import kotlinx.serialization.Serializable

@Serializable
data class DocumentDto(
    val id: String,
    val path: String,
    val name: String,
    val pages: Int,
    val folderId: String = "",
    val ts: Long = 0L,
    val extractionStatus: String = "none",
    val extractionMode: String = "",
    val isPinned: Boolean = false,
    val bookmarkedPages: List<Int> = emptyList()
) {
    fun toDomain(): PdfDocument = PdfDocument(
        id = id,
        uriString = "file://$path",
        displayName = name,
        pageCount = pages,
        folderId = folderId.ifBlank { null },
        addedTimestamp = ts,
        extractionStatus = try {
            ExtractionStatus.valueOf(
                extractionStatus.uppercase()
            )
        } catch (_: Throwable) {
            ExtractionStatus.NONE
        },
        extractionMode = extractionMode.ifBlank { null },
        isPinned = isPinned,
        bookmarkedPages = bookmarkedPages.toSet()
    )

    companion object {
        fun fromDomain(doc: PdfDocument, path: String): DocumentDto = DocumentDto(
            id = doc.id,
            path = path,
            name = doc.displayName,
            pages = doc.pageCount,
            folderId = doc.folderId ?: "",
            ts = doc.addedTimestamp,
            extractionStatus =
            doc.extractionStatus.name.lowercase(),
            extractionMode = doc.extractionMode ?: "",
            isPinned = doc.isPinned,
            bookmarkedPages = doc.bookmarkedPages.sorted()
        )
    }
}
