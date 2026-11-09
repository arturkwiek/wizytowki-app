package com.wizytowki.ui.scan

import androidx.lifecycle.ViewModel
import com.wizytowki.scan.ScanSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** Scanner backends compared in the Etap 1 prototype (issue #7). */
enum class ScannerMethod { MLKIT_DOCUMENT_SCANNER, CAMERAX }

data class ScanPrototypeState(
    val method: ScannerMethod? = null,
    val session: ScanSession = ScanSession(),
    val error: String? = null,
)

@HiltViewModel
class ScanPrototypeViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(ScanPrototypeState())
    val state: StateFlow<ScanPrototypeState> = _state.asStateFlow()

    fun chooseMethod(method: ScannerMethod) = _state.update { ScanPrototypeState(method = method) }
    fun onCaptured(uri: String) = _state.update { it.copy(session = it.session.withCapture(uri), error = null) }
    fun onError(message: String) = _state.update { it.copy(error = message) }
    fun skipBack() = _state.update { it.copy(session = it.session.skipBack()) }
    fun retakeFront() = _state.update { it.copy(session = it.session.retakeFront()) }
    fun retakeBack() = _state.update { it.copy(session = it.session.retakeBack()) }
    fun reset() = _state.update { ScanPrototypeState() }
}
