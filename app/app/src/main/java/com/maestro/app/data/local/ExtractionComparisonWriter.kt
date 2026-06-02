package com.maestro.app.data.local

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

object ExtractionComparisonWriter {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    fun writeIfPossible(documentDir: File) {
        val mineruMd = File(documentDir, "content.md")
        val mineruJson = File(documentDir, "content.json")
        val mlkitMd = File(documentDir, "local_mlkit_content.md")
        val mlkitJson = File(documentDir, "local_mlkit_content.json")
        if (!mineruMd.exists() || !mineruJson.exists() ||
            !mlkitMd.exists() || !mlkitJson.exists()
        ) {
            return
        }
        val mineruStats =
            statsFor(
                sourceName = "MinerU",
                md = mineruMd,
                rawJson = mineruJson.readText()
            )
        val mlkitStats =
            statsFor(
                sourceName = "ML Kit",
                md = mlkitMd,
                rawJson = mlkitJson.readText()
            )
        File(documentDir, "mineru_mlkit_comparison.md")
            .writeText(buildMarkdown(mineruStats, mlkitStats))
    }

    private fun statsFor(sourceName: String, md: File, rawJson: String): ExtractionStats {
        val root =
            runCatching {
                json.parseToJsonElement(rawJson)
            }.getOrNull()
        val pages =
            when (root) {
                is JsonObject -> root["pdf_info"] as? JsonArray
                is JsonArray -> root
                else -> null
            }.orEmpty()
        val blocks =
            pages.flatMap { page ->
                (page as? JsonObject)
                    ?.get("para_blocks") as? JsonArray
                    ?: JsonArray(emptyList())
            }
        val lines =
            blocks.flatMap { block ->
                (block as? JsonObject)
                    ?.get("lines") as? JsonArray
                    ?: JsonArray(emptyList())
            }
        val spans =
            lines.flatMap { line ->
                (line as? JsonObject)
                    ?.get("spans") as? JsonArray
                    ?: JsonArray(emptyList())
            }
        val types =
            blocks
                .mapNotNull { block ->
                    (block as? JsonObject)
                        ?.get("type")
                        ?.jsonPrimitive
                        ?.content
                }
                .groupingBy { it }
                .eachCount()
        val text = md.readText()
        return ExtractionStats(
            sourceName = sourceName,
            mdBytes = md.length(),
            jsonBytes = rawJson.toByteArray().size.toLong(),
            pageCount = pages.size,
            blockCount = blocks.size,
            lineCount = lines.size,
            spanCount = spans.size,
            markdownChars = text.length,
            tableCount =
            Regex("<table", RegexOption.IGNORE_CASE)
                .findAll(text)
                .count(),
            headingCount =
            Regex("^# ", RegexOption.MULTILINE)
                .findAll(text)
                .count(),
            typeCounts = types
        )
    }

    private fun buildMarkdown(mineru: ExtractionStats, mlkit: ExtractionStats): String = """
        # Same-PDF MinerU vs ML Kit Extraction Comparison

        Generated at: ${System.currentTimeMillis()}

        ## Summary

        This file compares extraction artifacts generated from the same PDF document.

        - MinerU output: `content.md`, `content.json`
        - ML Kit output: `local_mlkit_content.md`, `local_mlkit_content.json`

        ## Quantitative Snapshot

        | Metric | MinerU | ML Kit |
        |---|---:|---:|
        | Markdown bytes | ${mineru.mdBytes} | ${mlkit.mdBytes} |
        | JSON bytes | ${mineru.jsonBytes} | ${mlkit.jsonBytes} |
        | Pages | ${mineru.pageCount} | ${mlkit.pageCount} |
        | Blocks | ${mineru.blockCount} | ${mlkit.blockCount} |
        | Lines | ${mineru.lineCount} | ${mlkit.lineCount} |
        | Spans/words | ${mineru.spanCount} | ${mlkit.spanCount} |
        | Markdown chars | ${mineru.markdownChars} | ${mlkit.markdownChars} |
        | Markdown headings | ${mineru.headingCount} | ${mlkit.headingCount} |
        | HTML tables | ${mineru.tableCount} | ${mlkit.tableCount} |

        ## Block Type Counts

        ### MinerU

        ${mineru.typeCounts.toMarkdownList()}

        ### ML Kit

        ${mlkit.typeCounts.toMarkdownList()}

        ## Interpretation

        MinerU is better suited for structured document understanding because it can preserve semantic block types such as title, text, table, and image. Its Markdown is usually more suitable for whole-document LLM prompting.

        ML Kit is better suited for local fallback OCR, search, and crop-based text extraction. It stores detailed OCR boxes, but it does not infer document semantics, table structure, formula structure, or chart data.

        ## Recommended Use

        - Use MinerU for whole-document quiz generation, document-level concept profiling, and structured retrieval.
        - Use ML Kit when MinerU is unavailable, slow, or failed.
        - Use ML Kit for local search and crop selection fallback.
        - Do not rely on either MinerU or ML Kit alone for precise chart/table numeric extraction.
    """.trimIndent() + "\n"

    private fun Map<String, Int>.toMarkdownList(): String {
        if (isEmpty()) return "- none"
        return entries.sortedBy { it.key }
            .joinToString("\n") { "- `${it.key}`: ${it.value}" }
    }

    private data class ExtractionStats(
        val sourceName: String,
        val mdBytes: Long,
        val jsonBytes: Long,
        val pageCount: Int,
        val blockCount: Int,
        val lineCount: Int,
        val spanCount: Int,
        val markdownChars: Int,
        val tableCount: Int,
        val headingCount: Int,
        val typeCounts: Map<String, Int>
    )
}
