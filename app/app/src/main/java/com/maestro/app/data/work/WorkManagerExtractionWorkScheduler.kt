package com.maestro.app.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

class WorkManagerExtractionWorkScheduler(
    private val context: Context
) : ExtractionWorkScheduler {
    override fun enqueue(
        documentId: String,
        uriString: String,
        mode: String,
        replaceExisting: Boolean
    ) {
        val request = OneTimeWorkRequestBuilder<PdfExtractionWorker>()
            .setInputData(
                workDataOf(
                    PdfExtractionWorker.KEY_DOCUMENT_ID to documentId,
                    PdfExtractionWorker.KEY_URI_STRING to uriString,
                    PdfExtractionWorker.KEY_MODE to mode
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            PdfExtractionWorker.uniqueWorkName(documentId),
            if (replaceExisting) {
                ExistingWorkPolicy.REPLACE
            } else {
                ExistingWorkPolicy.KEEP
            },
            request
        )
    }
}
