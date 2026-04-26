package com.maestro.app.data.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.maestro.app.data.local.ExtractionProgressStore
import com.maestro.app.data.remote.MaterialAnalyzerClient
import com.maestro.app.data.remote.MaterialAnalyzerHash
import com.maestro.app.data.remote.ServerException
import com.maestro.app.domain.model.ExtractionStatus
import com.maestro.app.domain.model.PdfDocument
import com.maestro.app.domain.repository.DocumentRepository
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext

class PdfExtractionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val documentId = inputData.getString(KEY_DOCUMENT_ID)
            ?: return Result.failure()
        val uriString = inputData.getString(KEY_URI_STRING)
            ?: return Result.failure()
        val mode = inputData.getString(KEY_MODE)
            ?: return Result.failure()

        val koin = GlobalContext.get()
        val repository = koin.get<DocumentRepository>()
        val analyzerClient = koin.get<MaterialAnalyzerClient>()
        val progressStore = koin.get<ExtractionProgressStore>()
        val uri = Uri.parse(uriString)

        return try {
            progressStore.update(documentId, 1)
            val doc = latestDocument(repository, documentId)
                ?: return Result.failure()
            repository.updateDocument(
                doc.copy(
                    extractionStatus = ExtractionStatus.EXTRACTING,
                    extractionMode = mode
                )
            )
            progressStore.update(documentId, 8)
            val hash = MaterialAnalyzerHash.compute(
                applicationContext,
                uri,
                mode
            )
            progressStore.update(documentId, 22)
            val task = analyzerClient.upload(
                uri,
                mode,
                hash
            )
            progressStore.update(documentId, 32)
            pollUntilComplete(
                analyzerClient,
                progressStore,
                documentId,
                task.id
            )
            progressStore.update(documentId, 90)
            val md = analyzerClient.getResultMd(task.id)
            progressStore.update(documentId, 94)
            val json = analyzerClient.getResultJson(task.id)
            progressStore.update(documentId, 97)
            saveContent(documentId, md, json)
            progressStore.update(documentId, 100)
            latestDocument(repository, documentId)?.let { latest ->
                repository.updateDocument(
                    latest.copy(
                        extractionStatus = ExtractionStatus.DONE,
                        extractionMode = null
                    )
                )
            }
            delay(700L)
            progressStore.clear(documentId)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (runAttemptCount < MAX_RETRIES &&
                isRetryable(e)
            ) {
                Result.retry()
            } else {
                latestDocument(repository, documentId)?.let { latest ->
                    repository.updateDocument(
                        latest.copy(
                            extractionStatus = ExtractionStatus.FAILED,
                            extractionMode = mode
                        )
                    )
                }
                progressStore.clear(documentId)
                Result.failure()
            }
        }
    }

    private suspend fun pollUntilComplete(
        analyzerClient: MaterialAnalyzerClient,
        progressStore: ExtractionProgressStore,
        documentId: String,
        taskId: String
    ) {
        var estimate = 32
        while (true) {
            val task = analyzerClient.pollOnce(taskId)
            if (task.status == "completed") return
            if (task.status == "failed") {
                throw ServerException(500, "Analysis failed")
            }
            estimate = (estimate + 4).coerceAtMost(88)
            progressStore.update(documentId, estimate)
            delay(5000L)
        }
    }

    private suspend fun latestDocument(
        repository: DocumentRepository,
        documentId: String
    ): PdfDocument? =
        repository.loadDocuments()
            .find { it.id == documentId }

    private suspend fun saveContent(
        documentId: String,
        md: String,
        json: String
    ) = withContext(Dispatchers.IO) {
        val dir = File(
            applicationContext.filesDir,
            "documents/$documentId"
        )
        dir.mkdirs()
        File(dir, "content.md").writeText(md)
        File(dir, "content.json").writeText(json)
    }

    private fun isRetryable(error: Throwable): Boolean {
        return error !is ServerException ||
            error.code == 408 ||
            error.code == 429 ||
            error.code >= 500
    }

    companion object {
        const val KEY_DOCUMENT_ID = "document_id"
        const val KEY_URI_STRING = "uri_string"
        const val KEY_MODE = "mode"
        private const val MAX_RETRIES = 2

        fun uniqueWorkName(documentId: String): String =
            "pdf_extraction_$documentId"
    }
}
