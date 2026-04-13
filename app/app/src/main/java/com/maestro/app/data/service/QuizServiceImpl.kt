package com.maestro.app.data.service

import com.maestro.app.domain.model.ChatMessage
import com.maestro.app.domain.service.LlmService
import com.maestro.app.domain.service.QuizService

class QuizServiceImpl(
    private val llmService: LlmService
) : QuizService {

    override suspend fun generateQuiz(documentContent: String, questionCount: Int): String {
        val systemPrompt =
            "You are a quiz generator. Based on " +
                "the provided document content, " +
                "create $questionCount " +
                "multiple-choice questions. " +
                "Format each question with " +
                "4 options (A-D) and mark the " +
                "correct answer. Write in Korean."
        val messages = listOf(
            ChatMessage(
                role = ChatMessage.Role.USER,
                content = documentContent
            )
        )
        return llmService.complete(
            messages = messages,
            systemPrompt = systemPrompt
        )
    }
}
