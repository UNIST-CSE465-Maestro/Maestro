package com.maestro.app.data.remote

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Computes SHA256 hash for material analyzer requests.
 * Hash includes file content + mode for cache differentiation.
 *
 * Formula: SHA256(file_bytes + "|" + mode)
 */
object MaterialAnalyzerHash {

    /**
     * Compute SHA256(file + "|" + mode) in chunks.
     * Safe for large files (500MB+).
     */
    suspend fun compute(
        context: Context,
        uri: Uri,
        mode: String
    ): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)
            ?.use { stream -> hashStream(digest, stream) }
            ?: throw IllegalStateException(
                "Cannot open $uri"
            )
        digest.update("|$mode".toByteArray())
        digest.digest().joinToString("") {
            "%02x".format(it)
        }
    }

    private fun hashStream(
        digest: MessageDigest,
        stream: InputStream
    ) {
        val buffer = ByteArray(8192)
        var read: Int
        while (stream.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
    }
}
