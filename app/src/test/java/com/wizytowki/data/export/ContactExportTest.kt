package com.wizytowki.data.export

import com.wizytowki.data.ContactDetails
import com.wizytowki.data.JobHistoryItem
import com.wizytowki.data.db.entities.BusinessCardEntity
import com.wizytowki.domain.ContactDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ContactExportTest {
    private val BOM = Char(0xFEFF).toString()

    private val scanned = LocalDateTime.of(2026, 11, 13, 21, 50)

    private val lukasz = ContactDetails(
        personId = 1,
        companyId = 2,
        contact = ContactDraft(
            firstName = "Łukasz",
            lastName = "Żółkiewski",
            phones = listOf("+48600100200", "+48221234567"),
            emails = listOf("l.zolkiewski@acme.pl"),
            websites = listOf("www.acme.pl"),
            company = "ACME Polska Sp. z o.o.",
            nip = "5260250995",
            positionPl = "Dyrektor sprzedaży",
            positionEn = "Sales Director",
            addressPl = "ul. Długa 5, 00-001 Warszawa",
            comment = "Poznany na targach; oddzwonić\nw piątek",
        ),
        cards = listOf(BusinessCardEntity(id = 1, personId = 1, frontPhotoPath = "/f.jpg", rawOcrText = "Łukasz Żółkiewski", scannedAt = scanned, event = "Targi Poznań")),
        jobs = listOf(JobHistoryItem(2, "ACME Polska Sp. z o.o.", "Dyrektor sprzedaży", "", scanned, null, current = true)),
    )

    // --- vCard ---

    @Test
    fun `vCard has the standard fields with Polish letters and escaping`() {
        val card = ContactExport.vCard(lukasz)
        val lines = card.split("\r\n")

        assertEquals("BEGIN:VCARD", lines.first())
        assertTrue("VERSION:3.0" in lines)
        assertTrue("N:Żółkiewski;Łukasz;;;" in lines)
        assertTrue("FN:Łukasz Żółkiewski" in lines)
        assertTrue("ORG:ACME Polska Sp. z o.o." in lines)
        assertTrue("TITLE:Dyrektor sprzedaży" in lines)
        assertTrue("TEL;TYPE=VOICE:+48600100200" in lines)
        assertTrue("TEL;TYPE=VOICE:+48221234567" in lines)
        assertTrue("EMAIL;TYPE=INTERNET:l.zolkiewski@acme.pl" in lines)
        assertTrue("URL:www.acme.pl" in lines)
        assertTrue("ADR;TYPE=WORK:;;ul. Długa 5\\, 00-001 Warszawa;;;;" in lines)
        assertTrue(card.contains("NOTE:Poznany na targach\\; oddzwonić\\nw piątek\\nTargi Poznań"))
        assertTrue(card.endsWith("END:VCARD\r\n"))
    }

    @Test
    fun `long vCard lines are folded at 75 octets without breaking Polish letters`() {
        val long = lukasz.copy(contact = lukasz.contact.copy(comment = "Żółć ".repeat(40)))
        val lines = ContactExport.vCard(long).split("\r\n")

        assertTrue(lines.all { it.toByteArray(Charsets.UTF_8).size <= 75 })
        val note = lines.dropWhile { !it.startsWith("NOTE:") }.takeWhile { it.startsWith("NOTE:") || it.startsWith(" ") }
        assertTrue(note.size > 1)
        // Unfolding (dropping CRLF + the one leading space) gives back the whole value.
        assertEquals("Żółć ".repeat(40) + "\\nTargi Poznań", note.joinToString("") { it.removePrefix(" ") }.removePrefix("NOTE:"))
    }

    @Test
    fun `contact without a name uses the company as display name`() {
        val company = ContactDetails(3, null, ContactDraft(company = "Żabka S.A."), emptyList())
        assertTrue("FN:Żabka S.A." in ContactExport.vCard(company).split("\r\n"))
    }

    // --- CSV ---

    @Test
    fun `CSV has a header, one row per person, quoting and BOM`() {
        val csv = ContactExport.csv(listOf(lukasz, ContactDetails(4, null, ContactDraft(firstName = "Anna", lastName = "Nowak \"Ania\""), emptyList())))

        assertTrue(csv.startsWith(BOM + "first_name,last_name,company,nip,"))
        val rows = csv.removePrefix(BOM + "").split("\r\n").filter { it.isNotEmpty() }
        assertEquals(3, rows.size)
        assertTrue(rows[1].startsWith("Łukasz,Żółkiewski,ACME Polska Sp. z o.o.,5260250995,Dyrektor sprzedaży,Sales Director,,,+48600100200 | +48221234567,"))
        assertTrue(rows[1].contains("\"ul. Długa 5, 00-001 Warszawa\""))
        assertTrue(rows[1].endsWith(",1,2026-11-13T21:50:00,Targi Poznań,"))
        assertTrue(rows[2].startsWith("Anna,\"Nowak \"\"Ania\"\"\","))
    }

    @Test
    fun `comment with a new line stays in one quoted cell`() {
        val csv = ContactExport.csv(listOf(lukasz))
        assertTrue(csv.contains("\"Poznany na targach; oddzwonić\nw piątek\""))
    }

    // --- JSON ---

    @Test
    fun `JSON of a small contact`() {
        val ola = ContactDetails(5, null, ContactDraft(firstName = "Ola", phones = listOf("+48500600700"), comment = "cytat \"x\"\\y"), emptyList())

        val json = ContactExport.json(listOf(ola), scanned)

        assertEquals(
            "{\"format\":\"wizytowki-export\",\"version\":1,\"exported_at\":\"2026-11-13T21:50:00\",\"contacts\":[" +
                "{\"first_name\":\"Ola\",\"last_name\":\"\",\"phones\":[\"+48500600700\"],\"emails\":[],\"websites\":[],\"social\":[]," +
                "\"company\":{\"name\":\"\",\"nip\":\"\",\"address_pl\":\"\",\"address_en\":\"\"}," +
                "\"position\":{\"pl\":\"\",\"en\":\"\"},\"department\":{\"pl\":\"\",\"en\":\"\"}," +
                "\"comment\":\"cytat \\\"x\\\"\\\\y\",\"jobs\":[],\"cards\":[]}]}",
            json,
        )
    }

    @Test
    fun `JSON keeps history and cards, two contacts are separated by a comma`() {
        val json = ContactExport.json(listOf(lukasz, lukasz.copy(personId = 9)), scanned)

        assertTrue(json.contains("\"jobs\":[{\"company\":\"ACME Polska Sp. z o.o.\",\"position\":\"Dyrektor sprzedaży\",\"department\":\"\",\"from\":\"2026-11-13T21:50:00\",\"to\":null,\"current\":true}]"))
        assertTrue(json.contains("\"cards\":[{\"scanned_at\":\"2026-11-13T21:50:00\",\"event\":\"Targi Poznań\",\"location\":\"\",\"ocr_text\":\"Łukasz Żółkiewski\"}]"))
        assertTrue(json.contains("\"comment\":\"Poznany na targach; oddzwonić\\nw piątek\""))
        assertTrue(json.contains("]},{\"first_name\":\"Łukasz\""))
        assertFalse(json.contains(",,"))
    }

    @Test
    fun `file names and formats`() {
        assertEquals("wizytowki-2026-11-13.vcf", ContactExport.fileName(ExportFormat.VCARD, scanned))
        assertEquals("wizytowki-2026-11-13.csv", ContactExport.fileName(ExportFormat.CSV, scanned))
        assertEquals("wizytowki-2026-11-13.json", ContactExport.fileName(ExportFormat.JSON, scanned))
    }
}
