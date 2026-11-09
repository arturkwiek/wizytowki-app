package com.wizytowki.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wizytowki.scan.QualityChecker
import com.wizytowki.scan.QualityIssue
import com.wizytowki.scan.ScanSession
import com.wizytowki.scan.ScanSession.Step
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CardSide { FRONT, BACK }

/** Shown when a just-scanned side looks unreadable (WF-04); the user retakes it or keeps it. */
data class QualityWarning(val side: CardSide, val issues: Set<QualityIssue>)

data class ScanUiState(
    val session: ScanSession = ScanSession(),
    /** Scanner could not start (e.g. Google Play services missing). */
    val scannerUnavailable: Boolean = false,
    /** Front scanner is opened automatically only once per session (UI-01: minimum taps). */
    val frontAutoLaunched: Boolean = false,
    /** Quality analysis of the last scan is running. */
    val checkingQuality: Boolean = false,
    val qualityWarning: QualityWarning? = null,
    /** One-shot event: both sides are ready, go to the preview screen. */
    val navigateToPreview: Boolean = false,
)

/** Front/back scanning flow (WF-01, WF-02, WF-04, WF-05, UI-02, UI-05). */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val qualityChecker: QualityChecker,
) : ViewModel() {
    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    fun onFrontAutoLaunched() = _state.update { it.copy(frontAutoLaunched = true) }

    fun onScanned(uri: String) {
        val side = when (_state.value.session.step) {
            Step.FRONT -> CardSide.FRONT
            Step.BACK -> CardSide.BACK
            Step.DONE -> return
        }
        _state.update {
            it.copy(
                session = it.session.withCapture(uri),
                scannerUnavailable = false,
                checkingQuality = true,
                qualityWarning = null,
            )
        }
        viewModelScope.launch {
            val issues = qualityChecker.check(uri)?.issues.orEmpty()
            _state.update { current ->
                // Ignore a stale result if the side was retaken meanwhile.
                val stillCurrent = when (side) {
                    CardSide.FRONT -> current.session.frontUri == uri
                    CardSide.BACK -> current.session.backUri == uri
                }
                if (!stillCurrent) return@update current
                val warning = issues.takeIf { it.isNotEmpty() }?.let { QualityWarning(side, it) }
                current.copy(
                    checkingQuality = false,
                    qualityWarning = warning,
                    navigateToPreview = warning == null && current.session.step == Step.DONE,
                )
            }
        }
    }

    /** "Użyj mimo to": keep the photo and continue. */
    fun acceptQualityWarning() = _state.update {
        it.copy(qualityWarning = null, navigateToPreview = it.session.step == Step.DONE)
    }

    /** "Ponów zdjęcie" from the quality dialog; the caller then reopens the scanner. */
    fun retakeAfterWarning() {
        when (_state.value.qualityWarning?.side) {
            CardSide.FRONT -> retakeFront()
            CardSide.BACK -> retakeBack()
            null -> Unit
        }
    }

    fun skipBack() = _state.update { current ->
        val session = current.session.skipBack()
        current.copy(
            session = session,
            navigateToPreview = current.session.step != Step.DONE && session.step == Step.DONE,
        )
    }

    fun retakeFront() = _state.update {
        it.copy(session = it.session.retakeFront(), qualityWarning = null, checkingQuality = false)
    }

    fun retakeBack() = _state.update {
        it.copy(session = it.session.retakeBack(), qualityWarning = null, checkingQuality = false)
    }

    fun onScannerUnavailable() = _state.update { it.copy(scannerUnavailable = true) }

    fun onNavigatedToPreview() = _state.update { it.copy(navigateToPreview = false) }
}
