package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.PdfColorMode
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
import com.example.ui.theme.EdgeHandleShape
import com.example.ui.theme.TeenaTheme
import com.example.ui.viewmodel.ReaderLoadState
import com.example.ui.viewmodel.ReaderViewModel
import kotlinx.coroutines.delay
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
    val sz = TeenaTheme.sizes
    val motion = TeenaTheme.motion
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
    var isQuickMenuExpanded by remember(uriString) { mutableStateOf(false) }

    var shareQuoteInitialText by remember { mutableStateOf("") }
    var shareQuotePageNumber by remember { mutableIntStateOf(1) }

    BackHandler(enabled = state.isSearchBarVisible || isQuickMenuExpanded) {
        if (state.isSearchBarVisible) {
            viewModel.closeSearchBar()
        } else {
            isQuickMenuExpanded = false
        }
    }

    LaunchedEffect(uriString, initialPage) {
        viewModel.openDocument(uriString, initialPage)
    }

    var barsHiddenByReader by remember { mutableStateOf(false) }
    LaunchedEffect(state.isChromeVisible) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        if (!state.isChromeVisible) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            barsHiddenByReader = true
        } else if (barsHiddenByReader) {
            controller.show(WindowInsetsCompat.Type.systemBars())
            barsHiddenByReader = false
        }
    }

    var appliedBrightnessOverride by remember { mutableStateOf(false) }
    DisposableEffect(prefs.keepScreenOn, state.brightnessOverride) {
        val window = activity?.window
        if (window != null) {
            if (prefs.keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            val override = state.brightnessOverride
            if (override != null) {
                val attrs = window.attributes
                attrs.screenBrightness = override
                window.attributes = attrs
                appliedBrightnessOverride = true
            } else if (appliedBrightnessOverride) {
                val attrs = window.attributes
                attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = attrs
                appliedBrightnessOverride = false
            }
        }
        onDispose {
            val w = activity?.window
            if (w != null) {
                w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (appliedBrightnessOverride) {
                    val attrs = w.attributes
                    attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    w.attributes = attrs
                    appliedBrightnessOverride = false
                }
                if (barsHiddenByReader) {
                    WindowCompat.getInsetsController(w, view)
                        .show(WindowInsetsCompat.Type.systemBars())
                    barsHiddenByReader = false
                }
            }
        }
    }

    val isCurrentDoc = state.uriString == uriString
    val effectiveLoadState = if (isCurrentDoc) state.loadState else ReaderLoadState.Loading
    val effectiveColorMode = if (isCurrentDoc) state.colorMode else prefs.defaultPdfColorMode
    val isDark = TeenaTheme.colors.isDark
    val canvasColor = readerCanvasColorFor(effectiveColorMode, isDark)
    val colorFilter = remember(effectiveColorMode) { pdfColorFilterFor(effectiveColorMode) }

    var showLoadingSpinner by remember(uriString) { mutableStateOf(false) }
    LaunchedEffect(effectiveLoadState, uriString) {
        if (effectiveLoadState is ReaderLoadState.Loading) {
            delay(220L)
            showLoadingSpinner = true
        } else {
            showLoadingSpinner = false
        }
    }

    val containerBg = if (effectiveLoadState is ReaderLoadState.Ready) {
        canvasColor
    } else {
        MaterialTheme.colorScheme.background
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(containerBg)
    ) {
        AnimatedVisibility(
            visible = state.isChromeVisible || effectiveLoadState !is ReaderLoadState.Ready,
            enter = expandVertically(motion.fastSpec()) + fadeIn(motion.fastSpec()),
            exit = shrinkVertically(motion.fastSpec()) + fadeOut(motion.fastSpec())
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ReaderTopBar(
                    title = state.documentTitle.ifBlank { stringResource(R.string.reader_default_title) },
                    onBack = onNavigateBack,
                    onEnterFullScreen = {
                        isQuickMenuExpanded = false
                        viewModel.setChromeVisible(false)
                    },
                    onOpenMenu = { showToolsSheet = true }
                )

                AnimatedVisibility(visible = state.isSearchBarVisible) {
                    InPdfSearchOverlayBar(
                        query = state.searchQuery,
                        matchCount = state.searchMatches.size,
                        activeMatchIndex = state.activeSearchMatchIndex,
                        activeSnippet = state.searchMatches.getOrNull(state.activeSearchMatchIndex)?.snippet,
                        isExtractingText = state.isExtractingText,
                        onQueryChange = viewModel::updateSearchQuery,
                        onPrevMatch = { viewModel.navigateSearchMatch(-1) },
                        onNextMatch = { viewModel.navigateSearchMatch(1) },
                        onClose = viewModel::closeSearchBar
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (val load = effectiveLoadState) {
                ReaderLoadState.Loading -> {
                    if (showLoadingSpinner) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
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
                        renderQuality = prefs.renderQuality,
                        colorFilter = colorFilter,
                        canvasColor = canvasColor,
                        peekPage = viewModel::peekPageBitmap,
                        renderPage = viewModel::renderPageBitmap,
                        onPageChanged = viewModel::onPageScrolled,
                        onPendingScrollConsumed = viewModel::consumePendingScrollTarget,
                        onZoomChange = viewModel::setZoomScale,
                        onToggleChrome = {
                            if (isQuickMenuExpanded) {
                                isQuickMenuExpanded = false
                            } else {
                                viewModel.toggleChromeVisibility()
                            }
                        },
                        onStepPage = viewModel::stepPage,
                        onStopAutoScroll = viewModel::stopAutoScroll
                    )

                    if (prefs.showPageIndicatorPill) {
                        val progressPct = if (state.pageCount <= 0) {
                            0
                        } else {
                            ((state.currentPage + 1).toFloat() / state.pageCount.toFloat() * 100f)
                                .coerceIn(0f, 100f)
                                .roundToInt()
                        }
                        Surface(
                            onClick = { showGoToPageDialog = true },
                            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.82f),
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

                    androidx.compose.animation.AnimatedVisibility(
                        visible = !state.isChromeVisible,
                        enter = fadeIn(motion.fastSpec()),
                        exit = fadeOut(motion.fastSpec()),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = s.sm, end = s.sm)
                    ) {
                        Surface(
                            onClick = { viewModel.setChromeVisible(true) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            shadowElevation = sz.cardElevation,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(sz.fullscreenBadgeSize)
                                .testTag("reader_exit_fullscreen_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.FullscreenExit,
                                    contentDescription = stringResource(R.string.reader_exit_fullscreen),
                                    modifier = Modifier.size(sz.iconTool)
                                )
                            }
                        }
                    }

                    if (prefs.showEdgeShortcutButtons) {
                        CollapsibleEdgeQuickMenu(
                            expanded = isQuickMenuExpanded,
                            isBookmarked = state.isCurrentPageBookmarked,
                            isZoomLocked = state.isZoomLocked,
                            hideMargins = state.hideMargins,
                            isAutoScrollActive = state.isAutoScrollActive,
                            colorMode = state.colorMode,
                            isBrightnessOpen = state.isBrightnessOverlayVisible,
                            onExpand = { isQuickMenuExpanded = true },
                            onCollapse = { isQuickMenuExpanded = false },
                            onToggleBookmark = viewModel::toggleCurrentPageBookmark,
                            onToggleZoomLock = viewModel::toggleZoomLock,
                            onToggleHideMargins = viewModel::toggleHideMargins,
                            onToggleBrightness = viewModel::toggleBrightnessOverlay,
                            onRotateScreen = onRotateScreen,
                            onToggleAutoScroll = viewModel::toggleAutoScroll,
                            onCycleColorMode = viewModel::cycleColorMode,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = state.isAutoScrollActive,
                        enter = fadeIn(motion.fastSpec()),
                        exit = fadeOut(motion.fastSpec()),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = s.xl)
                    ) {
                        AutoScrollControlPill(
                            speed = state.autoScrollSpeed,
                            onDecrease = { viewModel.adjustAutoScrollSpeed(-1) },
                            onIncrease = { viewModel.adjustAutoScrollSpeed(1) },
                            onStop = viewModel::stopAutoScroll
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = state.isBrightnessOverlayVisible,
                        enter = fadeIn(motion.fastSpec()),
                        exit = fadeOut(motion.fastSpec()),
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
            onToggleBookmark = viewModel::toggleCurrentPageBookmark,
            onOpenGoToPage = { showGoToPageDialog = true },
            onToggleAutoScroll = viewModel::toggleAutoScroll,
            onJumpBack = viewModel::jumpBackInHistory,
            onJumpForward = viewModel::jumpForwardInHistory,
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
    title: String,
    onBack: () -> Unit,
    onEnterFullScreen: () -> Unit,
    onOpenMenu: () -> Unit
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val cs = MaterialTheme.colorScheme

    Surface(
        color = cs.surface,
        tonalElevation = sz.cardElevation,
        shadowElevation = sz.cardElevation,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = s.xs)
                        .testTag("reader_document_title")
                )

                IconButton(
                    onClick = onEnterFullScreen,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("reader_fullscreen_button")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(sz.fullscreenBadgeSize)
                            .background(cs.primaryContainer, CircleShape)
                            .border(sz.hairline, cs.primary.copy(alpha = 0.28f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fullscreen,
                            contentDescription = stringResource(R.string.reader_fullscreen),
                            tint = cs.onPrimaryContainer,
                            modifier = Modifier.size(sz.iconTool)
                        )
                    }
                }

                IconButton(
                    onClick = onOpenMenu,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("reader_more_tools_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = stringResource(R.string.reader_menu)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sz.hairline)
                    .background(TeenaTheme.colors.hairline)
            )
        }
    }
}

@Composable
private fun CollapsibleEdgeQuickMenu(
    expanded: Boolean,
    isBookmarked: Boolean,
    isZoomLocked: Boolean,
    hideMargins: Boolean,
    isAutoScrollActive: Boolean,
    colorMode: PdfColorMode,
    isBrightnessOpen: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleZoomLock: () -> Unit,
    onToggleHideMargins: () -> Unit,
    onToggleBrightness: () -> Unit,
    onRotateScreen: () -> Unit,
    onToggleAutoScroll: () -> Unit,
    onCycleColorMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val motion = TeenaTheme.motion
    val cs = MaterialTheme.colorScheme

    Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = modifier
    ) {
        if (!expanded) {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .width(sz.edgeSwipeStripWidth)
                    .height(sz.signaturePadHeight)
                    .pointerInput(Unit) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                totalDrag += dragAmount
                                if (totalDrag < -18f) {
                                    onExpand()
                                }
                            }
                        )
                    }
            ) {
                Surface(
                    onClick = onExpand,
                    shape = EdgeHandleShape,
                    color = cs.surface.copy(alpha = 0.72f),
                    contentColor = cs.onSurfaceVariant,
                    shadowElevation = sz.cardElevation,
                    modifier = Modifier
                        .width(sz.edgeHandleWidth)
                        .height(sz.edgeHandleHeight)
                        .alpha(0.86f)
                        .border(sz.hairline, TeenaTheme.colors.hairline, EdgeHandleShape)
                        .testTag("quick_menu_edge_handle")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.reader_quick_menu_open),
                            modifier = Modifier.size(sz.iconTool)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = slideInHorizontally(motion.navSlideSpec()) { it } + fadeIn(motion.fastSpec()),
            exit = slideOutHorizontally(motion.navSlideSpec()) { it } + fadeOut(motion.fastSpec())
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = cs.surface.copy(alpha = 0.94f),
                tonalElevation = sz.heroElevation,
                shadowElevation = sz.menuElevation,
                modifier = Modifier
                    .padding(end = s.sm)
                    .border(sz.hairline, TeenaTheme.colors.hairline, MaterialTheme.shapes.extraLarge)
                    .pointerInput(Unit) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                totalDrag += dragAmount
                                if (totalDrag > 24f) {
                                    onCollapse()
                                }
                            }
                        )
                    }
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(s.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = s.xs, vertical = s.sm)
                ) {
                    EdgeQuickActionButton(
                        icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        description = stringResource(R.string.reader_quick_menu_close),
                        active = false,
                        onClick = onCollapse,
                        tag = "quick_menu_collapse"
                    )
                    EdgeQuickActionButton(
                        icon = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        description = stringResource(R.string.reader_add_bookmark),
                        active = isBookmarked,
                        onClick = onToggleBookmark,
                        tag = "shortcut_bookmark"
                    )
                    EdgeQuickActionButton(
                        icon = if (isZoomLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        description = stringResource(R.string.reader_zoom_lock),
                        active = isZoomLocked,
                        onClick = onToggleZoomLock,
                        tag = "shortcut_zoom_lock"
                    )
                    EdgeQuickActionButton(
                        icon = Icons.Filled.CropFree,
                        description = stringResource(R.string.reader_hide_margins),
                        active = hideMargins,
                        onClick = onToggleHideMargins,
                        tag = "shortcut_hide_margins"
                    )
                    EdgeQuickActionButton(
                        icon = Icons.Filled.Brightness6,
                        description = stringResource(R.string.reader_brightness),
                        active = isBrightnessOpen,
                        onClick = onToggleBrightness,
                        tag = "shortcut_brightness"
                    )
                    EdgeQuickActionButton(
                        icon = Icons.Filled.ScreenRotation,
                        description = stringResource(R.string.reader_rotate),
                        active = false,
                        onClick = onRotateScreen,
                        tag = "shortcut_rotate"
                    )
                    EdgeQuickActionButton(
                        icon = if (isAutoScrollActive) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        description = stringResource(R.string.reader_auto_scroll),
                        active = isAutoScrollActive,
                        onClick = onToggleAutoScroll,
                        tag = "shortcut_autoscroll"
                    )
                    EdgeQuickActionButton(
                        icon = if (colorMode == PdfColorMode.DARK_INVERT) Icons.Filled.DarkMode else Icons.Filled.Contrast,
                        description = stringResource(R.string.reader_color_mode),
                        active = colorMode != PdfColorMode.NORMAL,
                        onClick = onCycleColorMode,
                        tag = "shortcut_dark_mode"
                    )
                }
            }
        }
    }
}

