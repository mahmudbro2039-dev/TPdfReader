package com.example.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
import com.example.data.pdf.PdfTextExtractor
import com.example.data.pdf.TocEntry
import com.example.data.repository.PdfLibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ReaderLoadState {
    data object Loading : ReaderLoadState
    data object Ready : ReaderLoadState
    data class Error(val message: String) : ReaderLoadState
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
    val folderName: String = "Documents",
    val fileSizeBytes: Long = 0L,
    val readingStatus: ReadingStatus = ReadingStatus.READING,
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
    val extractedTextPages: List<String> = emptyList(),
    val isExtractingText: Boolean = false
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
    private val pdfTextExtractor: PdfTextExtractor
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserPreferences()
        )

    private var documentJob: Job? = null
    private var bookmarksJob: Job? = null
    private var annotationsJob: Job? = null
    private var progressSaveJob: Job? = null
    private var textExtractionJob: Job? = null
    private var inPdfSearchJob: Job? = null
    private var openDocumentJob: Job? = null
    private var prefetchJob: Job? = null
    private var dimensionScanJob: Job? = null
    private var activeLoadingInitialPage: Int? = null
    private var lastRenderTargetWidthPx: Int = 0
    private var loadedTextUri: String? = null
    private var extractingTextUri: String? = null
    private var isExtractingTextDelayed: Boolean = false

    fun openDocument(uriString: String, initialPageOverride: Int? = null) {
        if (uriString.isBlank()) return
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
        textExtractionJob?.cancel()
        inPdfSearchJob?.cancel()
        activeLoadingInitialPage = initialPageOverride
        extractingTextUri = null
        loadedTextUri = null
        isExtractingTextDelayed = false

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
                pageFitPolicy = currentPrefs.defaultPageFitPolicy,
                colorMode = currentPrefs.defaultPdfColorMode,
                layoutMode = currentPrefs.defaultLayoutMode,
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
                extractedTextPages = emptyList(),
                isExtractingText = false,
                tocEntries = emptyList(),
                documentLinks = emptyList()
            )
        }

        openDocumentJob = viewModelScope.launch {
            val (prefs, savedDoc, result) = coroutineScope {
                val prefsDeferred = async { preferencesRepository.preferencesFlow.first() }
                val savedDocDeferred = async { libraryRepository.getDocument(uriString) }
                val engineDeferred = async {
                    pdfEngine.openDocument(uriString, initialPageOverride ?: 0)
                }
                Triple(prefsDeferred.await(), savedDocDeferred.await(), engineDeferred.await())
            }

            result.fold(
                onSuccess = { metadata ->
                    val maxIndex = (metadata.pageCount - 1).coerceAtLeast(0)
                    val startPage = (initialPageOverride ?: savedDoc?.currentPage ?: 0).coerceIn(0, maxIndex)
                    val resolvedTitle = savedDoc?.title?.takeIf { it.isNotBlank() }
                        ?: libraryRepository.resolveDocumentTitle(uriString).ifBlank { "PDF Document" }

                    _uiState.update {
                        it.copy(
                            loadState = ReaderLoadState.Ready,
                            uriString = uriString,
                            documentTitle = resolvedTitle,
                            folderName = savedDoc?.folderName ?: "Documents",
                            fileSizeBytes = savedDoc?.fileSizeBytes ?: 0L,
                            readingStatus = savedDoc?.status ?: ReadingStatus.READING,
                            pageCount = metadata.pageCount,
                            currentPage = startPage,
                            pageDimensions = metadata.pageDimensions,
                            zoomScale = (savedDoc?.zoomScale ?: 1f).coerceIn(1f, 4f),
                            hideMargins = prefs.hideMarginsByDefault,
                            pageFitPolicy = prefs.defaultPageFitPolicy,
                            colorMode = prefs.defaultPdfColorMode,
                            layoutMode = prefs.defaultLayoutMode,
                            isRtl = prefs.rtlReading,
                            incognitoMode = prefs.incognitoMode,
                            autoScrollSpeed = prefs.autoScrollSpeed,
                            visitedPagesHistory = listOf(startPage),
                            pendingScrollTargetPage = startPage
                        )
                    }

                    observeDocumentBookmarksAndAnnotations(uriString)
                    schedulePrefetchAround(startPage)
                    scheduleProgressPersist()
                    scheduleDimensionScan(uriString, metadata.pageCount)
                    // Pre-extract text in background for live search, Table of Contents, and Document Links
                    extractTextAndStructureInBackground(uriString, metadata.pageCount, immediate = false)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            loadState = ReaderLoadState.Error(
                                error.localizedMessage ?: "The PDF file could not be opened. It may be moved, corrupted, or password-protected."
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
                val docTitle = doc?.title?.trim()
                if (!docTitle.isNullOrBlank()) {
                    _uiState.update { state ->
                        if (state.uriString == uriString && state.documentTitle != docTitle) {
                            state.copy(
                                documentTitle = docTitle,
                                folderName = doc.folderName,
                                fileSizeBytes = doc.fileSizeBytes
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

    private fun extractTextAndStructureInBackground(
        uriString: String,
        pageCount: Int,
        immediate: Boolean = false
    ) {
        if (textExtractionJob?.isActive == true && extractingTextUri == uriString) {
            if (!immediate || !isExtractingTextDelayed) return
        }
        textExtractionJob?.cancel()
        extractingTextUri = uriString
        isExtractingTextDelayed = !immediate
        textExtractionJob = viewModelScope.launch {
            if (!immediate) {
                delay(250L)
            }
            isExtractingTextDelayed = false
            _uiState.update { it.copy(isExtractingText = true) }
            val pages = pdfTextExtractor.extractPagesText(uriString, pageCount)
            val toc = pdfTextExtractor.buildTableOfContents(pages, pageCount)
            val links = pdfTextExtractor.extractDocumentLinks(pages, pageCount)
            if (_uiState.value.uriString != uriString) return@launch
            loadedTextUri = uriString
            extractingTextUri = null
            _uiState.update {
                it.copy(
                    extractedTextPages = pages,
                    tocEntries = toc,
                    documentLinks = links,
                    isExtractingText = false
                )
            }
            val pendingQuery = _uiState.value.searchQuery
            if (_uiState.value.isSearchBarVisible && pendingQuery.isNotBlank()) {
                runInPdfSearch(pendingQuery, pages, jumpToFirst = true)
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
            val candidates = listOf(centerPage + 1, centerPage + 2, centerPage - 1, centerPage + 3)
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

    fun onPageScrolled(newPage: Int) {
        val state = _uiState.value
        val clamped = newPage.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
        if (clamped == state.currentPage) return
        val updatedVisited = if (state.visitedPagesHistory.lastOrNull() == clamped) {
            state.visitedPagesHistory
        } else {
            (state.visitedPagesHistory + clamped).takeLast(35)
        }
        _uiState.update {
            it.copy(
                currentPage = clamped,
                visitedPagesHistory = updatedVisited
            )
        }
        schedulePrefetchAround(clamped)
        scheduleProgressPersist()
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
                pendingScrollTargetPage = clamped
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
        val clamped = scale.coerceIn(1f, 4f)
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
        _uiState.update { it.copy(pageFitPolicy = policy) }
        viewModelScope.launch {
            preferencesRepository.setDefaultPageFitPolicy(policy)
        }
    }

    fun toggleIncognitoMode() {
        val next = !_uiState.value.incognitoMode
        _uiState.update { it.copy(incognitoMode = next) }
        viewModelScope.launch {
            preferencesRepository.setIncognitoMode(next)
        }
    }

    // Live In-Document Search with wrapping Next / Previous navigation (MJ PDF v3.1)
    fun toggleSearchBar() {
        if (_uiState.value.isSearchBarVisible) {
            closeSearchBar()
        } else {
            _uiState.update {
                it.copy(
                    isSearchBarVisible = true,
                    isChromeVisible = true
                )
            }
            ensureTextExtracted()
        }
    }

    fun closeSearchBar() {
        inPdfSearchJob?.cancel()
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
        _uiState.update { it.copy(searchQuery = query) }
        val trimmed = query.trim()
        val pages = _uiState.value.extractedTextPages
        if (trimmed.isEmpty()) {
            _uiState.update {
                it.copy(
                    searchMatches = emptyList(),
                    activeSearchMatchIndex = 0
                )
            }
            return
        }
        if (pages.isEmpty()) {
            _uiState.update {
                it.copy(
                    searchMatches = emptyList(),
                    activeSearchMatchIndex = 0
                )
            }
            ensureTextExtracted()
            return
        }
        runInPdfSearch(query, pages, jumpToFirst = true)
    }

    private fun runInPdfSearch(query: String, pages: List<String>, jumpToFirst: Boolean) {
        inPdfSearchJob?.cancel()
        inPdfSearchJob = viewModelScope.launch {
            val matches = withContext(Dispatchers.Default) {
                findInPdfMatches(pages, query)
            }
            val latest = _uiState.value
            if (!latest.isSearchBarVisible || latest.searchQuery != query) return@launch

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

    private fun findInPdfMatches(pages: List<String>, query: String): List<InPdfSearchMatch> {
        val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val pattern = Regex(
            tokens.joinToString("\\s+") { Regex.escape(it) },
            RegexOption.IGNORE_CASE
        )

        val matches = mutableListOf<InPdfSearchMatch>()
        for ((pageIndex, pageText) in pages.withIndex()) {
            if (matches.size >= 150) break
            var ordinal = 0
            for (result in pattern.findAll(pageText)) {
                if (matches.size >= 150) break
                val startIdx = result.range.first
                val endIdx = result.range.last + 1
                val snippetStart = (startIdx - 32).coerceAtLeast(0)
                val snippetEnd = (endIdx + 48).coerceAtMost(pageText.length)
                val snippet = pageText.substring(snippetStart, snippetEnd)
                    .replace(Regex("\\s+"), " ")
                    .trim()
                matches.add(
                    InPdfSearchMatch(
                        pageIndex = pageIndex,
                        snippet = "…$snippet…",
                        matchOrdinalOnPage = ordinal
                    )
                )
                ordinal++
            }
        }
        return matches
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
        _uiState.update { it.copy(colorMode = modes[nextIndex]) }
    }

    fun setColorMode(mode: PdfColorMode) {
        _uiState.update { it.copy(colorMode = mode) }
    }

    fun setLayoutMode(mode: ScrollLayoutMode) {
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
                    customTitle = "Page ${state.currentPage + 1}"
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
            libraryRepository.addAnnotation(
                documentUri = state.uriString,
                documentTitle = state.documentTitle,
                pageIndex = state.currentPage,
                type = AnnotationType.SIGNATURE,
                quotedText = signerLabel.ifBlank { "Handwritten Vector Signature" },
                noteText = "Signed on Page ${state.currentPage + 1}",
                colorHex = "#B91C1C",
                signatureVectorData = signatureVectorData
            )
        }
    }

    fun deleteAnnotation(annotationId: Long) {
        viewModelScope.launch {
            libraryRepository.deleteAnnotation(annotationId)
        }
    }

    fun ensureTextExtracted() {
        val state = _uiState.value
        if (state.uriString.isBlank() || loadedTextUri == state.uriString) return
        extractTextAndStructureInBackground(state.uriString, state.pageCount, immediate = true)
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
            if (state.uriString.isNotBlank() && state.pageCount > 0 && !state.incognitoMode) {
                libraryRepository.updateProgress(
                    uriString = state.uriString,
                    currentPage = state.currentPage,
                    pageCount = state.pageCount,
                    zoomScale = state.zoomScale,
                    incognitoMode = state.incognitoMode
                )
            }
        }
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
        textExtractionJob?.cancel()
        inPdfSearchJob?.cancel()
    }

    companion object {
        fun factory(
            libraryRepository: PdfLibraryRepository,
            preferencesRepository: PreferencesRepository,
            pdfEngine: PdfEngine,
            pdfTextExtractor: PdfTextExtractor
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReaderViewModel(
                    libraryRepository = libraryRepository,
                    preferencesRepository = preferencesRepository,
                    pdfEngine = pdfEngine,
                    pdfTextExtractor = pdfTextExtractor
                ) as T
            }
        }
    }
}
