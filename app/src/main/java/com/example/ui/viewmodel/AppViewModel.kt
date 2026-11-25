package com.example.ui.viewmodel

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AnnotationEntity
import com.example.data.local.BookmarkEntity
import com.example.data.local.LibraryPrefs
import com.example.data.local.PdfDocumentEntity
import com.example.data.local.PreferencesRepository
import com.example.data.local.UserPreferences
import com.example.data.local.toLibraryPrefs
import com.example.data.model.FolderKey
import com.example.data.model.ReadingStatus
import com.example.data.ocr.OcrRunner
import com.example.data.ocr.OcrState
import com.example.data.pdf.DevicePdfScanner
import com.example.data.pdf.PdfScanProgress
import com.example.data.repository.PdfLibraryRepository
import com.example.data.search.IndexHit
import com.example.data.search.SearchIndexRepository
import com.example.data.search.db.IndexStatus
import com.example.data.search.db.IndexedDocEntity
import com.example.data.system.DeviceAccessStatus
import com.example.ui.components.ThumbnailCache
import com.example.ui.model.AnnotationUiItem
import com.example.ui.model.BookmarkUiItem
import com.example.ui.model.FolderItem
import com.example.ui.model.PdfItem
import com.example.ui.model.SortOrder
import com.example.ui.model.ViewMode
import com.example.ui.model.toAppThemeMode
import com.example.ui.model.toPdfItem
import com.example.ui.model.toThemeMode
import com.example.ui.model.toUiItem
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
import java.text.Collator

@Immutable
data class DeepSearchHitUi(
    val pageIndex: Int,
    val snippet: String
)

@Immutable
data class DeepSearchDocGroupUi(
    val item: PdfItem,
    val hits: List<DeepSearchHitUi>
)

