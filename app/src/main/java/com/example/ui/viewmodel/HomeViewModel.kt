package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AnnotationEntity
import com.example.data.local.BookmarkEntity
import com.example.data.local.PdfDocumentEntity
import com.example.data.local.PreferencesRepository
import com.example.data.model.AppThemeMode
import com.example.data.model.LibrarySortOrder
import com.example.data.model.ReadingStatus
import com.example.data.repository.PdfLibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HomeTab {
    RECENT,
    LIBRARY,
    FOLDERS
}

data class VirtualFolderSummary(
    val folderName: String,
    val documentCount: Int,
    val totalSizeBytes: Long,
    val latestModifiedAt: Long
)

data class HomeUiState(
    val selectedTab: HomeTab = HomeTab.RECENT,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val selectedStatusFilter: ReadingStatus? = null,
    val sortOrder: LibrarySortOrder = LibrarySortOrder.RECENTLY_OPENED,
    val isGridView: Boolean = false,
    val incognitoMode: Boolean = false,
    val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val selectedFolderName: String? = null,
    val recentDocuments: List<PdfDocumentEntity> = emptyList(),
    val libraryDocuments: List<PdfDocumentEntity> = emptyList(),
    val folderSummaries: List<VirtualFolderSummary> = emptyList(),
    val currentFolderDocuments: List<PdfDocumentEntity> = emptyList(),
    val statusCounts: Map<ReadingStatus, Int> = emptyMap(),
    val totalDocumentCount: Int = 0,
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val annotations: List<AnnotationEntity> = emptyList(),
    val thumbnails: Map<String, Bitmap> = emptyMap()
)

