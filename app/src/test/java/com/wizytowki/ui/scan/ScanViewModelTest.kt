package com.wizytowki.ui.scan

import com.wizytowki.scan.QualityIssue
import com.wizytowki.scan.QualityReport
import com.wizytowki.scan.ScanSession.Step
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {
    /** URIs listed here get these issues; everything else is fine. */
    private val badPhotos = mutableMapOf<String, Set<QualityIssue>>()

    /** URIs listed here wait for the gate before the quality result arrives. */
    private val pending = mutableMapOf<String, CompletableDeferred<Unit>>()

    private lateinit var viewModel: ScanViewModel
    private val state get() = viewModel.state.value

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = ScanViewModel { uri ->
            pending[uri]?.await()
            QualityReport(sharpness = 100.0, brightness = 150.0, clippedShare = 0.0, issues = badPhotos[uri].orEmpty())
        }
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
        assertEquals("back", state.session.backUri)
        assertFalse(state.navigateToPreview)

        viewModel.onScanned("front2")
        assertTrue(state.navigateToPreview)
        assertEquals("front2", state.session.frontUri)
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
        badPhotos["front"] = setOf(QualityIssue.BLURRY)
        viewModel.onScanned("front")
        assertEquals(QualityWarning(CardSide.FRONT, setOf(QualityIssue.BLURRY)), state.qualityWarning)

        viewModel.retakeAfterWarning()
        assertNull(state.qualityWarning)
        assertEquals(Step.FRONT, state.session.step)
    }

    @Test
    fun badBack_blocksPreviewUntilAccepted() {
        badPhotos["back"] = setOf(QualityIssue.TOO_DARK)
        viewModel.onScanned("front")
        viewModel.onScanned("back")
        assertEquals(CardSide.BACK, state.qualityWarning?.side)
        assertFalse(state.navigateToPreview)

        viewModel.acceptQualityWarning()
        assertNull(state.qualityWarning)
        assertTrue(state.navigateToPreview)
        assertEquals("back", state.session.backUri)
    }

    @Test
    fun badBack_retake_keepsFront() {
        badPhotos["back"] = setOf(QualityIssue.TOO_BRIGHT)
        viewModel.onScanned("front")
        viewModel.onScanned("back")
        viewModel.retakeAfterWarning()
        assertEquals(Step.BACK, state.session.step)
        assertEquals("front", state.session.frontUri)
    }

    @Test
    fun previewWaitsForQualityCheck() {
        val gate = CompletableDeferred<Unit>().also { pending["back"] = it }
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
        badPhotos["front"] = setOf(QualityIssue.BLURRY)
        val gate = CompletableDeferred<Unit>().also { pending["front"] = it }
        viewModel.onScanned("front")
        viewModel.retakeFront()

        gate.complete(Unit)
        assertNull(state.qualityWarning)
        assertEquals(Step.FRONT, state.session.step)
    }
}
