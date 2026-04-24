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
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit, onLogout: () -> Unit = {}) {
    val geminiKeySet by viewModel.geminiKeySet
        .collectAsState()
    val openAiKeySet by viewModel.openAiKeySet
        .collectAsState()
    val validationResult by viewModel
        .validationResult.collectAsState()
    val isValidating by viewModel.isValidating
        .collectAsState()
    val username by viewModel.username
        .collectAsState()

    var geminiKeyInput by remember {
        mutableStateOf("")
    }
    var openAiKeyInput by remember {
        mutableStateOf("")
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
                            imageVector = Icons
                                .AutoMirrored
                                .Filled
                                .ArrowBack,
                            contentDescription = "뒤로"
                        )
                    }
                },
                colors = TopAppBarDefaults
                    .topAppBarColors(
                        containerColor = MaterialTheme
                            .colorScheme.surface
                    )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))

            // Gemini API Key
            Text(
                text = "Gemini API Key",
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (geminiKeySet) {
                    "Gemini API 키가 설정되어 있습니다"
                } else {
                    "Gemini API 키가 설정되지 않았습니다"
                },
                fontSize = 13.sp,
                color = if (geminiKeySet) {
                    Color(0xFF10B981)
                } else {
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
                }
            )
            Spacer(Modifier.height(12.dp))
            SettingsTextField(
                value = geminiKeyInput,
                onValueChange = {
                    geminiKeyInput = it
                },
                placeholder = "Gemini API 키 입력",
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
                        viewModel
                            .saveAndValidateGeminiKey(
                                geminiKeyInput
                            )
                        geminiKeyInput = ""
                    },
                    enabled =
                    geminiKeyInput.isNotBlank() &&
                        !isValidating
                ) {
                    if (isValidating) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .height(16.dp)
                                .width(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("저장 및 검증")
                    }
                }
                OutlinedButton(
                    onClick = {
                        viewModel.clearGeminiKey()
                        geminiKeyInput = ""
                    },
                    enabled = geminiKeySet,
                    colors = ButtonDefaults
                        .outlinedButtonColors(
                            contentColor =
                            MaterialTheme.colorScheme
                                .error
                        )
                ) { Text("삭제") }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))

            // OpenAI API Key
            Text(
                text = "OpenAI API Key",
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (openAiKeySet) {
                    "OpenAI API 키가 설정되어 있습니다"
                } else {
                    "OpenAI API 키가 설정되지 않았습니다"
                },
                fontSize = 13.sp,
                color = if (openAiKeySet) {
                    Color(0xFF10B981)
                } else {
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
                }
            )
            Spacer(Modifier.height(12.dp))
            SettingsTextField(
                value = openAiKeyInput,
                onValueChange = {
                    openAiKeyInput = it
                },
                placeholder = "OpenAI API 키 입력",
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
                        viewModel
                            .saveAndValidateOpenAiKey(
                                openAiKeyInput
                            )
                        openAiKeyInput = ""
                    },
                    enabled =
                    openAiKeyInput.isNotBlank() &&
                        !isValidating
                ) {
                    if (isValidating) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .height(16.dp)
                                .width(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("저장 및 검증")
                    }
                }
                OutlinedButton(
                    onClick = {
                        viewModel.clearOpenAiKey()
                        openAiKeyInput = ""
                    },
                    enabled = openAiKeySet,
                    colors = ButtonDefaults
                        .outlinedButtonColors(
                            contentColor =
                            MaterialTheme.colorScheme
                                .error
                        )
                ) { Text("삭제") }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))

            // Claude API Key
            val claudeKeySet by viewModel.claudeKeySet
                .collectAsState()
            var claudeKeyInput by remember {
                mutableStateOf("")
            }
            Text(
                text = "Claude API Key",
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (claudeKeySet) {
                    "Claude API 키가 설정되어 있습니다"
                } else {
                    "Claude API 키가 설정되지 않았습니다"
                },
                fontSize = 13.sp,
                color = if (claudeKeySet) {
                    Color(0xFF10B981)
                } else {
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
                }
            )
            Spacer(Modifier.height(12.dp))
            SettingsTextField(
                value = claudeKeyInput,
                onValueChange = {
                    claudeKeyInput = it
                },
                placeholder = "Claude API 키 입력",
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
                        viewModel
                            .saveAndValidateClaudeKey(
                                claudeKeyInput
                            )
                        claudeKeyInput = ""
                    },
                    enabled =
                    claudeKeyInput.isNotBlank() &&
                        !isValidating
                ) {
                    if (isValidating) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .height(16.dp)
                                .width(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("저장 및 검증")
                    }
                }
                OutlinedButton(
                    onClick = {
                        viewModel.clearClaudeKey()
                        claudeKeyInput = ""
                    },
                    enabled = claudeKeySet,
                    colors = ButtonDefaults
                        .outlinedButtonColors(
                            contentColor =
                            MaterialTheme.colorScheme
                                .error
                        )
                ) { Text("삭제") }
            }

            // Validation result
            validationResult?.let { result ->
                Spacer(Modifier.height(12.dp))
                val isOk = result.startsWith("OK")
                Text(
                    text = result,
                    fontSize = 13.sp,
                    color = if (isOk) {
                        Color(0xFF10B981)
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // Account section
            Text(
                text = "계정",
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "로그인됨: ${username ?: ""}",
                fontSize = 14.sp,
                color = Color(0xFF10B981)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    viewModel.logout()
                    onLogout()
                },
                colors = ButtonDefaults
                    .outlinedButtonColors(
                        contentColor =
                        MaterialTheme.colorScheme
                            .error
                    )
            ) {
                Text("로그아웃")
            }

            Spacer(Modifier.height(32.dp))
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
        visualTransformation = if (isPassword) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        singleLine = true,
        colors = TextFieldDefaults.colors(
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
