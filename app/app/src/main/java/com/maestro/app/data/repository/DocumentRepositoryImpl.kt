package com.maestro.app.data.repository

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.maestro.app.data.model.DocumentDto
import com.maestro.app.data.model.FolderDto
import com.maestro.app.domain.model.Folder
import com.maestro.app.domain.model.PdfDocument
import com.maestro.app.domain.repository.DocumentRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DocumentRepositoryImpl(private val context: Context) : DocumentRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private val pdfDir = File(context.filesDir, "pdfs").also { it.mkdirs() }
    private val metaFile = File(context.filesDir, "pdf_meta.json")
    private val foldersFile = File(context.filesDir, "folders_meta.json")

    // ── Documents ─────────────────────────────────────

    override suspend fun loadDocuments(): List<PdfDocument> = withContext(Dispatchers.IO) {
        if (!metaFile.exists()) return@withContext emptyList()
        try {
            val dtos = json.decodeFromString<List<DocumentDto>>(metaFile.readText())
            dtos.filter { File(it.path).exists() }.map { it.toDomain() }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    override suspend fun importPdf(uri: Uri, displayName: String): PdfDocument =
        withContext(Dispatchers.IO) {
            val name = queryDisplayName(uri) ?: displayName
            val id = UUID.randomUUID().toString()
            val destFile = File(pdfDir, "$id.pdf")

            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Cannot open input stream for $uri")

            val pageCount = countPages(destFile)
            val doc = PdfDocument(
                id = id,
                uriString = Uri.fromFile(destFile).toString(),
                displayName = name,
                pageCount = pageCount
            )

            val all = loadDocuments().toMutableList()
            all += doc
            saveAllDocs(all)
            doc
        }

    override suspend fun deleteDocument(documentId: String) = withContext(Dispatchers.IO) {
        val all = loadDocuments()
        val doc = all.find { it.id == documentId } ?: return@withContext
        val path = Uri.parse(doc.uriString).path
        if (path != null) File(path).delete()
        saveAllDocs(all.filter { it.id != documentId })
    }

    override suspend fun renameDocument(documentId: String, newName: String) =
        withContext(Dispatchers.IO) {
            val all = loadDocuments().map {
                if (it.id == documentId) it.copy(displayName = newName) else it
            }
            saveAllDocs(all)
        }

    override suspend fun moveDocument(documentId: String, targetFolderId: String?) =
        withContext(Dispatchers.IO) {
            val all = loadDocuments().map {
                if (it.id == documentId) it.copy(folderId = targetFolderId) else it
            }
            saveAllDocs(all)
        }

    private fun saveAllDocs(docs: List<PdfDocument>) {
        val dtos = docs.map { doc ->
            val path = Uri.parse(doc.uriString).path ?: ""
            DocumentDto.fromDomain(doc, path)
        }
        metaFile.writeText(json.encodeToString(dtos))
    }

    // ── Folders ───────────────────────────────────────

    override suspend fun loadFolders(): List<Folder> = withContext(Dispatchers.IO) {
        if (!foldersFile.exists()) return@withContext emptyList()
        try {
            json.decodeFromString<List<FolderDto>>(foldersFile.readText()).map { it.toDomain() }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    override suspend fun createFolder(name: String, parentId: String?): Folder =
        withContext(Dispatchers.IO) {
            val folder = Folder(
                id = UUID.randomUUID().toString(),
                name = name,
                parentId = parentId
            )
            val all = loadFolders().toMutableList()
            all += folder
            saveFolders(all)
            folder
        }

    override suspend fun renameFolder(folderId: String, newName: String) =
        withContext(Dispatchers.IO) {
            val all = loadFolders().map {
                if (it.id == folderId) it.copy(name = newName) else it
            }
            saveFolders(all)
        }

    override suspend fun moveFolder(folderId: String, newParentId: String?) =
        withContext(Dispatchers.IO) {
            val all = loadFolders().map {
                if (it.id == folderId) it.copy(parentId = newParentId) else it
            }
            saveFolders(all)
        }

    override suspend fun deleteFolder(folderId: String) = withContext(Dispatchers.IO) {
        saveFolders(loadFolders().filter { it.id != folderId })
        // Move orphaned documents to root
        val docs = loadDocuments().map {
            if (it.folderId == folderId) it.copy(folderId = null) else it
        }
        saveAllDocs(docs)
    }

    private fun saveFolders(folders: List<Folder>) {
        val dtos = folders.map { FolderDto.fromDomain(it) }
        foldersFile.writeText(json.encodeToString(dtos))
    }

    // ── Helpers ───────────────────────────────────────

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
        }
    }

    private fun countPages(file: File): Int {
        return try {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            val count = renderer.pageCount
            renderer.close()
            fd.close()
            count
        } catch (_: Throwable) {
            1
        }
    }
}
