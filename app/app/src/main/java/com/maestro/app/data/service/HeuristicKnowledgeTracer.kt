package com.maestro.app.data.service

import com.maestro.app.data.local.StudyEvent
import com.maestro.app.data.local.StudyEventType
import com.maestro.app.domain.service.KnowledgeTraceResult
import com.maestro.app.domain.service.RektKnowledgeTracer
import com.maestro.app.domain.service.RektTraceInput
import kotlin.math.min

class HeuristicKnowledgeTracer : RektKnowledgeTracer {
    override fun trace(inputs: List<RektTraceInput>): Map<String, KnowledgeTraceResult> {
        return inputs.associate { input ->
            input.keyId to traceOne(input.events)
        }
    }

    private fun traceOne(events: List<StudyEvent>): KnowledgeTraceResult {
        if (events.isEmpty()) {
            return KnowledgeTraceResult(
                mastery = 0f,
                confidence = 0f,
                usingModel = false
            )
        }
        var score = 0.18f
        var evidence = 0f
        events.sortedBy { it.timestamp }.forEach { event ->
            when (event.type) {
                StudyEventType.DOCUMENT_OPENED -> {
                    score += 0.02f
                    evidence += 0.4f
                }
                StudyEventType.PAGE_VIEWED -> {
                    score += 0.015f
                    evidence += 0.3f
                }
                StudyEventType.BOOKMARK_TOGGLED -> {
                    score += 0.015f
                    evidence += 0.25f
                }
                StudyEventType.ANNOTATION_SAVED -> {
                    score += 0.025f
                    evidence += 0.45f
                }
                StudyEventType.LLM_REQUESTED -> {
                    score += 0.035f
                    evidence += 0.65f
                }
                StudyEventType.QUIZ_REQUESTED -> {
                    score += 0.04f
                    evidence += 0.75f
                }
                StudyEventType.QUIZ_ANSWERED -> {
                    score += if (event.correctness == true) {
                        0.16f
                    } else {
                        -0.08f
                    }
                    evidence += 1.25f
                }
            }
            score = score.coerceIn(0f, 1f)
        }
        return KnowledgeTraceResult(
            mastery = score.coerceIn(0f, 1f),
            confidence = min(1f, evidence / 8f),
            usingModel = false
        )
    }
}
