package com.wizytowki.ui.contact

import com.wizytowki.data.CompanyEdit
import com.wizytowki.data.ContactRepository
import com.wizytowki.data.SavedChange
import com.wizytowki.data.ScanRecord
import com.wizytowki.domain.ContactDraft
import com.wizytowki.domain.ContactField
import com.wizytowki.domain.MatchReason
import com.wizytowki.domain.SavedContact
import com.wizytowki.extract.EntityExtractor
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
class AddContactViewModelTest {
    private val added = mutableListOf<ContactDraft>()
    private var existing = listOf(SavedContact(5, null, ContactDraft(firstName = "Jan", lastName = "Kowalski", phones = listOf("+48600100200"))))

    private val repository = object : ContactRepository {
        override suspend fun saveNew(contact: ContactDraft, scan: ScanRecord): SavedChange = error("unused")
        override suspend fun savedContacts(): List<SavedContact> = existing
        override suspend fun update(saved: SavedContact, contact: ContactDraft, scan: ScanRecord): SavedChange = error("unused")
        override suspend fun undo(change: SavedChange) = Unit
        override suspend fun mergePeople(keep: SavedContact, removeId: Long, result: ContactDraft) = Unit
        override suspend fun splitCard(cardId: Long, contact: ContactDraft): Long = 0
        override suspend fun editContact(personId: Long, contact: ContactDraft) = Unit
        override suspend fun addNote(personId: Long, note: String) = Unit
        override suspend fun editCompany(companyId: Long, edit: CompanyEdit) = Unit
        override suspend fun deletePerson(personId: Long) = Unit
        override suspend fun deleteCard(cardId: Long) = Unit
        override suspend fun addContact(contact: ContactDraft): Long {
            added += contact
            return 42
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = AddContactViewModel(repository, EntityExtractor())

    @Test
    fun `#118 empty form cannot be saved`() {
        val vm = vm()
        assertFalse(vm.state.value.canSave)
        vm.edit(ContactField.FIRST_NAME, listOf("   "))
        assertFalse(vm.state.value.canSave)
        vm.edit(ContactField.PHONES, listOf("500 600 700"))
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `new person is saved with comment and normalized phone`() {
        val vm = vm()
        vm.edit(ContactField.FIRST_NAME, listOf("Łucja"))
        vm.edit(ContactField.PHONES, listOf("500 600 700"))
        vm.editComment("Poznana na konferencji")
        vm.addDictation("oddzwonić")
        vm.save()

        val contact = added.single()
        assertEquals("Łucja", contact.firstName)
        assertEquals(listOf("+48500600700"), contact.phones)
        assertEquals("Poznana na konferencji oddzwonić", contact.comment)
        assertEquals(42L, vm.state.value.savedId)
    }

    @Test
    fun `same phone shows the saved person first, saving anyway works`() {
        val vm = vm()
        vm.edit(ContactField.FIRST_NAME, listOf("Janek"))
        vm.edit(ContactField.PHONES, listOf("600-100-200"))
        vm.save()

        assertTrue(added.isEmpty())
        assertEquals(5L, vm.state.value.duplicate!!.saved.personId)
        assertEquals(setOf(MatchReason.PHONE), vm.state.value.duplicate!!.reasons)

        vm.save(evenIfDuplicate = true)
        assertEquals(1, added.size)
        assertNull(vm.state.value.duplicate)
    }
}
