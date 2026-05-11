package com.maestro.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredContentSearchExtractorTest {
    @Test
    fun `search returns flat block matches with page coordinates`() {
        val matches = StructuredContentSearchExtractor.search(
            rawJson = """
                [
                  {
                    "type": "text",
                    "text": "Covariate shift changes the input distribution",
                    "bbox": [100, 200, 500, 240],
                    "page_idx": 12
                  },
                  {
                    "type": "page_number",
                    "text": "13",
                    "bbox": [940, 940, 950, 960],
                    "page_idx": 12
                  }
                ]
            """.trimIndent(),
            query = "shift"
        )

        assertEquals(1, matches.size)
        assertEquals(12, matches.first().pageIndex)
        assertEquals("shift", matches.first().matchedText)
        assertTrue(matches.first().left >= 100f)
        assertTrue(matches.first().right <= 500f)
    }

    @Test
    fun `search supports pdf info span matches`() {
        val matches = StructuredContentSearchExtractor.search(
            rawJson = """
                {
                  "pdf_info": [
                    {
                      "page_idx": 0,
                      "page_size": [1000, 800],
                      "para_blocks": [
                        {
                          "bbox": [10, 20, 500, 60],
                          "lines": [
                            {
                              "spans": [
                                {
                                  "content": "Concept shift",
                                  "bbox": [20, 25, 220, 50]
                                }
                              ]
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent(),
            query = "concept"
        )

        assertEquals(1, matches.size)
        assertEquals(0, matches.first().pageIndex)
        assertEquals(1000f, matches.first().pageWidth)
        assertEquals(800f, matches.first().pageHeight)
    }
}
