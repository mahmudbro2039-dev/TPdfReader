package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.example.R
import com.example.ui.model.ViewMode
import com.example.ui.theme.Brand
import com.example.ui.theme.TeenaTheme

enum class TeenaTab(val route: String) {
    Recent("tab_recent"),
    Library("tab_library"),
    Folders("tab_folders")
}

@Immutable
data class MenuAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val checked: Boolean? = null,
    val dividerBefore: Boolean = false
)

@Immutable
data class TopBarMenuCallbacks(
    val onToggleDarkMode: () -> Unit,
    val onToggleDeepSearch: () -> Unit,
    val onOpenSort: () -> Unit,
    val onToggleViewMode: () -> Unit,
    val onPickPdf: () -> Unit = {},
    val onRescanDevice: () -> Unit = {},
    val onOpenSettings: () -> Unit,
    val onOpenAbout: () -> Unit
)

@Composable
fun buildMenu(
    tab: TeenaTab,
    isDark: Boolean,
    deepSearch: Boolean,
    viewMode: ViewMode,
    callbacks: TopBarMenuCallbacks
): List<MenuAction> {
    val items = mutableListOf<MenuAction>()
    items += MenuAction(
        label = stringResource(R.string.open_pdf),
        icon = Icons.Rounded.FolderOpen,
        onClick = callbacks.onPickPdf
    )
    items += MenuAction(
        label = stringResource(R.string.menu_dark_mode),
        icon = Icons.Rounded.DarkMode,
        onClick = callbacks.onToggleDarkMode,
        checked = isDark,
        dividerBefore = true
    )
    items += MenuAction(
        label = stringResource(R.string.menu_deep_search),
        icon = Icons.Rounded.FindInPage,
        onClick = callbacks.onToggleDeepSearch,
        checked = deepSearch
    )
    if (tab == TeenaTab.Recent || tab == TeenaTab.Library) {
        items += MenuAction(
            label = stringResource(R.string.menu_sort),
            icon = Icons.AutoMirrored.Rounded.Sort,
            onClick = callbacks.onOpenSort
        )
    }
    if (tab == TeenaTab.Library) {
        val isGrid = viewMode == ViewMode.Grid
        items += MenuAction(
            label = stringResource(if (isGrid) R.string.menu_list_view else R.string.menu_grid_view),
            icon = if (isGrid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
            onClick = callbacks.onToggleViewMode
        )
    }
    items += MenuAction(
        label = stringResource(R.string.scanner_rescan_menu),
        icon = Icons.Rounded.Refresh,
        onClick = callbacks.onRescanDevice,
        dividerBefore = true
    )
    items += MenuAction(
        label = stringResource(R.string.menu_settings),
        icon = Icons.Rounded.Settings,
        onClick = callbacks.onOpenSettings
    )
    items += MenuAction(
        label = stringResource(R.string.menu_about),
        icon = Icons.Rounded.Info,
        onClick = callbacks.onOpenAbout
    )
    return items
}

@Composable
fun TeenaTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    menu: List<MenuAction>,
    modifier: Modifier = Modifier
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val motion = TeenaTheme.motion
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    BackHandler(enabled = focused || query.isNotEmpty()) {
        if (focused) {
            focusManager.clearFocus()
        } else {
            onQueryChange("")
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .heightIn(min = sz.topBarHeight)
                .padding(start = s.screenH, end = s.xs, top = s.xxs, bottom = s.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = !focused && query.isEmpty(),
                enter = fadeIn(motion.snapSpec()) + expandHorizontally(motion.snapSpec()),
                exit = fadeOut(motion.snapSpec()) + shrinkHorizontally(motion.snapSpec())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandWordmark()
                    Spacer(Modifier.width(s.md))
                }
            }
            TeenaSearchField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = onSearch,
                onFocusChange = { focused = it },
                modifier = Modifier.weight(1f)
            )
            OverflowMenu(menu)
        }
    }
}

