package com.maestro.app.data.local

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class MonitoringLogCategory {
    DEVICE_RESOURCE,
    KT_RUNTIME,
    LEARNING_BEHAVIOR,
    DOMAIN_EVALUATION,
    UX_RELIABILITY
}

@Serializable
data class MonitoringLogEntry(
    val id: String,
    val category: MonitoringLogCategory,
    val eventType: String,
    val timestamp: Long,
    val documentId: String? = null,
    val conceptId: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

class MonitoringLogLocalDataSource {
    private val logFile: File
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private val _logs =
        MutableStateFlow<List<MonitoringLogEntry>>(emptyList())
    val logs: StateFlow<List<MonitoringLogEntry>> =
        _logs.asStateFlow()

    constructor(context: Context) : this(
        File(context.filesDir, "monitoring/logs.json")
    )

    internal constructor(logFile: File) {
        this.logFile = logFile
        logFile.parentFile?.mkdirs()
        _logs.value = readLogs()
    }

    @Synchronized
    fun append(
        category: MonitoringLogCategory,
        eventType: String,
        documentId: String? = null,
        conceptId: String? = null,
        metadata: Map<String, String> = emptyMap(),
        timestamp: Long = System.currentTimeMillis()
    ): MonitoringLogEntry {
        val entry = MonitoringLogEntry(
            id = UUID.randomUUID().toString(),
            category = category,
            eventType = eventType,
            timestamp = timestamp,
            documentId = documentId,
            conceptId = conceptId,
            metadata = metadata
        )
        val next = (readLogs() + entry)
            .sortedByDescending { it.timestamp }
            .take(MAX_LOGS)
        save(next)
        _logs.value = next
        return entry
    }

    @Synchronized
    fun listLogs(): List<MonitoringLogEntry> = readLogs()

    @Synchronized
    fun clear() {
        logFile.delete()
        _logs.value = emptyList()
    }

    @Synchronized
    fun delete(ids: Set<String>) {
        if (ids.isEmpty()) return
        val next = readLogs().filterNot { it.id in ids }
        save(next)
        _logs.value = next
    }

    private fun readLogs(): List<MonitoringLogEntry> {
        if (!logFile.exists()) return emptyList()
        return try {
            json.decodeFromString<List<MonitoringLogEntry>>(
                logFile.readText()
            )
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun save(logs: List<MonitoringLogEntry>) {
        logFile.parentFile?.mkdirs()
        logFile.writeText(json.encodeToString(logs))
    }

    companion object {
        private const val MAX_LOGS = 1000
    }
}
