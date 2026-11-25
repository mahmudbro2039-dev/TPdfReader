package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferencesRepository
import com.example.data.local.UserPreferences
import com.example.data.model.AppThemeMode
import com.example.data.model.PageFitPolicy
import com.example.data.model.PdfColorMode
import com.example.data.model.RenderQuality
import com.example.data.model.ScrollLayoutMode
import com.example.data.pdf.DevicePdfScanner
import com.example.data.pdf.PdfEngine
import com.example.data.pdf.PdfScanProgress
import com.example.data.repository.PdfLibraryRepository
import com.example.data.search.PauseReason
import com.example.data.search.SearchIndexRepository
import com.example.data.search.SearchIndexer
import com.example.data.search.db.IndexStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchIndexUiStatus(
    val doneCount: Int = 0,
    val totalCount: Int = 0,
    val pauseReason: PauseReason? = null
)

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val libraryRepository: PdfLibraryRepository,
    private val pdfEngine: PdfEngine,
    private val devicePdfScanner: DevicePdfScanner? = null,
    private val searchIndexRepository: SearchIndexRepository? = null,
    private val searchIndexer: SearchIndexer? = null,
    private val appScope: CoroutineScope? = null,
    initialPreferences: UserPreferences = UserPreferences()
) : ViewModel() {

    val scanProgress: StateFlow<PdfScanProgress> =
        devicePdfScanner?.progress ?: MutableStateFlow(PdfScanProgress())

    val searchIndexStatus: StateFlow<SearchIndexUiStatus> = combine(
        libraryRepository.allDocuments,
        searchIndexRepository?.observeAllDocs() ?: flowOf(emptyList()),
        searchIndexer?.pauseReason ?: MutableStateFlow(null)
    ) { docs, indexedDocs, pauseReason ->
        val libraryUris = docs.mapTo(HashSet()) { it.uriString }
        val doneCount = indexedDocs.count {
            it.uriString in libraryUris && it.status == IndexStatus.DONE
        }
        SearchIndexUiStatus(
            doneCount = doneCount,
            totalCount = docs.size,
            pauseReason = pauseReason
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchIndexUiStatus()
    )

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = initialPreferences
        )

    fun setAppThemeMode(mode: AppThemeMode) = viewModelScope.launch {
        preferencesRepository.setAppThemeMode(mode)
    }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.setDynamicColor(enabled)
    }

    fun setDefaultPdfColorMode(mode: PdfColorMode) = viewModelScope.launch {
        preferencesRepository.setDefaultPdfColorMode(mode)
    }

    fun setDefaultLayoutMode(mode: ScrollLayoutMode) = viewModelScope.launch {
        preferencesRepository.setDefaultLayoutMode(mode)
    }

    fun setDefaultPageFitPolicy(policy: PageFitPolicy) = viewModelScope.launch {
        preferencesRepository.setDefaultPageFitPolicy(policy)
    }

    fun setHideMarginsByDefault(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.setHideMarginsByDefault(enabled)
    }

    fun setIncognitoMode(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.setIncognitoMode(enabled)
    }

    fun setRtlReading(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.setRtlReading(enabled)
    }

    fun setKeepScreenOn(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.setKeepScreenOn(enabled)
    }

    fun setVolumeKeyNavigation(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.setVolumeKeyNavigation(enabled)
    }

    fun setShowPageIndicatorPill(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.setShowPageIndicatorPill(enabled)
    }

    fun setShowEdgeShortcutButtons(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.setShowEdgeShortcutButtons(enabled)
    }

    fun setPageGapSpacing(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.setPageGapSpacing(enabled)
    }

    fun setRenderQuality(quality: RenderQuality) = viewModelScope.launch {
        preferencesRepository.setRenderQuality(quality)
    }

    fun clearRenderCache(onDone: () -> Unit) = viewModelScope.launch {
        pdfEngine.clearMemoryAndDiskCache()
        com.example.ui.components.ThumbnailCache.clear()
        onDone()
    }

    fun clearLibraryHistory(onDone: () -> Unit) = viewModelScope.launch {
        libraryRepository.clearAllHistory()
        onDone()
    }

    fun rescanDevicePdfs(onDone: (PdfScanProgress) -> Unit) {
        val scanner = devicePdfScanner ?: return
        viewModelScope.launch {
            val result = scanner.scanDevicePdfs()
            libraryRepository.refreshAvailability()
            onDone(result)
        }
    }

    fun rebuildSearchIndex() {
        val indexer = searchIndexer ?: return
        val scope = appScope ?: return
        scope.launch {
            indexer.rebuildAll()
        }
    }

    companion object {
        fun factory(
            preferencesRepository: PreferencesRepository,
            libraryRepository: PdfLibraryRepository,
            pdfEngine: PdfEngine,
            devicePdfScanner: DevicePdfScanner? = null,
            searchIndexRepository: SearchIndexRepository? = null,
            searchIndexer: SearchIndexer? = null,
            appScope: CoroutineScope? = null,
            initialPreferences: UserPreferences = UserPreferences()
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    preferencesRepository = preferencesRepository,
                    libraryRepository = libraryRepository,
                    pdfEngine = pdfEngine,
                    devicePdfScanner = devicePdfScanner,
                    searchIndexRepository = searchIndexRepository,
                    searchIndexer = searchIndexer,
                    appScope = appScope,
                    initialPreferences = initialPreferences
                ) as T
            }
        }
    }
}