@Immutable
data class AppUiState(
    val themeMode: ThemeMode = ThemeMode.System,
    val viewMode: ViewMode = ViewMode.Grid,
    val sort: SortOrder = SortOrder.LastOpened,
    val deepSearch: Boolean = false,
    val query: String = "",
    val selectedStatusFilter: ReadingStatus? = null,
    val selectedFolderName: String? = null,
    val selectedFolderDisplayName: String? = null,
    val isLoading: Boolean = true,
    val heroItem: PdfItem? = null,
    val recentRemainingItems: List<PdfItem> = emptyList(),
    val libraryItems: List<PdfItem> = emptyList(),
    val folderItems: List<FolderItem> = emptyList(),
    val currentFolderItems: List<PdfItem> = emptyList(),
    val statusCounts: Map<ReadingStatus, Int> = emptyMap(),
    val totalCount: Int = 0,
    val searchResults: List<PdfItem> = emptyList(),
    val deepSearchGroups: List<DeepSearchDocGroupUi> = emptyList(),
    val indexedDocsByUri: Map<String, IndexedDocEntity> = emptyMap(),
    val indexDoneCount: Int = 0,
    val indexTotalCount: Int = 0,
    val isSearching: Boolean = false,
    val bookmarks: List<BookmarkUiItem> = emptyList(),
    val annotations: List<AnnotationUiItem> = emptyList(),
    val hasAllFilesAccess: Boolean = true,
    val isScanningDevice: Boolean = false,
    val scannedPdfCount: Int = 0,
    val isAccessBannerDismissed: Boolean = false
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class AppViewModel(
    private val libraryRepository: PdfLibraryRepository,
    private val preferencesRepository: PreferencesRepository,
    private val searchIndexRepository: SearchIndexRepository? = null,
    private val devicePdfScanner: DevicePdfScanner? = null,
    private val ocrRunner: OcrRunner? = null,
    initialPreferences: UserPreferences = UserPreferences()
) : ViewModel() {

    val ocrState: StateFlow<OcrState> = ocrRunner?.state ?: MutableStateFlow(OcrState.Idle)

    private val _query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _query.asStateFlow()
    private val _submittedQuery = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val _selectedStatusFilter = MutableStateFlow<ReadingStatus?>(null)
    private val _selectedFolderName = MutableStateFlow<String?>(null)
    private val _accessStatus = MutableStateFlow(DeviceAccessStatus(hasAllFilesAccess = true))
    private val _bannerDismissed = MutableStateFlow(false)
    private val _permissionDeniedHintEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val permissionDeniedHintEvent: SharedFlow<Unit> = _permissionDeniedHintEvent.asSharedFlow()
    private var initialScanTriggered = false
    private var scanJob: kotlinx.coroutines.Job? = null

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = initialPreferences
        )

    private val libraryPrefsFlow: Flow<LibraryPrefs> = preferencesRepository.preferencesFlow
        .map { it.toLibraryPrefs() }
        .distinctUntilChanged()

    private val themeModeFlow: Flow<ThemeMode> = preferencesRepository.preferencesFlow
        .map { it.appThemeMode.toThemeMode() }
        .distinctUntilChanged()

    init {
        viewModelScope.launch {
            if (!preferencesRepository.preferencesFlow.first().lastOpenedRepairDone) {
                libraryRepository.runOneTimeLastOpenedRepair()
                preferencesRepository.setLastOpenedRepairDone(true)
            }
            libraryRepository.ensureStarterGuideExists()
            libraryRepository.allDocuments.first()
            libraryRepository.refreshAvailability()
        }
    }

    private val docsStateFlow = libraryRepository.allDocuments
        .map { DocsLoadState(isLoaded = true, docs = it) }
        .onStart { emit(DocsLoadState(isLoaded = false, docs = emptyList())) }

    private val deepSearchDebouncedQuery: StateFlow<String> = merge(
        _query
            .map { it.trim() }
            .debounce { trimmed -> if (trimmed.isEmpty()) 0L else 250L },
        _submittedQuery.map { it.trim() }
    )
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = ""
        )

    private val indexedDocsFlow: Flow<List<IndexedDocEntity>> =
        searchIndexRepository?.observeAllDocs() ?: flowOf(emptyList())

    private val deepSearchFlow = combine(
        deepSearchDebouncedQuery,
        preferencesRepository.preferencesFlow.map { it.deepSearch }.distinctUntilChanged()
    ) { q, deepEnabled ->
        q to deepEnabled
    }.flatMapLatest { (q, deepEnabled) ->
        if (q.isBlank() || !deepEnabled || searchIndexRepository == null) {
            flowOf(DeepSearchResult(query = q, isSearching = false, hits = emptyList()))
        } else {
            flow {
                emit(DeepSearchResult(query = q, isSearching = true, hits = emptyList()))
                val hits = searchIndexRepository.searchAll(q)
                emit(DeepSearchResult(query = q, isSearching = false, hits = hits))
            }.flowOn(Dispatchers.Default)
        }
    }

    private val dataFlow = combine(
        docsStateFlow,
        libraryRepository.allBookmarks,
        libraryRepository.allAnnotations,
        libraryPrefsFlow,
        libraryRepository.missingUris
    ) { docsState, bookmarks, annotations, prefs, missingUris ->
        DataSnapshot(
            isLoaded = docsState.isLoaded,
            docs = docsState.docs,
            bookmarks = bookmarks,
            annotations = annotations,
            prefs = prefs,
            missingUris = missingUris
        )
    }

    // Library grouping, sorting, status counts & folders only recompute when DB/LibraryPrefs/filters change,
    // NOT on every search keystroke or reader/text-mode preference edit.
    private val derivedLibraryFlow = combine(
        dataFlow,
        _selectedStatusFilter,
        _selectedFolderName
    ) { data, statusFilter, folderName ->
        val prefs = data.prefs
        val sortOrder = SortOrder.fromName(prefs.sortOrderName)
        val viewMode = if (prefs.isLibraryGridView) ViewMode.Grid else ViewMode.List

        val rawItems = data.docs.map { it.toPdfItem(isMissing = it.uriString in data.missingUris) }
        val folderKeys = rawItems.map { FolderKey.canonical(it.folderName) }
        val displayNames = FolderKey.displayNames(folderKeys)
        val allItems = rawItems.map { item ->
            val key = FolderKey.canonical(item.folderName)
            val resolvedName = displayNames[key] ?: FolderKey.displayName(key)
            if (resolvedName == item.folderDisplayName) item else item.copy(folderDisplayName = resolvedName)
        }
        val statusCounts = ReadingStatus.entries.associateWith { status ->
            allItems.count { it.status == status }
        }

        val openedItems = allItems
            .filter { it.hasBeenOpened }
            .sortedByDescending { it.lastOpenedAt }
        val heroItem = openedItems.firstOrNull {
            it.status == ReadingStatus.READING && it.progress < 1f && !it.isMissing
        }
        val remainingRecent = if (heroItem != null) {
            openedItems.filter { it.id != heroItem.id }
        } else {
            openedItems
        }

        val filteredLibrary = if (statusFilter != null) {
            allItems.filter { it.status == statusFilter }
        } else {
            allItems
        }
        val libraryItems = sortItems(filteredLibrary, sortOrder)

        val folderCollator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        val folderItems = allItems
            .groupBy { FolderKey.canonical(it.folderName) }
            .map { (key, itemsInFolder) ->
                FolderItem(
                    key = key,
                    name = displayNames[key] ?: FolderKey.displayName(key),
                    pdfCount = itemsInFolder.size,
                    totalSizeBytes = itemsInFolder.sumOf { it.sizeBytes },
                    lastUpdatedAt = itemsInFolder.maxOfOrNull { maxOf(it.lastOpenedAt, it.addedAt) } ?: 0L,
                    previewUris = itemsInFolder
                        .sortedWith(
                            compareByDescending<PdfItem> { it.lastOpenedAt }
                                .thenByDescending { it.addedAt }
                        )
                        .take(3)
                        .map { it.uri }
                )
            }
            .sortedWith { a, b -> folderCollator.compare(a.name, b.name) }

        val canonicalSelectedFolder = folderName?.let { FolderKey.canonical(it) }
        val selectedFolderDisplayName = canonicalSelectedFolder?.let {
            displayNames[it] ?: FolderKey.displayName(it)
        }
        val currentFolderItems = if (canonicalSelectedFolder != null) {
            sortItems(
                allItems.filter { FolderKey.canonical(it.folderName) == canonicalSelectedFolder },
                sortOrder
            )
        } else {
            emptyList()
        }

        DerivedLibrarySnapshot(
            viewMode = viewMode,
            sortOrder = sortOrder,
            deepSearch = prefs.deepSearch,
            isLoading = !data.isLoaded,
            allItems = allItems,
            statusFilter = statusFilter,
            folderName = canonicalSelectedFolder,
            folderDisplayName = selectedFolderDisplayName,
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
        deepSearchDebouncedQuery,
        deepSearchFlow,
        indexedDocsFlow
    ) { rawQuery, deepQuery, deepResult, indexedDocs ->
        SearchSnapshot(rawQuery, deepQuery, deepResult, indexedDocs)
    }

    private val scanProgressFlow = devicePdfScanner?.progress ?: MutableStateFlow(PdfScanProgress())

    private val systemAccessFlow = combine(
        _accessStatus,
        scanProgressFlow,
        _bannerDismissed
    ) { access, scan, dismissed ->
        SystemAccessSnapshot(access, scan, dismissed)
    }.distinctUntilChanged()

    val uiState: StateFlow<AppUiState> = combine(
        derivedLibraryFlow.distinctUntilChanged(),
        searchInputsFlow,
        systemAccessFlow,
        themeModeFlow
    ) { lib, search, sys, themeMode ->
        val rawTrimmed = search.rawQuery.trim()
        val deepQuery = search.debouncedQuery
        val deepResult = search.deepResult
        val searchResults = if (rawTrimmed.isEmpty()) {
            emptyList()
        } else {
            sortItems(
                lib.allItems.filter { item ->
                    containsNormalized(item.title, rawTrimmed) ||
                        containsNormalized(item.folderDisplayName, rawTrimmed) ||
                        containsNormalized(item.folderName, rawTrimmed)
                },
                lib.sortOrder
            )
        }

        val itemsByUri = if (lib.deepSearch && rawTrimmed.isNotEmpty()) {
            lib.allItems.associateBy { it.uri }
        } else {
            emptyMap()
        }
        val deepSearchGroups = if (
            lib.deepSearch &&
            rawTrimmed.isNotEmpty() &&
            deepResult.query == deepQuery
        ) {
            deepResult.hits
                .asSequence()
                .filter { it.uriString in itemsByUri }
                .groupBy { it.uriString }
                .mapNotNull { (uri, hits) ->
                    val pdfItem = itemsByUri[uri] ?: return@mapNotNull null
                    DeepSearchDocGroupUi(
                        item = pdfItem,
                        hits = hits
                            .sortedBy { it.pageIndex }
                            .take(3)
                            .map { hit -> DeepSearchHitUi(pageIndex = hit.pageIndex, snippet = hit.snippet) }
                    )
                }
                .sortedWith(
                    compareByDescending<DeepSearchDocGroupUi> { it.item.lastOpenedAt }
                        .thenByDescending { it.item.addedAt }
                )
        } else {
            emptyList()
        }

        val libraryUris = lib.allItems.mapTo(HashSet()) { it.uri }
        val indexDoneCount = search.indexedDocs.count {
            it.uriString in libraryUris && it.status == IndexStatus.DONE
        }
        val indexTotalCount = lib.allItems.size

        val waitingForDeep = lib.deepSearch && rawTrimmed.isNotEmpty() &&
            (deepResult.isSearching || deepResult.query != rawTrimmed)

        AppUiState(
            themeMode = themeMode,
            viewMode = lib.viewMode,
            sort = lib.sortOrder,
            deepSearch = lib.deepSearch,
            query = search.rawQuery,
            selectedStatusFilter = lib.statusFilter,
            selectedFolderName = lib.folderName,
            selectedFolderDisplayName = lib.folderDisplayName,
            isLoading = lib.isLoading,
            heroItem = lib.heroItem,
            recentRemainingItems = lib.recentRemainingItems,
            libraryItems = lib.libraryItems,
            folderItems = lib.folderItems,
            currentFolderItems = lib.currentFolderItems,
            statusCounts = lib.statusCounts,
            totalCount = lib.allItems.size,
            searchResults = searchResults,
            deepSearchGroups = deepSearchGroups,
            indexedDocsByUri = search.indexedDocs.associateBy { it.uriString },
            indexDoneCount = indexDoneCount,
            indexTotalCount = indexTotalCount,
            isSearching = waitingForDeep,
            bookmarks = lib.bookmarks,
            annotations = lib.annotations,
            hasAllFilesAccess = sys.access.hasAllFilesAccess,
            isScanningDevice = sys.scan.isScanning,
            scannedPdfCount = sys.scan.totalFoundCount,
            isAccessBannerDismissed = sys.dismissed
        )
    }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AppUiState(
                themeMode = initialPreferences.appThemeMode.toThemeMode(),
                viewMode = if (initialPreferences.isLibraryGridView) ViewMode.Grid else ViewMode.List,
                sort = SortOrder.fromName(initialPreferences.sortOrderName),
                deepSearch = initialPreferences.deepSearch
            )
        )

    private fun containsNormalized(haystack: String, query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return false
        if (haystack.contains(trimmed, ignoreCase = true)) return true
        val needle = trimmed.replace(whitespaceRegex, " ")
        if (needle != trimmed && haystack.contains(needle, ignoreCase = true)) return true
        return haystack.replace(whitespaceRegex, " ").contains(needle, ignoreCase = true)
    }

    private fun sortItems(items: List<PdfItem>, sortOrder: SortOrder): List<PdfItem> {
        val comparator = when (sortOrder) {
            SortOrder.LastOpened -> compareByDescending<PdfItem> { it.isPinned }
                .thenByDescending { it.lastOpenedAt }
                .thenByDescending { it.addedAt }
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
        val shouldAutoScan = status.hasAllFilesAccess && (!initialScanTriggered || !previouslyHadFiles)
        initialScanTriggered = true
        if (shouldAutoScan) {
            triggerDeviceScan()
        }
    }

    fun onManualAllFilesPermissionResult(granted: Boolean) {
        if (granted) {
            triggerDeviceScan()
        } else {
            _permissionDeniedHintEvent.tryEmit(Unit)
        }
    }

    fun triggerDeviceScan() {
        val scanner = devicePdfScanner ?: return
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            scanner.scanDevicePdfs()
            libraryRepository.refreshAvailability()
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
        _selectedFolderName.value = FolderKey.canonical(folderName)
    }

    fun closeFolder() {
        _selectedFolderName.value = null
    }

    fun importAndOpenPdf(uri: Uri, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val activeFolder = _selectedFolderName.value
            val incognito = preferencesRepository.preferencesFlow.first().incognitoMode
            val entity = libraryRepository.registerPickedDocument(
                uri = uri,
                targetFolder = activeFolder,
                incognitoMode = incognito
            )
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
            libraryRepository.updateReadingStatus(uri, status)
        }
    }

    fun moveToFolder(uri: String, folderName: String) {
        viewModelScope.launch {
            libraryRepository.moveDocumentToFolder(uri, folderName)
        }
    }

    fun removeDocument(uri: String) {
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

    fun startOcr(uri: String) {
        ocrRunner?.start(uri)
    }

    fun stopOcr() {
        ocrRunner?.stop()
    }

    fun consumeOcrTerminalState() {
        ocrRunner?.clearTerminalState()
    }

    private data class DocsLoadState(
        val isLoaded: Boolean,
        val docs: List<PdfDocumentEntity>
    )

    private data class DeepSearchResult(
        val query: String = "",
        val isSearching: Boolean = false,
        val hits: List<IndexHit> = emptyList()
    )

    private data class DataSnapshot(
        val isLoaded: Boolean,
        val docs: List<PdfDocumentEntity>,
        val bookmarks: List<BookmarkEntity>,
        val annotations: List<AnnotationEntity>,
        val prefs: LibraryPrefs,
        val missingUris: Set<String>
    )

    private data class DerivedLibrarySnapshot(
        val viewMode: ViewMode,
        val sortOrder: SortOrder,
        val deepSearch: Boolean,
        val isLoading: Boolean,
        val allItems: List<PdfItem>,
        val statusFilter: ReadingStatus?,
        val folderName: String?,
        val folderDisplayName: String?,
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
        val deepResult: DeepSearchResult,
        val indexedDocs: List<IndexedDocEntity>
    )

    private data class SystemAccessSnapshot(
        val access: DeviceAccessStatus,
        val scan: PdfScanProgress,
        val dismissed: Boolean
    )

    companion object {
        private val whitespaceRegex = Regex("\\s+")

        fun factory(
            libraryRepository: PdfLibraryRepository,
            preferencesRepository: PreferencesRepository,
            searchIndexRepository: SearchIndexRepository? = null,
            devicePdfScanner: DevicePdfScanner? = null,
            ocrRunner: OcrRunner? = null,
            initialPreferences: UserPreferences = UserPreferences()
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppViewModel(
                    libraryRepository = libraryRepository,
                    preferencesRepository = preferencesRepository,
                    searchIndexRepository = searchIndexRepository,
                    devicePdfScanner = devicePdfScanner,
                    ocrRunner = ocrRunner,
                    initialPreferences = initialPreferences
                ) as T
            }
        }
    }
}
