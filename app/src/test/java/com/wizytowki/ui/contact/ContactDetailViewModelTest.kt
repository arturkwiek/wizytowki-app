package com.wizytowki.ui.contact

import androidx.lifecycle.SavedStateHandle
import com.wizytowki.data.CompanyDetails
import com.wizytowki.data.ContactDetails
import com.wizytowki.data.ContactQueries
import com.wizytowki.data.ContactRepository
import com.wizytowki.data.ContactSummary
import com.wizytowki.data.SavedChange
import com.wizytowki.data.ScanRecord
import com.wizytowki.data.db.entities.BusinessCardEntity
import com.wizytowki.domain.ContactDraft
import com.wizytowki.domain.SavedContact
import com.wizytowki.extract.EntityExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContactDetailViewModelTest {
    private val details = MutableStateFlow<ContactDetails?>(ContactDetails(7, null, ContactDraft(firstName = "Jan"), emptyList()))

    private val queries = object : ContactQueries {
        override fun recentContacts(limit: Int): Flow<List<ContactSummary>> = flowOf(emptyList())
        override fun contact(personId: Long): Flow<ContactDetails?> = if (personId == 7L) details else flowOf(null)
        override fun company(companyId: Long): Flow<CompanyDetails?> = flowOf(null)
    }

    private val split = mutableListOf<Pair<Long, ContactDraft>>()
    private var failSplit = false
    private val repository = object : ContactRepository {
        override suspend fun saveNew(contact: ContactDraft, scan: ScanRecord): SavedChange = error("unused")
        override suspend fun savedContacts(): List<SavedContact> = emptyList()
        override suspend fun update(saved: SavedContact, contact: ContactDraft, scan: ScanRecord): SavedChange = error("unused")
        override suspend fun undo(change: SavedChange) = Unit
        override suspend fun mergePeople(keep: SavedContact, removeId: Long, result: ContactDraft) = Unit
        override suspend fun splitCard(cardId: Long, contact: ContactDraft): Long {
            if (failSplit) throw IllegalStateException("db")
            split += cardId to contact
            return 99
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(id: Long = 7) =
        ContactDetailViewModel(queries, repository, EntityExtractor(), SavedStateHandle(mapOf(ContactDetailViewModel.ARG_ID to id)))

    @Test
    fun `card follows the database`() = runTest {
        val vm = vm()

        val first = vm.state.first { it is ContactDetailState.Ready } as ContactDetailState.Ready
        assertEquals("Jan", first.details.contact.firstName)

        details.value = null
        assertEquals(ContactDetailState.Missing, vm.state.first { it !is ContactDetailState.Ready })
    }

    @Test
    fun `unknown person`() = runTest {
        assertEquals(ContactDetailState.Missing, vm(1).state.first { it != ContactDetailState.Loading })
    }

    @Test
    fun `WF-35 card moves to a new contact read from its OCR text`() {
        val vm = vm()
        val card = BusinessCardEntity(
            id = 5,
            personId = 7,
            frontPhotoPath = "/f.jpg",
            rawOcrText = listOf("Anna Nowak", "Kierownik projektu", "", "tel. 600 100 200", "anna@nowak.pl", "", "---", "", "ACME Sp. z o.o.")
                .joinToString(NL),
        )

        vm.splitCard(card)

        val (cardId, contact) = split.single()
        assertEquals(5L, cardId)
        assertEquals("Anna", contact.firstName)
        assertEquals("Nowak", contact.lastName)
        assertEquals(listOf("+48600100200"), contact.phones)
        assertEquals("ACME Sp. z o.o.", contact.company)
        assertEquals(99L, vm.movedTo.value)

        vm.movedShown()
        assertNull(vm.movedTo.value)
    }

    @Test
    fun `failed move is reported`() {
        failSplit = true
        val vm = vm()
        vm.splitCard(BusinessCardEntity(id = 5, personId = 7, frontPhotoPath = "/f.jpg"))
        assertTrue(vm.splitFailed.value)
        assertNull(vm.movedTo.value)
    }

    private companion object {
        const val NL = "\n"
    }
}
