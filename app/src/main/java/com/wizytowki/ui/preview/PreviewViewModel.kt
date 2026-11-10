package com.wizytowki.ui.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wizytowki.ocr.CardOcr
import com.wizytowki.ocr.CardRecognizer
import com.wizytowki.scan.DraftStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PreviewUiState {
    data object Analyzing : PreviewUiState

    /** No finished scan to analyze (e.g. the draft was discarded meanwhile). */
    data object NoScan : PreviewUiState

    data object Failed : PreviewUiState

    data class Ready(val ocr: CardOcr) : PreviewUiState
}

/**
 * Preview screen (REQUIREMENTS 8a, screen 4): analyzes the finished scan from the draft.
 * Etap 2 shows the recognized text; field editing arrives in Etap 3 (WF-20).
 */
@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val draftStore: DraftStore,
    private val cardRecognizer: CardRecognizer,
) : ViewModel() {
    private val _state = MutableStateFlow<PreviewUiState>(PreviewUiState.Analyzing)
    val state: StateFlow<PreviewUiState> = _state.asStateFlow()

    init {
        analyze()
    }

    fun retry() = analyze()

    private fun analyze() {
        _state.value = PreviewUiState.Analyzing
        viewModelScope.launch {
            val session = draftStore.draft.value ?: draftStore.load()
            val front = session?.frontUri
            if (front == null) {
                _state.value = PreviewUiState.NoScan
                return@launch
            }
            _state.value = try {
                PreviewUiState.Ready(cardRecognizer.recognize(front, session.backUri))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PreviewUiState.Failed
            }
        }
    }
}
