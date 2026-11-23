package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AppThemeMode
import com.example.data.model.PageFitPolicy
import com.example.data.model.PdfColorMode
import com.example.data.model.RenderQuality
import com.example.data.model.ScrollLayoutMode
import com.example.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    val cacheClearedMsg = stringResource(R.string.settings_cache_cleared)
    val historyClearedMsg = stringResource(R.string.settings_history_cleared)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item(key = "section_appearance") {
                SettingsCardSection(title = stringResource(R.string.settings_section_appearance)) {
                    ChipOptionGroup(
                        label = stringResource(R.string.settings_app_theme),
                        options = AppThemeMode.entries,
                        selected = prefs.appThemeMode,
                        optionLabel = { it.label },
                        onSelect = viewModel::setAppThemeMode
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ToggleSettingRow(
                            title = stringResource(R.string.settings_dynamic_color),
                            subtitle = stringResource(R.string.settings_dynamic_color_desc),
                            checked = prefs.dynamicColor,
                            onCheckedChange = viewModel::setDynamicColor
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ChipOptionGroup(
                        label = stringResource(R.string.settings_default_pdf_color),
                        options = PdfColorMode.entries,
                        selected = prefs.defaultPdfColorMode,
                        optionLabel = { it.label },
                        onSelect = viewModel::setDefaultPdfColorMode
                    )
                }
            }

            item(key = "section_reading") {
                SettingsCardSection(title = stringResource(R.string.settings_section_reading)) {
                    ChipOptionGroup(
                        label = stringResource(R.string.settings_default_layout),
                        options = ScrollLayoutMode.entries,
                        selected = prefs.defaultLayoutMode,
                        optionLabel = { it.label },
                        onSelect = viewModel::setDefaultLayoutMode
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ChipOptionGroup(
                        label = stringResource(R.string.settings_default_fit),
                        options = PageFitPolicy.entries,
                        selected = prefs.defaultPageFitPolicy,
                        optionLabel = { it.label },
                        onSelect = viewModel::setDefaultPageFitPolicy
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ToggleSettingRow(
                        title = stringResource(R.string.settings_rtl),
                        subtitle = stringResource(R.string.settings_rtl_desc),
                        checked = prefs.rtlReading,
                        onCheckedChange = viewModel::setRtlReading
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ToggleSettingRow(
                        title = stringResource(R.string.settings_keep_screen_on),
                        subtitle = stringResource(R.string.settings_keep_screen_on_desc),
                        checked = prefs.keepScreenOn,
                        onCheckedChange = viewModel::setKeepScreenOn
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ToggleSettingRow(
                        title = stringResource(R.string.settings_volume_keys),
                        subtitle = stringResource(R.string.settings_volume_keys_desc),
                        checked = prefs.volumeKeyNavigation,
                        onCheckedChange = viewModel::setVolumeKeyNavigation
                    )
                }
            }

            item(key = "section_fullscreen") {
                SettingsCardSection(title = stringResource(R.string.settings_section_fullscreen)) {
                    ToggleSettingRow(
                        title = stringResource(R.string.settings_show_page_pill),
                        subtitle = stringResource(R.string.settings_show_page_pill_desc),
                        checked = prefs.showPageIndicatorPill,
                        onCheckedChange = viewModel::setShowPageIndicatorPill
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ToggleSettingRow(
                        title = stringResource(R.string.settings_show_shortcuts),
                        subtitle = stringResource(R.string.settings_show_shortcuts_desc),
                        checked = prefs.showEdgeShortcutButtons,
                        onCheckedChange = viewModel::setShowEdgeShortcutButtons
                    )
                }
            }

            item(key = "section_rendering") {
                SettingsCardSection(title = stringResource(R.string.settings_section_rendering)) {
                    ChipOptionGroup(
                        label = stringResource(R.string.settings_render_quality),
                        options = RenderQuality.entries,
                        selected = prefs.renderQuality,
                        optionLabel = { it.label },
                        onSelect = viewModel::setRenderQuality
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ToggleSettingRow(
                        title = stringResource(R.string.settings_hide_margins),
                        subtitle = stringResource(R.string.settings_hide_margins_desc),
                        checked = prefs.hideMarginsByDefault,
                        onCheckedChange = viewModel::setHideMarginsByDefault
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ToggleSettingRow(
                        title = stringResource(R.string.settings_page_spacing),
                        subtitle = stringResource(R.string.settings_page_spacing_desc),
                        checked = prefs.pageGapSpacing,
                        onCheckedChange = viewModel::setPageGapSpacing
                    )
                }
            }

            item(key = "section_storage") {
                SettingsCardSection(title = stringResource(R.string.settings_section_storage)) {
                    ToggleSettingRow(
                        title = stringResource(R.string.incognito_mode),
                        subtitle = "Pause saving reading progress and recently opened document history",
                        checked = prefs.incognitoMode,
                        onCheckedChange = viewModel::setIncognitoMode
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ActionSettingRow(
                        icon = Icons.Filled.CleaningServices,
                        title = stringResource(R.string.settings_clear_cache),
                        subtitle = stringResource(R.string.settings_clear_cache_desc),
                        onClick = {
                            viewModel.clearRenderCache {
                                scope.launch { snackbarHostState.showSnackbar(cacheClearedMsg) }
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ActionSettingRow(
                        icon = Icons.Filled.DeleteSweep,
                        title = stringResource(R.string.settings_clear_history),
                        subtitle = stringResource(R.string.settings_clear_history_desc),
                        isDestructive = true,
                        onClick = { showClearHistoryDialog = true }
                    )
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(stringResource(R.string.settings_clear_history)) },
            text = { Text("Remove all saved library entries, bookmarks, and notes from MJ PDF? Your actual PDF files on storage will remain untouched.") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearHistoryDialog = false
                        viewModel.clearLibraryHistory {
                            scope.launch { snackbarHostState.showSnackbar(historyClearedMsg) }
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun SettingsCardSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun <T> ChipOptionGroup(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { item ->
                FilterChip(
                    selected = item == selected,
                    onClick = { onSelect(item) },
                    label = { Text(optionLabel(item)) }
                )
            }
        }
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
