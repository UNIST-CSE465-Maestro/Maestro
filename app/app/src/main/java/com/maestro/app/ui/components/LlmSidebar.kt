package com.maestro.app.ui.components

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maestro.app.data.local.ConversationLocalDataSource
import com.maestro.app.data.local.ConversationSummary
import com.maestro.app.data.model.LlmRequestBuilder
import com.maestro.app.data.remote.ClaudeClient
import com.maestro.app.data.remote.LlmClient
import com.maestro.app.data.remote.OpenAiClient
import com.maestro.app.domain.model.ChatMessage
import com.maestro.app.domain.model.LlmProvider
import com.maestro.app.domain.repository.SettingsRepository
import com.maestro.app.domain.service.LlmService
import com.maestro.app.ui.config.UxConfig
import com.maestro.app.ui.viewer.LlmConnectionState
import kotlinx.coroutines.launch

private fun buildSystemPrompt(documentContent: String?): String {
    val base = "You are a helpful AI assistant " +
        "integrated into Maestro, a PDF " +
        "annotation app. Help the user " +
        "understand and work with their documents. " +
        "Always respond in Korean."
    if (documentContent.isNullOrBlank()) return base
    return "$base\n\n" +
        "The user is currently viewing a document. " +
        "Here is the extracted text content:\n\n" +
        documentContent.take(5000)
}

private data class QueuedLlmRequest(
    val text: String,
    val images: List<ByteArray>
)

