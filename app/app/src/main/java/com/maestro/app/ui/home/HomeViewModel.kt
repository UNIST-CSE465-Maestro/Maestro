package com.maestro.app.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maestro.app.data.local.ExtractionProgressStore
import com.maestro.app.data.local.PdfMerger
import com.maestro.app.data.work.ExtractionWorkScheduler
import com.maestro.app.domain.model.ExtractionStatus
import com.maestro.app.domain.model.Folder
import com.maestro.app.domain.model.PdfDocument
import com.maestro.app.domain.repository.DocumentRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    private val repository: DocumentRepository,
    private val pdfMerger: PdfMerger,
    private val extractionProgressStore: ExtractionProgressStore,
    private val extractionWorkScheduler: ExtractionWorkScheduler,
    private val appContext: Context
) : ViewModel() {

    private val _documents =
        MutableStateFlow<List<PdfDocument>>(emptyList())
    val documents: StateFlow<List<PdfDocument>> =
        _documents.asStateFlow()

    private val _folders =
        MutableStateFlow<List<Folder>>(emptyList())
    val folders: StateFlow<List<Folder>> =
        _folders.asStateFlow()

    private val _currentFolderId =
        MutableStateFlow<String?>(null)
    val currentFolderId: StateFlow<String?> =
        _currentFolderId.asStateFlow()

    private val _selectedDocIds =
        MutableStateFlow<Set<String>>(emptySet())
    val selectedDocIds: StateFlow<Set<String>> =
        _selectedDocIds.asStateFlow()

    val isMultiSelectMode: StateFlow<Boolean> =
        _selectedDocIds.map { it.isNotEmpty() }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(),
                false
            )

    val extractingDocIds: StateFlow<Set<String>> =
        extractionProgressStore.activeDocumentIds

    val extractionProgress: StateFlow<Map<String, Int>> =
        extractionProgressStore.progress

    private val _docsWithExtractedContent =
        MutableStateFlow<Set<String>>(emptySet())
    val docsWithExtractedContent: StateFlow<Set<String>> =
        _docsWithExtractedContent.asStateFlow()

    // Pending URI waiting for mode selection
    private val _pendingImportUri =
        MutableStateFlow<Uri?>(null)
    val pendingImportUri: StateFlow<Uri?> =
        _pendingImportUri.asStateFlow()

    // Extraction error message
    private val _extractionError =
        MutableStateFlow<String?>(null)
    val extractionError: StateFlow<String?> =
        _extractionError.asStateFlow()

    init {
        loadDocuments()
        loadFolders()
        observeExtractionChanges()
        resumeExtracting()
        recoverFailedExtractions()
    }

    private fun loadDocuments() {
        viewModelScope.launch {
            val docs = repository.loadDocuments()
                .sortedWith(
                    compareByDescending<PdfDocument> {
                        it.isPinned
                    }.thenByDescending { it.addedTimestamp }
                )
            _documents.value = docs
            _docsWithExtractedContent.value =
                docs.filter { hasExtractedContent(it.id) }
                    .map { it.id }
                    .toSet()
        }
    }

    private fun loadFolders() {
        viewModelScope.launch {
            _folders.value = repository.loadFolders()
        }
    }

    private fun refresh() {
        loadDocuments()
        loadFolders()
    }

    private fun observeExtractionChanges() {
        viewModelScope.launch {
            extractionProgressStore.activeDocumentIds
                .drop(1)
                .collect {
                    refresh()
                }
        }
    }

    fun navigateFolder(folderId: String?) {
        _currentFolderId.value = folderId
    }

    /**
     * Stage a URI for import — shows mode selection dialog.
     */
    fun stagePdfImport(uri: Uri) {
        _pendingImportUri.value = uri
    }

    /**
     * Cancel pending import.
     */
    fun clearExtractionError() {
        _extractionError.value = null
    }

    fun cancelPdfImport() {
        _pendingImportUri.value = null
    }

    /**
     * Import PDF and start background extraction.
     */
    fun importAndExtract(uri: Uri, mode: String) {
        _pendingImportUri.value = null
        viewModelScope.launch {
            val doc = repository.importPdf(
                uri,
                "document.pdf"
            )
            refresh()
            // Use the imported file URI, not the
            // original content:// URI which may
            // have expired permissions
            extractInBackground(doc, doc.uriString, mode)
        }
    }

    fun retryExtraction(documentId: String, mode: String) {
        viewModelScope.launch {
            val doc = repository.loadDocuments()
                .find { it.id == documentId }
                ?: return@launch
            extractInBackground(doc, doc.uriString, mode)
        }
    }

    private fun resumeExtracting() {
        viewModelScope.launch {
            val docs = repository.loadDocuments()
            docs.filter {
                it.extractionStatus ==
                    ExtractionStatus.EXTRACTING &&
                    it.extractionMode != null
            }.forEach { doc ->
                extractInBackground(
                    doc,
                    doc.uriString,
                    doc.extractionMode!!,
                    replaceExisting = false
                )
            }
        }
    }

    private fun recoverFailedExtractions() {
        viewModelScope.launch {
            val docs = repository.loadDocuments()
            docs.filter {
                it.extractionStatus == ExtractionStatus.FAILED &&
                    !hasExtractedContent(it.id)
            }.forEach { doc ->
                extractInBackground(
                    doc,
                    doc.uriString,
                    doc.extractionMode ?: DEFAULT_RECOVERY_MODE,
                    replaceExisting = false
                )
            }
        }
    }

    private fun extractInBackground(
        doc: PdfDocument,
        uriString: String,
        mode: String,
        replaceExisting: Boolean = true
    ) {
        viewModelScope.launch {
            extractionProgressStore.update(doc.id, 1)
            repository.updateDocument(
                latestDocument(doc.id, doc).copy(
                    extractionStatus =
                    ExtractionStatus.EXTRACTING,
                    extractionMode = mode
                )
            )
            extractionWorkScheduler.enqueue(
                documentId = doc.id,
                uriString = uriString,
                mode = mode,
                replaceExisting = replaceExisting
            )
            refresh()
        }
    }

    private suspend fun latestDocument(
        documentId: String,
        fallback: PdfDocument
    ): PdfDocument {
        return repository.loadDocuments()
            .find { it.id == documentId }
            ?: fallback
    }

    private suspend fun hasExtractedContent(documentId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val file = File(
                    appContext.filesDir,
                    "documents/$documentId/content.md"
                )
                file.exists() && file.length() > 0L
            } catch (_: Throwable) {
                false
            }
        }

    fun importPdf(uri: Uri) {
        viewModelScope.launch {
            repository.importPdf(uri, "document.pdf")
            refresh()
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            repository.createFolder(name, _currentFolderId.value)
            refresh()
        }
    }

    fun renameDocument(id: String, name: String) {
        viewModelScope.launch {
            repository.renameDocument(id, name)
            refresh()
        }
    }

    fun duplicateDocument(id: String) {
        viewModelScope.launch {
            repository.duplicateDocument(id)
            refresh()
        }
    }

    fun deleteDocument(id: String) {
        viewModelScope.launch {
            repository.deleteDocument(id)
            refresh()
        }
    }

    fun isFolderNonEmpty(id: String): Boolean {
        val hasDocs = _documents.value
            .any { it.folderId == id }
        val hasSubfolders = _folders.value
            .any { it.parentId == id }
        return hasDocs || hasSubfolders
    }

    fun deleteFolder(id: String) {
        viewModelScope.launch {
            // Delete contained documents
            _documents.value
                .filter { it.folderId == id }
                .forEach {
                    repository.deleteDocument(it.id)
                }
            // Delete subfolders recursively
            _folders.value
                .filter { it.parentId == id }
                .forEach { deleteFolder(it.id) }
            repository.deleteFolder(id)
            refresh()
        }
    }

    fun renameFolder(id: String, name: String) {
        viewModelScope.launch {
            repository.renameFolder(id, name)
            refresh()
        }
    }

    fun moveDocument(docId: String, targetFolderId: String?) {
        viewModelScope.launch {
            repository.moveDocument(docId, targetFolderId)
            refresh()
        }
    }

    fun moveFolder(folderId: String, targetParentId: String?) {
        viewModelScope.launch {
            val allFolders = _folders.value
            var cur: String? = targetParentId
            while (cur != null) {
                if (cur == folderId) return@launch
                cur = allFolders
                    .find { it.id == cur }?.parentId
            }
            repository.moveFolder(
                folderId,
                targetParentId
            )
            refresh()
        }
    }

    fun togglePin(docId: String) {
        viewModelScope.launch {
            val doc = _documents.value
                .find { it.id == docId } ?: return@launch
            repository.updateDocument(
                doc.copy(isPinned = !doc.isPinned)
            )
            refresh()
        }
    }

    fun toggleSelect(docId: String) {
        _selectedDocIds.value = _selectedDocIds.value
            .toMutableSet().apply {
                if (contains(docId)) {
                    remove(docId)
                } else {
                    add(docId)
                }
            }
    }

    fun clearSelection() {
        _selectedDocIds.value = emptySet()
    }

    fun mergeOrdered(orderedIds: List<String>) {
        viewModelScope.launch {
            if (orderedIds.size < 2) return@launch
            val docMap = _documents.value
                .associateBy { it.id }
            val docs = orderedIds.mapNotNull {
                docMap[it]
            }
            if (docs.size < 2) return@launch

            val uris = docs.map {
                Uri.parse(it.uriString)
            }
            val outputName = docs.first().displayName
                .removeSuffix(".pdf") +
                " 외 ${docs.size - 1}건 병합.pdf"

            val resultUri = pdfMerger.merge(
                uris, outputName
            ) ?: return@launch

            repository.importPdf(resultUri, outputName)
            _selectedDocIds.value = emptySet()
            refresh()
        }
    }

    private companion object {
        const val DEFAULT_RECOVERY_MODE = "ai"
    }
}
