package com.maestro.app.data.remote

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Computes SHA256 hash for material analyzer requests.
 * Hash includes file content + mode for cache key.
 *
 * Formula: SHA256(SHA256_hex(file_bytes) + mode)
 */
object MaterialAnalyzerHash {

    /**
     * Compute SHA256(fileHash + mode) in chunks.
     * Safe for large files (500MB+).
     */
    suspend fun compute(context: Context, uri: Uri, mode: String): String =
        withContext(Dispatchers.IO) {
            val fileDigest =
                MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)
                ?.use { stream ->
                    hashStream(fileDigest, stream)
                }
                ?: throw IllegalStateException(
                    "Cannot open $uri"
                )
            val fileHash =
                fileDigest.digest().joinToString("") {
                    "%02x".format(it)
                }
            val finalDigest =
                MessageDigest.getInstance("SHA-256")
            finalDigest.update(
                (fileHash + mode).toByteArray()
            )
            finalDigest.digest().joinToString("") {
                "%02x".format(it)
            }
        }

    private fun hashStream(digest: MessageDigest, stream: InputStream) {
        val buffer = ByteArray(8192)
        var read: Int
        while (stream.read(buffer).also {
                read = it
            } != -1
        ) {
            digest.update(buffer, 0, read)
        }
    }
}

/**
 * Client for the Maestro material-analyzer server.
 * Handles upload, polling, and result retrieval.
 */
class MaterialAnalyzerClient(
    private val api: MaestroServerApi,
    private val context: Context
) {

    suspend fun upload(uri: Uri, mode: String, sha256: String): AnalysisTaskResponse =
        withContext(Dispatchers.IO) {
            val inputStream =
                context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException(
                        "Cannot open $uri"
                    )
            val bytes = inputStream.use { it.readBytes() }
            val fileName = getFileName(uri)
            val filePart = MultipartBody.Part.createFormData(
                "file",
                fileName,
                bytes.toRequestBody(
                    "application/pdf".toMediaType()
                )
            )
            val sha256Body = sha256.toRequestBody(
                "text/plain".toMediaType()
            )
            val modeBody = mode.toRequestBody(
                "text/plain".toMediaType()
            )
            val resp = api.uploadPdf(
                filePart,
                sha256Body,
                modeBody
            )
            if (!resp.isSuccessful) {
                throw ServerException(
                    resp.code(),
                    resp.errorBody()?.string() ?: ""
                )
            }
            resp.body() ?: throw ServerException(
                resp.code(),
                "Empty response body"
            )
        }

    suspend fun pollUntilComplete(taskId: String, intervalMs: Long = 5000L): AnalysisTaskResponse {
        while (true) {
            val task = pollOnce(taskId)
            if (task.status == "completed") return task
            if (task.status == "failed") {
                throw ServerException(
                    500,
                    "Analysis failed"
                )
            }
            delay(intervalMs)
        }
    }

    suspend fun pollOnce(taskId: String): AnalysisTaskResponse {
        val resp = api.getTaskStatus(taskId)
        if (!resp.isSuccessful) {
            throw ServerException(
                resp.code(),
                resp.errorBody()?.string() ?: ""
            )
        }
        return resp.body()
            ?: throw ServerException(
                resp.code(),
                "Empty response body"
            )
    }

    suspend fun getResultMd(taskId: String): String = withContext(Dispatchers.IO) {
        val resp = api.getResultMd(taskId)
        if (!resp.isSuccessful) {
            throw ServerException(
                resp.code(),
                resp.errorBody()?.string() ?: ""
            )
        }
        resp.body()?.string() ?: ""
    }

    suspend fun getResultJson(taskId: String): String = withContext(Dispatchers.IO) {
        val resp = api.getResultJson(taskId)
        if (!resp.isSuccessful) {
            throw ServerException(
                resp.code(),
                resp.errorBody()?.string() ?: ""
            )
        }
        resp.body()?.string() ?: ""
    }

    private fun getFileName(uri: Uri): String {
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(
                android.provider.OpenableColumns.DISPLAY_NAME
            ),
            null,
            null,
            null
        )
        return cursor?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "document.pdf"
    }
}

class ServerException(
    val code: Int,
    override val message: String
) : Exception("Server error $code: $message")
