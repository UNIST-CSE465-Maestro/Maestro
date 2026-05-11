package com.maestro.app.data.local

import com.maestro.app.domain.model.PdfSearchMatch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

object StructuredContentSearchExtractor {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun search(
        rawJson: String?,
        query: String
    ): List<PdfSearchMatch> {
        val normalizedQuery = query.trim()
        if (rawJson.isNullOrBlank() ||
            normalizedQuery.isBlank()
        ) {
            return emptyList()
        }
        return runCatching {
            when (val root = json.parseToJsonElement(rawJson)) {
                is JsonObject -> searchPdfInfo(root, normalizedQuery)
                is JsonArray -> searchFlatBlocks(root, normalizedQuery)
                else -> emptyList()
            }
        }.getOrElse { emptyList() }
    }

    private fun searchPdfInfo(
        root: JsonObject,
        query: String
    ): List<PdfSearchMatch> {
        val pages = root["pdf_info"]?.jsonArray
            ?: return emptyList()
        return pages
            .mapNotNull { it as? JsonObject }
            .flatMapIndexed { fallbackIndex, page ->
                val pageIndex = page["page_idx"]
                    ?.numberAsInt() ?: fallbackIndex
                val pageSize = page["page_size"] as? JsonArray
                val pageWidth = pageSize?.getOrNull(0)
                    ?.numberAsFloat()
                val pageHeight = pageSize?.getOrNull(1)
                    ?.numberAsFloat()
                val blocks = page["para_blocks"]?.jsonArray
                    .orEmpty()
                    .mapNotNull { it as? JsonObject }
                val units = blocks.flatMap(::textUnits)
                val fallbackSize = fallbackPageSize(units)
                val width = pageWidth ?: fallbackSize.first
                val height = pageHeight ?: fallbackSize.second
                units.flatMap { unit ->
                    unit.matches(
                        pageIndex = pageIndex,
                        pageWidth = width,
                        pageHeight = height,
                        query = query
                    )
                }
            }
    }

    private fun searchFlatBlocks(
        root: JsonArray,
        query: String
    ): List<PdfSearchMatch> {
        val blocks = root
            .mapNotNull { it as? JsonObject }
            .filterNot {
                val type = it["type"]?.stringValue().orEmpty()
                type == "header" || type == "footer" ||
                    type == "page_number"
            }
        val grouped = blocks.groupBy {
            it["page_idx"]?.numberAsInt() ?: 0
        }
        return grouped.flatMap { (pageIndex, pageBlocks) ->
            val units = pageBlocks.flatMap(::textUnits)
            val pageSize = fallbackPageSize(units)
            units.flatMap { unit ->
                unit.matches(
                    pageIndex = pageIndex,
                    pageWidth = pageSize.first,
                    pageHeight = pageSize.second,
                    query = query
                )
            }
        }
    }

    private fun textUnits(block: JsonObject): List<TextUnit> {
        val blockBox = block.bbox()
        val direct = buildList {
            block["text"]?.stringValue()?.let { text ->
                blockBox?.let { add(TextUnit(text, it)) }
            }
            block["html"]?.stringValue()?.let { text ->
                blockBox?.let { add(TextUnit(text, it)) }
            }
        }
        val listItems = listItemUnits(block, blockBox)
        val lines = (block["lines"] as? JsonArray)
            .orEmpty()
            .mapNotNull { it as? JsonObject }
            .flatMap { lineUnits(it, blockBox) }
        val nested = (block["blocks"] as? JsonArray)
            .orEmpty()
            .flatMap {
                (it as? JsonObject)?.let(::textUnits)
                    .orEmpty()
            }
        return (direct + listItems + lines + nested)
            .filter { it.text.isNotBlank() }
    }

    private fun listItemUnits(
        block: JsonObject,
        blockBox: FloatRect?
    ): List<TextUnit> {
        val items = (block["list_items"] as? JsonArray)
            .orEmpty()
            .mapNotNull { item ->
                when (item) {
                    is JsonPrimitive -> item.stringValue()
                    is JsonObject -> item["text"]?.stringValue()
                    else -> null
                }
            }
            .filter { it.isNotBlank() }
        if (items.isEmpty() || blockBox == null) {
            return emptyList()
        }
        val rowHeight = blockBox.height / items.size
        return items.mapIndexed { index, text ->
            TextUnit(
                text = text,
                bbox = FloatRect(
                    left = blockBox.left,
                    top = blockBox.top + rowHeight * index,
                    right = blockBox.right,
                    bottom = blockBox.top + rowHeight * (index + 1)
                )
            )
        }
    }

