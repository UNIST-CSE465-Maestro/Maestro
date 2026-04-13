package com.maestro.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfMerger(private val context: Context) {

    /**
     * Merge multiple PDFs into one.
     * @param pdfUris list of PDF file URIs to merge
     * @param outputName display name for the merged PDF
     * @return URI of the merged PDF file, or null on failure
     */
    suspend fun merge(pdfUris: List<Uri>, outputName: String): Uri? = withContext(Dispatchers.IO) {
        if (pdfUris.size < 2) return@withContext null

        val pdfDir = File(
            context.filesDir,
            "pdfs"
        ).also { it.mkdirs() }
        val outId = UUID.randomUUID().toString()
        val outFile = File(pdfDir, "$outId.pdf")
        val newDoc = PdfDocument()

        try {
            var pageIndex = 0
            for (uri in pdfUris) {
                val path = uri.path
                    ?: continue
                val file = File(path)
                if (!file.exists()) continue

                var fd: ParcelFileDescriptor? = null
                var renderer: PdfRenderer? = null
                try {
                    fd = ParcelFileDescriptor.open(
                        file,
                        ParcelFileDescriptor.MODE_READ_ONLY
                    )
                    renderer = PdfRenderer(fd)
                    for (i in 0 until renderer.pageCount) {
                        var srcPage: PdfRenderer.Page? =
                            null
                        try {
                            srcPage = renderer.openPage(i)
                            val w = srcPage.width
                            val h = srcPage.height
                            val bmp = Bitmap.createBitmap(
                                w,
                                h,
                                Bitmap.Config.ARGB_8888
                            )
                            bmp.eraseColor(
                                android.graphics.Color.WHITE
                            )
                            srcPage.render(
                                bmp,
                                null,
                                null,
                                PdfRenderer.Page
                                    .RENDER_MODE_FOR_DISPLAY
                            )
                            srcPage.close()
                            srcPage = null

                            val pageInfo =
                                PdfDocument.PageInfo
                                    .Builder(w, h, pageIndex)
                                    .create()
                            val outPage =
                                newDoc.startPage(pageInfo)
                            outPage.canvas.drawBitmap(
                                bmp,
                                0f,
                                0f,
                                null
                            )
                            newDoc.finishPage(outPage)
                            bmp.recycle()
                            pageIndex++
                        } finally {
                            try {
                                srcPage?.close()
                            } catch (_: Throwable) {
                            }
                        }
                    }
                } finally {
                    try {
                        renderer?.close()
                    } catch (_: Throwable) {
                    }
                    try {
                        fd?.close()
                    } catch (_: Throwable) {
                    }
                }
            }

            if (pageIndex == 0) {
                newDoc.close()
                return@withContext null
            }

            FileOutputStream(outFile).use { fos ->
                newDoc.writeTo(fos)
            }
            newDoc.close()
            Uri.fromFile(outFile)
        } catch (_: Throwable) {
            newDoc.close()
            if (outFile.exists()) outFile.delete()
            null
        }
    }
}
