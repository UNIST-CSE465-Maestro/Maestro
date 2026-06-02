package com.maestro.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LocalMlKitContentExtractor(
    private val context: Context
) {
    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }

    suspend fun extract(
        documentId: String,
        uriString: String,
        onProgress: suspend (Int) -> Unit = {}
    ): LocalMlKitExtraction = withContext(Dispatchers.IO) {
        val file =
            Uri.parse(uriString).path
                ?.let(::File)
                ?: error("Cannot resolve PDF path")
        val fd =
            ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_READ_ONLY
            )
        val renderer = PdfRenderer(fd)
        val recognizer =
            TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
            )
        val pages = mutableListOf<LocalMlKitPage>()
        val markdown = StringBuilder()
        try {
            val pageCount = renderer.pageCount
            repeat(pageCount) { pageIndex ->
                onProgress(
                    5 +
                        ((pageIndex.toFloat() / pageCount) * 90)
                            .toInt()
                )
                renderer.openPage(pageIndex).use { page ->
                    val bitmap = renderPage(page)
                    val result =
                        Tasks.await(
                            recognizer.process(
                                InputImage.fromBitmap(bitmap, 0)
                            )
                        )
                    val localPage =
                        pageFromResult(
                            pageIndex = pageIndex,
                            width = bitmap.width,
                            height = bitmap.height,
                            result = result
                        )
                    pages += localPage
                    markdown.appendPage(localPage)
                    bitmap.recycle()
                }
            }
        } finally {
            recognizer.close()
            renderer.close()
            fd.close()
        }

        val root =
            LocalMlKitRoot(
                source = "mlkit_text_recognition_v2_latin",
                documentId = documentId,
                generatedAt = System.currentTimeMillis(),
                pdf_info = pages
            )
        LocalMlKitExtraction(
            markdown = markdown.toString().trim() + "\n",
            json = json.encodeToString(root)
        )
    }

    suspend fun extractImageText(imageBytes: ByteArray): String = withContext(Dispatchers.IO) {
        val bitmap =
            BitmapFactory.decodeByteArray(
                imageBytes,
                0,
                imageBytes.size
            ) ?: return@withContext ""
        val recognizer =
            TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
            )
        try {
            val result =
                Tasks.await(
                    recognizer.process(
                        InputImage.fromBitmap(bitmap, 0)
                    )
                )
            result.textBlocks
                .flatMap { block -> block.lines }
                .sortedWith(
                    compareBy<Text.Line> {
                        it.boundingBox?.top ?: 0
                    }.thenBy {
                        it.boundingBox?.left ?: 0
                    }
                )
                .joinToString("\n") { it.text.trim() }
                .trim()
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }

    private fun renderPage(page: PdfRenderer.Page): Bitmap {
        val scale =
            minOf(
                MAX_RENDER_WIDTH.toFloat() / page.width,
                MAX_RENDER_HEIGHT.toFloat() / page.height
            ).coerceAtMost(MAX_RENDER_SCALE)
                .coerceAtLeast(1f)
        val width =
            (page.width * scale).toInt()
                .coerceAtLeast(page.width)
        val height =
            (page.height * scale).toInt()
                .coerceAtLeast(page.height)
        val bitmap =
            Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            )
        bitmap.eraseColor(Color.WHITE)
        page.render(
            bitmap,
            null,
            null,
            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
        )
        return bitmap
    }

    private fun pageFromResult(
        pageIndex: Int,
        width: Int,
        height: Int,
        result: Text
    ): LocalMlKitPage {
        val blocks =
            result.textBlocks
                .mapIndexedNotNull { index, block ->
                    block.toLocalBlock(index)
                }
                .sortedWith(
                    compareBy<LocalMlKitBlock> {
                        it.bbox[1]
                    }.thenBy { it.bbox[0] }
                )
        return LocalMlKitPage(
            page_idx = pageIndex,
            page_size = listOf(width, height),
            para_blocks = blocks
        )
    }

    private fun Text.TextBlock.toLocalBlock(index: Int): LocalMlKitBlock? {
        val rect = boundingBox ?: return null
        val localLines =
            lines.mapNotNull { line ->
                line.toLocalLine()
            }
        return LocalMlKitBlock(
            id = "b$index",
            type = "text",
            bbox = rect.toList(),
            text = text,
            lines = localLines
        )
    }

    private fun Text.Line.toLocalLine(): LocalMlKitLine? {
        val rect = boundingBox ?: return null
        val localSpans =
            elements.mapNotNull { element ->
                val elementRect =
                    element.boundingBox
                        ?: return@mapNotNull null
                LocalMlKitSpan(
                    bbox = elementRect.toList(),
                    content = element.text
                )
            }.ifEmpty {
                listOf(
                    LocalMlKitSpan(
                        bbox = rect.toList(),
                        content = text
                    )
                )
            }
        return LocalMlKitLine(
            bbox = rect.toList(),
            text = text,
            spans = localSpans
        )
    }

    private fun StringBuilder.appendPage(page: LocalMlKitPage) {
        append("# Page ")
        append(page.page_idx + 1)
        append("\n\n")
        page.para_blocks.forEach { block ->
            val text =
                block.text
                    .replace(Regex("\\s+"), " ")
                    .trim()
            if (text.isNotBlank()) {
                append(text)
                append("\n\n")
            }
        }
    }

    private fun Rect.toList(): List<Int> = listOf(left, top, right, bottom)

    companion object {
        private const val MAX_RENDER_WIDTH = 1800
        private const val MAX_RENDER_HEIGHT = 2400
        private const val MAX_RENDER_SCALE = 2.5f
    }
}

data class LocalMlKitExtraction(
    val markdown: String,
    val json: String
)

@Serializable
private data class LocalMlKitRoot(
    val source: String,
    val documentId: String,
    val generatedAt: Long,
    val pdf_info: List<LocalMlKitPage>
)

@Serializable
private data class LocalMlKitPage(
    val page_idx: Int,
    val page_size: List<Int>,
    val para_blocks: List<LocalMlKitBlock>
)

@Serializable
private data class LocalMlKitBlock(
    val id: String,
    val type: String,
    val bbox: List<Int>,
    val text: String,
    val lines: List<LocalMlKitLine>
)

@Serializable
private data class LocalMlKitLine(
    val bbox: List<Int>,
    val text: String,
    val spans: List<LocalMlKitSpan>
)

@Serializable
private data class LocalMlKitSpan(
    val bbox: List<Int>,
    val content: String
)
