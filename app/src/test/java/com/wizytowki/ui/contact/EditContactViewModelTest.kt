package com.wizytowki.ui.contact

import androidx.lifecycle.SavedStateHandle
import com.wizytowki.data.CompanyDetails
import com.wizytowki.data.CompanySummary
import com.wizytowki.data.ContactDetails
import com.wizytowki.data.ContactQueries
import com.wizytowki.data.ContactRepository
import com.wizytowki.data.ContactSummary
import com.wizytowki.data.SavedChange
import com.wizytowki.data.ScanRecord
import com.wizytowki.domain.ContactDraft
import com.wizytowki.domain.ContactField
import com.wizytowki.domain.SavedContact
import com.wizytowki.extract.EntityExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditContactViewModelTest {
    private val saved = ContactDetails(
        3, 9,
        ContactDraft(firstName = "Lukasz", lastName = "Zolkiewski", phones = listOf("+48600100200"), company = "ACME S.A.", comment = "stara notatka"),
        emptyList(),
    )

    private val queries = object : ContactQueries {
        override fun recentContacts(limit: Int): Flow<List<ContactSummary>> = flowOf(emptyList())
        override fun allContacts(): Flow<List<ContactSummary>> = flowOf(emptyList())
        override fun companies(): Flow<List<CompanySummary>> = flowOf(emptyList())
        override fun contact(personId: Long): Flow<ContactDetails?> = flowOf(saved.takeIf { personId == 3L })
        override fun company(companyId: Long): Flow<CompanyDetails?> = flowOf(null)
    }

    private val edits = mutableListOf<Pair<Long, ContactDraft>>()
    private val repository = object : ContactRepository {
        override suspend fun saveNew(contact: ContactDraft, scan: ScanRecord): SavedChange = error("unused")
        override suspend fun savedContacts(): List<SavedContact> = emptyList()
        override suspend fun update(saved: SavedContact, contact: ContactDraft, scan: ScanRecord): SavedChange = error("unused")
        override suspend fun undo(change: SavedChange) = Unit
        override suspend fun mergePeople(keep: SavedContact, removeId: Long, result: ContactDraft) = Unit
        override suspend fun splitCard(cardId: Long, contact: ContactDraft): Long = 0
        override suspend fun editContact(personId: Long, contact: ContactDraft) {
            edits += personId to contact
        }
        override suspend fun addNote(personId: Long, note: String) = Unit
        override suspend fun editCompany(companyId: Long, edit: com.wizytowki.data.CompanyEdit) = Unit
        override suspend fun deletePerson(personId: Long) = Unit
        override suspend fun deleteCard(cardId: Long) = Unit
        override suspend fun addContact(contact: ContactDraft): Long = 0
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(id: Long = 3) = EditContactViewModel(queries, repository, EntityExtractor(), SavedStateHandle(mapOf(EditContactViewModel.ARG_ID to id)))

    @Test
    fun `#115 corrected fields are saved, typed phones normalized, comment untouched`() {
        val vm = vm()
        assertEquals("", vm.state.value.draft!!.comment)

        vm.edit(ContactField.FIRST_NAME, listOf(" Łukasz "))
        vm.edit(ContactField.LAST_NAME, listOf("Żółkiewski"))
        vm.edit(ContactField.PHONES, listOf("+48600100200", "601 222 333", ""))
        vm.save()

        val (personId, contact) = edits.single()
        assertEquals(3L, personId)
        assertEquals("Łukasz", contact.firstName)
        assertEquals("Żółkiewski", contact.lastName)
        assertEquals(listOf("+48600100200", "+48601222333"), contact.phones)
        assertEquals("", contact.comment)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun `missing person`() {
        assertTrue(vm(4).state.value.missing)
    }
}
