package com.wizytowki.ui.scan

import androidx.lifecycle.SavedStateHandle
import com.wizytowki.scan.DraftStore
import com.wizytowki.scan.QualityIssue
import com.wizytowki.scan.QualityReport
import com.wizytowki.scan.ScanSession
import com.wizytowki.scan.ScanSession.Step
import com.wizytowki.scan.hasContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** In-memory draft store; "imported" images get a `stored:` prefix like a copy into app storage. */
private class FakeDraftStore(initial: ScanSession? = null) : DraftStore {
    var saved: ScanSession? = initial
    var failImport = false
    private val _draft = MutableStateFlow(initial)
    override val draft: StateFlow<ScanSession?> = _draft

    override suspend fun load(): ScanSession? = saved?.takeIf { it.hasContent }.also { _draft.value = it }

    override suspend fun importImage(sourceUri: String, prefix: String): String {
        if (failImport) throw java.io.IOException("disk full")
        return "stored:$sourceUri"
    }

    override suspend fun save(session: ScanSession) {
        saved = session
        _draft.value = session.takeIf { it.hasContent }
    }

    override suspend fun clear() {
        saved = null
        _draft.value = null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {
    /** Stored URIs listed here get these issues; everything else is fine. */
    private val badPhotos = mutableMapOf<String, Set<QualityIssue>>()

    /** Stored URIs listed here wait for the gate before the quality result arrives. */
    private val pending = mutableMapOf<String, CompletableDeferred<Unit>>()

    private var store = FakeDraftStore()
    private lateinit var viewModel: ScanViewModel
    private val state get() = viewModel.state.value

    private fun newViewModel(args: Map<String, Any?> = emptyMap()): ScanViewModel = ScanViewModel(
        qualityChecker = { uri ->
            pending[uri]?.await()
            QualityReport(sharpness = 100.0, brightness = 150.0, clippedShare = 0.0, issues = badPhotos[uri].orEmpty())
        },
        draftStore = store,
        savedState = SavedStateHandle(args),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = newViewModel()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun frontThenBack_requestsPreviewOnce() {
        viewModel.onScanned("front")
        assertFalse(state.navigateToPreview)
        assertEquals(Step.BACK, state.session.step)

        viewModel.onScanned("back")
        assertTrue(state.navigateToPreview)

        viewModel.onNavigatedToPreview()
        assertFalse(state.navigateToPreview)
    }

    @Test
    fun scannedImages_areStoredInAppStorage() {
        viewModel.onScanned("front")
        assertEquals("stored:front", state.session.frontUri)
    }

    @Test
    fun skipBack_requestsPreview() {
        viewModel.onScanned("front")
        viewModel.skipBack()
        assertEquals(Step.DONE, state.session.step)
        assertTrue(state.navigateToPreview)
    }

    @Test
    fun retakeFrontAfterDone_keepsBack_andRequestsPreviewAgain() {
        viewModel.onScanned("front")
        viewModel.onScanned("back")
        viewModel.onNavigatedToPreview()

        viewModel.retakeFront()
        assertEquals(Step.FRONT, state.session.step)
        assertEquals("stored:back", state.session.backUri)
        assertFalse(state.navigateToPreview)

        viewModel.onScanned("front2")
        assertTrue(state.navigateToPreview)
        assertEquals("stored:front2", state.session.frontUri)
    }

    @Test
    fun scannerUnavailable_isClearedBySuccessfulScan() {
        viewModel.onScannerUnavailable()
        assertTrue(state.scannerUnavailable)
        viewModel.onScanned("front")
        assertFalse(state.scannerUnavailable)
    }

    @Test
    fun frontAutoLaunch_isRememberedForSession() {
        assertFalse(state.frontAutoLaunched)
        viewModel.onFrontAutoLaunched()
        assertTrue(state.frontAutoLaunched)
        viewModel.retakeFront()
        assertTrue(state.frontAutoLaunched)
    }

    @Test
    fun blurryFront_showsWarning_retakeClearsFrontOnly() {
        badPhotos["stored:front"] = setOf(QualityIssue.BLURRY)
        viewModel.onScanned("front")
        assertEquals(QualityWarning(CardSide.FRONT, setOf(QualityIssue.BLURRY)), state.qualityWarning)

        viewModel.retakeAfterWarning()
        assertNull(state.qualityWarning)
        assertEquals(Step.FRONT, state.session.step)
    }

    @Test
    fun badBack_blocksPreviewUntilAccepted() {
        badPhotos["stored:back"] = setOf(QualityIssue.TOO_DARK)
        viewModel.onScanned("front")
        viewModel.onScanned("back")
        assertEquals(CardSide.BACK, state.qualityWarning?.side)
        assertFalse(state.navigateToPreview)

        viewModel.acceptQualityWarning()
        assertNull(state.qualityWarning)
        assertTrue(state.navigateToPreview)
        assertEquals("stored:back", state.session.backUri)
    }

    @Test
    fun badBack_retake_keepsFront() {
        badPhotos["stored:back"] = setOf(QualityIssue.TOO_BRIGHT)
        viewModel.onScanned("front")
        viewModel.onScanned("back")
        viewModel.retakeAfterWarning()
        assertEquals(Step.BACK, state.session.step)
        assertEquals("stored:front", state.session.frontUri)
    }

    @Test
    fun previewWaitsForQualityCheck() {
        val gate = CompletableDeferred<Unit>().also { pending["stored:back"] = it }
        viewModel.onScanned("front")
        viewModel.onScanned("back")
        assertTrue(state.checkingQuality)
        assertFalse(state.navigateToPreview)

        gate.complete(Unit)
        assertFalse(state.checkingQuality)
        assertTrue(state.navigateToPreview)
    }

    @Test
    fun staleQualityResult_isIgnoredAfterRetake() {
        badPhotos["stored:front"] = setOf(QualityIssue.BLURRY)
        val gate = CompletableDeferred<Unit>().also { pending["stored:front"] = it }
        viewModel.onScanned("front")
        viewModel.retakeFront()

        gate.complete(Unit)
        assertNull(state.qualityWarning)
        assertEquals(Step.FRONT, state.session.step)
    }

    // --- Draft (UI-09) ---

    @Test
    fun everyStep_isSavedAsDraft() {
        viewModel.onScanned("front")
        assertEquals(ScanSession(frontUri = "stored:front"), store.saved)
        viewModel.skipBack()
        assertEquals(ScanSession(frontUri = "stored:front", backSkipped = true), store.saved)
        viewModel.retakeBack()
        assertEquals(ScanSession(frontUri = "stored:front"), store.saved)
    }

    @Test
    fun newScan_discardsOldDraft() {
        store = FakeDraftStore(ScanSession(frontUri = "stored:old"))
        viewModel = newViewModel()
        assertNull(store.saved)
        assertEquals(Step.FRONT, state.session.step)
        assertFalse(state.frontAutoLaunched)
    }

    @Test
    fun resume_restoresDraft_withoutAutoOpeningScanner() {
        store = FakeDraftStore(ScanSession(frontUri = "stored:old"))
        viewModel = newViewModel(mapOf(ScanViewModel.ARG_RESUME to true))
        assertEquals("stored:old", state.session.frontUri)
        assertEquals(Step.BACK, state.session.step)
        assertTrue(state.frontAutoLaunched)
        assertFalse(state.busy)
    }

    @Test
    fun resumeWithoutDraft_startsEmpty() {
        viewModel = newViewModel(mapOf(ScanViewModel.ARG_RESUME to true))
        assertEquals(ScanSession(), state.session)
    }

    @Test
    fun recreatedAfterProcessDeath_resumesInsteadOfClearing() {
        val savedState = SavedStateHandle()
        val first = ScanViewModel({ null }, store, savedState)
        first.onScanned("front")

        // The system restores SavedStateHandle but not the ViewModel; the scan must not be lost.
        val restored = ScanViewModel({ null }, store, SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))
        assertEquals("stored:front", restored.state.value.session.frontUri)
    }

    @Test
    fun importFailure_showsError_andKeepsStep() {
        store.failImport = true
        viewModel.onScanned("front")
        assertTrue(state.saveFailed)
        assertEquals(Step.FRONT, state.session.step)
    }
}
