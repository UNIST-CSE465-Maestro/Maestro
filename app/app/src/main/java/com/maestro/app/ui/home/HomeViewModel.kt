package com.maestro.app.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maestro.app.domain.model.Folder
import com.maestro.app.domain.model.PdfDocument
import com.maestro.app.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: DocumentRepository
) : ViewModel() {

    private val _documents = MutableStateFlow<List<PdfDocument>>(emptyList())
    val documents: StateFlow<List<PdfDocument>> = _documents.asStateFlow()

    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    private val _currentFolderId = MutableStateFlow<String?>(null)
    val currentFolderId: StateFlow<String?> = _currentFolderId.asStateFlow()

    init {
        loadDocuments()
        loadFolders()
    }

    private fun loadDocuments() {
        viewModelScope.launch {
            _documents.value = repository.loadDocuments()
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

    fun navigateFolder(folderId: String?) {
        _currentFolderId.value = folderId
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

    fun deleteDocument(id: String) {
        viewModelScope.launch {
            repository.deleteDocument(id)
            refresh()
        }
    }

    fun renameFolder(id: String, name: String) {
        viewModelScope.launch {
            repository.renameFolder(id, name)
            refresh()
        }
    }

    fun deleteFolder(id: String) {
        viewModelScope.launch {
            repository.deleteFolder(id)
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
            // Prevent moving a folder into itself or its descendants
            val allFolders = _folders.value
            var cur: String? = targetParentId
            while (cur != null) {
                if (cur == folderId) return@launch
                cur = allFolders.find { it.id == cur }?.parentId
            }
            repository.moveFolder(folderId, targetParentId)
            refresh()
        }
    }
}
