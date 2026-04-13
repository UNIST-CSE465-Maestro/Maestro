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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val apiKeySet by viewModel.apiKeySet
        .collectAsState()
    val validationResult by viewModel.validationResult
        .collectAsState()
    val isValidating by viewModel.isValidating
        .collectAsState()

    var apiKeyInput by remember { mutableStateOf("") }

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
                            Icons.AutoMirrored.Filled.ArrowBack,
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
        ) {
            Spacer(Modifier.height(16.dp))

            // API Key status
            Text(
                text = "Anthropic API Key",
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme
                    .onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (apiKeySet) {
                    "API 키가 설정되어 있습니다"
                } else {
                    "API 키가 설정되지 않았습니다"
                },
                fontSize = 13.sp,
                color = if (apiKeySet) {
                    Color(0xFF10B981)
                } else {
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
                }
            )

            Spacer(Modifier.height(16.dp))

            // API key input
            TextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "sk-ant-...",
                        fontSize = 14.sp
                    )
                },
                visualTransformation =
                PasswordVisualTransformation(),
                singleLine = true,
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

            Spacer(Modifier.height(16.dp))

            // Buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.saveApiKey(apiKeyInput)
                        apiKeyInput = ""
                    },
                    enabled = apiKeyInput.isNotBlank()
                ) {
                    Text("저장")
                }

                OutlinedButton(
                    onClick = {
                        viewModel.validateApiKey(
                            apiKeyInput
                        )
                    },
                    enabled = apiKeyInput.isNotBlank() &&
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
                        Text("검증")
                    }
                }

                OutlinedButton(
                    onClick = {
                        viewModel.clearApiKey()
                        apiKeyInput = ""
                    },
                    enabled = apiKeySet,
                    colors = ButtonDefaults
                        .outlinedButtonColors(
                            contentColor = MaterialTheme
                                .colorScheme.error
                        )
                ) {
                    Text("삭제")
                }
            }

            // Validation result
            validationResult?.let { result ->
                Spacer(Modifier.height(12.dp))
                val isSuccess = result.startsWith("OK")
                Row(
                    verticalAlignment =
                    Alignment.CenterVertically
                ) {
                    Text(
                        text = result,
                        fontSize = 13.sp,
                        color = if (isSuccess) {
                            Color(0xFF10B981)
                        } else {
                            MaterialTheme.colorScheme
                                .error
                        }
                    )
                }
            }
        }
    }
}
