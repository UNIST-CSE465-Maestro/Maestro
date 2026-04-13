package com.maestro.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.maestro.app.domain.model.ChatMessage
import com.maestro.app.domain.service.LlmService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class PdfTextExtractor(
    private val llmService: LlmService,
    private val context: Context
) {
    suspend fun extractText(
        pdfUri: Uri,
        pageCount: Int
    ): String = withContext(Dispatchers.IO) {
        val images = mutableListOf<ByteArray>()
        for (i in 0 until pageCount) {
            val bytes = renderPageToPng(pdfUri, i)
            if (bytes != null) images.add(bytes)
        }
        val systemPrompt =
            "Extract all text from these PDF pages. " +
                "Return only the raw text content, " +
                "preserving paragraph structure. " +
                "Do not add any commentary."
        val messages = listOf(
            ChatMessage(
                role = ChatMessage.Role.USER,
                content = "Extract text from these pages."
            )
        )
        llmService.complete(
            messages = messages,
            systemPrompt = systemPrompt,
            images = images
        )
    }

    suspend fun saveContentMd(
        documentId: String,
        text: String
    ) = withContext(Dispatchers.IO) {
        val dir = File(
            context.filesDir,
            "documents/$documentId"
        )
        dir.mkdirs()
        File(dir, "content.md").writeText(text)
    }

    suspend fun loadContentMd(
        documentId: String
    ): String? = withContext(Dispatchers.IO) {
        val file = File(
            context.filesDir,
            "documents/$documentId/content.md"
        )
        if (file.exists()) file.readText() else null
    }

    private fun renderPageToPng(
        uri: Uri,
        pageIndex: Int
    ): ByteArray? {
        var fd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        return try {
            fd = openPfd(uri) ?: return null
            renderer = PdfRenderer(fd)
            if (pageIndex >= renderer.pageCount) {
                return null
            }
            page = renderer.openPage(pageIndex)
            val maxDim = 2048
            val w = page.width
            val h = page.height
            val scale = if (w > maxDim || h > maxDim) {
                maxDim.toFloat() / maxOf(w, h)
            } else {
                2f
            }
            val bmpW =
                (w * scale).toInt().coerceAtLeast(1)
            val bmpH =
                (h * scale).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(
                bmpW, bmpH, Bitmap.Config.ARGB_8888
            )
            bmp.eraseColor(
                android.graphics.Color.WHITE
            )
            page.render(
                bmp, null, null,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            )
            val stream = ByteArrayOutputStream()
            bmp.compress(
                Bitmap.CompressFormat.PNG, 100, stream
            )
            bmp.recycle()
            stream.toByteArray()
        } catch (_: Throwable) {
            null
        } finally {
            try { page?.close() } catch (_: Throwable) {}
            try {
                renderer?.close()
            } catch (_: Throwable) {}
            try { fd?.close() } catch (_: Throwable) {}
        }
    }

    private fun openPfd(
        uri: Uri
    ): ParcelFileDescriptor? {
        return try {
            if (uri.scheme == "file") {
                val path = uri.path ?: return null
                val file = File(path)
                if (!file.exists()) return null
                ParcelFileDescriptor.open(
                    file,
                    ParcelFileDescriptor.MODE_READ_ONLY
                )
            } else {
                context.contentResolver
                    .openFileDescriptor(uri, "r")
            }
        } catch (_: Throwable) {
            null
        }
    }
}
