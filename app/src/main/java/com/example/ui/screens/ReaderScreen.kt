package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.PdfColorMode
import com.example.data.model.ScrollLayoutMode
import com.example.ui.components.AnnotationsBottomSheet
import com.example.ui.components.BookmarksAndTocBottomSheet
import com.example.ui.components.DocumentLinksBottomSheet
import com.example.ui.components.GoToPageDialog
import com.example.ui.components.PdfDocumentViewport
import com.example.ui.components.QuickToolsBottomSheet
import com.example.ui.components.ShareQuoteBottomSheet
import com.example.ui.components.SignaturePadBottomSheet
import com.example.ui.components.pdfColorFilterFor
import com.example.ui.components.readerCanvasColorFor
import com.example.ui.theme.TeenaTheme
import com.example.ui.viewmodel.ReaderLoadState
import com.example.ui.viewmodel.ReaderUiState
import com.example.ui.viewmodel.ReaderViewModel
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ReaderScreen(
    uriString: String,
    initialPage: Int?,
    viewModel: ReaderViewModel,
    onNavigateBack: () -> Unit,
    onOpenTextMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onRotateScreen: () -> Unit,
    onSharePdf: (uri: String, title: String) -> Unit,
    onPrintPdf: (uri: String, title: String) -> Unit
) {
    val s = TeenaTheme.spacing
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val view = LocalView.current
    val context = LocalContext.current
    val activity = context as? Activity

    var showGoToPageDialog by remember { mutableStateOf(false) }
    var showBookmarksAndTocSheet by remember { mutableStateOf(false) }
    var showAnnotationsSheet by remember { mutableStateOf(false) }
    var showSignaturePadSheet by remember { mutableStateOf(false) }
    var showShareQuoteSheet by remember { mutableStateOf(false) }
    var showLinksSheet by remember { mutableStateOf(false) }
    var showToolsSheet by remember { mutableStateOf(false) }

    var shareQuoteInitialText by remember { mutableStateOf("") }
    var shareQuotePageNumber by remember { mutableIntStateOf(1) }

    LaunchedEffect(uriString, initialPage) {
        viewModel.openDocument(uriString, initialPage)
    }

    LaunchedEffect(state.isChromeVisible) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (state.isChromeVisible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(prefs.keepScreenOn, state.brightnessOverride) {
        val window = activity?.window
        if (window != null) {
            if (prefs.keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            val attrs = window.attributes
            attrs.screenBrightness =
                state.brightnessOverride ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = attrs
        }
        onDispose {
            val w = activity?.window
            if (w != null) {
                w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val attrs = w.attributes
                attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                w.attributes = attrs
                WindowCompat.getInsetsController(w, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val isDark = isSystemInDarkTheme()
    val canvasColor = readerCanvasColorFor(state.colorMode, isDark)
    val colorFilter = remember(state.colorMode) { pdfColorFilterFor(state.colorMode) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(canvasColor)
    ) {
        when (val load = state.loadState) {
            ReaderLoadState.Loading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            is ReaderLoadState.Error -> {
                ReaderErrorState(
                    message = load.message,
                    onRetry = { viewModel.openDocument(uriString, initialPage) },
                    onBack = onNavigateBack
                )
            }

            ReaderLoadState.Ready -> {
                PdfDocumentViewport(
                    state = state,
                    pageGapSpacing = prefs.pageGapSpacing,
                    colorFilter = colorFilter,
                    canvasColor = canvasColor,
                    renderPage = viewModel::renderPageBitmap,
                    onPageChanged = viewModel::onPageScrolled,
                    onPendingScrollConsumed = viewModel::consumePendingScrollTarget,
                    onZoomChange = viewModel::setZoomScale,
                    onToggleChrome = viewModel::toggleChromeVisibility,
                    onStepPage = viewModel::stepPage,
                    onStopAutoScroll = viewModel::stopAutoScroll
                )

                if (prefs.showPageIndicatorPill && !state.isChromeVisible) {
                    val progressPct = if (state.pageCount <= 0) {
                        0
                    } else {
                        ((state.currentPage + 1).toFloat() / state.pageCount.toFloat() * 100f)
                            .coerceIn(0f, 100f)
                            .roundToInt()
                    }
                    Surface(
                        onClick = { showGoToPageDialog = true },
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.84f),
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .navigationBarsPadding()
                            .padding(start = s.screenH, bottom = s.screenH)
                            .testTag("floating_page_pill")
                    ) {
                        Text(
                            text = "${state.currentPage + 1} / ${state.pageCount.coerceAtLeast(1)}  ·  $progressPct%",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = s.md, vertical = s.xs)
                        )
                    }
                }

                if (prefs.showEdgeShortcutButtons) {
                    FloatingEdgeShortcuts(
                        isZoomLocked = state.isZoomLocked,
                        hideMargins = state.hideMargins,
                        isAutoScrollActive = state.isAutoScrollActive,
                        colorMode = state.colorMode,
                        isBrightnessOpen = state.isBrightnessOverlayVisible,
                        onToggleZoomLock = viewModel::toggleZoomLock,
                        onToggleHideMargins = viewModel::toggleHideMargins,
                        onToggleBrightness = viewModel::toggleBrightnessOverlay,
                        onRotateScreen = onRotateScreen,
                        onToggleAutoScroll = viewModel::toggleAutoScroll,
                        onCycleColorMode = viewModel::cycleColorMode,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = s.sm)
                    )
                }

                AnimatedVisibility(
                    visible = state.isAutoScrollActive,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = if (state.isChromeVisible) s.readerBarClearance else s.xl)
                ) {
                    AutoScrollControlPill(
                        speed = state.autoScrollSpeed,
                        onDecrease = { viewModel.adjustAutoScrollSpeed(-1) },
                        onIncrease = { viewModel.adjustAutoScrollSpeed(1) },
                        onStop = viewModel::stopAutoScroll
                    )
                }

                AnimatedVisibility(
                    visible = state.isBrightnessOverlayVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = s.xl)
                ) {
                    BrightnessControlOverlay(
                        brightness = state.brightnessOverride ?: 0.65f,
                        onBrightnessChange = viewModel::setBrightnessOverride,
                        onResetAuto = { viewModel.setBrightnessOverride(null) },
                        onClose = viewModel::toggleBrightnessOverlay
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.isChromeVisible || state.loadState !is ReaderLoadState.Ready,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ReaderTopBar(
                    state = state,
                    onBack = onNavigateBack,
                    onJumpBack = viewModel::jumpBackInHistory,
                    onJumpForward = viewModel::jumpForwardInHistory,
                    onToggleSearch = viewModel::toggleSearchBar,
                    onToggleBookmark = viewModel::toggleCurrentPageBookmark,
                    onOpenTextMode = onOpenTextMode,
                    onOpenTools = { showToolsSheet = true }
                )

                AnimatedVisibility(visible = state.isSearchBarVisible) {
                    InPdfSearchOverlayBar(
                        query = state.searchQuery,
                        matchCount = state.searchMatches.size,
                        activeMatchIndex = state.activeSearchMatchIndex,
                        activeSnippet = state.searchMatches.getOrNull(state.activeSearchMatchIndex)?.snippet,
                        onQueryChange = viewModel::updateSearchQuery,
                        onPrevMatch = { viewModel.navigateSearchMatch(-1) },
                        onNextMatch = { viewModel.navigateSearchMatch(1) },
                        onClose = viewModel::closeSearchBar
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.isChromeVisible && state.loadState is ReaderLoadState.Ready,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ReaderBottomBar(
                state = state,
                onScrubPage = { page -> viewModel.jumpToPage(page, recordHistory = false) },
                onPagePillClick = { showGoToPageDialog = true },
                onCycleLayout = {
                    val modes = ScrollLayoutMode.entries
                    val next = modes[(modes.indexOf(state.layoutMode) + 1) % modes.size]
                    viewModel.setLayoutMode(next)
                },
                onCycleColorMode = viewModel::cycleColorMode,
                onOpenTocAndBookmarks = { showBookmarksAndTocSheet = true },
                onOpenAnnotations = { showAnnotationsSheet = true },
                onOpenTools = { showToolsSheet = true }
            )
        }
    }

    if (showGoToPageDialog) {
        GoToPageDialog(
            currentPage = state.currentPage,
            pageCount = state.pageCount,
            onDismiss = { showGoToPageDialog = false },
            onGoToPage = { page -> viewModel.jumpToPage(page, recordHistory = true) }
        )
    }

    if (showBookmarksAndTocSheet) {
        BookmarksAndTocBottomSheet(
            currentPage = state.currentPage,
            bookmarks = state.bookmarks,
            tocEntries = state.tocEntries,
            visitedHistory = state.visitedPagesHistory,
            onDismiss = { showBookmarksAndTocSheet = false },
            onAddBookmark = viewModel::addBookmark,
            onJumpToPage = { page -> viewModel.jumpToPage(page, recordHistory = true) },
            onRenameBookmark = viewModel::renameBookmark,
            onDeleteBookmark = viewModel::deleteBookmark
        )
    }

    if (showAnnotationsSheet) {
        val currentText = state.extractedTextPages.getOrElse(state.currentPage) { "" }
        AnnotationsBottomSheet(
            currentPage = state.currentPage,
            currentPageTextSnippet = currentText,
            annotations = state.annotations,
            onDismiss = { showAnnotationsSheet = false },
            onAddAnnotation = viewModel::addHighlightOrNote,
            onJumpToPage = { page -> viewModel.jumpToPage(page, recordHistory = true) },
            onShareQuoteText = { text, pageNum ->
                showAnnotationsSheet = false
                shareQuoteInitialText = text
                shareQuotePageNumber = pageNum
                showShareQuoteSheet = true
            },
            onDeleteAnnotation = viewModel::deleteAnnotation
        )
    }

    if (showSignaturePadSheet) {
        SignaturePadBottomSheet(
            currentPage = state.currentPage,
            savedSignatureVector = prefs.savedSignatureVector,
            onDismiss = { showSignaturePadSheet = false },
            onStampSignature = viewModel::stampSignatureOnCurrentPage
        )
    }

    if (showShareQuoteSheet) {
        val fallbackQuote = state.extractedTextPages.getOrElse(state.currentPage) { "" }
            .lines()
            .firstOrNull { it.length > 32 }
            ?: ""
        ShareQuoteBottomSheet(
            initialQuoteText = shareQuoteInitialText.ifBlank { fallbackQuote },
            bookTitle = state.documentTitle,
            pageNumber = shareQuotePageNumber,
            onDismiss = {
                showShareQuoteSheet = false
                shareQuoteInitialText = ""
            }
        )
    }

    if (showLinksSheet) {
        DocumentLinksBottomSheet(
            links = state.documentLinks,
            onDismiss = { showLinksSheet = false },
            onJumpToPage = { page -> viewModel.jumpToPage(page, recordHistory = true) },
            onOpenExternalUrl = { url ->
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                }
            }
        )
    }

    if (showToolsSheet) {
        QuickToolsBottomSheet(
            state = state,
            keepScreenOn = prefs.keepScreenOn,
            onDismiss = { showToolsSheet = false },
            onSelectLayout = viewModel::setLayoutMode,
            onSelectFitPolicy = viewModel::setPageFitPolicy,
            onSelectColorMode = viewModel::setColorMode,
            onToggleHideMargins = viewModel::toggleHideMargins,
            onToggleIncognito = viewModel::toggleIncognitoMode,
            onToggleRtl = viewModel::toggleRtl,
            onToggleKeepScreenOn = viewModel::setKeepScreenOn,
            onSelectStatus = viewModel::updateReadingStatus,
            onOpenSearch = viewModel::toggleSearchBar,
            onOpenTocAndBookmarks = { showBookmarksAndTocSheet = true },
            onOpenAnnotations = { showAnnotationsSheet = true },
            onOpenSignaturePad = { showSignaturePadSheet = true },
            onOpenShareQuote = {
                shareQuoteInitialText = ""
                shareQuotePageNumber = state.currentPage + 1
                showShareQuoteSheet = true
            },
            onOpenDocumentLinks = { showLinksSheet = true },
            onOpenTextMode = onOpenTextMode,
            onRotateScreen = onRotateScreen,
            onSharePdf = { onSharePdf(state.uriString, state.documentTitle) },
            onPrintPdf = { onPrintPdf(state.uriString, state.documentTitle) },
            onOpenSettings = onOpenSettings
        )
    }
}

@Composable
private fun ReaderTopBar(
    state: ReaderUiState,
    onBack: () -> Unit,
    onJumpBack: () -> Unit,
    onJumpForward: () -> Unit,
    onToggleSearch: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenTextMode: () -> Unit,
    onOpenTools: () -> Unit
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = sz.fabElevation,
        shadowElevation = sz.fabElevation,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = s.xs, vertical = s.xs)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("reader_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.documentTitle.ifBlank { stringResource(R.string.app_name) },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.pageCount > 0) {
                    val marginBadge = if (state.hideMargins) " · Crop" else ""
                    val incogBadge = if (state.incognitoMode) " · Incognito" else ""
                    Text(
                        text = "${state.layoutMode.shortLabel} · ${String.format(Locale.US, "%.1fx", state.zoomScale)}$marginBadge$incogBadge",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (state.canJumpBack) {
                IconButton(
                    onClick = onJumpBack,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = stringResource(R.string.reader_history_back)
                    )
                }
            }

            if (state.canJumpForward) {
                IconButton(
                    onClick = onJumpForward,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = stringResource(R.string.reader_history_forward)
                    )
                }
            }

            IconButton(
                onClick = onToggleSearch,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("reader_search_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(R.string.reader_search_in_pdf),
                    tint = if (state.isSearchBarVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = onToggleBookmark,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("reader_bookmark_toggle")
            ) {
                Icon(
                    imageVector = if (state.isCurrentPageBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = stringResource(R.string.reader_add_bookmark),
                    tint = if (state.isCurrentPageBookmarked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            IconButton(
                onClick = onOpenTextMode,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("reader_text_mode_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Subject,
                    contentDescription = stringResource(R.string.reader_text_mode)
                )
            }

            IconButton(
                onClick = onOpenTools,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("reader_more_tools_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.reader_tools)
                )
            }
        }
    }
}

@Composable
private fun InPdfSearchOverlayBar(
    query: String,
    matchCount: Int,
    activeMatchIndex: Int,
    activeSnippet: String?,
    onQueryChange: (String) -> Unit,
    onPrevMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onClose: () -> Unit
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = sz.heroElevation,
        shadowElevation = sz.heroElevation,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = s.md, vertical = s.sm)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(stringResource(R.string.reader_search_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("in_pdf_search_input")
                )
                Spacer(modifier = Modifier.width(s.sm))
                Text(
                    text = if (matchCount > 0) "${activeMatchIndex + 1}/$matchCount" else "0/0",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = onPrevMatch,
                    enabled = matchCount > 0,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.reader_previous_match)
                    )
                }
                IconButton(
                    onClick = onNextMatch,
                    enabled = matchCount > 0,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.reader_next_match)
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                }
            }
            if (!activeSnippet.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(s.xs))
                Text(
                    text = activeSnippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = s.xs)
                )
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    state: ReaderUiState,
    onScrubPage: (Int) -> Unit,
    onPagePillClick: () -> Unit,
    onCycleLayout: () -> Unit,
    onCycleColorMode: () -> Unit,
    onOpenTocAndBookmarks: () -> Unit,
    onOpenAnnotations: () -> Unit,
    onOpenTools: () -> Unit
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    var sliderValue by remember(state.currentPage, state.pageCount) {
        mutableFloatStateOf(state.currentPage.toFloat())
    }
    val maxIndex = (state.pageCount - 1).coerceAtLeast(1)

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = sz.fabElevation,
        shadowElevation = sz.heroElevation,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = s.md, vertical = s.sm)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    onClick = onPagePillClick,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.testTag("bottom_page_pill")
                ) {
                    Text(
                        text = "${sliderValue.roundToInt() + 1} / ${state.pageCount.coerceAtLeast(1)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = s.md, vertical = s.xs)
                    )
                }

                Spacer(modifier = Modifier.width(s.md))

                Slider(
                    value = sliderValue.coerceIn(0f, maxIndex.toFloat()),
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onScrubPage(sliderValue.roundToInt()) },
                    valueRange = 0f..maxIndex.toFloat(),
                    enabled = state.pageCount > 1,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("reader_page_slider")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val layoutIcon = when (state.layoutMode) {
                    ScrollLayoutMode.VERTICAL -> Icons.Filled.ViewAgenda
                    ScrollLayoutMode.HORIZONTAL -> Icons.Filled.ViewCarousel
                    ScrollLayoutMode.SINGLE_PAGE -> Icons.Filled.ViewDay
                    ScrollLayoutMode.DUAL_PAGE -> Icons.Filled.MenuBook
                }

                BottomActionItem(
                    icon = layoutIcon,
                    label = state.layoutMode.shortLabel,
                    active = false,
                    onClick = onCycleLayout,
                    tag = "reader_layout_button"
                )
                BottomActionItem(
                    icon = if (state.colorMode == PdfColorMode.DARK_INVERT) Icons.Filled.DarkMode else Icons.Filled.Contrast,
                    label = state.colorMode.label.substringBefore(" "),
                    active = state.colorMode != PdfColorMode.NORMAL,
                    onClick = onCycleColorMode,
                    tag = "reader_color_mode_button"
                )
                BottomActionItem(
                    icon = Icons.AutoMirrored.Filled.Toc,
                    label = stringResource(R.string.tab_bookmarks_label),
                    active = state.bookmarks.isNotEmpty(),
                    onClick = onOpenTocAndBookmarks,
                    tag = "reader_bookmarks_sheet_button"
                )
                BottomActionItem(
                    icon = Icons.Filled.Highlight,
                    label = stringResource(R.string.tab_highlights_label),
                    active = state.annotations.isNotEmpty(),
                    onClick = onOpenAnnotations,
                    tag = "reader_annotations_button"
                )
                BottomActionItem(
                    icon = Icons.Filled.Tune,
                    label = stringResource(R.string.reader_tools),
                    active = false,
                    onClick = onOpenTools,
                    tag = "reader_tools_button"
                )
            }
        }
    }
}

