package com.maestro.app.data.repository

import android.content.Context
import com.maestro.app.data.local.ModelArtifactLocalDataSource
import com.maestro.app.data.local.ModelArtifactType
import com.maestro.app.data.local.StudyEvent
import com.maestro.app.data.local.StudyEventLocalDataSource
import com.maestro.app.data.local.StudyEventType
import com.maestro.app.domain.model.ConceptKnowledge
import com.maestro.app.domain.model.DocumentConceptKnowledge
import com.maestro.app.domain.model.DocumentKnowledge
import com.maestro.app.domain.model.EngineeringMechanicsConceptCatalog
import com.maestro.app.domain.model.KnowledgeDashboard
import com.maestro.app.domain.model.PdfDocument
import com.maestro.app.domain.model.ProfileSummary
import com.maestro.app.domain.repository.DocumentRepository
import com.maestro.app.domain.repository.KnowledgeRepository
import com.maestro.app.domain.service.KnowledgeTracingEngine
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class KnowledgeRepositoryImpl(
    private val context: Context,
    private val documentRepository: DocumentRepository,
    private val studyEvents: StudyEventLocalDataSource,
    private val modelArtifacts: ModelArtifactLocalDataSource,
    private val knowledgeEngine: KnowledgeTracingEngine
) : KnowledgeRepository {
    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    override suspend fun loadDashboard(): KnowledgeDashboard = withContext(Dispatchers.IO) {
        val docs = documentRepository.loadDocuments()
        val events = studyEvents.listEvents()
        val concepts = engineeringMechanicsConcepts(docs, events)
        val ready = knowledgeEngine.isReady()
        // Knowledge state comes purely from the bundled TAP readout; no
        // event/prediction blending is applied.
        val masteryByKey =
            if (ready) {
                knowledgeEngine.masteryByConceptKey()
            } else {
                emptyMap()
            }

        fun masteryFor(conceptId: String): Pair<Float, Float> {
            val cm = masteryByKey[conceptId] ?: return 0f to 0f
            val mastery = cm.mastery
            val confidence =
                if (cm.seenCount > 0) {
                    cm.seenCount.coerceAtMost(TAP_RING_WINDOW)
                        .toFloat() / TAP_RING_WINDOW
                } else {
                    0f
                }
            return mastery to confidence
        }

        val documentRows =
            docs.map { doc ->
                val docEvents =
                    events.filter {
                        it.documentId == doc.id
                    }
                val docConcepts =
                    concepts
                        .filter { it.documentIds.contains(doc.id) }
                        .map { concept ->
                            val (mastery, confidence) = masteryFor(concept.id)
                            DocumentConceptKnowledge(
                                conceptId = concept.id,
                                name = concept.name,
                                mastery = mastery,
                                confidence = confidence
                            )
                        }
                        .sortedByDescending { it.confidence }
                val avgMastery =
                    docConcepts
                        .filter { it.confidence > 0f }
                        .takeIf { it.isNotEmpty() }
                        ?.map { it.mastery }
                        ?.average()
                        ?.toFloat() ?: 0f
                val avgConfidence =
                    docConcepts
                        .takeIf { it.isNotEmpty() }
                        ?.map { it.confidence }
                        ?.average()
                        ?.toFloat() ?: 0f
                DocumentKnowledge(
                    documentId = doc.id,
                    title = doc.displayName,
                    pageCount = doc.pageCount,
                    activityCount = docEvents.size,
                    mastery = avgMastery,
                    confidence = avgConfidence,
                    lastStudiedAt =
                    docEvents.maxOfOrNull {
                        it.timestamp
                    },
                    concepts = docConcepts
                )
            }.sortedWith(
                compareByDescending<DocumentKnowledge> {
                    it.lastStudiedAt ?: 0L
                }.thenBy { it.title.lowercase(Locale.US) }
            )
        val conceptRows =
            concepts.map { concept ->
                val (mastery, confidence) = masteryFor(concept.id)
                ConceptKnowledge(
                    id = concept.id,
                    name = concept.name,
                    documentIds = concept.documentIds,
                    mastery = mastery,
                    confidence = confidence
                )
            }.sortedByDescending { it.confidence }
        val activeDocs =
            events.mapNotNull {
                it.documentId
            }.toSet()
        val llmCount =
            events.count {
                it.type == StudyEventType.LLM_REQUESTED
            }
        val quizCount =
            events.count {
                it.type == StudyEventType.QUIZ_REQUESTED ||
                    it.type == StudyEventType.QUIZ_ANSWERED
            }
        val recentCutoff =
            System.currentTimeMillis() - RECENT_WINDOW_MS
        val avg =
            conceptRows
                .filter { it.confidence > 0f }
                .map { it.mastery }
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?.toFloat() ?: 0f
        KnowledgeDashboard(
            summary =
            ProfileSummary(
                totalPdfCount = docs.size,
                studiedDocumentCount = activeDocs.size,
                totalLlmRequests = llmCount,
                totalQuizEvents = quizCount,
                recentActivityCount =
                events.count {
                    it.timestamp >= recentCutoff
                },
                lastStudiedAt =
                events.maxOfOrNull {
                    it.timestamp
                },
                averageMastery = avg,
                miktStatus =
                if (ready) {
                    "MIKT+TAP 모델로 지식 상태 추적 중"
                } else {
                    "추적 불가 — MIKT+TAP 모델(mobile_mikt_update.onnx, " +
                        "mobile_tap_readout.onnx)이 앱에 필요합니다"
                }
            ),
            documents = documentRows,
            concepts = conceptRows,
            strongConcepts =
            conceptRows
                .filter { it.confidence > 0f }
                .sortedByDescending { it.mastery }
                .take(3),
            weakConcepts =
            conceptRows
                .filter { it.confidence > 0f }
                .sortedBy { it.mastery }
                .take(3)
        )
    }

    private fun engineeringMechanicsConcepts(
        docs: List<PdfDocument>,
        events: List<StudyEvent>
    ): List<ConceptRecord> {
        val linksFromEvents =
            events
                .flatMap { event ->
                    val ids =
                        if (event.conceptIds.isNotEmpty()) {
                            event.conceptIds
                        } else {
                            conceptIdsForQuestion(event)
                        }
                    ids.map { conceptId ->
                        conceptId to event.documentId
                    }
                }
                .groupBy({ it.first }, { it.second })
        val docsByConcept =
            docs.flatMap { doc ->
                assignedConceptIds(doc).map { conceptId ->
                    conceptId to doc.id
                }
            }.groupBy({ it.first }, { it.second })
        val externalNames = externalConceptNames()
        val catalog =
            EngineeringMechanicsConceptCatalog.concepts
                .associateBy { it.id }
        val allConceptIds =
            (
                catalog.keys +
                    externalNames.keys +
                    linksFromEvents.keys
                ).distinct()
        return allConceptIds.map { conceptId ->
            val catalogConcept = catalog[conceptId]
            ConceptRecord(
                id = conceptId,
                name =
                catalogConcept?.name
                    ?: externalNames[conceptId]
                    ?: "Concept $conceptId",
                documentIds =
                (
                    docsByConcept[conceptId].orEmpty() +
                        linksFromEvents[conceptId]
                            .orEmpty()
                            .filterNotNull()
                    ).distinct()
            )
        }
    }

    private fun externalConceptNames(): Map<String, String> {
        val file =
            modelArtifacts.getModelFile(
                ModelArtifactType.MIKT_CONCEPT_ID_MAP
            ) ?: return emptyMap()
        return try {
            json.parseToJsonElement(file.readText())
                .jsonObject
                .mapValues { (key, value) ->
                    when (value) {
                        is JsonPrimitive -> value.content
                        is JsonObject ->
                            value["name"]?.jsonPrimitive?.content
                                ?: value["source_kc"]
                                    ?.jsonPrimitive
                                    ?.content
                                ?: "Concept $key"
                        else -> "Concept $key"
                    }
                }
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun conceptIdsForQuestion(event: StudyEvent): List<String> {
        val questionId =
            event.metadata.firstNotBlank(
                "question_id",
                "problem_id",
                "item_id",
                "exercise_id"
            ) ?: return emptyList()
        val file =
            modelArtifacts.getModelFile(
                ModelArtifactType.MIKT_QUESTION_TO_CONCEPT
            ) ?: return emptyList()
        return try {
            val value =
                json.parseToJsonElement(
                    file.readText()
                ).jsonObject[questionId] ?: return emptyList()
            when (value) {
                is JsonPrimitive ->
                    listOfNotNull(
                        value.intOrNull?.toString()
                            ?: value.content.takeIf {
                                it.isNotBlank()
                            }
                    )
                is JsonArray ->
                    value.mapNotNull {
                        it.jsonPrimitive.intOrNull?.toString()
                            ?: it.jsonPrimitive.content.takeIf { raw ->
                                raw.isNotBlank()
                            }
                    }
                is JsonObject -> {
                    val ids =
                        value["concept_ids"]
                            ?: value["concepts"]
                            ?: value["skills"]
                    when (ids) {
                        is JsonArray ->
                            ids.mapNotNull {
                                it.jsonPrimitive.intOrNull?.toString()
                                    ?: it.jsonPrimitive.content.takeIf { raw ->
                                        raw.isNotBlank()
                                    }
                            }
                        is JsonPrimitive ->
                            listOfNotNull(
                                ids.intOrNull?.toString()
                                    ?: ids.content.takeIf {
                                        it.isNotBlank()
                                    }
                            )
                        else -> emptyList()
                    }
                }
                else -> emptyList()
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun Map<String, String>.firstNotBlank(vararg keys: String): String? {
        keys.forEach { key ->
            this[key]?.takeIf { it.isNotBlank() }?.let {
                return it
            }
        }
        val normalized =
            entries.associate {
                it.key.lowercase(Locale.US) to it.value
            }
        keys.forEach { key ->
            normalized[key.lowercase(Locale.US)]
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        return null
    }

    private fun documentText(doc: PdfDocument): String {
        val content =
            File(
                context.filesDir,
                "documents/${doc.id}/content.md"
            ).takeIf { it.exists() }?.readText().orEmpty()
        return (doc.displayName + "\n" + content)
            .lowercase(Locale.US)
    }

    private fun assignedConceptIds(doc: PdfDocument): List<String> {
        val haystack = documentText(doc)
        val scored =
            EngineeringMechanicsConceptCatalog.concepts
                .map { concept ->
                    concept.id to
                        concept.keywords.sumOf { keyword ->
                            Regex("\\b${Regex.escape(keyword.lowercase(Locale.US))}\\b")
                                .findAll(haystack)
                                .count()
                        }
                }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(MAX_CONCEPTS_PER_DOCUMENT)
                .map { it.first }
        if (scored.isNotEmpty()) return scored
        return listOf(
            EngineeringMechanicsConceptCatalog
                .bestMatch(haystack)
                .id
        )
    }

    private data class ConceptRecord(
        val id: String,
        val name: String,
        val documentIds: List<String>
    )

    companion object {
        private const val RECENT_WINDOW_MS =
            7L * 24L * 60L * 60L * 1000L
        private const val MAX_CONCEPTS_PER_DOCUMENT = 4
        private const val TAP_RING_WINDOW = 5
    }
}