@Composable
fun LlmSidebar(
    isVisible: Boolean,
    onCollapse: () -> Unit,
    llmService: LlmService,
    settingsRepository: SettingsRepository,
    conversationDataSource: ConversationLocalDataSource,
    documentContent: String? = null,
    pendingImage: ByteArray? = null,
    pendingPrompt: String? = null,
    llmConnectionState: LlmConnectionState = LlmConnectionState.READY,
    llmConnectionError: String? = null,
    onRetryConnection: () -> Unit = {},
    onLlmRequested: (prompt: String, hasImage: Boolean) -> Unit = { _, _ -> },
    onPendingConsumed: () -> Unit = {}
) {
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

    val savedProviderName by settingsRepository
        .getLlmProvider()
        .collectAsState(initial = null)
    val currentProvider = savedProviderName
        ?: LlmProvider.GEMINI.name
    val geminiKey by settingsRepository
        .getGeminiApiKey()
        .collectAsState(initial = null)
    val openAiKey by settingsRepository
        .getOpenAiApiKey()
        .collectAsState(initial = null)
    val claudeKey by settingsRepository
        .getClaudeApiKey()
        .collectAsState(initial = null)
    val hasApiKey = when (currentProvider) {
        LlmProvider.OPENAI.name ->
            !openAiKey.isNullOrBlank()
        LlmProvider.CLAUDE.name ->
            !claudeKey.isNullOrBlank()
        else -> !geminiKey.isNullOrBlank()
    }
    val savedModel by settingsRepository
        .getLlmModel()
        .collectAsState(initial = null)
    var availableModels by remember {
        mutableStateOf<List<String>>(emptyList())
    }
    var modelsLoading by remember {
        mutableStateOf(false)
    }

    fun defaultModelsFor(provider: String): List<String> {
        return when (provider) {
            LlmProvider.OPENAI.name ->
                listOf(OpenAiClient.DEFAULT_MODEL)
            LlmProvider.CLAUDE.name ->
                listOf(ClaudeClient.DEFAULT_MODEL)
            else -> listOf(
                LlmRequestBuilder.DEFAULT_MODEL
            )
        }
    }

    var widthPx by remember {
        mutableStateOf(defaultWidthPx)
    }
    val messages = remember {
        mutableStateListOf<ChatMessage>()
    }
    var currentInput by remember {
        mutableStateOf("")
    }
    var isLoading by remember {
        mutableStateOf(false)
    }
    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }
    var activeJob by remember {
        mutableStateOf<kotlinx.coroutines.Job?>(null)
    }
    var queuedRequest by remember {
        mutableStateOf<QueuedLlmRequest?>(null)
    }

    LaunchedEffect(currentProvider) {
        availableModels = defaultModelsFor(currentProvider)
        modelsLoading = false
    }

    fun loadModelsIfNeeded() {
        if (!hasApiKey || modelsLoading) return
        scope.launch {
            modelsLoading = true
            try {
                val fetched = llmService.fetchModels()
                if (fetched.isNotEmpty()) {
                    availableModels = fetched
                }
            } catch (e: Exception) {
                errorMessage = "모델 목록 실패: " +
                    "${e.message}"
            } finally {
                modelsLoading = false
            }
        }
    }

    var conversationId by remember {
        mutableStateOf<String?>(null)
    }
    var showHistory by remember {
        mutableStateOf(false)
    }
    var historyList by remember {
        mutableStateOf<List<ConversationSummary>>(
            emptyList()
        )
    }
    val listState = rememberLazyListState()

    // Track pending images for next send
    var pendingImages by remember {
        mutableStateOf<List<ByteArray>>(emptyList())
    }

    LaunchedEffect(conversationId) {
        val id =
            conversationId ?: return@LaunchedEffect
        val loaded =
            conversationDataSource.loadMessages(id)
        messages.clear()
        messages.addAll(loaded)
    }

    val lastMsgContent =
        messages.lastOrNull()?.content?.length ?: 0
    LaunchedEffect(messages.size, lastMsgContent) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(
                messages.size - 1
            )
        }
    }

    // Consume pending crop/quiz prompt
    fun sendMessage(text: String, imgs: List<ByteArray> = emptyList()) {
        activeJob?.cancel()
        isLoading = false
        errorMessage = null
        onLlmRequested(text, imgs.isNotEmpty())
        val userMsg = ChatMessage(
            role = ChatMessage.Role.USER,
            content = text
        )
        messages.add(userMsg)
        activeJob = scope.launch {
            val convId = conversationId
                ?: conversationDataSource
                    .create()
                    .also { conversationId = it }
            conversationDataSource
                .appendMessage(convId, userMsg)
            streamAssistantResponse(
                messages,
                llmService,
                conversationDataSource,
                convId,
                imgs,
                documentContent,
                { errorMessage = it },
                { isLoading = it }
            )
        }
    }

    fun submitMessage(text: String, imgs: List<ByteArray> = emptyList()) {
        when (llmConnectionState) {
            LlmConnectionState.READY -> {
                sendMessage(text, imgs)
            }
            LlmConnectionState.CONNECTING,
            LlmConnectionState.FAILED -> {
                queuedRequest = QueuedLlmRequest(text, imgs)
                errorMessage = null
                if (llmConnectionState ==
                    LlmConnectionState.FAILED
                ) {
                    onRetryConnection()
                }
            }
        }
    }

    LaunchedEffect(llmConnectionState, queuedRequest) {
        val request = queuedRequest
        if (
            llmConnectionState == LlmConnectionState.READY &&
            request != null
        ) {
            queuedRequest = null
            sendMessage(request.text, request.images)
        }
    }

    var lastProcessedPrompt by remember {
        mutableStateOf<String?>(null)
    }
    if (pendingPrompt != null &&
        pendingPrompt != lastProcessedPrompt
    ) {
        lastProcessedPrompt = pendingPrompt
        val imgs = if (pendingImage != null) {
            listOf(pendingImage)
        } else {
            emptyList()
        }
        val text = pendingPrompt
            .replace(
                Regex("\\n<!--\\d+-->$"),
                ""
            )
        onPendingConsumed()
        submitMessage(text, imgs)
    }

    if (!isVisible) return

    val widthDp = with(density) { widthPx.toDp() }

    Row(
        modifier = Modifier
            .width(widthDp)
            .fillMaxHeight()
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(
                    MaterialTheme.colorScheme
                        .outlineVariant
                )
                .pointerInput(Unit) {
                    detectHorizontalDragGestures {
                            _,
                            dx
                        ->
                        widthPx = (widthPx - dx)
                            .coerceIn(
                                minWidthPx,
                                maxWidthPx
                            )
                    }
                }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.surface
                )
        ) {
            SidebarTopBar(
                currentProvider = currentProvider,
                onProviderSelected = { provider ->
                    activeJob?.cancel()
                    activeJob = null
                    conversationId = null
                    messages.clear()
                    errorMessage = null
                    isLoading = false
                    scope.launch {
                        settingsRepository
                            .setLlmProvider(provider)
                        settingsRepository
                            .setLlmModel("")
                    }
                },
                currentModel = savedModel?.ifBlank {
                    null
                } ?: when (currentProvider) {
                    LlmProvider.OPENAI.name ->
                        OpenAiClient.DEFAULT_MODEL
                    LlmProvider.CLAUDE.name ->
                        ClaudeClient.DEFAULT_MODEL
                    else ->
                        LlmRequestBuilder.DEFAULT_MODEL
                },
                availableModels = availableModels,
                modelsLoading = modelsLoading,
                onModelMenuOpened = {
                    loadModelsIfNeeded()
                },
                onModelSelected = { model ->
                    activeJob?.cancel()
                    activeJob = null
                    conversationId = null
                    messages.clear()
                    errorMessage = null
                    isLoading = false
                    scope.launch {
                        settingsRepository
                            .setLlmModel(model)
                    }
                },
                onCollapse = onCollapse,
                onNewConversation = {
                    activeJob?.cancel()
                    activeJob = null
                    conversationId = null
                    messages.clear()
                    errorMessage = null
                    currentInput = ""
                    pendingImages = emptyList()
                    isLoading = false
                    showHistory = false
                },
                onToggleHistory = {
                    showHistory = !showHistory
                    if (showHistory) {
                        historyList =
                            conversationDataSource
                                .listConversations()
                    }
                }
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme
                    .outlineVariant
            )

            if (showHistory) {
                ConversationHistoryPanel(
                    conversations = historyList,
                    currentId = conversationId,
                    onSelect = { summary ->
                        activeJob?.cancel()
                        activeJob = null
                        conversationId = summary.id
                        isLoading = false
                        errorMessage = null
                        showHistory = false
                    },
                    onDelete = { summary ->
                        conversationDataSource
                            .deleteConversation(
                                summary.id
                            )
                        historyList = historyList
                            .filter {
                                it.id != summary.id
                            }
                        if (conversationId ==
                            summary.id
                        ) {
                            conversationId = null
                            messages.clear()
                        }
                    }
                )
            } else if (!hasApiKey) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment =
                        Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp),
                            tint = MaterialTheme
                                .colorScheme
                                .outline
                        )
                        Spacer(Modifier.height(16.dp))
                        val providerName =
                            when (currentProvider) {
                                LlmProvider.OPENAI
                                    .name -> "OpenAI"
                                LlmProvider.CLAUDE
                                    .name -> "Claude"
                                else -> "Gemini"
                            }
                        Text(
                            "$providerName API 키가 " +
                                "설정되지 않았습니다",
                            fontSize = 15.sp,
                            fontWeight = FontWeight
                                .SemiBold,
                            color = MaterialTheme
                                .colorScheme
                                .onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "설정 화면에서 $providerName" +
                                " API 키를 입력해주세요.",
                            fontSize = 13.sp,
                            color = MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                        )
                    }
                }
            } else {
                if (
                    llmConnectionState != LlmConnectionState.READY ||
                    queuedRequest != null
                ) {
                    LlmConnectionStatusBanner(
                        connectionState = llmConnectionState,
                        errorMessage = llmConnectionError,
                        hasQueuedRequest = queuedRequest != null,
                        onRetryConnection = onRetryConnection
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement
                        .spacedBy(8.dp)
                ) {
                    item {
                        Spacer(Modifier.height(8.dp))
                    }
                    items(messages) { msg ->
                        MessageBubble(message = msg)
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme
                            .colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 4.dp
                        )
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme
                    .outlineVariant
            )

            SidebarInput(
                value = currentInput,
                onValueChange = {
                    currentInput = it
                },
                isLoading = isLoading,
                pendingImages = pendingImages,
                onAddImage = { bytes ->
                    pendingImages =
                        pendingImages + bytes
                },
                onRemoveImage = { index ->
                    pendingImages = pendingImages
                        .toMutableList()
                        .apply { removeAt(index) }
                },
                onSend = {
                    val text = currentInput.trim()
                    if (text.isBlank() &&
                        pendingImages.isEmpty()
                    ) {
                        return@SidebarInput
                    }
                    val msgText = text.ifBlank {
                        "이 이미지를 분석해줘"
                    }
                    val imgs = pendingImages
                    pendingImages = emptyList()
                    currentInput = ""
                    submitMessage(msgText, imgs)
                }
            )
        }
    }
}

