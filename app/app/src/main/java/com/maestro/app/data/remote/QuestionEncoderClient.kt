package com.maestro.app.data.remote

import com.maestro.app.data.repository.SettingsRepositoryImpl
import com.maestro.app.domain.model.EngineeringMechanicsConceptCatalog
import com.maestro.app.domain.model.GeneratedQuizQuestion
import com.maestro.app.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * A single answer option, matching the MobileKT QE wire format
 * (`{ "label": "A", "text": "..." }`).
 */
@Serializable
data class QeOption(
    val label: String,
    val text: String
)

/**
 * Request body for `POST /v1/question/encode`. Field names follow the
 * server contract in `MobileKT/server` (see `qe_server_api_contract.json`).
 */
@Serializable
data class QeEncodeRequest(
    @SerialName("client_question_id")
    val clientQuestionId: String? = null,
    val question: String,
    val options: List<QeOption> = emptyList(),
    val answer: String = "",
    val solution: String = "",
    @SerialName("visual_description")
    val visualDescription: String = "",
    @SerialName("question_type")
    val questionType: String = "multiple_choice",
    @SerialName("concept_keys")
    val conceptKeys: List<String> = emptyList()
)

@Serializable
private data class QeBatchRequest(
    val questions: List<QeEncodeRequest>
)

/**
 * Response body for `POST /v1/question/encode`. The QE "answer" is the
 * question representation (embedding + difficulty + resolved concepts).
 */
@Serializable
data class QeRepresentation(
    @SerialName("question_hash")
    val questionHash: String = "",
    @SerialName("representation_id")
    val representationId: String = "",
    @SerialName("qe_model_version")
    val qeModelVersion: String = "",
    @SerialName("mikt_compatibility_version")
    val miktCompatibilityVersion: String = "",
    @SerialName("embedding_dim")
    val embeddingDim: Int = 0,
    @SerialName("embedding_dtype")
    val embeddingDtype: String = "float32",
    @SerialName("question_embedding")
    val questionEmbedding: List<Float> = emptyList(),
    val difficulty: Float = 0f,
    @SerialName("concept_ids")
    val conceptIds: List<Int> = emptyList(),
    @SerialName("concept_keys")
    val conceptKeys: List<String> = emptyList(),
    @SerialName("max_concepts_per_question")
    val maxConceptsPerQuestion: Int = 0,
    @SerialName("feature_encoder")
    val featureEncoder: String = "",
    @SerialName("feature_mode")
    val featureMode: String = ""
)

@Serializable
private data class QeBatchResponse(
    val items: List<QeRepresentation> = emptyList()
)

@Serializable
private data class QeErrorEnvelope(val error: QeError? = null)

@Serializable
private data class QeError(val code: String = "", val message: String = "")

class QuestionEncoderException(
    val code: String,
    override val message: String
) : Exception(message)

/**
 * Client for the MobileKT server-side Question Encoder.
 *
 * Sends LLM-generated quizzes in the format the repo specifies and returns
 * the question representation. The base URL is read from settings on every
 * call so the user can change it without restarting the app.
 */
class QuestionEncoderClient(
    private val httpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun encode(request: QeEncodeRequest): QeRepresentation = withContext(Dispatchers.IO) {
        val body = json.encodeToString(QeEncodeRequest.serializer(), request)
        val raw = post("v1/question/encode", body)
        json.decodeFromString(QeRepresentation.serializer(), raw)
    }

    suspend fun encodeBatch(requests: List<QeEncodeRequest>): List<QeRepresentation> =
        withContext(Dispatchers.IO) {
            val body =
                json.encodeToString(
                    QeBatchRequest.serializer(),
                    QeBatchRequest(requests)
                )
            val raw = post("v1/question/encode-batch", body)
            json.decodeFromString(QeBatchResponse.serializer(), raw).items
        }

    private suspend fun post(path: String, body: String): String {
        val url = baseUrl() + path
        val httpRequest =
            Request.Builder()
                .url(url)
                .post(body.toRequestBody(jsonMediaType))
                .build()
        httpClient.newCall(httpRequest).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw parseError(response.code, payload)
            }
            return payload
        }
    }

    private fun parseError(httpCode: Int, payload: String): Exception {
        val parsed =
            runCatching {
                json.decodeFromString(QeErrorEnvelope.serializer(), payload).error
            }.getOrNull()
        return QuestionEncoderException(
            code = parsed?.code ?: "http_$httpCode",
            message =
            parsed?.message
                ?: "QE 서버 요청에 실패했습니다 (HTTP $httpCode)."
        )
    }

    private suspend fun baseUrl(): String {
        val configured =
            settingsRepository.getQeServerUrl().firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: SettingsRepositoryImpl.DEFAULT_QE_SERVER_URL
        return if (configured.endsWith("/")) configured else "$configured/"
    }

    companion object {
        /**
         * Maps a generated quiz into the QE encode request. Choice keys
         * (A/B/C/D) become option labels; the target statics2011 KC is
         * resolved to a valid `concept_keys` entry the server can look up.
         */
        fun requestFromQuiz(
            quiz: GeneratedQuizQuestion,
            clientQuestionId: String
        ): QeEncodeRequest {
            val options =
                quiz.choices.entries
                    .sortedBy { it.key }
                    .map { QeOption(label = it.key, text = it.value) }
            val answerText =
                quiz.answerText.ifBlank {
                    quiz.answerKeys.mapNotNull { quiz.choices[it] }
                        .joinToString(", ")
                }
            val conceptKeys =
                EngineeringMechanicsConceptCatalog
                    .resolveConceptKeys(
                        targetConcept = quiz.targetConcept,
                        evidenceText =
                        listOf(
                            quiz.question,
                            quiz.sourceSentence
                        ).joinToString(" ")
                    )
            val questionType =
                if (quiz.mcqType == "multiple_select") {
                    "multiple_select"
                } else {
                    "multiple_choice"
                }
            return QeEncodeRequest(
                clientQuestionId = clientQuestionId,
                question = quiz.question,
                options = options,
                answer = answerText,
                solution = quiz.finalExplanation.ifBlank { quiz.explanation },
                questionType = questionType,
                conceptKeys = conceptKeys
            )
        }
    }
}
