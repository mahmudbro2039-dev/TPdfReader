package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.local.AnnotationEntity
import com.example.data.local.BookmarkEntity
import com.example.data.local.PdfDocumentEntity
import com.example.data.local.PreferencesRepository
import com.example.data.local.UserPreferences
import com.example.data.model.AnnotationType
import com.example.data.model.PageFitPolicy
import com.example.data.model.PdfColorMode
import com.example.data.model.ReadingStatus
import com.example.data.model.ScrollLayoutMode
import com.example.data.model.TextFontChoice
import com.example.data.model.TextModeTheme
import com.example.data.pdf.DocumentLinkEntry
import com.example.data.pdf.PdfEngine
import com.example.data.pdf.PdfPageDimension
import com.example.data.pdf.PdfStructureParser
import com.example.data.pdf.TocEntry
import com.example.data.repository.PdfLibraryRepository
import com.example.data.search.SearchIndexRepository
import com.example.data.search.SearchIndexer
import com.example.data.search.SearchTextNormalizer
import com.example.data.search.db.IndexStatus
import com.example.data.text.OpenResult
import com.example.data.text.PdfTextEngine
import com.example.ui.theme.ContentColors
import com.example.ui.theme.ReaderZoomTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class OrientationLock {
    AUTO,
    LOCKED
}

data class ReaderViewOptions(
    val colorMode: PdfColorMode? = null,
    val layoutMode: ScrollLayoutMode? = null,
    val pageFitPolicy: PageFitPolicy? = null
)

sealed interface ReaderLoadState {
    data object Loading : ReaderLoadState
    data object Ready : ReaderLoadState
    data class Error(val message: String = "") : ReaderLoadState
}

data class InPdfSearchMatch(
    val pageIndex: Int,
    val snippet: String,
    val matchOrdinalOnPage: Int
)

data class ReaderUiState(
    val loadState: ReaderLoadState = ReaderLoadState.Loading,
    val uriString: String = "",
    val documentTitle: String = "",
    val folderName: String = "",
    val fileSizeBytes: Long = 0L,
    val readingStatus: ReadingStatus = ReadingStatus.TO_READ,
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val pageDimensions: List<PdfPageDimension> = emptyList(),
    val zoomScale: Float = 1f,
    val isZoomLocked: Boolean = false,
    val hideMargins: Boolean = false,
    val pageFitPolicy: PageFitPolicy = PageFitPolicy.FIT_WIDTH,
    val colorMode: PdfColorMode = PdfColorMode.NORMAL,
    val layoutMode: ScrollLayoutMode = ScrollLayoutMode.VERTICAL,
    val isRtl: Boolean = false,
    val incognitoMode: Boolean = false,
    val isChromeVisible: Boolean = true,
    val isAutoScrollActive: Boolean = false,
    val autoScrollSpeed: Int = 3,
    val brightnessOverride: Float? = null,
    val isBrightnessOverlayVisible: Boolean = false,
    val isSearchBarVisible: Boolean = false,
    val searchQuery: String = "",
    val searchMatches: List<InPdfSearchMatch> = emptyList(),
    val activeSearchMatchIndex: Int = 0,
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val annotations: List<AnnotationEntity> = emptyList(),
    val tocEntries: List<TocEntry> = emptyList(),
    val documentLinks: List<DocumentLinkEntry> = emptyList(),
    val jumpBackStack: List<Int> = emptyList(),
    val jumpForwardStack: List<Int> = emptyList(),
    val visitedPagesHistory: List<Int> = emptyList(),
    val pendingScrollTargetPage: Int? = null,
    val isSearchIndexing: Boolean = false,
    val searchStatusRes: Int? = null,
    val currentPageText: String = "",
    val orientationLock: OrientationLock = OrientationLock.AUTO
) {
    val isCurrentPageBookmarked: Boolean
        get() = bookmarks.any { it.pageIndex == currentPage }

    val currentPageAnnotations: List<AnnotationEntity>
        get() = annotations.filter { it.pageIndex == currentPage }

    val canJumpBack: Boolean
        get() = jumpBackStack.isNotEmpty()

    val canJumpForward: Boolean
        get() = jumpForwardStack.isNotEmpty()
}

