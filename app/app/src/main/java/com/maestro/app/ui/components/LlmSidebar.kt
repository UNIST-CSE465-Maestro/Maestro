package com.maestro.app.ui.components

import android.graphics.BitmapFactory
import android.util.TypedValue
import android.widget.TextView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.maestro.app.data.local.ConversationLocalDataSource
import com.maestro.app.data.local.ConversationSummary
import com.maestro.app.data.local.LocalMlKitContentExtractor
import com.maestro.app.data.local.QuizResponseRecord
import com.maestro.app.data.local.StructuredContentCropExtractor
import com.maestro.app.data.model.LlmRequestBuilder
import com.maestro.app.data.remote.ClaudeClient
import com.maestro.app.data.remote.LlmClient
import com.maestro.app.data.remote.OpenAiClient
import com.maestro.app.domain.model.ChatMessage
import com.maestro.app.domain.model.ConceptKnowledge
import com.maestro.app.domain.model.CropCapturePayload
import com.maestro.app.domain.model.EngineeringMechanicsConceptCatalog
import com.maestro.app.domain.model.GeneratedQuizQuestion
import com.maestro.app.domain.model.LlmProvider
import com.maestro.app.domain.model.QuizGenerationRequest
import com.maestro.app.domain.model.SelectedTextQuizPayload
import com.maestro.app.domain.repository.SettingsRepository
import com.maestro.app.domain.service.LlmService
import com.maestro.app.domain.service.QuizPhase
import com.maestro.app.domain.service.QuizProgress
import com.maestro.app.domain.service.QuizService
import com.maestro.app.ui.config.UxConfig
import com.maestro.app.ui.viewer.LlmConnectionState
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private fun buildSystemPrompt(
    documentContent: String?,
    pageIndex: Int,
    userPrompt: String,
    extraContext: String
): String {
    val base =
        "You are a helpful AI assistant " +
            "integrated into Maestro, a PDF " +
            "annotation app. Help the user " +
            "understand and work with their documents. " +
            "Always respond in Korean."
    if (documentContent.isNullOrBlank()) return base
    val context =
        buildFocusedDocumentContext(
            documentContent = documentContent,
            pageIndex = pageIndex,
            query = userPrompt,
            maxChars = CHAT_CONTEXT_CHARS
        )
    val extra =
        extraContext.takeIf { it.isNotBlank() }
            ?.let {
                "\n\nAdditional OCR context from the selected image region:\n\n$it"
            }
            .orEmpty()
    return "$base\n\n" +
        "The user is currently viewing a document. " +
        "Use this focused context from the saved parsed document. " +
        "If it is not enough, say what part is missing instead of guessing.\n\n" +
        context + extra
}

private fun buildFocusedDocumentContext(
    documentContent: String,
    pageIndex: Int,
    query: String?,
    maxChars: Int
): String {
    val sections = mutableListOf<String>()
    val outline = documentOutline(documentContent)
    if (outline.isNotBlank()) {
        sections += "## Document Outline\n$outline"
    }
    val currentPage = pageSection(documentContent, pageIndex)
    if (currentPage.isNotBlank()) {
        sections += "## Current Page ${pageIndex + 1}\n$currentPage"
    }
    val relevant =
        relevantSnippets(
            documentContent = documentContent,
            query = query.orEmpty(),
            excluded = currentPage,
            maxChars = maxChars / 2
        )
    if (relevant.isNotBlank()) {
        sections += "## Related Snippets\n$relevant"
    } else if (currentPage.isBlank()) {
        sections += "## Opening Content\n" +
            documentContent.take(maxChars / 2)
    }
    val joined =
        sections.joinToString("\n\n").ifBlank {
            documentContent
        }
    return joined.take(maxChars)
}

private fun documentOutline(documentContent: String): String {
    return documentContent.lineSequence()
        .map { it.trim() }
        .filter {
            it.startsWith("#") &&
                !it.startsWith("# Page ")
        }
        .take(40)
        .joinToString("\n")
        .take(1200)
}

private fun pageSection(documentContent: String, pageIndex: Int): String {
    val target = pageIndex + 1
    val pattern = Regex("(?m)^# Page (\\d+)\\s*$")
    val matches = pattern.findAll(documentContent).toList()
    if (matches.isEmpty()) return ""
    val matchIndex =
        matches.indexOfFirst {
            it.groupValues.getOrNull(1)?.toIntOrNull() == target
        }
    if (matchIndex < 0) return ""
    val start = matches[matchIndex].range.last + 1
    val end =
        matches.getOrNull(matchIndex + 1)?.range?.first
            ?: documentContent.length
    return documentContent.substring(start, end)
        .trim()
}

private fun buildDocumentQuizContext(documentContent: String): String {
    if (documentContent.isBlank()) return ""
    val rawPageSections = numberedPageSections(documentContent)
    val candidateChunks =
        if (rawPageSections.isNotEmpty()) {
            rawPageSections
                .dropQuizBoundaryPages()
                .quizChunksFromPages()
        } else if (rawPageSections.isEmpty()) {
            quizChunksFromParagraphs(documentContent)
        } else {
            emptyList()
        }
    val sampleContent =
        candidateChunks
            .sortedByDescending { it.score }
            .take(DOCUMENT_SAMPLE_COUNT)
            .joinToString("\n\n") { chunk ->
                "## ${chunk.label}\n${chunk.text.take(DOCUMENT_PAGE_SAMPLE_CHARS)}"
            }
    if (sampleContent.isBlank()) return ""
    val sections = mutableListOf<String>()
    val outline = documentOutline(documentContent)
    if (outline.isNotBlank()) {
        sections += "## Document Outline\n$outline"
    }
    sections += sampleContent
    return sections.joinToString("\n\n")
        .take(QUIZ_CONTEXT_CHARS)
}

private fun buildPageRangeQuizContext(documentContent: String, pages: Set<Int>): String {
    if (documentContent.isBlank()) return ""
    val pageSections = numberedPageSections(documentContent)
    if (pageSections.isEmpty()) return buildDocumentQuizContext(documentContent)
    val chunks =
        pageSections
            .filter { (page, _) -> page in pages }
            .filterNot { (_, body) -> body.isAdministrativeQuizPage() }
            .quizChunksFromPages()
    return chunks.sortedByDescending { it.score }
        .take(DOCUMENT_SAMPLE_COUNT)
        .joinToString("\n\n") { chunk ->
            "## ${chunk.label}\n${chunk.text.take(DOCUMENT_PAGE_SAMPLE_CHARS)}"
        }.take(QUIZ_CONTEXT_CHARS)
}

private fun buildCurrentPageQuizContext(documentContent: String, pageIndex: Int): String {
    val pageBody = pageSection(documentContent, pageIndex)
    if (pageBody.isBlank() || pageBody.isAdministrativeQuizPage()) return ""
    val chunks = listOf(pageIndex + 1 to pageBody).quizChunksFromPages()
    return chunks.sortedByDescending { it.score }
        .take(DOCUMENT_SAMPLE_COUNT)
        .joinToString("\n\n") { chunk ->
            "## ${chunk.label}\n${chunk.text.take(DOCUMENT_PAGE_SAMPLE_CHARS)}"
        }.take(QUIZ_CONTEXT_CHARS)
}

private fun buildWeaknessQuizContext(
    documentContent: String,
    weakConcepts: List<ConceptKnowledge>,
    quizHistory: List<QuizResponseRecord>,
    fallbackPageIndex: Int
): String {
    val chunks = quizCandidateChunks(documentContent)
    if (chunks.isEmpty()) {
        return ""
    }
    val weakConceptIds =
        weakConcepts
            .sortedBy { it.mastery }
            .take(WEAK_KC_COUNT)
            .map { it.id }
    val conceptFiltered =
        if (weakConceptIds.isNotEmpty()) {
            chunks.filter { chunk ->
                EngineeringMechanicsConceptCatalog
                    .bestMatch(chunk.text)
                    .id in weakConceptIds
            }
        } else {
            chunks
        }
    val sourceCounts =
        quizHistory.mapNotNull {
            it.sourceSentence.takeIf { source -> source.isNotBlank() }
        }
    val selected =
        conceptFiltered
            .sortedWith(
                compareBy<QuizCandidateChunk> { chunk ->
                    sourceCounts.count { source ->
                        chunk.text.contains(source.take(40), ignoreCase = true)
                    }
                }.thenByDescending { it.score }
            )
            .take(WEAKNESS_SAMPLE_COUNT)
    if (selected.isEmpty()) return ""
    val weakLabel =
        weakConcepts
            .filter { it.id in weakConceptIds }
            .joinToString(", ") { "${it.name} ${(it.mastery * 100).toInt()}%" }
            .ifBlank {
                "low practice concepts"
            }
    return listOf(
        "## Weak KC targets\n$weakLabel",
        selected.joinToString("\n\n") { chunk ->
            "## ${chunk.label}\n${chunk.text.take(DOCUMENT_PAGE_SAMPLE_CHARS)}"
        }
    ).joinToString("\n\n").take(QUIZ_CONTEXT_CHARS)
}

private fun numberedPageSections(documentContent: String): List<Pair<Int, String>> {
    val pattern = Regex("(?m)^# Page (\\d+)\\s*$")
    val matches = pattern.findAll(documentContent).toList()
    return matches.mapIndexedNotNull { index, match ->
        val page =
            match.groupValues.getOrNull(1)?.toIntOrNull()
                ?: return@mapIndexedNotNull null
        val start = match.range.last + 1
        val end =
            matches.getOrNull(index + 1)?.range?.first
                ?: documentContent.length
        page to documentContent.substring(start, end).trim()
    }.filter { it.second.isNotBlank() }
}

private fun balancedPageSamples(pages: List<Pair<Int, String>>): String {
    val selected =
        if (pages.size <= DOCUMENT_SAMPLE_COUNT) {
            pages
        } else {
            (0 until DOCUMENT_SAMPLE_COUNT).map { index ->
                pages[
                    (index * (pages.lastIndex.toFloat() / (DOCUMENT_SAMPLE_COUNT - 1)))
                        .roundToInt()
                ]
            }.distinctBy { it.first }
        }
    return selected.joinToString("\n\n") { (page, body) ->
        "## Page $page\n${body.take(DOCUMENT_PAGE_SAMPLE_CHARS)}"
    }
}

private fun List<Pair<Int, String>>.dropQuizBoundaryPages(): List<Pair<Int, String>> {
    if (size <= 2) return emptyList()
    return drop(1).dropLast(1)
        .filterNot { (_, body) -> body.isAdministrativeQuizPage() }
}

private data class QuizCandidateChunk(
    val label: String,
    val text: String,
    val score: Int
)

private fun quizCandidateChunks(documentContent: String): List<QuizCandidateChunk> {
    val pages = numberedPageSections(documentContent)
    return if (pages.isNotEmpty()) {
        pages.dropQuizBoundaryPages().quizChunksFromPages()
    } else {
        quizChunksFromParagraphs(documentContent)
    }
}

private fun List<Pair<Int, String>>.quizChunksFromPages(): List<QuizCandidateChunk> {
    return flatMap { (page, body) ->
        body.splitQuizChunks().mapIndexedNotNull { index, chunk ->
            val score = chunk.quizWorthinessScore()
            if (score >= QUIZ_WORTHY_MIN_SCORE) {
                QuizCandidateChunk(
                    label = "Page $page · chunk ${index + 1}",
                    text = chunk,
                    score = score
                )
            } else {
                null
            }
        }
    }
}

