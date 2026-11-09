package com.wizytowki.ui.scan

import androidx.lifecycle.ViewModel
import com.wizytowki.scan.ScanSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ScanUiState(
    val session: ScanSession = ScanSession(),
    /** Scanner could not start (e.g. Google Play services missing). */
    val scannerUnavailable: Boolean = false,
    /** Front scanner is opened automatically only once per session (UI-01: minimum taps). */
    val frontAutoLaunched: Boolean = false,
    /** One-shot event: both sides are ready, go to the preview screen. */
    val navigateToPreview: Boolean = false,
)

/** Front/back scanning flow (WF-01, WF-02, WF-05, UI-05). */
@HiltViewModel
class ScanViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    fun onFrontAutoLaunched() = _state.update { it.copy(frontAutoLaunched = true) }

    fun onScanned(uri: String) = updateSession { it.withCapture(uri) }

    fun skipBack() = updateSession { it.skipBack() }

    fun retakeFront() = updateSession { it.retakeFront() }

    fun retakeBack() = updateSession { it.retakeBack() }

    fun onScannerUnavailable() = _state.update { it.copy(scannerUnavailable = true) }

    fun onNavigatedToPreview() = _state.update { it.copy(navigateToPreview = false) }

    private fun updateSession(change: (ScanSession) -> ScanSession) = _state.update { current ->
        val session = change(current.session)
        val becameDone = current.session.step != ScanSession.Step.DONE && session.step == ScanSession.Step.DONE
        current.copy(
            session = session,
            scannerUnavailable = false,
            navigateToPreview = current.navigateToPreview || becameDone,
        )
    }
}