class ReaderViewModel(
    private val libraryRepository: PdfLibraryRepository,
    private val preferencesRepository: PreferencesRepository,
    private val pdfEngine: PdfEngine,
    private val searchIndexRepository: SearchIndexRepository? = null,
    private val searchIndexer: SearchIndexer? = null,
    private val textEngine: PdfTextEngine? = null,
    private val appContext: Context? = null,
    initialPreferences: UserPreferences = UserPreferences()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _viewOptions = MutableStateFlow(ReaderViewOptions())

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = initialPreferences
        )

    private var documentJob: Job? = null
    private var bookmarksJob: Job? = null
    private var annotationsJob: Job? = null
    private var progressSaveJob: Job? = null
    private var indexObserveJob: Job? = null
    private var pendingDelayedRerunJob: Job? = null
    private var structureJob: Job? = null
    private var pageTextJob: Job? = null
    private var inPdfSearchJob: Job? = null
    private var openDocumentJob: Job? = null
    private var prefetchJob: Job? = null
    private var dimensionScanJob: Job? = null
    private var activeLoadingInitialPage: Int? = null
    private var lastRenderTargetWidthPx: Int = 0
    private var loadedStructureUri: String? = null
    private var prioritizedUri: String? = null
    private var lastObservedTextPages: Int = -1
    private var lastSearchRerunAtMs: Long = 0L

    private data class PrefDefaults(
        val incognito: Boolean,
        val colorMode: PdfColorMode,
        val layoutMode: ScrollLayoutMode,
        val pageFitPolicy: PageFitPolicy
    )

    init {
        viewModelScope.launch {
            combine(
                preferencesRepository.preferencesFlow.map { prefs ->
                    PrefDefaults(
                        incognito = prefs.incognitoMode,
                        colorMode = prefs.defaultPdfColorMode,
                        layoutMode = prefs.defaultLayoutMode,
                        pageFitPolicy = prefs.defaultPageFitPolicy
                    )
                }.distinctUntilChanged(),
                _viewOptions
            ) { defaults, overrides ->
                defaults to overrides
            }.collect { (defaults, overrides) ->
                if (defaults.incognito) {
                    progressSaveJob?.cancel()
                }
                _uiState.update { state ->
                    state.copy(
                        incognitoMode = defaults.incognito,
                        colorMode = overrides.colorMode ?: defaults.colorMode,
                        layoutMode = overrides.layoutMode ?: defaults.layoutMode,
                        pageFitPolicy = overrides.pageFitPolicy ?: defaults.pageFitPolicy
                    )
                }
            }
        }
    }

    fun openDocument(uriString: String, initialPageOverride: Int? = null) {
        if (uriString.isBlank()) {
            _uiState.update {
                it.copy(
                    loadState = ReaderLoadState.Error(),
                    uriString = ""
                )
            }
            return
        }
        val currentState = _uiState.value
        val alreadyOpen = currentState.uriString == uriString && currentState.loadState is ReaderLoadState.Ready
        if (alreadyOpen && initialPageOverride != null) {
            jumpToPage(initialPageOverride, recordHistory = true)
            return
        }
        if (alreadyOpen) return

        val alreadyLoadingSame = currentState.uriString == uriString &&
            currentState.loadState is ReaderLoadState.Loading &&
            openDocumentJob?.isActive == true &&
            activeLoadingInitialPage == initialPageOverride
        if (alreadyLoadingSame) return

        openDocumentJob?.cancel()
        prefetchJob?.cancel()
        dimensionScanJob?.cancel()
        documentJob?.cancel()
        bookmarksJob?.cancel()
        annotationsJob?.cancel()
        indexObserveJob?.cancel()
        pendingDelayedRerunJob?.cancel()
        structureJob?.cancel()
        pageTextJob?.cancel()
        inPdfSearchJob?.cancel()
        activeLoadingInitialPage = initialPageOverride
        loadedStructureUri = null
        prioritizedUri = null
        lastObservedTextPages = -1

        if (currentState.uriString != uriString) {
            _viewOptions.value = ReaderViewOptions()
        }
        val activeOverrides = _viewOptions.value
        val currentPrefs = preferences.value
        val instantFallbackTitle = if (currentState.uriString == uriString && currentState.documentTitle.isNotBlank()) {
            currentState.documentTitle
        } else {
            libraryRepository.fallbackTitleFromUri(uriString)
        }
        _uiState.update {
            it.copy(
                loadState = ReaderLoadState.Loading,
                uriString = uriString,
                documentTitle = instantFallbackTitle,
                pageCount = 0,
                currentPage = initialPageOverride ?: 0,
                pageDimensions = emptyList(),
                zoomScale = 1f,
                hideMargins = currentPrefs.hideMarginsByDefault,
                pageFitPolicy = activeOverrides.pageFitPolicy ?: currentPrefs.defaultPageFitPolicy,
                colorMode = activeOverrides.colorMode ?: currentPrefs.defaultPdfColorMode,
                layoutMode = activeOverrides.layoutMode ?: currentPrefs.defaultLayoutMode,
                isRtl = currentPrefs.rtlReading,
                incognitoMode = currentPrefs.incognitoMode,
                isChromeVisible = true,
                isAutoScrollActive = false,
                brightnessOverride = null,
                isBrightnessOverlayVisible = false,
                isSearchBarVisible = false,
                searchQuery = "",
                searchMatches = emptyList(),
                activeSearchMatchIndex = 0,
                bookmarks = emptyList(),
                annotations = emptyList(),
                jumpBackStack = emptyList(),
                jumpForwardStack = emptyList(),
                visitedPagesHistory = emptyList(),
                pendingScrollTargetPage = null,
                isSearchIndexing = false,
                searchStatusRes = null,
                currentPageText = "",
                tocEntries = emptyList(),
                documentLinks = emptyList(),
                orientationLock = if (currentState.uriString == uriString) {
                    currentState.orientationLock
                } else {
                    OrientationLock.AUTO
                }
            )
        }

        openDocumentJob = viewModelScope.launch {
            val (prefs, savedDoc, result) = try {
                coroutineScope {
                    val prefsDeferred = async { preferencesRepository.preferencesFlow.first() }
                    val savedDocDeferred = async { libraryRepository.getDocument(uriString) }
                    val engineDeferred = async {
                        pdfEngine.openDocument(uriString, initialPageOverride ?: 0)
                    }
                    Triple(prefsDeferred.await(), savedDocDeferred.await(), engineDeferred.await())
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loadState = ReaderLoadState.Error(
                            e.localizedMessage.orEmpty()
                        )
                    )
                }
                return@launch
            }

            result.fold(
                onSuccess = { metadata ->
                    val maxIndex = (metadata.pageCount - 1).coerceAtLeast(0)
                    val startPage = (initialPageOverride ?: savedDoc?.currentPage ?: 0).coerceIn(0, maxIndex)
                    val resolvedTitle = savedDoc?.title?.takeIf { it.isNotBlank() }
                        ?: libraryRepository.resolveDocumentTitle(uriString)

                    val resolvedOverrides = _viewOptions.value
                    _uiState.update {
                        it.copy(
                            loadState = ReaderLoadState.Ready,
                            uriString = uriString,
                            documentTitle = resolvedTitle,
                            folderName = savedDoc?.folderName.orEmpty(),
                            fileSizeBytes = savedDoc?.fileSizeBytes ?: 0L,
                            readingStatus = savedDoc?.status ?: ReadingStatus.TO_READ,
                            pageCount = metadata.pageCount,
                            currentPage = startPage,
                            pageDimensions = metadata.pageDimensions,
                            zoomScale = (savedDoc?.zoomScale ?: 1f).coerceIn(
                                ReaderZoomTokens.ZoomMin,
                                ReaderZoomTokens.ZoomMax
                            ),
                            hideMargins = prefs.hideMarginsByDefault,
                            pageFitPolicy = resolvedOverrides.pageFitPolicy ?: prefs.defaultPageFitPolicy,
                            colorMode = resolvedOverrides.colorMode ?: prefs.defaultPdfColorMode,
                            layoutMode = resolvedOverrides.layoutMode ?: prefs.defaultLayoutMode,
                            isRtl = prefs.rtlReading,
                            incognitoMode = prefs.incognitoMode,
                            autoScrollSpeed = prefs.autoScrollSpeed,
                            visitedPagesHistory = listOf(startPage),
                            pendingScrollTargetPage = startPage
                        )
                    }

                    observeDocumentBookmarksAndAnnotations(uriString)
                    prioritizedUri = uriString
                    searchIndexer?.prioritize(uriString)
                    observeIndexDoc(uriString)
                    if (!prefs.incognitoMode) {
                        libraryRepository.markOpened(
                            uriString = uriString,
                            now = System.currentTimeMillis(),
                            pageCount = metadata.pageCount
                        )
                    }
                    schedulePrefetchAround(startPage)
                    scheduleProgressPersist()
                    scheduleDimensionScan(uriString, metadata.pageCount)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            loadState = ReaderLoadState.Error(
                                error.localizedMessage.orEmpty()
                            )
                        )
                    }
                }
            )
        }
    }

    private fun observeDocumentBookmarksAndAnnotations(uriString: String) {
        documentJob?.cancel()
        documentJob = viewModelScope.launch {
            libraryRepository.observeDocument(uriString).collect { doc ->
                if (doc != null) {
                    val docTitle = doc.title.trim()
                    val docStatus = doc.status
                    _uiState.update { state ->
                        if (state.uriString == uriString &&
                            ((docTitle.isNotBlank() && state.documentTitle != docTitle) || state.readingStatus != docStatus)
                        ) {
                            state.copy(
                                documentTitle = if (docTitle.isNotBlank()) docTitle else state.documentTitle,
                                folderName = doc.folderName,
                                fileSizeBytes = doc.fileSizeBytes,
                                readingStatus = docStatus
                            )
                        } else {
                            state
                        }
                    }
                }
            }
        }
        bookmarksJob?.cancel()
        bookmarksJob = viewModelScope.launch {
            libraryRepository.observeBookmarksForDocument(uriString).collect { list ->
                _uiState.update { it.copy(bookmarks = list) }
            }
        }
        annotationsJob?.cancel()
        annotationsJob = viewModelScope.launch {
            libraryRepository.observeAnnotationsForDocument(uriString).collect { list ->
                _uiState.update { it.copy(annotations = list) }
            }
        }
    }

    private fun scheduleDimensionScan(uriString: String, pageCount: Int) {
        if (pageCount <= 1) return
        dimensionScanJob?.cancel()
        dimensionScanJob = viewModelScope.launch {
            delay(280L)
            val dims = pdfEngine.inspectAllPageDimensions(uriString) ?: return@launch
            _uiState.update { state ->
                if (state.uriString == uriString && state.pageDimensions != dims) {
                    state.copy(pageDimensions = dims)
                } else {
                    state
                }
            }
        }
    }

    private fun observeIndexDoc(uriString: String) {
        val repo = searchIndexRepository ?: return
        indexObserveJob?.cancel()
        pendingDelayedRerunJob?.cancel()
        lastObservedTextPages = -1
        indexObserveJob = viewModelScope.launch {
            repo.observeDoc(uriString).collect { doc ->
                val state = _uiState.value
                if (state.uriString != uriString) return@collect

                val status = doc?.status
                val textPages = doc?.textPages ?: 0
                val isIndexing = status == null ||
                    status == IndexStatus.PENDING ||
                    status == IndexStatus.INDEXING
                val statusRes = when (status) {
                    null, IndexStatus.PENDING, IndexStatus.INDEXING -> {
                        if (textPages == 0) R.string.reader_search_preparing
                        else R.string.reader_search_partial
                    }
                    IndexStatus.DONE -> {
                        if (textPages == 0) R.string.reader_search_no_text else null
                    }
                    IndexStatus.ENCRYPTED, IndexStatus.FAILED -> R.string.reader_search_no_text
                }

                if (isIndexing && prioritizedUri != uriString) {
                    prioritizedUri = uriString
                    searchIndexer?.prioritize(uriString)
                }

                _uiState.update {
                    it.copy(
                        isSearchIndexing = isIndexing,
                        searchStatusRes = statusRes
                    )
                }

                val latest = _uiState.value
                if (latest.isSearchBarVisible &&
                    SearchTextNormalizer.buildMatchQuery(latest.searchQuery) != null
                ) {
                    if (status == IndexStatus.DONE) {
                        pendingDelayedRerunJob?.cancel()
                        if (textPages != lastObservedTextPages) {
                            lastObservedTextPages = textPages
                            runInPdfSearch(latest.searchQuery, jumpToFirst = false)
                        }
                    } else if (textPages > 0 && textPages != lastObservedTextPages) {
                        val now = System.currentTimeMillis()
                        val elapsed = now - lastSearchRerunAtMs
                        if (elapsed >= 1_000L) {
                            lastSearchRerunAtMs = now
                            lastObservedTextPages = textPages
                            runInPdfSearch(latest.searchQuery, jumpToFirst = false)
                        } else if (pendingDelayedRerunJob?.isActive != true) {
                            val waitMs = (1_000L - elapsed).coerceAtLeast(50L)
                            pendingDelayedRerunJob = viewModelScope.launch {
                                delay(waitMs)
                                val snap = _uiState.value
                                if (snap.uriString == uriString &&
                                    snap.isSearchBarVisible &&
                                    SearchTextNormalizer.buildMatchQuery(snap.searchQuery) != null
                                ) {
                                    lastSearchRerunAtMs = System.currentTimeMillis()
                                    lastObservedTextPages = textPages
                                    runInPdfSearch(snap.searchQuery, jumpToFirst = false)
                                }
                            }
                        }
                    }
                } else {
                    lastObservedTextPages = textPages
                }
            }
        }
    }

    fun peekPageBitmap(pageIndex: Int, targetWidthPx: Int): Bitmap? {
        val state = _uiState.value
        if (state.uriString.isBlank() || targetWidthPx <= 0) return null
        if (lastRenderTargetWidthPx != targetWidthPx) {
            lastRenderTargetWidthPx = targetWidthPx
            schedulePrefetchAround(state.currentPage)
        }
        val quality = preferences.value.renderQuality.scaleFactor
        return pdfEngine.peekCachedPage(
            uriString = state.uriString,
            pageIndex = pageIndex,
            targetWidthPx = targetWidthPx,
            qualityScale = quality,
            hideMargins = state.hideMargins
        )
    }

    suspend fun renderPageBitmap(pageIndex: Int, targetWidthPx: Int): Bitmap? {
        val state = _uiState.value
        if (state.uriString.isBlank() || targetWidthPx <= 0) return null
        if (lastRenderTargetWidthPx != targetWidthPx) {
            lastRenderTargetWidthPx = targetWidthPx
            schedulePrefetchAround(state.currentPage)
        }
        val quality = preferences.value.renderQuality.scaleFactor
        return pdfEngine.renderPage(
            uriString = state.uriString,
            pageIndex = pageIndex,
            targetWidthPx = targetWidthPx,
            qualityScale = quality,
            hideMargins = state.hideMargins
        )
    }

    suspend fun renderPageRegion(
        pageIndex: Int,
        zoomedPageWidthPx: Int,
        left: Int,
        top: Int,
        outWidth: Int,
        outHeight: Int
    ): Bitmap? {
        val uri = _uiState.value.uriString
        if (uri.isBlank()) return null
        return pdfEngine.renderRegion(
            uriString = uri,
            pageIndex = pageIndex,
            zoomedPageWidthPx = zoomedPageWidthPx,
            left = left,
            top = top,
            outWidth = outWidth,
            outHeight = outHeight
        )
    }

    private fun schedulePrefetchAround(centerPage: Int) {
        val state = _uiState.value
        val widthPx = lastRenderTargetWidthPx
        if (state.uriString.isBlank() || state.pageCount <= 1 || widthPx <= 0) return
        val uri = state.uriString
        val total = state.pageCount
        val hideMargins = state.hideMargins
        val quality = preferences.value.renderQuality.scaleFactor

        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            delay(45L)
            val candidates = listOf(centerPage + 1, centerPage - 1, centerPage + 2, centerPage - 2)
                .distinct()
                .filter { it in 0 until total && it != centerPage }
            for (pageIdx in candidates) {
                if (_uiState.value.uriString != uri) break
                pdfEngine.prefetchPage(
                    uriString = uri,
                    pageIndex = pageIdx,
                    targetWidthPx = widthPx,
                    qualityScale = quality,
                    hideMargins = hideMargins
                )
            }
        }
    }

    fun onPageScrolled(newPage: Int, persistProgress: Boolean = true) {
        val state = _uiState.value
        val clamped = newPage.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
        if (clamped != state.currentPage) {
            val updatedVisited = if (state.visitedPagesHistory.lastOrNull() == clamped) {
                state.visitedPagesHistory
            } else {
                (state.visitedPagesHistory + clamped).takeLast(35)
            }
            _uiState.update {
                it.copy(
                    currentPage = clamped,
                    visitedPagesHistory = updatedVisited,
                    currentPageText = ""
                )
            }
            schedulePrefetchAround(clamped)
        }
        if (persistProgress) {
            scheduleProgressPersist()
        }
    }

    fun onPageSettled(settledPage: Int) {
        onPageScrolled(settledPage, persistProgress = true)
    }

    fun consumePendingScrollTarget() {
        _uiState.update { it.copy(pendingScrollTargetPage = null) }
    }

    fun jumpToPage(targetPage: Int, recordHistory: Boolean = true) {
        val state = _uiState.value
        val maxPage = (state.pageCount - 1).coerceAtLeast(0)
        val clamped = targetPage.coerceIn(0, maxPage)
        val updatedBack = if (recordHistory && clamped != state.currentPage) {
            (state.jumpBackStack + state.currentPage).takeLast(25)
        } else {
            state.jumpBackStack
        }
        val updatedForward = if (recordHistory) emptyList() else state.jumpForwardStack
        val updatedVisited = if (state.visitedPagesHistory.lastOrNull() == clamped) {
            state.visitedPagesHistory
        } else {
            (state.visitedPagesHistory + clamped).takeLast(35)
        }

        _uiState.update {
            it.copy(
                currentPage = clamped,
                jumpBackStack = updatedBack,
                jumpForwardStack = updatedForward,
                visitedPagesHistory = updatedVisited,
                pendingScrollTargetPage = clamped,
                currentPageText = ""
            )
        }
        schedulePrefetchAround(clamped)
        scheduleProgressPersist()
    }

    fun jumpBackInHistory() {
        val state = _uiState.value
        if (state.jumpBackStack.isEmpty()) return
        val previousPage = state.jumpBackStack.last()
        _uiState.update {
            it.copy(
                currentPage = previousPage,
                jumpBackStack = it.jumpBackStack.dropLast(1),
                jumpForwardStack = (it.jumpForwardStack + it.currentPage).takeLast(25),
                pendingScrollTargetPage = previousPage
            )
        }
        schedulePrefetchAround(previousPage)
        scheduleProgressPersist()
    }

    fun jumpForwardInHistory() {
        val state = _uiState.value
        if (state.jumpForwardStack.isEmpty()) return
        val nextPage = state.jumpForwardStack.last()
        _uiState.update {
            it.copy(
                currentPage = nextPage,
                jumpForwardStack = it.jumpForwardStack.dropLast(1),
                jumpBackStack = (it.jumpBackStack + it.currentPage).takeLast(25),
                pendingScrollTargetPage = nextPage
            )
        }
        schedulePrefetchAround(nextPage)
        scheduleProgressPersist()
    }

    fun stepPage(delta: Int) {
        val state = _uiState.value
        val step = if (state.layoutMode == ScrollLayoutMode.DUAL_PAGE) delta * 2 else delta
        val target = (state.currentPage + step).coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
        if (target != state.currentPage) {
            jumpToPage(target, recordHistory = false)
        }
    }

    fun stepSinglePage(delta: Int) {
        val state = _uiState.value
        val target = (state.currentPage + delta).coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
        if (target != state.currentPage) {
            jumpToPage(target, recordHistory = false)
        }
    }

    fun setChromeVisible(visible: Boolean) {
        _uiState.update {
            it.copy(
                isChromeVisible = visible,
                isBrightnessOverlayVisible = false
            )
        }
    }

    fun toggleChromeVisibility() {
        _uiState.update {
            it.copy(
                isChromeVisible = !it.isChromeVisible,
                isBrightnessOverlayVisible = false
            )
        }
    }

    fun setZoomScale(scale: Float) {
        val current = _uiState.value
        if (current.isZoomLocked) return
        val clamped = scale.coerceIn(ReaderZoomTokens.ZoomMin, ReaderZoomTokens.ZoomMax)
        if (kotlin.math.abs(current.zoomScale - clamped) < 0.005f) return
        _uiState.update { it.copy(zoomScale = clamped) }
        scheduleProgressPersist()
    }

    fun toggleZoomLock() {
        _uiState.update { it.copy(isZoomLocked = !it.isZoomLocked) }
    }

    fun toggleHideMargins() {
        _uiState.update { it.copy(hideMargins = !it.hideMargins) }
        schedulePrefetchAround(_uiState.value.currentPage)
    }

    fun setPageFitPolicy(policy: PageFitPolicy) {
        _viewOptions.update { it.copy(pageFitPolicy = policy) }
        _uiState.update { it.copy(pageFitPolicy = policy) }
    }

    fun toggleIncognitoMode() {
        val next = !_uiState.value.incognitoMode
        if (next) {
            progressSaveJob?.cancel()
        }
        _uiState.update { it.copy(incognitoMode = next) }
        viewModelScope.launch {
            preferencesRepository.setIncognitoMode(next)
        }
    }

    // Live In-Document Search backed by Room FTS4 index
    fun toggleSearchBar() {
        if (_uiState.value.isSearchBarVisible) {
            closeSearchBar()
        } else {
            val state = _uiState.value
            _uiState.update {
                it.copy(
                    isSearchBarVisible = true,
                    isChromeVisible = true
                )
            }
            if (state.isSearchIndexing && state.uriString.isNotBlank() && prioritizedUri != state.uriString) {
                prioritizedUri = state.uriString
                searchIndexer?.prioritize(state.uriString)
            }
        }
    }

    fun closeSearchBar() {
        inPdfSearchJob?.cancel()
        pendingDelayedRerunJob?.cancel()
        _uiState.update {
            it.copy(
                isSearchBarVisible = false,
                searchQuery = "",
                searchMatches = emptyList(),
                activeSearchMatchIndex = 0
            )
        }
    }

    fun updateSearchQuery(query: String) {
        inPdfSearchJob?.cancel()
        pendingDelayedRerunJob?.cancel()
        _uiState.update { it.copy(searchQuery = query) }
        if (SearchTextNormalizer.buildMatchQuery(query) == null) {
            _uiState.update {
                it.copy(
                    searchMatches = emptyList(),
                    activeSearchMatchIndex = 0
                )
            }
            return
        }
        runInPdfSearch(query, jumpToFirst = true)
    }

    private fun runInPdfSearch(query: String, jumpToFirst: Boolean) {
        val repo = searchIndexRepository ?: return
        val uri = _uiState.value.uriString
        if (uri.isBlank()) return
        inPdfSearchJob?.cancel()
        inPdfSearchJob = viewModelScope.launch {
            val hits = repo.searchInDocument(uri, query)
            val matches = hits.map { hit ->
                InPdfSearchMatch(
                    pageIndex = hit.pageIndex,
                    snippet = hit.snippet,
                    matchOrdinalOnPage = 0
                )
            }
            val latest = _uiState.value
            if (!latest.isSearchBarVisible || latest.searchQuery != query || latest.uriString != uri) {
                return@launch
            }

            val firstMatchNearCurrent = matches.indexOfFirst { it.pageIndex >= latest.currentPage }
                .takeIf { it >= 0 } ?: 0

            _uiState.update {
                it.copy(
                    searchMatches = matches,
                    activeSearchMatchIndex = firstMatchNearCurrent
                )
            }

            if (jumpToFirst) {
                matches.getOrNull(firstMatchNearCurrent)?.let { match ->
                    jumpToPage(match.pageIndex, recordHistory = false)
                }
            }
        }
    }

    fun navigateSearchMatch(delta: Int) {
        val matches = _uiState.value.searchMatches
        if (matches.isEmpty()) return
        // Wrapping search navigation at both ends
        val size = matches.size
        val nextIdx = ((_uiState.value.activeSearchMatchIndex + delta) % size + size) % size
        _uiState.update { it.copy(activeSearchMatchIndex = nextIdx) }
        jumpToPage(matches[nextIdx].pageIndex, recordHistory = false)
    }

    fun cycleColorMode() {
        val modes = PdfColorMode.entries
        val nextIndex = (modes.indexOf(_uiState.value.colorMode) + 1) % modes.size
        setColorMode(modes[nextIndex])
    }

    fun setColorMode(mode: PdfColorMode) {
        _viewOptions.update { it.copy(colorMode = mode) }
        _uiState.update { it.copy(colorMode = mode) }
    }

    fun setLayoutMode(mode: ScrollLayoutMode) {
        _viewOptions.update { it.copy(layoutMode = mode) }
        _uiState.update {
            it.copy(
                layoutMode = mode,
                pendingScrollTargetPage = it.currentPage
            )
        }
    }

    fun toggleRtl() {
        _uiState.update { it.copy(isRtl = !it.isRtl) }
    }

    fun toggleAutoScroll() {
        _uiState.update { it.copy(isAutoScrollActive = !it.isAutoScrollActive) }
    }

    fun stopAutoScroll() {
        _uiState.update { it.copy(isAutoScrollActive = false) }
    }

    fun adjustAutoScrollSpeed(delta: Int) {
        val newSpeed = (_uiState.value.autoScrollSpeed + delta).coerceIn(1, 10)
        _uiState.update { it.copy(autoScrollSpeed = newSpeed) }
        viewModelScope.launch {
            preferencesRepository.setAutoScrollSpeed(newSpeed)
        }
    }

    fun toggleBrightnessOverlay() {
        _uiState.update { it.copy(isBrightnessOverlayVisible = !it.isBrightnessOverlayVisible) }
    }

    fun setBrightnessOverride(value: Float?) {
        _uiState.update { it.copy(brightnessOverride = value?.coerceIn(0.05f, 1f)) }
    }

    fun toggleOrientationLock() {
        _uiState.update { state ->
            val next = if (state.orientationLock == OrientationLock.LOCKED) {
                OrientationLock.AUTO
            } else {
                OrientationLock.LOCKED
            }
            state.copy(orientationLock = next)
        }
    }

    fun updateReadingStatus(status: ReadingStatus) {
        val uri = _uiState.value.uriString
        if (uri.isBlank()) return
        _uiState.update { it.copy(readingStatus = status) }
        viewModelScope.launch {
            libraryRepository.updateReadingStatus(uri, status)
        }
    }

    fun toggleCurrentPageBookmark() {
        val state = _uiState.value
        if (state.uriString.isBlank()) return
        viewModelScope.launch {
            if (state.isCurrentPageBookmarked) {
                libraryRepository.deleteBookmarksAtPage(state.uriString, state.currentPage)
            } else {
                libraryRepository.addBookmark(
                    documentUri = state.uriString,
                    documentTitle = state.documentTitle,
                    pageIndex = state.currentPage,
                    customTitle = ""
                )
            }
        }
    }

    fun addBookmark(pageIndex: Int, title: String) {
        val state = _uiState.value
        if (state.uriString.isBlank()) return
        viewModelScope.launch {
            libraryRepository.addBookmark(
                documentUri = state.uriString,
                documentTitle = state.documentTitle,
                pageIndex = pageIndex,
                customTitle = title
            )
        }
    }

    fun renameBookmark(bookmarkId: Long, newTitle: String) {
        viewModelScope.launch {
            libraryRepository.renameBookmark(bookmarkId, newTitle)
        }
    }

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch {
            libraryRepository.deleteBookmark(bookmarkId)
        }
    }

    fun addHighlightOrNote(
        pageIndex: Int,
        type: AnnotationType,
        quotedText: String,
        noteText: String,
        colorHex: String
    ) {
        val state = _uiState.value
        if (state.uriString.isBlank()) return
        viewModelScope.launch {
            libraryRepository.addAnnotation(
                documentUri = state.uriString,
                documentTitle = state.documentTitle,
                pageIndex = pageIndex,
                type = type,
                quotedText = quotedText,
                noteText = noteText,
                colorHex = colorHex
            )
        }
    }

    fun stampSignatureOnCurrentPage(signatureVectorData: String, signerLabel: String) {
        val state = _uiState.value
        if (state.uriString.isBlank() || signatureVectorData.isBlank()) return
        viewModelScope.launch {
            preferencesRepository.setSavedSignatureVector(signatureVectorData)
            libraryRepository.stampSignatureAnnotation(
                documentUri = state.uriString,
                documentTitle = state.documentTitle,
                pageIndex = state.currentPage,
                signerLabel = signerLabel,
                colorHex = ContentColors.signatureInkHex,
                signatureVectorData = signatureVectorData
            )
        }
    }

    fun deleteAnnotation(annotationId: Long) {
        viewModelScope.launch {
            libraryRepository.deleteAnnotation(annotationId)
        }
    }

    fun loadCurrentPageTextIfNeeded() {
        val engine = textEngine ?: return
        val state = _uiState.value
        val uri = state.uriString
        val pageIndex = state.currentPage
        if (uri.isBlank() || state.currentPageText.isNotEmpty()) return
        pageTextJob?.cancel()
        pageTextJob = viewModelScope.launch(Dispatchers.Default) {
            val text = when (val openRes = engine.open(Uri.parse(uri))) {
                is OpenResult.Ok -> openRes.session.use { session ->
                    if (pageIndex in 0 until session.pageCount) session.page(pageIndex).text else ""
                }
                is OpenResult.Error -> ""
            }
            if (_uiState.value.uriString == uri && _uiState.value.currentPage == pageIndex) {
                _uiState.update { it.copy(currentPageText = text) }
            }
        }
    }

    fun loadStructureIfNeeded() {
        val engine = textEngine ?: return
        val ctx = appContext ?: return
        val state = _uiState.value
        val uri = state.uriString
        if (uri.isBlank() || loadedStructureUri == uri || structureJob?.isActive == true) return
        structureJob = viewModelScope.launch(Dispatchers.Default) {
            val pages = mutableListOf<String>()
            var totalPages = state.pageCount
            when (val openRes = engine.open(Uri.parse(uri))) {
                is OpenResult.Ok -> openRes.session.use { session ->
                    totalPages = session.pageCount
                    val cap = minOf(session.pageCount, 200)
                    for (i in 0 until cap) {
                        pages.add(session.page(i).text)
                    }
                }
                is OpenResult.Error -> Unit
            }
            if (_uiState.value.uriString != uri) return@launch
            val toc = PdfStructureParser.buildTableOfContents(ctx, pages, totalPages)
            val links = PdfStructureParser.extractDocumentLinks(ctx, pages, totalPages)
            loadedStructureUri = uri
            _uiState.update {
                it.copy(
                    tocEntries = toc,
                    documentLinks = links
                )
            }
        }
    }

    fun setTextModeTheme(theme: TextModeTheme) {
        viewModelScope.launch { preferencesRepository.setTextModeTheme(theme) }
    }

    fun setTextModeFontSize(sizeSp: Float) {
        viewModelScope.launch { preferencesRepository.setTextModeFontSize(sizeSp) }
    }

    fun setTextModeLineHeight(multiplier: Float) {
        viewModelScope.launch { preferencesRepository.setTextModeLineHeight(multiplier) }
    }

    fun setTextModeFontChoice(choice: TextFontChoice) {
        viewModelScope.launch { preferencesRepository.setTextModeFontChoice(choice) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setKeepScreenOn(enabled) }
    }

    private fun scheduleProgressPersist() {
        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch {
            delay(350L)
            val state = _uiState.value
            val isIncognito = state.incognitoMode ||
                preferencesRepository.preferencesFlow.first().incognitoMode
            if (state.uriString.isNotBlank() && state.pageCount > 0 && !isIncognito) {
                libraryRepository.updateProgress(
                    uriString = state.uriString,
                    currentPage = state.currentPage,
                    pageCount = state.pageCount,
                    zoomScale = state.zoomScale,
                    incognitoMode = false
                )
            }
        }
    }

    fun closeDocument() {
        val currentUri = _uiState.value.uriString
        if (currentUri.isBlank() && openDocumentJob?.isActive != true) return
        openDocumentJob?.cancel()
        prefetchJob?.cancel()
        dimensionScanJob?.cancel()
        documentJob?.cancel()
        bookmarksJob?.cancel()
        annotationsJob?.cancel()
        progressSaveJob?.cancel()
        indexObserveJob?.cancel()
        pendingDelayedRerunJob?.cancel()
        structureJob?.cancel()
        pageTextJob?.cancel()
        inPdfSearchJob?.cancel()
        activeLoadingInitialPage = null
        loadedStructureUri = null
        prioritizedUri = null
        lastObservedTextPages = -1
        _viewOptions.value = ReaderViewOptions()
        _uiState.update {
            ReaderUiState(
                incognitoMode = it.incognitoMode,
                orientationLock = OrientationLock.AUTO
            )
        }
        pdfEngine.closeDocumentAsync()
    }

    override fun onCleared() {
        super.onCleared()
        openDocumentJob?.cancel()
        prefetchJob?.cancel()
        dimensionScanJob?.cancel()
        documentJob?.cancel()
        bookmarksJob?.cancel()
        annotationsJob?.cancel()
        progressSaveJob?.cancel()
        indexObserveJob?.cancel()
        pendingDelayedRerunJob?.cancel()
        structureJob?.cancel()
        pageTextJob?.cancel()
        inPdfSearchJob?.cancel()
        pdfEngine.closeDocumentAsync()
    }

    companion object {
        fun factory(
            libraryRepository: PdfLibraryRepository,
            preferencesRepository: PreferencesRepository,
            pdfEngine: PdfEngine,
            searchIndexRepository: SearchIndexRepository? = null,
            searchIndexer: SearchIndexer? = null,
            textEngine: PdfTextEngine? = null,
            appContext: Context? = null,
            initialPreferences: UserPreferences = UserPreferences()
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReaderViewModel(
                    libraryRepository = libraryRepository,
                    preferencesRepository = preferencesRepository,
                    pdfEngine = pdfEngine,
                    searchIndexRepository = searchIndexRepository,
                    searchIndexer = searchIndexer,
                    textEngine = textEngine,
                    appContext = appContext,
                    initialPreferences = initialPreferences
                ) as T
            }
        }
    }
}
