package com.maestro.app.data.local

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfTextIndexLocalDataSourceTest {

    @Test
    fun `search returns word bounding boxes from stored index`() {
        val source = PdfTextIndexLocalDataSource(
            File(
                System.getProperty("java.io.tmpdir"),
                "maestro-text-index-test-${System.nanoTime()}"
            )
        )
        val index = sampleIndex()

        val matches = source.search(index, "covariate")

        assertEquals(1, matches.size)
        assertEquals(0, matches.first().pageIndex)
        assertEquals(10f, matches.first().left)
        assertEquals(80f, matches.first().right)
        assertEquals("Covariate", matches.first().matchedText)
    }

    @Test
    fun `search returns phrase union bounding box`() {
        val source = PdfTextIndexLocalDataSource(
            File(
                System.getProperty("java.io.tmpdir"),
                "maestro-text-index-test-${System.nanoTime()}"
            )
        )
        val index = sampleIndex()

        val matches = source.search(index, "domain shift")

        assertEquals(1, matches.size)
        assertEquals(86f, matches.first().left)
        assertEquals(162f, matches.first().right)
        assertTrue(matches.first().matchedText.contains("Domain"))
    }

    @Test
    fun `search ignores apostrophe differences`() {
        val source = PdfTextIndexLocalDataSource(
            File(
                System.getProperty("java.io.tmpdir"),
                "maestro-text-index-test-${System.nanoTime()}"
            )
        )
        val index = sampleIndex()

        val matches = source.search(index, "todays")

        assertEquals(1, matches.size)
        assertEquals("Today's", matches.first().matchedText)
    }

    @Test
    fun `search highlights only matched segment inside merged text run`() {
        val source = PdfTextIndexLocalDataSource(
            File(
                System.getProperty("java.io.tmpdir"),
                "maestro-text-index-test-${System.nanoTime()}"
            )
        )
        val index = sampleIndex()

        val matches = source.search(index, "having")

        assertEquals(1, matches.size)
        assertEquals("Having", matches.first().matchedText)
        assertEquals(10f, matches.first().left)
        assertTrue(matches.first().right < 90f)
    }

    private fun sampleIndex(): PdfTextIndex =
        PdfTextIndex(
            documentId = "doc",
            sourceName = "sample.pdf",
            sourceLength = 100L,
            sourceModifiedAt = 1L,
            pages = listOf(
                PdfTextIndexPage(
                    pageIndex = 0,
                    width = 300f,
                    height = 400f,
                    words = listOf(
                        PdfTextIndexWord(
                            text = "Covariate",
                            left = 10f,
                            top = 20f,
                            right = 80f,
                            bottom = 34f
                        ),
                        PdfTextIndexWord(
                            text = "Domain",
                            left = 86f,
                            top = 20f,
                            right = 130f,
                            bottom = 34f
                        ),
                        PdfTextIndexWord(
                            text = "shift",
                            left = 134f,
                            top = 20f,
                            right = 162f,
                            bottom = 34f
                        ),
                        PdfTextIndexWord(
                            text = "Today's",
                            left = 10f,
                            top = 44f,
                            right = 70f,
                            bottom = 58f
                        ),
                        PdfTextIndexWord(
                            text = "Having works",
                            left = 10f,
                            top = 68f,
                            right = 130f,
                            bottom = 82f
                        )
                    )
                )
            )
        )
}
