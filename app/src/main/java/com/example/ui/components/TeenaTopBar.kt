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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
        label = stringResource(R.string.menu_dark_mode),
        icon = Icons.Rounded.DarkMode,
        onClick = callbacks.onToggleDarkMode,
        checked = isDark
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
        label = stringResource(R.string.menu_settings),
        icon = Icons.Rounded.Settings,
        onClick = callbacks.onOpenSettings,
        dividerBefore = true
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
    BackHandler(enabled = focused) { focusManager.clearFocus() }

    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .height(sz.topBarHeight)
                .padding(start = s.screenH, end = s.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = !focused,
                enter = fadeIn(motion.normalSpec()) + expandHorizontally(motion.normalSpec()),
                exit = fadeOut(motion.fastSpec()) + shrinkHorizontally(motion.fastSpec())
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
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    LaunchedEffect(isFocused) { onFocusChange(isFocused) }

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface),
        cursorBrush = SolidColor(cs.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            onSearch(query)
            focusManager.clearFocus()
        }),
        interactionSource = interaction,
        modifier = modifier
            .height(sz.searchHeight)
            .testTag("top_search_field"),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(c.searchBg)
                    .border(sz.hairline, if (isFocused) cs.primary else c.hairline, CircleShape)
                    .padding(start = s.lg, end = s.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_hint),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                    }
                    inner()
                }
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier
                            .size(sz.searchButton)
                            .testTag("search_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.clear),
                            modifier = Modifier.size(sz.iconSm),
                            tint = cs.onSurfaceVariant
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(sz.searchButton)
                        .clip(CircleShape)
                        .background(cs.primary)
                        .clickable {
                            onSearch(query)
                            focusManager.clearFocus()
                        }
                        .testTag("search_submit_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.search),
                        modifier = Modifier.size(sz.searchIcon),
                        tint = cs.onPrimary
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
