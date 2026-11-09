package com.wizytowki.ui.scan

import com.wizytowki.scan.ScanSession.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanViewModelTest {
    private val viewModel = ScanViewModel()
    private val state get() = viewModel.state.value

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
}
