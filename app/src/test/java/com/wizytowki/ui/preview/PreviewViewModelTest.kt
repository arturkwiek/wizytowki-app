package com.wizytowki.ui.preview

import com.wizytowki.data.ContactRepository
import com.wizytowki.data.ScanRecord
import com.wizytowki.domain.ContactDraft
import com.wizytowki.domain.SavedContact
import com.wizytowki.extract.EntityExtractor
import com.wizytowki.extract.EntityType
import com.wizytowki.ocr.CardRecognizer
import com.wizytowki.ocr.FragmentLanguageDetector
import com.wizytowki.ocr.LanguageIdentifier
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

private class FakeContacts(var fail: Boolean = false) : ContactRepository {
    val saved = mutableListOf<Pair<ContactDraft, ScanRecord>>()
    override suspend fun saveNew(contact: ContactDraft, scan: ScanRecord): Long {
        if (fail) throw IllegalStateException("disk full")
        saved += contact to scan
        return saved.size.toLong()
    }

    override suspend fun savedContacts(): List<SavedContact> = emptyList()
}

@OptIn(ExperimentalCoroutinesApi::class)
class PreviewViewModelTest {
    private var failures = 0
    private val contacts = FakeContacts()

    private val recognizer = CardRecognizer(TextRecognizer { uri ->
        if (failures > 0) {
            failures--
            throw IllegalStateException("OCR failed")
        }
        OcrPage(listOf(OcrBlock(listOf(OcrLine("text of $uri"), OcrLine("tel. 600 100 200")))))
    }, FragmentLanguageDetector(LanguageIdentifier { emptyList() }))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `analyzes both sides of the finished scan`() {
        val vm = PreviewViewModel(StubDraftStore(ScanSession("f", "b")), recognizer, EntityExtractor(), contacts)

        val ready = vm.state.value as PreviewUiState.Ready
        assertEquals("text of f\ntel. 600 100 200", ready.ocr.front.text)
        assertEquals("text of b\ntel. 600 100 200", ready.ocr.back?.text)
        assertEquals(listOf(EntityType.PHONE, EntityType.PHONE), ready.entities.map { it.type })
    }

    @Test
    fun `skipped back gives front only`() {
        val vm = PreviewViewModel(StubDraftStore(ScanSession("f", backSkipped = true)), recognizer, EntityExtractor(), contacts)

        assertNull((vm.state.value as PreviewUiState.Ready).ocr.back)
    }

    @Test
    fun `no draft means nothing to analyze`() {
        val vm = PreviewViewModel(StubDraftStore(null), recognizer, EntityExtractor(), contacts)

        assertEquals(PreviewUiState.NoScan, vm.state.value)
    }

    @Test
    fun `failure can be retried`() {
        failures = 1
        val vm = PreviewViewModel(StubDraftStore(ScanSession("f", backSkipped = true)), recognizer, EntityExtractor(), contacts)
        assertEquals(PreviewUiState.Failed, vm.state.value)

        vm.retry()

        assertTrue(vm.state.value is PreviewUiState.Ready)
    }

    @Test
    fun `save stores the contact with the scan and can be retried after a failure`() {
        contacts.fail = true
        val vm = PreviewViewModel(StubDraftStore(ScanSession("f", "b")), recognizer, EntityExtractor(), contacts)

        vm.save()
        assertEquals(SaveState.FAILED, (vm.state.value as PreviewUiState.Ready).save)

        contacts.fail = false
        vm.save()
        vm.save()

        assertEquals(SaveState.SAVED, (vm.state.value as PreviewUiState.Ready).save)
        val (contact, scan) = contacts.saved.single()
        assertEquals(listOf("+48600100200"), contact.phones)
        assertEquals(ScanRecord("f", "b", (vm.state.value as PreviewUiState.Ready).ocr.rawText), scan)
    }
}
