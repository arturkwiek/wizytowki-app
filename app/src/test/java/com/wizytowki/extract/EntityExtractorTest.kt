package com.wizytowki.extract

import com.wizytowki.ocr.CardOcr
import com.wizytowki.ocr.OcrBlock
import com.wizytowki.ocr.OcrLine
import com.wizytowki.ocr.OcrPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityExtractorTest {
    private val extractor = EntityExtractor()

    private fun page(vararg blocks: List<String>) =
        OcrPage(blocks.map { lines -> OcrBlock(lines.map { OcrLine(it, 1f) }) })

    private fun extract(vararg lines: String) = extractor.extract(page(lines.toList()), Side.FRONT)

    private fun values(type: EntityType, vararg lines: String) = extract(*lines).filter { it.type == type }.map { it.value }

    // --- phones ---

    @Test
    fun `WF-13 Polish and international phones are normalized to E164`() {
        assertEquals(
            listOf("+48600123456", "+48221234567", "+48221234568", "+4930123456", "+48601222333"),
            values(
                EntityType.PHONE,
                "tel. +48 600 123 456",
                "(22) 123 45 67",
                "022 123-45-68",
                "+49 30 123456",
                "0048 601-222-333",
            ),
        )
    }

    @Test
    fun `phone kind from label or Polish prefix`() {
        val phones = extract("mob. 600 100 200", "tel. 22 123 45 67", "fax 22 123 45 99", "+44 20 7946 0958", "723 000 111")
            .associate { it.value to it.phoneKind }

        assertEquals(PhoneKind.MOBILE, phones["+48600100200"])
        assertEquals(PhoneKind.LANDLINE, phones["+48221234567"])
        assertEquals(PhoneKind.FAX, phones["+48221234599"])
        assertEquals(PhoneKind.UNKNOWN, phones["+442079460958"])
        assertEquals(PhoneKind.MOBILE, phones["+48723000111"])
    }

    @Test
    fun `two phones on one line`() {
        assertEquals(listOf("+48221234567", "+48600100200"), values(EntityType.PHONE, "T: 22 123 45 67, M: 600 100 200"))
    }

    @Test
    fun `NIP, postal code, account and dates are not phones`() {
        assertTrue(
            values(
                EntityType.PHONE,
                "NIP: 525-000-12-34",
                "1234567890",
                "00-950 Wrocław",
                "Konto: 12 1020 3040 5060 7080 9010 1112",
                "2024.01.15",
                "+48 600 123",
            ).isEmpty(),
        )
    }

    // --- e-mails, websites, social ---

    @Test
    fun `e-mails are lowercased and OCR spaces around at are fixed`() {
        val emails = extract("E-mail: Jan.Kowalski@Firma.com.pl", "anna.nowak @ example.eu.").filter { it.type == EntityType.EMAIL }

        assertEquals(listOf("jan.kowalski@firma.com.pl", "anna.nowak@example.eu"), emails.map { it.value })
        assertTrue(emails[1].confidence < emails[0].confidence)
    }

    @Test
    fun `websites without the e-mail domain`() {
        val found = extract("jan@firma.pl", "www.Firma.PL/kontakt", "https://example.com,", "acme.eu")

        assertEquals(listOf("www.firma.pl/kontakt", "https://example.com", "acme.eu"), found.filter { it.type == EntityType.WEBSITE }.map { it.value })
        assertEquals(listOf("jan@firma.pl"), found.filter { it.type == EntityType.EMAIL }.map { it.value })
    }

    @Test
    fun `bare domain is less certain than www`() {
        val (www, bare) = extract("www.acme.pl", "acme.eu")
        assertTrue(bare.confidence < www.confidence)
    }

    @Test
    fun `social profiles are recognized by host`() {
        val found = extract("linkedin.com/in/jan-kowalski", "www.fedex.com")

        assertEquals(EntityType.SOCIAL, found.single { it.value.contains("linkedin") }.type)
        assertEquals(EntityType.WEBSITE, found.single { it.value.contains("fedex") }.type)
    }

    @Test
    fun `company form and initials are not websites`() {
        assertTrue(values(EntityType.WEBSITE, "Acme Sp. z o.o.", "J. Kowalski", "ul. Polna 5").isEmpty())
    }

    // --- addresses ---

    @Test
    fun `WF-13 Polish address on two lines`() {
        assertEquals(
            listOf("ul. Źródlana 5/12, 00-950 Wrocław"),
            values(EntityType.ADDRESS, "Jan Kowalski", "ul. Źródlana 5/12", "00-950 Wrocław", "tel. 600 100 200"),
        )
    }

    @Test
    fun `Polish address on one line with label and country`() {
        assertEquals(
            listOf("Al. Jerozolimskie 100, 00-807 Warszawa, Polska"),
            values(EntityType.ADDRESS, "Adres: Al. Jerozolimskie 100, 00-807 Warszawa", "Polska"),
        )
    }

    @Test
    fun `foreign addresses`() {
        assertEquals(
            listOf("221B Baker Street, London NW1 6XE, United Kingdom", "Hauptstraße 5, 10115 Berlin"),
            extractor.extract(
                page(
                    listOf("221B Baker Street", "London NW1 6XE", "United Kingdom"),
                    listOf("Hauptstraße 5", "10115 Berlin"),
                ),
                Side.BACK,
            ).filter { it.type == EntityType.ADDRESS }.map { it.value },
        )
    }

    @Test
    fun `street without postal code is less certain`() {
        val (full) = extract("ul. Polna 5", "00-001 Kraków")
        val (street) = extract("ul. Polna 5")

        assertEquals("ul. Polna 5", street.value)
        assertTrue(street.confidence < full.confidence)
    }

    @Test
    fun `contact lines are not addresses`() {
        assertTrue(values(EntityType.ADDRESS, "tel. 22 123 45 67", "j.kowalski@firma.pl", "Kierownik działu").isEmpty())
    }

    // --- card ---

    @Test
    fun `both sides with side marked and duplicates within a side merged`() {
        val card = CardOcr(
            front = page(listOf("tel. 600 100 200", "kom. 600-100-200")),
            back = page(listOf("+48 600 100 200", "www.acme.pl")),
            durationMs = 0,
        )

        val found = extractor.extract(card)

        assertEquals(listOf(Side.FRONT, Side.BACK, Side.BACK), found.map { it.side })
        assertEquals(1, found.count { it.side == Side.FRONT })
    }

    @Test
    fun `confidence uses the OCR line confidence`() {
        val low = extractor.extract(OcrPage(listOf(OcrBlock(listOf(OcrLine("600 100 200", 0.5f))))), Side.FRONT).single()
        val unknown = extractor.extract(OcrPage(listOf(OcrBlock(listOf(OcrLine("600 100 200"))))), Side.FRONT).single()

        assertEquals(0.5f * 0.95f, low.confidence, 1e-4f)
        assertEquals(0.8f * 0.95f, unknown.confidence, 1e-4f)
    }

    @Test
    fun `normalizePhone rejects wrong Polish length`() {
        assertNull(extractor.normalizePhone("+48 60 012 345"))
        assertEquals("+48600123456", extractor.normalizePhone("48 600 123 456"))
    }
}
