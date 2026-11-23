package com.example.ui.viewmodel

import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AnnotationEntity
import com.example.data.local.BookmarkEntity
import com.example.data.local.PdfDocumentEntity
import com.example.data.local.PreferencesRepository
import com.example.data.local.UserPreferences
import com.example.data.pdf.DevicePdfScanner
import com.example.data.pdf.PdfScanProgress
import com.example.data.pdf.PdfTextExtractor
import com.example.data.repository.PdfLibraryRepository
import com.example.data.system.DeviceAccessStatus
import com.example.ui.components.ThumbnailCache
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val annotations: List<AnnotationUiItem> = emptyList(),
    val hasAllFilesAccess: Boolean = true,
    val isBatteryOptimizationIgnored: Boolean = true,
    val isScanningDevice: Boolean = false,
    val scannedPdfCount: Int = 0,
    val isAccessBannerDismissed: Boolean = false
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class AppViewModel(
    private val libraryRepository: PdfLibraryRepository,
    private val preferencesRepository: PreferencesRepository,
    private val pdfTextExtractor: PdfTextExtractor,
    private val devicePdfScanner: DevicePdfScanner? = null
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _submittedQuery = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val _selectedStatusFilter = MutableStateFlow<ReadingStatus?>(null)
    private val _selectedFolderName = MutableStateFlow<String?>(null)
    private val _accessStatus = MutableStateFlow(DeviceAccessStatus(hasAllFilesAccess = true, isBatteryOptimizationIgnored = true))
    private val _bannerDismissed = MutableStateFlow(false)
    private var initialScanTriggered = false
    private var scanJob: kotlinx.coroutines.Job? = null

    private val joinedTextCache = object : LruCache<String, String>(24) {}

    init {
        viewModelScope.launch {
            libraryRepository.ensureStarterGuideExists()
        }
    }

    private val docsStateFlow = libraryRepository.allDocuments
        .map { DocsLoadState(isLoaded = true, docs = it) }
        .onStart { emit(DocsLoadState(isLoaded = false, docs = emptyList())) }

    private val debouncedQuery: StateFlow<String> = merge(
        _query
            .map { it.trim() }
            .debounce { trimmed -> if (trimmed.isEmpty()) 0L else 280L },
        _submittedQuery.map { it.trim() }
    )
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = ""
        )

    private val deepSearchFlow = combine(
        debouncedQuery,
        preferencesRepository.preferencesFlow.map { it.deepSearch }.distinctUntilChanged(),
        libraryRepository.allDocuments
    ) { q, deepEnabled, docs ->
        Triple(q, deepEnabled, docs)
    }.flatMapLatest { (q, deepEnabled, docs) ->
        if (q.isBlank() || !deepEnabled) {
            flowOf(DeepSearchResult(query = q, isSearching = false, matchedUris = emptySet()))
        } else {
            flow {
                emit(DeepSearchResult(query = q, isSearching = true, matchedUris = emptySet()))
                val matches = withContext(Dispatchers.Default) {
                    val matched = HashSet<String>()
                    // Process in small concurrent batches for faster deep PDF search without memory spikes.
                    for (batch in docs.chunked(4)) {
                        val batchMatches = coroutineScope {
                            batch.map { doc ->
                                async {
                                    val cachedText = synchronized(joinedTextCache) {
                                        joinedTextCache.get(doc.uriString)
                                    } ?: run {
                                        val extracted = runCatching {
                                            pdfTextExtractor.extractPagesText(doc.uriString, doc.pageCount)
                                                .joinToString("\n")
                                        }.getOrDefault("")
                                        synchronized(joinedTextCache) {
                                            joinedTextCache.put(doc.uriString, extracted)
                                        }
                                        extracted
                                    }
                                    if (PdfTextExtractor.containsNormalized(cachedText, q)) {
                                        doc.uriString
                                    } else {
                                        null
                                    }
                                }
                            }.awaitAll()
                        }
                        batchMatches.forEach { uri -> if (uri != null) matched.add(uri) }
                    }
                    matched
                }
                emit(DeepSearchResult(query = q, isSearching = false, matchedUris = matches))
            }
        }
    }

    private val dataFlow = combine(
        docsStateFlow,
        libraryRepository.allBookmarks,
        libraryRepository.allAnnotations,
        preferencesRepository.preferencesFlow
    ) { docsState, bookmarks, annotations, prefs ->
        DataSnapshot(
            isLoaded = docsState.isLoaded,
            docs = docsState.docs,
            bookmarks = bookmarks,
            annotations = annotations,
            prefs = prefs
        )
    }

    // Library grouping, sorting, status counts & folders only recompute when DB/prefs/filters change,
    // NOT on every search keystroke.
    private val derivedLibraryFlow = combine(
        dataFlow,
        _selectedStatusFilter,
        _selectedFolderName
    ) { data, statusFilter, folderName ->
        val prefs = data.prefs
        val sortOrder = SortOrder.fromName(prefs.sortOrderName)
        val viewMode = if (prefs.isLibraryGridView) ViewMode.Grid else ViewMode.List
        val themeMode = prefs.appThemeMode.toThemeMode()

        val allItems = data.docs.map { it.toPdfItem() }
        val statusCounts = ReadingStatus.entries.associateWith { status ->
            allItems.count { it.status == status }
        }

        val byLastOpened = allItems.sortedByDescending { it.lastOpenedAt }
        val heroItem = byLastOpened.firstOrNull()
        val remainingRecent = if (heroItem != null) {
            sortItems(allItems.filter { it.id != heroItem.id }, sortOrder)
        } else {
            emptyList()
        }

        val filteredLibrary = if (statusFilter != null) {
            allItems.filter { it.status == statusFilter }
        } else {
            allItems
        }
        val libraryItems = sortItems(filteredLibrary, sortOrder)

        val folderItems = allItems
            .groupBy { normalizeFolderName(it.folderName) }
            .map { (name, itemsInFolder) ->
                FolderItem(
                    name = name,
                    pdfCount = itemsInFolder.size,
                    totalSizeBytes = itemsInFolder.sumOf { it.sizeBytes },
                    lastUpdatedAt = itemsInFolder.maxOfOrNull { it.lastOpenedAt } ?: 0L,
                    previewUris = itemsInFolder
                        .sortedByDescending { it.lastOpenedAt }
                        .take(3)
                        .map { it.uri }
                )
            }
            .sortedByDescending { it.lastUpdatedAt }

        val currentFolderItems = if (folderName != null) {
            sortItems(
                allItems.filter { normalizeFolderName(it.folderName).equals(folderName, ignoreCase = true) },
                sortOrder
            )
        } else {
            emptyList()
        }

        DerivedLibrarySnapshot(
            themeMode = themeMode,
            viewMode = viewMode,
            sortOrder = sortOrder,
            deepSearch = prefs.deepSearch,
            isLoading = !data.isLoaded,
            allItems = allItems,
            statusFilter = statusFilter,
            folderName = folderName,
            heroItem = heroItem,
            recentRemainingItems = remainingRecent,
            libraryItems = libraryItems,
            folderItems = folderItems,
            currentFolderItems = currentFolderItems,
            statusCounts = statusCounts,
            bookmarks = data.bookmarks.map { it.toUiItem() },
            annotations = data.annotations.map { it.toUiItem() }
        )
    }.flowOn(Dispatchers.Default)

    private val searchInputsFlow = combine(
        _query,
        debouncedQuery,
        deepSearchFlow
    ) { rawQuery, activeQuery, deepResult ->
        SearchSnapshot(rawQuery, activeQuery, deepResult)
    }

    private val scanProgressFlow = devicePdfScanner?.progress ?: MutableStateFlow(PdfScanProgress())

    private val systemAccessFlow = combine(
        _accessStatus,
        scanProgressFlow,
        _bannerDismissed
    ) { access, scan, dismissed ->
        SystemAccessSnapshot(access, scan, dismissed)
    }

    val uiState: StateFlow<AppUiState> = combine(
        derivedLibraryFlow,
        searchInputsFlow,
        systemAccessFlow
    ) { lib, search, sys ->
        val activeSearch = search.debouncedQuery
        val deepResult = search.deepResult
        val searchResults = if (activeSearch.isBlank()) {
            emptyList()
        } else {
            val matchedByText = if (lib.deepSearch && deepResult.query == activeSearch) {
                deepResult.matchedUris
            } else {
                emptySet()
            }
            sortItems(
                lib.allItems.filter { item ->
                    PdfTextExtractor.containsNormalized(item.title, activeSearch) ||
                        PdfTextExtractor.containsNormalized(item.folderName, activeSearch) ||
                        matchedByText.contains(item.id)
                },
                lib.sortOrder
            )
        }

        val rawTrimmed = search.rawQuery.trim()
        val waitingForDebounce = rawTrimmed.isNotEmpty() && rawTrimmed != activeSearch
        val waitingForDeep = lib.deepSearch && activeSearch.isNotEmpty() &&
            (deepResult.isSearching || deepResult.query != activeSearch)

        AppUiState(
            themeMode = lib.themeMode,
            viewMode = lib.viewMode,
            sort = lib.sortOrder,
            deepSearch = lib.deepSearch,
            query = search.rawQuery,
            selectedStatusFilter = lib.statusFilter,
            selectedFolderName = lib.folderName,
            isLoading = lib.isLoading,
            heroItem = lib.heroItem,
            recentRemainingItems = lib.recentRemainingItems,
            libraryItems = lib.libraryItems,
            folderItems = lib.folderItems,
            currentFolderItems = lib.currentFolderItems,
            statusCounts = lib.statusCounts,
            totalCount = lib.allItems.size,
            searchResults = searchResults,
            isSearching = waitingForDebounce || waitingForDeep,
            bookmarks = lib.bookmarks,
            annotations = lib.annotations,
            hasAllFilesAccess = sys.access.hasAllFilesAccess,
            isBatteryOptimizationIgnored = sys.access.isBatteryOptimizationIgnored,
            isScanningDevice = sys.scan.isScanning,
            scannedPdfCount = sys.scan.totalFoundCount,
            isAccessBannerDismissed = sys.dismissed
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AppUiState()
        )

    private fun normalizeFolderName(raw: String): String = raw.ifBlank { "Storage" }

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

    fun onPermissionsRefreshed(status: DeviceAccessStatus) {
        val previouslyHadFiles = _accessStatus.value.hasAllFilesAccess
        _accessStatus.value = status
        if (!initialScanTriggered || (!previouslyHadFiles && status.hasAllFilesAccess)) {
            initialScanTriggered = true
            triggerDeviceScan()
        }
    }

    fun triggerDeviceScan() {
        val scanner = devicePdfScanner ?: return
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            scanner.scanDevicePdfs()
        }
    }

    fun dismissAccessBanner() {
        _bannerDismissed.value = true
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun onSubmitSearch(submittedQuery: String) {
        _query.value = submittedQuery
        _submittedQuery.tryEmit(submittedQuery)
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
            synchronized(joinedTextCache) { joinedTextCache.remove(entity.uriString) }
            pdfTextExtractor.invalidateCache(entity.uriString)
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
        synchronized(joinedTextCache) { joinedTextCache.remove(uri) }
        pdfTextExtractor.invalidateCache(uri)
        ThumbnailCache.evict(uri)
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

    private data class DocsLoadState(
        val isLoaded: Boolean,
        val docs: List<PdfDocumentEntity>
    )

    private data class DeepSearchResult(
        val query: String = "",
        val isSearching: Boolean = false,
        val matchedUris: Set<String> = emptySet()
    )

    private data class DataSnapshot(
        val isLoaded: Boolean,
        val docs: List<PdfDocumentEntity>,
        val bookmarks: List<BookmarkEntity>,
        val annotations: List<AnnotationEntity>,
        val prefs: UserPreferences
    )

    private data class DerivedLibrarySnapshot(
        val themeMode: ThemeMode,
        val viewMode: ViewMode,
        val sortOrder: SortOrder,
        val deepSearch: Boolean,
        val isLoading: Boolean,
        val allItems: List<PdfItem>,
        val statusFilter: ReadingStatus?,
        val folderName: String?,
        val heroItem: PdfItem?,
        val recentRemainingItems: List<PdfItem>,
        val libraryItems: List<PdfItem>,
        val folderItems: List<FolderItem>,
        val currentFolderItems: List<PdfItem>,
        val statusCounts: Map<ReadingStatus, Int>,
        val bookmarks: List<BookmarkUiItem>,
        val annotations: List<AnnotationUiItem>
    )

    private data class SearchSnapshot(
        val rawQuery: String,
        val debouncedQuery: String,
        val deepResult: DeepSearchResult
    )

    private data class SystemAccessSnapshot(
        val access: DeviceAccessStatus,
        val scan: PdfScanProgress,
        val dismissed: Boolean
    )

    companion object {
        fun factory(
            libraryRepository: PdfLibraryRepository,
            preferencesRepository: PreferencesRepository,
            pdfTextExtractor: PdfTextExtractor,
            devicePdfScanner: DevicePdfScanner? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppViewModel(
                    libraryRepository = libraryRepository,
                    preferencesRepository = preferencesRepository,
                    pdfTextExtractor = pdfTextExtractor,
                    devicePdfScanner = devicePdfScanner
                ) as T
            }
        }
    }
}
