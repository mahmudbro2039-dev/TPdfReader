package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.example.R
import com.example.ui.components.EmptyState
import com.example.ui.components.FilterChipsRow
import com.example.ui.components.FolderBreadcrumb
import com.example.ui.components.FolderCard
import com.example.ui.components.HeroContinueCard
import com.example.ui.components.PdfGridCard
import com.example.ui.components.PdfRowCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.ShimmerLoadingGrid
import com.example.ui.components.ShimmerLoadingList
import com.example.ui.components.formatFileSize
import com.example.ui.model.FolderItem
import com.example.ui.model.PdfItem
import com.example.ui.model.ViewMode
import com.example.ui.theme.ReadingStatus
import com.example.ui.theme.TeenaTheme

@Composable
fun RecentTabScreen(
    isLoading: Boolean,
    heroItem: PdfItem?,
    remainingItems: List<PdfItem>,
    listState: LazyListState,
    onOpenPdf: (PdfItem) -> Unit,
    onItemMore: (PdfItem) -> Unit,
    onPickPdf: () -> Unit
) {
    val s = TeenaTheme.spacing

    when {
        isLoading -> ShimmerLoadingList()
        heroItem == null -> EmptyState(
            title = stringResource(R.string.empty_title),
            body = stringResource(R.string.empty_body),
            showOpenButton = true,
            onOpenPdf = onPickPdf
        )
        else -> {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = s.screenH,
                    end = s.screenH,
                    top = s.sm,
                    bottom = s.listBottom
                ),
                verticalArrangement = Arrangement.spacedBy(s.sm)
            ) {
                item(key = "hero", contentType = "hero") {
                    HeroContinueCard(
                        item = heroItem,
                        onClick = { onOpenPdf(heroItem) }
                    )
                }
                if (remainingItems.isNotEmpty()) {
                    item(key = "recent_header", contentType = "header") {
                        SectionHeader(
                            text = stringResource(R.string.recent_history)
                        )
                    }
                    items(
                        items = remainingItems,
                        key = { it.id },
                        contentType = { "pdf" }
                    ) { item ->
                        PdfRowCard(
                            item = item,
                            onClick = { onOpenPdf(item) },
                            onMore = onItemMore,
                            compact = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryTabScreen(
    isLoading: Boolean,
    items: List<PdfItem>,
    viewMode: ViewMode,
    selectedStatus: ReadingStatus?,
    statusCounts: Map<ReadingStatus, Int>,
    totalCount: Int,
    listState: LazyListState,
    gridState: LazyGridState,
    onSelectStatus: (ReadingStatus?) -> Unit,
    onOpenPdf: (PdfItem) -> Unit,
    onItemMore: (PdfItem) -> Unit,
    onPickPdf: () -> Unit
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes

    if (isLoading) {
        if (viewMode == ViewMode.Grid) {
            ShimmerLoadingGrid()
        } else {
            ShimmerLoadingList()
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FilterChipsRow(
            selectedStatus = selectedStatus,
            counts = statusCounts,
            totalCount = totalCount,
            onSelectStatus = onSelectStatus
        )

        when {
            totalCount == 0 -> EmptyState(
                title = stringResource(R.string.empty_title),
                body = stringResource(R.string.empty_body),
                showOpenButton = true,
                onOpenPdf = onPickPdf
            )
            items.isEmpty() -> EmptyState(
                title = stringResource(R.string.empty_filter_title),
                body = stringResource(R.string.empty_filter_body),
                showOpenButton = false,
                onOpenPdf = onPickPdf
            )
            viewMode == ViewMode.List -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = s.screenH,
                        end = s.screenH,
                        top = s.sm,
                        bottom = s.listBottom
                    ),
                    verticalArrangement = Arrangement.spacedBy(s.md)
                ) {
                    items(
                        items = items,
                        key = { it.id },
                        contentType = { "pdf" }
                    ) { item ->
                        PdfRowCard(
                            item = item,
                            onClick = { onOpenPdf(item) },
                            onMore = onItemMore
                        )
                    }
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(sz.gridMinCell),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = s.screenH,
                        end = s.screenH,
                        top = s.sm,
                        bottom = s.listBottom
                    ),
                    horizontalArrangement = Arrangement.spacedBy(s.gridGapH),
                    verticalArrangement = Arrangement.spacedBy(s.gridGapV)
                ) {
                    items(
                        items = items,
                        key = { it.id },
                        contentType = { "pdf" }
                    ) { item ->
                        PdfGridCard(
                            item = item,
                            onClick = { onOpenPdf(item) },
                            onMore = onItemMore
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FoldersTabScreen(
    isLoading: Boolean,
    folders: List<FolderItem>,
    selectedFolderName: String?,
    folderPdfs: List<PdfItem>,
    listState: LazyListState,
    gridState: LazyGridState,
    onOpenFolder: (String) -> Unit,
    onCloseFolder: () -> Unit,
    onOpenPdf: (PdfItem) -> Unit,
    onItemMore: (PdfItem) -> Unit,
    onPickPdf: () -> Unit
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val cs = MaterialTheme.colorScheme

    BackHandler(enabled = selectedFolderName != null) {
        onCloseFolder()
    }

    if (isLoading) {
        ShimmerLoadingGrid()
        return
    }

    val folderTotalSizeBytes = remember(folderPdfs) { folderPdfs.sumOf { it.sizeBytes } }

    Column(modifier = Modifier.fillMaxSize()) {
        FolderBreadcrumb(
            currentFolder = selectedFolderName,
            pdfCount = folderPdfs.size,
            totalSizeBytes = folderTotalSizeBytes,
            onRootClick = onCloseFolder
        )

        if (selectedFolderName == null) {
            if (folders.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.empty_folders_title),
                    body = stringResource(R.string.empty_folders_body),
                    showOpenButton = true,
                    onOpenPdf = onPickPdf
                )
            } else {
                val (totalPdfs, totalBytesAll) = remember(folders) {
                    folders.sumOf { it.pdfCount } to folders.sumOf { it.totalSizeBytes }
                }
                val totalSize = formatFileSize(totalBytesAll)
                val folderCountLabel = pluralStringResource(R.plurals.folder_count, folders.size, folders.size)
                val pdfCountLabel = pluralStringResource(R.plurals.pdf_count, totalPdfs, totalPdfs)

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(sz.folderGridMinCell),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = s.screenH,
                        end = s.screenH,
                        top = s.sm,
                        bottom = s.listBottom
                    ),
                    horizontalArrangement = Arrangement.spacedBy(s.gridGapH),
                    verticalArrangement = Arrangement.spacedBy(s.gridGapV)
                ) {
                    item(
                        key = "folders_summary",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "header"
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = s.xs),
                            horizontalArrangement = Arrangement.spacedBy(s.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = folderCountLabel.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = stringResource(R.string.folder_meta_format, pdfCountLabel, totalSize),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    items(
                        items = folders,
                        key = { it.name },
                        contentType = { "folder" }
                    ) { folder ->
                        FolderCard(
                            folder = folder,
                            onClick = { onOpenFolder(folder.name) }
                        )
                    }
                }
            }
        } else {
            if (folderPdfs.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.empty_title),
                    body = stringResource(R.string.empty_body),
                    showOpenButton = true,
                    onOpenPdf = onPickPdf
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = s.screenH,
                        end = s.screenH,
                        top = s.sm,
                        bottom = s.listBottom
                    ),
                    verticalArrangement = Arrangement.spacedBy(s.md)
                ) {
                    items(
                        items = folderPdfs,
                        key = { it.id },
                        contentType = { "pdf" }
                    ) { item ->
                        PdfRowCard(
                            item = item,
                            onClick = { onOpenPdf(item) },
                            onMore = onItemMore
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultsScreen(
    isSearching: Boolean,
    results: List<PdfItem>,
    onOpenPdf: (PdfItem) -> Unit,
    onItemMore: (PdfItem) -> Unit,
    onPickPdf: () -> Unit
) {
    val s = TeenaTheme.spacing

    when {
        isSearching && results.isEmpty() -> ShimmerLoadingList()
        results.isEmpty() -> EmptyState(
            title = stringResource(R.string.empty_search_title),
            body = stringResource(R.string.empty_search_body),
            showOpenButton = false,
            onOpenPdf = onPickPdf
        )
        else -> {
            Column(modifier = Modifier.fillMaxSize()) {
                if (isSearching) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = s.screenH,
                        end = s.screenH,
                        top = s.sm,
                        bottom = s.listBottom
                    ),
                    verticalArrangement = Arrangement.spacedBy(s.md)
                ) {
                    item(key = "search_header", contentType = "header") {
                        SectionHeader(
                            text = stringResource(R.string.search_results_header),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    items(
                        items = results,
                        key = { it.id },
                        contentType = { "pdf" }
                    ) { item ->
                        PdfRowCard(
                            item = item,
                            onClick = { onOpenPdf(item) },
                            onMore = onItemMore
                        )
                    }
                }
            }
        }
    }
}
