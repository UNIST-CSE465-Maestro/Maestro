package com.maestro.app.data.local

import android.content.Context
import com.maestro.app.domain.model.ChatMessage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ConversationDto(
    val id: String,
    val created: Long,
    val updated: Long,
    val title: String = "",
    val messages: List<ConversationMessageDto> = emptyList()
)

@Serializable
data class ConversationMessageDto(
    val role: String,
    val content: String,
    val ts: Long = 0L
)

data class ConversationSummary(
    val id: String,
    val title: String,
    val updated: Long
)

class ConversationLocalDataSource(context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private val convDir = File(
        context.filesDir,
        "conversations"
    ).also { it.mkdirs() }

    fun create(): String {
        val id = "conv_" + SimpleDateFormat(
            "yyyyMMdd_HHmmss", Locale.US
        ).format(Date()) +
            "_" + UUID.randomUUID().toString().take(6)
        val dto = ConversationDto(
            id = id,
            created = System.currentTimeMillis(),
            updated = System.currentTimeMillis()
        )
        save(id, dto)
        return id
    }

    fun appendMessage(conversationId: String, message: ChatMessage) {
        val dto = load(conversationId) ?: return
        val msgDto = ConversationMessageDto(
            role = message.role.name.lowercase(),
            content = message.content,
            ts = message.timestamp
        )
        val updated = dto.copy(
            messages = dto.messages + msgDto,
            updated = System.currentTimeMillis(),
            title = if (dto.title.isBlank() &&
                message.role == ChatMessage.Role.USER
            ) {
                message.content.take(50)
            } else {
                dto.title
            }
        )
        save(conversationId, updated)
    }

    fun loadMessages(conversationId: String): List<ChatMessage> {
        val dto = load(conversationId) ?: return emptyList()
        return dto.messages.map { m ->
            ChatMessage(
                role = if (m.role == "assistant") {
                    ChatMessage.Role.ASSISTANT
                } else {
                    ChatMessage.Role.USER
                },
                content = m.content,
                timestamp = m.ts
            )
        }
    }

    fun listConversations(): List<ConversationSummary> {
        return try {
            convDir.listFiles()
                ?.filter { it.extension == "json" }
                ?.mapNotNull { file ->
                    try {
                        val dto = json.decodeFromString<ConversationDto>(
                            file.readText()
                        )
                        ConversationSummary(
                            id = dto.id,
                            title = dto.title.ifBlank { "새 대화" },
                            updated = dto.updated
                        )
                    } catch (_: Throwable) {
                        null
                    }
                }
                ?.sortedByDescending { it.updated }
                ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun deleteConversation(conversationId: String) {
        File(convDir, "$conversationId.json").delete()
    }

    private fun load(id: String): ConversationDto? {
        val file = File(convDir, "$id.json")
        if (!file.exists()) return null
        return try {
            json.decodeFromString<ConversationDto>(
                file.readText()
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun save(id: String, dto: ConversationDto) {
        File(convDir, "$id.json")
            .writeText(json.encodeToString(dto))
    }
}
