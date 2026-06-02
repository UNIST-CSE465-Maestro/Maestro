package com.maestro.app.data.local

import android.content.Context
import com.maestro.app.data.remote.QeRepresentation
import java.io.File
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A QE-encoded quiz stored on the tablet. Combines the locally generated
 * quiz identity with the representation returned by the Question Encoder
 * server so it can later feed the on-device MIKT knowledge tracer.
 */
@Serializable
data class QuestionRepresentationRecord(
    @SerialName("representation_id")
    val representationId: String,
    @SerialName("question_hash")
    val questionHash: String,
    @SerialName("source_doc_id")
    val sourceDocId: String? = null,
    @SerialName("concept_id")
    val conceptId: String = "",
    @SerialName("local_question_hash")
    val localQuestionHash: String = "",
    val question: String = "",
    @SerialName("bloom_level")
    val bloomLevel: Int = 0,
    @SerialName("qe_model_version")
    val qeModelVersion: String = "",
    @SerialName("mikt_compatibility_version")
    val miktCompatibilityVersion: String = "",
    @SerialName("embedding_dim")
    val embeddingDim: Int = 0,
    @SerialName("question_embedding")
    val questionEmbedding: List<Float> = emptyList(),
    val difficulty: Float = 0f,
    @SerialName("concept_ids")
    val conceptIds: List<Int> = emptyList(),
    @SerialName("concept_keys")
    val conceptKeys: List<String> = emptyList(),
    @SerialName("feature_mode")
    val featureMode: String = "",
    @SerialName("encoded_at")
    val encodedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun from(
            representation: QeRepresentation,
            sourceDocId: String?,
            conceptId: String,
            localQuestionHash: String,
            question: String,
            bloomLevel: Int
        ): QuestionRepresentationRecord = QuestionRepresentationRecord(
            representationId = representation.representationId,
            questionHash = representation.questionHash,
            sourceDocId = sourceDocId,
            conceptId = conceptId,
            localQuestionHash = localQuestionHash,
            question = question,
            bloomLevel = bloomLevel,
            qeModelVersion = representation.qeModelVersion,
            miktCompatibilityVersion = representation.miktCompatibilityVersion,
            embeddingDim = representation.embeddingDim,
            questionEmbedding = representation.questionEmbedding,
            difficulty = representation.difficulty,
            conceptIds = representation.conceptIds,
            conceptKeys = representation.conceptKeys,
            featureMode = representation.featureMode
        )
    }
}

/**
 * JSON file persistence for QE question representations, mirroring the
 * pattern used by [QuizResponseLocalDataSource]. Records are de-duplicated
 * by `representation_id` so re-encoding the same quiz updates in place.
 */
class QuestionRepresentationLocalDataSource {
    private val file: File
    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

    constructor(context: Context) : this(
        File(context.filesDir, "question_representations/representations.json")
    )

    internal constructor(file: File) {
        this.file = file
        file.parentFile?.mkdirs()
    }

    @Synchronized
    fun upsert(record: QuestionRepresentationRecord): QuestionRepresentationRecord {
        val existing =
            listRecords()
                .filterNot { it.representationId == record.representationId }
        save(existing + record)
        return record
    }

    @Synchronized
    fun listRecords(): List<QuestionRepresentationRecord> {
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString<List<QuestionRepresentationRecord>>(
                file.readText()
            )
        } catch (_: Throwable) {
            emptyList()
        }
    }

    @Synchronized
    fun delete(representationId: String) {
        save(listRecords().filterNot { it.representationId == representationId })
    }

    private fun save(records: List<QuestionRepresentationRecord>) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(records))
    }
}
