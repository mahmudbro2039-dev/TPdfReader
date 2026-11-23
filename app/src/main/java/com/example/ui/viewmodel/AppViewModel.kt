package com.example.ui.viewmodel

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AnnotationEntity
import com.example.data.local.BookmarkEntity
import com.example.data.local.PdfDocumentEntity
import com.example.data.local.PreferencesRepository
import com.example.data.local.UserPreferences
import com.example.data.pdf.PdfTextExtractor
import com.example.data.repository.PdfLibraryRepository
import com.example.ui.model.AnnotationUiItem
import com.example.ui.model.BookmarkUiItem
import com.example.ui.model.FolderItem
import com.example.ui.model.PdfItem
import com.example.ui.model.SortOrder
import com.example.ui.model.ViewMode
import com.example.ui.model.progress
import com.example.ui.model.toAppThemeMode
import com.example.ui.model.toDataStatus
import com.example.ui.model.toPdfItem
import com.example.ui.model.toThemeMode
import com.example.ui.model.toUiItem
import com.example.ui.theme.ReadingStatus
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

@Immutable
data class AppUiState(
    val themeMode: ThemeMode = ThemeMode.System,
    val viewMode: ViewMode = ViewMode.Grid,
    val sort: SortOrder = SortOrder.LastOpened,
    val deepSearch: Boolean = false,
    val query: String = "",
    val selectedStatusFilter: ReadingStatus? = null,
    val selectedFolderName: String? = null,
    val isLoading: Boolean = true,
    val heroItem: PdfItem? = null,
    val recentRemainingItems: List<PdfItem> = emptyList(),
    val libraryItems: List<PdfItem> = emptyList(),
    val folderItems: List<FolderItem> = emptyList(),
    val currentFolderItems: List<PdfItem> = emptyList(),
    val statusCounts: Map<ReadingStatus, Int> = emptyMap(),
    val totalCount: Int = 0,
    val searchResults: List<PdfItem> = emptyList(),
    val isSearching: Boolean = false,
    val bookmarks: List<BookmarkUiItem> = emptyList(),
    val annotations: List<AnnotationUiItem> = emptyList()
)

