package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.BookmarkEntity
import com.example.data.model.PageFitPolicy
import com.example.data.model.PdfColorMode
import com.example.data.model.ReadingStatus
import com.example.data.model.ScrollLayoutMode
import com.example.data.pdf.TocEntry
import com.example.ui.viewmodel.ReaderUiState

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
    var selectedTab by remember { mutableIntStateOf(0) }
    var newBookmarkTitle by remember { mutableStateOf("") }
    var editingBookmark by remember { mutableStateOf<BookmarkEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Text(
                text = stringResource(R.string.reader_bookmarks),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Contents (${tocEntries.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Bookmarks (${bookmarks.size})") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("History (${visitedHistory.size})") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                0 -> {
                    // Table of Contents
                    if (tocEntries.isEmpty()) {
                        Text(
                            text = "No Table of Contents chapters detected in this document.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 340.dp)
                        ) {
                            items(tocEntries) { entry ->
                                val isCurrent = entry.pageIndex == currentPage
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onJumpToPage(entry.pageIndex)
                                            onDismiss()
                                        }
                                        .padding(vertical = 11.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Toc,
                                        contentDescription = null,
                                        tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
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
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "p. ${entry.pageIndex + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            }
                        }
                    }
                }

                1 -> {
                    // User Bookmarks
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = newBookmarkTitle,
                            onValueChange = { newBookmarkTitle = it },
                            placeholder = { Text("Bookmark Page ${currentPage + 1}…") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("bookmark_title_input")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onAddBookmark(currentPage, newBookmarkTitle)
                                newBookmarkTitle = ""
                            },
                            modifier = Modifier.testTag("add_bookmark_confirm_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()

                    if (bookmarks.isEmpty()) {
                        Text(
                            text = "No bookmarks saved in this PDF yet. Add one above for Page ${currentPage + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                        ) {
                            items(bookmarks, key = { it.id }) { bm ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onJumpToPage(bm.pageIndex)
                                            onDismiss()
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Bookmark,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = bm.title,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "Page ${bm.pageIndex + 1}",
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
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(onClick = { onDeleteBookmark(bm.id) }) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = stringResource(R.string.action_delete),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            }
                        }
                    }
                }

                else -> {
                    // Browser-like Jump & Visited History
                    val reversedHistory = visitedHistory.reversed()
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                    ) {
                        items(reversedHistory.size) { idx ->
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
                                    .padding(vertical = 11.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Page ${pageIdx + 1}",
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
                                        text = "Current",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
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

/**
 * MJ PDF v3.1's signature "Every Tool, One Tap Away" Unified Reader Menu Sheet.
 */
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Text(
                text = stringResource(R.string.reader_tools),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${state.documentTitle}  •  Page ${state.currentPage + 1} of ${state.pageCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 1. "Every Tool, One Tap Away" Action Grid (3 columns x 4 rows)
            val toolActions = listOf(
                ToolGridTile(Icons.Filled.Search, "Search PDF", state.isSearchBarVisible) {
                    onDismiss()
                    onOpenSearch()
                },
                ToolGridTile(Icons.AutoMirrored.Filled.Toc, "Contents & TOC", false) {
                    onDismiss()
                    onOpenTocAndBookmarks()
                },
                ToolGridTile(Icons.AutoMirrored.Filled.Subject, "Text Mode", false) {
                    onDismiss()
                    onOpenTextMode()
                },
                ToolGridTile(Icons.Filled.Highlight, "Notes & Marks", state.annotations.isNotEmpty()) {
                    onDismiss()
                    onOpenAnnotations()
                },
                ToolGridTile(Icons.Filled.Draw, "Sign PDF", false) {
                    onDismiss()
                    onOpenSignaturePad()
                },
                ToolGridTile(Icons.Filled.FormatQuote, "Share Quote", false) {
                    onDismiss()
                    onOpenShareQuote()
                },
                ToolGridTile(Icons.Filled.CropFree, "Hide Margins", state.hideMargins) {
                    onToggleHideMargins()
                },
                ToolGridTile(Icons.Filled.Link, "Links (${state.documentLinks.size})", state.documentLinks.isNotEmpty()) {
                    onDismiss()
                    onOpenDocumentLinks()
                },
                ToolGridTile(Icons.Filled.Policy, "Incognito", state.incognitoMode) {
                    onToggleIncognito()
                },
                ToolGridTile(Icons.Filled.ScreenRotation, "Rotate", false) {
                    onDismiss()
                    onRotateScreen()
                },
                ToolGridTile(Icons.Filled.Share, "Share File", false) {
                    onDismiss()
                    onSharePdf()
                },
                ToolGridTile(Icons.Filled.Print, "Print PDF", false) {
                    onDismiss()
                    onPrintPdf()
                }
            )

            toolActions.chunked(3).forEach { rowItems ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    rowItems.forEach { tile ->
                        ToolGridButton(
                            tile = tile,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // 2. Unified Reading Modes (Vertical, Horizontal, Single-Page Book Flip, Dual-Page)
            Text(
                text = stringResource(R.string.reader_layout_mode),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ScrollLayoutMode.entries) { mode ->
                    FilterChip(
                        selected = state.layoutMode == mode,
                        onClick = { onSelectLayout(mode) },
                        label = { Text(mode.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Page Fit Policies (Fit Width, Fit Height, Whole Page)
            Text(
                text = stringResource(R.string.reader_fit_policy),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PageFitPolicy.entries) { policy ->
                    FilterChip(
                        selected = state.pageFitPolicy == policy,
                        onClick = { onSelectFitPolicy(policy) },
                        label = { Text(policy.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. PDF Page Theme (Original, Dark Mode, Sepia, High Contrast)
            Text(
                text = stringResource(R.string.reader_color_mode),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PdfColorMode.entries) { colorMode ->
                    FilterChip(
                        selected = state.colorMode == colorMode,
                        onClick = { onSelectColorMode(colorMode) },
                        label = { Text(colorMode.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 5. Reading Status (5 statuses including Abandoned)
            Text(
                text = stringResource(R.string.change_reading_status),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ReadingStatus.entries) { status ->
                    FilterChip(
                        selected = state.readingStatus == status,
                        onClick = { onSelectStatus(status) },
                        label = { Text(status.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.reader_rtl_mode), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = state.isRtl, onCheckedChange = { onToggleRtl() })
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.reader_keep_screen_on), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = keepScreenOn, onCheckedChange = onToggleKeepScreenOn)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Document metadata & Settings entry card
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.reader_document_info),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${state.folderName}  •  ${formatFileSize(state.fileSizeBytes)}  •  Zoom ${String.format("%.1fx", state.zoomScale)}",
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
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.nav_settings))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
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
    val bgColor = if (tile.isActive) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val fgColor = if (tile.isActive) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = tile.onClick,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        contentColor = fgColor,
        modifier = modifier
            .height(68.dp)
            .then(
                if (tile.isActive) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = tile.icon,
                contentDescription = tile.label,
                modifier = Modifier.size(21.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tile.label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
