package com.maestro.app.domain.service

interface QuizService {
    suspend fun generateQuiz(
        documentContent: String,
        questionCount: Int = 5
    ): String
}
