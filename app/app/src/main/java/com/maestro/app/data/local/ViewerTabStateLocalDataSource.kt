package com.maestro.app.data.local

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PersistedPdfTab(
    @SerialName("document_id")
    val documentId: String,
    val title: String,
    @SerialName("page_count")
    val pageCount: Int,
    @SerialName("uri_string")
    val uriString: String,
    @SerialName("first_visible_page_index")
    val firstVisiblePageIndex: Int = 0,
    @SerialName("first_visible_page_scroll_offset")
    val firstVisiblePageScrollOffset: Int = 0
)

@Serializable
data class PersistedViewerTabState(
    val tabs: List<PersistedPdfTab> = emptyList(),
    @SerialName("active_document_id")
    val activeDocumentId: String? = null
)

class ViewerTabStateLocalDataSource {
    private val file: File
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    constructor(context: Context) : this(
        File(context.filesDir, "viewer_tabs/state.json")
    )

    internal constructor(file: File) {
        this.file = file
        file.parentFile?.mkdirs()
    }

    suspend fun load(): PersistedViewerTabState =
        withContext(Dispatchers.IO) {
            if (!file.exists()) {
                return@withContext PersistedViewerTabState()
            }
            try {
                json.decodeFromString<PersistedViewerTabState>(
                    file.readText()
                )
            } catch (_: Throwable) {
                PersistedViewerTabState()
            }
        }

    suspend fun save(state: PersistedViewerTabState) =
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(state))
        }
}
