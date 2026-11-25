package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.example.R
import com.example.data.local.BookmarkEntity
import com.example.data.model.PageFitPolicy
import com.example.data.model.PdfColorMode
import com.example.data.model.ReadingStatus
import com.example.data.model.ScrollLayoutMode
import com.example.data.pdf.TocEntry
import com.example.ui.theme.Alpha
import com.example.ui.theme.TeenaTheme
import com.example.ui.viewmodel.OrientationLock
import com.example.ui.viewmodel.ReaderUiState
import java.util.Locale

@Composable
fun GoToPageDialog(
    currentPage: Int,
    pageCount: Int,
    onDismiss: () -> Unit,
    onGoToPage: (Int) -> Unit
) {
    var pageInput by remember { mutableStateOf((currentPage + 1).toString()) }
    val parsedPage = pageInput.toIntOrNull()
    val isValid = parsedPage != null && parsedPage in 1..pageCount.coerceAtLeast(1)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_go_to_page)) },
        text = {
            Column {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { input -> pageInput = input.filter { it.isDigit() } },
                    label = { Text(stringResource(R.string.reader_go_to_page_hint, pageCount.coerceAtLeast(1))) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = pageInput.isNotEmpty() && !isValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("go_to_page_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValid && parsedPage != null) {
                        onGoToPage(parsedPage - 1)
                        onDismiss()
                    }
                },
                enabled = isValid,
                modifier = Modifier.testTag("go_to_page_confirm")
            ) {
                Text(stringResource(R.string.reader_go))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksAndTocBottomSheet(
    currentPage: Int,
    bookmarks: List<BookmarkEntity>,
    tocEntries: List<TocEntry>,
    visitedHistory: List<Int>,
    onDismiss: () -> Unit,
    onAddBookmark: (pageIndex: Int, title: String) -> Unit,
    onJumpToPage: (Int) -> Unit,
    onRenameBookmark: (Long, String) -> Unit,
    onDeleteBookmark: (Long) -> Unit
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes

    var selectedTab by remember { mutableIntStateOf(0) }
    var newBookmarkTitle by remember { mutableStateOf("") }
    var editingBookmark by remember { mutableStateOf<BookmarkEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = s.screenH, vertical = s.sm)
        ) {
            Text(
                text = stringResource(R.string.reader_bookmarks),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(s.md))

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.reader_tab_contents, tocEntries.size)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.reader_tab_bookmarks, bookmarks.size)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(stringResource(R.string.reader_tab_history, visitedHistory.size)) }
                )
            }

            Spacer(modifier = Modifier.height(s.md))

            when (selectedTab) {
                0 -> {
                    if (tocEntries.isEmpty()) {
                        Text(
                            text = stringResource(R.string.reader_no_toc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = s.xl)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = sz.sheetListMaxHeight)
                        ) {
                            items(
                                count = tocEntries.size,
                                key = { idx -> "toc_${idx}_${tocEntries[idx].pageIndex}" },
                                contentType = { "toc_entry" }
                            ) { idx ->
                                val entry = tocEntries[idx]
                                val isCurrent = entry.pageIndex == currentPage
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onJumpToPage(entry.pageIndex)
                                            onDismiss()
                                        }
                                        .padding(vertical = s.md)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Toc,
                                        contentDescription = null,
                                        tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(s.md))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.chapterLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = entry.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Surface(
                                        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            text = stringResource(R.string.reader_page_short, entry.pageIndex + 1),
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = s.sm, vertical = s.xs)
                                        )
                                    }
                                }
                                HorizontalDivider(color = TeenaTheme.colors.hairline)
                            }
                        }
                    }
                }

                1 -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = newBookmarkTitle,
                            onValueChange = { newBookmarkTitle = it },
                            placeholder = { Text(stringResource(R.string.reader_bookmark_title_hint)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("bookmark_title_input")
                        )
                        Spacer(modifier = Modifier.width(s.sm))
                        Button(
                            onClick = {
                                onAddBookmark(currentPage, newBookmarkTitle)
                                newBookmarkTitle = ""
                            },
                            modifier = Modifier.testTag("add_bookmark_confirm_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(sz.searchIcon))
                            Spacer(modifier = Modifier.width(s.xs))
                            Text(stringResource(R.string.action_save))
                        }
                    }

                    Spacer(modifier = Modifier.height(s.md))
                    HorizontalDivider(color = TeenaTheme.colors.hairline)

                    if (bookmarks.isEmpty()) {
                        Text(
                            text = stringResource(R.string.empty_bookmarks_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = s.xl)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = sz.sheetListMaxHeight)
                        ) {
                            items(
                                items = bookmarks,
                                key = { it.id },
                                contentType = { "bookmark_item" }
                            ) { bm ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onJumpToPage(bm.pageIndex)
                                            onDismiss()
                                        }
                                        .padding(vertical = s.sm),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Bookmark,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(s.md))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = bm.title.ifBlank {
                                                stringResource(R.string.page_badge_short, bm.pageIndex + 1)
                                            },
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = stringResource(R.string.page_badge_short, bm.pageIndex + 1),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            editingBookmark = bm
                                            renameText = bm.title
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = stringResource(R.string.reader_edit_bookmark),
                                            modifier = Modifier.size(sz.searchIcon)
                                        )
                                    }
                                    IconButton(onClick = { onDeleteBookmark(bm.id) }) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = stringResource(R.string.action_delete),
                                            modifier = Modifier.size(sz.searchIcon)
                                        )
                                    }
                                }
                                HorizontalDivider(color = TeenaTheme.colors.hairline)
                            }
                        }
                    }
                }

                else -> {
                    val reversedHistory = visitedHistory.reversed()
                    if (reversedHistory.isEmpty()) {
                        Text(
                            text = stringResource(R.string.reader_no_jump_history),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = s.xl)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = sz.sheetListMaxHeight)
                        ) {
                            items(
                                count = reversedHistory.size,
                                key = { idx -> "history_${idx}_${reversedHistory[idx]}" },
                                contentType = { "history_item" }
                            ) { idx ->
                                val pageIdx = reversedHistory[idx]
                                val matchingChapter = tocEntries.lastOrNull { it.pageIndex <= pageIdx }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onJumpToPage(pageIdx)
                                            onDismiss()
                                        }
                                        .padding(vertical = s.md)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(s.md))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.page_badge_short, pageIdx + 1),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (matchingChapter != null) {
                                            Text(
                                                text = matchingChapter.title,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (idx == 0) {
                                        Text(
                                            text = stringResource(R.string.reader_current_page_label, pageIdx + 1),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                HorizontalDivider(color = TeenaTheme.colors.hairline)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(s.xl))
        }
    }

    editingBookmark?.let { target ->
        AlertDialog(
            onDismissRequest = { editingBookmark = null },
            title = { Text(stringResource(R.string.reader_edit_bookmark)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameBookmark(target.id, renameText)
                        editingBookmark = null
                    }
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingBookmark = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickToolsBottomSheet(
    state: ReaderUiState,
    keepScreenOn: Boolean,
    onDismiss: () -> Unit,
    onSelectLayout: (ScrollLayoutMode) -> Unit,
    onSelectFitPolicy: (PageFitPolicy) -> Unit,
    onSelectColorMode: (PdfColorMode) -> Unit,
    onToggleHideMargins: () -> Unit,
    onToggleIncognito: () -> Unit,
    onToggleRtl: () -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onSelectStatus: (ReadingStatus) -> Unit,
    onOpenSearch: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenGoToPage: () -> Unit,
    onToggleAutoScroll: () -> Unit,
    onJumpBack: () -> Unit,
    onJumpForward: () -> Unit,
    onOpenTocAndBookmarks: () -> Unit,
    onOpenAnnotations: () -> Unit,
    onOpenSignaturePad: () -> Unit,
    onOpenShareQuote: () -> Unit,
    onOpenDocumentLinks: () -> Unit,
    onOpenTextMode: () -> Unit,
    onRotateScreen: () -> Unit,
    onSharePdf: () -> Unit,
    onPrintPdf: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = s.screenH, vertical = s.sm)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.reader_tools),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(
                            R.string.reader_doc_page_meta,
                            state.documentTitle.ifBlank { stringResource(R.string.reader_default_title) },
                            stringResource(R.string.page_of, state.currentPage + 1, state.pageCount.coerceAtLeast(1))
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (state.canJumpBack) {
                    IconButton(onClick = {
                        onDismiss()
                        onJumpBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = stringResource(R.string.reader_history_back)
                        )
                    }
                }
                if (state.canJumpForward) {
                    IconButton(onClick = {
                        onDismiss()
                        onJumpForward()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = stringResource(R.string.reader_history_forward)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(s.lg))

            val toolActions = listOf(
                ToolGridTile(Icons.Filled.Search, stringResource(R.string.reader_search_in_pdf), state.isSearchBarVisible) {
                    onDismiss()
                    onOpenSearch()
                },
                ToolGridTile(
                    if (state.isCurrentPageBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    stringResource(R.string.reader_add_bookmark),
                    state.isCurrentPageBookmarked
                ) {
                    onToggleBookmark()
                },
                ToolGridTile(Icons.AutoMirrored.Filled.Toc, stringResource(R.string.reader_bookmarks), false) {
                    onDismiss()
                    onOpenTocAndBookmarks()
                },
                ToolGridTile(Icons.Filled.Numbers, stringResource(R.string.reader_go_to_page), false) {
                    onDismiss()
                    onOpenGoToPage()
                },
                ToolGridTile(Icons.AutoMirrored.Filled.Subject, stringResource(R.string.reader_text_mode), false) {
                    onDismiss()
                    onOpenTextMode()
                },
                ToolGridTile(Icons.Filled.Highlight, stringResource(R.string.reader_annotations), state.annotations.isNotEmpty()) {
                    onDismiss()
                    onOpenAnnotations()
                },
                ToolGridTile(Icons.Filled.Draw, stringResource(R.string.reader_sign_pdf), false) {
                    onDismiss()
                    onOpenSignaturePad()
                },
                ToolGridTile(Icons.Filled.FormatQuote, stringResource(R.string.reader_share_quote), false) {
                    onDismiss()
                    onOpenShareQuote()
                },
                ToolGridTile(Icons.Filled.CropFree, stringResource(R.string.reader_hide_margins), state.hideMargins) {
                    onToggleHideMargins()
                },
                ToolGridTile(
                    if (state.isAutoScrollActive) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    stringResource(R.string.reader_auto_scroll),
                    state.isAutoScrollActive
                ) {
                    onDismiss()
                    onToggleAutoScroll()
                },
                ToolGridTile(
                    Icons.Filled.Link,
                    stringResource(
                        R.string.reader_links_count,
                        stringResource(R.string.reader_links),
                        state.documentLinks.size
                    ),
                    state.documentLinks.isNotEmpty()
                ) {
                    onDismiss()
                    onOpenDocumentLinks()
                },
                ToolGridTile(Icons.Filled.Policy, stringResource(R.string.incognito_mode), state.incognitoMode) {
                    onToggleIncognito()
                },
                ToolGridTile(
                    Icons.Filled.ScreenRotation,
                    stringResource(
                        if (state.orientationLock == OrientationLock.LOCKED) {
                            R.string.reader_orientation_locked
                        } else {
                            R.string.reader_orientation_lock
                        }
                    ),
                    state.orientationLock == OrientationLock.LOCKED
                ) {
                    onDismiss()
                    onRotateScreen()
                },
                ToolGridTile(Icons.Filled.Share, stringResource(R.string.action_share), false) {
                    onDismiss()
                    onSharePdf()
                },
                ToolGridTile(Icons.Filled.Print, stringResource(R.string.action_print), false) {
                    onDismiss()
                    onPrintPdf()
                }
            )

            toolActions.chunked(3).forEach { rowItems ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(s.sm),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = s.xs)
                ) {
                    rowItems.forEach { tile ->
                        ToolGridButton(
                            tile = tile,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(s.lg))
            HorizontalDivider(color = TeenaTheme.colors.hairline)
            Spacer(modifier = Modifier.height(s.md))

            Text(
                text = stringResource(R.string.reader_layout_mode),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(s.sm))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(s.sm)) {
                items(
                    items = ScrollLayoutMode.entries,
                    key = { it.name },
                    contentType = { "layout_mode" }
                ) { mode ->
                    FilterChip(
                        selected = state.layoutMode == mode,
                        onClick = { onSelectLayout(mode) },
                        label = { Text(stringResource(mode.labelRes)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(s.md))

            Text(
                text = stringResource(R.string.reader_fit_policy),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(s.sm))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(s.sm)) {
                items(
                    items = PageFitPolicy.entries,
                    key = { it.name },
                    contentType = { "fit_policy" }
                ) { policy ->
                    FilterChip(
                        selected = state.pageFitPolicy == policy,
                        onClick = { onSelectFitPolicy(policy) },
                        label = { Text(stringResource(policy.labelRes)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(s.md))

            Text(
                text = stringResource(R.string.reader_color_mode),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(s.sm))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(s.sm)) {
                items(
                    items = PdfColorMode.entries,
                    key = { it.name },
                    contentType = { "color_mode" }
                ) { colorMode ->
                    FilterChip(
                        selected = state.colorMode == colorMode,
                        onClick = { onSelectColorMode(colorMode) },
                        label = { Text(stringResource(colorMode.labelRes)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(s.md))

            Text(
                text = stringResource(R.string.change_reading_status),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(s.sm))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(s.sm)) {
                items(
                    items = ReadingStatus.entries,
                    key = { it.name },
                    contentType = { "reading_status" }
                ) { status ->
                    FilterChip(
                        selected = state.readingStatus == status,
                        onClick = { onSelectStatus(status) },
                        label = { Text(stringResource(status.labelRes)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(s.md))
            HorizontalDivider(color = TeenaTheme.colors.hairline)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = s.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.reader_rtl_mode), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = state.isRtl, onCheckedChange = { onToggleRtl() })
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = s.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.reader_keep_screen_on), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = keepScreenOn, onCheckedChange = onToggleKeepScreenOn)
            }

            Spacer(modifier = Modifier.height(s.md))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = Alpha.cardSurface),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(s.lg)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.reader_document_info),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(s.xxs))
                        val displayFolder = state.folderName.ifBlank {
                            stringResource(R.string.default_folder_documents)
                        }
                        Text(
                            text = stringResource(
                                R.string.reader_doc_info_detail,
                                displayFolder,
                                formatFileSize(state.fileSizeBytes),
                                state.zoomScale
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = {
                            onDismiss()
                            onOpenSettings()
                        }
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(sz.iconSm))
                        Spacer(modifier = Modifier.width(s.xs))
                        Text(stringResource(R.string.menu_settings))
                    }
                }
            }

            Spacer(modifier = Modifier.height(s.xl))
        }
    }
}

private data class ToolGridTile(
    val icon: ImageVector,
    val label: String,
    val isActive: Boolean,
    val onClick: () -> Unit
)

@Composable
private fun ToolGridButton(
    tile: ToolGridTile,
    modifier: Modifier = Modifier
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val bgColor = if (tile.isActive) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = Alpha.medium)
    }
    val fgColor = if (tile.isActive) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = tile.onClick,
        shape = MaterialTheme.shapes.medium,
        color = bgColor,
        contentColor = fgColor,
        modifier = modifier
            .height(sz.toolTileHeight)
            .then(
                if (tile.isActive) {
                    Modifier.border(sz.hairline, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                } else {
                    Modifier
                }
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = s.sm, vertical = s.sm)
        ) {
            Icon(
                imageVector = tile.icon,
                contentDescription = tile.label,
                modifier = Modifier.size(sz.iconTool)
            )
            Spacer(modifier = Modifier.height(s.xs))
            Text(
                text = tile.label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