@Composable
private fun BottomActionItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .padding(horizontal = s.sm, vertical = s.xs)
            .testTag(tag)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(sz.iconTool))
        Spacer(modifier = Modifier.height(s.xxs))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

@Composable
private fun FloatingEdgeShortcuts(
    isZoomLocked: Boolean,
    hideMargins: Boolean,
    isAutoScrollActive: Boolean,
    colorMode: PdfColorMode,
    isBrightnessOpen: Boolean,
    onToggleZoomLock: () -> Unit,
    onToggleHideMargins: () -> Unit,
    onToggleBrightness: () -> Unit,
    onRotateScreen: () -> Unit,
    onToggleAutoScroll: () -> Unit,
    onCycleColorMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(TeenaTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        EdgeShortcutChip(
            icon = if (isZoomLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
            description = stringResource(R.string.reader_zoom_lock),
            active = isZoomLocked,
            onClick = onToggleZoomLock,
            tag = "shortcut_zoom_lock"
        )
        EdgeShortcutChip(
            icon = Icons.Filled.CropFree,
            description = stringResource(R.string.reader_hide_margins),
            active = hideMargins,
            onClick = onToggleHideMargins,
            tag = "shortcut_hide_margins"
        )
        EdgeShortcutChip(
            icon = Icons.Filled.Brightness6,
            description = stringResource(R.string.reader_brightness),
            active = isBrightnessOpen,
            onClick = onToggleBrightness,
            tag = "shortcut_brightness"
        )
        EdgeShortcutChip(
            icon = Icons.Filled.ScreenRotation,
            description = stringResource(R.string.reader_rotate),
            active = false,
            onClick = onRotateScreen,
            tag = "shortcut_rotate"
        )
        EdgeShortcutChip(
            icon = if (isAutoScrollActive) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            description = stringResource(R.string.reader_auto_scroll),
            active = isAutoScrollActive,
            onClick = onToggleAutoScroll,
            tag = "shortcut_autoscroll"
        )
        EdgeShortcutChip(
            icon = Icons.Filled.DarkMode,
            description = stringResource(R.string.reader_color_mode),
            active = colorMode != PdfColorMode.NORMAL,
            onClick = onCycleColorMode,
            tag = "shortcut_dark_mode"
        )
    }
}

@Composable
private fun EdgeShortcutChip(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    val sz = TeenaTheme.sizes
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
        },
        contentColor = if (active) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        shadowElevation = sz.cardElevation,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(sz.shortcutPillSize)
            .testTag(tag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = description, modifier = Modifier.size(sz.iconTool))
        }
    }
}

