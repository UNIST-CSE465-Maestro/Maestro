package com.maestro.app.util

import androidx.compose.ui.graphics.Color
import com.maestro.app.domain.model.ChatMessage
import com.maestro.app.domain.model.ExtractionStatus
import com.maestro.app.domain.model.Folder
import com.maestro.app.domain.model.InkStroke
import com.maestro.app.domain.model.PdfDocument
import com.maestro.app.domain.model.StrokePoint
import java.util.UUID

/**
 * Factory methods for domain models used in tests.
 * Provides sensible defaults — override only what matters for the test.
 */
object TestFixtures {

    fun pdfDocument(
        id: String = UUID.randomUUID().toString(),
        uriString: String = "file:///test/pdfs/$id.pdf",
        displayName: String = "test-document.pdf",
        pageCount: Int = 3,
        folderId: String? = null,
        addedTimestamp: Long = 1000L,
        extractionStatus: ExtractionStatus = ExtractionStatus.NONE,
        extractionMode: String? = null,
        isPinned: Boolean = false,
        bookmarkedPages: Set<Int> = emptySet()
    ) = PdfDocument(
        id = id,
        uriString = uriString,
        displayName = displayName,
        pageCount = pageCount,
        folderId = folderId,
        addedTimestamp = addedTimestamp,
        extractionStatus = extractionStatus,
        extractionMode = extractionMode,
        isPinned = isPinned,
        bookmarkedPages = bookmarkedPages
    )

    fun folder(
        id: String = UUID.randomUUID().toString(),
        name: String = "Test Folder",
        parentId: String? = null,
        createdTimestamp: Long = 1000L
    ) = Folder(
        id = id,
        name = name,
        parentId = parentId,
        createdTimestamp = createdTimestamp
    )

    fun chatMessage(
        role: ChatMessage.Role = ChatMessage.Role.USER,
        content: String = "Hello",
        timestamp: Long = System.currentTimeMillis()
    ) = ChatMessage(
        role = role,
        content = content,
        timestamp = timestamp
    )

    fun userMessage(content: String = "Hello") =
        chatMessage(role = ChatMessage.Role.USER, content = content)

    fun assistantMessage(content: String = "Hi there!") = chatMessage(
        role = ChatMessage.Role.ASSISTANT,
        content = content
    )

    fun strokePoint(
        x: Float = 10f,
        y: Float = 20f,
        pressure: Float = 0.5f,
        isStylusInput: Boolean = false
    ) = StrokePoint(
        x = x,
        y = y,
        pressure = pressure,
        isStylusInput = isStylusInput
    )

    fun inkStroke(
        points: List<StrokePoint> = listOf(
            strokePoint(0f, 0f),
            strokePoint(10f, 10f),
            strokePoint(20f, 20f)
        ),
        color: Color = Color.Black,
        baseWidth: Float = 3f
    ) = InkStroke(
        points = points,
        color = color,
        baseWidth = baseWidth
    )

    fun strokeList(count: Int = 3): List<InkStroke> = (0 until count).map { i ->
        inkStroke(
            points = listOf(
                strokePoint(i * 10f, i * 10f),
                strokePoint(i * 10f + 5f, i * 10f + 5f),
                strokePoint(i * 10f + 10f, i * 10f + 10f)
            )
        )
    }
}
