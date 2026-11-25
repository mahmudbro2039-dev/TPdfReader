package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.example.R
import com.example.data.model.FolderKey
import com.example.data.search.parseSnippet
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
import com.example.data.model.ReadingStatus
import com.example.ui.model.FolderItem
import com.example.ui.model.PdfItem
import com.example.ui.model.ViewMode
import com.example.ui.theme.TeenaTheme
import com.example.ui.viewmodel.DeepSearchDocGroupUi

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
        heroItem == null && remainingItems.isEmpty() -> EmptyState(
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
                if (heroItem != null) {
                    item(key = "hero", contentType = "hero") {
                        HeroContinueCard(
                            item = heroItem,
                            onClick = { onOpenPdf(heroItem) }
                        )
                    }
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
    selectedFolderDisplayName: String? = null,
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
    val breadcrumbTitle = when {
        selectedFolderName == null -> null
        selectedFolderName == FolderKey.IMPORTED -> stringResource(R.string.folder_imported)
        !selectedFolderDisplayName.isNullOrBlank() -> selectedFolderDisplayName
        else -> FolderKey.displayName(selectedFolderName, stringResource(R.string.folder_imported))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FolderBreadcrumb(
            currentFolder = breadcrumbTitle,
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
                        key = { it.key },
                        contentType = { "folder" }
                    ) { folder ->
                        FolderCard(
                            folder = folder,
                            onClick = { onOpenFolder(folder.key) }
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
    deepSearch: Boolean,
    results: List<PdfItem>,
    deepSearchGroups: List<DeepSearchDocGroupUi>,
    indexDoneCount: Int,
    indexTotalCount: Int,
    onOpenPdf: (PdfItem) -> Unit,
    onOpenHit: (PdfItem, Int) -> Unit,
    onItemMore: (PdfItem) -> Unit,
    onPickPdf: () -> Unit
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val cs = MaterialTheme.colorScheme
    val showIncompleteBanner = deepSearch && indexTotalCount > 0 && indexDoneCount < indexTotalCount
    val hasAnyResults = results.isNotEmpty() || (deepSearch && deepSearchGroups.isNotEmpty())

    Column(modifier = Modifier.fillMaxSize()) {
        if (showIncompleteBanner) {
            Text(
                text = pluralStringResource(
                    R.plurals.search_index_incomplete,
                    indexTotalCount,
                    indexDoneCount,
                    indexTotalCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = s.screenH, vertical = s.xs)
                    .testTag("search_index_incomplete_banner")
            )
        }

        if (isSearching && !hasAnyResults) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            ShimmerLoadingList()
        } else if (!hasAnyResults) {
            val emptyTitle = if (deepSearch && indexTotalCount > 0 && indexDoneCount >= indexTotalCount) {
                stringResource(R.string.search_no_results)
            } else {
                stringResource(R.string.empty_search_title)
            }
            EmptyState(
                title = emptyTitle,
                body = stringResource(R.string.empty_search_body),
                showOpenButton = false,
                onOpenPdf = onPickPdf
            )
        } else {
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
                if (results.isNotEmpty()) {
                    item(key = "search_header", contentType = "header") {
                        SectionHeader(
                            text = stringResource(R.string.search_results_header),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    items(
                        items = results,
                        key = { "fn_${it.id}" },
                        contentType = { "pdf" }
                    ) { item ->
                        PdfRowCard(
                            item = item,
                            onClick = { onOpenPdf(item) },
                            onMore = onItemMore
                        )
                    }
                }

                if (deepSearch && deepSearchGroups.isNotEmpty()) {
                    item(key = "inside_pdfs_header", contentType = "header") {
                        SectionHeader(
                            text = stringResource(R.string.search_inside_pdfs_title),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    deepSearchGroups.forEach { group ->
                        item(key = "deep_doc_${group.item.id}", contentType = "pdf") {
                            PdfRowCard(
                                item = group.item,
                                onClick = { onOpenPdf(group.item) },
                                onMore = onItemMore
                            )
                        }
                        items(
                            items = group.hits.take(3),
                            key = { hit -> "hit_${group.item.id}_${hit.pageIndex}" },
                            contentType = { "deep_hit" }
                        ) { hit ->
                            DeepSearchHitRow(
                                pageNumber = hit.pageIndex + 1,
                                rawSnippet = hit.snippet,
                                onClick = { onOpenHit(group.item, hit.pageIndex) },
                                modifier = Modifier.padding(start = s.md)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeepSearchHitRow(
    pageNumber: Int,
    rawSnippet: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val cs = MaterialTheme.colorScheme
    val chunks = remember(rawSnippet) { parseSnippet(rawSnippet) }
    val annotated = remember(chunks, cs.primary) {
        buildAnnotatedString {
            for ((text, highlighted) in chunks) {
                if (highlighted) {
                    withStyle(SpanStyle(color = cs.primary, fontWeight = FontWeight.Bold)) {
                        append(text)
                    }
                } else {
                    append(text)
                }
            }
        }
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = cs.surface,
        border = BorderStroke(sz.hairline, TeenaTheme.colors.hairline),
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("search_hit_page_$pageNumber")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = s.md, vertical = s.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.search_result_page, pageNumber),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = cs.primary,
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.width(s.sm))
            Text(
                text = annotated,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