private fun quizChunksFromParagraphs(documentContent: String): List<QuizCandidateChunk> {
    val paragraphs = documentContent.splitQuizChunks()
    val bodyParagraphs =
        if (paragraphs.size > 4) {
            paragraphs.drop(1).dropLast(1)
        } else {
            paragraphs
        }
    return bodyParagraphs.mapIndexedNotNull { index, chunk ->
        val score = chunk.quizWorthinessScore()
        if (score >= QUIZ_WORTHY_MIN_SCORE) {
            QuizCandidateChunk(
                label = "Concept chunk ${index + 1}",
                text = chunk,
                score = score
            )
        } else {
            null
        }
    }
}

private fun String.isAdministrativeQuizPage(): Boolean {
    val lower = lowercase(Locale.US)
    val blockedTerms =
        listOf(
            "course intro",
            "instructor",
            "office hr",
            "office hour",
            "teaching assistant",
            "copyright",
            "mcgraw-hill",
            "references",
            "bibliography",
            "thank you",
            "appendix",
            "homework",
            "assignment",
            "exam",
            "grading",
            "grade",
            "policy",
            "attendance",
            "covid",
            "syllabus",
            "office",
            "@unist.ac.kr"
        )
    return blockedTerms.count { lower.containsAdminTerm(it) } >= 2
}

private fun String.splitQuizChunks(): List<String> {
    return split(Regex("\\n\\s*\\n|(?m)^[-•]\\s+"))
        .flatMap { chunk ->
            val trimmed = chunk.normalizeQuizText()
            if (trimmed.length > 700) {
                trimmed.chunked(600)
            } else {
                listOf(trimmed)
            }
        }
        .map { it.normalizeQuizText() }
        .filter { it.length in 80..900 }
        .filterNot { it.isAdministrativeQuizPage() }
}

private fun String.normalizeQuizText(): String = lineSequence()
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .joinToString(" ")
    .replace(Regex("\\s+"), " ")
    .trim()

/**
 * Matches an admin/boilerplate term with word boundaries for plain word terms
 * (so "exam" no longer matches "example", "ta" no longer matches "data"), while
 * keeping substring matching for terms with punctuation (e.g. "@unist.ac.kr",
 * "mcgraw-hill").
 */
private fun String.containsAdminTerm(term: String): Boolean {
    val trimmed = term.trim()
    return if (trimmed.matches(Regex("[a-z0-9 ]+"))) {
        Regex("\\b${Regex.escape(trimmed)}\\b").containsMatchIn(this)
    } else {
        contains(term)
    }
}

private fun String.quizWorthinessScore(): Int {
    val lower = lowercase(Locale.US)
    val adminPenalty =
        QUIZ_ADMIN_TERMS.count { term ->
            lower.containsAdminTerm(term)
        } * 8
    if (adminPenalty >= 16) return 0
    val conceptScore =
        EngineeringMechanicsConceptCatalog.concepts.maxOf { concept ->
            concept.keywords.sumOf { keyword ->
                Regex("\\b${Regex.escape(keyword.lowercase(Locale.US))}\\b")
                    .findAll(lower)
                    .count()
            }
        } * 8
    if (conceptScore == 0) return 0
    val explanationScore = QUIZ_EXPLANATION_TERMS.count { lower.contains(it) } * 2
    val mathScore =
        listOf("=", "m/s", "rad", "ft/s", "kg", "n ", "lb").count {
            lower.contains(it)
        } * 2
    val densityScore = if (count { it.isLetterOrDigit() } >= 60) 2 else 0
    return conceptScore + explanationScore + mathScore + densityScore - adminPenalty
}

private fun String.hasKcQuizEvidence(): Boolean = quizWorthinessScore() >= QUIZ_WORTHY_MIN_SCORE

private fun balancedParagraphSamples(documentContent: String): String {
    val paragraphs =
        documentContent.split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.length >= 80 }
    if (paragraphs.isEmpty()) return documentContent.take(QUIZ_CONTEXT_CHARS)
    val quizParagraphs =
        if (paragraphs.size > 4) {
            paragraphs.drop(1).dropLast(1)
        } else {
            paragraphs
        }.filterNot { it.isAdministrativeQuizPage() }
    if (quizParagraphs.isEmpty()) return ""
    val selected =
        if (quizParagraphs.size <= DOCUMENT_SAMPLE_COUNT) {
            quizParagraphs
        } else {
            (0 until DOCUMENT_SAMPLE_COUNT).map { index ->
                val sampleIndex =
                    index *
                        (quizParagraphs.lastIndex.toFloat() / (DOCUMENT_SAMPLE_COUNT - 1))
                quizParagraphs[sampleIndex.roundToInt()]
            }
        }
    return selected.joinToString("\n\n---\n\n") {
        it.take(DOCUMENT_PAGE_SAMPLE_CHARS)
    }
}

private fun relevantSnippets(
    documentContent: String,
    query: String,
    excluded: String,
    maxChars: Int
): String {
    val tokens =
        query.lowercase(Locale.US)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 }
            .toSet()
    if (tokens.isEmpty()) return ""
    return documentContent.split(Regex("\\n\\s*\\n"))
        .asSequence()
        .map { it.trim() }
        .filter { it.length in 40..1200 && it !in excluded }
        .map { snippet ->
            val lower = snippet.lowercase(Locale.US)
            val score = tokens.count { lower.contains(it) }
            score to snippet
        }
        .filter { it.first > 0 }
        .sortedByDescending { it.first }
        .map { it.second }
        .take(8)
        .joinToString("\n\n---\n\n")
        .take(maxChars)
}

private const val CHAT_CONTEXT_CHARS = 7000
private const val QUIZ_CONTEXT_CHARS = 2800
private const val DOCUMENT_SAMPLE_COUNT = 4
private const val DOCUMENT_PAGE_SAMPLE_CHARS = 420
private const val QUIZ_WORTHY_MIN_SCORE = 8
private const val WEAK_KC_COUNT = 3
private const val WEAKNESS_SAMPLE_COUNT = 3
private val QUIZ_ADMIN_TERMS =
    listOf(
        "course intro",
        "instructor",
        "office hr",
        "office hour",
        "ta ",
        "teaching assistant",
        "email",
        "@unist.ac.kr",
        "homework",
        "assignment",
        "exam",
        "grading",
        "grade",
        "policy",
        "attendance",
        "covid",
        "syllabus",
        "copyright",
        "mcgraw-hill",
        "references",
        "bibliography",
        "thank you"
    )
private val QUIZ_EXPLANATION_TERMS =
    listOf(
        "because",
        "therefore",
        "when",
        "if",
        "where",
        "relation",
        "equation",
        "component",
        "direction",
        "magnitude",
        "vector",
        "motion",
        "force",
        "moment",
        "energy",
        "velocity",
        "acceleration"
    )

private data class QueuedLlmRequest(
    val text: String,
    val images: List<ByteArray>
)

enum class StudySidebarMode {
    CHAT,
    QUIZ
}

private enum class HistoryPane {
    CHAT,
    QUIZ
}

private data class QuizSourceOverride(
    val content: String,
    val label: String
)

private enum class QuizScopeMode(
    val label: String,
    val selectionMode: String
) {
    DOCUMENT("페이지 범위", "page_range"),
    CURRENT_PAGE("현재 페이지", "current_page"),
    SELECTION("선택 영역", "selection"),
    WEAKNESS("약점 보완", "weakness")
}

