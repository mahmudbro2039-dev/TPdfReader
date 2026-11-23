package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ui.components.EmptyState
import com.example.ui.components.FabController
import com.example.ui.components.FilterChipsRow
import com.example.ui.components.FolderBreadcrumb
import com.example.ui.components.FolderRow
import com.example.ui.components.HeroContinueCard
import com.example.ui.components.PdfGridCard
import com.example.ui.components.PdfRowCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.ShimmerLoadingGrid
import com.example.ui.components.ShimmerLoadingList
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
    fabController: FabController,
    onOpenPdf: (PdfItem) -> Unit,
    onItemMore: (PdfItem) -> Unit,
    onPickPdf: () -> Unit
) {
    val s = TeenaTheme.spacing

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 }
            .collect { fabController.expanded = it }
    }

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
                verticalArrangement = Arrangement.spacedBy(s.md)
            ) {
                item(key = "hero", contentType = "hero") {
                    HeroContinueCard(
                        item = heroItem,
                        onClick = { onOpenPdf(heroItem) },
                        modifier = Modifier.animateItem()
                    )
                }
                if (remainingItems.isNotEmpty()) {
                    item(key = "recent_header", contentType = "header") {
                        SectionHeader(
                            text = stringResource(R.string.recent_history),
                            modifier = Modifier.animateItem()
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
                            modifier = Modifier.animateItem()
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
    fabController: FabController,
    onSelectStatus: (ReadingStatus?) -> Unit,
    onOpenPdf: (PdfItem) -> Unit,
    onItemMore: (PdfItem) -> Unit,
    onPickPdf: () -> Unit
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes

    LaunchedEffect(viewMode, listState, gridState) {
        if (viewMode == ViewMode.List) {
            snapshotFlow { listState.firstVisibleItemIndex == 0 }
                .collect { fabController.expanded = it }
        } else {
            snapshotFlow { gridState.firstVisibleItemIndex == 0 }
                .collect { fabController.expanded = it }
        }
    }

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
                            onMore = onItemMore,
                            modifier = Modifier.animateItem()
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
                            onMore = onItemMore,
                            modifier = Modifier.animateItem()
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
    fabController: FabController,
    onOpenFolder: (String) -> Unit,
    onCloseFolder: () -> Unit,
    onOpenPdf: (PdfItem) -> Unit,
    onItemMore: (PdfItem) -> Unit,
    onPickPdf: () -> Unit
) {
    val s = TeenaTheme.spacing

    BackHandler(enabled = selectedFolderName != null) {
        onCloseFolder()
    }

    LaunchedEffect(listState, selectedFolderName) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 }
            .collect { fabController.expanded = it }
    }

    if (isLoading) {
        ShimmerLoadingList()
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FolderBreadcrumb(
            currentFolder = selectedFolderName,
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
                        items = folders,
                        key = { it.name },
                        contentType = { "folder" }
                    ) { folder ->
                        FolderRow(
                            folder = folder,
                            onClick = { onOpenFolder(folder.name) },
                            modifier = Modifier.animateItem()
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
                            onMore = onItemMore,
                            modifier = Modifier.animateItem()
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
                        onMore = onItemMore,
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}
