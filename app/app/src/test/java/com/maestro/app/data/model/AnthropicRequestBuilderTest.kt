package com.maestro.app.data.model

import com.maestro.app.domain.model.ChatMessage
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AnthropicRequestBuilderTest {

    @Test
    fun `build creates correct structure for text messages`() {
        val messages = listOf(
            ChatMessage(ChatMessage.Role.USER, "Hello"),
            ChatMessage(ChatMessage.Role.ASSISTANT, "Hi"),
            ChatMessage(ChatMessage.Role.USER, "How are you?")
        )

        val result = AnthropicRequestBuilder.build(
            messages = messages,
            systemPrompt = "You are helpful.",
            stream = true
        )

        assertEquals(
            AnthropicRequestBuilder.DEFAULT_MODEL,
            result["model"]?.jsonPrimitive?.content
        )
        assertEquals(
            "true",
            result["stream"]?.jsonPrimitive?.content
        )
        assertEquals(
            "You are helpful.",
            result["system"]?.jsonPrimitive?.content
        )

        val msgs = result["messages"]?.jsonArray
        assertNotNull(msgs)
        assertEquals(3, msgs!!.size)
        assertEquals(
            "user",
            msgs[0].jsonObject["role"]?.jsonPrimitive?.content
        )
        assertEquals(
            "Hello",
            msgs[0].jsonObject["content"]?.jsonPrimitive?.content
        )
    }

    @Test
    fun `build omits system prompt when null`() {
        val messages = listOf(
            ChatMessage(ChatMessage.Role.USER, "Hi")
        )

        val result = AnthropicRequestBuilder.build(
            messages = messages,
            systemPrompt = null
        )

        assertNull(result["system"])
    }

    @Test
    fun `build attaches images to last user message`() {
        val messages = listOf(
            ChatMessage(ChatMessage.Role.USER, "First"),
            ChatMessage(ChatMessage.Role.ASSISTANT, "Ok"),
            ChatMessage(ChatMessage.Role.USER, "Describe this")
        )
        val fakeImage = byteArrayOf(1, 2, 3)

        val result = AnthropicRequestBuilder.build(
            messages = messages,
            images = listOf(fakeImage)
        )

        val msgs = result["messages"]!!.jsonArray

        // First user message: plain text
        val first = msgs[0].jsonObject["content"]
        assertNotNull(first?.jsonPrimitive)

        // Last user message: array with image + text
        val last = msgs[2].jsonObject["content"]
        val parts = last!!.jsonArray
        assertEquals(2, parts.size)

        // First part is image
        assertEquals(
            "image",
            parts[0].jsonObject["type"]?.jsonPrimitive?.content
        )
        // Second part is text
        assertEquals(
            "text",
            parts[1].jsonObject["type"]?.jsonPrimitive?.content
        )
        assertEquals(
            "Describe this",
            parts[1].jsonObject["text"]?.jsonPrimitive?.content
        )
    }

    @Test
    fun `build with stream false`() {
        val result = AnthropicRequestBuilder.build(
            messages = listOf(
                ChatMessage(ChatMessage.Role.USER, "Hi")
            ),
            stream = false
        )

        assertEquals(
            "false",
            result["stream"]?.jsonPrimitive?.content
        )
    }

    @Test
    fun `buildValidation creates minimal request`() {
        val result = AnthropicRequestBuilder.buildValidation(
            "sk-test"
        )

        assertEquals(
            AnthropicRequestBuilder.VALIDATION_MODEL,
            result["model"]?.jsonPrimitive?.content
        )
        assertEquals(
            "1",
            result["max_tokens"]?.jsonPrimitive?.content
        )
        assertEquals(
            1,
            result["messages"]?.jsonArray?.size
        )
    }
}