@OptIn(FlowPreview::class)
class AppViewModel(
    private val libraryRepository: PdfLibraryRepository,
    private val preferencesRepository: PreferencesRepository,
    private val pdfTextExtractor: PdfTextExtractor
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _selectedStatusFilter = MutableStateFlow<ReadingStatus?>(null)
    private val _selectedFolderName = MutableStateFlow<String?>(null)
    private val _hasLoadedDocs = MutableStateFlow(false)
    private val _deepSearchMatches = MutableStateFlow<Set<String>>(emptySet())
    private val _isDeepSearching = MutableStateFlow(false)
    private val extractedTextCache = ConcurrentHashMap<String, String>()

    init {
        viewModelScope.launch {
            libraryRepository.ensureStarterGuideExists()
        }
        observeDeepSearch()
    }

    private fun observeDeepSearch() {
        val debouncedQuery = _query
            .map { it.trim() }
            .debounce(300L)
            .distinctUntilChanged()

        viewModelScope.launch {
            combine(
                debouncedQuery,
                preferencesRepository.preferencesFlow.map { it.deepSearch }.distinctUntilChanged(),
                libraryRepository.allDocuments
            ) { q, deepEnabled, docs ->
                Triple(q, deepEnabled, docs)
            }.collect { (q, deepEnabled, docs) ->
                if (q.isBlank() || !deepEnabled) {
                    _deepSearchMatches.value = emptySet()
                    _isDeepSearching.value = false
                    return@collect
                }
                _isDeepSearching.value = true
                val matches = withContext(Dispatchers.Default) {
                    docs.mapNotNull { doc ->
                        val cachedText = extractedTextCache[doc.uriString] ?: run {
                            val extracted = runCatching {
                                pdfTextExtractor.extractPagesText(doc.uriString, doc.pageCount)
                                    .joinToString("\n")
                            }.getOrDefault("")
                            extractedTextCache[doc.uriString] = extracted
                            extracted
                        }
                        if (cachedText.contains(q, ignoreCase = true)) doc.uriString else null
                    }.toSet()
                }
                _deepSearchMatches.value = matches
                _isDeepSearching.value = false
            }
        }
    }

    private val dataFlow = combine(
        libraryRepository.allDocuments.map { docs ->
            _hasLoadedDocs.value = true
            docs
        },
        libraryRepository.allBookmarks,
        libraryRepository.allAnnotations,
        preferencesRepository.preferencesFlow
    ) { docs, bookmarks, annotations, prefs ->
        DataSnapshot(docs, bookmarks, annotations, prefs)
    }

    private val uiFiltersFlow = combine(
        _query,
        _query.map { it.trim() }.debounce(300L).distinctUntilChanged(),
        _selectedStatusFilter,
        _selectedFolderName,
        _deepSearchMatches
    ) { rawQuery, debouncedQuery, statusFilter, folderName, deepMatches ->
        FilterSnapshot(rawQuery, debouncedQuery, statusFilter, folderName, deepMatches)
    }

    val uiState: StateFlow<AppUiState> = combine(
        dataFlow,
        uiFiltersFlow,
        _hasLoadedDocs,
        _isDeepSearching
    ) { data, filters, loaded, isDeepSearching ->
        val prefs = data.prefs
        val sortOrder = SortOrder.fromName(prefs.sortOrderName)
        val viewMode = if (prefs.isLibraryGridView) ViewMode.Grid else ViewMode.List
        val themeMode = prefs.appThemeMode.toThemeMode()

        val allItems = data.docs.map { it.toPdfItem() }
        val statusCounts = ReadingStatus.entries.associateWith { status ->
            allItems.count { it.status == status }
        }

        // Recent: Hero = most recently opened, remaining sorted by user sort order
        val byLastOpened = allItems.sortedByDescending { it.lastOpenedAt }
        val heroItem = byLastOpened.firstOrNull()
        val remainingRecent = if (heroItem != null) {
            sortItems(allItems.filter { it.id != heroItem.id }, sortOrder)
        } else {
            emptyList()
        }

        // Library: filter by status, pinned first, then selected sort order
        val filteredLibrary = if (filters.statusFilter != null) {
            allItems.filter { it.status == filters.statusFilter }
        } else {
            allItems
        }
        val libraryItems = sortItems(filteredLibrary, sortOrder)

        // Folders
        val folderItems = allItems
            .groupBy { it.folderName.ifBlank { "Storage" } }
            .map { (name, itemsInFolder) ->
                FolderItem(
                    name = name,
                    pdfCount = itemsInFolder.size,
                    totalSizeBytes = itemsInFolder.sumOf { it.sizeBytes },
                    lastUpdatedAt = itemsInFolder.maxOfOrNull { it.lastOpenedAt } ?: 0L
                )
            }
            .sortedByDescending { it.lastUpdatedAt }

        val currentFolderItems = if (filters.folderName != null) {
            sortItems(
                allItems.filter { it.folderName.equals(filters.folderName, ignoreCase = true) },
                sortOrder
            )
        } else {
            emptyList()
        }

        // Debounced Search results (title/folder + optional deep PDF text matches)
        val activeSearch = filters.debouncedQuery
        val searchResults = if (activeSearch.isBlank()) {
            emptyList()
        } else {
            sortItems(
                allItems.filter { item ->
                    item.title.contains(activeSearch, ignoreCase = true) ||
                        item.folderName.contains(activeSearch, ignoreCase = true) ||
                        (prefs.deepSearch && filters.deepMatches.contains(item.id))
                },
                sortOrder
            )
        }

        AppUiState(
            themeMode = themeMode,
            viewMode = viewMode,
            sort = sortOrder,
            deepSearch = prefs.deepSearch,
            query = filters.rawQuery,
            selectedStatusFilter = filters.statusFilter,
            selectedFolderName = filters.folderName,
            isLoading = !loaded,
            heroItem = heroItem,
            recentRemainingItems = remainingRecent,
            libraryItems = libraryItems,
            folderItems = folderItems,
            currentFolderItems = currentFolderItems,
            statusCounts = statusCounts,
            totalCount = allItems.size,
            searchResults = searchResults,
            isSearching = isDeepSearching || (filters.rawQuery.trim() != filters.debouncedQuery),
            bookmarks = data.bookmarks.map { it.toUiItem() },
            annotations = data.annotations.map { it.toUiItem() }
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AppUiState()
        )

    private fun sortItems(items: List<PdfItem>, sortOrder: SortOrder): List<PdfItem> {
        val comparator = when (sortOrder) {
            SortOrder.LastOpened -> compareByDescending<PdfItem> { it.isPinned }
                .thenByDescending { it.lastOpenedAt }
            SortOrder.Name -> compareByDescending<PdfItem> { it.isPinned }
                .thenBy { it.title.lowercase() }
            SortOrder.DateAdded -> compareByDescending<PdfItem> { it.isPinned }
                .thenByDescending { it.addedAt }
            SortOrder.Size -> compareByDescending<PdfItem> { it.isPinned }
                .thenByDescending { it.sizeBytes }
            SortOrder.Progress -> compareByDescending<PdfItem> { it.isPinned }
                .thenByDescending { it.progress }
        }
        return items.sortedWith(comparator)
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun onSubmitSearch(submittedQuery: String) {
        _query.value = submittedQuery
    }

    fun toggleDarkMode(isCurrentlyDark: Boolean) {
        viewModelScope.launch {
            val target = if (isCurrentlyDark) ThemeMode.Light else ThemeMode.Dark
            preferencesRepository.setAppThemeMode(target.toAppThemeMode())
        }
    }

    fun toggleDeepSearch() {
        viewModelScope.launch {
            preferencesRepository.setDeepSearch(!uiState.value.deepSearch)
        }
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch {
            preferencesRepository.setSortOrderName(order.name)
        }
    }

    fun toggleViewMode() {
        viewModelScope.launch {
            val nextIsGrid = uiState.value.viewMode == ViewMode.List
            preferencesRepository.setLibraryGridView(nextIsGrid)
        }
    }

    fun selectStatusFilter(status: ReadingStatus?) {
        _selectedStatusFilter.value = status
    }

    fun openFolder(folderName: String) {
        _selectedFolderName.value = folderName
    }

    fun closeFolder() {
        _selectedFolderName.value = null
    }

    fun importAndOpenPdf(uri: Uri, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val activeFolder = _selectedFolderName.value
            val entity = libraryRepository.registerPickedDocument(uri, activeFolder)
            onReady(entity.uriString)
        }
    }

    fun togglePin(item: PdfItem) {
        viewModelScope.launch {
            libraryRepository.toggleFavorite(item.uri, item.isPinned)
        }
    }

    fun updateStatus(uri: String, status: ReadingStatus) {
        viewModelScope.launch {
            libraryRepository.updateReadingStatus(uri, status.toDataStatus())
        }
    }

    fun moveToFolder(uri: String, folderName: String) {
        viewModelScope.launch {
            libraryRepository.moveDocumentToFolder(uri, folderName)
        }
    }

    fun removeDocument(uri: String) {
        viewModelScope.launch {
            libraryRepository.removeDocument(uri)
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            libraryRepository.deleteBookmark(id)
        }
    }

    fun deleteAnnotation(id: Long) {
        viewModelScope.launch {
            libraryRepository.deleteAnnotation(id)
        }
    }

    private data class DataSnapshot(
        val docs: List<PdfDocumentEntity>,
        val bookmarks: List<BookmarkEntity>,
        val annotations: List<AnnotationEntity>,
        val prefs: UserPreferences
    )

    private data class FilterSnapshot(
        val rawQuery: String,
        val debouncedQuery: String,
        val statusFilter: ReadingStatus?,
        val folderName: String?,
        val deepMatches: Set<String>
    )

    companion object {
        fun factory(
            libraryRepository: PdfLibraryRepository,
            preferencesRepository: PreferencesRepository,
            pdfTextExtractor: PdfTextExtractor
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppViewModel(
                    libraryRepository = libraryRepository,
                    preferencesRepository = preferencesRepository,
                    pdfTextExtractor = pdfTextExtractor
                ) as T
            }
        }
    }
}
