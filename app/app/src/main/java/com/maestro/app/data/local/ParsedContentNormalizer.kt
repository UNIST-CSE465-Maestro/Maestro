package com.maestro.app.data.local

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object ParsedContentNormalizer {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = true
        }

    fun normalizeMineruJson(documentId: String, rawJson: String): String {
        val root =
            runCatching {
                json.parseToJsonElement(rawJson)
            }.getOrNull() ?: return rawJson
        if (root is JsonObject && root["pdf_info"] is JsonArray) {
            return json.encodeToString(JsonElement.serializer(), root)
        }
        val pages = root as? JsonArray ?: return rawJson
        val normalizedPages =
            buildJsonArray {
                pages.forEachIndexed { pageIndex, page ->
                    val blocks =
                        page as? JsonArray
                            ?: return@forEachIndexed
                    add(normalizePage(pageIndex, blocks))
                }
            }
        val normalizedRoot =
            buildJsonObject {
                put("schema_version", 1)
                put("source", "mineru")
                put("documentId", documentId)
                put("generatedAt", System.currentTimeMillis())
                put("pdf_info", normalizedPages)
            }
        return json.encodeToString(
            JsonElement.serializer(),
            normalizedRoot
        )
    }

    private fun normalizePage(pageIndex: Int, blocks: JsonArray): JsonObject {
        val normalizedBlocks =
            blocks
                .mapNotNull { it as? JsonObject }
                .mapIndexed { index, block ->
                    normalizeBlock(index, block)
                }
        val pageSize = pageSize(normalizedBlocks)
        return buildJsonObject {
            put("page_idx", pageIndex)
            put(
                "page_size",
                listOf(pageSize.first, pageSize.second).toJsonArray()
            )
            put(
                "para_blocks",
                buildJsonArray {
                    normalizedBlocks.forEach(::add)
                }
            )
        }
    }

    private fun normalizeBlock(index: Int, block: JsonObject): JsonObject {
        val bbox = block.bbox()
        val text = extractText(block["content"])
        val imagePath = findImagePath(block["content"])
        val type =
            block["type"]?.stringValue().orEmpty()
                .ifBlank { "text" }
        return buildJsonObject {
            put("id", "b$index")
            put("type", type)
            put("bbox", bbox.toJsonArray())
            put("text", text)
            imagePath?.let {
                put("image_path", it)
            }
            put(
                "lines",
                buildJsonArray {
                    if (text.isNotBlank()) {
                        add(
                            buildJsonObject {
                                put("bbox", bbox.toJsonArray())
                                put("text", text)
                                put(
                                    "spans",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("bbox", bbox.toJsonArray())
                                                put("content", text)
                                            }
                                        )
                                    }
                                )
                            }
                        )
                    }
                }
            )
        }
    }

    private fun extractText(element: JsonElement?): String {
        val parts = mutableListOf<String>()

        fun walk(value: JsonElement?) {
            when (value) {
                is JsonObject ->
                    value.forEach { (key, child) ->
                        if ((key == "content" || key == "item_content") &&
                            child.stringValue() != null
                        ) {
                            parts += child.stringValue().orEmpty()
                        } else if (
                            key.endsWith("_content") ||
                            key == "paragraph_content" ||
                            key == "title_content" ||
                            key == "list_items" ||
                            child is JsonArray ||
                            child is JsonObject
                        ) {
                            walk(child)
                        }
                    }
                is JsonArray -> value.forEach(::walk)
                else -> Unit
            }
        }
        walk(element)
        return parts.joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun findImagePath(element: JsonElement?): String? {
        fun walk(value: JsonElement?): String? {
            return when (value) {
                is JsonObject -> {
                    val source = value["image_source"] as? JsonObject
                    source?.get("path")?.stringValue()
                        ?: value.values.firstNotNullOfOrNull(::walk)
                }
                is JsonArray -> value.firstNotNullOfOrNull(::walk)
                else -> null
            }
        }
        return walk(element)
    }

    private fun pageSize(blocks: List<JsonObject>): Pair<Float, Float> {
        val rects = blocks.mapNotNull { it["bbox"] as? JsonArray }
        val width =
            rects.maxOfOrNull {
                it.getOrNull(2)?.numberValue() ?: 0f
            } ?: 0f
        val height =
            rects.maxOfOrNull {
                it.getOrNull(3)?.numberValue() ?: 0f
            } ?: 0f
        return width.coerceAtLeast(1f) to height.coerceAtLeast(1f)
    }

    private fun JsonObject.bbox(): List<Float> {
        val bbox = this["bbox"] as? JsonArray
        if (bbox == null || bbox.size < 4) {
            return listOf(0f, 0f, 1f, 1f)
        }
        return listOf(
            bbox[0].numberValue() ?: 0f,
            bbox[1].numberValue() ?: 0f,
            bbox[2].numberValue() ?: 1f,
            bbox[3].numberValue() ?: 1f
        )
    }

    private fun List<Float>.toJsonArray(): JsonArray = buildJsonArray {
        forEach { value ->
            add(JsonPrimitive(value))
        }
    }

    private fun JsonElement.stringValue(): String? = runCatching {
        jsonPrimitive.contentOrNull
    }.getOrNull()

    private fun JsonElement.numberValue(): Float? = stringValue()?.toFloatOrNull()
}
