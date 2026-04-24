package com.maestro.app.navigation

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.maestro.app.data.local.ConversationLocalDataSource
import com.maestro.app.data.remote.MaestroServerApi
import com.maestro.app.domain.repository.SettingsRepository
import com.maestro.app.domain.service.LlmService
import com.maestro.app.ui.auth.AuthScreen
import com.maestro.app.ui.auth.AuthViewModel
import com.maestro.app.ui.home.HomeScreen
import com.maestro.app.ui.home.HomeViewModel
import com.maestro.app.ui.settings.SettingsScreen
import com.maestro.app.ui.settings.SettingsViewModel
import com.maestro.app.ui.theme.MaestroBackground
import com.maestro.app.ui.theme.MaestroPrimary
import com.maestro.app.ui.viewer.ViewerScreen
import com.maestro.app.ui.viewer.ViewerViewModel
import kotlinx.coroutines.flow.first
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun MaestroNavGraph() {
    val navController = rememberNavController()
    val activity = LocalContext.current as? Activity
    var showExitDialog by remember { mutableStateOf(false) }

    val settingsRepository: SettingsRepository =
        koinInject()
    val serverApi: MaestroServerApi = koinInject()

    // Health check + auth state
    var healthChecked by remember {
        mutableStateOf(false)
    }
    var serverError by remember {
        mutableStateOf<String?>(null)
    }
    var startDest by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(Unit) {
        // Silent health check
        try {
            val resp = serverApi.health()
            if (!resp.isSuccessful) {
                serverError =
                    "서버에 연결할 수 없습니다"
            }
        } catch (_: Exception) {
            serverError =
                "서버에 연결할 수 없습니다"
        }

        // Determine start destination
        val token =
            settingsRepository.getAccessToken().first()
        startDest = if (!token.isNullOrBlank()) {
            Screen.Home.route
        } else {
            Screen.Auth.route
        }
        healthChecked = true
    }

    // Server error dialog
    if (serverError != null) {
        AlertDialog(
            onDismissRequest = { serverError = null },
            title = {
                Text(
                    "서버 연결 오류",
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(serverError ?: "") },
            confirmButton = {
                TextButton(
                    onClick = { serverError = null }
                ) { Text("확인") }
            }
        )
    }

    // Loading state while checking
    if (!healthChecked || startDest == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaestroBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment =
                Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaestroPrimary
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Maestro",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaestroPrimary
                )
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp,
                    color = MaestroPrimary
                )
            }
        }
        return
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = {
                showExitDialog = false
            },
            title = {
                Text(
                    "앱 종료",
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text("앱을 종료하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = { activity?.finish() }
                ) { Text("예") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExitDialog = false }
                ) { Text("아니오") }
            }
        )
    }

    NavHost(
        navController = navController,
        startDestination = startDest!!
    ) {
        composable(Screen.Auth.route) {
            val viewModel: AuthViewModel =
                koinViewModel()

            BackHandler { showExitDialog = true }

            AuthScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate(
                        Screen.Home.route
                    ) {
                        popUpTo(Screen.Auth.route) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            val viewModel: HomeViewModel =
                koinViewModel()

            BackHandler {
                val folderId =
                    viewModel.currentFolderId.value
                if (folderId != null) {
                    val parent =
                        viewModel.folders.value
                            .find { it.id == folderId }
                            ?.parentId
                    viewModel.navigateFolder(parent)
                } else {
                    showExitDialog = true
                }
            }

            HomeScreen(
                viewModel = viewModel,
                onOpenPdf = { doc ->
                    val encoded =
                        Uri.encode(doc.uriString)
                    navController.navigate(
                        Screen.Viewer.createRoute(
                            doc.id,
                            doc.pageCount,
                            encoded
                        )
                    )
                },
                onOpenSettings = {
                    navController.navigate(
                        Screen.Settings.route
                    )
                }
            )
        }

        composable(
            route = Screen.Viewer.route,
            arguments = listOf(
                navArgument("pdfId") {
                    type = NavType.StringType
                },
                navArgument("pageCount") {
                    type = NavType.IntType
                },
                navArgument("uriEncoded") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val pdfId = backStackEntry.arguments
                ?.getString("pdfId")
                ?: return@composable
            val pageCount = backStackEntry.arguments
                ?.getInt("pageCount") ?: 1
            val uriEncoded = backStackEntry.arguments
                ?.getString("uriEncoded")
                ?: return@composable
            val pdfUri =
                Uri.parse(Uri.decode(uriEncoded))

            val viewModel: ViewerViewModel =
                koinViewModel {
                    parametersOf(
                        pdfId,
                        pageCount,
                        pdfUri
                    )
                }
            val llmService: LlmService =
                koinInject()
            val settingsRepo: SettingsRepository =
                koinInject()
            val convDataSource:
                ConversationLocalDataSource =
                koinInject()

            BackHandler {
                navController.popBackStack()
            }

            ViewerScreen(
                viewModel = viewModel,
                llmService = llmService,
                settingsRepository = settingsRepo,
                conversationDataSource =
                convDataSource,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) {
            val viewModel: SettingsViewModel =
                koinViewModel()

            BackHandler {
                navController.popBackStack()
            }

            SettingsScreen(
                viewModel = viewModel,
                onBack = {
                    navController.popBackStack()
                },
                onLogout = {
                    navController.navigate(
                        Screen.Auth.route
                    ) {
                        popUpTo(Screen.Home.route) {
                            inclusive = true
                        }
                    }
                }
            )
        }
    }
}
