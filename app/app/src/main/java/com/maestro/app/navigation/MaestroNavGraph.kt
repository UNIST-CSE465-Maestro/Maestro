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
import com.maestro.app.data.local.PersistedPdfTab
import com.maestro.app.data.local.PersistedViewerTabState
import com.maestro.app.data.local.ViewerTabStateLocalDataSource
import com.maestro.app.data.remote.MaestroServerApi
import com.maestro.app.domain.repository.DocumentRepository
import com.maestro.app.domain.repository.SettingsRepository
import com.maestro.app.domain.service.LlmService
import com.maestro.app.domain.service.QuizService
import com.maestro.app.ui.auth.AuthScreen
import com.maestro.app.ui.auth.AuthViewModel
import com.maestro.app.ui.home.HomeScreen
import com.maestro.app.ui.home.HomeViewModel
import com.maestro.app.ui.profile.ProfileScreen
import com.maestro.app.ui.profile.ProfileViewModel
import com.maestro.app.ui.settings.SettingsScreen
import com.maestro.app.ui.settings.SettingsViewModel
import com.maestro.app.ui.theme.MaestroBackground
import com.maestro.app.ui.theme.MaestroPrimary
import com.maestro.app.ui.viewer.OpenPdfTab
import com.maestro.app.ui.viewer.PdfTabViewportState
import com.maestro.app.ui.viewer.ViewerScreen
import com.maestro.app.ui.viewer.ViewerViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@OptIn(FlowPreview::class)
@Composable
fun MaestroNavGraph() {
    val navController = rememberNavController()
    val activity = LocalContext.current as? Activity
    var showExitDialog by remember { mutableStateOf(false) }
    val openPdfTabs = remember {
        mutableStateListOf<OpenPdfTab>()
    }
    val pdfTabViewports = remember {
        mutableStateMapOf<String, PdfTabViewportState>()
    }
    var activePdfTabId by remember {
        mutableStateOf<String?>(null)
    }
    var tabsRestored by remember {
        mutableStateOf(false)
    }

    val settingsRepository: SettingsRepository =
        koinInject()
    val documentRepository: DocumentRepository =
        koinInject()
    val viewerTabStore: ViewerTabStateLocalDataSource =
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
        // Determine start destination
        val token =
            settingsRepository.getAccessToken().first()
        val authenticated = !token.isNullOrBlank()
        if (authenticated) {
            val savedState = viewerTabStore.load()
            val docsById = documentRepository
                .loadDocuments()
                .associateBy { it.id }
            pdfTabViewports.clear()
            val restoredTabs = savedState.tabs
                .mapNotNull { tab ->
                    docsById[tab.documentId]?.let { doc ->
                        pdfTabViewports[doc.id] =
                            PdfTabViewportState(
                                firstVisiblePageIndex =
                                tab.firstVisiblePageIndex
                                    .coerceIn(
                                        0,
                                        (doc.pageCount - 1)
                                            .coerceAtLeast(0)
                                    ),
                                firstVisiblePageScrollOffset =
                                tab.firstVisiblePageScrollOffset
                                    .coerceAtLeast(0)
                            )
                        OpenPdfTab(
                            documentId = doc.id,
                            title = doc.displayName,
                            pageCount = doc.pageCount,
                            uriString = doc.uriString
                        )
                    }
                }
            openPdfTabs.clear()
            openPdfTabs.addAll(restoredTabs)
            activePdfTabId = savedState.activeDocumentId
                ?.takeIf { id ->
                    restoredTabs.any {
                        it.documentId == id
                    }
                }
                ?: restoredTabs.firstOrNull()?.documentId
        }
        tabsRestored = true
        startDest = if (authenticated) {
            Screen.Home.route
        } else {
            Screen.Auth.route
        }
        healthChecked = true

        // Silent health check must not block local startup.
        val resp = try {
            withTimeoutOrNull(3_000L) {
                serverApi.health()
            }
        } catch (_: Exception) {
            null
        }
        if (resp == null || !resp.isSuccessful) {
            serverError =
                "서버에 연결할 수 없습니다"
        }
    }

    LaunchedEffect(tabsRestored) {
        if (!tabsRestored) return@LaunchedEffect
        snapshotFlow {
            Triple(
                openPdfTabs.toList(),
                activePdfTabId,
                pdfTabViewports.toMap()
            )
        }.distinctUntilChanged()
            .debounce(400L)
            .collect { (tabs, activeId, viewports) ->
            viewerTabStore.save(
                PersistedViewerTabState(
                    tabs = tabs.map { tab ->
                        val viewport = viewports[tab.documentId]
                            ?: PdfTabViewportState()
                        PersistedPdfTab(
                            documentId = tab.documentId,
                            title = tab.title,
                            pageCount = tab.pageCount,
                            uriString = tab.uriString,
                            firstVisiblePageIndex =
                            viewport.firstVisiblePageIndex,
                            firstVisiblePageScrollOffset =
                            viewport.firstVisiblePageScrollOffset
                        )
                    },
                    activeDocumentId = activeId
                        ?.takeIf { id ->
                            tabs.any {
                                it.documentId == id
                            }
                        }
                )
            )
        }
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

    fun viewerRouteFor(tab: OpenPdfTab): String {
        return Screen.Viewer.createRoute(
            tab.documentId,
            tab.pageCount,
            Uri.encode(tab.uriString)
        )
    }

    fun openPdfTab(tab: OpenPdfTab) {
        val existingIndex = openPdfTabs.indexOfFirst {
            it.documentId == tab.documentId
        }
        if (existingIndex >= 0) {
            openPdfTabs[existingIndex] = tab
        } else {
            openPdfTabs += tab
        }
        if (pdfTabViewports[tab.documentId] == null) {
            pdfTabViewports[tab.documentId] =
                PdfTabViewportState()
        }
        activePdfTabId = tab.documentId
        navController.navigate(viewerRouteFor(tab)) {
            launchSingleTop = true
        }
    }

    fun selectPdfTab(tab: OpenPdfTab) {
        activePdfTabId = tab.documentId
    }

    fun closePdfTab(documentId: String) {
        val closingIndex = openPdfTabs.indexOfFirst {
            it.documentId == documentId
        }
        if (closingIndex < 0) return
        val wasActive = activePdfTabId == documentId
        openPdfTabs.removeAt(closingIndex)
        pdfTabViewports.remove(documentId)
        if (!wasActive) return
        val nextTab = openPdfTabs.getOrNull(
            closingIndex.coerceAtMost(
                openPdfTabs.lastIndex
            )
        )
        activePdfTabId = nextTab?.documentId
        if (nextTab == null) {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Home.route) {
                    inclusive = false
                }
                launchSingleTop = true
            }
        }
    }

    fun updatePdfTabViewport(
        documentId: String,
        pageIndex: Int,
        scrollOffset: Int
    ) {
        val tab = openPdfTabs.find {
            it.documentId == documentId
        } ?: return
        val normalizedPageIndex = pageIndex.coerceIn(
            0,
            (tab.pageCount - 1).coerceAtLeast(0)
        )
        val normalizedScrollOffset =
            (scrollOffset.coerceAtLeast(0) / 24) * 24
        val current = pdfTabViewports[documentId]
        if (
            current?.firstVisiblePageIndex ==
            normalizedPageIndex &&
            current.firstVisiblePageScrollOffset ==
            normalizedScrollOffset
        ) {
            return
        }
        pdfTabViewports[documentId] =
            PdfTabViewportState(
                firstVisiblePageIndex = normalizedPageIndex,
                firstVisiblePageScrollOffset =
                normalizedScrollOffset
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
                    openPdfTab(
                        OpenPdfTab(
                            documentId = doc.id,
                            title = doc.displayName,
                            pageCount = doc.pageCount,
                            uriString = doc.uriString
                        )
                    )
                },
                onOpenSettings = {
                    navController.navigate(
                        Screen.Settings.route
                    )
                },
                onOpenProfile = {
                    navController.navigate(
                        Screen.Profile.route
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
            LaunchedEffect(pdfId, pageCount, pdfUri) {
                if (openPdfTabs.none {
                        it.documentId == pdfId
                    }
                ) {
                    openPdfTabs += OpenPdfTab(
                        documentId = pdfId,
                        title = "PDF",
                        pageCount = pageCount,
                        uriString = pdfUri.toString()
                    )
                }
                if (pdfTabViewports[pdfId] == null) {
                    pdfTabViewports[pdfId] =
                        PdfTabViewportState()
                }
                activePdfTabId = pdfId
            }

            val routeTab = OpenPdfTab(
                documentId = pdfId,
                title = "PDF",
                pageCount = pageCount,
                uriString = pdfUri.toString()
            )
            val activeTab = openPdfTabs.find {
                it.documentId == activePdfTabId
            } ?: routeTab
            val activeViewport =
                pdfTabViewports[activeTab.documentId]
                    ?: PdfTabViewportState()
            val activePdfUri = Uri.parse(activeTab.uriString)

            val viewModel: ViewerViewModel =
                koinViewModel(
                    key = "viewer-${activeTab.documentId}"
                ) {
                    parametersOf(
                        activeTab.documentId,
                        activeTab.pageCount,
                        activePdfUri
                    )
                }
            val llmService: LlmService =
                koinInject()
            val quizService: QuizService =
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
                quizService = quizService,
                settingsRepository = settingsRepo,
                conversationDataSource =
                convDataSource,
                openTabs = openPdfTabs,
                activeTabId = activeTab.documentId,
                initialFirstVisiblePageIndex =
                activeViewport.firstVisiblePageIndex,
                initialFirstVisiblePageScrollOffset =
                activeViewport.firstVisiblePageScrollOffset,
                onSelectTab = { tab ->
                    selectPdfTab(tab)
                },
                onCloseTab = { tab ->
                    closePdfTab(tab.documentId)
                },
                onOpenNewTab = {
                    navController.navigate(Screen.Home.route) {
                        launchSingleTop = true
                    }
                },
                onScrollPositionChanged = { pageIndex, scrollOffset ->
                    updatePdfTabViewport(
                        activeTab.documentId,
                        pageIndex,
                        scrollOffset
                    )
                },
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

        composable(Screen.Profile.route) {
            val viewModel: ProfileViewModel =
                koinViewModel()

            BackHandler {
                navController.popBackStack()
            }

            ProfileScreen(
                viewModel = viewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }

}
