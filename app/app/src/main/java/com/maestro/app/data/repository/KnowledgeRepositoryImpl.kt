package com.maestro.app.data.repository

import android.content.Context
import com.maestro.app.data.local.StudyEvent
import com.maestro.app.data.local.StudyEventLocalDataSource
import com.maestro.app.data.local.StudyEventType
import com.maestro.app.domain.model.ConceptKnowledge
import com.maestro.app.domain.model.DocumentKnowledge
import com.maestro.app.domain.model.KnowledgeDashboard
import com.maestro.app.domain.model.ProfileSummary
import com.maestro.app.domain.model.PdfDocument
import com.maestro.app.domain.repository.DocumentRepository
import com.maestro.app.domain.repository.KnowledgeRepository
import com.maestro.app.domain.service.RektKnowledgeTracer
import com.maestro.app.domain.service.RektTraceInput
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class KnowledgeRepositoryImpl(
    private val context: Context,
    private val documentRepository: DocumentRepository,
    private val studyEvents: StudyEventLocalDataSource,
    private val tracer: RektKnowledgeTracer
) : KnowledgeRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private val conceptsFile =
        File(context.filesDir, "study_events/concepts.json")

    override suspend fun loadDashboard(): KnowledgeDashboard =
        withContext(Dispatchers.IO) {
            val docs = documentRepository.loadDocuments()
            val events = studyEvents.listEvents()
            val concepts = loadOrGenerateConcepts(docs)
            val docTrace = tracer.trace(
                docs.map { doc ->
                    RektTraceInput(
                        keyId = doc.id,
                        events = events.filter {
                            it.documentId == doc.id
                        }
                    )
                }
            )
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
            val documentRows = docs.map { doc ->
                val docEvents = events.filter {
                    it.documentId == doc.id
                }
                val trace = docTrace[doc.id]
                DocumentKnowledge(
                    documentId = doc.id,
                    title = doc.displayName,
                    pageCount = doc.pageCount,
                    activityCount = docEvents.size,
                    mastery = trace?.mastery ?: 0f,
                    confidence = trace?.confidence ?: 0f,
                    lastStudiedAt = docEvents.maxOfOrNull {
                        it.timestamp
                    }
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
            val usingModel = docTrace.values.any { it.usingModel } ||
                conceptTrace.values.any { it.usingModel }
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
                        "ReKT local inference active"
                    } else {
                        "로컬 ReKT 모델 준비 전, 활동 기반 추정으로 표시 중"
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

    private fun loadOrGenerateConcepts(docs: List<PdfDocument>): List<ConceptRecord> {
        val generated = docs.flatMap { doc ->
            extractConceptNames(doc).map { name ->
                ConceptRecord(
                    id = conceptId(doc.id, name),
                    name = name,
                    documentIds = listOf(doc.id)
                )
            }
        }
        saveConcepts(generated)
        return generated
    }

    private fun extractConceptNames(doc: PdfDocument): List<String> {
        val names = linkedSetOf<String>()
        names += doc.displayName
            .removeSuffix(".pdf")
            .replace('_', ' ')
            .trim()
        val content = File(
            context.filesDir,
            "documents/${doc.id}/content.md"
        ).takeIf { it.exists() }?.readText().orEmpty()
        Regex("^#{1,3}\\s+(.+)$", RegexOption.MULTILINE)
            .findAll(content)
            .map { it.groupValues[1].trim() }
            .filter { it.length in 3..48 }
            .take(4)
            .forEach { names += it }
        tokenize(content)
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(4)
            .forEach { names += it.key }
        return names.filter { it.isNotBlank() }.take(6)
    }

    private fun tokenize(text: String): List<String> {
        val stop = setOf(
            "the", "and", "for", "with", "this", "that",
            "from", "you", "are", "was", "were", "have",
            "about", "into", "your", "문서", "내용", "설명"
        )
        return Regex("[A-Za-z가-힣][A-Za-z가-힣0-9_-]{2,}")
            .findAll(text)
            .map { it.value.lowercase(Locale.US) }
            .filter { it !in stop }
            .toList()
    }

    private fun saveConcepts(concepts: List<ConceptRecord>) {
        try {
            conceptsFile.parentFile?.mkdirs()
            conceptsFile.writeText(json.encodeToString(concepts))
        } catch (_: Throwable) {}
    }

    private fun conceptId(documentId: String, name: String): String {
        val slug = name.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9가-힣]+"), "_")
            .trim('_')
            .take(40)
            .ifBlank { "concept" }
        return "${documentId.take(8)}_$slug"
    }

    @Serializable
    private data class ConceptRecord(
        val id: String,
        val name: String,
        val documentIds: List<String>
    )

    companion object {
        private const val RECENT_WINDOW_MS =
            7L * 24L * 60L * 60L * 1000L
    }
}
