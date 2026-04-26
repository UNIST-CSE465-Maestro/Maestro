package com.maestro.app.domain.model

data class BloomLevel(
    val level: Int,
    val description: String,
    val verb: String,
    val exampleQuestion: String,
    val exampleAnswer: String
)

data class GeneratedQuizQuestion(
    val question: String,
    val choices: Map<String, String>,
    val answer: String,
    val explanation: String,
    val sourceSentence: String,
    val bloomLevel: Int,
    val targetConcept: String
)

data class QuizGenerationRequest(
    val documentContent: String,
    val conceptName: String,
    val mastery: Float,
    val bloomLevel: Int
)
