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
import com.example.data.pdf.PdfEngine
import com.example.data.repository.PdfLibraryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val libraryRepository: PdfLibraryRepository,
    private val pdfEngine: PdfEngine
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserPreferences()
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
        onDone()
    }

    fun clearLibraryHistory(onDone: () -> Unit) = viewModelScope.launch {
        libraryRepository.clearAllHistory()
        onDone()
    }

    companion object {
        fun factory(
            preferencesRepository: PreferencesRepository,
            libraryRepository: PdfLibraryRepository,
            pdfEngine: PdfEngine
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(preferencesRepository, libraryRepository, pdfEngine) as T
            }
        }
    }
}