@Composable
private fun LlmConnectionStatusBanner(
    connectionState: LlmConnectionState,
    errorMessage: String?,
    hasQueuedRequest: Boolean,
    onRetryConnection: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme
            .primaryContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 10.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (connectionState ==
                LlmConnectionState.CONNECTING
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    Icons.Default.Key,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (connectionState) {
                        LlmConnectionState.CONNECTING ->
                            if (hasQueuedRequest) {
                                "LLM 서버와 연결 중입니다. 요청을 보관했고 연결되면 자동으로 전송합니다."
                            } else {
                                "LLM 서버와 연결을 준비하는 중입니다."
                            }
                        LlmConnectionState.FAILED ->
                            if (hasQueuedRequest) {
                                "LLM 서버 연결에 실패했습니다. 요청을 보관했고 재연결되면 자동으로 전송합니다."
                            } else {
                                "LLM 서버 연결에 실패했습니다."
                            }
                        LlmConnectionState.READY ->
                            "LLM 서버 연결이 준비되었습니다."
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme
                        .onPrimaryContainer,
                    lineHeight = 16.sp
                )
                if (
                    connectionState ==
                    LlmConnectionState.FAILED &&
                    !errorMessage.isNullOrBlank()
                ) {
                    Text(
                        text = errorMessage,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        lineHeight = 15.sp
                    )
                }
            }
            if (connectionState == LlmConnectionState.FAILED) {
                Text(
                    "다시 연결",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onRetryConnection() }
                        .padding(
                            horizontal = 8.dp,
                            vertical = 5.dp
                        )
                )
            }
        }
    }
}

