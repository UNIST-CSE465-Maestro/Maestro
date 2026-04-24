package com.maestro.app.data.local

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class StudyEventType {
    DOCUMENT_OPENED,
    PAGE_VIEWED,
    BOOKMARK_TOGGLED,
    ANNOTATION_SAVED,
    LLM_REQUESTED,
    QUIZ_REQUESTED,
    QUIZ_ANSWERED
}

@Serializable
data class StudyEvent(
    val id: String,
    val type: StudyEventType,
    val timestamp: Long,
    val documentId: String? = null,
    val pageIndex: Int? = null,
    val conceptIds: List<String> = emptyList(),
    val correctness: Boolean? = null,
    val promptLength: Int? = null,
    val metadata: Map<String, String> = emptyMap()
)

class StudyEventLocalDataSource {
    private val eventFile: File
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    constructor(context: Context) : this(
        File(context.filesDir, "study_events/events.json")
    )

    internal constructor(eventFile: File) {
        this.eventFile = eventFile
        eventFile.parentFile?.mkdirs()
    }

    @Synchronized
    fun append(
        type: StudyEventType,
        documentId: String? = null,
        pageIndex: Int? = null,
        conceptIds: List<String> = emptyList(),
        correctness: Boolean? = null,
        promptLength: Int? = null,
        metadata: Map<String, String> = emptyMap(),
        timestamp: Long = System.currentTimeMillis()
    ): StudyEvent {
        val event = StudyEvent(
            id = UUID.randomUUID().toString(),
            type = type,
            timestamp = timestamp,
            documentId = documentId,
            pageIndex = pageIndex,
            conceptIds = conceptIds,
            correctness = correctness,
            promptLength = promptLength,
            metadata = metadata
        )
        save(listEvents() + event)
        return event
    }

    @Synchronized
    fun listEvents(): List<StudyEvent> {
        if (!eventFile.exists()) return emptyList()
        return try {
            json.decodeFromString<List<StudyEvent>>(
                eventFile.readText()
            )
        } catch (_: Throwable) {
            emptyList()
        }
    }

    @Synchronized
    fun clear() {
        eventFile.delete()
    }

    private fun save(events: List<StudyEvent>) {
        eventFile.parentFile?.mkdirs()
        eventFile.writeText(json.encodeToString(events))
    }
}
