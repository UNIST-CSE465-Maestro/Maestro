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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
        val raw = StringBuilder()
        llmService.stream(
            messages = listOf(
                ChatMessage(
                    role = ChatMessage.Role.USER,
                    content = buildUserPrompt(request, bloom)
                )
            ),
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
        return parseQuestion(raw.toString(), request, bloom)
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
        return """
            ## 학습 자료
            ${request.documentContent.take(MAX_CONTEXT_CHARS)}

            ## 학생 정보
            - 대상 개념: ${request.conceptName}
            - 현재 숙련도(mastery): ${"%.2f".format(request.mastery)}  (0.0 ~ 1.0)
            - 목표 Bloom's Taxonomy 레벨: ${bloom.level} (${bloom.description})
              -> 이 레벨은 학생이 "${bloom.verb}" 수준의 인지 활동을 해야 답할 수 있는 문제입니다.

            ## 예시 (Bloom Level ${bloom.level})
            Q: ${bloom.exampleQuestion}
            A: ${bloom.exampleAnswer}

            ## 생성 지침
            1. 위 학습 자료 안에서만 답을 찾을 수 있는 문제를 1개 생성하세요.
            2. 객관식(MCQ) 형식으로, 보기 4개를 만드세요.
            3. 오답 보기는 그럴듯하지만 원문 기반으로 명확히 틀린 것으로 구성하세요.
            4. source_sentence는 원문에서 답의 근거가 되는 문장을 그대로 발췌하세요.
            5. answer는 반드시 A, B, C, D 중 하나여야 합니다.

            ## JSON 형식
            {
              "question": "다음 중 ...",
              "choices": {
                "A": "...",
                "B": "...",
                "C": "...",
                "D": "..."
              },
              "answer": "B",
              "explanation": "원문에 따르면 ...",
              "source_sentence": "원문에서 발췌한 근거 문장",
              "bloom_level": ${bloom.level},
              "target_concept": "${request.conceptName}"
            }
        """.trimIndent()
    }

    private fun parseQuestion(
        raw: String,
        request: QuizGenerationRequest,
        bloom: BloomLevel
    ): GeneratedQuizQuestion {
        val cleaned = extractJson(raw)
        val dto = json.decodeFromString<LlmQuizQuestionDto>(cleaned)
        val choices = listOf("A", "B", "C", "D")
            .associateWith { key ->
                dto.choices[key].orEmpty()
            }
            .filterValues { it.isNotBlank() }
        require(choices.size == 4) {
            "퀴즈 선택지를 파싱할 수 없습니다"
        }
        val answer = dto.answer.trim().uppercase()
        require(answer in choices.keys) {
            "퀴즈 정답을 파싱할 수 없습니다"
        }
        return GeneratedQuizQuestion(
            question = dto.question.trim(),
            choices = choices,
            answer = answer,
            explanation = dto.explanation.trim(),
            sourceSentence = dto.sourceSentence.trim(),
            bloomLevel = dto.bloomLevel.takeIf {
                it in 1..6
            } ?: bloom.level,
            targetConcept = dto.targetConcept
                .ifBlank { request.conceptName }
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

    @Serializable
    private data class LlmQuizQuestionDto(
        val question: String = "",
        val choices: Map<String, String> = emptyMap(),
        val answer: String = "",
        val explanation: String = "",
        @SerialName("source_sentence")
        val sourceSentence: String = "",
        @SerialName("bloom_level")
        val bloomLevel: Int = 1,
        @SerialName("target_concept")
        val targetConcept: String = ""
    )

    companion object {
        private const val MAX_CONTEXT_CHARS = 7000
        private const val SYSTEM_PROMPT =
            "당신은 교육 문제 생성 전문가입니다.\n" +
                "반드시 아래 [학습 자료] 내용만 근거로 문제를 생성하세요.\n" +
                "외부 지식이나 추론은 절대 사용하지 마세요.\n" +
                "응답은 반드시 JSON 형식만으로 출력하고, 다른 텍스트는 포함하지 마세요."

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
