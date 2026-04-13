package com.maestro.app.data.model

import com.maestro.app.domain.model.PdfDocument
import kotlinx.serialization.Serializable

@Serializable
data class DocumentDto(
    val id: String,
    val path: String,
    val name: String,
    val pages: Int,
    val folderId: String = "",
    val ts: Long = 0L
) {
    fun toDomain(): PdfDocument = PdfDocument(
        id = id,
        uriString = "file://$path",
        displayName = name,
        pageCount = pages,
        folderId = folderId.ifBlank { null },
        addedTimestamp = ts
    )

    companion object {
        fun fromDomain(doc: PdfDocument, path: String): DocumentDto = DocumentDto(
            id = doc.id,
            path = path,
            name = doc.displayName,
            pages = doc.pageCount,
            folderId = doc.folderId ?: "",
            ts = doc.addedTimestamp
        )
    }
}
