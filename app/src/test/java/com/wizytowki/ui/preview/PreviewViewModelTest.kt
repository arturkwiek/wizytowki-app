package com.wizytowki.ui.preview

import com.wizytowki.data.ContactRepository
import com.wizytowki.data.SavedChange
import com.wizytowki.data.ScanRecord
import com.wizytowki.domain.ContactDraft
import com.wizytowki.domain.Choice
import com.wizytowki.domain.ContactField
import com.wizytowki.domain.MatchReason
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
import org.junit.Assert.assertFalse
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
    val undone = mutableListOf<SavedChange>()

    override suspend fun saveNew(contact: ContactDraft, scan: ScanRecord): SavedChange {
        if (fail) throw IllegalStateException("disk full")
        saved += contact to scan
        return SavedChange.Created(saved.size.toLong(), 1, listOf("/cards/front.jpg", "/cards/back.jpg"), null)
    }

    override suspend fun undo(change: SavedChange) {
        undone += change
    }

    override suspend fun mergePeople(keep: SavedContact, removeId: Long, result: ContactDraft) = Unit
    override suspend fun splitCard(cardId: Long, contact: ContactDraft): Long = 99

    var existing: List<SavedContact> = emptyList()
    val updated = mutableListOf<Pair<SavedContact, ContactDraft>>()

    override suspend fun savedContacts(): List<SavedContact> = existing

    override suspend fun update(saved: SavedContact, contact: ContactDraft, scan: ScanRecord): SavedChange {
        updated += saved to contact
        return SavedChange.Created(saved.personId, 2, listOf("/cards/front2.jpg"), null)
    }
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

    private fun duplicateVm(): PreviewViewModel {
        contacts.existing = listOf(SavedContact(7, null, ContactDraft(firstName = "Jan", phones = listOf("+48600100200"))))
        val vm = PreviewViewModel(StubDraftStore(ScanSession("f", backSkipped = true)), recognizer, EntityExtractor(), contacts)
        vm.save()
        return vm
    }

    private val PreviewViewModel.ready get() = state.value as PreviewUiState.Ready

    @Test
    fun `same phone shows the comparison instead of saving`() {
        val vm = duplicateVm()

        val review = vm.ready.review!!
        assertEquals(setOf(MatchReason.PHONE), review.match.reasons)
        assertTrue(contacts.saved.isEmpty())
        assertEquals(SaveState.IDLE, vm.ready.save)
    }

    @Test
    fun `chosen fields update the saved contact`() {
        val vm = duplicateVm()

        vm.chooseAll(Choice.SAVED)
        vm.confirmUpdate()

        val (saved, result) = contacts.updated.single()
        assertEquals(7L, saved.personId)
        assertEquals(saved.contact, result)
        assertEquals(SaveState.UPDATED, vm.ready.save)
        assertNull(vm.ready.review)
    }

    @Test
    fun `separate contact or cancel`() {
        val vm = duplicateVm()
        vm.cancelReview()
        assertNull(vm.ready.review)

        vm.save()
        vm.saveAsNew()
        assertEquals(1, contacts.saved.size)
        assertEquals(SaveState.SAVED, vm.ready.save)
    }

    @Test
    fun `WF-20 edited fields are saved and typed phones normalized`() {
        val vm = PreviewViewModel(StubDraftStore(ScanSession("f", "b")), recognizer, EntityExtractor(), contacts)
        assertEquals(listOf("f", "b"), vm.ready.photos)

        vm.edit(ContactField.FIRST_NAME, listOf("  Łucja "))
        vm.edit(ContactField.PHONES, listOf("+48600100200", "601 222 333", ""))
        vm.save()

        val contact = contacts.saved.single().first
        assertEquals("Łucja", contact.firstName)
        assertEquals(listOf("+48600100200", "+48601222333"), contact.phones)

        vm.edit(ContactField.FIRST_NAME, listOf("after save"))
        assertEquals("  Łucja ", vm.ready.draft.firstName)
        assertFalse(vm.ready.editable)
    }

    @Test
    fun `WF-23 typed and dictated comment is saved, also when updating a duplicate`() {
        val vm = PreviewViewModel(StubDraftStore(ScanSession("f", "b")), recognizer, EntityExtractor(), contacts)
        vm.editComment("Poznany na targach")
        vm.addDictation("zadzwonić w poniedziałek")
        vm.save()
        assertEquals("Poznany na targach zadzwonić w poniedziałek", contacts.saved.single().first.comment)

        contacts.existing = listOf(SavedContact(7, null, ContactDraft(firstName = "Jan", phones = listOf("+48600100200"))))
        val dup = PreviewViewModel(StubDraftStore(ScanSession("f", backSkipped = true)), recognizer, EntityExtractor(), contacts)
        dup.addDictation("druga rozmowa")
        dup.save()
        dup.confirmUpdate()
        assertEquals("druga rozmowa", contacts.updated.single().second.comment)
    }

    @Test
    fun `WF-24 event and place are saved with the card`() {
        val vm = PreviewViewModel(StubDraftStore(ScanSession("f", "b")), recognizer, EntityExtractor(), contacts)
        vm.editEvent("Targi Infosystem")
        vm.setLocation("52.40640, 16.92520")
        vm.save()

        val scan = contacts.saved.single().second
        assertEquals("Targi Infosystem", scan.event)
        assertEquals("52.40640, 16.92520", scan.location)
    }

    @Test
    fun `coordinates use a dot in every language`() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale("pl", "PL"))
            assertEquals("52.40640, -16.92520", formatCoordinates(52.4064, -16.9252))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun `undo reverts the save and brings the scan back for editing`() {
        val vm = PreviewViewModel(StubDraftStore(ScanSession("f", "b")), recognizer, EntityExtractor(), contacts)
        vm.editComment("pierwsza wersja")
        vm.save()
        assertEquals(SaveState.SAVED, vm.ready.save)

        vm.undo()

        assertEquals(contacts.saved.size, contacts.undone.size)
        assertEquals(SaveState.IDLE, vm.ready.save)
        assertTrue(vm.ready.editable)
        assertEquals(listOf("file:///cards/front.jpg", "file:///cards/back.jpg"), vm.ready.photos)
        assertEquals("pierwsza wersja", vm.ready.draft.comment)

        vm.save()
        assertEquals(2, contacts.saved.size)
        assertEquals("file:///cards/front.jpg", contacts.saved.last().second.frontUri)
    }

    @Test
    fun `undo after updating a duplicate`() {
        val vm = duplicateVm()
        vm.confirmUpdate()
        vm.undo()
        assertEquals(2L, contacts.undone.single().cardId)
        assertEquals(SaveState.IDLE, vm.ready.save)
        assertEquals(listOf("file:///cards/front2.jpg"), vm.ready.photos)
    }
}
