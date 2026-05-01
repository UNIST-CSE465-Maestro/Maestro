package com.maestro.app.data.repository

import android.content.Context
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
import com.maestro.app.domain.service.RektKnowledgeTracer
import com.maestro.app.domain.service.RektTraceInput
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KnowledgeRepositoryImpl(
    private val context: Context,
    private val documentRepository: DocumentRepository,
    private val studyEvents: StudyEventLocalDataSource,
    private val tracer: RektKnowledgeTracer
) : KnowledgeRepository {
    override suspend fun loadDashboard(): KnowledgeDashboard =
        withContext(Dispatchers.IO) {
            val docs = documentRepository.loadDocuments()
            val events = studyEvents.listEvents()
            val concepts = engineeringMechanicsConcepts(docs, events)
            val conceptTrace = tracer.trace(
                concepts.map { concept ->
                    RektTraceInput(
                        keyId = concept.id,
                        events = events.filter {
                            concept.documentIds.contains(
                                it.documentId
                            ) || it.conceptIds.contains(concept.id)
                        }
                    )
                }
            )
            val docConceptInputs = docs.flatMap { doc ->
                concepts
                    .filter { it.documentIds.contains(doc.id) }
                    .map { concept ->
                        RektTraceInput(
                            keyId = docConceptKey(doc.id, concept.id),
                            events = events.filter { event ->
                                event.documentId == doc.id &&
                                    (
                                        event.conceptIds.isEmpty() ||
                                            event.conceptIds
                                                .contains(concept.id)
                                        )
                            }
                        )
                    }
            }
            val docConceptTrace = tracer.trace(docConceptInputs)
            val documentRows = docs.map { doc ->
                val docEvents = events.filter {
                    it.documentId == doc.id
                }
                val docConcepts = concepts
                    .filter { it.documentIds.contains(doc.id) }
                    .map { concept ->
                        val trace = docConceptTrace[
                            docConceptKey(doc.id, concept.id)
                        ]
                        DocumentConceptKnowledge(
                            conceptId = concept.id,
                            name = concept.name,
                            mastery = trace?.mastery ?: 0f,
                            confidence = trace?.confidence ?: 0f
                        )
                    }
                    .sortedByDescending { it.confidence }
                val avgMastery = docConcepts
                    .takeIf { it.isNotEmpty() }
                    ?.map { it.mastery }
                    ?.average()
                    ?.toFloat() ?: 0f
                val avgConfidence = docConcepts
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
                    lastStudiedAt = docEvents.maxOfOrNull {
                        it.timestamp
                    },
                    concepts = docConcepts
                )
            }.sortedWith(
                compareByDescending<DocumentKnowledge> {
                    it.lastStudiedAt ?: 0L
                }.thenBy { it.title.lowercase(Locale.US) }
            )
            val conceptRows = concepts.map { concept ->
                val trace = conceptTrace[concept.id]
                ConceptKnowledge(
                    id = concept.id,
                    name = concept.name,
                    documentIds = concept.documentIds,
                    mastery = trace?.mastery ?: 0f,
                    confidence = trace?.confidence ?: 0f
                )
            }.sortedByDescending { it.confidence }
            val activeDocs = events.mapNotNull {
                it.documentId
            }.toSet()
            val llmCount = events.count {
                it.type == StudyEventType.LLM_REQUESTED
            }
            val quizCount = events.count {
                it.type == StudyEventType.QUIZ_REQUESTED ||
                    it.type == StudyEventType.QUIZ_ANSWERED
            }
            val recentCutoff =
                System.currentTimeMillis() - RECENT_WINDOW_MS
            val usingModel = conceptTrace.values.any { it.usingModel } ||
                docConceptTrace.values.any { it.usingModel }
            val avg = (documentRows.map { it.mastery } +
                conceptRows.map { it.mastery })
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?.toFloat() ?: 0f
            KnowledgeDashboard(
                summary = ProfileSummary(
                    totalPdfCount = docs.size,
                    studiedDocumentCount = activeDocs.size,
                    totalLlmRequests = llmCount,
                    totalQuizEvents = quizCount,
                    recentActivityCount = events.count {
                        it.timestamp >= recentCutoff
                    },
                    lastStudiedAt = events.maxOfOrNull {
                        it.timestamp
                    },
                    averageMastery = avg,
                    rektStatus = if (usingModel) {
                        "KT ONNX local inference active"
                    } else {
                        "KT ONNX 모델 업로드 전, 활동 기반 추정으로 표시 중"
                    }
                ),
                documents = documentRows,
                concepts = conceptRows,
                strongConcepts = conceptRows
                    .filter { it.confidence > 0f }
                    .sortedByDescending { it.mastery }
                    .take(3),
                weakConcepts = conceptRows
                    .filter { it.confidence > 0f }
                    .sortedBy { it.mastery }
                    .take(3)
            )
        }

    private fun engineeringMechanicsConcepts(
        docs: List<PdfDocument>,
        events: List<StudyEvent>
    ): List<ConceptRecord> {
        val linksFromEvents = events
            .flatMap { event ->
                event.conceptIds.map { conceptId ->
                    conceptId to event.documentId
                }
            }
            .groupBy({ it.first }, { it.second })
        val docsByConcept = docs.flatMap { doc ->
            assignedConceptIds(doc).map { conceptId ->
                conceptId to doc.id
            }
        }.groupBy({ it.first }, { it.second })
        return EngineeringMechanicsConceptCatalog.concepts.map { concept ->
            ConceptRecord(
                id = concept.id,
                name = concept.name,
                documentIds = (
                    docsByConcept[concept.id].orEmpty() +
                        linksFromEvents[concept.id]
                            .orEmpty()
                            .filterNotNull()
                    ).distinct()
            )
        }
    }

    private fun documentText(doc: PdfDocument): String {
        val content = File(
            context.filesDir,
            "documents/${doc.id}/content.md"
        ).takeIf { it.exists() }?.readText().orEmpty()
        return (doc.displayName + "\n" + content)
            .lowercase(Locale.US)
    }

    private fun assignedConceptIds(doc: PdfDocument): List<String> {
        val haystack = documentText(doc)
        val scored = EngineeringMechanicsConceptCatalog.concepts
            .map { concept ->
                concept.id to concept.keywords.sumOf { keyword ->
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

    private fun docConceptKey(
        documentId: String,
        conceptId: String
    ): String = "${documentId}_$conceptId"

    private data class ConceptRecord(
        val id: String,
        val name: String,
        val documentIds: List<String>
    )

    companion object {
        private const val RECENT_WINDOW_MS =
            7L * 24L * 60L * 60L * 1000L
        private const val MAX_CONCEPTS_PER_DOCUMENT = 4
    }
}
