package com.maestro.app.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ExtractionProgressStore {
    private val _activeDocumentIds =
        MutableStateFlow<Set<String>>(emptySet())
    val activeDocumentIds: StateFlow<Set<String>> =
        _activeDocumentIds.asStateFlow()

    private val _progress =
        MutableStateFlow<Map<String, Int>>(emptyMap())
    val progress: StateFlow<Map<String, Int>> =
        _progress.asStateFlow()

    fun update(documentId: String, progress: Int) {
        _activeDocumentIds.value =
            _activeDocumentIds.value + documentId
        _progress.value = _progress.value + (
            documentId to progress.coerceIn(0, 100)
            )
    }

    fun clear(documentId: String) {
        _activeDocumentIds.value =
            _activeDocumentIds.value - documentId
        _progress.value = _progress.value - documentId
    }
}
