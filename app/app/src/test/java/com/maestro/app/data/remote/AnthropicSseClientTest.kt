package com.maestro.app.data.remote

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnthropicSseClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: AnthropicSseClient

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()

        val httpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        client = AnthropicSseClient(
            httpClient,
            baseUrl = server.url("/").toString().trimEnd('/')
        )
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    private fun sseResponse(vararg deltas: String): String {
        val sb = StringBuilder()
        deltas.forEach { delta ->
            sb.appendLine(
                "data: {\"type\":\"content_block_delta\"," +
                    "\"delta\":{\"type\":\"text_delta\"," +
                    "\"text\":\"$delta\"}}"
            )
        }
        sb.appendLine("data: [DONE]")
        return sb.toString()
    }

    private fun minimalRequestBody() = buildJsonObject {
        put("model", "test")
        put("max_tokens", 10)
        put("stream", true)
        put(
            "messages",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", "hi")
                    }
                )
            }
        )
    }

    @Test
    fun `stream collects text deltas`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(
                    "Content-Type",
                    "text/event-stream"
                )
                .setBody(sseResponse("Hello", " world"))
        )

        val tokens = mutableListOf<String>()
        client.stream(
            "sk-test",
            minimalRequestBody()
        ).collect { tokens += it }

        assertEquals(listOf("Hello", " world"), tokens)
    }

    @Test
    fun `stream throws on API error`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody(
                    """{"error":{"message":"Invalid API key"}}"""
                )
        )

        try {
            client.stream(
                "sk-bad",
                minimalRequestBody()
            ).collect {}
            assertTrue("Should have thrown", false)
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("401"))
            assertTrue(e.message!!.contains("Invalid API key"))
        }
    }

    @Test
    fun `stream ignores non-delta events`() = runTest {
        val body = """
            |data: {"type":"message_start","message":{}}
            |data: {"type":"content_block_start","content_block":{}}
            |data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"Only this"}}
            |data: {"type":"message_stop"}
            |data: [DONE]
        """.trimMargin()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(
                    "Content-Type",
                    "text/event-stream"
                )
                .setBody(body)
        )

        val tokens = mutableListOf<String>()
        client.stream(
            "sk-test",
            minimalRequestBody()
        ).collect { tokens += it }

        assertEquals(listOf("Only this"), tokens)
    }

    @Test
    fun `complete extracts full text`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"content":[{"type":"text","text":"Full response"}],"role":"assistant"}"""
                )
        )

        val body = buildJsonObject {
            put("model", "test")
            put("max_tokens", 10)
            put("stream", false)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", "hi")
                        }
                    )
                }
            )
        }

        val result = client.complete("sk-test", body)
        assertEquals("Full response", result)
    }

    @Test
    fun `complete throws on error`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody(
                    """{"error":{"message":"Server error"}}"""
                )
        )

        try {
            client.complete("sk-test", minimalRequestBody())
            assertTrue("Should have thrown", false)
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("500"))
        }
    }

    @Test
    fun `validateKey returns true for 200`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"content":[]}""")
        )

        assertTrue(client.validateKey("sk-valid"))
    }

    @Test
    fun `validateKey returns false for 401`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"bad key"}}""")
        )

        assertFalse(client.validateKey("sk-invalid"))
    }

    @Test
    fun `request includes correct headers`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"content":[]}""")
        )

        client.validateKey("sk-my-key")

        val request = server.takeRequest()
        assertEquals("sk-my-key", request.getHeader("x-api-key"))
        assertEquals(
            "2023-06-01",
            request.getHeader("anthropic-version")
        )
        assertTrue(
            request.getHeader("Content-Type")!!
                .startsWith("application/json")
        )
        assertTrue(request.path!!.endsWith("/v1/messages"))
    }
}
