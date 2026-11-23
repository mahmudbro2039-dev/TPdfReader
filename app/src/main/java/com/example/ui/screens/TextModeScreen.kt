package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.TextFontChoice
import com.example.data.model.TextModeTheme
import com.example.ui.components.ShareQuoteBottomSheet
import com.example.ui.theme.TeenaTheme
import com.example.ui.theme.readerTextModeStyle
import com.example.ui.viewmodel.ReaderViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextModeScreen(
    viewModel: ReaderViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showStyleSheet by remember { mutableStateOf(false) }
    var showShareQuoteSheet by remember { mutableStateOf(false) }

    val s = TeenaTheme.spacing
    val readerColors = TeenaTheme.colors.reader
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(state.uriString) {
        viewModel.ensureTextExtracted()
    }

    val (bgColor, fgColor, accentColor, barColor) = when (prefs.textModeTheme) {
        TextModeTheme.LIGHT -> QuadColors(
            readerColors.textLightBg,
            readerColors.textLightFg,
            cs.primary,
            readerColors.textLightSurface
        )
        TextModeTheme.SEPIA -> QuadColors(
            readerColors.textSepiaBg,
            readerColors.textSepiaFg,
            readerColors.textSepiaAccent,
            readerColors.textSepiaSurface
        )
        TextModeTheme.DARK -> QuadColors(
            readerColors.textDarkBg,
            readerColors.textDarkFg,
            cs.primary,
            readerColors.textDarkSurface
        )
        TextModeTheme.DRACULA -> QuadColors(
            readerColors.draculaBg,
            readerColors.draculaFg,
            readerColors.draculaAccent,
            readerColors.draculaSurface
        )
    }

    val useSerif = prefs.textModeFontChoice == TextFontChoice.SERIF
    val bodyStyle = readerTextModeStyle(
        useSerif = useSerif,
        fontSizeSp = prefs.textModeFontSizeSp,
        lineHeightMultiplier = prefs.textModeLineHeightMultiplier
    )

    val currentText = state.extractedTextPages.getOrElse(state.currentPage) { "" }
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
                    actionIconContentColor = fgColor
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("text_mode_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.text_mode_title),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Text(
                            text = state.documentTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = fgColor.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showShareQuoteSheet = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("text_mode_quote_button")
                    ) {
                        Icon(Icons.Rounded.FormatQuote, contentDescription = stringResource(R.string.text_mode_share_quote))
                    }
                    IconButton(
                        onClick = {
                            if (currentText.isNotBlank()) {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText(state.documentTitle, currentText))
                                scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                            }
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.text_mode_copy))
                    }
                    IconButton(
                        onClick = {
                            if (currentText.isNotBlank()) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, state.documentTitle)
                                    putExtra(Intent.EXTRA_TEXT, currentText)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, state.documentTitle))
                            }
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.text_mode_share))
                    }
                    IconButton(
                        onClick = { showStyleSheet = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("text_mode_style_button")
                    ) {
                        Icon(Icons.Rounded.FormatSize, contentDescription = stringResource(R.string.text_mode_appearance))
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = barColor, contentColor = fgColor) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = s.lg, vertical = s.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { viewModel.stepSinglePage(-1) },
                        enabled = state.currentPage > 0,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("text_mode_prev_page")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.text_mode_prev_page)
                        )
                    }

                    Text(
                        text = stringResource(R.string.page_of, state.currentPage + 1, state.pageCount.coerceAtLeast(1)),
                        style = MaterialTheme.typography.labelLarge,
                        color = accentColor,
                        maxLines = 1
                    )

                    IconButton(
                        onClick = { viewModel.stepSinglePage(1) },
                        enabled = state.currentPage < state.pageCount - 1,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("text_mode_next_page")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.text_mode_next_page)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .padding(innerPadding)
        ) {
            if (state.isExtractingText) {
                CircularProgressIndicator(
                    color = accentColor,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                val scrollState = rememberScrollState()
                LaunchedEffect(state.currentPage) { scrollState.scrollTo(0) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = s.xl, vertical = s.xl)
                ) {
                    if (currentText.isBlank()) {
                        Surface(
                            color = barColor,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.text_mode_empty_page),
                                style = MaterialTheme.typography.bodyMedium,
                                color = fgColor.copy(alpha = 0.8f),
                                modifier = Modifier.padding(s.lg)
                            )
                        }
                    } else {
                        SelectionContainer {
                            Text(
                                text = currentText,
                                color = fgColor,
                                style = bodyStyle
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(s.xxl))
                }
            }
        }
    }

    if (showShareQuoteSheet) {
        val defaultPassage = currentText.lines().firstOrNull { it.length > 30 } ?: currentText.take(240)
        ShareQuoteBottomSheet(
            initialQuoteText = defaultPassage,
            bookTitle = state.documentTitle,
            pageNumber = state.currentPage + 1,
            onDismiss = { showShareQuoteSheet = false }
        )
    }

    if (showStyleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStyleSheet = false },
            containerColor = cs.surface,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = s.lg, vertical = s.sm),
                verticalArrangement = Arrangement.spacedBy(s.md)
            ) {
                Text(
                    text = stringResource(R.string.text_mode_appearance),
                    style = MaterialTheme.typography.titleLarge
                )

                Text(stringResource(R.string.text_mode_theme), style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(s.sm)) {
                    items(
                        items = TextModeTheme.entries,
                        key = { it.name },
                        contentType = { "text_theme" }
                    ) { theme ->
                        FilterChip(
                            selected = prefs.textModeTheme == theme,
                            onClick = { viewModel.setTextModeTheme(theme) },
                            label = { Text(theme.label) },
                            shape = CircleShape
                        )
                    }
                }

                Text(stringResource(R.string.text_mode_font), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(s.sm)) {
                    FilterChip(
                        selected = !useSerif,
                        onClick = { viewModel.setTextModeFontChoice(TextFontChoice.SANS) },
                        label = { Text(stringResource(R.string.text_mode_font_sans)) },
                        shape = CircleShape
                    )
                    FilterChip(
                        selected = useSerif,
                        onClick = { viewModel.setTextModeFontChoice(TextFontChoice.SERIF) },
                        label = { Text(stringResource(R.string.text_mode_font_serif)) },
                        shape = CircleShape
                    )
                }

                Text(
                    text = stringResource(R.string.text_mode_font_size, prefs.textModeFontSizeSp.toInt()),
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = prefs.textModeFontSizeSp,
                    onValueChange = viewModel::setTextModeFontSize,
                    valueRange = 13f..28f
                )

                Text(
                    text = stringResource(R.string.text_mode_line_spacing, prefs.textModeLineHeightMultiplier),
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = prefs.textModeLineHeightMultiplier,
                    onValueChange = viewModel::setTextModeLineHeight,
                    valueRange = 1.2f..2.2f
                )

                Spacer(modifier = Modifier.height(s.lg))
            }
        }
    }
}

private data class QuadColors(
    val bg: Color,
    val fg: Color,
    val accent: Color,
    val bar: Color
)
