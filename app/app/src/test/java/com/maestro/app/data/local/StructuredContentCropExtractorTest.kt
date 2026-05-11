package com.maestro.app.data.local

import com.maestro.app.domain.model.CropCapturePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredContentCropExtractorTest {
    @Test
    fun `extract returns only blocks intersecting crop area`() {
        val result = StructuredContentCropExtractor.extract(
            rawJson = """
                {
                  "pdf_info": [
                    {
                      "page_idx": 0,
                      "page_size": [100, 100],
                      "para_blocks": [
                        {
                          "bbox": [10, 10, 40, 30],
                          "lines": [
                            {
                              "spans": [
                                { "content": "Inside concept" }
                              ]
                            }
                          ]
                        },
                        {
                          "bbox": [70, 70, 95, 95],
                          "lines": [
                            {
                              "spans": [
                                { "content": "Outside concept" }
                              ]
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent(),
            payload = CropCapturePayload(
                imageBytes = byteArrayOf(),
                pageIndex = 0,
                left = 5f,
                top = 5f,
                right = 45f,
                bottom = 35f,
                pageRefWidth = 100f,
                pageRefHeight = 100f
            )
        )

        assertEquals(1, result.matchedBlockCount)
        assertTrue(result.content.contains("Inside concept"))
        assertFalse(result.content.contains("Outside concept"))
        assertTrue(
            result.label.startsWith(
                "선택 영역: Inside concept"
            )
        )
    }

    @Test
    fun `extract supports flat block array content json`() {
        val result = StructuredContentCropExtractor.extract(
            rawJson = """
                [
                  {
                    "type": "text",
                    "text": "A Running Example across the Lecture",
                    "bbox": [42, 85, 644, 151],
                    "page_idx": 6
                  },
                  {
                    "type": "list",
                    "list_items": [
                      "User asks for help",
                      "LLM explains the situation in text"
                    ],
                    "bbox": [44, 170, 647, 490],
                    "page_idx": 6
                  },
                  {
                    "type": "page_number",
                    "text": "7",
                    "bbox": [941, 940, 953, 962],
                    "page_idx": 6
                  }
                ]
            """.trimIndent(),
            payload = CropCapturePayload(
                imageBytes = byteArrayOf(),
                pageIndex = 6,
                left = 30f,
                top = 70f,
                right = 700f,
                bottom = 520f,
                pageRefWidth = 1000f,
                pageRefHeight = 1000f
            )
        )

        assertEquals(2, result.matchedBlockCount)
        assertTrue(
            result.content.contains(
                "A Running Example across the Lecture"
            )
        )
        assertTrue(
            result.content.contains(
                "LLM explains the situation in text"
            )
        )
        assertFalse(result.content.contains("7"))
        assertTrue(
            result.label.startsWith(
                "선택 영역: A Running Example"
            )
        )
    }
}
