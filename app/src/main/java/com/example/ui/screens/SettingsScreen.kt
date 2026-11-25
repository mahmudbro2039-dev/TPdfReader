package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
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
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.PageFitPolicy
import com.example.data.model.PdfColorMode
import com.example.data.model.RenderQuality
import com.example.data.model.ScrollLayoutMode
import com.example.data.search.PauseReason
import com.example.data.system.DevicePermissionsHelper
import com.example.ui.model.toAppThemeMode
import com.example.ui.model.toThemeMode
import com.example.ui.theme.TeenaTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onRequestAllFilesAccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val searchIndexStatus by viewModel.searchIndexStatus.collectAsStateWithLifecycle()
    var accessStatus by remember { mutableStateOf(DevicePermissionsHelper.getStatus(context)) }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessStatus = DevicePermissionsHelper.getStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showRebuildIndexDialog by remember { mutableStateOf(false) }

    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val c = TeenaTheme.colors
    val cs = MaterialTheme.colorScheme

    val cacheClearedMsg = stringResource(R.string.settings_cache_cleared)
    val historyClearedMsg = stringResource(R.string.settings_history_cleared)
    val themeSystemLabel = stringResource(R.string.theme_system)
    val themeLightLabel = stringResource(R.string.theme_light)
    val themeDarkLabel = stringResource(R.string.theme_dark)

    Scaffold(
        containerColor = cs.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.background,
                    titleContentColor = cs.onBackground,
                    navigationIconContentColor = cs.onBackground
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = s.screenH, vertical = s.md),
            verticalArrangement = Arrangement.spacedBy(s.lg),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item(key = "section_permissions", contentType = "settings_section") {
                SettingsCardSection(title = stringResource(R.string.settings_section_permissions)) {
                    ActionSettingRow(
                        icon = if (accessStatus.hasAllFilesAccess) Icons.Rounded.CheckCircle else Icons.Rounded.FolderOpen,
                        title = stringResource(R.string.perm_all_files_title),
                        subtitle = stringResource(
                            if (accessStatus.hasAllFilesAccess) R.string.perm_all_files_granted
                            else R.string.perm_all_files_missing
                        ),
                        onClick = onRequestAllFilesAccess
                    )
                    HorizontalDivider(thickness = sz.hairline, color = c.hairline)
                    val scanSubtitle = if (scanProgress.isScanning) {
                        stringResource(R.string.scanner_found_count, scanProgress.totalFoundCount)
                    } else {
                        stringResource(R.string.scanner_rescan_desc)
                    }
                    ActionSettingRow(
                        icon = Icons.Rounded.Refresh,
                        title = stringResource(
                            if (scanProgress.isScanning) R.string.scanner_scanning_title
                            else R.string.scanner_rescan_title
                        ),
                        subtitle = scanSubtitle,
                        onClick = {
                            viewModel.rescanDevicePdfs { result ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(
                                            R.string.scanner_rescan_done,
                                            result.newlyAddedCount,
                                            result.totalFoundCount
                                        )
                                    )
                                }
                            }
                        }
                    )
                    HorizontalDivider(thickness = sz.hairline, color = c.hairline)
                    val indexCountSubtitle = stringResource(
                        R.string.settings_search_index_status,
                        searchIndexStatus.doneCount,
                        searchIndexStatus.totalCount
                    )
                    val pausedText = when (searchIndexStatus.pauseReason) {
                        PauseReason.LOW_BATTERY -> stringResource(R.string.settings_search_index_paused_battery)
                        PauseReason.POWER_SAVER -> stringResource(R.string.settings_search_index_paused_saver)
                        null -> null
                    }
                    ActionSettingRow(
                        icon = Icons.Rounded.ManageSearch,
                        title = stringResource(R.string.settings_search_index_title),
                        subtitle = indexCountSubtitle,
                        secondarySubtitle = pausedText,
                        onClick = null
                    )
                    HorizontalDivider(thickness = sz.hairline, color = c.hairline)
                    ActionSettingRow(
                        icon = Icons.Rounded.RestartAlt,
                        title = stringResource(R.string.settings_search_index_rebuild),
                        subtitle = stringResource(R.string.settings_search_index_rebuild_confirm),
                        modifier = Modifier.testTag("settings_rebuild_index"),
                        onClick = { showRebuildIndexDialog = true }
                    )
                }
            }

            item(key = "section_appearance", contentType = "settings_section") {
                SettingsCardSection(title = stringResource(R.string.settings_section_appearance)) {
                    ChipOptionGroup(
                        label = stringResource(R.string.settings_app_theme),
                        options = ThemeMode.entries,
                        selected = prefs.appThemeMode.toThemeMode(),
                        optionLabel = { mode ->
                            when (mode) {
                                ThemeMode.System -> themeSystemLabel
                                ThemeMode.Light -> themeLightLabel
                                ThemeMode.Dark -> themeDarkLabel
                            }
                        },
                        onSelect = { mode -> viewModel.setAppThemeMode(mode.toAppThemeMode()) }
                    )
                    HorizontalDivider(thickness = sz.hairline, color = c.hairline)
                    ChipOptionGroup(
                        label = stringResource(R.string.settings_default_pdf_color),
                        options = PdfColorMode.entries,
                        selected = prefs.defaultPdfColorMode,
                        optionLabel = { stringResource(it.labelRes) },
                        onSelect = viewModel::setDefaultPdfColorMode
                    )
                }
            }

            item(key = "section_reading", contentType = "settings_section") {
                SettingsCardSection(title = stringResource(R.string.settings_section_reading)) {
                    ChipOptionGroup(
                        label = stringResource(R.string.settings_default_layout),
                        options = ScrollLayoutMode.entries,
                        selected = prefs.defaultLayoutMode,
                        optionLabel = { stringResource(it.labelRes) },
                        onSelect = viewModel::setDefaultLayoutMode
                    )
                    HorizontalDivider(thickness = sz.hairline, color = c.hairline)
                    ChipOptionGroup(
                        label = stringResource(R.string.settings_default_fit),
                        options = PageFitPolicy.entries,
                        selected = prefs.defaultPageFitPolicy,
                        optionLabel = { stringResource(it.labelRes) },
                        onSelect = viewModel::setDefaultPageFitPolicy
                    )
                    HorizontalDivider(thickness = sz.hairline, color = c.hairline)
                    ToggleSettingRow(
                        title = stringResource(R.string.settings_rtl),
                        subtitle = stringResource(R.string.settings_rtl_desc),
                        checked = prefs.rtlReading,
                        onCheckedChange = viewModel::setRtlReading
                    )
                    HorizontalDivider(thickness = sz.hairline, color = c.hairline)
                    ToggleSettingRow(
                        title = stringResource(R.string.settings_keep_screen_on),
                        subtitle = stringResource(R.string.settings_keep_screen_on_desc),
                        checked = prefs.keepScreenOn,
                        onCheckedChange = viewModel::setKeepScreenOn
                    )
                    HorizontalDivider(thickness = sz.hairline, color = c.hairline)
                    ToggleSettingRow(
                        title = stringResource(R.string.settings_volume_keys),
                        subtitle = stringResource(R.string.settings_volume_keys_desc),
                        checked = prefs.volumeKeyNavigation,
                        onCheckedChange = viewModel::setVolumeKeyNavigation
                    )
                }
            }

            item(key = "section_fullscreen", contentType = "settings_section") {
                SettingsCardSection(title = stringResource(R.string.settings_section_fullscreen)) {
                    ToggleSettingRow(
                        title = stringResource(R.string.settings_show_page_pill),
                        subtitle = stringResource(R.string.settings_show_page_pill_desc),
                        checked = prefs.showPageIndicatorPill,
                        onCheckedChange = viewModel::setShowPageIndicatorPill
                    )
                    HorizontalDivider(thickness = sz.hairline, color = c.hairline)
                    ToggleSettingRow(
                        title = stringResource(R.string.settings_show_shortcuts),
                        subtitle = stringResource(R.string.settings_show_shortcuts_desc),
                        checked = prefs.showEdgeShortcutButtons,
                        onCheckedChange = viewModel::setShowEdgeShortcutButtons
                    )
                }
            }

            item(key = "section_rendering", contentType = "settings_section") {
                SettingsCardSection(title = stringResource(R.string.settings_section_rendering)) {
                    ChipOptionGroup(
                        label = stringResource(R.string.settings_render_quality),
                        options = RenderQuality.entries,
                        selected = prefs.renderQuality,
                        optionLabel = { stringResource(it.labelRes) },
                        onSelect = viewModel::setRenderQuality
                    )
                    HorizontalDivider(thickness = sz.hairline, color = c.hairline)
                    ToggleSettingRow(
                        title = stringResource(R.string.settings_hide_margins),
                        subtitle = stringResource(R.string.settings_hide_margins_desc),
                        checked = prefs.hideMarginsByDefault,
                        onCheckedChange = viewModel::setHideMarginsByDefault
                    )
                    HorizontalDivider(thickness = sz.hairline, color = c.hairline)
                    ToggleSettingRow(
                        title = stringResource(R.string.settings_page_spacing),
                        subtitle = stringResource(R.string.settings_page_spacing_desc),
                        checked = prefs.pageGapSpacing,
                        onCheckedChange = viewModel::setPageGapSpacing
                    )
                }
            }

            item(key = "section_storage", contentType = "settings_section") {
                SettingsCardSection(title = stringResource(R.string.settings_section_storage)) {
                    ToggleSettingRow(
                        title = stringResource(R.string.incognito_mode),
                        subtitle = stringResource(R.string.incognito_mode_active),
                        checked = prefs.incognitoMode,
                        onCheckedChange = viewModel::setIncognitoMode
                    )
                    HorizontalDivider(thickness = sz.hairline, color = c.hairline)
                    ActionSettingRow(
                        icon = Icons.Rounded.CleaningServices,
                        title = stringResource(R.string.settings_clear_cache),
                        subtitle = stringResource(R.string.settings_clear_cache_desc),
                        onClick = {
                            viewModel.clearRenderCache {
                                scope.launch { snackbarHostState.showSnackbar(cacheClearedMsg) }
                            }
                        }
                    )
                    HorizontalDivider(thickness = sz.hairline, color = c.hairline)
                    ActionSettingRow(
                        icon = Icons.Rounded.DeleteSweep,
                        title = stringResource(R.string.settings_clear_history),
                        subtitle = stringResource(R.string.settings_clear_history_desc),
                        isDestructive = true,
                        onClick = { showClearHistoryDialog = true }
                    )
                }
            }

            item(key = "bottom_spacer", contentType = "spacer") {
                Spacer(modifier = Modifier.height(s.xl))
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            containerColor = cs.surface,
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(stringResource(R.string.settings_clear_history)) },
            text = { Text(stringResource(R.string.settings_clear_history_confirm)) },
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

    if (showRebuildIndexDialog) {
        AlertDialog(
            onDismissRequest = { showRebuildIndexDialog = false },
            containerColor = cs.surface,
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(stringResource(R.string.settings_search_index_rebuild)) },
            text = { Text(stringResource(R.string.settings_search_index_rebuild_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        showRebuildIndexDialog = false
                        viewModel.rebuildSearchIndex()
                    },
                    modifier = Modifier.testTag("settings_rebuild_index_confirm")
                ) {
                    Text(stringResource(R.string.settings_search_index_rebuild))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRebuildIndexDialog = false }) {
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
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = cs.primary,
            modifier = Modifier.padding(start = s.xs, bottom = s.sm)
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = cs.surface,
            border = BorderStroke(sz.hairline, TeenaTheme.colors.hairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = s.lg, vertical = s.xs)) {
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
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit
) {
    val s = TeenaTheme.spacing
    Column(modifier = Modifier.padding(vertical = s.md)) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(s.sm))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(s.sm)) {
            items(
                items = options,
                key = { (it as? Enum<*>)?.name ?: it.hashCode() },
                contentType = { "option_chip" }
            ) { item ->
                FilterChip(
                    selected = item == selected,
                    onClick = { onSelect(item) },
                    label = { Text(optionLabel(item), style = MaterialTheme.typography.labelMedium) },
                    shape = CircleShape
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
    val s = TeenaTheme.spacing
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = s.md)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = s.md)
        ) {
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
    secondarySubtitle: String? = null,
    isDestructive: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?
) {
    val s = TeenaTheme.spacing
    val cs = MaterialTheme.colorScheme
    val tint = if (isDestructive) cs.error else cs.primary
    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(vertical = s.md)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Column(modifier = Modifier.padding(start = s.md)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isDestructive) cs.error else cs.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
            if (secondarySubtitle != null) {
                Text(
                    text = secondarySubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
        }
    }
}
