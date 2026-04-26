package com.maestro.app.data.local

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class QuizResponseRecord(
    val id: String = UUID.randomUUID().toString(),
    @SerialName("concept_id")
    val conceptId: String,
    @SerialName("bloom_level")
    val bloomLevel: Int,
    @SerialName("is_correct")
    val isCorrect: Boolean,
    @SerialName("response_time_ms")
    val responseTimeMs: Long? = null,
    @SerialName("answered_at")
    val answeredAt: Long = System.currentTimeMillis(),
    @SerialName("question_hash")
    val questionHash: String? = null,
    @SerialName("source_doc_id")
    val sourceDocId: String? = null,
    val question: String = "",
    val choices: Map<String, String> = emptyMap(),
    @SerialName("selected_answer")
    val selectedAnswer: String = "",
    @SerialName("correct_answer")
    val correctAnswer: String = "",
    val explanation: String = "",
    @SerialName("source_sentence")
    val sourceSentence: String = ""
)

class QuizResponseLocalDataSource {
    private val file: File
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    constructor(context: Context) : this(
        File(context.filesDir, "quiz_response/responses.json")
    )

    internal constructor(file: File) {
        this.file = file
        file.parentFile?.mkdirs()
    }

    @Synchronized
    fun append(record: QuizResponseRecord): QuizResponseRecord {
        save(listResponses() + record)
        return record
    }

    @Synchronized
    fun listResponses(): List<QuizResponseRecord> {
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString<List<QuizResponseRecord>>(
                file.readText()
            )
        } catch (_: Throwable) {
            emptyList()
        }
    }

    @Synchronized
    fun delete(id: String) {
        save(listResponses().filterNot { it.id == id })
    }

    private fun save(records: List<QuizResponseRecord>) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(records))
    }
}
