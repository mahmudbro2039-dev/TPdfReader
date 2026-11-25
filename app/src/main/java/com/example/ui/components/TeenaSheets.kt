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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.example.R
import com.example.data.model.FolderKey
import com.example.data.model.ReadingStatus
import com.example.data.ocr.OcrState
import com.example.data.search.db.IndexStatus
import com.example.data.search.db.IndexedDocEntity
import com.example.ui.model.FolderItem
import com.example.ui.model.PdfItem
import com.example.ui.model.SortOrder
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
    existingFolders: List<FolderItem>,
    indexedDoc: IndexedDocEntity? = null,
    ocrState: OcrState = OcrState.Idle,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onSelectStatus: (ReadingStatus) -> Unit,
    onMoveToFolder: (String) -> Unit,
    onStartOcr: () -> Unit = {},
    onStopOcr: () -> Unit = {},
    onRemove: () -> Unit
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val cs = MaterialTheme.colorScheme
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderInput by remember { mutableStateOf("") }
    val itemFolderKey = remember(item.folderName) { FolderKey.canonical(item.folderName) }
    val importedLabel = stringResource(R.string.folder_imported)
    val folderChoices = remember(existingFolders, importedLabel) {
        if (existingFolders.any { it.key == FolderKey.IMPORTED }) {
            existingFolders
        } else {
            existingFolders + FolderItem(
                key = FolderKey.IMPORTED,
                name = importedLabel,
                pdfCount = 0,
                totalSizeBytes = 0L,
                lastUpdatedAt = 0L
            )
        }
    }

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
                        if (item.hasBeenOpened && item.totalPages > 0) {
                            stringResource(R.string.page_of, item.currentPage, item.totalPages)
                        } else {
                            stringResource(R.string.file_size_local)
                        },
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
                            ReadingStatus.TO_READ -> R.string.status_to_read
                            ReadingStatus.READING -> R.string.status_reading
                            ReadingStatus.ON_HOLD -> R.string.status_on_hold
                            ReadingStatus.COMPLETED -> R.string.status_completed
                            ReadingStatus.ABANDONED -> R.string.status_abandoned
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

            Text(
                text = stringResource(R.string.move_to_folder),
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(s.sm)) {
                items(
                    items = folderChoices,
                    key = { it.key },
                    contentType = { "folder_option" }
                ) { folder ->
                    val label = if (folder.key == FolderKey.IMPORTED) {
                        importedLabel
                    } else {
                        folder.name.ifBlank { importedLabel }
                    }
                    FilterChip(
                        selected = itemFolderKey == folder.key,
                        onClick = {
                            onMoveToFolder(folder.key)
                            onDismiss()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(sz.iconSm)
                            )
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        shape = CircleShape
                    )
                }
                item(key = "new_virtual_folder", contentType = "new_folder_option") {
                    FilterChip(
                        selected = false,
                        onClick = {
                            newFolderInput = ""
                            showNewFolderDialog = true
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.CreateNewFolder,
                                contentDescription = null,
                                modifier = Modifier.size(sz.iconSm)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.folder_new),
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        shape = CircleShape,
                        modifier = Modifier.testTag("new_folder_chip")
                    )
                }
            }
            Text(
                text = stringResource(R.string.folder_move_hint),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )

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

            val runningOcrForDoc = ocrState as? OcrState.Running
            val isOcrRunningForDoc = runningOcrForDoc?.uri == item.uri
            val canStartOcr = indexedDoc != null &&
                indexedDoc.status == IndexStatus.DONE &&
                indexedDoc.textPages < indexedDoc.pageCount &&
                !indexedDoc.ocrFinished

            if (isOcrRunningForDoc && runningOcrForDoc != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onStopOcr() }
                        .padding(vertical = s.md, horizontal = s.xs)
                        .testTag("action_stop_ocr"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(s.md)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.StopCircle,
                        contentDescription = null,
                        tint = cs.primary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(s.xxs)) {
                        Text(
                            text = stringResource(R.string.action_stop_ocr),
                            style = MaterialTheme.typography.bodyLarge,
                            color = cs.onSurface
                        )
                        Text(
                            text = stringResource(
                                R.string.ocr_progress,
                                runningOcrForDoc.page,
                                runningOcrForDoc.total
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            } else if (canStartOcr) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onStartOcr() }
                        .padding(vertical = s.md, horizontal = s.xs)
                        .testTag("action_make_searchable"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(s.md)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DocumentScanner,
                        contentDescription = null,
                        tint = cs.primary
                    )
                    Text(
                        text = stringResource(R.string.action_make_searchable),
                        style = MaterialTheme.typography.bodyLarge,
                        color = cs.onSurface
                    )
                }
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

    if (showNewFolderDialog) {
        val trimmedName = newFolderInput.trim()
        val isValidName = trimmedName.length in 1..40
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            containerColor = cs.surface,
            title = {
                Text(
                    text = stringResource(R.string.folder_new_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface
                )
            },
            text = {
                OutlinedTextField(
                    value = newFolderInput,
                    onValueChange = { next ->
                        if (next.length <= 40) newFolderInput = next
                    },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.folder_new_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_folder_name_input")
                )
            },
            confirmButton = {
                TextButton(
                    enabled = isValidName,
                    onClick = {
                        onMoveToFolder(FolderKey.virtual(trimmedName))
                        showNewFolderDialog = false
                        onDismiss()
                    },
                    modifier = Modifier.testTag("new_folder_confirm_button")
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNewFolderDialog = false },
                    modifier = Modifier.testTag("new_folder_cancel_button")
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
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

@Composable
fun RemoveDocumentConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Text(
                text = stringResource(R.string.remove_confirm_title),
                style = MaterialTheme.typography.titleLarge,
                color = cs.onSurface
            )
        },
        text = {
            Text(
                text = stringResource(R.string.remove_confirm_with_notes),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("remove_confirm_cancel")
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("remove_confirm_delete")
            ) {
                Text(
                    text = stringResource(R.string.action_remove),
                    color = cs.error
                )
            }
        }
    )
}
