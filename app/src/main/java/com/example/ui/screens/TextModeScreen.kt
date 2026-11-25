package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.TextFontChoice
import com.example.data.model.TextModeTheme
import com.example.data.text.OpenFailure
import com.example.data.text.TextQuality
import com.example.ui.components.ShareQuoteBottomSheet
import com.example.ui.theme.TeenaTheme
import com.example.ui.theme.readerTextModeStyle
import com.example.ui.viewmodel.PageUiState
import com.example.ui.viewmodel.TextModeDocState
import com.example.ui.viewmodel.TextModeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextModeScreen(
    viewModel: TextModeViewModel,
    documentTitle: String,
    initialPage: Int,
    onNavigateBack: () -> Unit,
    onViewOriginalPage: (Int) -> Unit,
) {
    val docState by viewModel.docState.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showStyleSheet by remember { mutableStateOf(false) }
    var showShareQuoteSheet by remember { mutableStateOf(false) }

    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val readerColors = TeenaTheme.colors.reader
    val cs = MaterialTheme.colorScheme

    val (bgColor, fgColor, accentColor, barColor) = when (prefs.textModeTheme) {
        TextModeTheme.LIGHT -> QuadColors(
            readerColors.textLightBg,
            readerColors.textLightFg,
            cs.primary,
            readerColors.textLightSurface,
        )
        TextModeTheme.SEPIA -> QuadColors(
            readerColors.textSepiaBg,
            readerColors.textSepiaFg,
            readerColors.textSepiaAccent,
            readerColors.textSepiaSurface,
        )
        TextModeTheme.DARK -> QuadColors(
            readerColors.textDarkBg,
            readerColors.textDarkFg,
            cs.primary,
            readerColors.textDarkSurface,
        )
        TextModeTheme.DRACULA -> QuadColors(
            readerColors.draculaBg,
            readerColors.draculaFg,
            readerColors.draculaAccent,
            readerColors.draculaSurface,
        )
    }

    var fontSizeDraft by remember(prefs.textModeFontSizeSp) {
        mutableFloatStateOf(prefs.textModeFontSizeSp)
    }
    var lineHeightDraft by remember(prefs.textModeLineHeightMultiplier) {
        mutableFloatStateOf(prefs.textModeLineHeightMultiplier)
    }

    val useSerif = prefs.textModeFontChoice == TextFontChoice.SERIF
    val bodyStyle = readerTextModeStyle(
        useSerif = useSerif,
        fontSizeSp = fontSizeDraft,
        lineHeightMultiplier = lineHeightDraft,
    )

    val readyState = docState as? TextModeDocState.Ready
    val totalPages = readyState?.pageCount ?: 0
    val safeInitialPage = if (totalPages > 0) initialPage.coerceIn(0, totalPages - 1) else 0
    val listState: LazyListState? = if (readyState != null && totalPages > 0) {
        rememberLazyListState(initialFirstVisibleItemIndex = safeInitialPage)
    } else {
        null
    }

    val visiblePageIndex by remember(listState) {
        derivedStateOf { listState?.firstVisibleItemIndex ?: safeInitialPage }
    }
    val visiblePageText = (pages[visiblePageIndex] as? PageUiState.Ready)?.text.orEmpty()
    val copiedMessage = stringResource(R.string.text_mode_copied)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = bgColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = barColor,
                    titleContentColor = fgColor,
                    navigationIconContentColor = fgColor,
                    actionIconContentColor = fgColor,
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("text_mode_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.text_mode_title),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                        if (documentTitle.isNotBlank()) {
                            Text(
                                text = documentTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = fgColor.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showShareQuoteSheet = true },
                        enabled = visiblePageText.isNotBlank(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("text_mode_quote_button"),
                    ) {
                        Icon(
                            Icons.Rounded.FormatQuote,
                            contentDescription = stringResource(R.string.text_mode_share_quote),
                        )
                    }
                    IconButton(
                        onClick = {
                            if (visiblePageText.isNotBlank()) {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText(documentTitle, visiblePageText))
                                scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                            }
                        },
                        enabled = visiblePageText.isNotBlank(),
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.text_mode_copy),
                        )
                    }
                    IconButton(
                        onClick = {
                            if (visiblePageText.isNotBlank()) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, documentTitle)
                                    putExtra(Intent.EXTRA_TEXT, visiblePageText)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, documentTitle))
                            }
                        },
                        enabled = visiblePageText.isNotBlank(),
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(
                            Icons.Rounded.Share,
                            contentDescription = stringResource(R.string.text_mode_share),
                        )
                    }
                    IconButton(
                        onClick = { showStyleSheet = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("text_mode_style_button"),
                    ) {
                        Icon(
                            Icons.Rounded.FormatSize,
                            contentDescription = stringResource(R.string.text_mode_appearance),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (listState != null && totalPages > 0) {
                Surface(color = barColor, contentColor = fgColor) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = s.lg, vertical = s.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(
                            onClick = {
                                val prev = (visiblePageIndex - 1).coerceAtLeast(0)
                                scope.launch { listState.animateScrollToItem(prev) }
                            },
                            enabled = visiblePageIndex > 0,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("text_mode_prev_page"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = stringResource(R.string.text_mode_prev_page),
                            )
                        }

                        Text(
                            text = stringResource(
                                R.string.text_mode_page_label,
                                visiblePageIndex + 1,
                                totalPages,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = accentColor,
                            maxLines = 1,
                        )

                        IconButton(
                            onClick = {
                                val next = (visiblePageIndex + 1).coerceAtMost(totalPages - 1)
                                scope.launch { listState.animateScrollToItem(next) }
                            },
                            enabled = visiblePageIndex < totalPages - 1,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("text_mode_next_page"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = stringResource(R.string.text_mode_next_page),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .padding(innerPadding),
        ) {
            when (val currentDocState = docState) {
                TextModeDocState.Loading -> {
                    CircularProgressIndicator(
                        color = accentColor,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                is TextModeDocState.Error -> {
                    val errorMsgRes = when (currentDocState.failure) {
                        OpenFailure.ENCRYPTED -> R.string.text_mode_error_encrypted
                        OpenFailure.CORRUPT -> R.string.text_mode_error_corrupt
                        OpenFailure.IO, OpenFailure.OUT_OF_MEMORY -> R.string.text_mode_error_generic
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = s.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(errorMsgRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = fgColor,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(s.lg))
                        Button(
                            onClick = onNavigateBack,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = cs.onPrimary,
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("text_mode_error_back_button"),
                        ) {
                            Text(text = stringResource(R.string.action_back))
                        }
                    }
                }

                is TextModeDocState.Ready -> {
                    if (currentDocState.pageCount <= 0 || listState == null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = s.xl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            PageWarningCard(
                                message = stringResource(R.string.text_mode_page_empty),
                                pageIndex = 0,
                                barColor = barColor,
                                fgColor = fgColor,
                                accentColor = accentColor,
                                onViewOriginalPage = onViewOriginalPage,
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("text_mode_lazy_column"),
                            contentPadding = PaddingValues(horizontal = s.xl, vertical = s.lg),
                            verticalArrangement = Arrangement.spacedBy(s.xl),
                        ) {
                            items(
                                count = currentDocState.pageCount,
                                key = { it },
                                contentType = { "page" },
                            ) { index ->
                                LaunchedEffect(index) {
                                    viewModel.requestPage(index)
                                }

                                TextModePageItem(
                                    pageIndex = index,
                                    pageCount = currentDocState.pageCount,
                                    pageState = pages[index],
                                    bodyStyle = bodyStyle,
                                    fgColor = fgColor,
                                    accentColor = accentColor,
                                    barColor = barColor,
                                    dividerColor = TeenaTheme.colors.hairline,
                                    dividerThickness = sz.hairline,
                                    onViewOriginalPage = onViewOriginalPage,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showShareQuoteSheet) {
        val defaultPassage = visiblePageText.lines().firstOrNull { it.length > 30 }
            ?: visiblePageText.take(240)
        ShareQuoteBottomSheet(
            initialQuoteText = defaultPassage,
            bookTitle = documentTitle,
            pageNumber = visiblePageIndex + 1,
            onDismiss = { showShareQuoteSheet = false },
        )
    }

    if (showStyleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStyleSheet = false },
            containerColor = cs.surface,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = s.lg, vertical = s.sm),
                verticalArrangement = Arrangement.spacedBy(s.md),
            ) {
                Text(
                    text = stringResource(R.string.text_mode_appearance),
                    style = MaterialTheme.typography.titleLarge,
                )

                Text(
                    stringResource(R.string.text_mode_theme),
                    style = MaterialTheme.typography.labelLarge,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(s.sm)) {
                    items(
                        items = TextModeTheme.entries,
                        key = { it.name },
                        contentType = { "text_theme" },
                    ) { theme ->
                        FilterChip(
                            selected = prefs.textModeTheme == theme,
                            onClick = { viewModel.setTextModeTheme(theme) },
                            label = { Text(stringResource(theme.labelRes)) },
                            shape = CircleShape,
                        )
                    }
                }

                Text(
                    stringResource(R.string.text_mode_font),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(s.sm)) {
                    FilterChip(
                        selected = !useSerif,
                        onClick = { viewModel.setTextModeFontChoice(TextFontChoice.SANS) },
                        label = { Text(stringResource(R.string.text_mode_font_sans)) },
                        shape = CircleShape,
                    )
                    FilterChip(
                        selected = useSerif,
                        onClick = { viewModel.setTextModeFontChoice(TextFontChoice.SERIF) },
                        label = { Text(stringResource(R.string.text_mode_font_serif)) },
                        shape = CircleShape,
                    )
                }

                Text(
                    text = stringResource(R.string.text_mode_font_size, fontSizeDraft.toInt()),
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = fontSizeDraft,
                    onValueChange = { fontSizeDraft = it },
                    onValueChangeFinished = { viewModel.setTextModeFontSize(fontSizeDraft) },
                    valueRange = 13f..28f,
                )

                Text(
                    text = stringResource(
                        R.string.text_mode_line_spacing,
                        lineHeightDraft,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = lineHeightDraft,
                    onValueChange = { lineHeightDraft = it },
                    onValueChangeFinished = { viewModel.setTextModeLineHeight(lineHeightDraft) },
                    valueRange = 1.2f..2.2f,
                )

                Spacer(modifier = Modifier.height(s.lg))
            }
        }
    }
}

@Composable
private fun TextModePageItem(
    pageIndex: Int,
    pageCount: Int,
    pageState: PageUiState?,
    bodyStyle: TextStyle,
    fgColor: Color,
    accentColor: Color,
    barColor: Color,
    dividerColor: Color,
    dividerThickness: androidx.compose.ui.unit.Dp,
    onViewOriginalPage: (Int) -> Unit,
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(s.md),
    ) {
        Text(
            text = stringResource(R.string.text_mode_page_label, pageIndex + 1, pageCount),
            style = MaterialTheme.typography.labelMedium,
            color = accentColor,
        )

        when (pageState) {
            null, PageUiState.Loading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = s.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(s.md),
                ) {
                    CircularProgressIndicator(
                        color = accentColor,
                        strokeWidth = sz.ringStroke,
                        modifier = Modifier.size(sz.iconTool),
                    )
                    Text(
                        text = stringResource(R.string.text_mode_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = fgColor.copy(alpha = 0.7f),
                    )
                }
            }

            is PageUiState.Ready -> {
                SelectionContainer {
                    Text(
                        text = pageState.text,
                        color = fgColor,
                        style = bodyStyle,
                    )
                }
            }

            is PageUiState.Unreadable -> {
                val msgRes = when (pageState.quality) {
                    TextQuality.EMPTY -> R.string.text_mode_page_empty
                    TextQuality.GARBLED -> R.string.text_mode_page_garbled
                    TextQuality.LEGACY_FONT -> R.string.text_mode_page_legacy_font
                    TextQuality.OK -> R.string.text_mode_page_empty
                }
                PageWarningCard(
                    message = stringResource(msgRes),
                    pageIndex = pageIndex,
                    barColor = barColor,
                    fgColor = fgColor,
                    accentColor = accentColor,
                    onViewOriginalPage = onViewOriginalPage,
                )
            }

            PageUiState.Failed -> {
                PageWarningCard(
                    message = stringResource(R.string.text_mode_page_failed),
                    pageIndex = pageIndex,
                    barColor = barColor,
                    fgColor = fgColor,
                    accentColor = accentColor,
                    onViewOriginalPage = onViewOriginalPage,
                )
            }
        }

        if (pageIndex < pageCount - 1) {
            Spacer(modifier = Modifier.height(s.sm))
            HorizontalDivider(thickness = dividerThickness, color = dividerColor)
        }
    }
}

@Composable
private fun PageWarningCard(
    message: String,
    pageIndex: Int,
    barColor: Color,
    fgColor: Color,
    accentColor: Color,
    onViewOriginalPage: (Int) -> Unit,
) {
    val s = TeenaTheme.spacing
    Surface(
        color = barColor,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(s.lg),
            verticalArrangement = Arrangement.spacedBy(s.md),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = fgColor.copy(alpha = 0.85f),
            )
            OutlinedButton(
                onClick = { onViewOriginalPage(pageIndex) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("view_original_page_$pageIndex"),
            ) {
                Text(
                    text = stringResource(R.string.action_view_original_page),
                    color = accentColor,
                )
            }
        }
    }
}

private data class QuadColors(
    val bg: Color,
    val fg: Color,
    val accent: Color,
    val bar: Color,
)
