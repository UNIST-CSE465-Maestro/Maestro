package com.maestro.app.domain.service

import com.maestro.app.data.local.StudyEvent

data class RektTraceInput(
    val keyId: String,
    val events: List<StudyEvent>
)

data class KnowledgeTraceResult(
    val mastery: Float,
    val confidence: Float,
    val usingModel: Boolean
)

interface RektKnowledgeTracer {
    fun trace(inputs: List<RektTraceInput>): Map<String, KnowledgeTraceResult>
}
