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

        val id = repository.saveNew(contact, ScanRecord(photo("front.jpg"), photo("back.jpg"), "OCR text"))

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
        val id = repository.saveNew(contact, ScanRecord(photo("f3.jpg"), null, ""))

        val saved = repository.savedContacts().single()
        assertEquals(id, saved.personId)
        assertEquals(contact, saved.contact)
    }

    @Test
    fun personWithoutCompanyAndBack() = runBlocking {
        val id = repository.saveNew(ContactDraft(firstName = "Anna", phones = listOf("+48600100200")), ScanRecord(photo("f2.jpg"), null, ""))

        val person = db.personDao().getPersonById(id).first()!!
        assertNull(person.currentCompanyId)
        assertEquals("", db.businessCardDao().getCardsByPerson(id).first().single().backPhotoPath)
    }
}
