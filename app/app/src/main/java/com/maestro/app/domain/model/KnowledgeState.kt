package com.maestro.app.domain.model

data class ProfileSummary(
    val totalPdfCount: Int = 0,
    val studiedDocumentCount: Int = 0,
    val totalLlmRequests: Int = 0,
    val totalQuizEvents: Int = 0,
    val recentActivityCount: Int = 0,
    val lastStudiedAt: Long? = null,
    val averageMastery: Float = 0f,
    val rektStatus: String = ""
)

data class DocumentKnowledge(
    val documentId: String,
    val title: String,
    val pageCount: Int,
    val activityCount: Int,
    val mastery: Float,
    val confidence: Float,
    val lastStudiedAt: Long?
)

data class ConceptKnowledge(
    val id: String,
    val name: String,
    val documentIds: List<String>,
    val mastery: Float,
    val confidence: Float
)

data class KnowledgeDashboard(
    val summary: ProfileSummary = ProfileSummary(),
    val documents: List<DocumentKnowledge> = emptyList(),
    val concepts: List<ConceptKnowledge> = emptyList(),
    val strongConcepts: List<ConceptKnowledge> = emptyList(),
    val weakConcepts: List<ConceptKnowledge> = emptyList()
)
