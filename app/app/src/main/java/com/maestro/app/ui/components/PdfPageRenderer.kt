package com.maestro.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maestro.app.ui.config.UxConfig
import com.maestro.app.ui.theme.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class PageResult(
    val bitmap: Bitmap,
    val aspectRatio: Float
)

@Composable
fun PdfPageView(uri: Uri, pageIndex: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var result by remember(uri, pageIndex) {
        mutableStateOf<PageResult?>(null)
    }
    var error by remember(uri, pageIndex) {
        mutableStateOf(false)
    }

    LaunchedEffect(uri, pageIndex) {
        result = null
        error = false
        val r = withContext(Dispatchers.IO) {
            renderPage(context, uri, pageIndex)
        }
        result = r
        if (r == null) error = true
    }

    val aspectRatio =
        result?.aspectRatio
            ?: getPageAspectRatio(context, uri, pageIndex)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .shadow(
                UxConfig.Canvas.PAGE_SHADOW_ELEVATION,
                RoundedCornerShape(UxConfig.Canvas.PAGE_CORNER_RADIUS)
            )
            .background(
                MaestroSurfaceContainerLowest,
                RoundedCornerShape(UxConfig.Canvas.PAGE_CORNER_RADIUS)
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            result != null -> {
                Image(
                    bitmap = result!!.bitmap.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.BrokenImage,
                        null,
                        tint = MaestroOutlineVariant,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "페이지 로딩 실패",
                        fontSize = 12.sp,
                        color = Slate500
                    )
                }
            }
            else -> LoadingDots()
        }
    }
}

@Composable
private fun getPageAspectRatio(context: Context, uri: Uri, pageIndex: Int): Float {
    val ratio = remember(uri, pageIndex) {
        try {
            val fd = openPfd(context, uri)
                ?: return@remember 1f / 1.414f
            val renderer = PdfRenderer(fd)
            if (pageIndex >= renderer.pageCount) {
                renderer.close()
                fd.close()
                return@remember 1f / 1.414f
            }
            val page = renderer.openPage(pageIndex)
            val r = page.width.toFloat() /
                page.height.toFloat()
            page.close()
            renderer.close()
            fd.close()
            r
        } catch (_: Throwable) {
            1f / 1.414f
        }
    }
    return ratio
}

private fun renderPage(context: Context, uri: Uri, pageIndex: Int): PageResult? {
    var fd: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    var page: PdfRenderer.Page? = null
    return try {
        fd = openPfd(context, uri) ?: return null
        renderer = PdfRenderer(fd)
        if (pageIndex >= renderer.pageCount) return null
        page = renderer.openPage(pageIndex)

        val maxDim = UxConfig.Canvas.PDF_MAX_RENDER_DIM
        val w = page.width
        val h = page.height
        val scale = if (w > maxDim || h > maxDim) {
            maxDim.toFloat() / maxOf(w, h)
        } else {
            UxConfig.Canvas.PDF_DEFAULT_SCALE
        }
        val bmpW = (w * scale).toInt().coerceAtLeast(1)
        val bmpH = (h * scale).toInt().coerceAtLeast(1)

        val bmp = Bitmap.createBitmap(
            bmpW,
            bmpH,
            Bitmap.Config.ARGB_8888
        )
        bmp.eraseColor(android.graphics.Color.WHITE)
        page.render(
            bmp,
            null,
            null,
            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
        )
        PageResult(bmp, w.toFloat() / h.toFloat())
    } catch (_: Throwable) {
        null
    } finally {
        try {
            page?.close()
        } catch (_: Throwable) {}
        try {
            renderer?.close()
        } catch (_: Throwable) {}
        try {
            fd?.close()
        } catch (_: Throwable) {}
    }
}

private fun openPfd(context: Context, uri: Uri): ParcelFileDescriptor? {
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

@Composable
private fun LoadingDots() {
    val transition =
        rememberInfiniteTransition(label = "dots")
    Row(
        horizontalArrangement = Arrangement.spacedBy(
            UxConfig.Animation.LOADING_DOT_SPACING
        )
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = UxConfig.Animation.LOADING_DOT_INITIAL_ALPHA,
                targetValue = UxConfig.Animation.LOADING_DOT_TARGET_ALPHA,
                animationSpec = infiniteRepeatable(
                    tween(
                        UxConfig.Animation.LOADING_DOT_DURATION_MS,
                        delayMillis = index * UxConfig.Animation.LOADING_DOT_DELAY_MS
                    ),
                    RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                Modifier
                    .size(UxConfig.Animation.LOADING_DOT_SIZE)
                    .background(
                        MaestroPrimary.copy(alpha = alpha),
                        CircleShape
                    )
            )
        }
    }
}
