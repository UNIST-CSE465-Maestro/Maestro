package com.maestro.app.domain.service

import com.maestro.app.data.local.StudyEvent

data class MiktTraceInput(
    val keyId: String,
    val events: List<StudyEvent>
)

data class KnowledgeTraceResult(
    val mastery: Float,
    val confidence: Float,
    val usingModel: Boolean,
    val modelName: String? = null
)

interface MiktKnowledgeTracer {
    fun trace(inputs: List<MiktTraceInput>): Map<String, KnowledgeTraceResult>
}
