package com.maestro.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val geminiKeySet by viewModel.geminiKeySet
        .collectAsState()
    val openAiKeySet by viewModel.openAiKeySet
        .collectAsState()
    val openRouterKeySet by viewModel.openRouterKeySet
        .collectAsState()
    val claudeKeySet by viewModel.claudeKeySet
        .collectAsState()
    val currentProvider by viewModel.llmProvider
        .collectAsState()
    val serverTokenSet by viewModel.serverTokenSet
        .collectAsState()
    val serverUsername by viewModel.serverUsername
        .collectAsState()
    val qeServerUrl by viewModel.qeServerUrl
        .collectAsState()
    val validationResult by viewModel
        .validationResult.collectAsState()
    val isValidating by viewModel.isValidating
        .collectAsState()
    val serverAuthInProgress by viewModel
        .serverAuthInProgress.collectAsState()
    var geminiKeyInput by remember {
        mutableStateOf("")
    }
    var openAiKeyInput by remember {
        mutableStateOf("")
    }
    var openRouterKeyInput by remember {
        mutableStateOf("")
    }
    var claudeKeyInput by remember {
        mutableStateOf("")
    }
    var serverTokenInput by remember {
        mutableStateOf("")
    }
    var serverUsernameInput by remember {
        mutableStateOf("")
    }
    var serverPasswordInput by remember {
        mutableStateOf("")
    }
    var qeServerUrlInput by remember {
        mutableStateOf("")
    }
    LaunchedEffect(qeServerUrl) {
        if (qeServerUrlInput.isBlank()) {
            qeServerUrlInput = qeServerUrl
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "설정",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector =
                            Icons
                                .AutoMirrored
                                .Filled
                                .ArrowBack,
                            contentDescription = "뒤로"
                        )
                    }
                },
                colors =
                TopAppBarDefaults
                    .topAppBarColors(
                        containerColor =
                        MaterialTheme
                            .colorScheme.surface
                    )
            )
        }
    ) { padding ->
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))

            val providers =
                listOf(
                    ApiProviderTab("OPENROUTER", "OpenRouter", openRouterKeySet),
                    ApiProviderTab("GEMINI", "Gemini", geminiKeySet),
                    ApiProviderTab("OPENAI", "ChatGPT", openAiKeySet),
                    ApiProviderTab("CLAUDE", "Claude", claudeKeySet)
                )
            val activeProvider =
                providers.firstOrNull {
                    it.id == currentProvider
                } ?: providers.first()
            Text(
                text = "LLM API Key",
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(8.dp))
            ScrollableTabRow(
                selectedTabIndex = providers.indexOf(activeProvider),
                edgePadding = 0.dp,
                divider = {}
            ) {
                providers.forEach { provider ->
                    Tab(
                        selected = provider.id == activeProvider.id,
                        onClick = {
                            viewModel.activateProvider(provider.id)
                        },
                        text = {
                            Text(
                                provider.label,
                                color =
                                if (provider.keySet) {
                                    Color(0xFF10B981)
                                } else {
                                    MaterialTheme.colorScheme
                                        .onSurfaceVariant
                                },
                                fontWeight =
                                if (
                                    provider.id == activeProvider.id
                                ) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Medium
                                }
                            )
                        }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            ActiveApiKeyEditor(
                provider = activeProvider,
                value =
                when (activeProvider.id) {
                    "GEMINI" -> geminiKeyInput
                    "OPENAI" -> openAiKeyInput
                    "CLAUDE" -> claudeKeyInput
                    else -> openRouterKeyInput
                },
                onValueChange = {
                    when (activeProvider.id) {
                        "GEMINI" -> geminiKeyInput = it
                        "OPENAI" -> openAiKeyInput = it
                        "CLAUDE" -> claudeKeyInput = it
                        else -> openRouterKeyInput = it
                    }
                },
                isValidating = isValidating,
                onSave = {
                    when (activeProvider.id) {
                        "GEMINI" -> {
                            viewModel.saveAndValidateGeminiKey(geminiKeyInput)
                            geminiKeyInput = ""
                        }
                        "OPENAI" -> {
                            viewModel.saveAndValidateOpenAiKey(openAiKeyInput)
                            openAiKeyInput = ""
                        }
                        "CLAUDE" -> {
                            viewModel.saveAndValidateClaudeKey(claudeKeyInput)
                            claudeKeyInput = ""
                        }
                        else -> {
                            viewModel.saveAndValidateOpenRouterKey(openRouterKeyInput)
                            openRouterKeyInput = ""
                        }
                    }
                },
                onClear = {
                    when (activeProvider.id) {
                        "GEMINI" -> {
                            viewModel.clearGeminiKey()
                            geminiKeyInput = ""
                        }
                        "OPENAI" -> {
                            viewModel.clearOpenAiKey()
                            openAiKeyInput = ""
                        }
                        "CLAUDE" -> {
                            viewModel.clearClaudeKey()
                            claudeKeyInput = ""
                        }
                        else -> {
                            viewModel.clearOpenRouterKey()
                            openRouterKeyInput = ""
                        }
                    }
                }
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))

            Text(
                text = "MinerU 서버 인증",
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text =
                if (serverTokenSet) {
                    "MinerU 서버 인증이 설정되어 있습니다"
                } else {
                    "MinerU 서버 인증이 설정되지 않았습니다"
                },
                fontSize = 13.sp,
                color =
                if (serverTokenSet) {
                    Color(0xFF10B981)
                } else {
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
                }
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text =
                serverUsername?.let {
                    "서버 계정: $it"
                } ?: "서버 계정으로 인증하면 access/refresh token을 받아 MinerU 추출에 자동 사용합니다.",
                fontSize = 12.sp,
                color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            SettingsTextField(
                value = serverUsernameInput,
                onValueChange = {
                    serverUsernameInput = it
                },
                placeholder = "MinerU 서버 아이디",
                isPassword = false
            )
            Spacer(Modifier.height(8.dp))
            SettingsTextField(
                value = serverPasswordInput,
                onValueChange = {
                    serverPasswordInput = it
                },
                placeholder = "MinerU 서버 비밀번호",
                isPassword = true
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.authenticateMineruServer(
                            serverUsernameInput,
                            serverPasswordInput
                        )
                        serverPasswordInput = ""
                    },
                    enabled =
                    serverUsernameInput.isNotBlank() &&
                        serverPasswordInput.isNotBlank() &&
                        !serverAuthInProgress
                ) {
                    if (serverAuthInProgress) {
                        CircularProgressIndicator(
                            modifier =
                            Modifier
                                .height(16.dp)
                                .width(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("서버 인증")
                    }
                }
                OutlinedButton(
                    onClick = {
                        viewModel.clearServerBearerToken()
                        serverTokenInput = ""
                        serverPasswordInput = ""
                    },
                    enabled = serverTokenSet,
                    colors =
                    ButtonDefaults
                        .outlinedButtonColors(
                            contentColor =
                            MaterialTheme.colorScheme
                                .error
                        )
                ) { Text("인증 삭제") }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "고급: Bearer 토큰을 직접 저장할 수도 있습니다.",
                fontSize = 12.sp,
                color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            SettingsTextField(
                value = serverTokenInput,
                onValueChange = {
                    serverTokenInput = it
                },
                placeholder = "Bearer 토큰 입력",
                isPassword = true
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.saveServerBearerToken(
                            serverTokenInput
                        )
                        serverTokenInput = ""
                    },
                    enabled = serverTokenInput.isNotBlank()
                ) {
                    Text("토큰 저장")
                }
            }

            // Validation result
            validationResult?.let { result ->
                Spacer(Modifier.height(12.dp))
                val isOk = result.startsWith("OK")
                Text(
                    text = result,
                    fontSize = 13.sp,
                    color =
                    if (isOk) {
                        Color(0xFF10B981)
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            Text(
                text = "퀴즈 인코더(QE) 서버",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text =
                "생성된 퀴즈를 MobileKT Question Encoder 서버로 전송해 " +
                    "문제 표현(임베딩·난이도)을 받아 태블릿에 저장합니다.",
                fontSize = 13.sp,
                color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            SettingsTextField(
                value = qeServerUrlInput,
                onValueChange = {
                    qeServerUrlInput = it
                },
                placeholder = "http://h-router.iptime.org:9511/"
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.saveQeServerUrl(qeServerUrlInput)
                },
                enabled = qeServerUrlInput.isNotBlank()
            ) {
                Text("QE 서버 주소 저장")
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            Spacer(Modifier.height(32.dp))
        }
    }
}

private data class ApiProviderTab(
    val id: String,
    val label: String,
    val keySet: Boolean
)

@Composable
private fun ActiveApiKeyEditor(
    provider: ApiProviderTab,
    value: String,
    onValueChange: (String) -> Unit,
    isValidating: Boolean,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    Text(
        text =
        if (provider.keySet) {
            "${provider.label} API 키가 설정되어 있습니다"
        } else {
            "${provider.label} API 키가 설정되지 않았습니다"
        },
        fontSize = 13.sp,
        color =
        if (provider.keySet) {
            Color(0xFF10B981)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
    if (provider.id == "OPENROUTER") {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "OpenRouter가 채팅/퀴즈 기본 provider입니다.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(12.dp))
    SettingsTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = "${provider.label} API 키 입력",
        isPassword = true
    )
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onSave,
            enabled = value.isNotBlank() && !isValidating
        ) {
            if (isValidating) {
                CircularProgressIndicator(
                    modifier =
                    Modifier
                        .height(16.dp)
                        .width(16.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("저장 및 검증")
            }
        }
        OutlinedButton(
            onClick = onClear,
            enabled = provider.keySet,
            colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("삭제")
        }
    }
}

@Composable
private fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(placeholder, fontSize = 14.sp)
        },
        visualTransformation =
        if (isPassword) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        singleLine = true,
        colors =
        TextFieldDefaults.colors(
            focusedContainerColor =
            MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor =
            MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        shape = RoundedCornerShape(12.dp)
    )
}
