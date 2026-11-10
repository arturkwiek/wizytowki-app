package com.wizytowki.ui.preview

import com.wizytowki.ocr.CardRecognizer
import com.wizytowki.ocr.OcrBlock
import com.wizytowki.ocr.OcrLine
import com.wizytowki.ocr.OcrPage
import com.wizytowki.ocr.TextRecognizer
import com.wizytowki.scan.DraftStore
import com.wizytowki.scan.ScanSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class StubDraftStore(private val session: ScanSession?) : DraftStore {
    override val draft: StateFlow<ScanSession?> = MutableStateFlow(null)
    override suspend fun load(): ScanSession? = session
    override suspend fun importImage(sourceUri: String, prefix: String): String = sourceUri
    override suspend fun save(session: ScanSession) = Unit
    override suspend fun clear() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class PreviewViewModelTest {
    private var failures = 0

    private val recognizer = CardRecognizer(TextRecognizer { uri ->
        if (failures > 0) {
            failures--
            throw IllegalStateException("OCR failed")
        }
        OcrPage(listOf(OcrBlock(listOf(OcrLine("text of $uri")))))
    })

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `analyzes both sides of the finished scan`() {
        val vm = PreviewViewModel(StubDraftStore(ScanSession("f", "b")), recognizer)

        val ready = vm.state.value as PreviewUiState.Ready
        assertEquals("text of f", ready.ocr.front.text)
        assertEquals("text of b", ready.ocr.back?.text)
    }

    @Test
    fun `skipped back gives front only`() {
        val vm = PreviewViewModel(StubDraftStore(ScanSession("f", backSkipped = true)), recognizer)

        assertNull((vm.state.value as PreviewUiState.Ready).ocr.back)
    }

    @Test
    fun `no draft means nothing to analyze`() {
        val vm = PreviewViewModel(StubDraftStore(null), recognizer)

        assertEquals(PreviewUiState.NoScan, vm.state.value)
    }

    @Test
    fun `failure can be retried`() {
        failures = 1
        val vm = PreviewViewModel(StubDraftStore(ScanSession("f", backSkipped = true)), recognizer)
        assertEquals(PreviewUiState.Failed, vm.state.value)

        vm.retry()

        assertTrue(vm.state.value is PreviewUiState.Ready)
    }
}
