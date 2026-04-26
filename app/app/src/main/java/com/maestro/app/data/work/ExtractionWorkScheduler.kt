package com.maestro.app.data.work

interface ExtractionWorkScheduler {
    fun enqueue(
        documentId: String,
        uriString: String,
        mode: String,
        replaceExisting: Boolean
    )
}