private suspend fun streamAssistantResponse(
    messages: MutableList<ChatMessage>,
    llmService: LlmService,
    conversationDataSource: ConversationLocalDataSource,
    convId: String,
    images: List<ByteArray>,
    documentContent: String?,
    onError: (String?) -> Unit,
    onLoading: (Boolean) -> Unit
) {
    onLoading(true)
    var assistantIndex: Int? = null

    fun ensureAssistantMessage(initialContent: String = ""): Int {
        val existing = assistantIndex
        if (existing != null && existing in messages.indices) {
            return existing
        }
        messages.add(
            ChatMessage(
                role = ChatMessage.Role.ASSISTANT,
                content = initialContent
            )
        )
        assistantIndex = messages.lastIndex
        return messages.lastIndex
    }

    fun updateAssistant(content: String) {
        val idx = ensureAssistantMessage(content)
        if (idx in messages.indices) {
            messages[idx] = messages[idx].copy(
                content = content
            )
        }
    }

    try {
        val sb = StringBuilder()
        var thinking = false
        llmService.stream(
            messages = messages.toList(),
            systemPrompt = buildSystemPrompt(
                documentContent
            ),
            images = images
        ).collect { token ->
            when (token) {
                LlmClient.THINKING_TOKEN -> {
                    thinking = true
                    updateAssistant("생각 중...")
                }
                LlmClient.THINKING_DONE_TOKEN -> {
                    thinking = false
                    sb.clear()
                }
                ClaudeClient.GENERATING_TOKEN,
                LlmClient.GENERATING_TOKEN -> {
                    updateAssistant("응답 생성 중...")
                }
                LlmClient.RETRY_TOKEN -> {
                    updateAssistant("서버 과부하, 재시도 중...")
                }
                else -> {
                    if (!thinking) {
                        sb.append(token)
                        updateAssistant(sb.toString())
                    }
                }
            }
        }
        val idx = assistantIndex
        if (idx != null && idx in messages.indices) {
            if (
                messages[idx].content.isBlank() ||
                messages[idx].content == "응답 생성 중..." ||
                messages[idx].content == "생각 중..."
            ) {
                messages.removeAt(idx)
                onError(
                    "이 모델에서 응답을 받지 못했습니다. " +
                        "다른 모델을 선택해주세요."
                )
            } else {
                conversationDataSource.appendMessage(
                    convId,
                    messages[idx]
                )
            }
        }
    } catch (_: kotlin.coroutines.cancellation.CancellationException) {
        val idx = assistantIndex
        if (idx != null && idx in messages.indices) {
            messages.removeAt(idx)
        }
    } catch (e: Exception) {
        val msg = e.message ?: ""
        onError(
            when {
                msg.contains("API key not configured") ->
                    "API 키가 설정되지 않았습니다"
                msg.contains("API error 401") ||
                    msg.contains("API error 403") ->
                    "API 키가 유효하지 않습니다"
                msg.contains("API error 429") ->
                    "요청 한도 초과: $msg"
                msg.contains("Unable to resolve host", true) ||
                    msg.contains("No address associated", true) ->
                    "LLM 서버 주소를 찾을 수 없습니다. " +
                        "네트워크/DNS/VPN 상태를 확인한 뒤 " +
                        "다시 연결을 눌러주세요."
                msg.contains("API error") -> msg
                msg.contains("cancel", true) ||
                    msg.contains("closed") ->
                    null
                else -> "AI 응답을 받을 수 없습니다: $msg"
            }
        )
        val idx = assistantIndex
        if (idx != null && idx in messages.indices) {
            messages.removeAt(idx)
        }
    } finally {
        onLoading(false)
    }
}