@Composable
private fun AutoScrollControlPill(
    speed: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onStop: () -> Unit
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = sz.heroElevation
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = s.sm, vertical = s.xs)
        ) {
            IconButton(
                onClick = onDecrease,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.reader_scroll_slower))
            }
            Text(
                text = stringResource(R.string.reader_speed_label, speed),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = s.sm)
            )
            IconButton(
                onClick = onIncrease,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.reader_scroll_faster))
            }
            IconButton(
                onClick = onStop,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
            }
        }
    }
}

@Composable
private fun BrightnessControlOverlay(
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    onResetAuto: () -> Unit,
    onClose: () -> Unit
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = sz.heroElevation,
        shadowElevation = sz.menuElevation,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(s.lg)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Brightness6, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(s.sm))
                Text(
                    text = stringResource(R.string.reader_brightness),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onResetAuto) {
                    Text(stringResource(R.string.reader_system_brightness))
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                }
            }
            Slider(
                value = brightness,
                onValueChange = onBrightnessChange,
                valueRange = 0.05f..1f
            )
        }
    }
}

@Composable
private fun ReaderErrorState(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(s.xxl)
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(sz.iconButton)
        )
        Spacer(modifier = Modifier.height(s.lg))
        Text(
            text = stringResource(R.string.reader_error_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(s.sm))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(s.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(s.md)) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            Button(onClick = onRetry) { Text(stringResource(R.string.reader_error_retry)) }
        }
    }
}
