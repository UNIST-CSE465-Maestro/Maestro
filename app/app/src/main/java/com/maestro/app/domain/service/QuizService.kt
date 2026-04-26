package com.maestro.app.domain.service

import com.maestro.app.domain.model.BloomLevel
import com.maestro.app.domain.model.GeneratedQuizQuestion
import com.maestro.app.domain.model.QuizGenerationRequest

interface QuizService {
    suspend fun generateQuestion(
        request: QuizGenerationRequest
    ): GeneratedQuizQuestion

    fun bloomLevels(): List<BloomLevel>

    fun defaultBloomLevel(mastery: Float): Int
}