@Composable
private fun SidebarTopBar(
    currentProvider: String,
    onProviderSelected: (String) -> Unit,
    currentModel: String,
    availableModels: List<String>,
    modelsLoading: Boolean,
    onModelMenuOpened: () -> Unit,
    onModelSelected: (String) -> Unit,
    onCollapse: () -> Unit,
    onNewConversation: () -> Unit,
    onToggleHistory: () -> Unit
) {
    var showModelMenu by remember {
        mutableStateOf(false)
    }
    var showProviderMenu by remember {
        mutableStateOf(false)
    }
    val providerLabel = when (currentProvider) {
        LlmProvider.OPENAI.name -> "OpenAI"
        LlmProvider.CLAUDE.name -> "Claude"
        else -> "Gemini"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Provider toggle
        Box {
            Text(
                text = providerLabel,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme
                    .onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        MaterialTheme.colorScheme
                            .surfaceVariant
                    )
                    .clickable {
                        showProviderMenu = true
                    }
                    .padding(
                        horizontal = 8.dp,
                        vertical = 4.dp
                    )
            )
            DropdownMenu(
                expanded = showProviderMenu,
                onDismissRequest = {
                    showProviderMenu = false
                }
            ) {
                DropdownMenuItem(
                    text = { Text("Gemini") },
                    onClick = {
                        onProviderSelected(
                            LlmProvider.GEMINI.name
                        )
                        showProviderMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("OpenAI") },
                    onClick = {
                        onProviderSelected(
                            LlmProvider.OPENAI.name
                        )
                        showProviderMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Claude") },
                    onClick = {
                        onProviderSelected(
                            LlmProvider.CLAUDE.name
                        )
                        showProviderMenu = false
                    }
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        // Model selector
        Box(modifier = Modifier.weight(1f)) {
            Text(
                text = currentModel,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text
                    .style.TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme
                    .primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        onModelMenuOpened()
                        showModelMenu = true
                    }
                    .padding(
                        horizontal = 8.dp,
                        vertical = 4.dp
                    )
            )
            DropdownMenu(
                expanded = showModelMenu,
                onDismissRequest = {
                    showModelMenu = false
                }
            ) {
                if (modelsLoading) {
                    Box(
                        modifier = Modifier
                            .padding(16.dp),
                        contentAlignment =
                        Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                } else if (
                    availableModels.isEmpty()
                ) {
                    Text(
                        "모델 목록을 불러올 수 없습니다",
                        modifier = Modifier
                            .padding(16.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                    )
                } else {
                    availableModels.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    model,
                                    fontSize = 13.sp,
                                    fontWeight =
                                    if (model ==
                                        currentModel
                                    ) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight
                                            .Normal
                                    },
                                    color =
                                    if (model ==
                                        currentModel
                                    ) {
                                        MaterialTheme
                                            .colorScheme
                                            .primary
                                    } else {
                                        MaterialTheme
                                            .colorScheme
                                            .onSurface
                                    }
                                )
                            },
                            onClick = {
                                onModelSelected(model)
                                showModelMenu = false
                            }
                        )
                    }
                }
            }
        }
        IconButton(
            onClick = onToggleHistory,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = "대화 기록",
                tint = MaterialTheme.colorScheme
                    .onSurfaceVariant
            )
        }
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
    pendingImages: List<ByteArray>,
    onAddImage: (ByteArray) -> Unit,
    onRemoveImage: (Int) -> Unit,
    onSend: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(
        android.content.Context.CLIPBOARD_SERVICE
    ) as android.content.ClipboardManager

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver
                .openInputStream(uri)?.use {
                    onAddImage(it.readBytes())
                }
        } catch (_: Throwable) {}
    }

    val canSend =
        value.isNotBlank() ||
            pendingImages.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        if (pendingImages.isNotEmpty()) {
            LazyRow(
                horizontalArrangement =
                Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                items(pendingImages.size) { idx ->
                    ImageThumbnail(
                        bytes = pendingImages[idx],
                        onRemove = {
                            onRemoveImage(idx)
                        }
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = {
                    imagePicker.launch("image/*")
                },
                enabled = !isLoading,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = "이미지 첨부",
                    tint = if (!isLoading) {
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme
                            .outline
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = {
                    try {
                        val clip = clipboardManager
                            .primaryClip
                        val item =
                            clip?.getItemAt(0)
                        val uri = item?.uri
                        if (uri != null) {
                            context
                                .contentResolver
                                .openInputStream(uri)
                                ?.use {
                                    onAddImage(
                                        it.readBytes()
                                    )
                                }
                        }
                    } catch (_: Throwable) {}
                },
                enabled = !isLoading,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.ContentPaste,
                    contentDescription =
                    "클립보드에서 붙여넣기",
                    tint = if (!isLoading) {
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme
                            .outline
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
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
                    focusedContainerColor =
                    MaterialTheme.colorScheme
                        .surfaceVariant,
                    unfocusedContainerColor =
                    MaterialTheme.colorScheme
                        .surfaceVariant,
                    focusedIndicatorColor =
                    Color.Transparent,
                    unfocusedIndicatorColor =
                    Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onSend,
                enabled = canSend
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored
                        .Filled.Send,
                    contentDescription = "전송",
                    tint = if (canSend) {
                        MaterialTheme.colorScheme
                            .primary
                    } else {
                        MaterialTheme.colorScheme
                            .outline
                    }
                )
            }
        }
    }
}

@Composable
private fun ImageThumbnail(bytes: ByteArray, onRemove: () -> Unit) {
    val bitmap = remember(bytes) {
        BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size
        )
    }
    Box(modifier = Modifier.size(56.dp)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "첨부 이미지",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        MaterialTheme.colorScheme
                            .surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme
                        .outline
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(18.dp)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.error
                )
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "제거",
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme
                    .onError
            )
        }
    }
}

