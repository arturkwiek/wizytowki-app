package com.wizytowki.ui.contact

import androidx.lifecycle.SavedStateHandle
import com.wizytowki.data.CompanyDetails
import com.wizytowki.data.ContactDetails
import com.wizytowki.data.ContactQueries
import com.wizytowki.data.ContactRepository
import com.wizytowki.data.ContactSummary
import com.wizytowki.data.SavedChange
import com.wizytowki.data.ScanRecord
import com.wizytowki.domain.Choice
import com.wizytowki.domain.ContactDraft
import com.wizytowki.domain.ContactField
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MergeViewModelTest {
    private val jan = ContactDetails(1, 10, ContactDraft(firstName = "Jan", lastName = "Kowalski", phones = listOf("+48600100200"), company = "ACME S.A."), emptyList())
    private val janek = ContactDetails(
        2, 20,
        ContactDraft(firstName = "Janek", lastName = "Kowalski", phones = listOf("+48221234567"), emails = listOf("jan@acme.pl"), positionPl = "Prezes"),
        emptyList(),
    )

    private val queries = object : ContactQueries {
        override fun recentContacts(limit: Int): Flow<List<ContactSummary>> = flowOf(
            listOf(ContactSummary(1, "Jan", "Kowalski", "ACME S.A.", ""), ContactSummary(2, "Janek", "Kowalski", "", "Prezes")),
        )
        override fun contact(personId: Long): Flow<ContactDetails?> = flowOf(listOf(jan, janek).firstOrNull { it.personId == personId })
        override fun company(companyId: Long): Flow<CompanyDetails?> = flowOf(null)
        override fun allContacts(): Flow<List<ContactSummary>> = flowOf(emptyList())
        override fun companies(): Flow<List<com.wizytowki.data.CompanySummary>> = flowOf(emptyList())
    }

    private val merged = mutableListOf<Triple<SavedContact, Long, ContactDraft>>()
    private val repository = object : ContactRepository {
        override suspend fun saveNew(contact: ContactDraft, scan: ScanRecord): SavedChange = error("unused")
        override suspend fun savedContacts(): List<SavedContact> = emptyList()
        override suspend fun update(saved: SavedContact, contact: ContactDraft, scan: ScanRecord): SavedChange = error("unused")
        override suspend fun undo(change: SavedChange) = Unit
        override suspend fun mergePeople(keep: SavedContact, removeId: Long, result: ContactDraft) {
            merged += Triple(keep, removeId, result)
        }
        override suspend fun splitCard(cardId: Long, contact: ContactDraft): Long = 99
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = MergeViewModel(queries, repository, SavedStateHandle(mapOf(MergeViewModel.ARG_ID to 1L)))

    @Test
    fun `WF-35 other people are offered, this one is not`() {
        assertEquals(listOf(2L), vm().state.value.candidates.map { it.personId })
    }

    @Test
    fun `defaults keep this person's values, add missing ones and join lists`() {
        val vm = vm()
        vm.select(2)

        val result = vm.state.value.result!!
        assertEquals("Jan", result.firstName)
        assertEquals(listOf("+48600100200", "+48221234567"), result.phones)
        assertEquals(listOf("jan@acme.pl"), result.emails)
        assertEquals("Prezes", result.positionPl)
        assertEquals("ACME S.A.", result.company)
    }

    @Test
    fun `chosen values are merged into this person`() {
        val vm = vm()
        vm.select(2)
        vm.choose(ContactField.FIRST_NAME, Choice.NEW)
        vm.merge()

        val (keep, removeId, result) = merged.single()
        assertEquals(1L, keep.personId)
        assertEquals(10L, keep.companyId)
        assertEquals(2L, removeId)
        assertEquals("Janek", result.firstName)
        assertTrue(vm.state.value.done)
    }
}
