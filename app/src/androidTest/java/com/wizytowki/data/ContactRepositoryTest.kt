package com.wizytowki.data

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wizytowki.data.db.WizytowkiDatabase
import com.wizytowki.domain.ContactDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Saving a scanned contact into the real Room schema (#98), in memory. */
@RunWith(AndroidJUnit4::class)
class ContactRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, WizytowkiDatabase::class.java).build()
    private val repository = RoomContactRepository(db, CardPhotoStore(context))

    @After
    fun tearDown() = db.close()

    private fun photo(name: String): String {
        val file = File(context.cacheDir, name).apply { writeText("jpeg $name") }
        return Uri.fromFile(file).toString()
    }

    @Test
    fun savesPersonCompanyPositionAndCard() = runBlocking {
        val contact = ContactDraft(
            firstName = "Łukasz",
            lastName = "Żółkiewski",
            phones = listOf("+48600100200", "+48221234567"),
            emails = listOf("l.zolkiewski@acme.pl"),
            company = "ACME Sp. z o.o.",
            nip = "5260250995",
            positionPl = "Dyrektor handlowy",
            positionEn = "Sales Director",
            addressPl = "ul. Długa 5, 00-001 Warszawa",
        )

        val id = repository.saveNew(contact, ScanRecord(photo("front.jpg"), photo("back.jpg"), "OCR text")).personId

        val person = db.personDao().getPersonById(id).first()!!
        assertEquals("Łukasz", person.firstName)
        assertEquals(listOf("+48600100200", "+48221234567"), person.phones.splitLines())
        val company = db.companyDao().getCompanyById(person.currentCompanyId!!).first()!!
        assertEquals("ACME Sp. z o.o.", company.name)
        assertEquals("acme.pl", company.emailDomain)
        val job = db.employmentHistoryDao().getHistoryByPerson(id).first().single()
        assertEquals("Sales Director", job.positionEn)
        val card = db.businessCardDao().getCardsByPerson(id).first().single()
        assertEquals("OCR text", card.rawOcrText)
        assertTrue(File(card.frontPhotoPath).readText() == "jpeg front.jpg")
        assertTrue(File(card.backPhotoPath).exists())
    }

    @Test
    fun savedContactReadsBackTheSameData() = runBlocking {
        val contact = ContactDraft(
            firstName = "Grażyna",
            lastName = "Wiśniewska",
            phones = listOf("+48600100200"),
            emails = listOf("g.wisniewska@nt.pl"),
            company = "Nowe Technologie S.A.",
            positionPl = "Księgowa",
            departmentEn = "Finance",
            addressEn = "5 Polna Street, 00-625 Warsaw",
        )
        val id = repository.saveNew(contact, ScanRecord(photo("f3.jpg"), null, "")).personId

        val saved = repository.savedContacts().single()
        assertEquals(id, saved.personId)
        assertEquals(contact, saved.contact)
    }

    @Test
    fun updateKeepsPersonAndAddsTheCard() = runBlocking {
        val first = ContactDraft(firstName = "Jan", lastName = "Kowalski", phones = listOf("+48600100200"), company = "ACME S.A.", positionPl = "Kierownik")
        val id = repository.saveNew(first, ScanRecord(photo("u1.jpg"), null, "1")).personId
        val saved = repository.savedContacts().single()

        val merged = first.copy(phones = listOf("+48600100200", "+48221234567"), company = "ACME s.a.", positionPl = "Dyrektor")
        repository.update(saved, merged, ScanRecord(photo("u2.jpg"), null, "2"))

        val after = repository.savedContacts().single()
        assertEquals(id, after.personId)
        assertEquals(saved.companyId, after.companyId)
        assertEquals(merged, after.contact)
        assertEquals(2, db.businessCardDao().getCardsByPerson(id).first().size)
        assertEquals(1, db.employmentHistoryDao().getHistoryByPerson(id).first().size)
    }

    @Test
    fun newCompanyEndsThePreviousJob() = runBlocking {
        val first = ContactDraft(firstName = "Anna", lastName = "Nowak", company = "Stara Firma Sp. z o.o.", positionPl = "Analityk")
        val id = repository.saveNew(first, ScanRecord(photo("j1.jpg"), null, "")).personId
        val saved = repository.savedContacts().single()

        repository.update(saved, first.copy(company = "Nowa Firma S.A.", positionPl = "Kierownik"), ScanRecord(photo("j2.jpg"), null, ""))

        val jobs = db.employmentHistoryDao().getHistoryByPerson(id).first()
        assertEquals(2, jobs.size)
        assertEquals("Analityk", jobs.single { !it.isCurrent }.positionPl)
        assertEquals("Kierownik", jobs.single { it.isCurrent }.positionPl)
        assertEquals("Nowa Firma S.A.", repository.savedContacts().single().contact.company)
    }

    @Test
    fun personWithoutCompanyAndBack() = runBlocking {
        val id = repository.saveNew(ContactDraft(firstName = "Anna", phones = listOf("+48600100200")), ScanRecord(photo("f2.jpg"), null, "")).personId

        val person = db.personDao().getPersonById(id).first()!!
        assertNull(person.currentCompanyId)
        assertEquals("", db.businessCardDao().getCardsByPerson(id).first().single().backPhotoPath)
    }

    @Test
    fun commentIsSavedAndLaterNotesAreAdded() = runBlocking {
        val first = ContactDraft(firstName = "Ewa", lastName = "Lis", comment = "Targi Poznań")
        val id = repository.saveNew(first, ScanRecord(photo("c1.jpg"), null, "")).personId
        repository.update(repository.savedContacts().single(), first.copy(comment = "Oferta wysłana"), ScanRecord(photo("c2.jpg"), null, ""))

        assertEquals("Targi Poznań\n\nOferta wysłana", db.personDao().getPersonById(id).first()!!.comment)
    }

    @Test
    fun eventAndPlaceAreStoredWithTheCard() = runBlocking {
        val id = repository.saveNew(ContactDraft(firstName = "Ola"), ScanRecord(photo("e1.jpg"), null, "", "Targi Infosystem", "52.40640, 16.92520")).personId
        val card = db.businessCardDao().getCardsByPerson(id).first().single()
        assertEquals("Targi Infosystem", card.event)
        assertEquals("52.40640, 16.92520", card.location)
    }

    @Test
    fun undoOfNewContactRemovesEverything() = runBlocking {
        val change = repository.saveNew(ContactDraft(firstName = "Ewa", company = "ACME S.A.", positionPl = "Prezes"), ScanRecord(photo("x1.jpg"), photo("x2.jpg"), ""))
        val photos = change.cardPhotos
        assertEquals(2, photos.size)

        repository.undo(change)

        assertTrue(repository.savedContacts().isEmpty())
        assertTrue(db.companyDao().getAllCompanies().first().isEmpty())
        assertTrue(db.businessCardDao().getAllCards().first().isEmpty())
        assertTrue(photos.none { File(it).exists() })
    }

    @Test
    fun undoOfUpdateRestoresThePreviousContact() = runBlocking {
        val first = ContactDraft(firstName = "Anna", lastName = "Nowak", company = "Stara Firma Sp. z o.o.", positionPl = "Analityk", comment = "notatka")
        val id = repository.saveNew(first, ScanRecord(photo("y1.jpg"), null, "")).personId
        val before = repository.savedContacts().single()

        val change = repository.update(before, first.copy(company = "Nowa Firma S.A.", positionPl = "Kierownik", comment = "druga"), ScanRecord(photo("y2.jpg"), null, ""))
        repository.undo(change)

        val after = repository.savedContacts().single()
        assertEquals(before, after)
        assertEquals("notatka", db.personDao().getPersonById(id).first()!!.comment)
        assertEquals(1, db.businessCardDao().getCardsByPerson(id).first().size)
        assertEquals(listOf("Analityk"), db.employmentHistoryDao().getHistoryByPerson(id).first().map { it.positionPl })
        assertEquals(1, db.companyDao().getAllCompanies().first().size)
    }

    @Test
    fun peopleOfOneCompanyShareItsRecord() = runBlocking {
        val first = repository.saveNew(
            ContactDraft(firstName = "Jan", lastName = "Kowalski", emails = listOf("jan@acme.pl"), company = "ACME Sp. z o.o.", positionPl = "Prezes"),
            ScanRecord(photo("k1.jpg"), null, ""),
        )
        val second = repository.saveNew(
            ContactDraft(firstName = "Anna", lastName = "Nowak", emails = listOf("anna@acme.pl"), company = "Acme sp. z o. o.", nip = "5260250995", positionPl = "Księgowa"),
            ScanRecord(photo("k2.jpg"), null, ""),
        )

        val companies = db.companyDao().getAllCompanies().first()
        assertEquals(1, companies.size)
        assertEquals("ACME Sp. z o.o.", companies.single().name)
        assertEquals("5260250995", companies.single().nip)
        assertEquals(setOf(companies.single().id), repository.savedContacts().map { it.companyId }.toSet())

        // Undo of the second person keeps the shared company, without the NIP it brought.
        repository.undo(second)
        assertEquals("", db.companyDao().getAllCompanies().first().single().nip)
        assertEquals(first.personId, repository.savedContacts().single().personId)
    }

    @Test
    fun movingToAKnownCompanyLinksIt() = runBlocking {
        repository.saveNew(ContactDraft(firstName = "Jan", lastName = "Kowalski", company = "Nowa Firma S.A."), ScanRecord(photo("m1.jpg"), null, ""))
        val anna = ContactDraft(firstName = "Anna", lastName = "Nowak", company = "Stara Firma Sp. z o.o.")
        val id = repository.saveNew(anna, ScanRecord(photo("m2.jpg"), null, "")).personId
        val saved = repository.savedContacts().single { it.personId == id }

        repository.update(saved, anna.copy(company = "NOWA FIRMA SA"), ScanRecord(photo("m3.jpg"), null, ""))

        assertEquals(2, db.companyDao().getAllCompanies().first().size)
        val jan = repository.savedContacts().single { it.personId != id }
        assertEquals(jan.companyId, repository.savedContacts().single { it.personId == id }.companyId)
    }
}
