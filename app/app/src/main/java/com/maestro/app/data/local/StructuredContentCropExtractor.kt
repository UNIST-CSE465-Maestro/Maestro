package com.maestro.app.data.local

import com.maestro.app.domain.model.CropCapturePayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class CropContentSelection(
    val content: String,
    val label: String,
    val matchedBlockCount: Int
)

object StructuredContentCropExtractor {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun extract(
        rawJson: String?,
        payload: CropCapturePayload
    ): CropContentSelection {
        if (rawJson.isNullOrBlank()) {
            return emptySelection(payload)
        }
        return runCatching {
            when (val root = json.parseToJsonElement(rawJson)) {
                is JsonObject -> extractFromPdfInfo(root, payload)
                is JsonArray -> extractFromFlatBlocks(root, payload)
                else -> emptySelection(payload)
            }
        }.getOrElse {
            emptySelection(payload)
        }
    }

    private fun extractFromPdfInfo(
        root: JsonObject,
        payload: CropCapturePayload
    ): CropContentSelection {
        val pages = root["pdf_info"]?.jsonArray
            ?: return emptySelection(payload)
        val page = pageForIndex(pages, payload.pageIndex)
            ?: return emptySelection(payload)
        val crop = cropRectForPage(page, payload)
        val blocks = page["para_blocks"]?.jsonArray
            .orEmpty()
            .mapNotNull { it as? JsonObject }
        return buildSelection(blocks, crop, payload)
    }

    private fun extractFromFlatBlocks(
        root: JsonArray,
        payload: CropCapturePayload
    ): CropContentSelection {
        val pageBlocks = root
            .mapNotNull { it as? JsonObject }
            .filter {
                it["page_idx"]?.numberAsInt() == payload.pageIndex
            }
        val blocks = pageBlocks
            .filterNot {
                val type = it["type"]?.stringValue().orEmpty()
                type == "header" || type == "footer" ||
                    type == "page_number"
            }
        if (blocks.isEmpty()) return emptySelection(payload)
        val rects = pageBlocks.mapNotNull { it.bbox() }
        val pageWidth = rects.maxOfOrNull { it.right }
            ?: payload.pageRefWidth
        val pageHeight = rects.maxOfOrNull { it.bottom }
            ?: payload.pageRefHeight
        val crop = cropRectForSize(
            payload = payload,
            pageWidth = pageWidth,
            pageHeight = pageHeight
        )
        return buildSelection(blocks, crop, payload)
    }

    private fun buildSelection(
        blocks: List<JsonObject>,
        crop: FloatRect,
        payload: CropCapturePayload
    ): CropContentSelection {
        val matched = blocks
            .mapNotNull { block ->
                val bbox = block.bbox()
                    ?: return@mapNotNull null
                val score = overlapRatio(crop, bbox)
                val centerInside = crop.contains(
                    bbox.centerX,
                    bbox.centerY
                )
                if (score >= MIN_OVERLAP_RATIO ||
                    centerInside ||
                    bbox.contains(crop.centerX, crop.centerY)
                ) {
                    block to bbox
                } else {
                    null
                }
            }
            .sortedWith(
                compareBy<Pair<JsonObject, FloatRect>> {
                    it.second.top
                }.thenBy { it.second.left }
            )
        val body = matched
            .flatMap { extractText(it.first) }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n\n")
        return CropContentSelection(
            content = body,
            label = selectionLabel(body, payload, matched.size),
            matchedBlockCount = matched.size
        )
    }

    private fun selectionLabel(
        body: String,
        payload: CropCapturePayload,
        blockCount: Int
    ): String {
        val preview = body
            .replace(Regex("\\s+"), " ")
            .trim()
            .let { text ->
                if (text.length > LABEL_PREVIEW_CHARS) {
                    text.take(LABEL_PREVIEW_CHARS)
                        .trimEnd() + "..."
                } else {
                    text
                }
            }
        val prefix = if (preview.isBlank()) {
            "선택 영역"
        } else {
            "선택 영역: $preview"
        }
        return "$prefix · 페이지 ${payload.pageIndex + 1} · $blockCount blocks"
    }

    private fun pageForIndex(
        pages: JsonArray,
        pageIndex: Int
    ): JsonObject? {
        return pages
            .mapNotNull { it as? JsonObject }
            .firstOrNull {
                it["page_idx"]?.numberAsInt() == pageIndex
            }
            ?: pages.getOrNull(pageIndex) as? JsonObject
    }