class HomeViewModel(
    private val libraryRepository: PdfLibraryRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(HomeTab.RECENT)
    private val _searchQuery = MutableStateFlow("")
    private val _isSearchActive = MutableStateFlow(false)
    private val _statusFilter = MutableStateFlow<ReadingStatus?>(null)
    private val _sortOrder = MutableStateFlow(LibrarySortOrder.RECENTLY_OPENED)
    private val _selectedFolderName = MutableStateFlow<String?>(null)
    private val _thumbnails = MutableStateFlow<Map<String, Bitmap>>(emptyMap())

    private val loadingThumbnails = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            libraryRepository.ensureStarterGuideExists()
        }
    }

    private val filterParamsFlow = combine(
        _selectedTab,
        _searchQuery,
        _isSearchActive,
        _statusFilter,
        _sortOrder
    ) { tab, q, searchOpen, filter, sort ->
        FilterParams(tab, q, searchOpen, filter, sort)
    }

    private val dataBundleFlow = combine(
        libraryRepository.allDocuments,
        libraryRepository.allBookmarks,
        libraryRepository.allAnnotations,
        preferencesRepository.preferencesFlow
    ) { docs, bookmarks, annotations, prefs ->
        DataBundle(docs, bookmarks, annotations, prefs)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        dataBundleFlow,
        filterParamsFlow,
        _selectedFolderName,
        _thumbnails
    ) { bundle, params, activeFolder, thumbs ->
        val docs = bundle.docs
        val bookmarks = bundle.bookmarks
        val annotations = bundle.annotations
        val prefs = bundle.prefs
        val query = params.query.trim()

        val statusCounts = ReadingStatus.entries.associateWith { st ->
            docs.count { it.status == st }
        }

        val filteredRecent = docs.filter { doc ->
            query.isEmpty() ||
                doc.title.contains(query, ignoreCase = true) ||
                doc.folderName.contains(query, ignoreCase = true)
        }

        val filteredLibrary = docs
            .filter { doc ->
                (params.statusFilter == null || doc.status == params.statusFilter) &&
                    (query.isEmpty() || doc.title.contains(query, ignoreCase = true) || doc.folderName.contains(query, ignoreCase = true))
            }
            .let { list -> sortDocuments(list, params.sortOrder) }

        val folderGroups = docs.groupBy { it.folderName.ifBlank { "Internal Storage" } }
        val folderSummaries = folderGroups.map { (name, items) ->
            VirtualFolderSummary(
                folderName = name,
                documentCount = items.size,
                totalSizeBytes = items.sumOf { it.fileSizeBytes },
                latestModifiedAt = items.maxOfOrNull { it.lastOpenedAt } ?: 0L
            )
        }.sortedByDescending { it.latestModifiedAt }

        val folderDocs = if (activeFolder != null) {
            folderGroups[activeFolder].orEmpty().filter { doc ->
                query.isEmpty() || doc.title.contains(query, ignoreCase = true)
            }.let { sortDocuments(it, params.sortOrder) }
        } else {
            emptyList()
        }

        val filteredBookmarks = bookmarks.filter { bm ->
            query.isEmpty() ||
                bm.title.contains(query, ignoreCase = true) ||
                bm.documentTitle.contains(query, ignoreCase = true)
        }

        val filteredAnnotations = annotations.filter { ann ->
            query.isEmpty() ||
                ann.quotedText.contains(query, ignoreCase = true) ||
                ann.noteText.contains(query, ignoreCase = true) ||
                ann.documentTitle.contains(query, ignoreCase = true)
        }

        HomeUiState(
            selectedTab = params.tab,
            searchQuery = params.query,
            isSearchActive = params.searchOpen,
            selectedStatusFilter = params.statusFilter,
            sortOrder = params.sortOrder,
            isGridView = prefs.isLibraryGridView,
            incognitoMode = prefs.incognitoMode,
            appThemeMode = prefs.appThemeMode,
            selectedFolderName = activeFolder,
            recentDocuments = filteredRecent,
            libraryDocuments = filteredLibrary,
            folderSummaries = folderSummaries,
            currentFolderDocuments = folderDocs,
            statusCounts = statusCounts,
            totalDocumentCount = docs.size,
            bookmarks = filteredBookmarks,
            annotations = filteredAnnotations,
            thumbnails = thumbs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    private fun sortDocuments(
        list: List<PdfDocumentEntity>,
        sortOrder: LibrarySortOrder
    ): List<PdfDocumentEntity> {
        return when (sortOrder) {
            LibrarySortOrder.RECENTLY_OPENED -> list.sortedWith(
                compareByDescending<PdfDocumentEntity> { it.isFavorite }
                    .thenByDescending { it.lastOpenedAt }
            )
            LibrarySortOrder.TITLE_ASC -> list.sortedWith(
                compareByDescending<PdfDocumentEntity> { it.isFavorite }
                    .thenBy { it.title.lowercase() }
            )
            LibrarySortOrder.PROGRESS_DESC -> list.sortedWith(
                compareByDescending<PdfDocumentEntity> { it.isFavorite }
                    .thenByDescending { it.progressFraction }
            )
            LibrarySortOrder.PAGE_COUNT_DESC -> list.sortedWith(
                compareByDescending<PdfDocumentEntity> { it.isFavorite }
                    .thenByDescending { it.pageCount }
            )
            LibrarySortOrder.FILE_SIZE_DESC -> list.sortedWith(
                compareByDescending<PdfDocumentEntity> { it.isFavorite }
                    .thenByDescending { it.fileSizeBytes }
            )
        }
    }

    private data class FilterParams(
        val tab: HomeTab,
        val query: String,
        val searchOpen: Boolean,
        val statusFilter: ReadingStatus?,
        val sortOrder: LibrarySortOrder
    )

    private data class DataBundle(
        val docs: List<PdfDocumentEntity>,
        val bookmarks: List<BookmarkEntity>,
        val annotations: List<AnnotationEntity>,
        val prefs: com.example.data.local.UserPreferences
    )

    fun selectTab(tab: HomeTab) {
        _selectedTab.value = tab
    }

    fun selectFolder(folderName: String?) {
        _selectedFolderName.value = folderName
    }

    fun setSearchActive(active: Boolean) {
        _isSearchActive.value = active
        if (!active) _searchQuery.value = ""
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectStatusFilter(status: ReadingStatus?) {
        _statusFilter.value = status
    }

    fun setSortOrder(order: LibrarySortOrder) {
        _sortOrder.value = order
    }

    fun toggleGridView() {
        viewModelScope.launch {
            preferencesRepository.setLibraryGridView(!uiState.value.isGridView)
        }
    }

    fun toggleIncognitoMode() {
        viewModelScope.launch {
            preferencesRepository.setIncognitoMode(!uiState.value.incognitoMode)
        }
    }

    fun cycleAppTheme() {
        viewModelScope.launch {
            val next = when (uiState.value.appThemeMode) {
                AppThemeMode.SYSTEM -> AppThemeMode.LIGHT
                AppThemeMode.LIGHT -> AppThemeMode.DARK
                AppThemeMode.DARK -> AppThemeMode.AMOLED
                AppThemeMode.AMOLED -> AppThemeMode.SYSTEM
            }
            preferencesRepository.setAppThemeMode(next)
        }
    }

    fun importAndOpenPdf(uri: Uri, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val activeFolder = _selectedFolderName.value
            val entity = libraryRepository.registerPickedDocument(uri, activeFolder)
            requestThumbnail(entity.uriString)
            onReady(entity.uriString)
        }
    }

    fun requestThumbnail(uriString: String) {
        if (_thumbnails.value.containsKey(uriString) || !loadingThumbnails.add(uriString)) return
        viewModelScope.launch {
            val bitmap = libraryRepository.pdfEngine.getOrGenerateThumbnail(uriString)
            if (bitmap != null) {
                _thumbnails.update { it + (uriString to bitmap) }
            }
            loadingThumbnails.remove(uriString)
        }
    }

    fun updateDocumentStatus(uriString: String, status: ReadingStatus) {
        viewModelScope.launch {
            libraryRepository.updateReadingStatus(uriString, status)
        }
    }

    fun moveDocumentToFolder(uriString: String, folderName: String) {
        viewModelScope.launch {
            libraryRepository.moveDocumentToFolder(uriString, folderName)
        }
    }

    fun toggleFavorite(document: PdfDocumentEntity) {
        viewModelScope.launch {
            libraryRepository.toggleFavorite(document.uriString, document.isFavorite)
        }
    }

    fun removeDocument(uriString: String) {
        viewModelScope.launch {
            libraryRepository.removeDocument(uriString)
        }
    }

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch {
            libraryRepository.deleteBookmark(bookmarkId)
        }
    }

    fun deleteAnnotation(annotationId: Long) {
        viewModelScope.launch {
            libraryRepository.deleteAnnotation(annotationId)
        }
    }

    companion object {
        fun factory(
            libraryRepository: PdfLibraryRepository,
            preferencesRepository: PreferencesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(libraryRepository, preferencesRepository) as T
            }
        }
    }
}
