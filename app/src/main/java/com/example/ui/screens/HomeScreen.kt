package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.AnnotationEntity
import com.example.data.local.BookmarkEntity
import com.example.data.local.PdfDocumentEntity
import com.example.data.model.LibrarySortOrder
import com.example.data.model.ReadingStatus
import com.example.ui.components.ReadingStatusBadge
import com.example.ui.components.formatFileSize
import com.example.ui.components.formatRelativeTime
import com.example.ui.components.parseHexColor
import com.example.ui.viewmodel.HomeTab
import com.example.ui.viewmodel.HomeUiState
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.VirtualFolderSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onPickPdfClick: () -> Unit,
    onOpenDocument: (uriString: String, pageIndex: Int?) -> Unit,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var activeActionDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showNotesAndBookmarksHub by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useNavigationRail) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HomeTab.entries.forEach { tab ->
                        val selected = state.selectedTab == tab
                        NavigationRailItem(
                            selected = selected,
                            onClick = { viewModel.selectTab(tab) },
                            icon = { TabIcon(tab = tab, selected = selected) },
                            label = { Text(tabLabel(tab)) },
                            modifier = Modifier.testTag("nav_rail_${tab.name.lowercase()}")
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    Column {
                        HomeTopAppBar(
                            state = state,
                            sortMenuExpanded = sortMenuExpanded,
                            onSortMenuDismiss = { sortMenuExpanded = false },
                            onSortMenuOpen = { sortMenuExpanded = true },
                            onSelectSort = {
                                viewModel.setSortOrder(it)
                                sortMenuExpanded = false
                            },
                            onToggleSearch = { viewModel.setSearchActive(!state.isSearchActive) },
                            onSearchQueryChange = viewModel::updateSearchQuery,
                            onToggleGridView = viewModel::toggleGridView,
                            onToggleIncognito = viewModel::toggleIncognitoMode,
                            onCycleTheme = viewModel::cycleAppTheme,
                            onOpenNotesHub = { showNotesAndBookmarksHub = true },
                            onOpenSettings = onOpenSettings
                        )

                        // Incognito active ribbon banner
                        AnimatedVisibility(visible = state.incognitoMode) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Policy,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.incognito_mode_active),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                },
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp
                        ) {
                            HomeTab.entries.forEach { tab ->
                                val selected = state.selectedTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(tab) },
                                    icon = { TabIcon(tab = tab, selected = selected) },
                                    label = { Text(tabLabel(tab)) },
                                    modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = onPickPdfClick,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.FolderOpen,
                                contentDescription = stringResource(R.string.action_open_pdf)
                            )
                        },
                        text = { Text(stringResource(R.string.action_open_pdf)) },
                        modifier = Modifier.testTag("open_pdf_fab")
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (state.selectedTab) {
                        HomeTab.RECENT -> RecentTabContent(
                            documents = state.recentDocuments,
                            bookmarksCount = state.bookmarks.size,
                            annotationsCount = state.annotations.size,
                            thumbnails = state.thumbnails,
                            onRequestThumbnail = viewModel::requestThumbnail,
                            onOpenDocument = { onOpenDocument(it.uriString, null) },
                            onOpenNotesHub = { showNotesAndBookmarksHub = true },
                            onMoreClick = { activeActionDoc = it }
                        )

                        HomeTab.LIBRARY -> LibraryTabContent(
                            documents = state.libraryDocuments,
                            totalDocumentCount = state.totalDocumentCount,
                            statusCounts = state.statusCounts,
                            thumbnails = state.thumbnails,
                            selectedStatus = state.selectedStatusFilter,
                            isGridView = state.isGridView,
                            onSelectStatus = viewModel::selectStatusFilter,
                            onRequestThumbnail = viewModel::requestThumbnail,
                            onOpenDocument = { onOpenDocument(it.uriString, null) },
                            onMoreClick = { activeActionDoc = it }
                        )

                        HomeTab.FOLDERS -> FoldersTabContent(
                            folderSummaries = state.folderSummaries,
                            selectedFolderName = state.selectedFolderName,
                            currentFolderDocuments = state.currentFolderDocuments,
                            thumbnails = state.thumbnails,
                            onSelectFolder = viewModel::selectFolder,
                            onRequestThumbnail = viewModel::requestThumbnail,
                            onOpenDocument = { onOpenDocument(it.uriString, null) },
                            onMoreClick = { activeActionDoc = it }
                        )
                    }
                }
            }
        }
    }

    activeActionDoc?.let { doc ->
        DocumentOptionsSheet(
            document = doc,
            availableFolders = state.folderSummaries.map { it.folderName },
            onDismiss = { activeActionDoc = null },
            onToggleFavorite = {
                viewModel.toggleFavorite(doc)
                activeActionDoc = null
            },
            onStatusSelected = { status ->
                viewModel.updateDocumentStatus(doc.uriString, status)
                activeActionDoc = null
            },
            onMoveToFolder = { folder ->
                viewModel.moveDocumentToFolder(doc.uriString, folder)
                activeActionDoc = null
            },
            onRemove = {
                viewModel.removeDocument(doc.uriString)
                activeActionDoc = null
            }
        )
    }

    if (showNotesAndBookmarksHub) {
        NotesAndBookmarksHubSheet(
            bookmarks = state.bookmarks,
            annotations = state.annotations,
            onDismiss = { showNotesAndBookmarksHub = false },
            onOpenPage = { uri, page ->
                showNotesAndBookmarksHub = false
                onOpenDocument(uri, page)
            },
            onDeleteBookmark = viewModel::deleteBookmark,
            onDeleteAnnotation = viewModel::deleteAnnotation
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopAppBar(
    state: HomeUiState,
    sortMenuExpanded: Boolean,
    onSortMenuDismiss: () -> Unit,
    onSortMenuOpen: () -> Unit,
    onSelectSort: (LibrarySortOrder) -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleGridView: () -> Unit,
    onToggleIncognito: () -> Unit,
    onCycleTheme: () -> Unit,
    onOpenNotesHub: () -> Unit,
    onOpenSettings: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        title = {
            if (state.isSearchActive) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text(stringResource(R.string.search_documents_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_search_input")
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(9.dp),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(
                onClick = onToggleSearch,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("home_search_button")
            ) {
                Icon(
                    imageVector = if (state.isSearchActive) Icons.Filled.Close else Icons.Filled.Search,
                    contentDescription = stringResource(R.string.action_search)
                )
            }

            if (!state.isSearchActive) {
                // Quick Theme Toggle (MJ PDF v3.1 persistent theme toggle button)
                IconButton(
                    onClick = onCycleTheme,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("home_theme_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DarkMode,
                        contentDescription = stringResource(R.string.settings_app_theme)
                    )
                }

                // Incognito Mode Toggle
                IconButton(
                    onClick = onToggleIncognito,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("home_incognito_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Policy,
                        contentDescription = stringResource(R.string.incognito_mode),
                        tint = if (state.incognitoMode) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                // My Notes, Highlights & Bookmarks Hub Button
                IconButton(
                    onClick = onOpenNotesHub,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("home_notes_hub_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bookmarks,
                        contentDescription = stringResource(R.string.my_notes_and_highlights)
                    )
                }

                if (state.selectedTab == HomeTab.LIBRARY) {
                    IconButton(
                        onClick = onToggleGridView,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("library_view_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (state.isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = stringResource(R.string.toggle_view_mode)
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = onSortMenuOpen,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("library_sort_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.sort_documents)
                        )
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = onSortMenuDismiss
                    ) {
                        LibrarySortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.label) },
                                trailingIcon = {
                                    if (state.sortOrder == order) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                    }
                                },
                                onClick = { onSelectSort(order) }
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("open_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.nav_settings)
                    )
                }
            }
        }
    )
}

@Composable
private fun RecentTabContent(
    documents: List<PdfDocumentEntity>,
    bookmarksCount: Int,
    annotationsCount: Int,
    thumbnails: Map<String, Bitmap>,
    onRequestThumbnail: (String) -> Unit,
    onOpenDocument: (PdfDocumentEntity) -> Unit,
    onOpenNotesHub: () -> Unit,
    onMoreClick: (PdfDocumentEntity) -> Unit
) {
    if (documents.isEmpty()) {
        EmptyStateBlock(
            icon = Icons.Outlined.History,
            title = stringResource(R.string.empty_recent_title),
            subtitle = stringResource(R.string.empty_recent_subtitle)
        )
        return
    }

    val heroDoc = documents.first()
    val otherDocs = documents.drop(1)

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "continue_reading_hero") {
            ContinueReadingHeroCard(
                document = heroDoc,
                thumbnail = thumbnails[heroDoc.uriString],
                onRequestThumbnail = { onRequestThumbnail(heroDoc.uriString) },
                onClick = { onOpenDocument(heroDoc) },
                onMoreClick = { onMoreClick(heroDoc) }
            )
        }

        // Quick Access Bar for My Notes, My Highlights & Saved Bookmarks (MJ PDF v3.0/v3.1)
        item(key = "notes_highlights_quick_bar") {
            Surface(
                onClick = onOpenNotesHub,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                        RoundedCornerShape(14.dp)
                    )
                    .testTag("my_notes_highlights_banner")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Highlight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.my_notes_and_highlights),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "$annotationsCount highlights & notes  •  $bookmarksCount bookmarks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (otherDocs.isNotEmpty()) {
            item(key = "recent_header") {
                Text(
                    text = "RECENT HISTORY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                )
            }
            items(otherDocs, key = { it.uriString }) { doc ->
                PdfListRowCard(
                    document = doc,
                    thumbnail = thumbnails[doc.uriString],
                    onRequestThumbnail = { onRequestThumbnail(doc.uriString) },
                    onClick = { onOpenDocument(doc) },
                    onMoreClick = { onMoreClick(doc) }
                )
            }
        }
    }
}

@Composable
private fun ContinueReadingHeroCard(
    document: PdfDocumentEntity,
    thumbnail: Bitmap?,
    onRequestThumbnail: () -> Unit,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    LaunchedEffect(document.uriString) { onRequestThumbnail() }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(18.dp)
            )
            .testTag("continue_reading_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PdfThumbnailBox(
                bitmap = thumbnail,
                modifier = Modifier
                    .width(82.dp)
                    .aspectRatio(0.71f)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CONTINUE READING",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = onMoreClick,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.document_options),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReadingStatusBadge(status = document.status)
                    Text(
                        text = "Page ${document.currentPage + 1} / ${document.pageCount.coerceAtLeast(1)} (${document.progressPercent}%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { document.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Resume in ${document.folderName}  •  ${formatRelativeTime(document.lastOpenedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryTabContent(
    documents: List<PdfDocumentEntity>,
    totalDocumentCount: Int,
    statusCounts: Map<ReadingStatus, Int>,
    thumbnails: Map<String, Bitmap>,
    selectedStatus: ReadingStatus?,
    isGridView: Boolean,
    onSelectStatus: (ReadingStatus?) -> Unit,
    onRequestThumbnail: (String) -> Unit,
    onOpenDocument: (PdfDocumentEntity) -> Unit,
    onMoreClick: (PdfDocumentEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item(key = "filter_all") {
                FilterChip(
                    selected = selectedStatus == null,
                    onClick = { onSelectStatus(null) },
                    label = { Text("${stringResource(R.string.status_all)} ($totalDocumentCount)") },
                    modifier = Modifier.testTag("filter_chip_all")
                )
            }
            items(ReadingStatus.entries, key = { it.name }) { status ->
                val count = statusCounts[status] ?: 0
                FilterChip(
                    selected = selectedStatus == status,
                    onClick = { onSelectStatus(if (selectedStatus == status) null else status) },
                    label = { Text("${status.label} ($count)") },
                    modifier = Modifier.testTag("filter_chip_${status.name.lowercase()}")
                )
            }
        }

        if (documents.isEmpty()) {
            EmptyStateBlock(
                icon = Icons.Outlined.LibraryBooks,
                title = stringResource(R.string.empty_library_title),
                subtitle = stringResource(R.string.empty_library_subtitle)
            )
            return
        }

        if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 152.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(documents, key = { it.uriString }) { doc ->
                    PdfGridCard(
                        document = doc,
                        thumbnail = thumbnails[doc.uriString],
                        onRequestThumbnail = { onRequestThumbnail(doc.uriString) },
                        onClick = { onOpenDocument(doc) },
                        onMoreClick = { onMoreClick(doc) }
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(documents, key = { it.uriString }) { doc ->
                    PdfListRowCard(
                        document = doc,
                        thumbnail = thumbnails[doc.uriString],
                        onRequestThumbnail = { onRequestThumbnail(doc.uriString) },
                        onClick = { onOpenDocument(doc) },
                        onMoreClick = { onMoreClick(doc) }
                    )
                }
            }
        }
    }
}

/**
 * MJ PDF v3.0/v3.1's 3rd main tab: Folders (File Manager with breadcrumb navigation).
 */
@Composable
private fun FoldersTabContent(
    folderSummaries: List<VirtualFolderSummary>,
    selectedFolderName: String?,
    currentFolderDocuments: List<PdfDocumentEntity>,
    thumbnails: Map<String, Bitmap>,
    onSelectFolder: (String?) -> Unit,
    onRequestThumbnail: (String) -> Unit,
    onOpenDocument: (PdfDocumentEntity) -> Unit,
    onMoreClick: (PdfDocumentEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Breadcrumb Navigation Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Storage",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selectedFolderName == null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .clickable { onSelectFolder(null) }
                        .testTag("breadcrumb_root")
                )
                if (selectedFolderName != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = selectedFolderName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (folderSummaries.isEmpty()) {
            EmptyStateBlock(
                icon = Icons.Outlined.Folder,
                title = stringResource(R.string.empty_folders_title),
                subtitle = stringResource(R.string.empty_folders_subtitle)
            )
            return
        }

        if (selectedFolderName == null) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(folderSummaries, key = { it.folderName }) { summary ->
                    Card(
                        onClick = { onSelectFolder(summary.folderName) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                                RoundedCornerShape(14.dp)
                            )
                            .testTag("folder_item_card")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = summary.folderName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${summary.documentCount} PDFs  •  ${formatFileSize(summary.totalSizeBytes)}  •  Updated ${formatRelativeTime(summary.latestModifiedAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(currentFolderDocuments, key = { it.uriString }) { doc ->
                    PdfListRowCard(
                        document = doc,
                        thumbnail = thumbnails[doc.uriString],
                        onRequestThumbnail = { onRequestThumbnail(doc.uriString) },
                        onClick = { onOpenDocument(doc) },
                        onMoreClick = { onMoreClick(doc) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfListRowCard(
    document: PdfDocumentEntity,
    thumbnail: Bitmap?,
    onRequestThumbnail: () -> Unit,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    LaunchedEffect(document.uriString) { onRequestThumbnail() }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                shape = RoundedCornerShape(14.dp)
            )
            .testTag("pdf_item_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PdfThumbnailBox(
                bitmap = thumbnail,
                modifier = Modifier
                    .width(54.dp)
                    .aspectRatio(0.71f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (document.isFavorite) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReadingStatusBadge(status = document.status)
                    Text(
                        text = "${document.currentPage + 1}/${document.pageCount.coerceAtLeast(1)} p (${document.progressPercent}%)  •  ${formatFileSize(document.fileSizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { document.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.document_options)
                )
            }
        }
    }
}

@Composable
private fun PdfGridCard(
    document: PdfDocumentEntity,
    thumbnail: Bitmap?,
    onRequestThumbnail: () -> Unit,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    LaunchedEffect(document.uriString) { onRequestThumbnail() }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                shape = RoundedCornerShape(14.dp)
            )
            .testTag("pdf_grid_card")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                PdfThumbnailBox(
                    bitmap = thumbnail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.74f)
                )
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(34.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.document_options),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = document.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ReadingStatusBadge(status = document.status)
                Text(
                    text = "${document.progressPercent}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesAndBookmarksHubSheet(
    bookmarks: List<BookmarkEntity>,
    annotations: List<AnnotationEntity>,
    onDismiss: () -> Unit,
    onOpenPage: (uriString: String, pageIndex: Int) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onDeleteAnnotation: (Long) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Text(
                text = stringResource(R.string.my_notes_and_highlights),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Highlights & Notes (${annotations.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Bookmarks (${bookmarks.size})") }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedTab == 0) {
                if (annotations.isEmpty()) {
                    Text(
                        text = stringResource(R.string.empty_annotations_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                    ) {
                        items(annotations, key = { it.id }) { ann ->
                            val swatch = parseHexColor(ann.colorHex)
                            Card(
                                onClick = { onOpenPage(ann.documentUri, ann.pageIndex) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(5.dp)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(swatch)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${ann.documentTitle}  •  Page ${ann.pageIndex + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (ann.quotedText.isNotBlank()) {
                                            Text(
                                                text = "“${ann.quotedText}”",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontStyle = FontStyle.Italic,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (ann.noteText.isNotBlank()) {
                                            Text(
                                                text = ann.noteText,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    IconButton(onClick = { onDeleteAnnotation(ann.id) }) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = stringResource(R.string.action_delete)
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
                        text = stringResource(R.string.empty_bookmarks_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                    ) {
                        items(bookmarks, key = { it.id }) { bm ->
                            Card(
                                onClick = { onOpenPage(bm.documentUri, bm.pageIndex) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
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
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${bm.documentTitle}  •  Page ${bm.pageIndex + 1}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { onDeleteBookmark(bm.id) }) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = stringResource(R.string.action_delete)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(22.dp))
        }
    }
}

@Composable
private fun PdfThumbnailBox(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), RoundedCornerShape(10.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        if (bitmap != null && !bitmap.isRecycled) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentOptionsSheet(
    document: PdfDocumentEntity,
    availableFolders: List<String>,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onStatusSelected: (ReadingStatus) -> Unit,
    onMoveToFolder: (String) -> Unit,
    onRemove: () -> Unit
) {
    val folderChoices = remember(availableFolders) {
        (availableFolders + listOf("Guides & Manuals", "Books & Research", "Downloads", "Work & Study")).distinct()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = document.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${document.pageCount.coerceAtLeast(1)} pages  •  ${formatFileSize(document.fileSizeBytes)}  •  ${document.folderName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.change_reading_status),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ReadingStatus.entries) { status ->
                    FilterChip(
                        selected = document.status == status,
                        onClick = { onStatusSelected(status) },
                        label = { Text(status.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Folder Category",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(folderChoices) { folder ->
                    FilterChip(
                        selected = document.folderName == folder,
                        onClick = { onMoveToFolder(folder) },
                        label = { Text(folder) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleFavorite)
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (document.isFavorite) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = stringResource(
                        if (document.isFavorite) R.string.unpin_favorite else R.string.pin_favorite
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRemove)
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.remove_from_library),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun EmptyStateBlock(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TabIcon(tab: HomeTab, selected: Boolean) {
    val icon = when (tab) {
        HomeTab.RECENT -> if (selected) Icons.Filled.History else Icons.Outlined.History
        HomeTab.LIBRARY -> if (selected) Icons.Filled.LibraryBooks else Icons.Outlined.LibraryBooks
        HomeTab.FOLDERS -> if (selected) Icons.Filled.Folder else Icons.Outlined.Folder
    }
    Icon(imageVector = icon, contentDescription = tabLabel(tab))
}

@Composable
private fun tabLabel(tab: HomeTab): String = when (tab) {
    HomeTab.RECENT -> stringResource(R.string.nav_recent)
    HomeTab.LIBRARY -> stringResource(R.string.nav_library)
    HomeTab.FOLDERS -> stringResource(R.string.nav_folders)
}
