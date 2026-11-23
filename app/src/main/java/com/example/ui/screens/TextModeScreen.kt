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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.TextFontChoice
import com.example.data.model.TextModeTheme
import com.example.ui.components.ShareQuoteBottomSheet
import com.example.ui.theme.DraculaAccent
import com.example.ui.theme.DraculaBg
import com.example.ui.theme.DraculaFg
import com.example.ui.theme.DraculaSurface
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NewsreaderFamily
import com.example.ui.theme.PlusJakartaSansFamily
import com.example.ui.theme.TextModeDarkBg
import com.example.ui.theme.TextModeDarkFg
import com.example.ui.theme.TextModeLightBg
import com.example.ui.theme.TextModeLightFg
import com.example.ui.theme.TextModeSepiaBg
import com.example.ui.theme.TextModeSepiaFg
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

    LaunchedEffect(state.uriString) {
        viewModel.ensureTextExtracted()
    }

    val (bgColor, fgColor, accentColor, barColor) = when (prefs.textModeTheme) {
        TextModeTheme.LIGHT -> QuadColors(TextModeLightBg, TextModeLightFg, MaterialTheme.colorScheme.primary, Color(0xFFF3F1EC))
        TextModeTheme.SEPIA -> QuadColors(TextModeSepiaBg, TextModeSepiaFg, Color(0xFF8C5000), Color(0xFFEAE0C8))
        TextModeTheme.DARK -> QuadColors(TextModeDarkBg, TextModeDarkFg, MaterialTheme.colorScheme.primary, Color(0xFF1E1E22))
        TextModeTheme.DRACULA -> QuadColors(DraculaBg, DraculaFg, DraculaAccent, DraculaSurface)
    }

    val fontFamily = when (prefs.textModeFontChoice) {
        TextFontChoice.SANS -> PlusJakartaSansFamily
        TextFontChoice.SERIF -> NewsreaderFamily
        TextFontChoice.MONO -> JetBrainsMonoFamily
    }

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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.text_mode_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.documentTitle,
                            style = MaterialTheme.typography.labelSmall,
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
                        Icon(Icons.Filled.FormatQuote, contentDescription = stringResource(R.string.text_mode_share_quote))
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
                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.text_mode_copy))
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
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.text_mode_share))
                    }
                    IconButton(
                        onClick = { showStyleSheet = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("text_mode_style_button")
                    ) {
                        Icon(Icons.Filled.FormatSize, contentDescription = stringResource(R.string.text_mode_appearance))
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
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { viewModel.stepPage(-1) },
                        enabled = state.currentPage > 0,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("text_mode_prev_page")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
                    }

                    Text(
                        text = "Page ${state.currentPage + 1} of ${state.pageCount.coerceAtLeast(1)}  •  ${prefs.textModeTheme.label}",
                        style = MaterialTheme.typography.labelLarge,
                        color = accentColor
                    )

                    IconButton(
                        onClick = { viewModel.stepPage(1) },
                        enabled = state.currentPage < state.pageCount - 1,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("text_mode_next_page")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page")
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
                        .padding(horizontal = 24.dp, vertical = 22.dp)
                ) {
                    if (currentText.isBlank()) {
                        Surface(
                            color = barColor,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.text_mode_empty_page),
                                style = MaterialTheme.typography.bodyMedium,
                                color = fgColor.copy(alpha = 0.8f),
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    } else {
                        SelectionContainer {
                            Text(
                                text = currentText,
                                color = fgColor,
                                fontFamily = fontFamily,
                                fontSize = prefs.textModeFontSizeSp.sp,
                                lineHeight = (prefs.textModeFontSizeSp * prefs.textModeLineHeightMultiplier).sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
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
        ModalBottomSheet(onDismissRequest = { showStyleSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.text_mode_appearance),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))

                Text("Theme", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TextModeTheme.entries) { theme ->
                        FilterChip(
                            selected = prefs.textModeTheme == theme,
                            onClick = { viewModel.setTextModeTheme(theme) },
                            label = { Text(theme.label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("Font Family", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextFontChoice.entries.forEach { choice ->
                        FilterChip(
                            selected = prefs.textModeFontChoice == choice,
                            onClick = { viewModel.setTextModeFontChoice(choice) },
                            label = { Text(choice.label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Font Size", style = MaterialTheme.typography.labelLarge)
                    Text("${prefs.textModeFontSizeSp.toInt()} sp", style = MaterialTheme.typography.labelSmall)
                }
                Slider(
                    value = prefs.textModeFontSizeSp,
                    onValueChange = viewModel::setTextModeFontSize,
                    valueRange = 13f..28f
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Line Spacing", style = MaterialTheme.typography.labelLarge)
                    Text(String.format("%.1fx", prefs.textModeLineHeightMultiplier), style = MaterialTheme.typography.labelSmall)
                }
                Slider(
                    value = prefs.textModeLineHeightMultiplier,
                    onValueChange = viewModel::setTextModeLineHeight,
                    valueRange = 1.2f..2.2f
                )

                Spacer(modifier = Modifier.height(24.dp))
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
