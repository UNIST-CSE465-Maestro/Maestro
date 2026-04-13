package com.maestro.app.domain.repository

import android.net.Uri
import com.maestro.app.domain.model.Folder
import com.maestro.app.domain.model.PdfDocument

interface DocumentRepository {
    suspend fun loadDocuments(): List<PdfDocument>
    suspend fun loadFolders(): List<Folder>
    suspend fun importPdf(uri: Uri, displayName: String): PdfDocument
    suspend fun deleteDocument(documentId: String)
    suspend fun renameDocument(documentId: String, newName: String)
    suspend fun moveDocument(documentId: String, targetFolderId: String?)
    suspend fun createFolder(name: String, parentId: String? = null): Folder
    suspend fun deleteFolder(folderId: String)
    suspend fun renameFolder(folderId: String, newName: String)
    suspend fun moveFolder(folderId: String, newParentId: String?)
}
