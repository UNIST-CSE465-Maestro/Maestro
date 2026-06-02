package com.maestro.app.data.service

import com.maestro.app.data.remote.ClaudeClient
import com.maestro.app.data.remote.LlmClient
import com.maestro.app.domain.model.BloomLevel
import com.maestro.app.domain.model.ChatMessage
import com.maestro.app.domain.model.EngineeringMechanicsConceptCatalog
import com.maestro.app.domain.model.GeneratedQuizQuestion
import com.maestro.app.domain.model.QuizGenerationRequest
import com.maestro.app.domain.service.LlmService
import com.maestro.app.domain.service.QuizPhase
import com.maestro.app.domain.service.QuizProgress
import com.maestro.app.domain.service.QuizService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    override suspend fun generateQuestion(
        request: QuizGenerationRequest,
        onProgress: (QuizProgress) -> Unit
    ): GeneratedQuizQuestion {
        // The requested Bloom level is kept fixed — we never silently lower the
        // difficulty. If a question comes back structurally valid but low quality
        // it is simply regenerated at the SAME level.
        val bloom =
            bloomLevels()
                .firstOrNull { it.level == request.bloomLevel }
                ?: bloomLevels().first()

        // Quality loop: a structurally valid but low-quality question (weak
        // evidence, generic recall, KC-label phrasing) is regenerated at the same
        // difficulty up to MAX_QUALITY_ATTEMPTS times. Transient network failures
        // are retried separately inside generateOnce and do NOT consume a quality
        // attempt. If every attempt is still flagged, the last usable question is
        // shown to the learner as-is (better than an error).
        var lastUsable: GeneratedQuizQuestion? = null
        repeat(MAX_QUALITY_ATTEMPTS) { qIndex ->
            val qAttempt = qIndex + 1
            if (qIndex > 0) delay(RETRY_BACKOFF_MS)
            val parsed = generateOnce(request, bloom, qAttempt, onProgress)
            parsed.question?.let { question ->
                if (parsed.qualityOk) {
                    onProgress(
                        QuizProgress(1f, QuizPhase.DONE, qAttempt, MAX_QUALITY_ATTEMPTS)
                    )
                    return question
                }
                // Usable but flagged — remember the latest and regenerate.
                lastUsable = question
            }
        }

        // Every attempt was flagged or unparseable: show the best usable question
        // if we have one, otherwise ask the learner to retry.
        lastUsable?.let {
            onProgress(QuizProgress(1f, QuizPhase.DONE, MAX_QUALITY_ATTEMPTS, MAX_QUALITY_ATTEMPTS))
            return it
        }
        throw IllegalStateException(QUIZ_RETRY_MESSAGE)
    }

    /**
     * One quality attempt: streams a question and parses it, retrying only
     * transient network/timeout failures (up to [MAX_NETWORK_ATTEMPTS]). Throws
     * hard errors (bad key/quota) and surfaces a network error if every transient
     * retry fails. The returned [QuizParseResult] may carry a usable-but-flagged
     * question (quality issue) or a structural error.
     */
    private suspend fun generateOnce(
        request: QuizGenerationRequest,
        bloom: BloomLevel,
        qAttempt: Int,
        onProgress: (QuizProgress) -> Unit
    ): QuizParseResult {
        var lastError: Throwable? = null
        repeat(MAX_NETWORK_ATTEMPTS) { nIndex ->
            if (nIndex > 0) delay(RETRY_BACKOFF_MS)
            val prompt = buildUserPrompt(request, bloom)
            // Phase 1: request sent, waiting for the first token (TTFT).
            onProgress(QuizProgress(0.05f, QuizPhase.REQUESTING, qAttempt, MAX_QUALITY_ATTEMPTS))
            val raw =
                runCatching {
                    requestQuestion(prompt) { chars ->
                        // Phase 2: streaming — approximate against a typical quiz
                        // JSON size, capped below 100% (true length is unknown).
                        val fraction =
                            (0.15f + 0.75f * (chars / EXPECTED_QUIZ_CHARS).coerceAtMost(1f))
                                .coerceAtMost(0.90f)
                        onProgress(
                            QuizProgress(
                                fraction,
                                QuizPhase.GENERATING,
                                qAttempt,
                                MAX_QUALITY_ATTEMPTS
                            )
                        )
                    }
                }.getOrElse { error ->
                    // Hard errors (bad/expired key, quota) won't recover on retry.
                    if (!shouldRetry(error)) throw error
                    // Transient (network/timeout): remember it and retry.
                    lastError = error
                    return@repeat
                }
            if (raw.isBlank()) {
                // Empty stream — almost always a dropped/transient connection.
                // Retry within the network budget instead of burning a quality
                // attempt on a response that never arrived.
                lastError = IllegalStateException("빈 응답을 받았습니다")
                return@repeat
            }
            // Phase 3: validating the response.
            onProgress(QuizProgress(0.92f, QuizPhase.VALIDATING, qAttempt, MAX_QUALITY_ATTEMPTS))
            return parseQuestionResult(raw, request, bloom)
        }
        // Network retries exhausted with only transient failures.
        lastError?.let { throw it }
        return QuizParseResult(error = "network_exhausted")
    }

    override fun bloomLevels(): List<BloomLevel> = BLOOM_LEVELS

    override fun defaultBloomLevel(mastery: Float): Int {
        return when (mastery.coerceIn(0f, 1f)) {
            in 0f..0.4f -> 2
            in 0.4f..0.7f -> 4
            else -> 6
        }
    }

    private fun buildUserPrompt(request: QuizGenerationRequest, bloom: BloomLevel): String {
        val template =
            if (bloom.level <= 3) {
                MCQ_SINGLE_TEMPLATE
            } else {
                MCQ_MULTIPLE_TEMPLATE
            }
        val section = request.sourceLabel.orEmpty()
        val filled =
            template
                .replace(
                    "{retrieved_chunk}",
                    request.documentContent.toQuizPromptContext(MAX_CONTEXT_CHARS)
                )
                .replace("{selection_mode}", request.selectionMode)
                .replace("{book_title}", "Maestro PDF")
                .replace("{chapter_title}", section.ifBlank { request.conceptName })
                .replace("{section_title}", section.ifBlank { request.selectionMode })
                .replace("{concept_name}", request.conceptName)
                .replace("{bloom_level}", bloom.level.toString())
                .replace("{bloom_verb}", bloom.verb)
                .replace("{bloom_requirement}", bloom.requirement)
        return filled + "\n\n" + languageDirective(request.language)
    }

    /** Forces the quiz output language while keeping verbatim source excerpts. */
    private fun languageDirective(language: String): String =
        if (language.equals("en", ignoreCase = true)) {
            "[OUTPUT LANGUAGE]\n" +
                "Write the question, all four choices, every explanation and " +
                "final_explanation in ENGLISH only. Keep source_sentence(s) as the " +
                "exact verbatim excerpt from the material (do not translate them)."
        } else {
            "[출력 언어]\n" +
                "question, 네 개의 choices, 모든 explanation, final_explanation은 반드시 " +
                "한국어로 작성하세요. source_sentence(s)는 학습 자료의 원문을 그대로 발췌하고 " +
                "번역하지 마세요."
        }

    private suspend fun requestQuestion(prompt: String, onChars: (Int) -> Unit): String {
        val messages =
            listOf(
                ChatMessage(
                    role = ChatMessage.Role.USER,
                    content = prompt
                )
            )
        return requestQuestionByStream(messages, onChars)
    }

    /**
     * Streams the response with a stall-only timeout: as long as tokens keep
     * arriving, generation runs to completion with **no** total time cap — a
     * slow-but-working answer is never truncated. It gives up only when the
     * stream is genuinely stuck: no first token within [FIRST_TOKEN_TIMEOUT_MS],
     * or no new token within [STALL_TIMEOUT_MS]. A stalled stream returns the
     * partial text, which fails parsing and triggers a retry.
     */
    private suspend fun requestQuestionByStream(
        messages: List<ChatMessage>,
        onChars: (Int) -> Unit
    ): String = coroutineScope {
        val raw = StringBuilder()
        val lastTokenAt = AtomicLong(System.currentTimeMillis())
        val firstTokenReceived = AtomicBoolean(false)

        val collectJob =
            launch {
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
                        firstTokenReceived.set(true)
                        lastTokenAt.set(System.currentTimeMillis())
                        onChars(raw.length)
                    }
                }
            }

        val watchdog =
            launch {
                while (collectJob.isActive) {
                    delay(WATCHDOG_TICK_MS)
                    val idle = System.currentTimeMillis() - lastTokenAt.get()
                    // Only the inactivity gap matters — there is no total cap,
                    // so an actively streaming response is never cut off.
                    val idleLimit =
                        if (firstTokenReceived.get()) {
                            STALL_TIMEOUT_MS
                        } else {
                            FIRST_TOKEN_TIMEOUT_MS
                        }
                    if (idle > idleLimit) {
                        collectJob.cancel()
                        break
                    }
                }
            }

        collectJob.join()
        watchdog.cancel()
        raw.toString()
    }

    private fun parseQuestionResult(
        raw: String,
        request: QuizGenerationRequest,
        bloom: BloomLevel
    ): QuizParseResult {
        val cleaned =
            runCatching { extractJson(raw) }
                .getOrElse {
                    return QuizParseResult(error = it.message ?: "JSON 없음")
                }
        val dto =
            runCatching {
                json.decodeFromString<LlmQuizQuestionDto>(cleaned)
            }.getOrElse {
                return QuizParseResult(error = it.message ?: "JSON 파싱 실패")
            }
        // NOTE: a model-returned insufficient_context flag is intentionally
        // ignored. Generation is only ever reached when the content chunk is
        // non-blank (the panel blocks empty content upstream), so the refusal is
        // a false negative — we always try to build a question from what we have.
        val choices =
            listOf("A", "B", "C", "D")
                .associateWith { key ->
                    dto.choices[key].orEmpty()
                }
                .filterValues { it.isNotBlank() }
        if (choices.size != 4) {
            return QuizParseResult(error = "A/B/C/D 선택지가 부족합니다")
        }
        val requestedMultiple = bloom.level >= 4
        val mcqType =
            if (requestedMultiple) {
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
        val sourceSentences =
            dto.sourceSentences
                .mapValues { it.value.trim() }
                .filterValues { it.isNotBlank() }
        val explanationText = dto.explanationText()
        // The question is structurally valid here, so build it now. Quality
        // concerns below mark it for regeneration but never discard it — after
        // the quality attempts are exhausted it can still be shown as-is.
        val question =
            GeneratedQuizQuestion(
                questionType = dto.questionType,
                mcqType = mcqType,
                question = dto.question.trim(),
                choices = choices,
                answer = answerKeys.joinToString(","),
                answerKeys = answerKeys,
                answerText = dto.answerTextString(),
                explanation = explanationText,
                finalExplanation =
                dto.finalExplanation.ifBlank {
                    explanationText
                },
                choiceExplanations =
                normalizeChoiceExplanations(
                    dto.explanationMap() + dto.choiceExplanations,
                    choices.keys
                ),
                sourceSentence =
                sourceSentence.ifBlank {
                    sourceSentences.values.joinToString("\n")
                },
                sourceSentences = sourceSentences,
                // Always report the level we actually requested — the model
                // sometimes echoes a different bloom_level in its JSON.
                bloomLevel = bloom.level,
                targetConcept =
                dto.targetConcept
                    .ifBlank { request.conceptName }
            )
        val sourceOk =
            if (requestedMultiple) {
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
        val visibleText = (dto.question + " " + choices.values.joinToString(" "))
        val qualityIssue =
            when {
                !sourceOk -> "근거 문장이 학습 자료에 없습니다"
                dto.question.isGenericEvidenceQuestion() ->
                    "KC 이해를 테스트하지 않는 일반 근거 확인 문제입니다"
                mentionsKcTaxonomy(dto.question, choices) ->
                    "KC 분류 라벨을 묻는 문제입니다"
                hasUnexpectedScript(visibleText) ->
                    "한국어/영어 외 외국어(일본어·중국어)가 섞여 있습니다"
                else -> null
            }
        return QuizParseResult(
            question = question,
            qualityOk = qualityIssue == null,
            error = qualityIssue.orEmpty()
        )
    }

    private fun extractJson(raw: String): String {
        val withoutFence =
            raw
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

    private fun hasEvidence(documentContent: String, sourceSentence: String): Boolean {
        val normalizedContent =
            documentContent
                .normalizeEvidenceText()
        val normalizedSource =
            sourceSentence
                .normalizeEvidenceText()
        if (normalizedSource.length < 12) {
            return normalizedSource.isNotBlank() &&
                normalizedContent.contains(normalizedSource)
        }
        if (normalizedContent.contains(normalizedSource)) {
            return true
        }
        val sourceTokens =
            normalizedSource
                .split(" ")
                .filter { it.length >= 2 }
        if (sourceTokens.size < 4) return false
        val matches =
            sourceTokens.count {
                normalizedContent.contains(it)
            }
        return matches >=
            (sourceTokens.size * 0.72f).toInt()
                .coerceAtLeast(4)
    }

    private fun String.normalizeEvidenceText(): String = replace(Regex("\\s+"), " ")
        .replace("−", "-")
        .replace("–", "-")
        .replace("—", "-")
        .trim()
        .lowercase()

    private fun String.toQuizPromptContext(maxChars: Int): String {
        return lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .take(maxChars)
    }

    /** Transient failures are worth retrying; hard errors should surface. */
    private fun shouldRetry(error: Throwable): Boolean {
        val msg = error.message.orEmpty()
        if (error is TimeoutCancellationException) return true
        return !listOf(
            "API error 400",
            "API error 401",
            "API error 403",
            "API error 429",
            "API 키"
        ).any { msg.contains(it, ignoreCase = true) }
    }

    /**
     * Rejects KC-taxonomy questions: the quiz surface must never reference the
     * statics2011 KC labels. Catches questions that name the taxonomy or whose
     * options are concept labels rather than physics statements.
     */
    private fun mentionsKcTaxonomy(question: String, choices: Map<String, String>): Boolean {
        val q = question.lowercase()
        if (q.contains("statics2011") ||
            q.contains("knowledge component") ||
            Regex("\\bkc\\b").containsMatchIn(q)
        ) {
            return true
        }
        val catalogNames =
            EngineeringMechanicsConceptCatalog.concepts
                .map { it.name.trim().lowercase() }
                .toSet()
        val labelLikeChoices =
            choices.values.count { it.trim().lowercase() in catalogNames }
        return labelLikeChoices >= 2
    }

    /**
     * Detects characters that should never appear in a Korean/English quiz:
     * Japanese kana and Chinese/Japanese Han ideographs. Weaker (often
     * Chinese-origin) free models leak these despite the language directive;
     * flagging triggers a regeneration.
     */
    private fun hasUnexpectedScript(text: String): Boolean = CJK_REGEX.containsMatchIn(text)

    private fun String.isGenericEvidenceQuestion(): Boolean {
        val normalized = lowercase()
        return listOf(
            "학습 자료의 설명과 일치",
            "학습 자료 설명과 일치",
            "다음 설명과 일치",
            "다음 근거 문구가 설명하는 내용",
            "which statement is consistent",
            "which of the following matches"
        ).any { normalized.contains(it.lowercase()) }
    }

    private val BloomLevel.requirement: String
        get() =
            when (level) {
                1 ->
                    "정의, 용어, 명시된 사실을 기억하거나 확인하는 객관식 문제를 생성한다. " +
                        "문제는 학습 자료에 직접 등장하는 정보를 기반으로 해야 한다. " +
                        "권장 MCQ 유형: single_answer"
                2 ->
                    "개념의 의미, 역할, 관계, 구성 요소를 이해해야 답할 수 있는 객관식 문제를 생성한다. " +
                        "문제는 단순 암기보다 개념 이해를 요구해야 하지만, 정답은 반드시 학습 자료 안에서 확인 가능해야 한다. " +
                        "권장 MCQ 유형: single_answer"
                3 ->
                    "학습 자료의 규칙, 조건, 절차를 간단한 상황에 적용하는 객관식 문제를 생성한다. " +
                        "원문을 그대로 묻는 recall 문제가 아니라, 자료의 원리를 간단한 상황에 적용해야 한다. " +
                        "권장 MCQ 유형: single_answer"
                4 ->
                    "조건, 차이, 관계, 구조를 분석하는 객관식 문제를 생성한다. " +
                        "여러 선택지를 비교하거나, 조건에 따라 참/거짓을 판단하게 해야 한다. " +
                        "권장 MCQ 유형: multiple_select"
                5 ->
                    "학습 자료에 제시된 기준, 원리, 조건을 바탕으로 판단하거나 평가하는 객관식 문제를 생성한다. " +
                        "내용이 다소 얇더라도 자료에서 확인 가능한 범위에서 최선의 문제를 반드시 만든다. " +
                        "권장 MCQ 유형: multiple_select"
                else ->
                    "학습 자료의 원리와 일치하는 절차, 예시, 구성, 설명을 선택하게 하는 객관식 문제를 생성한다. " +
                        "완전한 자유 창작 문제가 아니라, 자료의 원리에 부합하는 산출물을 고르는 문제여야 한다. " +
                        "내용이 다소 얇더라도 자료에서 확인 가능한 범위에서 최선의 문제를 반드시 만든다. " +
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
                else ->
                    listOfNotNull(
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
                is JsonObject ->
                    value.values.joinToString("\n") {
                        it.jsonPrimitive.contentOrNull.orEmpty()
                    }
                null -> ""
                else -> value.jsonPrimitive.contentOrNull.orEmpty()
            }
        }

        fun explanationMap(): Map<String, String> {
            return when (val value = explanation) {
                is JsonObject ->
                    value.mapValues {
                        it.value.jsonPrimitive.contentOrNull.orEmpty()
                    }
                else -> emptyMap()
            }
        }
    }

    private data class QuizParseResult(
        val question: GeneratedQuizQuestion? = null,
        // True when the question passed every quality heuristic. A usable but
        // flagged question has question != null and qualityOk == false.
        val qualityOk: Boolean = false,
        val error: String = ""
    )

    companion object {
        // Hiragana, Katakana, CJK Extension A, and CJK Unified Ideographs —
        // none of which belong in a Korean (Hangul) or English quiz.
        private val CJK_REGEX =
            Regex("[\\u3040-\\u30FF\\u3400-\\u4DBF\\u4E00-\\u9FFF]")

        private const val MAX_CONTEXT_CHARS = 2200

        // Quality regenerations at the SAME difficulty: 1 initial + 2 retries.
        private const val MAX_QUALITY_ATTEMPTS = 3

        // Transient network/timeout retries within a single quality attempt.
        // These do not consume a quality attempt.
        private const val MAX_NETWORK_ATTEMPTS = 3

        // Short pause before a retry so a transient network/DNS blip can recover
        // (a failed attempt often returns instantly).
        private const val RETRY_BACKOFF_MS = 1500L

        // Stall-only generation timeouts (free LLM tiers are slow/variable):
        // wait up to FIRST_TOKEN for the queue/TTFT, then cut only after a STALL
        // gap with no new token. There is no total time cap — while tokens keep
        // arriving, the response streams to completion.
        private const val FIRST_TOKEN_TIMEOUT_MS = 40_000L
        private const val STALL_TIMEOUT_MS = 18_000L
        private const val WATCHDOG_TICK_MS = 500L

        // Typical compact quiz JSON length; used only to approximate streaming
        // progress (the model's real output length is unknown).
        private const val EXPECTED_QUIZ_CHARS = 1100f
        const val QUIZ_RETRY_MESSAGE =
            "AI가 이 범위로 좋은 퀴즈를 만들지 못했어요. " +
                "잠시 후 다시 시도하거나 다른 페이지·영역을 선택해 주세요."
        private const val SYSTEM_PROMPT =
            "You are Maestro's production quiz generation engine. " +
                "Use only the provided chunk. Return one compact JSON object only. " +
                "Do not use outside knowledge. Keep every field concise. " +
                "Generate a question that tests the learner's understanding of the " +
                "core engineering content in the material — its definitions, " +
                "relationships, equations, and problem-solving steps (e.g. setting up " +
                "a free-body diagram, choosing the right equation, computing a quantity). " +
                "target_concept is an INTERNAL focus hint only: never name it, never " +
                "write the words 'KC' or 'statics2011', and never ask the learner to " +
                "pick a concept label. The question and every option must be physics/" +
                "engineering statements about the material itself, not taxonomy. " +
                "Avoid generic 'which statement matches the material' recall questions. " +
                "You must ALWAYS return one complete question built from the provided " +
                "chunk — never refuse, never claim the content is insufficient, and " +
                "never leave choices empty. Use whatever engineering content is present."

        private val MCQ_SINGLE_TEMPLATE =
            """
            [학습 자료]
            {retrieved_chunk}

            [설정]
            selection_mode: {selection_mode}
            target_concept: {concept_name}
            bloom_level: {bloom_level}
            bloom_requirement: {bloom_requirement}
            mcq_type: single_answer

            [규칙]
            이 학습 자료의 핵심 물리 내용(정의·관계·식·풀이 절차, 예제 문제의 설정·해법)을
            제대로 이해했는지 검사하는 객관식 1개를 만드세요.
            target_concept는 출제 초점을 잡기 위한 내부 힌트일 뿐입니다.
            질문이나 선택지에 KC 이름, 'KC', 'statics2011' 같은 분류 용어를 절대 쓰지 말고,
            "어떤 KC가 필요한가?"처럼 분류 라벨을 고르게 하는 문제는 절대 만들지 마세요.
            질문과 선택지는 모두 자료의 물리/공학 내용 자체에 대한 진술이어야 합니다.
            예: 자유물체도에 들어갈 힘, 적용할 평형/운동 방정식, 계산 결과, 성립 조건 등.
            “학습 자료의 설명과 일치하는 것은?” 같은 단순 근거 확인/암기 문제는 만들지 마세요.
            문서의 첫 페이지, 마지막 페이지, 강의 운영 정보, 연락처, 저작권, 참고문헌 내용은 출제하지 마세요.
            choices는 A-D 네 개, answer는 정답 key 하나입니다.
            question/choices/explanation/final_explanation은 짧게 작성하세요.
            source_sentence는 학습 자료에서 짧은 근거 문구를 그대로 발췌하세요.
            주어진 내용이 다소 얇더라도 거부하지 말고 반드시 완전한 문제 1개를 출력하세요.

            [JSON]
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
              "explanation": "",
              "final_explanation": "",
              "source_sentence": "",
              "grading_method": "exact_match",
              "bloom_level": {bloom_level},
              "bloom_verb": "{bloom_verb}",
              "target_concept": "{concept_name}"
            }
            """.trimIndent()

        private val MCQ_MULTIPLE_TEMPLATE =
            """
            [학습 자료]
            {retrieved_chunk}

            [설정]
            selection_mode: {selection_mode}
            target_concept: {concept_name}
            bloom_level: {bloom_level}
            bloom_requirement: {bloom_requirement}
            mcq_type: multiple_select

            [규칙]
            이 학습 자료의 핵심 물리 내용(정의·관계·식·풀이 절차, 예제 문제의 설정·해법)을
            제대로 이해했는지 검사하는 multiple-select 객관식 1개를 만드세요.
            target_concept는 출제 초점을 잡기 위한 내부 힌트일 뿐입니다.
            질문이나 선택지에 KC 이름, 'KC', 'statics2011' 같은 분류 용어를 절대 쓰지 말고,
            분류 라벨을 고르게 하는 문제는 절대 만들지 마세요.
            질문과 선택지는 모두 자료의 물리/공학 내용 자체에 대한 진술이어야 합니다.
            “학습 자료의 설명과 일치하는 항목” 같은 단순 근거 확인/암기 문제는 만들지 마세요.
            문서의 첫 페이지, 마지막 페이지, 강의 운영 정보, 연락처, 저작권, 참고문헌 내용은 출제하지 마세요.
            choices는 A-D 네 개, answer는 정답 key 배열입니다.
            question은 “옳은 것을 모두 고르시오” 형식을 따릅니다.
            question/choices/explanation/final_explanation은 짧게 작성하세요.
            source_sentences 값은 학습 자료에서 짧은 근거 문구를 그대로 발췌하세요.
            주어진 내용이 다소 얇더라도 거부하지 말고 반드시 완전한 문제 1개를 출력하세요.

            [JSON]
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
              "target_concept": "{concept_name}"
            }
            """.trimIndent()

        private val BLOOM_LEVELS =
            listOf(
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
