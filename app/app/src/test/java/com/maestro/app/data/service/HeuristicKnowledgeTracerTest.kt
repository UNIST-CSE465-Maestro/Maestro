package com.maestro.app.data.service

import com.maestro.app.data.local.StudyEvent
import com.maestro.app.data.local.StudyEventType
import com.maestro.app.domain.service.RektTraceInput
import org.junit.Assert.assertTrue
import org.junit.Test

class HeuristicKnowledgeTracerTest {
    @Test
    fun `correct quiz answers raise mastery more than incorrect answers`() {
        val tracer = HeuristicKnowledgeTracer()
        val correct = tracer.trace(
            listOf(
                RektTraceInput(
                    keyId = "correct",
                    events = listOf(
                        event(StudyEventType.QUIZ_REQUESTED, 1L),
                        event(
                            StudyEventType.QUIZ_ANSWERED,
                            2L,
                            correctness = true
                        )
                    )
                ),
                RektTraceInput(
                    keyId = "incorrect",
                    events = listOf(
                        event(StudyEventType.QUIZ_REQUESTED, 1L),
                        event(
                            StudyEventType.QUIZ_ANSWERED,
                            2L,
                            correctness = false
                        )
                    )
                )
            )
        )

        assertTrue(
            correct.getValue("correct").mastery >
                correct.getValue("incorrect").mastery
        )
        assertTrue(correct.getValue("correct").confidence > 0f)
    }

    @Test
    fun `llm and reading events increase confidence`() {
        val tracer = HeuristicKnowledgeTracer()

        val result = tracer.trace(
            listOf(
                RektTraceInput(
                    keyId = "concept",
                    events = listOf(
                        event(StudyEventType.DOCUMENT_OPENED, 1L),
                        event(StudyEventType.PAGE_VIEWED, 2L),
                        event(StudyEventType.LLM_REQUESTED, 3L)
                    )
                )
            )
        ).getValue("concept")

        assertTrue(result.mastery > 0f)
        assertTrue(result.confidence > 0f)
    }

    private fun event(
        type: StudyEventType,
        timestamp: Long,
        correctness: Boolean? = null
    ) = StudyEvent(
        id = "$type-$timestamp",
        type = type,
        timestamp = timestamp,
        correctness = correctness
    )
}
