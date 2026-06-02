package com.maestro.app.data.local

import kotlin.math.abs
import kotlin.math.max
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TextLayerContentBuilder {
    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }

    fun canUseTextLayer(index: PdfTextIndex?): Boolean {
        if (index == null) return false
        val totalWords = index.pages.sumOf { it.words.size }
        if (totalWords < MIN_TOTAL_WORDS) return false
        val pagesWithText = index.pages.count { it.words.size >= MIN_WORDS_PER_TEXT_PAGE }
        val requiredPages = max(1, (index.pages.size * MIN_TEXT_PAGE_RATIO).toInt())
        return pagesWithText >= requiredPages
    }

    fun build(documentId: String, index: PdfTextIndex): TextLayerContent {
        val pages =
            index.pages.map { page ->
                val lines = wordsToLines(page.words)
                // Group consecutive lines into paragraph blocks so the markdown
                // has real paragraphs (not one blank-line-separated block per
                // line). Downstream consumers — quiz chunking and LLM context
                // injection — rely on paragraph structure.
                val paragraphs = groupLinesIntoParagraphs(lines)
                val blocks =
                    paragraphs.mapIndexed { blockIndex, paragraphLines ->
                        TextLayerBlock(
                            id = "b$blockIndex",
                            type = "text",
                            bbox = paragraphLines.linesBbox(),
                            text =
                            paragraphLines.joinToString(" ") { it.text }
                                .replace(Regex("\\s+"), " ")
                                .trim(),
                            lines = paragraphLines
                        )
                    }
                TextLayerPage(
                    page_idx = page.pageIndex,
                    page_size = listOf(page.width, page.height),
                    para_blocks = blocks
                )
            }
        val root =
            TextLayerRoot(
                documentId = documentId,
                generatedAt = System.currentTimeMillis(),
                sourceName = index.sourceName,
                sourceLength = index.sourceLength,
                sourceModifiedAt = index.sourceModifiedAt,
                pdf_info = pages
            )
        return TextLayerContent(
            markdown = buildMarkdown(pages),
            json = json.encodeToString(root)
        )
    }

    private fun wordsToLines(words: List<PdfTextIndexWord>): List<TextLayerLine> {
        val sorted =
            words.sortedWith(
                compareBy<PdfTextIndexWord> { it.top }
                    .thenBy { it.left }
            )
        val lines = mutableListOf<MutableList<PdfTextIndexWord>>()
        sorted.forEach { word ->
            val line = lines.lastOrNull()
            if (line == null || startsNewLine(line, word)) {
                lines += mutableListOf(word)
            } else {
                line += word
            }
        }
        return lines.mapNotNull { lineWords ->
            val ordered = lineWords.sortedBy { it.left }
            val text =
                ordered.joinToString(" ") { it.text }
                    .replace(Regex("\\s+"), " ")
                    .trim()
            if (text.isBlank()) return@mapNotNull null
            TextLayerLine(
                bbox = ordered.bbox(),
                text = text,
                spans =
                ordered.map { word ->
                    TextLayerSpan(
                        bbox = word.bbox(),
                        content = word.text
                    )
                }
            )
        }
    }

    /**
     * Groups vertically adjacent lines into paragraph blocks. A new paragraph
     * starts when the vertical gap to the previous line is large (blank space)
     * or the next line opens a bullet/number marker.
     */
    private fun groupLinesIntoParagraphs(lines: List<TextLayerLine>): List<List<TextLayerLine>> {
        if (lines.isEmpty()) return emptyList()
        val paragraphs = mutableListOf<MutableList<TextLayerLine>>()
        lines.forEach { line ->
            val current = paragraphs.lastOrNull()
            if (current == null || startsNewParagraph(current.last(), line)) {
                paragraphs += mutableListOf(line)
            } else {
                current += line
            }
        }
        return paragraphs
    }

    private fun startsNewParagraph(previous: TextLayerLine, next: TextLayerLine): Boolean {
        val prevTop = previous.bbox.getOrElse(1) { 0f }
        val prevBottom = previous.bbox.getOrElse(3) { 0f }
        val nextTop = next.bbox.getOrElse(1) { 0f }
        val lineHeight = (prevBottom - prevTop).coerceAtLeast(1f)
        val gap = nextTop - prevBottom
        if (gap > lineHeight * PARAGRAPH_GAP_RATIO) return true
        val head = next.text.trimStart()
        return head.startsWith("•") ||
            head.startsWith("·") ||
            Regex("^[-*]\\s").containsMatchIn(head) ||
            Regex("^\\d+[.)]\\s").containsMatchIn(head)
    }

    private fun List<TextLayerLine>.linesBbox(): List<Float> = listOf(
        minOf { it.bbox.getOrElse(0) { 0f } },
        minOf { it.bbox.getOrElse(1) { 0f } },
        maxOf { it.bbox.getOrElse(2) { 0f } },
        maxOf { it.bbox.getOrElse(3) { 0f } }
    )

    private fun startsNewLine(
        currentLine: List<PdfTextIndexWord>,
        next: PdfTextIndexWord
    ): Boolean {
        val currentTop = currentLine.minOf { it.top }
        val currentBottom = currentLine.maxOf { it.bottom }
        val currentHeight = (currentBottom - currentTop).coerceAtLeast(1f)
        val nextHeight = (next.bottom - next.top).coerceAtLeast(1f)
        val tolerance = max(currentHeight, nextHeight) * LINE_Y_TOLERANCE
        return abs(next.top - currentTop) > tolerance
    }

    private fun buildMarkdown(pages: List<TextLayerPage>): String {
        return buildString {
            pages.forEach { page ->
                append("# Page ")
                append(page.page_idx + 1)
                append("\n\n")
                page.para_blocks.forEach { block ->
                    val text = block.text.trim()
                    if (text.isNotBlank()) {
                        append(text)
                        append("\n\n")
                    }
                }
            }
        }.trimEnd() + "\n"
    }

    private fun List<PdfTextIndexWord>.bbox(): List<Float> = listOf(
        minOf { it.left },
        minOf { it.top },
        maxOf { it.right },
        maxOf { it.bottom }
    )

    private fun PdfTextIndexWord.bbox(): List<Float> = listOf(left, top, right, bottom)

    private companion object {
        private const val MIN_TOTAL_WORDS = 40
        private const val MIN_WORDS_PER_TEXT_PAGE = 3
        private const val MIN_TEXT_PAGE_RATIO = 0.15f
        private const val LINE_Y_TOLERANCE = 0.72f

        // A vertical gap larger than this fraction of the line height starts a
        // new paragraph block.
        private const val PARAGRAPH_GAP_RATIO = 0.8f
    }
}

data class TextLayerContent(
    val markdown: String,
    val json: String
)

@Serializable
private data class TextLayerRoot(
    @SerialName("schema_version")
    val schemaVersion: Int = 1,
    val source: String = "pdf_text_layer",
    val documentId: String,
    val generatedAt: Long,
    val sourceName: String,
    val sourceLength: Long,
    val sourceModifiedAt: Long,
    val pdf_info: List<TextLayerPage>
)

@Serializable
private data class TextLayerPage(
    val page_idx: Int,
    val page_size: List<Float>,
    val para_blocks: List<TextLayerBlock>
)

@Serializable
private data class TextLayerBlock(
    val id: String,
    val type: String,
    val bbox: List<Float>,
    val text: String,
    val lines: List<TextLayerLine>
)

@Serializable
private data class TextLayerLine(
    val bbox: List<Float>,
    val text: String,
    val spans: List<TextLayerSpan>
)

@Serializable
private data class TextLayerSpan(
    val bbox: List<Float>,
    val content: String
)
