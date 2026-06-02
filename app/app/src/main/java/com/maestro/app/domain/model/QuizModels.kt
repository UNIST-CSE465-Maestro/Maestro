package com.maestro.app.domain.model

data class BloomLevel(
    val level: Int,
    val description: String,
    val verb: String,
    val exampleQuestion: String,
    val exampleAnswer: String
)

data class GeneratedQuizQuestion(
    val questionType: String = "MCQ",
    val mcqType: String = "single_answer",
    val question: String,
    val choices: Map<String, String>,
    val answer: String,
    val answerKeys: List<String> = listOf(answer),
    val answerText: String = "",
    val explanation: String,
    val finalExplanation: String = explanation,
    val choiceExplanations: Map<String, String> = emptyMap(),
    val sourceSentence: String,
    val sourceSentences: Map<String, String> = emptyMap(),
    val bloomLevel: Int,
    val targetConcept: String
)

data class QuizGenerationRequest(
    val documentContent: String,
    val conceptName: String,
    val mastery: Float,
    val bloomLevel: Int,
    val selectionMode: String = "document",
    val sourceLabel: String? = null,
    // Output language for the generated quiz: "ko" or "en".
    val language: String = "ko"
)
