package com.wizytowki.data

import android.content.Intent
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wizytowki.data.db.WizytowkiDatabase
import com.wizytowki.domain.ContactDraft
import com.wizytowki.ui.contact.QuickActions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Contact list and person card data (REQUIREMENTS 8a, screens 1 and 7). */
@RunWith(AndroidJUnit4::class)
class ContactQueriesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, WizytowkiDatabase::class.java).build()
    private val repository = RoomContactRepository(db, CardPhotoStore(context))
    private val queries = RoomContactQueries(db)

    @After
    fun tearDown() = db.close()

    private fun scan(name: String, event: String = "") =
        ScanRecord(Uri.fromFile(File(context.cacheDir, name).apply { writeText(name) }).toString(), null, "", event)

    @Test
    fun recentContactsWithCompanyAndPosition() = runBlocking {
        repository.saveNew(ContactDraft(firstName = "Jan", lastName = "Kowalski", company = "ACME S.A.", positionPl = "Prezes"), scan("q1.jpg"))
        repository.saveNew(ContactDraft(firstName = "Anna", lastName = "Nowak", positionEn = "Sales Director"), scan("q2.jpg"))

        val recent = queries.recentContacts().first()

        assertEquals(setOf("Jan Kowalski", "Anna Nowak"), recent.map { it.name }.toSet())
        val jan = recent.single { it.firstName == "Jan" }
        assertEquals("ACME S.A.", jan.company)
        assertEquals("Prezes", jan.position)
        assertEquals("Sales Director", recent.single { it.firstName == "Anna" }.position)
    }

    @Test
    fun personCardData() = runBlocking {
        val change = repository.saveNew(
            ContactDraft(firstName = "Łucja", phones = listOf("+48600100200"), company = "ACME S.A.", comment = "Targi"),
            scan("q3.jpg", event = "Infosystem"),
        )

        val details = queries.contact(change.personId).first()!!

        assertEquals("Łucja", details.contact.firstName)
        assertEquals("Targi", details.contact.comment)
        assertEquals("ACME S.A.", details.contact.company)
        assertEquals("Infosystem", details.cards.single().event)
        assertNull(queries.contact(999).first())
    }

    @Test
    fun quickActionIntents() {
        assertEquals(Intent.ACTION_DIAL, QuickActions.call("+48600100200").action)
        assertEquals("tel:%2B48600100200", QuickActions.call("+48600100200").data.toString())
        assertEquals("mailto:jan@acme.pl", QuickActions.email("jan@acme.pl").data.toString())
        assertEquals("https://www.acme.pl", QuickActions.web("www.acme.pl").data.toString())
        assertEquals("geo:0,0?q=ul.%20D%C5%82uga%205%2C%2000-001%20Warszawa", QuickActions.map("ul. Długa 5, 00-001 Warszawa").data.toString())
    }

    @Test
    fun companyCardWithItsPeople() = runBlocking {
        val jan = repository.saveNew(ContactDraft(firstName = "Jan", lastName = "Kowalski", company = "ACME S.A.", positionPl = "Prezes"), scan("c1.jpg"))
        repository.saveNew(ContactDraft(firstName = "Anna", lastName = "Nowak", company = "Acme SA", nip = "5260250995"), scan("c2.jpg"))
        repository.saveNew(ContactDraft(firstName = "Ola", company = "Inna Firma"), scan("c3.jpg"))
        val companyId = queries.contact(jan.personId).first()!!.companyId!!

        val details = queries.company(companyId).first()!!

        assertEquals("ACME S.A.", details.company.name)
        assertEquals("5260250995", details.company.nip)
        assertEquals(listOf("Jan Kowalski", "Anna Nowak").sorted(), details.people.map { it.name }.sorted())
        assertEquals("Prezes", details.people.single { it.firstName == "Jan" }.position)
        assertNull(queries.company(999).first())
    }

    @Test
    fun jobHistoryAndAllCardsAfterAJobChange() = runBlocking {
        val first = ContactDraft(firstName = "Anna", lastName = "Nowak", company = "Stara Firma Sp. z o.o.", positionPl = "Analityk")
        val id = repository.saveNew(first, scan("h1.jpg", event = "Targi 2025")).personId
        val saved = repository.savedContacts().single()
        repository.update(saved, first.copy(company = "Nowa Firma S.A.", positionPl = "Kierownik"), scan("h2.jpg", event = "Targi 2026"))

        val details = queries.contact(id).first()!!

        assertEquals(listOf("Kierownik" to "Nowa Firma S.A.", "Analityk" to "Stara Firma Sp. z o.o."), details.jobs.map { it.position to it.company })
        assertEquals(listOf(true, false), details.jobs.map { it.current })
        assertEquals(2, details.cards.size)
        assertEquals(setOf("Targi 2025", "Targi 2026"), details.cards.map { it.event }.toSet())
    }

    @Test
    fun everyoneAndCompaniesWithPeopleCount() = runBlocking {
        repository.saveNew(ContactDraft(firstName = "Jan", lastName = "Kowalski", phones = listOf("+48600100200"), company = "ACME S.A."), scan("b1.jpg"))
        repository.saveNew(ContactDraft(firstName = "Anna", lastName = "Nowak", emails = listOf("anna@acme.pl"), company = "Acme SA"), scan("b2.jpg"))
        repository.saveNew(ContactDraft(firstName = "Ola", positionPl = "Analityk"), scan("b3.jpg"))

        assertEquals(3, queries.allContacts().first().size)
        assertEquals(listOf("+48600100200"), queries.allContacts().first().single { it.firstName == "Jan" }.contactData)
        // The nameless company created only for Ola's position is not listed.
        assertEquals(listOf("ACME S.A." to 2), queries.companies().first().map { it.name to it.people })
    }
}
