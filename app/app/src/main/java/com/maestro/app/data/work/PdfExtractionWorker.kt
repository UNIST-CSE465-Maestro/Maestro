package com.maestro.app.data.work

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.maestro.app.BuildConfig
import com.maestro.app.data.local.ExtractionComparisonWriter
import com.maestro.app.data.local.ExtractionProgressStore
import com.maestro.app.data.local.ExtractionSourceStatus
import com.maestro.app.data.local.LocalMlKitContentExtractor
import com.maestro.app.data.local.ParsedContentNormalizer
import com.maestro.app.data.local.PdfTextIndexLocalDataSource
import com.maestro.app.data.local.TextLayerContentBuilder
import com.maestro.app.data.remote.MaterialAnalyzerClient
import com.maestro.app.data.remote.MaterialAnalyzerHash
import com.maestro.app.data.remote.ServerException
import com.maestro.app.domain.model.ExtractionStatus
import com.maestro.app.domain.model.PdfDocument
import com.maestro.app.domain.repository.DocumentRepository
import com.maestro.app.domain.repository.SettingsRepository
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
            if (mode == MODE_LOCAL_MLKIT ||
                mode == MODE_COMPARE_MLKIT_MINERU
            ) {
                progressStore.updateSource(
                    documentId = documentId,
                    sourceId = SOURCE_MLKIT,
                    label = LABEL_MLKIT,
                    progress = 1
                )
                val localOnly = mode == MODE_LOCAL_MLKIT
                if (localOnly) {
                    runLocalMlKitExtraction(
                        documentId = documentId,
                        uriString = uriString,
                        progressStore = progressStore,
                        updateProgress = true
                    )
                    progressStore.update(documentId, 100)
                    markDone(
                        repository,
                        documentId,
                        EXTRACTION_SOURCE_MLKIT
                    )
                    delay(700L)
                    progressStore.clear(documentId)
                    return Result.success()
                }
            }
            val compareMode =
                mode == MODE_COMPARE_MLKIT_MINERU
            var localError: Throwable? = null
            if (compareMode) {
                localError = runCatching {
                    runLocalMlKitExtraction(
                        documentId = documentId,
                        uriString = uriString,
                        progressStore = progressStore,
                        updateProgress = false
                    )
                }.exceptionOrNull()
                if (localError != null) {
                    Log.e(
                        TAG,
                        "ML Kit extraction failed document=$documentId",
                        localError
                    )
                    progressStore.markSourceFailed(
                        documentId,
                        SOURCE_MLKIT,
                        LABEL_MLKIT,
                        localError.message
                    )
                }
            }
            if (shouldPreferTextLayer(mode) &&
                trySaveTextLayerContent(
                    document = doc,
                    uriString = uriString,
                    progressStore = progressStore,
                    repository = repository
                )
            ) {
                delay(700L)
                progressStore.clear(documentId)
                return Result.success()
            }
            mineruMutex.withLock {
            ensureMineruTokenIfNeeded(
                documentId = documentId,
                mode = mode,
                progressStore = progressStore
            )
            val analyzerClient = koin.get<MaterialAnalyzerClient>()
            progressStore.updateSource(
                documentId = documentId,
                sourceId = SOURCE_MINERU,
                label = LABEL_MINERU,
                progress = 8
            )
            val serverMode = if (compareMode || mode == MODE_AUTO) {
                MODE_AI
            } else {
                mode
            }
            val hash = MaterialAnalyzerHash.compute(
                applicationContext,
                uri,
                serverMode
            )
            val savedTask = loadStoredTask(documentId)
            if (savedTask != null &&
                savedTask.sha256 == hash &&
                savedTask.serverMode == serverMode
            ) {
                Log.i(
                    TAG,
                    "Resuming stored task document=$documentId task=${savedTask.taskId}"
                )
                val resumeProgress = savedTask.progress
                    .coerceIn(32, 88)
                progressStore.update(documentId, resumeProgress)
                progressStore.updateSource(
                    documentId = documentId,
                    sourceId = SOURCE_MINERU,
                    label = LABEL_MINERU,
                    progress = resumeProgress
                )
                try {
                    completeMineruTask(
                        analyzerClient = analyzerClient,
                        progressStore = progressStore,
                        repository = repository,
                        documentId = documentId,
                        mode = mode,
                        serverMode = serverMode,
                        sha256 = hash,
                        taskId = savedTask.taskId,
                        initialProgress = resumeProgress,
                        createdAt = savedTask.createdAt
                    )
                    delay(700L)
                    progressStore.clear(documentId)
                    return Result.success()
                } catch (e: ServerException) {
                    if (e.code == 404 || e.code == 410) {
                        deleteStoredTask(documentId)
                    } else {
                        throw e
                    }
                }
            } else if (savedTask != null) {
                deleteStoredTask(documentId)
            }
            progressStore.update(documentId, 22)
            progressStore.updateSource(
                documentId = documentId,
                sourceId = SOURCE_MINERU,
                label = LABEL_MINERU,
                progress = 22
            )
            val task = analyzerClient.upload(
                uri,
                serverMode,
                hash
            )
            saveStoredTask(
                StoredExtractionTask(
                    taskId = task.id,
                    requestedMode = mode,
                    serverMode = serverMode,
                    sha256 = hash,
                    status = task.status.ifBlank {
                        "queued"
                    },
                    progress = 32,
                    createdAt = System.currentTimeMillis(),
                    lastPolledAt = System.currentTimeMillis()
                ),
                documentId
            )
            progressStore.update(documentId, 32)
            progressStore.updateSource(
                documentId = documentId,
                sourceId = SOURCE_MINERU,
                label = LABEL_MINERU,
                progress = 32
            )
            completeMineruTask(
                analyzerClient = analyzerClient,
                progressStore = progressStore,
                repository = repository,
                documentId = documentId,
                mode = mode,
                serverMode = serverMode,
                sha256 = hash,
                taskId = task.id,
                initialProgress = 32,
                createdAt = System.currentTimeMillis()
            )
            }
            delay(700L)
            progressStore.clear(documentId)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(
                TAG,
                "Extraction failed document=$documentId mode=$mode",
                e
            )
            if (isFailedServerTask(e)) {
                deleteStoredTask(documentId)
            }
            saveExtractionError(
                documentId = documentId,
                mode = mode,
                error = e
            )
            if (runAttemptCount < MAX_RETRIES &&
                isRetryable(e)
            ) {
                Result.retry()
            } else {
                markFailureSource(
                    progressStore = progressStore,
                    documentId = documentId,
                    mode = mode,
                    error = e.message
                )
                latestDocument(repository, documentId)?.let { latest ->
                    repository.updateDocument(
                        latest.copy(
                            extractionStatus = ExtractionStatus.FAILED,
                            extractionMode = mode
                        )
                    )
                }
                delay(1200L)
                progressStore.clear(documentId)
                Result.failure()
            }
        }
    }

    private suspend fun ensureMineruTokenIfNeeded(
        documentId: String,
        mode: String,
        progressStore: ExtractionProgressStore
    ) {
        if (!requiresMineru(mode)) return
        val settings = GlobalContext.get()
            .get<SettingsRepository>()
        val accessToken = settings.getAccessToken()
            .firstOrNull()
        val bundledToken =
            BuildConfig.MAESTRO_SERVER_BEARER_TOKEN
        if (!accessToken.isNullOrBlank() ||
            bundledToken.isNotBlank()
        ) {
            return
        }

        progressStore.updateSource(
            documentId = documentId,
            sourceId = SOURCE_MINERU,
            label = LABEL_MINERU,
            progress = 0,
            status = ExtractionSourceStatus.FAILED,
            error = MINERU_TOKEN_REQUIRED_MESSAGE
        )
        throw ServerException(
            401,
            MINERU_TOKEN_REQUIRED_MESSAGE
        )
    }

    private fun shouldPreferTextLayer(mode: String): Boolean =
        mode == MODE_STANDARD || mode == MODE_AI || mode == MODE_AUTO

    private suspend fun trySaveTextLayerContent(
        document: PdfDocument,
        uriString: String,
        progressStore: ExtractionProgressStore,
        repository: DocumentRepository
    ): Boolean = withContext(Dispatchers.IO) {
        val pdfFile = Uri.parse(uriString).path
            ?.let(::File)
            ?: return@withContext false
        if (!pdfFile.exists()) return@withContext false
        val koin = GlobalContext.get()
        val textIndex = koin.get<PdfTextIndexLocalDataSource>()
        val builder = koin.get<TextLayerContentBuilder>()
        progressStore.updateSource(
            documentId = document.id,
            sourceId = SOURCE_TEXT_LAYER,
            label = LABEL_TEXT_LAYER,
            progress = 20
        )
        val index = textIndex.ensureIndex(
            documentId = document.id,
            pdfFile = pdfFile,
            displayName = document.displayName
        )
        if (!builder.canUseTextLayer(index)) {
            progressStore.markSourceFailed(
                document.id,
                SOURCE_TEXT_LAYER,
                LABEL_TEXT_LAYER,
                "텍스트 레이어가 없거나 충분하지 않습니다."
            )
            return@withContext false
        }
        val content = builder.build(document.id, index ?: return@withContext false)
        progressStore.update(document.id, 88)
        progressStore.updateSource(
            documentId = document.id,
            sourceId = SOURCE_TEXT_LAYER,
            label = LABEL_TEXT_LAYER,
            progress = 88
        )
        saveContent(document.id, content.markdown, content.json)
        deleteStoredTask(document.id)
        progressStore.update(document.id, 100)
        progressStore.updateSource(
            documentId = document.id,
            sourceId = SOURCE_TEXT_LAYER,
            label = LABEL_TEXT_LAYER,
            progress = 100,
            status = ExtractionSourceStatus.DONE
        )
        markDone(
            repository,
            document.id,
            EXTRACTION_SOURCE_TEXT_LAYER
        )
        true
    }

    private fun requiresMineru(mode: String): Boolean {
        return mode != MODE_LOCAL_MLKIT
    }

    private suspend fun completeMineruTask(
        analyzerClient: MaterialAnalyzerClient,
        progressStore: ExtractionProgressStore,
        repository: DocumentRepository,
        documentId: String,
        mode: String,
        serverMode: String,
        sha256: String,
        taskId: String,
        initialProgress: Int,
        createdAt: Long
    ) {
        pollUntilComplete(
            analyzerClient = analyzerClient,
            progressStore = progressStore,
            documentId = documentId,
            taskId = taskId,
            mode = mode,
            serverMode = serverMode,
            sha256 = sha256,
            initialProgress = initialProgress,
            createdAt = createdAt
        )
        progressStore.update(documentId, 90)
        progressStore.updateSource(
            documentId = documentId,
            sourceId = SOURCE_MINERU,
            label = LABEL_MINERU,
            progress = 90
        )
        val md = analyzerClient.getResultMd(taskId)
        progressStore.update(documentId, 94)
        progressStore.updateSource(
            documentId = documentId,
            sourceId = SOURCE_MINERU,
            label = LABEL_MINERU,
            progress = 94
        )
        val resultJson = ParsedContentNormalizer.normalizeMineruJson(
            documentId = documentId,
            rawJson = analyzerClient.getResultJson(taskId)
        )
        progressStore.update(documentId, 97)
        progressStore.updateSource(
            documentId = documentId,
            sourceId = SOURCE_MINERU,
            label = LABEL_MINERU,
            progress = 97
        )
        saveContent(documentId, md, resultJson)
        writeComparisonIfPossible(documentId)
        deleteStoredTask(documentId)
        progressStore.update(documentId, 100)
        progressStore.updateSource(
            documentId = documentId,
            sourceId = SOURCE_MINERU,
            label = LABEL_MINERU,
            progress = 100,
            status = ExtractionSourceStatus.DONE
        )
        markDone(
            repository,
            documentId,
            EXTRACTION_SOURCE_MINERU
        )
    }

    private suspend fun pollUntilComplete(
        analyzerClient: MaterialAnalyzerClient,
        progressStore: ExtractionProgressStore,
        documentId: String,
        taskId: String,
        mode: String,
        serverMode: String,
        sha256: String,
        initialProgress: Int,
        createdAt: Long
    ) {
        var estimate = initialProgress.coerceIn(32, 88)
        while (true) {
            val task = analyzerClient.pollOnce(taskId)
            if (task.status == "completed") {
                saveStoredTask(
                    StoredExtractionTask(
                        taskId = taskId,
                        requestedMode = mode,
                        serverMode = serverMode,
                        sha256 = sha256,
                        status = task.status,
                        progress = 88,
                        createdAt = createdAt,
                        lastPolledAt = System.currentTimeMillis()
                    ),
                    documentId
                )
                return
            }
            if (task.status == "failed") {
                saveStoredTask(
                    StoredExtractionTask(
                        taskId = taskId,
                        requestedMode = mode,
                        serverMode = serverMode,
                        sha256 = sha256,
                        status = task.status,
                        progress = estimate,
                        createdAt = createdAt,
                        lastPolledAt = System.currentTimeMillis()
                    ),
                    documentId
                )
                throw ServerException(500, "Analysis failed")
            }
            estimate = (estimate + 4).coerceAtMost(88)
            saveStoredTask(
                StoredExtractionTask(
                    taskId = taskId,
                    requestedMode = mode,
                    serverMode = serverMode,
                    sha256 = sha256,
                    status = task.status.ifBlank {
                        "processing"
                    },
                    progress = estimate,
                    createdAt = createdAt,
                    lastPolledAt = System.currentTimeMillis()
                ),
                documentId
            )
            progressStore.update(documentId, estimate)
            progressStore.updateSource(
                documentId = documentId,
                sourceId = SOURCE_MINERU,
                label = LABEL_MINERU,
                progress = estimate
            )
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

    private suspend fun runLocalMlKitExtraction(
        documentId: String,
        uriString: String,
        progressStore: ExtractionProgressStore,
        updateProgress: Boolean
    ) {
        val extractor = GlobalContext.get()
            .get<LocalMlKitContentExtractor>()
        val extraction = extractor.extract(
            documentId = documentId,
            uriString = uriString
        ) { progress ->
            progressStore.updateSource(
                documentId = documentId,
                sourceId = SOURCE_MLKIT,
                label = LABEL_MLKIT,
                progress = progress
            )
            if (updateProgress) {
                progressStore.update(documentId, progress)
            }
        }
        if (updateProgress) {
            progressStore.update(documentId, 96)
        }
        saveLocalMlKitContent(
            documentId = documentId,
            md = extraction.markdown,
            json = extraction.json
        )
        progressStore.updateSource(
            documentId = documentId,
            sourceId = SOURCE_MLKIT,
            label = LABEL_MLKIT,
            progress = 100,
            status = ExtractionSourceStatus.DONE
        )
    }

    private fun markFailureSource(
        progressStore: ExtractionProgressStore,
        documentId: String,
        mode: String,
        error: String?
    ) {
        if (mode == MODE_LOCAL_MLKIT) {
            progressStore.markSourceFailed(
                documentId,
                SOURCE_MLKIT,
                LABEL_MLKIT,
                error
            )
        } else {
            progressStore.markSourceFailed(
                documentId,
                SOURCE_MINERU,
                LABEL_MINERU,
                error
            )
        }
    }

    private suspend fun markDone(
        repository: DocumentRepository,
        documentId: String,
        source: String
    ) {
        latestDocument(repository, documentId)?.let { latest ->
            repository.updateDocument(
                latest.copy(
                    extractionStatus = ExtractionStatus.DONE,
                    extractionMode = null,
                    extractionSource = source
                )
            )
        }
    }

    private suspend fun saveLocalMlKitContent(
        documentId: String,
        md: String,
        json: String
    ) = withContext(Dispatchers.IO) {
        val dir = File(
            applicationContext.filesDir,
            "documents/$documentId"
        )
        dir.mkdirs()
        File(dir, "local_mlkit_content.md").writeText(md)
        File(dir, "local_mlkit_content.json").writeText(json)
    }

    private suspend fun writeComparisonIfPossible(
        documentId: String
    ) = withContext(Dispatchers.IO) {
        ExtractionComparisonWriter.writeIfPossible(
            File(
                applicationContext.filesDir,
                "documents/$documentId"
            )
        )
    }

    private suspend fun saveExtractionError(
        documentId: String,
        mode: String,
        error: Throwable
    ) = withContext(Dispatchers.IO) {
        val dir = File(
            applicationContext.filesDir,
            "documents/$documentId"
        )
        dir.mkdirs()
        File(dir, "extraction_error.txt").writeText(
            buildString {
                appendLine("mode=$mode")
                appendLine("time=${System.currentTimeMillis()}")
                appendLine("type=${error::class.java.name}")
                appendLine("message=${error.message.orEmpty()}")
                appendLine("stacktrace=")
                appendLine(error.stackTraceToString())
            }
        )
    }

    private suspend fun loadStoredTask(
        documentId: String
    ): StoredExtractionTask? = withContext(Dispatchers.IO) {
        val file = storedTaskFile(documentId)
        if (!file.exists() || file.length() == 0L) {
            return@withContext null
        }
        runCatching {
            extractionTaskJson.decodeFromString<StoredExtractionTask>(
                file.readText()
            )
        }.getOrNull()
    }

    private suspend fun saveStoredTask(
        task: StoredExtractionTask,
        documentId: String
    ) = withContext(Dispatchers.IO) {
        val file = storedTaskFile(documentId)
        file.parentFile?.mkdirs()
        file.writeText(
            extractionTaskJson.encodeToString(task)
        )
    }

    private suspend fun deleteStoredTask(
        documentId: String
    ) = withContext(Dispatchers.IO) {
        storedTaskFile(documentId).delete()
    }

    private fun storedTaskFile(documentId: String): File =
        File(
            applicationContext.filesDir,
            "documents/$documentId/extraction_task.json"
        )

    private fun isRetryable(error: Throwable): Boolean {
        if (isFailedServerTask(error)) {
            return false
        }
        return error !is ServerException ||
            error.code == 408 ||
            error.code == 429 ||
            error.code >= 500
    }

    private fun isFailedServerTask(error: Throwable): Boolean {
        return error is ServerException &&
            error.message.contains(
                "\"status\":\"failed\"",
                ignoreCase = true
            )
    }

    companion object {
        const val KEY_DOCUMENT_ID = "document_id"
        const val KEY_URI_STRING = "uri_string"
        const val KEY_MODE = "mode"
        const val MODE_STANDARD = "standard"
        const val MODE_AI = "ai"
        const val MODE_AUTO = "auto"
        const val MODE_LOCAL_MLKIT = "local_mlkit"
        const val MODE_COMPARE_MLKIT_MINERU = "compare_mlkit_mineru"
        private const val SOURCE_MINERU = "mineru"
        private const val SOURCE_MLKIT = "mlkit"
        private const val SOURCE_TEXT_LAYER = "pdf_text_layer"
        private const val LABEL_MINERU = "MinerU"
        private const val LABEL_MLKIT = "ML Kit"
        private const val LABEL_TEXT_LAYER = "PDF Text Layer"
        const val EXTRACTION_SOURCE_MINERU = "mineru"
        const val EXTRACTION_SOURCE_TEXT_LAYER = "pdf_text_layer"
        const val EXTRACTION_SOURCE_MLKIT = "mlkit"
        private const val MAX_RETRIES = 2
        private const val MINERU_TOKEN_REQUIRED_MESSAGE =
            "MinerU 서버 Bearer Token이 필요합니다. 설정에서 토큰을 저장한 뒤 다시 추출해 주세요."
        private val mineruMutex = Mutex()
        private val extractionTaskJson = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

        fun uniqueWorkName(documentId: String): String =
            "pdf_extraction_$documentId"

        private const val TAG = "PdfExtractionWorker"
    }
}

@Serializable
private data class StoredExtractionTask(
    val taskId: String,
    val requestedMode: String,
    val serverMode: String,
    val sha256: String,
    val status: String,
    val progress: Int,
    val createdAt: Long,
    val lastPolledAt: Long
)
