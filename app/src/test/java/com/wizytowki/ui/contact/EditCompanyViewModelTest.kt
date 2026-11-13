package com.wizytowki.ui.contact

import androidx.lifecycle.SavedStateHandle
import com.wizytowki.data.CompanyDetails
import com.wizytowki.data.CompanyEdit
import com.wizytowki.data.CompanySummary
import com.wizytowki.data.ContactDetails
import com.wizytowki.data.ContactQueries
import com.wizytowki.data.ContactRepository
import com.wizytowki.data.ContactSummary
import com.wizytowki.data.SavedChange
import com.wizytowki.data.ScanRecord
import com.wizytowki.data.db.entities.CompanyEntity
import com.wizytowki.domain.ContactDraft
import com.wizytowki.domain.SavedContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditCompanyViewModelTest {
    private val details = CompanyDetails(
        CompanyEntity(id = 4, name = "Akme", nip = "5260250995", website = "acme.pl", addressPl = "ul. Długa 5"),
        listOf(ContactSummary(1, "Jan", "Kowalski", "Akme", ""), ContactSummary(2, "Anna", "Nowak", "Akme", "")),
    )

    private val queries = object : ContactQueries {
        override fun recentContacts(limit: Int): Flow<List<ContactSummary>> = flowOf(emptyList())
        override fun allContacts(): Flow<List<ContactSummary>> = flowOf(emptyList())
        override fun companies(): Flow<List<CompanySummary>> = flowOf(emptyList())
        override fun contact(personId: Long): Flow<ContactDetails?> = flowOf(null)
        override fun company(companyId: Long): Flow<CompanyDetails?> = flowOf(details.takeIf { companyId == 4L })
    }

    private val edits = mutableListOf<Pair<Long, CompanyEdit>>()
    private val repository = object : ContactRepository {
        override suspend fun saveNew(contact: ContactDraft, scan: ScanRecord): SavedChange = error("unused")
        override suspend fun savedContacts(): List<SavedContact> = emptyList()
        override suspend fun update(saved: SavedContact, contact: ContactDraft, scan: ScanRecord): SavedChange = error("unused")
        override suspend fun undo(change: SavedChange) = Unit
        override suspend fun mergePeople(keep: SavedContact, removeId: Long, result: ContactDraft) = Unit
        override suspend fun splitCard(cardId: Long, contact: ContactDraft): Long = 0
        override suspend fun editContact(personId: Long, contact: ContactDraft) = Unit
        override suspend fun addNote(personId: Long, note: String) = Unit
        override suspend fun editCompany(companyId: Long, edit: CompanyEdit) {
            edits += companyId to edit
        }
        override suspend fun deletePerson(personId: Long) = Unit
        override suspend fun deleteCard(cardId: Long) = Unit
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(id: Long = 4) = EditCompanyViewModel(queries, repository, SavedStateHandle(mapOf(EditCompanyViewModel.ARG_ID to id)))

    @Test
    fun `#115 company loads, is corrected and saved`() {
        val vm = vm()
        assertEquals(CompanyEdit("Akme", "5260250995", "acme.pl", "ul. Długa 5", ""), vm.state.value.edit)
        assertEquals(2, vm.state.value.people)

        vm.change { it.copy(name = "ACME Polska Sp. z o.o.", addressEn = "5 Długa Street") }
        vm.save()

        assertEquals(4L to CompanyEdit("ACME Polska Sp. z o.o.", "5260250995", "acme.pl", "ul. Długa 5", "5 Długa Street"), edits.single())
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun `NIP with a bad checksum is flagged but can be saved`() {
        val vm = vm()
        assertFalse(vm.state.value.nipSuspicious)

        vm.change { it.copy(nip = "526-025-09-96") }
        assertTrue(vm.state.value.nipSuspicious)
        vm.save()
        assertEquals("526-025-09-96", edits.single().second.nip)
    }

    @Test
    fun `missing company`() {
        assertTrue(vm(5).state.value.missing)
    }
}
