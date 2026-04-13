package com.maestro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maestro.app.data.local.ConversationLocalDataSource
import com.maestro.app.domain.model.ChatMessage
import com.maestro.app.domain.repository.SettingsRepository
import com.maestro.app.domain.service.LlmService
import com.maestro.app.ui.config.UxConfig
import kotlinx.coroutines.launch

private const val DEFAULT_SYSTEM_PROMPT =
    "You are a helpful AI assistant integrated into " +
        "Maestro, a PDF annotation app. Help the user " +
        "understand and work with their documents."

@Composable
fun LlmSidebar(
    isVisible: Boolean,
    onCollapse: () -> Unit,
    llmService: LlmService,
    settingsRepository: SettingsRepository,
    conversationDataSource: ConversationLocalDataSource
) {
    if (!isVisible) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val minWidthPx = with(density) {
        UxConfig.Viewer.SIDEBAR_MIN_WIDTH.toPx()
    }
    val maxWidthPx = with(density) {
        UxConfig.Viewer.SIDEBAR_MAX_WIDTH.toPx()
    }
    val defaultWidthPx = with(density) {
        UxConfig.Viewer.SIDEBAR_DEFAULT_WIDTH.toPx()
    }

    var widthPx by remember { mutableStateOf(defaultWidthPx) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var currentInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var conversationId by remember {
        mutableStateOf<String?>(null)
    }
    val listState = rememberLazyListState()

    LaunchedEffect(conversationId) {
        val id = conversationId ?: return@LaunchedEffect
        val loaded = conversationDataSource.loadMessages(id)
        messages.clear()
        messages.addAll(loaded)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(
                messages.size - 1
            )
        }
    }

    val widthDp = with(density) { widthPx.toDp() }

    Row(
        modifier = Modifier
            .width(widthDp)
            .fillMaxHeight()
    ) {
        // Drag handle on the left edge
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(
                    MaterialTheme.colorScheme.outlineVariant
                )
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, dx ->
                        widthPx = (widthPx - dx)
                            .coerceIn(minWidthPx, maxWidthPx)
                    }
                }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // Top bar
            SidebarTopBar(
                onCollapse = onCollapse,
                onNewConversation = {
                    conversationId = null
                    messages.clear()
                    error = null
                    currentInput = ""
                }
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme
                    .outlineVariant
            )

            // Message list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement
                    .spacedBy(8.dp)
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                items(messages) { msg ->
                    MessageBubble(message = msg)
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            // Error display
            if (error != null) {
                Text(
                    text = error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 4.dp
                    )
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme
                    .outlineVariant
            )

            // Input area
            SidebarInput(
                value = currentInput,
                onValueChange = { currentInput = it },
                isLoading = isLoading,
                onSend = {
                    val text = currentInput.trim()
                    if (text.isBlank() || isLoading) return@SidebarInput
                    error = null

                    val userMsg = ChatMessage(
                        role = ChatMessage.Role.USER,
                        content = text
                    )
                    messages.add(userMsg)
                    currentInput = ""

                    scope.launch {
                        val convId = conversationId
                            ?: conversationDataSource.create()
                                .also { conversationId = it }
                        conversationDataSource
                            .appendMessage(convId, userMsg)

                        isLoading = true
                        val assistantMsg = ChatMessage(
                            role = ChatMessage.Role.ASSISTANT,
                            content = ""
                        )
                        messages.add(assistantMsg)
                        val idx = messages.size - 1

                        try {
                            val sb = StringBuilder()
                            llmService.stream(
                                messages = messages
                                    .dropLast(1)
                                    .toList(),
                                systemPrompt =
                                DEFAULT_SYSTEM_PROMPT
                            ).collect { token ->
                                sb.append(token)
                                messages[idx] = messages[idx]
                                    .copy(
                                        content =
                                        sb.toString()
                                    )
                            }
                            conversationDataSource
                                .appendMessage(
                                    convId,
                                    messages[idx]
                                )
                        } catch (e: Exception) {
                            error = e.message
                                ?: "Unknown error"
                            if (messages[idx]
                                    .content.isBlank()
                            ) {
                                messages.removeAt(idx)
                            }
                        } finally {
                            isLoading = false
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun SidebarTopBar(onCollapse: () -> Unit, onNewConversation: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Claude",
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onNewConversation,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "새 대화",
                tint = MaterialTheme.colorScheme
                    .onSurfaceVariant
            )
        }
        IconButton(
            onClick = onCollapse,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "닫기",
                tint = MaterialTheme.colorScheme
                    .onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser =
        message.role == ChatMessage.Role.USER
    val bgColor = if (isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val alignment = if (isUser) {
        Alignment.End
    } else {
        Alignment.Start
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .padding(
                    horizontal = 12.dp,
                    vertical = 8.dp
                )
        ) {
            Text(
                text = message.content,
                color = textColor,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun SidebarInput(
    value: String,
    onValueChange: (String) -> Unit,
    isLoading: Boolean,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    "메시지를 입력하세요...",
                    fontSize = 14.sp
                )
            },
            maxLines = 5,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme
                    .colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme
                    .colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.width(4.dp))
        IconButton(
            onClick = onSend,
            enabled = !isLoading &&
                value.isNotBlank()
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "전송",
                tint = if (!isLoading &&
                    value.isNotBlank()
                ) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                }
            )
        }
    }
}
