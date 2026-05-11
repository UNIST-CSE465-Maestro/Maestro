package com.maestro.app.data.local

import android.content.Context
import com.maestro.app.domain.model.PdfSearchMatch
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PdfTextIndex(
    @SerialName("schema_version")
    val schemaVersion: Int = 1,
    @SerialName("document_id")
    val documentId: String,
    @SerialName("source_name")
    val sourceName: String,
    @SerialName("source_length")
    val sourceLength: Long,
    @SerialName("source_modified_at")
    val sourceModifiedAt: Long,
    @SerialName("created_at")
    val createdAt: Long = System.currentTimeMillis(),
    val pages: List<PdfTextIndexPage> = emptyList()
) {
    val hasText: Boolean
        get() = pages.any { it.words.isNotEmpty() }
}

@Serializable
data class PdfTextIndexPage(
    @SerialName("page_index")
    val pageIndex: Int,
    val width: Float,
    val height: Float,
    val words: List<PdfTextIndexWord> = emptyList()
)

@Serializable
data class PdfTextIndexWord(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

class PdfTextIndexLocalDataSource {
    private val rootDir: File
    private val appContext: Context?
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    constructor(context: Context) : this(
        rootDir = File(context.filesDir, "documents"),
        appContext = context.applicationContext
    )

    internal constructor(rootDir: File) : this(
        rootDir = rootDir,
        appContext = null
    )

    private constructor(rootDir: File, appContext: Context?) {
        this.rootDir = rootDir
        this.appContext = appContext
        rootDir.mkdirs()
    }

    suspend fun ensureIndex(
        documentId: String,
        pdfFile: File,
        displayName: String,
        force: Boolean = false
    ): PdfTextIndex? = withContext(Dispatchers.IO) {
        if (!pdfFile.exists()) return@withContext null
        val existing = loadIndex(documentId)
        if (!force &&
            existing != null &&
            existing.schemaVersion == CURRENT_INDEX_VERSION &&
            existing.sourceLength == pdfFile.length() &&
            existing.sourceModifiedAt == pdfFile.lastModified()
        ) {
            return@withContext existing
        }
        runCatching {
            buildIndex(documentId, pdfFile, displayName)
        }.getOrNull()
    }

    fun loadIndex(documentId: String): PdfTextIndex? {
        val file = indexFile(documentId)
        if (!file.exists()) return null
        return try {
            json.decodeFromString<PdfTextIndex>(file.readText())
        } catch (_: Throwable) {
            null
        }
    }

    fun deleteIndex(documentId: String) {
        indexFile(documentId).delete()
    }

    fun copyIndex(sourceDocumentId: String, targetDocumentId: String) {
        val index = loadIndex(sourceDocumentId) ?: return
        saveIndex(index.copy(documentId = targetDocumentId))
    }

    fun search(index: PdfTextIndex?, query: String): List<PdfSearchMatch> =
        searchIndex(index, query)

    private fun buildIndex(
        documentId: String,
        pdfFile: File,
        displayName: String
    ): PdfTextIndex {
        appContext?.let { PDFBoxResourceLoader.init(it) }
        val pages = PDDocument.load(pdfFile).use { document ->
            (0 until document.numberOfPages).map { pageIndex ->
                val page = document.getPage(pageIndex)
                val box = page.cropBox ?: page.mediaBox
                val fallbackWidth = box.width.coerceAtLeast(1f)
                val fallbackHeight = box.height.coerceAtLeast(1f)
                val stripper = PositionCollectingStripper()
                val pageNumber = pageIndex + 1
                stripper.setSortByPosition(true)
                stripper.setStartPage(pageNumber)
                stripper.setEndPage(pageNumber)
                stripper.getText(document)
                val width = stripper.positions.firstOrNull()
                    ?.getPageWidth()
                    ?.coerceAtLeast(1f)
                    ?: fallbackWidth
                val height = stripper.positions.firstOrNull()
                    ?.getPageHeight()
                    ?.coerceAtLeast(1f)
                    ?: fallbackHeight
                PdfTextIndexPage(
                    pageIndex = pageIndex,
                    width = width,
                    height = height,
                    words = positionsToWords(
                        stripper.positions,
                        width,
                        height
                    )
                )
            }
        }
        val index = PdfTextIndex(
            schemaVersion = CURRENT_INDEX_VERSION,
            documentId = documentId,
            sourceName = displayName,
            sourceLength = pdfFile.length(),
            sourceModifiedAt = pdfFile.lastModified(),
            pages = pages
        )
        saveIndex(index)
        return index
    }

    private fun saveIndex(index: PdfTextIndex) {
        val file = indexFile(index.documentId)
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(index))
    }

    private fun indexFile(documentId: String): File =
        File(rootDir, "$documentId/pdf_text_index.json")

    private class PositionCollectingStripper : PDFTextStripper() {
        val positions = mutableListOf<TextPosition>()

        override fun processTextPosition(text: TextPosition) {
            positions += text
        }
    }

    private companion object {
        private const val CURRENT_INDEX_VERSION = 2
        private const val WORD_GAP_MULTIPLIER = 1.35f
        private const val TEXT_TOP_ASCENT_RATIO = 0.78f
        private const val TEXT_BOTTOM_DESCENT_RATIO = 0.22f

        fun searchIndex(
            index: PdfTextIndex?,
            query: String
        ): List<PdfSearchMatch> {
            val normalizedQuery = query.trim()
            if (index == null || normalizedQuery.isBlank()) {
                return emptyList()
            }
            val tokens = normalizedQuery.split(Regex("\\s+"))
                .filter { it.isNotBlank() }
            if (tokens.isEmpty()) return emptyList()
            val normalizedTokens = tokens.map(::normalize)
                .filter { it.isNotBlank() }
            if (normalizedTokens.isEmpty()) return emptyList()
            return index.pages.flatMap { page ->
                if (normalizedTokens.size == 1) {
                    searchSingleToken(page, normalizedTokens.first())
                } else {
                    searchPhrase(page, normalizedTokens)
                }
            }
        }

        private fun searchSingleToken(
            page: PdfTextIndexPage,
            token: String
        ): List<PdfSearchMatch> {
            return page.words.flatMap { word ->
                word.matchesToken(page, token)
            }
        }

        private fun searchPhrase(
            page: PdfTextIndexPage,
            tokens: List<String>
        ): List<PdfSearchMatch> {
            if (page.words.size < tokens.size) return emptyList()
            return buildList {
                for (start in 0..page.words.size - tokens.size) {
                    val window = page.words.subList(
                        start,
                        start + tokens.size
                    )
                    val text = window.joinToString(" ") {
                        normalize(it.text)
                    }
                    if (text == tokens.joinToString(" ")) {
                        add(window.toMatch(page))
                    }
                }
            }
        }

        private fun List<PdfTextIndexWord>.toMatch(
            page: PdfTextIndexPage
        ): PdfSearchMatch {
            val left = minOf { it.left }
            val top = minOf { it.top }
            val right = maxOf { it.right }
            val bottom = maxOf { it.bottom }
            return PdfSearchMatch(
                pageIndex = page.pageIndex,
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                pageWidth = page.width,
                pageHeight = page.height,
                matchedText = joinToString(" ") { it.text }
            )
        }

        private fun PdfTextIndexWord.toMatch(
            page: PdfTextIndexPage,
            matchedText: String
        ): PdfSearchMatch =
            PdfSearchMatch(
                pageIndex = page.pageIndex,
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                pageWidth = page.width,
                pageHeight = page.height,
                matchedText = matchedText
            )

        private fun PdfTextIndexWord.matchesToken(
            page: PdfTextIndexPage,
            token: String
        ): List<PdfSearchMatch> {
            val normalized = normalizeWithMapping(text)
            if (normalized.value.isBlank()) {
                return emptyList()
            }
            val matches = mutableListOf<PdfSearchMatch>()
            var start = normalized.value.indexOf(token)
            while (start >= 0) {
                val end = start + token.length
                val rawStart = normalized.rawIndices
                    .getOrNull(start)
                    ?: break
                val rawEndExclusive = normalized.rawIndices
                    .getOrNull(end - 1)
                    ?.plus(1)
                    ?: break
                matches += toSegmentMatch(
                    page = page,
                    rawStart = rawStart,
                    rawEndExclusive = rawEndExclusive
                )
                start = normalized.value.indexOf(token, end)
            }
            return matches
        }

        private fun PdfTextIndexWord.toSegmentMatch(
            page: PdfTextIndexPage,
            rawStart: Int,
            rawEndExclusive: Int
        ): PdfSearchMatch {
            val safeLength = text.length.coerceAtLeast(1)
            val leftRatio = rawStart
                .coerceIn(0, safeLength)
                .toFloat() / safeLength
            val rightRatio = rawEndExclusive
                .coerceIn(rawStart + 1, safeLength)
                .toFloat() / safeLength
            val width = (right - left).coerceAtLeast(1f)
            val segmentLeft = left + width * leftRatio
            val segmentRight = left + width * rightRatio
            val minWidth = (bottom - top)
                .coerceAtLeast(1f) * 0.45f
            return PdfSearchMatch(
                pageIndex = page.pageIndex,
                left = segmentLeft.coerceIn(left, right),
                top = top,
                right = max(
                    segmentRight.coerceIn(left, right),
                    segmentLeft + minWidth
                ).coerceAtMost(right),
                bottom = bottom,
                pageWidth = page.width,
                pageHeight = page.height,
                matchedText = text.substring(
                    rawStart.coerceIn(0, text.length),
                    rawEndExclusive.coerceIn(0, text.length)
                )
            )
        }

        private fun positionsToWords(
            positions: List<TextPosition>,
            pageWidth: Float,
            pageHeight: Float
        ): List<PdfTextIndexWord> {
            val sorted = positions.sortedWith(
                compareBy<TextPosition> {
                    it.getYDirAdj()
                }.thenBy { it.getXDirAdj() }
            )
            val words = mutableListOf<PdfTextIndexWord>()
            var current: WordBuilder? = null
            sorted.forEach { position ->
                val unicode = position.getUnicode().orEmpty()
                if (unicode.isBlank()) {
                    current?.finish(pageWidth, pageHeight)
                        ?.let(words::add)
                    current = null
                    return@forEach
                }
                val left = position.getXDirAdj()
                    .coerceIn(0f, pageWidth)
                val right = (left + position.getWidthDirAdj())
                    .coerceIn(left, pageWidth)
                val height = position.getHeightDir()
                    .coerceAtLeast(1f)
                val top = (position.getYDirAdj() -
                    height * TEXT_TOP_ASCENT_RATIO)
                    .coerceIn(0f, pageHeight)
                val bottom = min(
                    max(
                        position.getYDirAdj() +
                            height * TEXT_BOTTOM_DESCENT_RATIO,
                        top + 1f
                    ),
                    pageHeight
                )
                val spaceWidth = position.getWidthOfSpace()
                    .takeIf { it > 0f }
                    ?: height * 0.35f
                val builder = current
                val startsNewWord = builder == null ||
                    isNewWord(
                        builder = builder,
                        left = left,
                        top = top,
                        bottom = bottom,
                        spaceWidth = spaceWidth
                    )
                if (startsNewWord) {
                    builder?.finish(pageWidth, pageHeight)
                        ?.let(words::add)
                    current = WordBuilder(
                        text = StringBuilder(unicode),
                        left = left,
                        top = top,
                        right = right,
                        bottom = bottom
                    )
                } else {
                    builder.append(
                        text = unicode,
                        left = left,
                        top = top,
                        right = right,
                        bottom = bottom
                    )
                }
            }
            current?.finish(pageWidth, pageHeight)?.let(words::add)
            return words.filter { it.text.isNotBlank() }
        }

        private fun isNewWord(
            builder: WordBuilder,
            left: Float,
            top: Float,
            bottom: Float,
            spaceWidth: Float
        ): Boolean {
            val currentHeight =
                (builder.bottom - builder.top).coerceAtLeast(1f)
            val nextHeight = (bottom - top).coerceAtLeast(1f)
            val lineTolerance = max(
                currentHeight,
                nextHeight
            ) * 0.72f
            val isNewLine = abs(top - builder.top) > lineTolerance
            val gap = left - builder.right
            return isNewLine || gap > spaceWidth * WORD_GAP_MULTIPLIER
        }

        private fun normalize(text: String): String =
            text.trim()
                .lowercase(Locale.US)
                .filter { it.isLetterOrDigit() }

        private fun normalizeWithMapping(
            text: String
        ): NormalizedText {
            val value = StringBuilder()
            val rawIndices = mutableListOf<Int>()
            text.forEachIndexed { index, char ->
                if (char.isLetterOrDigit()) {
                    value.append(
                        char.toString()
                            .lowercase(Locale.US)
                    )
                    rawIndices += index
                }
            }
            return NormalizedText(
                value = value.toString(),
                rawIndices = rawIndices
            )
        }

        private data class NormalizedText(
            val value: String,
            val rawIndices: List<Int>
        )

        private data class WordBuilder(
            val text: StringBuilder,
            var left: Float,
            var top: Float,
            var right: Float,
            var bottom: Float
        ) {
            fun append(
                text: String,
                left: Float,
                top: Float,
                right: Float,
                bottom: Float
            ) {
                this.text.append(text)
                this.left = min(this.left, left)
                this.top = min(this.top, top)
                this.right = max(this.right, right)
                this.bottom = max(this.bottom, bottom)
            }

            fun finish(
                pageWidth: Float,
                pageHeight: Float
            ): PdfTextIndexWord? {
                val value = text.toString().trim()
                if (value.isBlank()) return null
                return PdfTextIndexWord(
                    text = value,
                    left = left.coerceIn(0f, pageWidth),
                    top = top.coerceIn(0f, pageHeight),
                    right = right.coerceIn(left, pageWidth),
                    bottom = min(
                        max(bottom, top + 1f),
                        pageHeight
                    )
                )
            }
        }
    }
}