@Composable
fun LlmSidebar(
    isVisible: Boolean,
    onCollapse: () -> Unit,
    llmService: LlmService,
    quizService: QuizService,
    localMlKitContentExtractor: LocalMlKitContentExtractor,
    settingsRepository: SettingsRepository,
    conversationDataSource: ConversationLocalDataSource,
    documentContent: String? = null,
    documentJsonContent: String? = null,
    documentId: String,
    pageIndex: Int = 0,
    pageCount: Int = 0,
    quizMastery: Float = 0.35f,
    quizConceptMastery: Pair<Float, Float>? = null,
    quizConceptCurrentMastery: Float? = null,
    quizHistory: List<QuizResponseRecord> = emptyList(),
    weakConcepts: List<ConceptKnowledge> = emptyList(),
    sidebarMode: StudySidebarMode = StudySidebarMode.CHAT,
    onSidebarModeChanged: (StudySidebarMode) -> Unit = {},
    pendingImage: ByteArray? = null,
    pendingPrompt: String? = null,
    llmConnectionState: LlmConnectionState = LlmConnectionState.READY,
    llmConnectionError: String? = null,
    onRetryConnection: () -> Unit = {},
    onLlmRequested: (prompt: String, hasImage: Boolean) -> Unit = { _, _ -> },
    onQuizRequested: (conceptId: String, bloomLevel: Int) -> Unit = { _, _ -> },
    onQuizGenerated: (quiz: GeneratedQuizQuestion) -> Unit = {},
    onQuizAnswered: (
        conceptId: String,
        bloomLevel: Int,
        isCorrect: Boolean,
        responseTimeMs: Long?,
        question: String,
        choices: Map<String, String>,
        selectedAnswer: String,
        correctAnswer: String,
        explanation: String,
        choiceExplanations: Map<String, String>,
        sourceSentence: String
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _ -> },
    onQuizHistoryDeleted: (String) -> Unit = {},
    onPendingConsumed: () -> Unit = {},
    pendingQuizCrop: CropCapturePayload? = null,
    onPendingQuizCropConsumed: () -> Unit = {},
    pendingQuizText: SelectedTextQuizPayload? = null,
    onPendingQuizTextConsumed: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val minWidthPx =
        with(density) {
            UxConfig.Viewer.SIDEBAR_MIN_WIDTH.toPx()
        }
    val maxWidthPx =
        with(density) {
            UxConfig.Viewer.SIDEBAR_MAX_WIDTH.toPx()
        }
    val defaultWidthPx =
        with(density) {
            UxConfig.Viewer.SIDEBAR_DEFAULT_WIDTH.toPx()
        }

    val savedProviderName by settingsRepository
        .getLlmProvider()
        .collectAsState(initial = null)
    val currentProvider =
        savedProviderName
            ?: LlmProvider.OPENROUTER.name
    val geminiKey by settingsRepository
        .getGeminiApiKey()
        .collectAsState(initial = null)
    val openAiKey by settingsRepository
        .getOpenAiApiKey()
        .collectAsState(initial = null)
    val claudeKey by settingsRepository
        .getClaudeApiKey()
        .collectAsState(initial = null)
    val openRouterKey by settingsRepository
        .getOpenRouterApiKey()
        .collectAsState(initial = null)
    val hasApiKey =
        when (currentProvider) {
            LlmProvider.OPENAI.name ->
                !openAiKey.isNullOrBlank()
            LlmProvider.CLAUDE.name ->
                !claudeKey.isNullOrBlank()
            LlmProvider.OPENROUTER.name ->
                !openRouterKey.isNullOrBlank()
            else -> !geminiKey.isNullOrBlank()
        }
    val savedModel by settingsRepository
        .getLlmModel()
        .collectAsState(initial = null)
    val quizLanguage by settingsRepository
        .getQuizLanguage()
        .collectAsState(initial = "ko")
    var availableModels by remember {
        mutableStateOf<List<String>>(emptyList())
    }
    var modelsLoading by remember {
        mutableStateOf(false)
    }

    fun defaultModelsFor(provider: String): List<String> {
        return when (provider) {
            LlmProvider.OPENAI.name ->
                listOf(OpenAiClient.DEFAULT_MODEL)
            LlmProvider.CLAUDE.name ->
                listOf(ClaudeClient.DEFAULT_MODEL)
            LlmProvider.OPENROUTER.name ->
                listOf(OpenAiClient.OPENROUTER_DEFAULT_MODEL)
            else ->
                listOf(
                    LlmRequestBuilder.DEFAULT_MODEL
                )
        }
    }

    var widthPx by remember {
        mutableStateOf(defaultWidthPx)
    }
    val messages =
        remember {
            mutableStateListOf<ChatMessage>()
        }
    var currentInput by remember {
        mutableStateOf("")
    }
    var isLoading by remember {
        mutableStateOf(false)
    }
    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }
    var activeJob by remember {
        mutableStateOf<kotlinx.coroutines.Job?>(null)
    }
    var queuedRequest by remember {
        mutableStateOf<QueuedLlmRequest?>(null)
    }

    LaunchedEffect(currentProvider) {
        availableModels = defaultModelsFor(currentProvider)
        modelsLoading = false
    }

    fun loadModelsIfNeeded() {
        if (!hasApiKey || modelsLoading) return
        scope.launch {
            modelsLoading = true
            try {
                val fetched = llmService.fetchModels()
                if (fetched.isNotEmpty()) {
                    availableModels = fetched
                }
            } catch (e: Exception) {
                errorMessage = "모델 목록 실패: " +
                    "${e.message}"
            } finally {
                modelsLoading = false
            }
        }
    }

    var conversationId by remember {
        mutableStateOf<String?>(null)
    }
    var showHistory by remember {
        mutableStateOf(false)
    }
    var historyPane by remember {
        mutableStateOf(HistoryPane.CHAT)
    }
    var historyList by remember {
        mutableStateOf<List<ConversationSummary>>(
            emptyList()
        )
    }
    val listState = rememberLazyListState()

    // Track pending images for next send
    var pendingImages by remember {
        mutableStateOf<List<ByteArray>>(emptyList())
    }

    var quizSourceOverride by remember(documentId) {
        mutableStateOf<QuizSourceOverride?>(null)
    }
    var quizScopeMode by remember(documentId) {
        mutableStateOf(QuizScopeMode.DOCUMENT)
    }
    var quizPageRangeSpec by remember(documentId) {
        mutableStateOf((pageIndex + 1).toString())
    }
    var quizPageRangeError by remember(documentId) {
        mutableStateOf<String?>(null)
    }
    val baseQuizContent =
        remember(
            documentContent,
            pageIndex,
            quizScopeMode,
            quizSourceOverride,
            quizHistory,
            quizPageRangeSpec,
            pageCount,
            weakConcepts
        ) {
            val content = documentContent.orEmpty()
            when (quizScopeMode) {
                QuizScopeMode.CURRENT_PAGE ->
                    buildCurrentPageQuizContext(
                        documentContent = content,
                        pageIndex = pageIndex
                    )
                QuizScopeMode.WEAKNESS ->
                    buildWeaknessQuizContext(
                        documentContent = content,
                        weakConcepts = weakConcepts,
                        quizHistory = quizHistory,
                        fallbackPageIndex = pageIndex
                    )
                QuizScopeMode.SELECTION -> quizSourceOverride?.content.orEmpty()
                QuizScopeMode.DOCUMENT ->
                    buildPageRangeQuizContext(
                        documentContent = content,
                        pages =
                        parsePageRangeSpec(
                            spec = quizPageRangeSpec,
                            maxPage = pageCount
                        ).getOrNull().orEmpty()
                    )
            }
        }
    val quizContent =
        quizSourceOverride?.content
            ?: baseQuizContent
    // Whether the page currently shown in the viewer yields quiz-worthy content.
    // Used to enable the "현재 페이지" scope only on a generatable page.
    val currentPageHasContent =
        remember(documentContent, pageIndex) {
            buildCurrentPageQuizContext(
                documentContent = documentContent.orEmpty(),
                pageIndex = pageIndex
            ).isNotBlank()
        }
    val quizSourceLabel =
        quizSourceOverride?.label
            ?: quizScopeMode.label
    val quizConceptName =
        remember(quizContent) {
            extractQuizConcept(quizContent)
        }
    val quizConceptId =
        remember(documentId, quizConceptName) {
            stableConceptId(documentId, quizConceptName)
        }
    var selectedBloomLevel by remember(
        documentId,
        quizConceptName,
        quizMastery
    ) {
        mutableStateOf(
            quizService.defaultBloomLevel(quizMastery)
        )
    }
    var currentQuiz by remember(documentId) {
        mutableStateOf<GeneratedQuizQuestion?>(null)
    }
    var selectedQuizChoice by remember(documentId) {
        mutableStateOf<String?>(null)
    }
    var selectedQuizChoices by remember(documentId) {
        mutableStateOf<Set<String>>(emptySet())
    }
    var quizAnswered by remember(documentId) {
        mutableStateOf(false)
    }
    var quizLoading by remember(documentId) {
        mutableStateOf(false)
    }
    var quizProgress by remember(documentId) {
        mutableStateOf<QuizProgress?>(null)
    }
    var quizError by remember(documentId) {
        mutableStateOf<String?>(null)
    }
    var quizStartedAt by remember(documentId) {
        mutableStateOf<Long?>(null)
    }
    var pendingTextQuizGeneration by remember(documentId) {
        mutableStateOf(false)
    }

    LaunchedEffect(pendingQuizCrop, documentJsonContent) {
        val payload =
            pendingQuizCrop
                ?: return@LaunchedEffect
        val selection =
            StructuredContentCropExtractor.extract(
                documentJsonContent,
                payload
            )
        currentQuiz = null
        selectedQuizChoice = null
        selectedQuizChoices = emptySet()
        quizAnswered = false
        quizStartedAt = null
        quizLoading = false
        if (selection.content.isBlank()) {
            val ocrText =
                runCatching {
                    localMlKitContentExtractor.extractImageText(
                        payload.imageBytes
                    )
                }.getOrDefault("")
            if (ocrText.isBlank()) {
                quizSourceOverride = null
                quizError =
                    "선택한 영역에서 추출 가능한 텍스트를 찾지 " +
                    "못했습니다. 텍스트가 포함된 영역을 선택하거나 " +
                    "AI 설명 기능을 사용해 주세요."
            } else {
                quizSourceOverride =
                    QuizSourceOverride(
                        content = ocrText,
                        label = "선택 영역 OCR · 페이지 ${payload.pageIndex + 1}"
                    )
                quizScopeMode = QuizScopeMode.SELECTION
                quizError = null
            }
        } else {
            quizSourceOverride =
                QuizSourceOverride(
                    content = selection.content,
                    label = selection.label
                )
            quizScopeMode = QuizScopeMode.SELECTION
            quizError = null
        }
        onPendingQuizCropConsumed()
    }

    fun generateQuizQuestion() {
        if (quizScopeMode == QuizScopeMode.DOCUMENT) {
            val parsed =
                parsePageRangeSpec(
                    spec = quizPageRangeSpec,
                    maxPage = pageCount
                )
            if (parsed.isFailure || parsed.getOrNull().orEmpty().isEmpty()) {
                quizPageRangeError = PAGE_RANGE_ERROR
                quizError = PAGE_RANGE_ERROR
                return
            }
            quizPageRangeError = null
        }
        if (quizContent.isBlank() ||
            quizLoading ||
            llmConnectionState != LlmConnectionState.READY ||
            !hasApiKey
        ) {
            return
        }
        if (!quizContent.hasKcQuizEvidence()) {
            quizError =
                "선택한 범위에서 업로드된 KC셋과 연결되는 학습 내용을 찾지 못했습니다. " +
                "공학역학 KC가 포함된 페이지나 영역을 선택해 주세요."
            return
        }
        quizLoading = true
        quizProgress = null
        quizError = null
        selectedQuizChoice = null
        selectedQuizChoices = emptySet()
        quizAnswered = false
        currentQuiz = null
        onQuizRequested(quizConceptId, selectedBloomLevel)
        scope.launch {
            try {
                val generated =
                    quizService.generateQuestion(
                        QuizGenerationRequest(
                            documentContent = quizContent,
                            conceptName = quizConceptName,
                            mastery = quizMastery,
                            bloomLevel = selectedBloomLevel,
                            selectionMode = quizScopeMode.selectionMode,
                            sourceLabel = quizSourceLabel,
                            language = quizLanguage
                        )
                    ) { progress ->
                        quizProgress = progress
                    }
                currentQuiz = generated
                quizStartedAt = System.currentTimeMillis()
                // Send the generated quiz to the QE server and cache the
                // returned representation locally.
                onQuizGenerated(generated)
            } catch (e: Exception) {
                quizError = mapQuizError(e)
            } finally {
                quizLoading = false
                quizProgress = null
            }
        }
    }

    LaunchedEffect(pendingQuizText) {
        val payload =
            pendingQuizText
                ?: return@LaunchedEffect
        currentQuiz = null
        selectedQuizChoice = null
        selectedQuizChoices = emptySet()
        quizAnswered = false
        quizStartedAt = null
        quizLoading = false
        if (payload.text.isBlank()) {
            quizSourceOverride = null
            quizError =
                "선택한 텍스트가 비어 있어 퀴즈를 만들 수 없습니다."
            pendingTextQuizGeneration = false
        } else {
            quizSourceOverride =
                QuizSourceOverride(
                    content = payload.text,
                    label = payload.label
                )
            quizScopeMode = QuizScopeMode.SELECTION
            quizError = null
            pendingTextQuizGeneration = true
        }
        onPendingQuizTextConsumed()
    }

    LaunchedEffect(
        pendingTextQuizGeneration,
        quizContent,
        llmConnectionState,
        hasApiKey,
        quizLoading
    ) {
        if (!pendingTextQuizGeneration) {
            return@LaunchedEffect
        }
        if (!hasApiKey) {
            quizError =
                "현재 LLM provider의 API 키가 없어 선택 텍스트 퀴즈를 생성할 수 없습니다."
            pendingTextQuizGeneration = false
            return@LaunchedEffect
        }
        if (llmConnectionState == LlmConnectionState.FAILED) {
            quizError =
                "LLM 서버 연결이 준비되지 않아 선택 텍스트 퀴즈를 생성하지 못했습니다. 연결 재시도 후 다시 선택해 주세요."
            pendingTextQuizGeneration = false
            return@LaunchedEffect
        }
        if (
            llmConnectionState == LlmConnectionState.READY &&
            quizContent.isNotBlank() &&
            !quizLoading
        ) {
            pendingTextQuizGeneration = false
            generateQuizQuestion()
        }
    }

    LaunchedEffect(conversationId) {
        val id =
            conversationId ?: return@LaunchedEffect
        val loaded =
            conversationDataSource.loadMessages(id)
        messages.clear()
        messages.addAll(loaded)
    }

    val lastMsgContent =
        messages.lastOrNull()?.content?.length ?: 0
    LaunchedEffect(messages.size, lastMsgContent) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(
                messages.size - 1
            )
        }
    }

    // Consume pending crop/quiz prompt
    fun sendMessage(text: String, imgs: List<ByteArray> = emptyList()) {
        activeJob?.cancel()
        isLoading = false
        errorMessage = null
        onLlmRequested(text, imgs.isNotEmpty())
        val userMsg =
            ChatMessage(
                role = ChatMessage.Role.USER,
                content = text
            )
        messages.add(userMsg)
        activeJob =
            scope.launch {
                val convId =
                    conversationId
                        ?: conversationDataSource
                            .create()
                            .also { conversationId = it }
                conversationDataSource
                    .appendMessage(convId, userMsg)
                val extraOcrContext =
                    if (imgs.isNotEmpty()) {
                        runCatching {
                            localMlKitContentExtractor.extractImageText(
                                imgs.first()
                            )
                        }.getOrDefault("")
                    } else {
                        ""
                    }
                streamAssistantResponse(
                    messages,
                    llmService,
                    conversationDataSource,
                    convId,
                    imgs,
                    documentContent,
                    pageIndex,
                    text,
                    extraOcrContext,
                    { errorMessage = it },
                    { isLoading = it }
                )
            }
    }

    fun submitMessage(text: String, imgs: List<ByteArray> = emptyList()) {
        when (llmConnectionState) {
            LlmConnectionState.READY -> {
                sendMessage(text, imgs)
            }
            LlmConnectionState.CONNECTING,
            LlmConnectionState.FAILED
            -> {
                queuedRequest = QueuedLlmRequest(text, imgs)
                errorMessage = null
                if (llmConnectionState ==
                    LlmConnectionState.FAILED
                ) {
                    onRetryConnection()
                }
            }
        }
    }

    LaunchedEffect(llmConnectionState, queuedRequest) {
        val request = queuedRequest
        if (
            llmConnectionState == LlmConnectionState.READY &&
            request != null
        ) {
            queuedRequest = null
            sendMessage(request.text, request.images)
        }
    }

    var lastProcessedPrompt by remember {
        mutableStateOf<String?>(null)
    }
    if (pendingPrompt != null &&
        pendingPrompt != lastProcessedPrompt
    ) {
        lastProcessedPrompt = pendingPrompt
        val imgs =
            if (pendingImage != null) {
                listOf(pendingImage)
            } else {
                emptyList()
            }
        val text =
            pendingPrompt
                .replace(
                    Regex("\\n<!--\\d+-->$"),
                    ""
                )
        onPendingConsumed()
        submitMessage(text, imgs)
    }

    if (!isVisible) return

    val widthDp = with(density) { widthPx.toDp() }

    Row(
        modifier =
        Modifier
            .width(widthDp)
            .fillMaxHeight()
    ) {
        // Drag handle
        Box(
            modifier =
            Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(
                    MaterialTheme.colorScheme
                        .outlineVariant
                )
                .pointerInput(Unit) {
                    detectHorizontalDragGestures {
                            _,
                            dx
                        ->
                        widthPx =
                            (widthPx - dx)
                                .coerceIn(
                                    minWidthPx,
                                    maxWidthPx
                                )
                    }
                }
        )

        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.surface
                )
                // Shrink the panel when the soft keyboard is shown so the
                // chat input stays visible above it (app uses edge-to-edge
                // with decorFitsSystemWindows=false, so resize isn't automatic).
                .imePadding()
        ) {
            SidebarTopBar(
                currentProvider = currentProvider,
                onProviderSelected = { provider ->
                    activeJob?.cancel()
                    activeJob = null
                    conversationId = null
                    messages.clear()
                    errorMessage = null
                    isLoading = false
                    scope.launch {
                        settingsRepository
                            .setLlmProvider(provider)
                        settingsRepository
                            .setLlmModel("")
                    }
                },
                currentModel =
                savedModel?.ifBlank {
                    null
                } ?: when (currentProvider) {
                    LlmProvider.OPENAI.name ->
                        OpenAiClient.DEFAULT_MODEL
                    LlmProvider.CLAUDE.name ->
                        ClaudeClient.DEFAULT_MODEL
                    LlmProvider.OPENROUTER.name ->
                        OpenAiClient.OPENROUTER_DEFAULT_MODEL
                    else ->
                        LlmRequestBuilder.DEFAULT_MODEL
                },
                availableModels = availableModels,
                modelsLoading = modelsLoading,
                onModelMenuOpened = {
                    loadModelsIfNeeded()
                },
                onModelSelected = { model ->
                    activeJob?.cancel()
                    activeJob = null
                    conversationId = null
                    messages.clear()
                    errorMessage = null
                    isLoading = false
                    scope.launch {
                        settingsRepository
                            .setLlmModel(model)
                    }
                },
                onCollapse = onCollapse,
                onNewConversation = {
                    onSidebarModeChanged(StudySidebarMode.CHAT)
                    activeJob?.cancel()
                    activeJob = null
                    conversationId = null
                    messages.clear()
                    errorMessage = null
                    currentInput = ""
                    pendingImages = emptyList()
                    isLoading = false
                    showHistory = false
                },
                onToggleHistory = {
                    if (showHistory) {
                        onSidebarModeChanged(
                            when (historyPane) {
                                HistoryPane.CHAT -> StudySidebarMode.CHAT
                                HistoryPane.QUIZ -> StudySidebarMode.QUIZ
                            }
                        )
                        showHistory = false
                    } else {
                        historyPane =
                            if (sidebarMode == StudySidebarMode.QUIZ) {
                                HistoryPane.QUIZ
                            } else {
                                HistoryPane.CHAT
                            }
                        historyList =
                            conversationDataSource
                                .listConversations()
                        showHistory = true
                    }
                },
                showHistory = showHistory
            )

            HorizontalDivider(
                color =
                MaterialTheme.colorScheme
                    .outlineVariant
            )

            StudySidebarTabs(
                mode = sidebarMode,
                showHistory = showHistory,
                historyPane = historyPane,
                onModeChanged = { mode ->
                    showHistory = false
                    onSidebarModeChanged(mode)
                },
                onHistoryPaneChanged = {
                    historyPane = it
                }
            )

            if (showHistory) {
                UnifiedHistoryPanel(
                    pane = historyPane,
                    onPaneChanged = {
                        historyPane = it
                    },
                    conversations = historyList,
                    currentConversationId = conversationId,
                    quizHistory = quizHistory,
                    onSelectConversation = { summary ->
                        activeJob?.cancel()
                        activeJob = null
                        conversationId = summary.id
                        isLoading = false
                        errorMessage = null
                        showHistory = false
                        onSidebarModeChanged(StudySidebarMode.CHAT)
                    },
                    onDeleteConversation = { summary ->
                        conversationDataSource
                            .deleteConversation(summary.id)
                        historyList =
                            historyList
                                .filter { it.id != summary.id }
                        if (conversationId == summary.id) {
                            conversationId = null
                            messages.clear()
                        }
                    },
                    onDeleteQuizRecord = { record ->
                        onQuizHistoryDeleted(record.id)
                    },
                    modifier = Modifier.weight(1f)
                )
            } else if (sidebarMode == StudySidebarMode.QUIZ) {
                QuizPanel(
                    quizService = quizService,
                    documentContent = quizContent,
                    sourceLabel = quizSourceLabel,
                    onClearSource = {
                        quizSourceOverride = null
                        quizScopeMode = QuizScopeMode.DOCUMENT
                        currentQuiz = null
                        selectedQuizChoice = null
                        selectedQuizChoices = emptySet()
                        quizAnswered = false
                        quizError = null
                    },
                    scopeMode = quizScopeMode,
                    onScopeModeSelected = { mode ->
                        quizScopeMode = mode
                        if (mode != QuizScopeMode.SELECTION) {
                            quizSourceOverride = null
                        }
                        currentQuiz = null
                        selectedQuizChoice = null
                        selectedQuizChoices = emptySet()
                        quizAnswered = false
                        quizError = null
                    },
                    hasSelectionSource = quizSourceOverride != null,
                    currentPageHasContent = currentPageHasContent,
                    pageIndex = pageIndex,
                    pageCount = pageCount,
                    conceptName = quizConceptName,
                    conceptId = quizConceptId,
                    mastery = quizMastery,
                    conceptMastery = quizConceptMastery,
                    conceptCurrentMastery = quizConceptCurrentMastery,
                    selectedBloomLevel = selectedBloomLevel,
                    onBloomLevelSelected = {
                        selectedBloomLevel = it
                    },
                    quizLanguage = quizLanguage,
                    onQuizLanguageChange = { lang ->
                        scope.launch {
                            settingsRepository.setQuizLanguage(lang)
                        }
                    },
                    pageRangeSpec = quizPageRangeSpec,
                    pageRangeError = quizPageRangeError,
                    onPageRangeSpecChanged = {
                        quizPageRangeSpec = it
                        quizPageRangeError = null
                        currentQuiz = null
                        quizError = null
                    },
                    quiz = currentQuiz,
                    selectedChoice = selectedQuizChoice,
                    selectedChoices = selectedQuizChoices,
                    answered = quizAnswered,
                    loading = quizLoading,
                    progress = quizProgress,
                    error = quizError,
                    connectionState = llmConnectionState,
                    connectionError = llmConnectionError,
                    hasApiKey = hasApiKey,
                    onRetryConnection = onRetryConnection,
                    onGenerateQuestion = {
                        generateQuizQuestion()
                    },
                    onAnswerChanged = { choices ->
                        selectedQuizChoices = choices
                        selectedQuizChoice = choices.sorted().joinToString(",")
                    },
                    onAnswerSubmitted = { submitted, current ->
                        if (!quizAnswered) {
                            if (submitted.isNotEmpty()) {
                                selectedQuizChoices = submitted
                                selectedQuizChoice =
                                    submitted.sorted().joinToString(",")
                                quizAnswered = true
                                val correct =
                                    submitted == current.answerKeys.toSet()
                                val elapsed =
                                    quizStartedAt?.let {
                                        System.currentTimeMillis() - it
                                    }
                                val submittedText =
                                    submitted.sorted().joinToString(",")
                                onQuizAnswered(
                                    quizConceptId,
                                    current.bloomLevel,
                                    correct,
                                    elapsed,
                                    current.question,
                                    current.choices,
                                    submittedText,
                                    current.answer,
                                    current.explanation,
                                    current.choiceExplanations,
                                    current.sourceSentence
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            } else if (!hasApiKey) {
                Box(
                    modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment =
                        Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = null,
                            modifier =
                            Modifier
                                .size(48.dp),
                            tint =
                            MaterialTheme
                                .colorScheme
                                .outline
                        )
                        Spacer(Modifier.height(16.dp))
                        val providerName =
                            when (currentProvider) {
                                LlmProvider.OPENAI
                                    .name
                                -> "ChatGPT"
                                LlmProvider.CLAUDE
                                    .name
                                -> "Claude"
                                LlmProvider.OPENROUTER
                                    .name
                                -> "OpenRouter"
                                else -> "Gemini"
                            }
                        Text(
                            "$providerName API 키가 " +
                                "설정되지 않았습니다",
                            fontSize = 15.sp,
                            fontWeight =
                            FontWeight
                                .SemiBold,
                            color =
                            MaterialTheme
                                .colorScheme
                                .onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "설정 화면에서 $providerName" +
                                " API 키를 입력해주세요.",
                            fontSize = 13.sp,
                            color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                        )
                    }
                }
            } else {
                if (
                    llmConnectionState != LlmConnectionState.READY ||
                    queuedRequest != null
                ) {
                    LlmConnectionStatusBanner(
                        connectionState = llmConnectionState,
                        errorMessage = llmConnectionError,
                        hasQueuedRequest = queuedRequest != null,
                        onRetryConnection = onRetryConnection
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement =
                    Arrangement
                        .spacedBy(8.dp)
                ) {
                    item {
                        Spacer(Modifier.height(8.dp))
                    }
                    itemsIndexed(messages) { index, msg ->
                        MessageBubble(
                            message = msg,
                            isStreaming =
                            isLoading &&
                                index == messages.lastIndex &&
                                msg.role == ChatMessage.Role.ASSISTANT
                        )
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color =
                        MaterialTheme
                            .colorScheme.error,
                        fontSize = 12.sp,
                        modifier =
                        Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 4.dp
                        )
                    )
                }
            }

            HorizontalDivider(
                color =
                MaterialTheme.colorScheme
                    .outlineVariant
            )

            if (!showHistory && sidebarMode == StudySidebarMode.CHAT) {
                SidebarInput(
                    value = currentInput,
                    onValueChange = {
                        currentInput = it
                    },
                    isLoading = isLoading,
                    pendingImages = pendingImages,
                    onAddImage = { bytes ->
                        pendingImages =
                            pendingImages + bytes
                    },
                    onRemoveImage = { index ->
                        pendingImages =
                            pendingImages
                                .toMutableList()
                                .apply { removeAt(index) }
                    },
                    onSend = {
                        val text = currentInput.trim()
                        if (text.isBlank() &&
                            pendingImages.isEmpty()
                        ) {
                            return@SidebarInput
                        }
                        val msgText =
                            text.ifBlank {
                                "이 이미지를 분석해줘"
                            }
                        val imgs = pendingImages
                        pendingImages = emptyList()
                        currentInput = ""
                        submitMessage(msgText, imgs)
                    }
                )
            }
        }
    }
}

@Composable
private fun StudySidebarTabs(
    mode: StudySidebarMode,
    showHistory: Boolean,
    historyPane: HistoryPane,
    onModeChanged: (StudySidebarMode) -> Unit,
    onHistoryPaneChanged: (HistoryPane) -> Unit
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SidebarTab(
            label =
            if (showHistory) {
                "Chat History"
            } else {
                "Chat"
            },
            selected =
            if (showHistory) {
                historyPane == HistoryPane.CHAT
            } else {
                mode == StudySidebarMode.CHAT
            },
            icon = {
                Icon(
                    Icons.Default.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            onClick = {
                if (showHistory) {
                    onHistoryPaneChanged(HistoryPane.CHAT)
                } else {
                    onModeChanged(StudySidebarMode.CHAT)
                }
            },
            modifier = Modifier.weight(1f)
        )
        SidebarTab(
            label =
            if (showHistory) {
                "Quiz History"
            } else {
                "Quiz"
            },
            selected =
            if (showHistory) {
                historyPane == HistoryPane.QUIZ
            } else {
                mode == StudySidebarMode.QUIZ
            },
            icon = {
                Icon(
                    Icons.Default.Quiz,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            onClick = {
                if (showHistory) {
                    onHistoryPaneChanged(HistoryPane.QUIZ)
                } else {
                    onModeChanged(StudySidebarMode.QUIZ)
                }
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SidebarTab(
    label: String,
    selected: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val fg =
        if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    Row(
        modifier =
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides fg
        ) {
            icon()
        }
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = fg
        )
    }
}

@Composable
private fun QuizPanel(
    quizService: QuizService,
    documentContent: String?,
    sourceLabel: String?,
    onClearSource: () -> Unit,
    scopeMode: QuizScopeMode,
    onScopeModeSelected: (QuizScopeMode) -> Unit,
    hasSelectionSource: Boolean,
    currentPageHasContent: Boolean,
    pageIndex: Int,
    pageCount: Int,
    conceptName: String,
    conceptId: String,
    mastery: Float,
    conceptMastery: Pair<Float, Float>? = null,
    conceptCurrentMastery: Float? = null,
    selectedBloomLevel: Int,
    onBloomLevelSelected: (Int) -> Unit,
    quizLanguage: String,
    onQuizLanguageChange: (String) -> Unit,
    pageRangeSpec: String,
    pageRangeError: String?,
    onPageRangeSpecChanged: (String) -> Unit,
    quiz: GeneratedQuizQuestion?,
    selectedChoice: String?,
    selectedChoices: Set<String>,
    answered: Boolean,
    loading: Boolean,
    progress: QuizProgress?,
    error: String?,
    connectionState: LlmConnectionState,
    connectionError: String?,
    hasApiKey: Boolean,
    onRetryConnection: () -> Unit,
    onGenerateQuestion: () -> Unit,
    onAnswerChanged: (Set<String>) -> Unit,
    onAnswerSubmitted: (Set<String>, GeneratedQuizQuestion) -> Unit,
    modifier: Modifier = Modifier
) {
    val content = documentContent.orEmpty()

    LazyColumn(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(6.dp))
            QuizHeader(
                conceptName = conceptName,
                conceptId = conceptId,
                // Show the quizzed concept's mastery (matches the gauge) when we
                // have it; fall back to the document average otherwise.
                mastery = conceptCurrentMastery ?: mastery,
                pageIndex = pageIndex,
                scopeMode = scopeMode,
                onScopeModeSelected = onScopeModeSelected,
                hasSelectionSource = hasSelectionSource,
                currentPageHasContent = currentPageHasContent,
                selectedBloomLevel = selectedBloomLevel,
                onBloomLevelSelected = {
                    onBloomLevelSelected(it)
                },
                quizLanguage = quizLanguage,
                onQuizLanguageChange = onQuizLanguageChange,
                quizService = quizService
            )
        }

        if (scopeMode == QuizScopeMode.DOCUMENT) {
            item {
                QuizPageRangeControls(
                    value = pageRangeSpec,
                    pageCount = pageCount,
                    error = pageRangeError,
                    onValueChanged = onPageRangeSpecChanged
                )
            }
        }

        sourceLabel?.let { label ->
            item {
                QuizSourceCard(
                    label = label,
                    onClear = onClearSource
                )
            }
        }

        if (!hasApiKey) {
            item {
                QuizMessage(
                    title = "API 키가 필요합니다",
                    body = "설정 화면에서 현재 LLM provider의 API 키를 입력하면 퀴즈를 생성할 수 있습니다."
                )
            }
        } else if (connectionState != LlmConnectionState.READY) {
            item {
                LlmConnectionStatusBanner(
                    connectionState = connectionState,
                    errorMessage = connectionError,
                    hasQueuedRequest = false,
                    onRetryConnection = onRetryConnection
                )
            }
        } else if (content.isBlank()) {
            item {
                QuizMessage(
                    title = "KC와 연결된 학습 내용이 없습니다",
                    body = "선택한 범위에서 업로드된 KC셋과 직접 연결되는 문단을 찾지 못했습니다."
                )
            }
        }

        if (loading) {
            item {
                QuizLoadingCard(progress = progress)
            }
        }

        error?.let { message ->
            item {
                QuizMessage(
                    title = "퀴즈 생성 실패",
                    body = message,
                    isError = true
                )
            }
        }

        quiz?.let { currentQuiz ->
            item {
                QuizQuestionCard(
                    quiz = currentQuiz,
                    mastery = mastery,
                    conceptMastery = conceptMastery,
                    selectedChoice = selectedChoice,
                    selectedChoices = selectedChoices,
                    answered = answered,
                    onAnswerChanged = onAnswerChanged,
                    onSubmit = { submitted ->
                        onAnswerSubmitted(submitted, currentQuiz)
                    }
                )
            }
        }

        item {
            QuizActionButton(
                loading = loading,
                enabled =
                content.isNotBlank() &&
                    hasApiKey &&
                    connectionState == LlmConnectionState.READY,
                hasQuestion = quiz != null,
                onClick = {
                    onGenerateQuestion()
                }
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun QuizSourceCard(label: String, onClear: () -> Unit) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                MaterialTheme.colorScheme.primaryContainer
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Quiz,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        TextButton(
            onClick = onClear,
            modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White),
            colors =
            ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            ),
            contentPadding =
            PaddingValues(
                horizontal = 10.dp,
                vertical = 4.dp
            )
        ) {
            Text(
                "페이지 범위",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun QuizPageRangeControls(
    value: String,
    pageCount: Int,
    error: String?,
    onValueChanged: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TextField(
            value = value,
            onValueChange = onValueChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = {
                Text("페이지 범위", fontSize = 11.sp)
            },
            placeholder = {
                Text("예: 1-4, 9-11, 21-", fontSize = 12.sp)
            },
            isError = error != null,
            colors =
            TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
        Text(
            error ?: "쉼표와 하이픈으로 지정 · 전체 ${pageCount.coerceAtLeast(0)}쪽",
            fontSize = 11.sp,
            color =
            if (error == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            }
        )
    }
}

private fun parsePageRangeSpec(spec: String, maxPage: Int): Result<Set<Int>> {
    val normalized = spec.replace(" ", "")
    if (normalized.isBlank() ||
        normalized.any { it !in '0'..'9' && it != ',' && it != '-' }
    ) {
        return Result.failure(IllegalArgumentException(PAGE_RANGE_ERROR))
    }
    val upper = maxPage.coerceAtLeast(1)
    val pages = mutableSetOf<Int>()
    normalized.split(",").forEach { part ->
        if (part.isBlank()) {
            return Result.failure(
                IllegalArgumentException(PAGE_RANGE_ERROR)
            )
        }
        if (part.contains("-")) {
            val pieces = part.split("-")
            if (pieces.size != 2 || (pieces[0].isBlank() && pieces[1].isBlank())) {
                return Result.failure(IllegalArgumentException(PAGE_RANGE_ERROR))
            }
            val start =
                if (pieces[0].isBlank()) {
                    1
                } else {
                    pieces[0].toIntOrNull()
                        ?: return Result.failure(IllegalArgumentException(PAGE_RANGE_ERROR))
                }
            val end =
                if (pieces[1].isBlank()) {
                    upper
                } else {
                    pieces[1].toIntOrNull()
                        ?: return Result.failure(IllegalArgumentException(PAGE_RANGE_ERROR))
                }
            if (start < 1 || end < start) {
                return Result.failure(IllegalArgumentException(PAGE_RANGE_ERROR))
            }
            pages += start..end.coerceAtMost(upper)
        } else {
            val page =
                part.toIntOrNull()
                    ?: return Result.failure(IllegalArgumentException(PAGE_RANGE_ERROR))
            if (page !in 1..upper) {
                return Result.failure(IllegalArgumentException(PAGE_RANGE_ERROR))
            }
            pages += page
        }
    }
    return if (pages.isEmpty()) {
        Result.failure(IllegalArgumentException(PAGE_RANGE_ERROR))
    } else {
        Result.success(pages)
    }
}

private const val PAGE_RANGE_ERROR =
    "올바른 페이지 범위를 입력해주세요. 예: 1-4, 9-11, 21-"

@Composable
private fun QuizHeader(
    conceptName: String,
    conceptId: String,
    mastery: Float,
    pageIndex: Int,
    scopeMode: QuizScopeMode,
    onScopeModeSelected: (QuizScopeMode) -> Unit,
    hasSelectionSource: Boolean,
    currentPageHasContent: Boolean,
    selectedBloomLevel: Int,
    onBloomLevelSelected: (Int) -> Unit,
    quizLanguage: String,
    onQuizLanguageChange: (String) -> Unit,
    quizService: QuizService
) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Text(
            "Bloom Quiz",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "개념: $conceptName · 페이지 ${pageIndex + 1} · mastery ${masteryPct(mastery)}%",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(QuizScopeMode.entries) { mode ->
                val enabled =
                    when (mode) {
                        QuizScopeMode.SELECTION -> hasSelectionSource
                        QuizScopeMode.CURRENT_PAGE -> currentPageHasContent
                        else -> true
                    }
                QuizScopeChip(
                    label = mode.label,
                    selected = mode == scopeMode,
                    enabled = enabled,
                    onClick = {
                        onScopeModeSelected(mode)
                    }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(quizService.bloomLevels()) { bloom ->
                BloomChip(
                    level = bloom.level,
                    selected = bloom.level == selectedBloomLevel,
                    onClick = {
                        onBloomLevelSelected(bloom.level)
                    }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        QuizLanguageToggle(
            language = quizLanguage,
            onLanguageChange = onQuizLanguageChange
        )
    }
}

/** Switch that selects the quiz output language (Korean / English). */
@Composable
private fun QuizLanguageToggle(language: String, onLanguageChange: (String) -> Unit) {
    val isEnglish = language.equals("en", ignoreCase = true)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "퀴즈 언어",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "한국어",
            fontSize = 12.sp,
            fontWeight = if (!isEnglish) FontWeight.Bold else FontWeight.Normal,
            color =
            if (!isEnglish) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(Modifier.width(6.dp))
        Switch(
            checked = isEnglish,
            onCheckedChange = { checked ->
                onLanguageChange(if (checked) "en" else "ko")
            }
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "English",
            fontSize = 12.sp,
            fontWeight = if (isEnglish) FontWeight.Bold else FontWeight.Normal,
            color =
            if (isEnglish) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun BloomChip(level: Int, selected: Boolean, onClick: () -> Unit) {
    Text(
        "L$level",
        modifier =
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        color =
        if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun QuizScopeChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier =
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.primaryContainer
                    enabled -> MaterialTheme.colorScheme.surface
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        color =
        when {
            selected -> MaterialTheme.colorScheme.onPrimaryContainer
            enabled -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.outline
        },
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun QuizQuestionCard(
    quiz: GeneratedQuizQuestion,
    mastery: Float,
    conceptMastery: Pair<Float, Float>?,
    selectedChoice: String?,
    selectedChoices: Set<String>,
    answered: Boolean,
    onAnswerChanged: (Set<String>) -> Unit,
    onSubmit: (Set<String>) -> Unit
) {
    val multiple = quiz.mcqType == "multiple_select"
    // Document-level average as of when this question was presented, used only as
    // a fallback when the per-concept (before, after) pair is not yet available.
    val masteryBefore = remember(quiz) { mastery }
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Text(
            "Level ${quiz.bloomLevel} · ${quiz.targetConcept}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            quiz.question,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 21.sp
        )
        Spacer(Modifier.height(12.dp))
        quiz.choices.toSortedMap().forEach { (key, value) ->
            val selected =
                if (multiple) {
                    key in selectedChoices
                } else {
                    selectedChoice == key
                }
            QuizChoiceRow(
                keyLabel = key,
                text = value,
                selected = selected,
                correct = answered && key in quiz.answerKeys,
                wrong =
                answered &&
                    selected &&
                    key !in quiz.answerKeys,
                enabled = !answered,
                onClick = {
                    if (multiple) {
                        onAnswerChanged(
                            if (key in selectedChoices) {
                                selectedChoices - key
                            } else {
                                selectedChoices + key
                            }
                        )
                    } else {
                        // Single-select: pick one, but don't submit until the
                        // learner presses the "정답 확인" button.
                        onAnswerChanged(setOf(key))
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
        }
        if (!answered) {
            Spacer(Modifier.height(4.dp))
            Button(
                enabled = selectedChoices.isNotEmpty(),
                onClick = {
                    onSubmit(selectedChoices)
                },
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "정답 확인",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (answered) {
            QuizExplanation(
                correct = selectedChoices == quiz.answerKeys.toSet(),
                answer = quiz.answer,
                explanation = quiz.explanation,
                selectedChoice = selectedChoice,
                selectedChoiceExplanation =
                selectedChoice?.let {
                    quiz.choiceExplanations[it]
                },
                sourceSentence = quiz.sourceSentence
            )
            Spacer(Modifier.height(12.dp))
            // Prefer the per-concept (before, after) pair from the TAP engine;
            // fall back to the document-level average if it is not ready yet.
            val (gaugeBefore, gaugeAfter) =
                conceptMastery ?: (masteryBefore to mastery)
            MasteryGauge(
                mastery = gaugeAfter,
                before = gaugeBefore,
                conceptName = quiz.targetConcept
            )
        }
    }
}

/** Mastery fraction (0..1) formatted as a percentage with one decimal. */
private fun masteryPct(fraction: Float): String = String.format(Locale.US, "%.1f", fraction * 100f)

/**
 * Mastery gauge shown under a graded quiz. Labels the concept (KC), shows the
 * post-answer mastery with the change since before the answer beside it (green
 * ▲ up / red ▼ down), plus a bar of the current mastery.
 */
@Composable
private fun MasteryGauge(mastery: Float, before: Float, conceptName: String) {
    val target = mastery.coerceIn(0f, 1f)
    val from = before.coerceIn(0f, 1f)
    val deltaPoints = (target - from) * 100f
    val accent =
        when {
            deltaPoints > 0.05f -> Color(0xFF10B981)
            deltaPoints < -0.05f -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val deltaLabel =
        when {
            deltaPoints > 0.05f -> "▲${masteryPct(target - from)}%"
            deltaPoints < -0.05f -> "▼${masteryPct(from - target)}%"
            else -> "±0.0%"
        }
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 600),
        label = "masteryGauge"
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "지식 상태: $conceptName",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Changed (post-answer) mastery, then the delta beside it.
                Text(
                    "${masteryPct(target)}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    deltaLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { animated },
            modifier =
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = accent,
            trackColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun QuizChoiceRow(
    keyLabel: String,
    text: String,
    selected: Boolean,
    correct: Boolean,
    wrong: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bg =
        when {
            correct -> Color(0xFFE7F8EF)
            wrong -> Color(0xFFFFEDEA)
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
        }
    val fg =
        when {
            correct -> Color(0xFF047857)
            wrong -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
        }
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier =
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                keyLabel,
                color = fg,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            modifier = Modifier.weight(1f),
            color = fg,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        if (correct || wrong) {
            Icon(
                if (correct) {
                    Icons.Default.CheckCircle
                } else {
                    Icons.Default.Cancel
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = fg
            )
        }
    }
}

@Composable
private fun QuizExplanation(
    correct: Boolean,
    answer: String,
    explanation: String,
    selectedChoice: String?,
    selectedChoiceExplanation: String?,
    sourceSentence: String
) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp)
    ) {
        Text(
            if (correct) {
                "정답입니다"
            } else {
                "정답은 $answer 입니다"
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color =
            if (correct) {
                Color(0xFF047857)
            } else {
                MaterialTheme.colorScheme.error
            }
        )
        Spacer(Modifier.height(6.dp))
        if (!correct &&
            !selectedChoice.isNullOrBlank() &&
            !selectedChoiceExplanation.isNullOrBlank()
        ) {
            Text(
                "선택한 $selectedChoice 보기의 오답 이유",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(3.dp))
            Text(
                selectedChoiceExplanation,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "정답 해설",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(3.dp))
        }
        Text(
            explanation,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (sourceSentence.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "근거 문장",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                sourceSentence,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuizActionButton(
    loading: Boolean,
    enabled: Boolean,
    hasQuestion: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.width(8.dp))
        } else if (hasQuestion) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint =
                if (enabled) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            if (hasQuestion) "새 문제 생성" else "문제 생성",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color =
            if (enabled) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun QuizLoadingCard(progress: QuizProgress?) {
    val fraction = (progress?.fraction ?: 0f).coerceIn(0f, 1f)
    val percent = (fraction * 100).toInt()
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        label = "quizProgress"
    )
    val phaseLabel =
        when (progress?.phase) {
            QuizPhase.REQUESTING -> "요청 보내는 중…"
            QuizPhase.GENERATING -> "퀴즈 생성 중…"
            QuizPhase.VALIDATING -> "검증 중…"
            QuizPhase.DONE -> "완료"
            null -> "퀴즈 생성 준비 중…"
        }
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                phaseLabel,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "$percent%",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (progress == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { animatedFraction },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (progress != null && progress.attempt > 1) {
            Text(
                "재시도 ${progress.attempt}/${progress.totalAttempts}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuizMessage(title: String, body: String, isError: Boolean = false) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Text(
            title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color =
            if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun extractQuizConcept(content: String): String {
    if (content.isBlank()) {
        return EngineeringMechanicsConceptCatalog
            .concepts
            .first()
            .name
    }
    return EngineeringMechanicsConceptCatalog
        .bestMatch(content)
        .name
}

private fun stableConceptId(documentId: String, conceptName: String): String {
    EngineeringMechanicsConceptCatalog.concepts
        .find { it.name == conceptName }
        ?.let { return it.id }
    val slug =
        conceptName.lowercase()
            .replace(Regex("[^a-z0-9가-힣]+"), "_")
            .trim('_')
            .take(40)
            .ifBlank { "concept" }
    return "${documentId.take(8)}_$slug"
}

private fun mapQuizError(error: Exception): String {
    val msg = error.message.orEmpty()
    return when {
        msg.contains("좋은 퀴즈를 만들지", true) ->
            msg
        msg.contains("API 키", true) ->
            msg
        msg.contains("Unable to resolve host", true) ||
            msg.contains("No address associated", true) ->
            "LLM 서버 주소를 찾을 수 없습니다. 네트워크/DNS 상태를 확인해주세요."
        msg.contains("connection abort", true) ||
            msg.contains("Connection reset", true) ||
            msg.contains("Software caused connection", true) ||
            msg.contains("SocketException", true) ||
            msg.contains("SocketTimeout", true) ||
            msg.contains("failed to connect", true) ->
            "네트워크 연결이 끊겼어요. 연결 상태를 확인하고 다시 시도해주세요."
        msg.contains("JSON", true) ||
            msg.contains("파싱", true) ->
            "LLM 응답을 퀴즈 JSON으로 해석하지 못했습니다. 새 문제 생성을 다시 눌러주세요."
        msg.contains("핵심 개념", true) ||
            msg.contains("KC", true) ->
            "선택한 범위에서 KC를 테스트할 수 있는 근거 문단을 찾지 못했습니다. 다른 페이지나 영역을 선택해 주세요."
        msg.contains("API error 429", true) ->
            "요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요."
        msg.contains("API error 400", true) ->
            "LLM 요청 형식이나 선택한 모델이 맞지 않습니다. OpenRouter 모델을 다시 불러오거나 provider를 확인해주세요."
        msg.contains("Timed out", true) ||
            msg.contains("timeout", true) ->
            "퀴즈 생성 시간이 초과되었습니다. 문서 전체 대신 현재 페이지나 선택 영역으로 다시 시도해주세요."
        else ->
            "퀴즈를 생성할 수 없습니다: $msg"
    }
}

@Composable
private fun LlmConnectionStatusBanner(
    connectionState: LlmConnectionState,
    errorMessage: String?,
    hasQueuedRequest: Boolean,
    onRetryConnection: () -> Unit
) {
    Surface(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color =
        MaterialTheme.colorScheme
            .primaryContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier =
            Modifier.padding(
                horizontal = 12.dp,
                vertical = 10.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (connectionState ==
                LlmConnectionState.CONNECTING
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    Icons.Default.Key,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text =
                    when (connectionState) {
                        LlmConnectionState.CONNECTING ->
                            if (hasQueuedRequest) {
                                "LLM 서버와 연결 중입니다. 요청을 보관했고 연결되면 자동으로 전송합니다."
                            } else {
                                "LLM 서버와 연결을 준비하는 중입니다."
                            }
                        LlmConnectionState.FAILED ->
                            if (hasQueuedRequest) {
                                "LLM 서버 연결에 실패했습니다. 요청을 보관했고 재연결되면 자동으로 전송합니다."
                            } else {
                                "LLM 서버 연결에 실패했습니다."
                            }
                        LlmConnectionState.READY ->
                            "LLM 서버 연결이 준비되었습니다."
                    },
                    fontSize = 12.sp,
                    color =
                    MaterialTheme.colorScheme
                        .onPrimaryContainer,
                    lineHeight = 16.sp
                )
                if (
                    connectionState ==
                    LlmConnectionState.FAILED &&
                    !errorMessage.isNullOrBlank()
                ) {
                    Text(
                        text = errorMessage,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        lineHeight = 15.sp
                    )
                }
            }
            if (connectionState == LlmConnectionState.FAILED) {
                Text(
                    "다시 연결",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onRetryConnection() }
                        .padding(
                            horizontal = 8.dp,
                            vertical = 5.dp
                        )
                )
            }
        }
    }
}

private suspend fun streamAssistantResponse(
    messages: MutableList<ChatMessage>,
    llmService: LlmService,
    conversationDataSource: ConversationLocalDataSource,
    convId: String,
    images: List<ByteArray>,
    documentContent: String?,
    pageIndex: Int,
    userPrompt: String,
    extraContext: String,
    onError: (String?) -> Unit,
    onLoading: (Boolean) -> Unit
) {
    onLoading(true)
    var assistantIndex: Int? = null

    fun ensureAssistantMessage(initialContent: String = ""): Int {
        val existing = assistantIndex
        if (existing != null && existing in messages.indices) {
            return existing
        }
        messages.add(
            ChatMessage(
                role = ChatMessage.Role.ASSISTANT,
                content = initialContent
            )
        )
        assistantIndex = messages.lastIndex
        return messages.lastIndex
    }

    fun updateAssistant(content: String) {
        val idx = ensureAssistantMessage(content)
        if (idx in messages.indices) {
            messages[idx] =
                messages[idx].copy(
                    content = content
                )
        }
    }

    try {
        val sb = StringBuilder()
        var thinking = false
        llmService.stream(
            messages = messages.toList(),
            systemPrompt =
            buildSystemPrompt(
                documentContent,
                pageIndex,
                userPrompt,
                extraContext
            ),
            images = images
        ).collect { token ->
            when (token) {
                LlmClient.THINKING_TOKEN -> {
                    thinking = true
                    updateAssistant("생각 중...")
                }
                LlmClient.THINKING_DONE_TOKEN -> {
                    thinking = false
                    sb.clear()
                }
                ClaudeClient.GENERATING_TOKEN,
                LlmClient.GENERATING_TOKEN
                -> {
                    updateAssistant("응답 생성 중...")
                }
                LlmClient.RETRY_TOKEN -> {
                    updateAssistant("서버 과부하, 재시도 중...")
                }
                else -> {
                    if (!thinking) {
                        sb.append(token)
                        updateAssistant(sb.toString())
                    }
                }
            }
        }
        val idx = assistantIndex
        if (idx != null && idx in messages.indices) {
            if (
                messages[idx].content.isBlank() ||
                messages[idx].content == "응답 생성 중..." ||
                messages[idx].content == "생각 중..."
            ) {
                messages.removeAt(idx)
                onError(
                    "이 모델에서 응답을 받지 못했습니다. " +
                        "다른 모델을 선택해주세요."
                )
            } else {
                conversationDataSource.appendMessage(
                    convId,
                    messages[idx]
                )
            }
        }
    } catch (_: kotlin.coroutines.cancellation.CancellationException) {
        val idx = assistantIndex
        if (idx != null && idx in messages.indices) {
            messages.removeAt(idx)
        }
    } catch (e: Exception) {
        val msg = e.message ?: ""
        onError(
            when {
                msg.contains("API key not configured") ->
                    "API 키가 설정되지 않았습니다"
                msg.contains("API error 401") ||
                    msg.contains("API error 403") ->
                    "API 키가 유효하지 않습니다"
                msg.contains("API error 429") ->
                    "요청 한도 초과: $msg"
                msg.contains("API error 400") ->
                    "요청 형식 또는 선택한 모델 문제입니다: $msg"
                msg.contains("Unable to resolve host", true) ||
                    msg.contains("No address associated", true) ->
                    "LLM 서버 주소를 찾을 수 없습니다. " +
                        "네트워크/DNS/VPN 상태를 확인한 뒤 " +
                        "다시 연결을 눌러주세요."
                msg.contains("API error") -> msg
                msg.contains("cancel", true) ||
                    msg.contains("closed") ->
                    null
                else -> "AI 응답을 받을 수 없습니다: $msg"
            }
        )
        val idx = assistantIndex
        if (idx != null && idx in messages.indices) {
            messages.removeAt(idx)
        }
    } finally {
        onLoading(false)
    }
}

@Composable
private fun SidebarTopBar(
    currentProvider: String,
    onProviderSelected: (String) -> Unit,
    currentModel: String,
    availableModels: List<String>,
    modelsLoading: Boolean,
    onModelMenuOpened: () -> Unit,
    onModelSelected: (String) -> Unit,
    onCollapse: () -> Unit,
    onNewConversation: () -> Unit,
    onToggleHistory: () -> Unit,
    showHistory: Boolean
) {
    var showModelMenu by remember {
        mutableStateOf(false)
    }
    var showProviderMenu by remember {
        mutableStateOf(false)
    }
    val providerLabel =
        when (currentProvider) {
            LlmProvider.OPENAI.name -> "ChatGPT"
            LlmProvider.CLAUDE.name -> "Claude"
            LlmProvider.OPENROUTER.name -> "OpenRouter"
            else -> "Gemini"
        }
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Provider toggle
        Box {
            Text(
                text = providerLabel,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
                modifier =
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        MaterialTheme.colorScheme
                            .surfaceVariant
                    )
                    .clickable {
                        showProviderMenu = true
                    }
                    .padding(
                        horizontal = 8.dp,
                        vertical = 4.dp
                    )
            )
            DropdownMenu(
                expanded = showProviderMenu,
                onDismissRequest = {
                    showProviderMenu = false
                }
            ) {
                DropdownMenuItem(
                    text = { Text("OpenRouter") },
                    onClick = {
                        onProviderSelected(
                            LlmProvider.OPENROUTER.name
                        )
                        showProviderMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Gemini") },
                    onClick = {
                        onProviderSelected(
                            LlmProvider.GEMINI.name
                        )
                        showProviderMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("ChatGPT") },
                    onClick = {
                        onProviderSelected(
                            LlmProvider.OPENAI.name
                        )
                        showProviderMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Claude") },
                    onClick = {
                        onProviderSelected(
                            LlmProvider.CLAUDE.name
                        )
                        showProviderMenu = false
                    }
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        // Model selector
        Box(modifier = Modifier.weight(1f)) {
            Text(
                text = currentModel,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow =
                androidx.compose.ui.text
                    .style.TextOverflow.Ellipsis,
                color =
                MaterialTheme.colorScheme
                    .primary,
                modifier =
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        onModelMenuOpened()
                        showModelMenu = true
                    }
                    .padding(
                        horizontal = 8.dp,
                        vertical = 4.dp
                    )
            )
            DropdownMenu(
                expanded = showModelMenu,
                onDismissRequest = {
                    showModelMenu = false
                }
            ) {
                if (modelsLoading) {
                    Box(
                        modifier =
                        Modifier
                            .padding(16.dp),
                        contentAlignment =
                        Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier =
                            Modifier
                                .size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                } else if (
                    availableModels.isEmpty()
                ) {
                    Text(
                        "모델 목록을 불러올 수 없습니다",
                        modifier =
                        Modifier
                            .padding(16.dp),
                        fontSize = 13.sp,
                        color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                    )
                } else {
                    availableModels.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    model,
                                    fontSize = 13.sp,
                                    fontWeight =
                                    if (model ==
                                        currentModel
                                    ) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight
                                            .Normal
                                    },
                                    color =
                                    if (model ==
                                        currentModel
                                    ) {
                                        MaterialTheme
                                            .colorScheme
                                            .primary
                                    } else {
                                        MaterialTheme
                                            .colorScheme
                                            .onSurface
                                    }
                                )
                            },
                            onClick = {
                                onModelSelected(model)
                                showModelMenu = false
                            }
                        )
                    }
                }
            }
        }
        IconButton(
            onClick = onToggleHistory,
            modifier =
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (showHistory) {
                        MaterialTheme.colorScheme
                            .primaryContainer
                    } else {
                        Color.Transparent
                    }
                )
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = "대화 기록",
                tint =
                if (showHistory) {
                    MaterialTheme.colorScheme
                        .onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
                }
            )
        }
        IconButton(
            onClick = onNewConversation,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "새 대화",
                tint =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
            )
        }
        IconButton(
            onClick = onCollapse,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "닫기",
                tint =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, isStreaming: Boolean = false) {
    val isUser =
        message.role == ChatMessage.Role.USER
    val bgColor =
        if (isUser) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val textColor =
        if (isUser) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    val alignment =
        if (isUser) {
            Alignment.End
        } else {
            Alignment.Start
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier =
            Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .padding(
                    horizontal = 12.dp,
                    vertical = 8.dp
                )
        ) {
            // User text is plain; assistant output is Markdown + LaTeX. While a
            // response is still streaming, render plain text to avoid re-parsing
            // (and re-rendering LaTeX) on every token — switch to rich rendering
            // once the message is complete.
            if (isUser || isStreaming) {
                Text(
                    text = message.content,
                    color = textColor,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            } else {
                MarkdownLatexText(
                    markdown = message.content,
                    color = textColor,
                    fontSizeSp = 14f
                )
            }
        }
    }
}

/**
 * Renders Markdown (bold/italic/headings/lists/code/tables/links) and LaTeX math
 * via Markwon + jlatexmath, so the LLM's `**bold**`, `$x^2$`, `$$...$$`, etc. are
 * shown as formatted output rather than raw syntax.
 */
@Composable
private fun MarkdownLatexText(markdown: String, color: Color, fontSizeSp: Float) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val argb = color.toArgb()
    val markwon =
        remember(context, fontSizeSp) {
            val textSizePx = with(density) { fontSizeSp.sp.toPx() }
            Markwon.builder(context)
                // Required by JLatexMathPlugin when inline LaTeX is enabled.
                .usePlugin(MarkwonInlineParserPlugin.create())
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(context))
                .usePlugin(LinkifyPlugin.create())
                .usePlugin(
                    JLatexMathPlugin.create(textSizePx) { builder ->
                        builder.inlinesEnabled(true)
                    }
                )
                .build()
        }
    val rendered = remember(markdown) { normalizeLatexDelimiters(markdown) }
    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(argb)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
                setTextIsSelectable(true)
                setLineSpacing(0f, 1.25f)
            }
        },
        update = { tv ->
            tv.setTextColor(argb)
            markwon.setMarkdown(tv, rendered)
        }
    )
}

/**
 * Maps the LaTeX delimiters LLMs commonly emit (`\(...\)`, `\[...\]`) to the
 * `$...$` / `$$...$$` form Markwon's jlatexmath plugin recognizes.
 */
private fun normalizeLatexDelimiters(text: String): String = text
    .replace("\\[", "$$")
    .replace("\\]", "$$")
    .replace("\\(", "$")
    .replace("\\)", "$")

@Composable
private fun SidebarInput(
    value: String,
    onValueChange: (String) -> Unit,
    isLoading: Boolean,
    pendingImages: List<ByteArray>,
    onAddImage: (ByteArray) -> Unit,
    onRemoveImage: (Int) -> Unit,
    onSend: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager =
        context.getSystemService(
            android.content.Context.CLIPBOARD_SERVICE
        ) as android.content.ClipboardManager

    val imagePicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            try {
                context.contentResolver
                    .openInputStream(uri)?.use {
                        onAddImage(it.readBytes())
                    }
            } catch (_: Throwable) {
            }
        }

    val canSend =
        value.isNotBlank() ||
            pendingImages.isNotEmpty()

    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        if (pendingImages.isNotEmpty()) {
            LazyRow(
                horizontalArrangement =
                Arrangement.spacedBy(6.dp),
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                items(pendingImages.size) { idx ->
                    ImageThumbnail(
                        bytes = pendingImages[idx],
                        onRemove = {
                            onRemoveImage(idx)
                        }
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = {
                    imagePicker.launch("image/*")
                },
                enabled = !isLoading,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = "이미지 첨부",
                    tint =
                    if (!isLoading) {
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme
                            .outline
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = {
                    try {
                        val clip =
                            clipboardManager
                                .primaryClip
                        val item =
                            clip?.getItemAt(0)
                        val uri = item?.uri
                        if (uri != null) {
                            context
                                .contentResolver
                                .openInputStream(uri)
                                ?.use {
                                    onAddImage(
                                        it.readBytes()
                                    )
                                }
                        }
                    } catch (_: Throwable) {
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.ContentPaste,
                    contentDescription =
                    "클립보드에서 붙여넣기",
                    tint =
                    if (!isLoading) {
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme
                            .outline
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "메시지를 입력하세요...",
                        fontSize = 14.sp
                    )
                },
                maxLines = 5,
                colors =
                TextFieldDefaults.colors(
                    focusedContainerColor =
                    MaterialTheme.colorScheme
                        .surfaceVariant,
                    unfocusedContainerColor =
                    MaterialTheme.colorScheme
                        .surfaceVariant,
                    focusedIndicatorColor =
                    Color.Transparent,
                    unfocusedIndicatorColor =
                    Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onSend,
                enabled = canSend
            ) {
                Icon(
                    imageVector =
                    Icons.AutoMirrored
                        .Filled.Send,
                    contentDescription = "전송",
                    tint =
                    if (canSend) {
                        MaterialTheme.colorScheme
                            .primary
                    } else {
                        MaterialTheme.colorScheme
                            .outline
                    }
                )
            }
        }
    }
}

@Composable
private fun ImageThumbnail(bytes: ByteArray, onRemove: () -> Unit) {
    val bitmap =
        remember(bytes) {
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size
            )
        }
    Box(modifier = Modifier.size(56.dp)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "첨부 이미지",
                modifier =
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier =
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        MaterialTheme.colorScheme
                            .surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint =
                    MaterialTheme.colorScheme
                        .outline
                )
            }
        }
        Box(
            modifier =
            Modifier
                .align(Alignment.TopEnd)
                .size(18.dp)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.error
                )
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "제거",
                modifier = Modifier.size(12.dp),
                tint =
                MaterialTheme.colorScheme
                    .onError
            )
        }
    }
}

@Composable
private fun UnifiedHistoryPanel(
    pane: HistoryPane,
    onPaneChanged: (HistoryPane) -> Unit,
    conversations: List<ConversationSummary>,
    currentConversationId: String?,
    quizHistory: List<QuizResponseRecord>,
    onSelectConversation: (ConversationSummary) -> Unit,
    onDeleteConversation: (ConversationSummary) -> Unit,
    onDeleteQuizRecord: (QuizResponseRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (pane == HistoryPane.CHAT) {
            ConversationHistoryPanel(
                conversations = conversations,
                currentId = currentConversationId,
                onSelect = onSelectConversation,
                onDelete = onDeleteConversation
            )
        } else {
            QuizHistoryPanel(
                records = quizHistory,
                onDelete = onDeleteQuizRecord
            )
        }
    }
}

@Composable
private fun QuizHistoryPanel(
    records: List<QuizResponseRecord>,
    onDelete: (QuizResponseRecord) -> Unit
) {
    var pendingDelete by remember {
        mutableStateOf<QuizResponseRecord?>(null)
    }
    pendingDelete?.let { record ->
        DeleteConfirmDialog(
            title = "퀴즈 기록 삭제",
            message = "이 퀴즈 풀이 기록을 삭제할까요?",
            onConfirm = {
                onDelete(record)
                pendingDelete = null
            },
            onDismiss = {
                pendingDelete = null
            }
        )
    }
    if (records.isEmpty()) {
        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "이 문서에서 푼 퀴즈 기록이 없습니다",
                fontSize = 14.sp,
                color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
            )
        }
        return
    }
    val dayFormat =
        remember {
            SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
        }
    val timeFormat =
        remember {
            SimpleDateFormat("HH:mm", Locale.getDefault())
        }
    val grouped =
        remember(records) {
            records.groupBy {
                dayFormat.format(Date(it.answeredAt))
            }.toSortedMap(compareByDescending { it })
        }
    var selectedDay by remember(records) {
        mutableStateOf(grouped.keys.first())
    }
    val selectedRecords =
        grouped[selectedDay].orEmpty()
            .sortedByDescending { it.answeredAt }

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            items(grouped.keys.toList()) { day ->
                Text(
                    day,
                    modifier =
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (day == selectedDay) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .clickable { selectedDay = day }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    color =
                    if (day == selectedDay) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(selectedRecords, key = { it.id }) { record ->
                SwipeRevealDeleteContainer(
                    onDelete = {
                        pendingDelete = record
                    }
                ) {
                    QuizHistoryCard(
                        record = record,
                        timeText =
                        timeFormat.format(
                            Date(record.answeredAt)
                        )
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun QuizHistoryCard(record: QuizResponseRecord, timeText: String) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                timeText,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "L${record.bloomLevel}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (record.isCorrect) "정답" else "오답",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color =
                if (record.isCorrect) {
                    Color(0xFF047857)
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            record.question.ifBlank { "저장된 질문 내용이 없습니다" },
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        record.choices.toSortedMap().forEach { (key, value) ->
            val isSelected = key == record.selectedAnswer
            val isAnswer = key == record.correctAnswer
            Text(
                "$key. $value",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color =
                when {
                    isAnswer -> Color(0xFF047857)
                    isSelected && !record.isCorrect ->
                        MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight =
                if (isSelected || isAnswer) {
                    FontWeight.Bold
                } else {
                    FontWeight.Normal
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (record.isCorrect) {
                "선택한 ${record.selectedAnswer}가 정답입니다."
            } else {
                "선택한 답: ${record.selectedAnswer} · 정답: ${record.correctAnswer}"
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color =
            if (record.isCorrect) {
                Color(0xFF047857)
            } else {
                MaterialTheme.colorScheme.error
            }
        )
        if (record.explanation.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            val selectedExplanation =
                record.choiceExplanations[record.selectedAnswer]
            if (!record.isCorrect &&
                !selectedExplanation.isNullOrBlank()
            ) {
                Text(
                    "선택한 ${record.selectedAnswer} 보기의 오답 이유",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    selectedExplanation,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "정답 해설",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(3.dp))
            }
            Text(
                record.explanation,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (record.sourceSentence.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "근거: ${record.sourceSentence}",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SwipeRevealDeleteContainer(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val revealWidth = 86.dp
    val revealWidthPx = with(density) { revealWidth.toPx() }
    var offsetPx by remember {
        mutableStateOf(0f)
    }

    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier =
                Modifier
                    .width(revealWidth)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.error)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "삭제",
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "삭제",
                        color = MaterialTheme.colorScheme.onError,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .offset {
                    IntOffset(offsetPx.roundToInt(), 0)
                }
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            offsetPx =
                                if (
                                    offsetPx <= -revealWidthPx / 2f
                                ) {
                                    -revealWidthPx
                                } else {
                                    0f
                                }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            offsetPx =
                                (
                                    offsetPx + dragAmount
                                    ).coerceIn(
                                    -revealWidthPx,
                                    0f
                                )
                        }
                    )
                }
        ) {
            content()
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                title,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(message)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    "삭제",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun ConversationHistoryPanel(
    conversations: List<ConversationSummary>,
    currentId: String?,
    onSelect: (ConversationSummary) -> Unit,
    onDelete: (ConversationSummary) -> Unit
) {
    var pendingDelete by remember {
        mutableStateOf<ConversationSummary?>(null)
    }
    pendingDelete?.let { conversation ->
        DeleteConfirmDialog(
            title = "채팅 기록 삭제",
            message = "이 채팅 기록을 삭제할까요?",
            onConfirm = {
                onDelete(conversation)
                pendingDelete = null
            },
            onDismiss = {
                pendingDelete = null
            }
        )
    }
    if (conversations.isEmpty()) {
        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "저장된 대화가 없습니다",
                fontSize = 14.sp,
                color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalArrangement =
            Arrangement.spacedBy(2.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            items(
                conversations,
                key = { it.id }
            ) { conv ->
                val isSelected =
                    conv.id == currentId
                SwipeRevealDeleteContainer(
                    onDelete = {
                        pendingDelete = conv
                    }
                ) {
                    Row(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(8.dp)
                            )
                            .background(
                                if (isSelected) {
                                    MaterialTheme
                                        .colorScheme
                                        .primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                            .clickable {
                                onSelect(conv)
                            }
                            .padding(
                                horizontal = 12.dp,
                                vertical = 10.dp
                            ),
                        verticalAlignment =
                        Alignment.CenterVertically
                    ) {
                        Column(
                            modifier =
                            Modifier
                                .weight(1f)
                        ) {
                            Text(
                                conv.title,
                                fontSize = 13.sp,
                                fontWeight =
                                FontWeight.Medium,
                                maxLines = 1,
                                color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurface
                            )
                            val dateStr =
                                java.text.SimpleDateFormat(
                                    "MM/dd HH:mm",
                                    java.util.Locale
                                        .getDefault()
                                ).format(
                                    java.util.Date(
                                        conv.updated
                                    )
                                )
                            Text(
                                dateStr,
                                fontSize = 11.sp,
                                color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }
    }
}
