package com.wizytowki.ui.contact

import com.wizytowki.data.CompanyDetails
import com.wizytowki.data.CompanySummary
import com.wizytowki.data.ContactDetails
import com.wizytowki.data.ContactQueries
import com.wizytowki.data.ContactSummary
import com.wizytowki.data.export.ExportFormat
import com.wizytowki.domain.ContactDraft
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
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest {
    private val people = listOf(
        ContactDetails(1, null, ContactDraft(firstName = "Łucja", lastName = "Wiśniewska", phones = listOf("+48600100200")), emptyList()),
        ContactDetails(2, null, ContactDraft(firstName = "Jan", lastName = "Kowalski"), emptyList()),
    )

    private val queries = object : ContactQueries {
        override fun recentContacts(limit: Int): Flow<List<ContactSummary>> = flowOf(emptyList())
        override fun allContacts(): Flow<List<ContactSummary>> =
            flowOf(people.map { ContactSummary(it.personId, it.contact.firstName, it.contact.lastName, "", "") })
        override fun companies(): Flow<List<CompanySummary>> = flowOf(emptyList())
        override fun contact(personId: Long): Flow<ContactDetails?> = flowOf(people.firstOrNull { it.personId == personId })
        override fun company(companyId: Long): Flow<CompanyDetails?> = flowOf(null)
    }

    private val written = mutableListOf<Pair<String, String>>()
    private var fail = false
    private val target = ExportTarget { uri, text ->
        if (fail) throw IOException("no space")
        written += uri to text
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `#122 all contacts are written to the chosen file`() {
        val vm = ExportViewModel(queries, target)

        vm.export(ExportFormat.VCARD, "content://downloads/1")

        assertEquals(ExportState.Done(2), vm.state.value)
        val (uri, text) = written.single()
        assertEquals("content://downloads/1", uri)
        assertEquals(2, Regex("BEGIN:VCARD").findAll(text).count())
        assertTrue(text.contains("N:Wiśniewska;Łucja;;;"))
    }

    @Test
    fun `failure to write is shown`() {
        fail = true
        val vm = ExportViewModel(queries, target)
        vm.export(ExportFormat.CSV, "content://x")
        assertEquals(ExportState.Failed, vm.state.value)

        vm.dismiss()
        assertEquals(ExportState.Idle, vm.state.value)
    }
}
