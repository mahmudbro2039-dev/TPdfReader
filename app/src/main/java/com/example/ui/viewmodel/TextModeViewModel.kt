package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferencesRepository
import com.example.data.local.UserPreferences
import com.example.data.model.TextFontChoice
import com.example.data.model.TextModeTheme
import com.example.data.text.OpenFailure
import com.example.data.text.OpenResult
import com.example.data.text.PdfTextEngine
import com.example.data.text.TextNormalizer
import com.example.data.text.TextQuality
import com.example.data.text.TextSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

sealed interface TextModeDocState {
    data object Loading : TextModeDocState
    data class Error(val failure: OpenFailure) : TextModeDocState
    data class Ready(val pageCount: Int) : TextModeDocState
}

sealed interface PageUiState {
    data object Loading : PageUiState
    data class Ready(val text: String) : PageUiState
    data class Unreadable(val quality: TextQuality) : PageUiState
    data object Failed : PageUiState
}

class TextModeViewModel(
    private val uriString: String,
    private val textEngine: PdfTextEngine,
    private val preferencesRepository: PreferencesRepository,
    initialPreferences: UserPreferences,
) : ViewModel() {

    private val _docState = MutableStateFlow<TextModeDocState>(TextModeDocState.Loading)
    val docState: StateFlow<TextModeDocState> = _docState.asStateFlow()

    private val _pages = MutableStateFlow<Map<Int, PageUiState>>(emptyMap())
    val pages: StateFlow<Map<Int, PageUiState>> = _pages.asStateFlow()

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = initialPreferences,
        )

    private var session: TextSession? = null
    private val inFlightJobs = mutableMapOf<Int, Job>()
    private var lastRequestedPage: Int = 0

    init {
        openSession()
    }

    private fun openSession() {
        if (uriString.isBlank()) {
            _docState.value = TextModeDocState.Error(OpenFailure.IO)
            return
        }

        viewModelScope.launch {
            _docState.value = TextModeDocState.Loading
            when (val result = textEngine.open(Uri.parse(uriString))) {
                is OpenResult.Ok -> {
                    if (!isActive) {
                        result.session.close()
                        return@launch
                    }
                    session = result.session
                    _docState.value = TextModeDocState.Ready(result.session.pageCount)
                }

                is OpenResult.Error -> {
                    _docState.value = TextModeDocState.Error(result.failure)
                }
            }
        }
    }

    fun requestPage(index: Int) {
        val activeSession = session ?: return
        val totalPages = activeSession.pageCount
        if (totalPages <= 0 || index !in 0 until totalPages) return

        lastRequestedPage = index
        val windowStart = (index - 1).coerceAtLeast(0)
        val windowEnd = (index + 2).coerceAtMost(totalPages - 1)

        for (pageIdx in windowStart..windowEnd) {
            val current = _pages.value[pageIdx]
            if (current != null && current !is PageUiState.Loading) continue
            if (inFlightJobs[pageIdx]?.isActive == true) continue

            _pages.update { it + (pageIdx to PageUiState.Loading) }
            val job = viewModelScope.launch {
                try {
                    val pageText = activeSession.page(pageIdx)
                    val state = when (pageText.quality) {
                        TextQuality.OK -> {
                            val reflowed = withContext(Dispatchers.Default) {
                                TextNormalizer.reflow(pageText.text)
                            }
                            if (reflowed.isBlank()) {
                                PageUiState.Unreadable(TextQuality.EMPTY)
                            } else {
                                PageUiState.Ready(reflowed)
                            }
                        }

                        else -> PageUiState.Unreadable(pageText.quality)
                    }
                    putWithEviction(pageIdx, state)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    putWithEviction(pageIdx, PageUiState.Failed)
                } finally {
                    inFlightJobs.remove(pageIdx)
                }
            }
            inFlightJobs[pageIdx] = job
        }
    }

    private fun putWithEviction(pageIndex: Int, state: PageUiState) {
        _pages.update { existing ->
            val mutable = existing.toMutableMap()
            mutable[pageIndex] = state
            while (mutable.size > MAX_CACHED_PAGES) {
                val victim = mutable.keys
                    .filter { it != pageIndex && it != lastRequestedPage }
                    .maxByOrNull { abs(it - lastRequestedPage) }
                    ?: break
                mutable.remove(victim)
            }
            mutable
        }
    }

    fun setTextModeTheme(theme: TextModeTheme) {
        viewModelScope.launch { preferencesRepository.setTextModeTheme(theme) }
    }

    fun setTextModeFontChoice(choice: TextFontChoice) {
        viewModelScope.launch { preferencesRepository.setTextModeFontChoice(choice) }
    }

    fun setTextModeFontSize(sizeSp: Float) {
        viewModelScope.launch { preferencesRepository.setTextModeFontSize(sizeSp) }
    }

    fun setTextModeLineHeight(multiplier: Float) {
        viewModelScope.launch { preferencesRepository.setTextModeLineHeight(multiplier) }
    }

    override fun onCleared() {
        inFlightJobs.values.forEach { it.cancel() }
        inFlightJobs.clear()
        session?.close()
        session = null
        super.onCleared()
    }

    companion object {
        private const val MAX_CACHED_PAGES = 30

        fun factory(
            uriString: String,
            textEngine: PdfTextEngine,
            preferencesRepository: PreferencesRepository,
            initialPreferences: UserPreferences,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TextModeViewModel(
                    uriString = uriString,
                    textEngine = textEngine,
                    preferencesRepository = preferencesRepository,
                    initialPreferences = initialPreferences,
                ) as T
            }
        }
    }
}
