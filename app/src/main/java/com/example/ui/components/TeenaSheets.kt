package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.example.R
import com.example.ui.model.AnnotationUiItem
import com.example.ui.model.BookmarkUiItem
import com.example.ui.model.PdfItem
import com.example.ui.model.SortOrder
import com.example.ui.theme.ReadingStatus
import com.example.ui.theme.TeenaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(
    selectedSort: SortOrder,
    onSelectSort: (SortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    val s = TeenaTheme.spacing
    val cs = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = s.lg, vertical = s.sm)
        ) {
            Text(
                text = stringResource(R.string.sort_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                color = cs.onSurface,
                modifier = Modifier.padding(bottom = s.sm)
            )
            SortOrder.entries.forEach { order ->
                val label = stringResource(
                    when (order) {
                        SortOrder.LastOpened -> R.string.sort_last_opened
                        SortOrder.Name -> R.string.sort_name
                        SortOrder.DateAdded -> R.string.sort_date_added
                        SortOrder.Size -> R.string.sort_size
                        SortOrder.Progress -> R.string.sort_progress
                    }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable {
                            onSelectSort(order)
                            onDismiss()
                        }
                        .padding(vertical = s.sm, horizontal = s.xs)
                        .testTag("sort_option_${order.name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedSort == order,
                        onClick = null
                    )
                    Spacer(Modifier.width(s.md))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = cs.onSurface
                    )
                }
            }
            Spacer(Modifier.height(s.xl))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentActionsSheet(
    item: PdfItem,
    existingFolders: List<String>,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onSelectStatus: (ReadingStatus) -> Unit,
    onMoveToFolder: (String) -> Unit,
    onRemove: () -> Unit
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val cs = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = s.lg, vertical = s.sm),
            verticalArrangement = Arrangement.spacedBy(s.md)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(s.xxs)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        R.string.folder_meta_format,
                        stringResource(R.string.page_of, item.currentPage, item.totalPages),
                        formatFileSize(item.sizeBytes)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(thickness = sz.hairline, color = TeenaTheme.colors.hairline)

            Text(
                text = stringResource(R.string.change_reading_status),
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(s.sm)) {
                items(
                    items = ReadingStatus.entries,
                    key = { it.name },
                    contentType = { "status_option" }
                ) { status ->
                    val label = stringResource(
                        when (status) {
                            ReadingStatus.ToRead -> R.string.status_to_read
                            ReadingStatus.Reading -> R.string.status_reading
                            ReadingStatus.Completed -> R.string.status_completed
                            ReadingStatus.OnHold -> R.string.status_on_hold
                        }
                    )
                    FilterChip(
                        selected = item.status == status,
                        onClick = {
                            onSelectStatus(status)
                            onDismiss()
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        shape = CircleShape
                    )
                }
            }

            if (existingFolders.size > 1) {
                Text(
                    text = stringResource(R.string.move_to_folder),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(s.sm)) {
                    items(
                        items = existingFolders,
                        key = { it },
                        contentType = { "folder_option" }
                    ) { folder ->
                        FilterChip(
                            selected = item.folderName.equals(folder, ignoreCase = true),
                            onClick = {
                                onMoveToFolder(folder)
                                onDismiss()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(sz.iconSm)
                                )
                            },
                            label = { Text(folder, style = MaterialTheme.typography.labelMedium) },
                            shape = CircleShape
                        )
                    }
                }
            }

            HorizontalDivider(thickness = sz.hairline, color = TeenaTheme.colors.hairline)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable {
                        onTogglePin()
                        onDismiss()
                    }
                    .padding(vertical = s.md, horizontal = s.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(s.md)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = null,
                    tint = cs.primary
                )
                Text(
                    text = stringResource(if (item.isPinned) R.string.unpin_favorite else R.string.pin_favorite),
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurface
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable {
                        onRemove()
                        onDismiss()
                    }
                    .padding(vertical = s.md, horizontal = s.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(s.md)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = cs.error
                )
                Text(
                    text = stringResource(R.string.remove_from_library),
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.error
                )
            }
            Spacer(Modifier.height(s.lg))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesAndHighlightsSheet(
    annotations: List<AnnotationUiItem>,
    bookmarks: List<BookmarkUiItem>,
    onOpenDocumentPage: (uri: String, pageIndex: Int) -> Unit,
    onDeleteAnnotation: (Long) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val cs = MaterialTheme.colorScheme
    var selectedTab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = s.lg)
        ) {
            Text(
                text = stringResource(R.string.notes_highlights),
                style = MaterialTheme.typography.titleLarge,
                color = cs.onSurface,
                modifier = Modifier.padding(bottom = s.sm)
            )
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = cs.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.tab_highlights_label)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.tab_bookmarks_label)) }
                )
            }
            Spacer(Modifier.height(s.md))

            if (selectedTab == 0) {
                if (annotations.isEmpty()) {
                    Text(
                        text = stringResource(R.string.empty_annotations_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = s.xl)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = sz.sheetListMaxHeight),
                        verticalArrangement = Arrangement.spacedBy(s.sm)
                    ) {
                        items(
                            items = annotations,
                            key = { it.id },
                            contentType = { "annotation_item" }
                        ) { note ->
                            val swatchColor = parseHexColor(
                                hex = note.colorHex,
                                fallback = TeenaTheme.colors.reader.highlightDefault
                            )
                            Surface(
                                onClick = {
                                    onDismiss()
                                    onOpenDocumentPage(note.documentUri, (note.pageNumber - 1).coerceAtLeast(0))
                                },
                                shape = MaterialTheme.shapes.medium,
                                color = cs.background,
                                border = BorderStroke(sz.hairline, TeenaTheme.colors.hairline)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(s.md),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(s.md)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(sz.colorDotSize)
                                            .clip(CircleShape)
                                            .background(swatchColor)
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(s.xxs)
                                    ) {
                                        Text(
                                            text = note.quotedText.ifBlank { note.noteText },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = cs.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.folder_meta_format,
                                                note.documentTitle,
                                                stringResource(R.string.page_badge_short, note.pageNumber)
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = cs.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(onClick = { onDeleteAnnotation(note.id) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = stringResource(R.string.action_delete),
                                            tint = cs.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (bookmarks.isEmpty()) {
                    Text(
                        text = stringResource(R.string.empty_bookmarks_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = s.xl)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = sz.sheetListMaxHeight),
                        verticalArrangement = Arrangement.spacedBy(s.sm)
                    ) {
                        items(
                            items = bookmarks,
                            key = { it.id },
                            contentType = { "bookmark_item" }
                        ) { bookmark ->
                            Surface(
                                onClick = {
                                    onDismiss()
                                    onOpenDocumentPage(bookmark.documentUri, (bookmark.pageNumber - 1).coerceAtLeast(0))
                                },
                                shape = MaterialTheme.shapes.medium,
                                color = cs.background,
                                border = BorderStroke(sz.hairline, TeenaTheme.colors.hairline)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(s.md),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(s.md)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bookmark,
                                        contentDescription = null,
                                        tint = cs.primary
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(s.xxs)
                                    ) {
                                        Text(
                                            text = bookmark.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = cs.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.folder_meta_format,
                                                bookmark.documentTitle,
                                                stringResource(R.string.page_badge_short, bookmark.pageNumber)
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = cs.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(onClick = { onDeleteBookmark(bookmark.id) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = stringResource(R.string.action_delete),
                                            tint = cs.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(s.xl))
        }
    }
}

@Composable
fun AboutTeenaDialog(onDismiss: () -> Unit) {
    val s = TeenaTheme.spacing
    val cs = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(s.xs)) {
                BrandWordmark()
                Text(
                    text = stringResource(R.string.about_version),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant
                )
            }
        },
        text = {
            Text(
                text = stringResource(R.string.about_body),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_ok))
            }
        }
    )
}
