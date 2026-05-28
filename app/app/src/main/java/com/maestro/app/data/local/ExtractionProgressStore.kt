package com.maestro.app.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ExtractionSourceProgress(
    val label: String,
    val progress: Int,
    val status: ExtractionSourceStatus =
        ExtractionSourceStatus.RUNNING,
    val error: String? = null
)

enum class ExtractionSourceStatus {
    RUNNING,
    DONE,
    FAILED
}

class ExtractionProgressStore {
    private val _activeDocumentIds =
        MutableStateFlow<Set<String>>(emptySet())
    val activeDocumentIds: StateFlow<Set<String>> =
        _activeDocumentIds.asStateFlow()

    private val _progress =
        MutableStateFlow<Map<String, Int>>(emptyMap())
    val progress: StateFlow<Map<String, Int>> =
        _progress.asStateFlow()

    private val _sourceProgress =
        MutableStateFlow<
            Map<String, Map<String, ExtractionSourceProgress>>
            >(emptyMap())
    val sourceProgress:
        StateFlow<
            Map<String, Map<String, ExtractionSourceProgress>>
            > = _sourceProgress.asStateFlow()

    fun update(documentId: String, progress: Int) {
        _activeDocumentIds.value =
            _activeDocumentIds.value + documentId
        _progress.value = _progress.value + (
            documentId to progress.coerceIn(0, 100)
            )
    }

    fun updateSource(
        documentId: String,
        sourceId: String,
        label: String,
        progress: Int,
        status: ExtractionSourceStatus =
            ExtractionSourceStatus.RUNNING,
        error: String? = null
    ) {
        _activeDocumentIds.value =
            _activeDocumentIds.value + documentId
        val current = _sourceProgress.value[documentId]
            .orEmpty()
        val next = current + (
            sourceId to ExtractionSourceProgress(
                label = label,
                progress = progress.coerceIn(0, 100),
                status = status,
                error = error
            )
            )
        _sourceProgress.value =
            _sourceProgress.value + (documentId to next)
        val aggregate = next.values
            .map { it.progress }
            .average()
            .takeIf { !it.isNaN() }
            ?.toInt()
            ?: progress
        _progress.value =
            _progress.value + (documentId to aggregate)
    }

    fun markSourceFailed(
        documentId: String,
        sourceId: String,
        label: String,
        error: String?
    ) {
        val current = _sourceProgress.value[documentId]
            ?.get(sourceId)
        updateSource(
            documentId = documentId,
            sourceId = sourceId,
            label = label,
            progress = current?.progress ?: 0,
            status = ExtractionSourceStatus.FAILED,
            error = error
        )
    }

    fun clear(documentId: String) {
        _activeDocumentIds.value =
            _activeDocumentIds.value - documentId
        _progress.value = _progress.value - documentId
        _sourceProgress.value =
            _sourceProgress.value - documentId
    }
}
