package com.maestro.app.fake

import android.net.Uri
import com.maestro.app.domain.model.Folder
import com.maestro.app.domain.model.PdfDocument
import com.maestro.app.domain.repository.DocumentRepository
import java.util.UUID

/**
 * In-memory DocumentRepository for ViewModel tests.
 * Maintains real state so "save then load" flows work naturally.
 */
class FakeDocumentRepository : DocumentRepository {

    val docs = mutableListOf<PdfDocument>()
    val folders = mutableListOf<Folder>()

    override suspend fun loadDocuments(): List<PdfDocument> = docs.toList()

    override suspend fun loadFolders(): List<Folder> = folders.toList()

    override suspend fun importPdf(uri: Uri, displayName: String): PdfDocument {
        val doc = PdfDocument(
            id = UUID.randomUUID().toString(),
            uriString = uri.toString(),
            displayName = displayName,
            pageCount = 1
        )
        docs += doc
        return doc
    }

    override suspend fun deleteDocument(documentId: String) {
        docs.removeAll { it.id == documentId }
    }

    override suspend fun renameDocument(documentId: String, newName: String) {
        val idx = docs.indexOfFirst { it.id == documentId }
        if (idx >= 0) {
            docs[idx] = docs[idx].copy(displayName = newName)
        }
    }

    override suspend fun moveDocument(documentId: String, targetFolderId: String?) {
        val idx = docs.indexOfFirst { it.id == documentId }
        if (idx >= 0) {
            docs[idx] = docs[idx].copy(folderId = targetFolderId)
        }
    }

    override suspend fun createFolder(name: String, parentId: String?): Folder {
        val folder = Folder(
            id = UUID.randomUUID().toString(),
            name = name,
            parentId = parentId
        )
        folders += folder
        return folder
    }

    override suspend fun deleteFolder(folderId: String) {
        folders.removeAll { it.id == folderId }
        docs.replaceAll {
            if (it.folderId == folderId) {
                it.copy(folderId = null)
            } else {
                it
            }
        }
    }

    override suspend fun renameFolder(folderId: String, newName: String) {
        val idx = folders.indexOfFirst { it.id == folderId }
        if (idx >= 0) {
            folders[idx] = folders[idx].copy(name = newName)
        }
    }

    override suspend fun moveFolder(folderId: String, newParentId: String?) {
        val idx = folders.indexOfFirst { it.id == folderId }
        if (idx >= 0) {
            folders[idx] = folders[idx].copy(
                parentId = newParentId
            )
        }
    }
}
