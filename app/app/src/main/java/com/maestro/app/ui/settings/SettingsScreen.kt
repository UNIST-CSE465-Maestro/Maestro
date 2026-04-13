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
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val apiKeySet by viewModel.apiKeySet
        .collectAsState()
    val validationResult by viewModel
        .validationResult.collectAsState()
    val isValidating by viewModel.isValidating
        .collectAsState()
    val serverUrl by viewModel.serverUrl
        .collectAsState()
    val isLoggedIn by viewModel.isLoggedIn
        .collectAsState()
    val username by viewModel.username
        .collectAsState()
    val serverMessage by viewModel.serverMessage
        .collectAsState()
    val isServerLoading by viewModel.isServerLoading
        .collectAsState()

    var apiKeyInput by remember { mutableStateOf("") }
    var serverUrlInput by remember {
        mutableStateOf(serverUrl ?: "")
    }
    var loginUsernameInput by remember {
        mutableStateOf("")
    }
    var loginPasswordInput by remember {
        mutableStateOf("")
    }
    var regUsernameInput by remember {
        mutableStateOf("")
    }
    var regEmailInput by remember {
        mutableStateOf("")
    }
    var regPasswordInput by remember {
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

            // API Key section
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

            SettingsTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                placeholder = "sk-ant-...",
                isPassword = true
            )

            Spacer(Modifier.height(16.dp))

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
                    enabled =
                    apiKeyInput.isNotBlank() &&
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
                            contentColor =
                            MaterialTheme.colorScheme
                                .error
                        )
                ) {
                    Text("삭제")
                }
            }

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

            // Server settings section
            Text(
                text = "서버 설정",
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme
                    .onSurface
            )

            Spacer(Modifier.height(12.dp))

            SettingsTextField(
                value = serverUrlInput,
                onValueChange = {
                    serverUrlInput = it
                },
                placeholder = "https://example.com"
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    viewModel.saveServerUrl(
                        serverUrlInput
                    )
                },
                enabled = serverUrlInput.isNotBlank()
            ) {
                Text("서버 URL 저장")
            }

            Spacer(Modifier.height(16.dp))

            if (isLoggedIn) {
                LoggedInSection(
                    username = username,
                    onLogout = { viewModel.logout() }
                )
            } else {
                LoginSection(
                    loginUsername = loginUsernameInput,
                    onLoginUsernameChange = {
                        loginUsernameInput = it
                    },
                    loginPassword = loginPasswordInput,
                    onLoginPasswordChange = {
                        loginPasswordInput = it
                    },
                    isLoading = isServerLoading,
                    onLogin = {
                        viewModel.login(
                            loginUsernameInput,
                            loginPasswordInput
                        )
                    }
                )

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                RegisterSection(
                    regUsername = regUsernameInput,
                    onRegUsernameChange = {
                        regUsernameInput = it
                    },
                    regEmail = regEmailInput,
                    onRegEmailChange = {
                        regEmailInput = it
                    },
                    regPassword = regPasswordInput,
                    onRegPasswordChange = {
                        regPasswordInput = it
                    },
                    isLoading = isServerLoading,
                    onRegister = {
                        viewModel.register(
                            regUsernameInput,
                            regEmailInput,
                            regPasswordInput
                        )
                    }
                )
            }

            serverMessage?.let { msg ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = msg,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme
                        .onSurfaceVariant
                )
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
}

@Composable
private fun LoggedInSection(username: String?, onLogout: () -> Unit) {
    Text(
        text = "로그인됨: ${username ?: ""}",
        fontSize = 14.sp,
        color = Color(0xFF10B981)
    )
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = onLogout,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme
                .error
        )
    ) {
        Text("로그아웃")
    }
}

@Composable
private fun LoginSection(
    loginUsername: String,
    onLoginUsernameChange: (String) -> Unit,
    loginPassword: String,
    onLoginPasswordChange: (String) -> Unit,
    isLoading: Boolean,
    onLogin: () -> Unit
) {
    Text(
        text = "로그인",
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    )
    Spacer(Modifier.height(8.dp))
    SettingsTextField(
        value = loginUsername,
        onValueChange = onLoginUsernameChange,
        placeholder = "사용자명"
    )
    Spacer(Modifier.height(8.dp))
    SettingsTextField(
        value = loginPassword,
        onValueChange = onLoginPasswordChange,
        placeholder = "비밀번호",
        isPassword = true
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = onLogin,
        enabled = loginUsername.isNotBlank() &&
            loginPassword.isNotBlank() &&
            !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .height(16.dp)
                    .width(16.dp),
                strokeWidth = 2.dp
            )
        } else {
            Text("로그인")
        }
    }
}

@Composable
private fun RegisterSection(
    regUsername: String,
    onRegUsernameChange: (String) -> Unit,
    regEmail: String,
    onRegEmailChange: (String) -> Unit,
    regPassword: String,
    onRegPasswordChange: (String) -> Unit,
    isLoading: Boolean,
    onRegister: () -> Unit
) {
    Text(
        text = "회원가입",
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    )
    Spacer(Modifier.height(8.dp))
    SettingsTextField(
        value = regUsername,
        onValueChange = onRegUsernameChange,
        placeholder = "사용자명"
    )
    Spacer(Modifier.height(8.dp))
    SettingsTextField(
        value = regEmail,
        onValueChange = onRegEmailChange,
        placeholder = "이메일"
    )
    Spacer(Modifier.height(8.dp))
    SettingsTextField(
        value = regPassword,
        onValueChange = onRegPasswordChange,
        placeholder = "비밀번호",
        isPassword = true
    )
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = onRegister,
        enabled = regUsername.isNotBlank() &&
            regEmail.isNotBlank() &&
            regPassword.isNotBlank() &&
            !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .height(16.dp)
                    .width(16.dp),
                strokeWidth = 2.dp
            )
        } else {
            Text("회원가입")
        }
    }
}