    private fun cropRectForPage(
        page: JsonObject,
        payload: CropCapturePayload
    ): FloatRect {
        val pageSize = page["page_size"] as? JsonArray
        val pageWidth = pageSize?.getOrNull(0)
            ?.numberAsFloat()
            ?: payload.pageRefWidth
        val pageHeight = pageSize?.getOrNull(1)
            ?.numberAsFloat()
            ?: payload.pageRefHeight
        val scaleX = if (payload.pageRefWidth > 0f) {
            pageWidth / payload.pageRefWidth
        } else {
            1f
        }
        val scaleY = if (payload.pageRefHeight > 0f) {
            pageHeight / payload.pageRefHeight
        } else {
            scaleX
        }
        return FloatRect(
            left = minOf(payload.left, payload.right) * scaleX,
            top = minOf(payload.top, payload.bottom) * scaleY,
            right = maxOf(payload.left, payload.right) * scaleX,
            bottom = maxOf(payload.top, payload.bottom) * scaleY
        )
    }

    private fun cropRectForSize(
        payload: CropCapturePayload,
        pageWidth: Float,
        pageHeight: Float
    ): FloatRect {
        val scaleX = if (payload.pageRefWidth > 0f) {
            pageWidth / payload.pageRefWidth
        } else {
            1f
        }
        val scaleY = if (payload.pageRefHeight > 0f) {
            pageHeight / payload.pageRefHeight
        } else {
            scaleX
        }
        return FloatRect(
            left = minOf(payload.left, payload.right) * scaleX,
            top = minOf(payload.top, payload.bottom) * scaleY,
            right = maxOf(payload.left, payload.right) * scaleX,
            bottom = maxOf(payload.top, payload.bottom) * scaleY
        )
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

    private fun extractText(block: JsonObject): List<String> {
        val flatText = listOfNotNull(
            block["text"]?.stringValue(),
            block["html"]?.stringValue(),
            block["image_path"]?.stringValue()
                ?.let { "[image: $it]" }
        )
        val listItems = (block["list_items"] as? JsonArray)
            .orEmpty()
            .mapNotNull { item ->
                when (item) {
                    is JsonPrimitive -> item.stringValue()
                    is JsonObject -> item["text"]?.stringValue()
                    else -> null
                }
            }
        val lines = block["lines"] as? JsonArray
        val direct = lines.orEmpty().flatMap { line ->
            val spans = (line as? JsonObject)
                ?.get("spans") as? JsonArray
            spans.orEmpty().mapNotNull { span ->
                (span as? JsonObject)?.let {
                    it["content"]?.stringValue()
                        ?: it["html"]?.stringValue()
                        ?: it["image_path"]?.stringValue()
                            ?.let { path -> "[image: $path]" }
                }
            }
        }
        val nested = (block["blocks"] as? JsonArray)
            .orEmpty()
            .flatMap {
                (it as? JsonObject)?.let(::extractText)
                    .orEmpty()
            }
        return flatText + listItems + direct + nested
    }

    private fun overlapRatio(
        a: FloatRect,
        b: FloatRect
    ): Float {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        val overlap = (right - left).coerceAtLeast(0f) *
            (bottom - top).coerceAtLeast(0f)
        val blockArea = b.area.coerceAtLeast(1f)
        return overlap / blockArea
    }

    private fun emptySelection(
        payload: CropCapturePayload
    ): CropContentSelection =
        CropContentSelection(
            content = "",
            label = "선택 영역 · 페이지 ${payload.pageIndex + 1}",
            matchedBlockCount = 0
        )

    private fun JsonElement.stringValue(): String? =
        runCatching { jsonPrimitive.content }.getOrNull()

    private fun JsonElement.numberAsFloat(): Float? =
        stringValue()?.toFloatOrNull()

    private fun JsonElement.numberAsInt(): Int? =
        stringValue()?.toIntOrNull()

    private data class FloatRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        val centerX: Float = (left + right) / 2f
        val centerY: Float = (top + bottom) / 2f
        val area: Float =
            (right - left).coerceAtLeast(0f) *
                (bottom - top).coerceAtLeast(0f)

        fun contains(x: Float, y: Float): Boolean =
            x in left..right && y in top..bottom
    }

    private const val MIN_OVERLAP_RATIO = 0.12f
    private const val LABEL_PREVIEW_CHARS = 42
}