@Composable
fun BrandWordmark(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = cs.onSurface)) {
                append(Brand.NAME_STRONG)
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Light, color = cs.primary)) {
                append(Brand.NAME_LIGHT)
            }
        },
        style = MaterialTheme.typography.titleLarge,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.testTag("brand_wordmark")
    )
}

@Composable
fun TeenaSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val c = TeenaTheme.colors
    val cs = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val pillTapInteraction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    LaunchedEffect(isFocused) { onFocusChange(isFocused) }

    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = query, selection = TextRange(query.length)))
    }
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)

    LaunchedEffect(query) {
        if (query != textFieldValue.text) {
            textFieldValue = TextFieldValue(
                text = query,
                selection = TextRange(query.length)
            )
        }
    }

    val activeText = textFieldValue.text
    val isActive = isFocused || activeText.isNotEmpty()
    val borderColor = if (isFocused) cs.primary.copy(alpha = 0.55f) else c.hairline.copy(alpha = 0.7f)
    val searchPillBg = if (isActive) cs.primaryContainer else cs.surfaceVariant
    val searchIconTint = if (isActive) cs.primary else cs.onSurfaceVariant

    BasicTextField(
        value = textFieldValue,
        onValueChange = { updated ->
            val previousText = textFieldValue.text
            textFieldValue = updated
            if (updated.text != previousText) {
                currentOnQueryChange(updated.text)
            }
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface),
        cursorBrush = SolidColor(cs.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            onSearch(textFieldValue.text)
            focusManager.clearFocus()
        }),
        interactionSource = interaction,
        modifier = modifier
            .heightIn(min = sz.searchHeight)
            .focusRequester(focusRequester)
            .testTag("top_search_field"),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = sz.searchHeight)
                    .clip(CircleShape)
                    .background(c.searchBg)
                    .border(sz.hairline, borderColor, CircleShape)
                    .clickable(
                        interactionSource = pillTapInteraction,
                        indication = null
                    ) {
                        focusRequester.requestFocus()
                    }
                    .padding(start = s.md, end = s.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = s.xs),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (activeText.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_hint),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        inner()
                    }
                }
                Spacer(Modifier.width(s.xs))
                if (activeText.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(sz.searchButton)
                            .clip(CircleShape)
                            .clickable {
                                textFieldValue = TextFieldValue("")
                                currentOnQueryChange("")
                                focusRequester.requestFocus()
                            }
                            .testTag("search_clear_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.clear),
                            modifier = Modifier.size(sz.iconXs),
                            tint = cs.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(s.xxs))
                }
                Box(
                    modifier = Modifier
                        .size(sz.searchButton)
                        .clip(CircleShape)
                        .background(searchPillBg)
                        .clickable {
                            onSearch(textFieldValue.text)
                            focusManager.clearFocus()
                        }
                        .testTag("search_submit_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.search),
                        modifier = Modifier.size(sz.searchIcon),
                        tint = searchIconTint
                    )
                }
            }
        }
    )
}

@Composable
fun OverflowMenu(actions: List<MenuAction>) {
    var open by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val sz = TeenaTheme.sizes
    Box {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.testTag("top_overflow_menu_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.more_options),
                tint = cs.onSurface
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = cs.surface,
            tonalElevation = sz.zeroElevation,
            shadowElevation = sz.menuElevation
        ) {
            actions.forEach { action ->
                if (action.dividerBefore) {
                    HorizontalDivider(
                        thickness = sz.hairline,
                        color = TeenaTheme.colors.hairline
                    )
                }
                DropdownMenuItem(
                    text = { Text(action.label, style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(action.icon, null, tint = cs.onSurfaceVariant) },
                    trailingIcon = action.checked?.let { checked ->
                        { Switch(checked = checked, onCheckedChange = null) }
                    },
                    onClick = {
                        open = false
                        action.onClick()
                    },
                    modifier = Modifier.testTag("menu_item_${action.label.lowercase().replace(" ", "_")}")
                )
            }
        }
    }
}
