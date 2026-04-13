package com.maestro.app.navigation

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.maestro.app.data.local.ConversationLocalDataSource
import com.maestro.app.domain.repository.SettingsRepository
import com.maestro.app.domain.service.LlmService
import com.maestro.app.ui.home.HomeScreen
import com.maestro.app.ui.home.HomeViewModel
import com.maestro.app.ui.settings.SettingsScreen
import com.maestro.app.ui.settings.SettingsViewModel
import com.maestro.app.ui.viewer.ViewerScreen
import com.maestro.app.ui.viewer.ViewerViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun MaestroNavGraph() {
    val navController = rememberNavController()
    val activity = LocalContext.current as? Activity
    var showExitDialog by remember { mutableStateOf(false) }

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
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            val viewModel: HomeViewModel = koinViewModel()

            BackHandler {
                val folderId =
                    viewModel.currentFolderId.value
                if (folderId != null) {
                    val parent = viewModel.folders.value
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
                    val encoded = Uri.encode(doc.uriString)
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
            val pdfUri = Uri.parse(Uri.decode(uriEncoded))

            val viewModel: ViewerViewModel =
                koinViewModel {
                    parametersOf(pdfId, pageCount, pdfUri)
                }
            val llmService: LlmService = koinInject()
            val settingsRepo: SettingsRepository =
                koinInject()
            val convDataSource: ConversationLocalDataSource =
                koinInject()

            BackHandler {
                navController.popBackStack()
            }

            ViewerScreen(
                viewModel = viewModel,
                llmService = llmService,
                settingsRepository = settingsRepo,
                conversationDataSource = convDataSource,
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
                }
            )
        }
    }
}
