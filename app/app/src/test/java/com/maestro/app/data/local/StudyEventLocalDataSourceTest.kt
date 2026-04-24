package com.maestro.app.data.local

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyEventLocalDataSourceTest {
    @Test
    fun `append and list events preserves metadata`() {
        val file = tempFile()
        val dataSource = StudyEventLocalDataSource(file)

        dataSource.append(
            type = StudyEventType.LLM_REQUESTED,
            documentId = "doc-1",
            pageIndex = 2,
            promptLength = 42,
            metadata = mapOf("hasImage" to "false"),
            timestamp = 1000L
        )

        val events = dataSource.listEvents()
        assertEquals(1, events.size)
        assertEquals(StudyEventType.LLM_REQUESTED, events[0].type)
        assertEquals("doc-1", events[0].documentId)
        assertEquals(2, events[0].pageIndex)
        assertEquals(42, events[0].promptLength)
        assertEquals("false", events[0].metadata["hasImage"])
    }

    @Test
    fun `list events returns empty for corrupt json`() {
        val file = tempFile()
        file.parentFile?.mkdirs()
        file.writeText("{ not valid json")
        val dataSource = StudyEventLocalDataSource(file)

        assertTrue(dataSource.listEvents().isEmpty())
    }

    private fun tempFile(): File =
        File(
            System.getProperty("java.io.tmpdir"),
            "maestro-study-events-${System.nanoTime()}.json"
        )
}