    private fun lineUnits(
        line: JsonObject,
        fallbackBox: FloatRect?
    ): List<TextUnit> {
        val lineBox = line.bbox() ?: fallbackBox
        val spans = (line["spans"] as? JsonArray)
            .orEmpty()
            .mapNotNull { it as? JsonObject }
        val spanUnits = spans.mapNotNull { span ->
            val text = span["content"]?.stringValue()
                ?: span["text"]?.stringValue()
                ?: span["html"]?.stringValue()
            val box = span.bbox()
            if (text.isNullOrBlank() || box == null) {
                null
            } else {
                TextUnit(text, box)
            }
        }
        if (spanUnits.isNotEmpty()) {
            return spanUnits
        }
        val joined = spans.mapNotNull { span ->
            span["content"]?.stringValue()
                ?: span["text"]?.stringValue()
                ?: span["html"]?.stringValue()
        }.joinToString(" ").trim()
        return if (joined.isNotBlank() && lineBox != null) {
            listOf(TextUnit(joined, lineBox))
        } else {
            emptyList()
        }
    }

    private fun TextUnit.matches(
        pageIndex: Int,
        pageWidth: Float,
        pageHeight: Float,
        query: String
    ): List<PdfSearchMatch> {
        val textLower = text.lowercase(Locale.US)
        val queryLower = query.lowercase(Locale.US)
        val results = mutableListOf<PdfSearchMatch>()
        var start = textLower.indexOf(queryLower)
        while (start >= 0) {
            val end = start + queryLower.length
            val textLength = text.length.coerceAtLeast(1)
            val leftRatio = start.toFloat() / textLength
            val rightRatio = end.toFloat() / textLength
            val estimatedLeft = bbox.left + bbox.width * leftRatio
            val estimatedRight = bbox.left + bbox.width * rightRatio
            val minWidth = (bbox.height * 0.45f).coerceAtLeast(4f)
            results += PdfSearchMatch(
                pageIndex = pageIndex,
                left = estimatedLeft.coerceIn(bbox.left, bbox.right),
                top = bbox.top,
                right = maxOf(
                    estimatedRight.coerceIn(bbox.left, bbox.right),
                    estimatedLeft + minWidth
                ).coerceAtMost(bbox.right),
                bottom = bbox.bottom,
                pageWidth = pageWidth.coerceAtLeast(1f),
                pageHeight = pageHeight.coerceAtLeast(1f),
                matchedText = text.substring(
                    start,
                    end.coerceAtMost(text.length)
                )
            )
            start = textLower.indexOf(queryLower, end)
        }
        return results
    }

    private fun fallbackPageSize(
        units: List<TextUnit>
    ): Pair<Float, Float> {
        val width = units.maxOfOrNull { it.bbox.right } ?: 1f
        val height = units.maxOfOrNull { it.bbox.bottom } ?: 1f
        return width.coerceAtLeast(1f) to height.coerceAtLeast(1f)
    }

    private fun JsonObject.bbox(): FloatRect? {
        val bbox = this["bbox"] as? JsonArray ?: return null
        if (bbox.size < 4) return null
        return FloatRect(
            left = bbox[0].numberAsFloat() ?: return null,
            top = bbox[1].numberAsFloat() ?: return null,
            right = bbox[2].numberAsFloat() ?: return null,
            bottom = bbox[3].numberAsFloat() ?: return null
        )
    }

    private fun JsonElement.stringValue(): String? =
        runCatching { jsonPrimitive.content }.getOrNull()

    private fun JsonElement.numberAsFloat(): Float? =
        stringValue()?.toFloatOrNull()

    private fun JsonElement.numberAsInt(): Int? =
        stringValue()?.toIntOrNull()

    private data class TextUnit(
        val text: String,
        val bbox: FloatRect
    )

    private data class FloatRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        val width: Float = (right - left).coerceAtLeast(1f)
        val height: Float = (bottom - top).coerceAtLeast(1f)
    }
}
