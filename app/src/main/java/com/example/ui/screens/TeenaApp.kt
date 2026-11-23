package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.AboutTeenaDialog
import com.example.ui.components.DocumentActionsSheet
import com.example.ui.components.FabController
import com.example.ui.components.OpenPdfFab
import com.example.ui.components.SortBottomSheet
import com.example.ui.components.TeenaBottomBar
import com.example.ui.components.TeenaTab
import com.example.ui.components.TeenaTopBar
import com.example.ui.components.TopBarMenuCallbacks
import com.example.ui.components.buildMenu
import com.example.ui.model.PdfItem
import com.example.ui.theme.TeenaTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.AppViewModel

@Composable
fun TeenaApp(
    viewModel: AppViewModel,
    onPickPdf: () -> Unit,
    onOpenDocument: (uriString: String, pageIndex: Int?) -> Unit,
    onOpenSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tabNavController = rememberNavController()
    val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: TeenaTab.Recent.route
    val currentTab = remember(currentRoute) {
        TeenaTab.entries.firstOrNull { it.route == currentRoute } ?: TeenaTab.Recent
    }

    val systemDark = isSystemInDarkTheme()
    val isCurrentlyDark = when (uiState.themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    val fabController = remember { FabController() }
    val recentListState = rememberLazyListState()
    val libraryListState = rememberLazyListState()
    val libraryGridState = rememberLazyGridState()
    val foldersListState = rememberLazyListState()

    var showSortSheet by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var activeItemForMenu by remember { mutableStateOf<PdfItem?>(null) }

    val menuCallbacks = remember(isCurrentlyDark, viewModel, onOpenSettings) {
        TopBarMenuCallbacks(
            onToggleDarkMode = { viewModel.toggleDarkMode(isCurrentlyDark) },
            onToggleDeepSearch = { viewModel.toggleDeepSearch() },
            onOpenSort = { showSortSheet = true },
            onToggleViewMode = { viewModel.toggleViewMode() },
            onOpenSettings = onOpenSettings,
            onOpenAbout = { showAboutDialog = true }
        )
    }

    val menuActions = buildMenu(
        tab = currentTab,
        isDark = isCurrentlyDark,
        deepSearch = uiState.deepSearch,
        viewMode = uiState.viewMode,
        callbacks = menuCallbacks
    )

    val motion = TeenaTheme.motion

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TeenaTopBar(
                query = uiState.query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = viewModel::onSubmitSearch,
                menu = menuActions
            )
        },
        bottomBar = {
            TeenaBottomBar(
                currentTab = currentTab,
                onSelectTab = { targetTab ->
                    if (uiState.query.isNotBlank()) {
                        viewModel.onQueryChange("")
                    }
                    fabController.expanded = true
                    tabNavController.navigate(targetTab.route) {
                        popUpTo(tabNavController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        },
        floatingActionButton = {
            OpenPdfFab(
                expanded = fabController.expanded,
                onClick = onPickPdf
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isSearchMode = uiState.query.isNotBlank()
            AnimatedContent(
                targetState = isSearchMode,
                transitionSpec = {
                    fadeIn(motion.normalSpec()) togetherWith fadeOut(motion.fastSpec())
                },
                label = "search_or_tabs"
            ) { searching ->
                if (searching) {
                    SearchResultsScreen(
                        isSearching = uiState.isSearching,
                        results = uiState.searchResults,
                        onOpenPdf = { onOpenDocument(it.uri, null) },
                        onItemMore = { activeItemForMenu = it },
                        onPickPdf = onPickPdf
                    )
                } else {
                    NavHost(
                        navController = tabNavController,
                        startDestination = TeenaTab.Recent.route,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable(TeenaTab.Recent.route) {
                            RecentTabScreen(
                                isLoading = uiState.isLoading,
                                heroItem = uiState.heroItem,
                                remainingItems = uiState.recentRemainingItems,
                                listState = recentListState,
                                fabController = fabController,
                                onOpenPdf = { onOpenDocument(it.uri, null) },
                                onItemMore = { activeItemForMenu = it },
                                onPickPdf = onPickPdf
                            )
                        }

                        composable(TeenaTab.Library.route) {
                            LibraryTabScreen(
                                isLoading = uiState.isLoading,
                                items = uiState.libraryItems,
                                viewMode = uiState.viewMode,
                                selectedStatus = uiState.selectedStatusFilter,
                                statusCounts = uiState.statusCounts,
                                totalCount = uiState.totalCount,
                                listState = libraryListState,
                                gridState = libraryGridState,
                                fabController = fabController,
                                onSelectStatus = viewModel::selectStatusFilter,
                                onOpenPdf = { onOpenDocument(it.uri, null) },
                                onItemMore = { activeItemForMenu = it },
                                onPickPdf = onPickPdf
                            )
                        }

                        composable(TeenaTab.Folders.route) {
                            FoldersTabScreen(
                                isLoading = uiState.isLoading,
                                folders = uiState.folderItems,
                                selectedFolderName = uiState.selectedFolderName,
                                folderPdfs = uiState.currentFolderItems,
                                listState = foldersListState,
                                fabController = fabController,
                                onOpenFolder = viewModel::openFolder,
                                onCloseFolder = viewModel::closeFolder,
                                onOpenPdf = { onOpenDocument(it.uri, null) },
                                onItemMore = { activeItemForMenu = it },
                                onPickPdf = onPickPdf
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSortSheet) {
        SortBottomSheet(
            selectedSort = uiState.sort,
            onSelectSort = viewModel::setSortOrder,
            onDismiss = { showSortSheet = false }
        )
    }

    if (showAboutDialog) {
        AboutTeenaDialog(onDismiss = { showAboutDialog = false })
    }

    activeItemForMenu?.let { item ->
        val folderNames = remember(uiState.folderItems) {
            uiState.folderItems.map { it.name }
        }
        DocumentActionsSheet(
            item = item,
            existingFolders = folderNames,
            onDismiss = { activeItemForMenu = null },
            onTogglePin = { viewModel.togglePin(item) },
            onSelectStatus = { status -> viewModel.updateStatus(item.uri, status) },
            onMoveToFolder = { folder -> viewModel.moveToFolder(item.uri, folder) },
            onRemove = { viewModel.removeDocument(item.uri) }
        )
    }
}
