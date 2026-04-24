package com.maestro.app.ui.auth

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maestro.app.ui.theme.MaestroBackground
import com.maestro.app.ui.theme.MaestroPrimary

@Composable
fun AuthScreen(viewModel: AuthViewModel, onLoginSuccess: () -> Unit) {
    val isLoggedIn by viewModel.isLoggedIn
        .collectAsState()
    val message by viewModel.message
        .collectAsState()
    val isLoading by viewModel.isLoading
        .collectAsState()

    if (isLoggedIn) {
        onLoginSuccess()
        return
    }

    var selectedTab by remember {
        mutableIntStateOf(0)
    }
    var loginUsername by remember {
        mutableStateOf("")
    }
    var loginPassword by remember {
        mutableStateOf("")
    }
    var regUsername by remember {
        mutableStateOf("")
    }
    var regEmail by remember {
        mutableStateOf("")
    }
    var regPassword by remember {
        mutableStateOf("")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaestroBackground)
            .imePadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .padding(32.dp),
            horizontalAlignment =
            Alignment.CenterHorizontally
        ) {
            // Logo & Title
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaestroPrimary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Maestro",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaestroPrimary
            )
            Spacer(Modifier.height(32.dp))

            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaestroPrimary,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier
                                .tabIndicatorOffset(
                                    tabPositions[selectedTab]
                                ),
                            color = MaestroPrimary
                        )
                    }
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        viewModel.clearMessage()
                    },
                    text = {
                        Text(
                            "로그인",
                            fontWeight = FontWeight.Medium
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        viewModel.clearMessage()
                    },
                    text = {
                        Text(
                            "회원가입",
                            fontWeight = FontWeight.Medium
                        )
                    }
                )
            }

            Spacer(Modifier.height(24.dp))

            // Forms
            Column(
                modifier = Modifier.animateContentSize()
            ) {
                if (selectedTab == 0) {
                    LoginForm(
                        username = loginUsername,
                        onUsernameChange = {
                            loginUsername = it
                        },
                        password = loginPassword,
                        onPasswordChange = {
                            loginPassword = it
                        },
                        isLoading = isLoading,
                        onLogin = {
                            viewModel.login(
                                loginUsername,
                                loginPassword
                            )
                        }
                    )
                } else {
                    RegisterForm(
                        username = regUsername,
                        onUsernameChange = {
                            regUsername = it
                        },
                        email = regEmail,
                        onEmailChange = {
                            regEmail = it
                        },
                        password = regPassword,
                        onPasswordChange = {
                            regPassword = it
                        },
                        isLoading = isLoading,
                        onRegister = {
                            viewModel.register(
                                regUsername,
                                regEmail,
                                regPassword
                            )
                        }
                    )
                }
            }

            // Message
            message?.let { msg ->
                Spacer(Modifier.height(16.dp))
                val isSuccess =
                    msg.contains("성공")
                Text(
                    text = msg,
                    fontSize = 13.sp,
                    color = if (isSuccess) {
                        Color(0xFF10B981)
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LoginForm(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    isLoading: Boolean,
    onLogin: () -> Unit
) {
    AuthTextField(
        value = username,
        onValueChange = onUsernameChange,
        placeholder = "사용자명"
    )
    Spacer(Modifier.height(12.dp))
    AuthTextField(
        value = password,
        onValueChange = onPasswordChange,
        placeholder = "비밀번호",
        isPassword = true
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onLogin,
        enabled = username.isNotBlank() &&
            password.isNotBlank() && !isLoading,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaestroPrimary
        )
    ) {
        if (isLoading) {
            LoadingIndicator()
        } else {
            Text(
                "로그인",
                modifier = Modifier.padding(
                    vertical = 4.dp
                ),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun RegisterForm(
    username: String,
    onUsernameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    isLoading: Boolean,
    onRegister: () -> Unit
) {
    AuthTextField(
        value = username,
        onValueChange = onUsernameChange,
        placeholder = "사용자명"
    )
    Spacer(Modifier.height(12.dp))
    AuthTextField(
        value = email,
        onValueChange = onEmailChange,
        placeholder = "이메일"
    )
    Spacer(Modifier.height(12.dp))
    AuthTextField(
        value = password,
        onValueChange = onPasswordChange,
        placeholder = "비밀번호",
        isPassword = true
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onRegister,
        enabled = username.isNotBlank() &&
            email.isNotBlank() &&
            password.isNotBlank() && !isLoading,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaestroPrimary
        )
    ) {
        if (isLoading) {
            LoadingIndicator()
        } else {
            Text(
                "회원가입",
                modifier = Modifier.padding(
                    vertical = 4.dp
                ),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AuthTextField(
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
private fun LoadingIndicator() {
    Row(
        horizontalArrangement =
        Arrangement.Center,
        verticalAlignment =
        Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = Color.White
        )
        Spacer(Modifier.width(8.dp))
        Text("처리 중...")
    }
}
