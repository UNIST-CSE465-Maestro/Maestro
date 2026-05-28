package com.maestro.app.data.service

import com.maestro.app.data.remote.ClaudeClient
import com.maestro.app.data.remote.LlmClient
import com.maestro.app.domain.model.BloomLevel
import com.maestro.app.domain.model.ChatMessage
import com.maestro.app.domain.model.GeneratedQuizQuestion
import com.maestro.app.domain.model.QuizGenerationRequest
import com.maestro.app.domain.service.LlmService
import com.maestro.app.domain.service.QuizService
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class QuizServiceImpl(
    private val llmService: LlmService
) : QuizService {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun generateQuestion(
        request: QuizGenerationRequest
    ): GeneratedQuizQuestion {
        val bloom = bloomLevels()
            .firstOrNull { it.level == request.bloomLevel }
            ?: bloomLevels().first()
        val prompt = buildUserPrompt(request, bloom)
        val raw = requestQuestion(prompt)
        return parseQuestionResult(
            raw = raw,
            request = request,
            bloom = bloom
        ).question ?: insufficientQuestion(request, bloom)
    }

    override fun bloomLevels(): List<BloomLevel> = BLOOM_LEVELS

    override fun defaultBloomLevel(mastery: Float): Int {
        return when (mastery.coerceIn(0f, 1f)) {
            in 0f..0.4f -> 2
            in 0.4f..0.7f -> 4
            else -> 6
        }
    }

    private fun buildUserPrompt(
        request: QuizGenerationRequest,
        bloom: BloomLevel
    ): String {
        val template = if (bloom.level <= 3) {
            MCQ_SINGLE_TEMPLATE
        } else {
            MCQ_MULTIPLE_TEMPLATE
        }
        val section = request.sourceLabel.orEmpty()
        return template
            .replace("{retrieved_chunk}", request.documentContent.take(MAX_CONTEXT_CHARS))
            .replace("{selection_mode}", request.selectionMode)
            .replace("{book_title}", "Maestro PDF")
            .replace("{chapter_title}", section.ifBlank { request.conceptName })
            .replace("{section_title}", section.ifBlank { request.selectionMode })
            .replace("{concept_name}", request.conceptName)
            .replace("{bloom_level}", bloom.level.toString())
            .replace("{bloom_verb}", bloom.verb)
            .replace("{bloom_requirement}", bloom.requirement)
    }

    private suspend fun requestQuestion(prompt: String): String {
        return withTimeout(QUIZ_REQUEST_TIMEOUT_MS) {
            val messages = listOf(
                ChatMessage(
                    role = ChatMessage.Role.USER,
                    content = prompt
                )
            )
            runCatching {
                llmService.complete(
                    messages = messages,
                    systemPrompt = SYSTEM_PROMPT
                )
            }.getOrElse {
                requestQuestionByStream(messages)
            }
        }
    }

    private suspend fun requestQuestionByStream(
        messages: List<ChatMessage>
    ): String {
        val raw = StringBuilder()
        llmService.stream(
            messages = messages,
            systemPrompt = SYSTEM_PROMPT
        ).collect { token ->
            if (
                token != LlmClient.THINKING_TOKEN &&
                token != LlmClient.THINKING_DONE_TOKEN &&
                token != LlmClient.GENERATING_TOKEN &&
                token != LlmClient.RETRY_TOKEN &&
                token != ClaudeClient.GENERATING_TOKEN
            ) {
                raw.append(token)
            }
        }
        return raw.toString()
    }

    private fun parseQuestionResult(
        raw: String,
        request: QuizGenerationRequest,
        bloom: BloomLevel
    ): QuizParseResult {
        val cleaned = runCatching { extractJson(raw) }
            .getOrElse {
                return QuizParseResult(error = it.message ?: "JSON 없음")
            }
        val dto = runCatching {
            json.decodeFromString<LlmQuizQuestionDto>(cleaned)
        }.getOrElse {
            return QuizParseResult(error = it.message ?: "JSON 파싱 실패")
        }
        if (dto.insufficientContext) {
            return QuizParseResult(error = "insufficient_context")
        }
        val choices = listOf("A", "B", "C", "D")
            .associateWith { key ->
                dto.choices[key].orEmpty()
            }
            .filterValues { it.isNotBlank() }
        if (choices.size != 4) {
            return QuizParseResult(error = "A/B/C/D 선택지가 부족합니다")
        }
        val requestedMultiple = bloom.level >= 4
        val mcqType = if (requestedMultiple) {
            "multiple_select"
        } else {
            "single_answer"
        }
        if (dto.questionType != "MCQ" || dto.mcqType != mcqType) {
            return QuizParseResult(error = "MCQ 유형이 요청과 다릅니다")
        }
        val answerKeys = dto.answerKeys()
        if (answerKeys.isEmpty() || answerKeys.any { it !in choices.keys }) {
            return QuizParseResult(error = "정답 형식이 올바르지 않습니다")
        }
        if (!requestedMultiple && answerKeys.size != 1) {
            return QuizParseResult(error = "single_answer 정답은 하나여야 합니다")
        }
        val sourceSentence = dto.sourceSentence.trim()
        val sourceSentences = dto.sourceSentences
            .mapValues { it.value.trim() }
            .filterValues { it.isNotBlank() }
        val sourceOk = if (requestedMultiple) {
            sourceSentences.values.any {
                hasEvidence(
                    request.documentContent,
                    it
                )
            } || dto.finalExplanation.isNotBlank()
        } else {
            sourceSentence.isNotBlank() &&
                hasEvidence(
                    request.documentContent,
                    sourceSentence
                )
        }
        if (!sourceOk) {
            return QuizParseResult(error = "근거 문장이 학습 자료에 없습니다")
        }
        val explanationText = dto.explanationText()
        return QuizParseResult(
            GeneratedQuizQuestion(
                questionType = dto.questionType,
                mcqType = mcqType,
            question = dto.question.trim(),
            choices = choices,
                answer = answerKeys.joinToString(","),
                answerKeys = answerKeys,
                answerText = dto.answerTextString(),
                explanation = explanationText,
                finalExplanation = dto.finalExplanation.ifBlank {
                    explanationText
                },
            choiceExplanations = normalizeChoiceExplanations(
                    dto.explanationMap() + dto.choiceExplanations,
                choices.keys
            ),
                sourceSentence = sourceSentence.ifBlank {
                    sourceSentences.values.joinToString("\n")
                },
                sourceSentences = sourceSentences,
            bloomLevel = dto.bloomLevel.takeIf {
                it in 1..6
            } ?: bloom.level,
            targetConcept = dto.targetConcept
                .ifBlank { request.conceptName }
            )
        )
    }

    private fun extractJson(raw: String): String {
        val withoutFence = raw
            .replace("```json", "")
            .replace("```JSON", "")
            .replace("```", "")
            .trim()
        val start = withoutFence.indexOf('{')
        val end = withoutFence.lastIndexOf('}')
        require(start >= 0 && end > start) {
            "퀴즈 JSON을 찾을 수 없습니다"
        }
        return withoutFence.substring(start, end + 1)
    }

    private fun normalizeChoiceExplanations(
        explanations: Map<String, String>,
        choiceKeys: Set<String>
    ): Map<String, String> {
        return choiceKeys.associateWith { key ->
            explanations[key].orEmpty()
                .ifBlank {
                    explanations[key.lowercase()].orEmpty()
                }
                .trim()
        }.filterValues { it.isNotBlank() }
    }

    private fun hasEvidence(
        documentContent: String,
        sourceSentence: String
    ): Boolean {
        val normalizedContent = documentContent
            .normalizeEvidenceText()
        val normalizedSource = sourceSentence
            .normalizeEvidenceText()
        if (normalizedSource.length < 12) {
            return normalizedSource.isNotBlank() &&
                normalizedContent.contains(normalizedSource)
        }
        if (normalizedContent.contains(normalizedSource)) {
            return true
        }
        val sourceTokens = normalizedSource
            .split(" ")
            .filter { it.length >= 2 }
        if (sourceTokens.size < 4) return false
        val matches = sourceTokens.count {
            normalizedContent.contains(it)
        }
        return matches >= (sourceTokens.size * 0.72f).toInt()
            .coerceAtLeast(4)
    }

    private fun String.normalizeEvidenceText(): String =
        replace(Regex("\\s+"), " ")
            .replace("−", "-")
            .replace("–", "-")
            .replace("—", "-")
            .trim()
            .lowercase()

    private fun insufficientQuestion(
        request: QuizGenerationRequest,
        bloom: BloomLevel
    ): GeneratedQuizQuestion {
        return GeneratedQuizQuestion(
            mcqType = if (bloom.level <= 3) {
                "single_answer"
            } else {
                "multiple_select"
            },
            question = "주어진 학습 자료만으로는 신뢰할 수 있는 퀴즈를 생성하기 어렵습니다.",
            choices = mapOf(
                "A" to "학습 자료의 근거가 충분하지 않습니다.",
                "B" to "학습 자료의 근거가 충분하지 않습니다.",
                "C" to "학습 자료의 근거가 충분하지 않습니다.",
                "D" to "학습 자료의 근거가 충분하지 않습니다."
            ),
            answer = "A",
            answerKeys = listOf("A"),
            answerText = "학습 자료의 근거가 충분하지 않습니다.",
            explanation = "프롬프트 검증 결과, 근거 문장이 학습 자료 안에 충분히 존재하지 않았습니다.",
            finalExplanation = "선택 범위를 넓히거나 다른 퀴즈 범위를 선택한 뒤 다시 생성해 주세요.",
            sourceSentence = "",
            bloomLevel = bloom.level,
            targetConcept = request.conceptName
        )
    }

    private val BloomLevel.requirement: String
        get() = when (level) {
            1 -> "정의, 용어, 명시된 사실을 기억하거나 확인하는 객관식 문제를 생성한다. " +
                "문제는 학습 자료에 직접 등장하는 정보를 기반으로 해야 한다. " +
                "권장 MCQ 유형: single_answer"
            2 -> "개념의 의미, 역할, 관계, 구성 요소를 이해해야 답할 수 있는 객관식 문제를 생성한다. " +
                "문제는 단순 암기보다 개념 이해를 요구해야 하지만, 정답은 반드시 학습 자료 안에서 확인 가능해야 한다. " +
                "권장 MCQ 유형: single_answer"
            3 -> "학습 자료의 규칙, 조건, 절차를 간단한 상황에 적용하는 객관식 문제를 생성한다. " +
                "원문을 그대로 묻는 recall 문제가 아니라, 자료의 원리를 간단한 상황에 적용해야 한다. " +
                "권장 MCQ 유형: single_answer"
            4 -> "조건, 차이, 관계, 구조를 분석하는 객관식 문제를 생성한다. " +
                "여러 선택지를 비교하거나, 조건에 따라 참/거짓을 판단하게 해야 한다. " +
                "권장 MCQ 유형: multiple_select"
            5 -> "학습 자료에 제시된 기준, 원리, 조건을 바탕으로 판단하거나 평가하는 객관식 문제를 생성한다. " +
                "학습 자료에 판단 기준이 충분하지 않으면 insufficient_context를 반환해야 한다. " +
                "권장 MCQ 유형: multiple_select"
            else -> "학습 자료의 원리와 일치하는 절차, 예시, 구성, 설명을 선택하게 하는 객관식 문제를 생성한다. " +
                "완전한 자유 창작 문제가 아니라, 자료의 원리에 부합하는 산출물을 고르는 문제여야 한다. " +
                "학습 자료에 생성 판단 기준이 충분하지 않으면 insufficient_context를 반환해야 한다. " +
                "권장 MCQ 유형: multiple_select"
        }

    @Serializable
    private data class LlmQuizQuestionDto(
        @SerialName("question_type")
        val questionType: String = "",
        @SerialName("mcq_type")
        val mcqType: String? = "",
        val question: String = "",
        val choices: Map<String, String> = emptyMap(),
        val answer: JsonElement? = null,
        @SerialName("answer_text")
        val answerText: JsonElement? = null,
        val explanation: JsonElement? = null,
        @SerialName("final_explanation")
        val finalExplanation: String = "",
        @SerialName("choice_explanations")
        val choiceExplanations: Map<String, String> = emptyMap(),
        @SerialName("source_sentence")
        val sourceSentence: String = "",
        @SerialName("source_sentences")
        val sourceSentences: Map<String, String> = emptyMap(),
        @SerialName("grading_method")
        val gradingMethod: String? = null,
        @SerialName("bloom_level")
        val bloomLevel: Int = 1,
        @SerialName("bloom_verb")
        val bloomVerb: String = "",
        @SerialName("target_concept")
        val targetConcept: String = "",
        @SerialName("insufficient_context")
        val insufficientContext: Boolean = false
    ) {
        fun answerKeys(): List<String> {
            val element = answer ?: return emptyList()
            return when (element) {
                is kotlinx.serialization.json.JsonArray ->
                    element.mapNotNull {
                        it.jsonPrimitive.contentOrNull
                    }
                else -> listOfNotNull(
                    element.jsonPrimitive.contentOrNull
                )
            }.map {
                it.trim().uppercase()
            }.filter { it.isNotBlank() }
        }

        fun answerTextString(): String {
            val element = answerText ?: return ""
            return when (element) {
                is kotlinx.serialization.json.JsonArray ->
                    element.joinToString(", ") {
                        it.jsonPrimitive.contentOrNull.orEmpty()
                    }
                else -> element.jsonPrimitive.contentOrNull.orEmpty()
            }
        }

        fun explanationText(): String {
            return when (val value = explanation) {
                is JsonObject -> value.values.joinToString("\n") {
                    it.jsonPrimitive.contentOrNull.orEmpty()
                }
                null -> ""
                else -> value.jsonPrimitive.contentOrNull.orEmpty()
            }
        }

        fun explanationMap(): Map<String, String> {
            return when (val value = explanation) {
                is JsonObject -> value.mapValues {
                    it.value.jsonPrimitive.contentOrNull.orEmpty()
                }
                else -> emptyMap()
            }
        }
    }

    private data class QuizParseResult(
        val question: GeneratedQuizQuestion? = null,
        val error: String = ""
    )

    companion object {
        private const val MAX_CONTEXT_CHARS = 7000
        private const val QUIZ_REQUEST_TIMEOUT_MS = 45_000L
        private const val SYSTEM_PROMPT =
            "You are Maestro's production quiz generation engine.\n" +
                "Generate MCQ-only questions from the provided retrieved chunk only.\n" +
                "Do not use outside knowledge.\n" +
                "Prefer generating a grounded question when the chunk contains at least one clear factual statement.\n" +
                "Return insufficient_context only when the chunk is empty, unreadable, or unrelated to the target concept.\n" +
                "source_sentence or source_sentences should copy short evidence phrases from the retrieved chunk.\n" +
                "Return exactly one JSON object and no extra text."

        private val MCQ_SINGLE_TEMPLATE = """
            [학습 자료]
            {retrieved_chunk}

            [퀴즈 설정]
            selection_mode: {selection_mode}
            book_title: {book_title}
            chapter_title: {chapter_title}
            section_title: {section_title}
            target_concept: {concept_name}
            bloom_level: {bloom_level}
            bloom_verb: {bloom_verb}
            bloom_requirement: {bloom_requirement}
            question_type: MCQ
            mcq_type: single_answer

            [생성 목표]
            [학습 자료] 안에서만 답을 찾을 수 있는 single-answer 객관식 문제 1개를 생성하세요.
            정답은 반드시 하나여야 합니다.
            문제는 target_concept와 직접 관련되어야 합니다.
            문제는 bloom_requirement에 맞는 사고 활동을 요구해야 합니다.

            [생성 규칙]
            - choices는 반드시 A, B, C, D 네 개입니다.
            - answer는 반드시 "A", "B", "C", "D" 중 하나입니다.
            - 오답은 그럴듯하지만 [학습 자료] 기준으로 명확히 틀려야 합니다.
            - question_type은 반드시 "MCQ"입니다.
            - mcq_type은 반드시 "single_answer"입니다.
            - grading_method는 반드시 "exact_match"입니다.
            - SHORT 관련 필드(short_type, acceptable_answers)는 출력하지 마세요.
            - source_sentence는 [학습 자료]에서 확인 가능한 짧은 근거 문구를 복사하거나 요약 없이 발췌하세요.
            - 학습 자료가 비어 있거나 target_concept와 무관할 때만 insufficient_context: true를 반환하세요.

            [정답 및 해설 출력 규칙]
            - answer에는 정답 보기 key를 출력하세요.
            - answer_text에는 실제 정답 보기 내용을 출력하세요.
            - explanation에는 정답 및 오답 판단 근거를 설명하세요.
            - final_explanation에는 학습자가 정답 확인 후 볼 수 있는 최종 해설을 자연어로 작성하세요.
            - answer, answer_text, explanation, final_explanation은 모두 source_sentence에 근거해야 합니다.

            [출력 JSON]
            {
              "question_type": "MCQ",
              "mcq_type": "single_answer",
              "question": "",
              "choices": {
                "A": "",
                "B": "",
                "C": "",
                "D": ""
              },
              "answer": "",
              "answer_text": "",
              "explanation": {
                "correct": "",
                "A": "",
                "B": "",
                "C": "",
                "D": ""
              },
              "final_explanation": "",
              "source_sentence": "",
              "grading_method": "exact_match",
              "bloom_level": {bloom_level},
              "bloom_verb": "{bloom_verb}",
              "target_concept": "{concept_name}",
              "insufficient_context": false
            }
        """.trimIndent()

        private val MCQ_MULTIPLE_TEMPLATE = """
            [학습 자료]
            {retrieved_chunk}

            [퀴즈 설정]
            selection_mode: {selection_mode}
            book_title: {book_title}
            chapter_title: {chapter_title}
            section_title: {section_title}
            target_concept: {concept_name}
            bloom_level: {bloom_level}
            bloom_verb: {bloom_verb}
            bloom_requirement: {bloom_requirement}
            question_type: MCQ
            mcq_type: multiple_select

            [생성 목표]
            [학습 자료] 안에서만 답을 찾을 수 있는 multiple-select 객관식 문제 1개를 생성하세요.
            문제는 “옳은 것을 모두 고르시오” 형식입니다.
            정답은 1개일 수도 있고 여러 개일 수도 있습니다.
            문제는 target_concept와 직접 관련되어야 합니다.
            문제는 bloom_requirement에 맞는 사고 활동을 요구해야 합니다.

            [생성 규칙]
            - choices는 반드시 A, B, C, D 네 개입니다.
            - answer는 반드시 배열입니다.
            - answer 배열에는 정답 보기 key만 포함하세요.
            - 오답은 [학습 자료] 기준으로 명확히 틀려야 합니다.
            - question_type은 반드시 "MCQ"입니다.
            - mcq_type은 반드시 "multiple_select"입니다.
            - grading_method는 반드시 "exact_set_match"입니다.
            - SHORT 관련 필드(short_type, acceptable_answers)는 출력하지 마세요.
            - source_sentences는 정답 판단에 필요한 선택지 중심으로 포함하세요.
            - 각 source_sentences 값은 [학습 자료]에서 확인 가능한 짧은 근거 문구를 복사하거나 요약 없이 발췌하세요.
            - 학습 자료가 비어 있거나 target_concept와 무관할 때만 insufficient_context: true를 반환하세요.
            - Bloom Level 5~6에서는 학습 자료 안의 조건, 관계, 원리를 바탕으로 판단 가능한 문제를 우선 생성하세요.

            [정답 및 해설 출력 규칙]
            - answer에는 정답 보기 key 배열을 출력하세요.
            - answer_text에는 실제 정답 보기 내용 배열을 출력하세요.
            - explanation에는 A, B, C, D 각각이 맞거나 틀린 이유를 설명하세요.
            - final_explanation에는 학습자가 정답 확인 후 볼 수 있는 최종 해설을 자연어로 작성하세요.
            - answer, answer_text, explanation, final_explanation은 모두 source_sentences에 근거해야 합니다.

            [출력 JSON]
            {
              "question_type": "MCQ",
              "mcq_type": "multiple_select",
              "question": "옳은 것을 모두 고르시오.",
              "choices": {
                "A": "",
                "B": "",
                "C": "",
                "D": ""
              },
              "answer": [],
              "answer_text": [],
              "explanation": {
                "A": "",
                "B": "",
                "C": "",
                "D": ""
              },
              "final_explanation": "",
              "source_sentences": {
                "A": "",
                "B": "",
                "C": "",
                "D": ""
              },
              "grading_method": "exact_set_match",
              "bloom_level": {bloom_level},
              "bloom_verb": "{bloom_verb}",
              "target_concept": "{concept_name}",
              "insufficient_context": false
            }
        """.trimIndent()

        private val BLOOM_LEVELS = listOf(
            BloomLevel(
                level = 1,
                description = "사실을 기억하고 재현",
                verb = "정의하다, 나열하다",
                exampleQuestion = "자료에서 정의한 핵심 용어는 무엇인가요?",
                exampleAnswer = "자료에 직접 제시된 용어 정의를 고릅니다."
            ),
            BloomLevel(
                level = 2,
                description = "개념을 자신의 말로 설명",
                verb = "설명하다, 요약하다",
                exampleQuestion = "자료의 설명을 가장 잘 요약한 선택지는 무엇인가요?",
                exampleAnswer = "원문의 의미를 유지한 요약을 고릅니다."
            ),
            BloomLevel(
                level = 3,
                description = "배운 내용을 새 상황에 적용",
                verb = "계산하다, 적용하다",
                exampleQuestion = "자료의 원리를 적용하면 어떤 결과가 예상되나요?",
                exampleAnswer = "원문에서 제시한 원리에 맞는 적용 결과를 고릅니다."
            ),
            BloomLevel(
                level = 4,
                description = "구성 요소를 분해하고 관계 분석",
                verb = "비교하다, 분석하다",
                exampleQuestion = "자료에서 두 요소의 관계를 가장 잘 분석한 것은 무엇인가요?",
                exampleAnswer = "원문에 드러난 관계를 정확히 비교한 선택지를 고릅니다."
            ),
            BloomLevel(
                level = 5,
                description = "근거를 들어 판단하고 평가",
                verb = "평가하다, 판단하다",
                exampleQuestion = "자료의 근거로 볼 때 가장 타당한 평가는 무엇인가요?",
                exampleAnswer = "원문 근거와 가장 잘 맞는 평가를 고릅니다."
            ),
            BloomLevel(
                level = 6,
                description = "새로운 것을 설계하거나 창조",
                verb = "설계하다, 제안하다",
                exampleQuestion = "자료의 조건을 만족하는 가장 적절한 설계안은 무엇인가요?",
                exampleAnswer = "원문 조건을 충족하는 제안 또는 설계를 고릅니다."
            )
        )
    }
}
