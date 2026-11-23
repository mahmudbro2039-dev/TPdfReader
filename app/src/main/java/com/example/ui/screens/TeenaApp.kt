package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AboutTeenaDialog
import com.example.ui.components.DeviceAccessStatusBanner
import com.example.ui.components.DeviceScanProgressStrip
import com.example.ui.components.DocumentActionsSheet
import com.example.ui.components.SortBottomSheet
import com.example.ui.components.TeenaBottomBar
import com.example.ui.components.TeenaTab
import com.example.ui.components.TeenaTopBar
import com.example.ui.components.ThumbnailCache
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
    onOpenSettings: () -> Unit,
    onRequestAllFilesAccess: () -> Unit = {},
    onRequestBatteryExemption: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val liveQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    var currentTab by rememberSaveable { mutableStateOf(TeenaTab.Recent) }
    val tabStateHolder = rememberSaveableStateHolder()

    val systemDark = isSystemInDarkTheme()
    val isCurrentlyDark = when (uiState.themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val motion = TeenaTheme.motion
    val recentListState = rememberLazyListState()
    val libraryListState = rememberLazyListState()
    val libraryGridState = rememberLazyGridState()
    val foldersListState = rememberLazyListState()
    val foldersGridState = rememberLazyGridState()

    val prewarmUris = remember(uiState.libraryItems, uiState.folderItems) {
        (uiState.libraryItems.take(9).map { it.uri } +
            uiState.folderItems.take(4).flatMap { it.previewUris })
            .distinct()
            .take(12)
    }
    LaunchedEffect(prewarmUris) {
        if (prewarmUris.isNotEmpty()) {
            ThumbnailCache.prewarm(context, prewarmUris)
        }
    }

    var showSortSheet by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var activeItemForMenu by remember { mutableStateOf<PdfItem?>(null) }

    val menuCallbacks = remember(isCurrentlyDark, viewModel, onPickPdf, onOpenSettings) {
        TopBarMenuCallbacks(
            onToggleDarkMode = { viewModel.toggleDarkMode(isCurrentlyDark) },
            onToggleDeepSearch = { viewModel.toggleDeepSearch() },
            onOpenSort = { showSortSheet = true },
            onToggleViewMode = { viewModel.toggleViewMode() },
            onPickPdf = onPickPdf,
            onRescanDevice = { viewModel.triggerDeviceScan() },
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

    val isSearchMode = liveQuery.isNotBlank()
    BackHandler(enabled = !isSearchMode && currentTab != TeenaTab.Recent) {
        currentTab = TeenaTab.Recent
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TeenaTopBar(
                query = liveQuery,
                onQueryChange = viewModel::onQueryChange,
                onSearch = viewModel::onSubmitSearch,
                menu = menuActions
            )
        },
        bottomBar = {
            TeenaBottomBar(
                currentTab = currentTab,
                onSelectTab = { targetTab ->
                    if (liveQuery.isNotEmpty()) {
                        viewModel.onQueryChange("")
                        focusManager.clearFocus()
                    }
                    if (currentTab != targetTab) {
                        currentTab = targetTab
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isScanningDevice) {
                DeviceScanProgressStrip(foundCount = uiState.scannedPdfCount)
            }
            if (!uiState.isAccessBannerDismissed &&
                (!uiState.hasAllFilesAccess || !uiState.isBatteryOptimizationIgnored)
            ) {
                DeviceAccessStatusBanner(
                    hasAllFilesAccess = uiState.hasAllFilesAccess,
                    isBatteryOptimizationIgnored = uiState.isBatteryOptimizationIgnored,
                    onRequestAllFilesAccess = onRequestAllFilesAccess,
                    onRequestBatteryExemption = onRequestBatteryExemption,
                    onDismiss = viewModel::dismissAccessBanner
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isSearchMode) {
                    SearchResultsScreen(
                        isSearching = uiState.isSearching,
                        results = uiState.searchResults,
                        onOpenPdf = { onOpenDocument(it.uri, null) },
                        onItemMore = { activeItemForMenu = it },
                        onPickPdf = onPickPdf
                    )
                } else {
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            val forward = targetState.ordinal >= initialState.ordinal
                            val enter = fadeIn(motion.tabEnterSpec()) + slideInHorizontally(motion.tabSlideSpec()) { w ->
                                if (forward) w / 18 else -w / 18
                            }
                            val exit = fadeOut(motion.tabExitSpec()) + slideOutHorizontally(motion.tabSlideSpec()) { w ->
                                if (forward) -w / 24 else w / 24
                            }
                            (enter togetherWith exit) using null
                        },
                        label = "tab_switch"
                    ) { tab ->
                        tabStateHolder.SaveableStateProvider(tab.route) {
                            when (tab) {
                                TeenaTab.Recent -> RecentTabScreen(
                                    isLoading = uiState.isLoading,
                                    heroItem = uiState.heroItem,
                                    remainingItems = uiState.recentRemainingItems,
                                    listState = recentListState,
                                    onOpenPdf = { onOpenDocument(it.uri, null) },
                                    onItemMore = { activeItemForMenu = it },
                                    onPickPdf = onPickPdf
                                )

                                TeenaTab.Library -> LibraryTabScreen(
                                    isLoading = uiState.isLoading,
                                    items = uiState.libraryItems,
                                    viewMode = uiState.viewMode,
                                    selectedStatus = uiState.selectedStatusFilter,
                                    statusCounts = uiState.statusCounts,
                                    totalCount = uiState.totalCount,
                                    listState = libraryListState,
                                    gridState = libraryGridState,
                                    onSelectStatus = viewModel::selectStatusFilter,
                                    onOpenPdf = { onOpenDocument(it.uri, null) },
                                    onItemMore = { activeItemForMenu = it },
                                    onPickPdf = onPickPdf
                                )

                                TeenaTab.Folders -> FoldersTabScreen(
                                    isLoading = uiState.isLoading,
                                    folders = uiState.folderItems,
                                    selectedFolderName = uiState.selectedFolderName,
                                    folderPdfs = uiState.currentFolderItems,
                                    listState = foldersListState,
                                    gridState = foldersGridState,
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