@Composable
private fun ConversationHistoryPanel(
    conversations: List<ConversationSummary>,
    currentId: String?,
    onSelect: (ConversationSummary) -> Unit,
    onDelete: (ConversationSummary) -> Unit
) {
    if (conversations.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "저장된 대화가 없습니다",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme
                    .onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalArrangement =
            Arrangement.spacedBy(2.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            items(
                conversations,
                key = { it.id }
            ) { conv ->
                val isSelected =
                    conv.id == currentId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(8.dp)
                        )
                        .background(
                            if (isSelected) {
                                MaterialTheme
                                    .colorScheme
                                    .primaryContainer
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable {
                            onSelect(conv)
                        }
                        .padding(
                            horizontal = 12.dp,
                            vertical = 10.dp
                        ),
                    verticalAlignment =
                    Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                    ) {
                        Text(
                            conv.title,
                            fontSize = 13.sp,
                            fontWeight =
                            FontWeight.Medium,
                            maxLines = 1,
                            color = MaterialTheme
                                .colorScheme
                                .onSurface
                        )
                        val dateStr =
                            java.text.SimpleDateFormat(
                                "MM/dd HH:mm",
                                java.util.Locale
                                    .getDefault()
                            ).format(
                                java.util.Date(
                                    conv.updated
                                )
                            )
                        Text(
                            dateStr,
                            fontSize = 11.sp,
                            color = MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { onDelete(conv) },
                        modifier = Modifier
                            .size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription =
                            "삭제",
                            modifier = Modifier
                                .size(16.dp),
                            tint = MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }
    }
}
