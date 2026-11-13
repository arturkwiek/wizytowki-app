package com.wizytowki.ui.contact

import androidx.lifecycle.SavedStateHandle
import com.wizytowki.data.CompanyDetails
import com.wizytowki.data.CompanySummary
import com.wizytowki.data.ContactDetails
import com.wizytowki.data.ContactQueries
import com.wizytowki.data.ContactSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseViewModelTest {
    private val people = listOf(
        ContactSummary(1, "Zenon", "Żak", "ACME S.A.", "Prezes", listOf("+48600100200")),
        ContactSummary(2, "Łukasz", "Żółkiewski", "Nowa Firma", "Kierownik", listOf("l.z@nowa.pl")),
        ContactSummary(3, "Anna", "Zając", "", "", emptyList()),
        ContactSummary(4, "Ewa", "Lis", "ACME S.A.", "Księgowa", emptyList()),
    )
    private val companies = listOf(
        CompanySummary(10, "Żabka Polska S.A.", "", 0),
        CompanySummary(11, "ACME S.A.", "5260250995", 2),
    )

    private val queries = object : ContactQueries {
        override fun recentContacts(limit: Int): Flow<List<ContactSummary>> = flowOf(emptyList())
        override fun allContacts(): Flow<List<ContactSummary>> = flowOf(people)
        override fun companies(): Flow<List<CompanySummary>> = flowOf(companies)
        override fun contact(personId: Long): Flow<ContactDetails?> = flowOf(null)
        override fun company(companyId: Long): Flow<CompanyDetails?> = flowOf(null)
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = BrowseViewModel(queries, SavedStateHandle())

    @Test
    fun `everyone in Polish alphabetical order`() = runTest {
        val state = vm().state.first { it.people.isNotEmpty() }
        assertEquals(listOf("Lis", "Zając", "Żak", "Żółkiewski"), state.people.map { it.lastName })
        assertEquals(listOf("ACME S.A.", "Żabka Polska S.A."), state.companies.map { it.name })
    }

    @Test
    fun `search ignores case and Polish letters`() = runTest {
        val vm = vm()
        vm.search("zolk")
        assertEquals(listOf(2L), vm.state.first { it.query == "zolk" }.people.map { it.personId })

        vm.search("acme ksieg")
        assertEquals(listOf(4L), vm.state.first { it.query == "acme ksieg" }.people.map { it.personId })

        vm.search("zabka")
        assertEquals(listOf(10L), vm.state.first { it.query == "zabka" }.companies.map { it.companyId })
    }

    @Test
    fun `search by phone and e-mail`() {
        assertTrue(BrowseViewModel.matches("600 100 200", listOf("Zenon", "+48600100200")))
        assertTrue(BrowseViewModel.matches("600-100", listOf("+48600100200")))
        assertTrue(BrowseViewModel.matches("nowa.pl", listOf("l.z@nowa.pl")))
        assertFalse(BrowseViewModel.matches("700", listOf("+48600100200")))
        assertTrue(BrowseViewModel.matches("  ", listOf("cokolwiek")))
    }

    @Test
    fun `tab is remembered`() = runTest {
        val handle = SavedStateHandle()
        BrowseViewModel(queries, handle).selectTab(BrowseTab.COMPANIES)
        assertEquals(BrowseTab.COMPANIES, BrowseViewModel(queries, handle).state.first { it.people.isNotEmpty() }.tab)
    }
}
