package com.maestro.app.domain.service

import com.maestro.app.domain.model.BloomLevel
import com.maestro.app.domain.model.GeneratedQuizQuestion
import com.maestro.app.domain.model.QuizGenerationRequest

/** Phase of a single quiz-generation attempt, used for progress display. */
enum class QuizPhase { REQUESTING, GENERATING, VALIDATING, DONE }

/**
 * Progress of quiz generation. [fraction] is an approximation (the LLM's total
 * output length is unknown) weighted across phases; [attempt]/[totalAttempts]
 * lets the UI show retries.
 */
data class QuizProgress(
    val fraction: Float,
    val phase: QuizPhase,
    val attempt: Int,
    val totalAttempts: Int
)

interface QuizService {
    suspend fun generateQuestion(
        request: QuizGenerationRequest,
        onProgress: (QuizProgress) -> Unit = {}
    ): GeneratedQuizQuestion

    fun bloomLevels(): List<BloomLevel>

    fun defaultBloomLevel(mastery: Float): Int
}
