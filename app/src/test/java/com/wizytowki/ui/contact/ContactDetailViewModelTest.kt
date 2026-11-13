package com.wizytowki.ui.contact

import androidx.lifecycle.SavedStateHandle
import com.wizytowki.data.CompanyDetails
import com.wizytowki.data.ContactDetails
import com.wizytowki.data.ContactQueries
import com.wizytowki.data.ContactSummary
import com.wizytowki.domain.ContactDraft
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

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `card follows the database`() = runTest {
        val vm = ContactDetailViewModel(queries, SavedStateHandle(mapOf(ContactDetailViewModel.ARG_ID to 7L)))

        val first = vm.state.first { it is ContactDetailState.Ready } as ContactDetailState.Ready
        assertEquals("Jan", first.details.contact.firstName)

        details.value = null
        assertEquals(ContactDetailState.Missing, vm.state.first { it !is ContactDetailState.Ready })
    }

    @Test
    fun `unknown person`() = runTest {
        val vm = ContactDetailViewModel(queries, SavedStateHandle(mapOf(ContactDetailViewModel.ARG_ID to 1L)))
        assertEquals(ContactDetailState.Missing, vm.state.first { it != ContactDetailState.Loading })
    }
}