@Composable
private fun EdgeQuickActionButton(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    val sz = TeenaTheme.sizes
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (active) cs.primaryContainer else cs.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (active) cs.onPrimaryContainer else cs.onSurface,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(sz.quickMenuTileSize)
            .testTag(tag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(sz.iconTool)
            )
        }
    }
}

@Composable
private fun InPdfSearchOverlayBar(
    query: String,
    matchCount: Int,
    activeMatchIndex: Int,
    activeSnippet: String?,
    isExtractingText: Boolean,
    onQueryChange: (String) -> Unit,
    onPrevMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onClose: () -> Unit
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val focusManager = LocalFocusManager.current
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = query, selection = TextRange(query.length)))
    }
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    LaunchedEffect(query) {
        if (query != textFieldValue.text) {
            textFieldValue = TextFieldValue(text = query, selection = TextRange(query.length))
        }
    }

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
            if (isExtractingText && textFieldValue.text.isNotBlank()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(s.xs))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { updated ->
                        val prev = textFieldValue.text
                        textFieldValue = updated
                        if (updated.text != prev) {
                            currentOnQueryChange(updated.text)
                        }
                    },
                    placeholder = { Text(stringResource(R.string.reader_search_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (matchCount > 0) onNextMatch()
                            focusManager.clearFocus()
                        }
                    ),
                    trailingIcon = if (textFieldValue.text.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = {
                                    textFieldValue = TextFieldValue("")
                                    currentOnQueryChange("")
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("in_pdf_search_clear")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.clear)
                                )
                            }
                        }
                    } else null,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("in_pdf_search_input")
                )
                Spacer(modifier = Modifier.width(s.sm))
                val statusLabel = when {
                    matchCount > 0 -> stringResource(
                        R.string.reader_match_counter,
                        activeMatchIndex + 1,
                        matchCount
                    )
                    query.isNotBlank() && !isExtractingText -> stringResource(R.string.reader_no_matches)
                    else -> stringResource(R.string.reader_match_counter, 0, 0)
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (matchCount > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
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
