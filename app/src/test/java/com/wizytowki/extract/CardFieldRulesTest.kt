package com.wizytowki.extract

import com.wizytowki.ocr.FragmentLanguage
import com.wizytowki.ocr.Language
import com.wizytowki.ocr.OcrBlock
import com.wizytowki.ocr.OcrBox
import com.wizytowki.ocr.OcrLine
import com.wizytowki.ocr.OcrPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardFieldRulesTest {
    private val extractor = EntityExtractor()

    private fun extract(vararg lines: String) =
        extractor.extract(OcrPage(listOf(OcrBlock(lines.map { OcrLine(it, 1f) }))), Side.FRONT)

    private fun List<Entity>.value(type: EntityType) = singleOrNull { it.type == type }?.value
    private fun List<Entity>.values(type: EntityType) = filter { it.type == type }.map { it.value }

    // --- NIP ---

    @Test
    fun `NIP checksum`() {
        assertTrue(CardFieldRules.isValidNip("5260250995"))
        assertTrue(CardFieldRules.isValidNip("1234563218"))
        assertFalse(CardFieldRules.isValidNip("1234563219"))
        assertFalse(CardFieldRules.isValidNip("123456321"))
    }

    @Test
    fun `labeled NIP in different notations is normalized to digits`() {
        assertEquals(listOf("5260250995"), extract("NIP: 526-025-09-95").values(EntityType.NIP))
        assertEquals(listOf("5260250995"), extract("NIP 526-02-50-995").values(EntityType.NIP))
        assertEquals(listOf("5260250995"), extract("VAT ID: PL 5260250995").values(EntityType.NIP))
        assertEquals(listOf("1234563218"), extract("NIP 1234563218").values(EntityType.NIP))
    }

    @Test
    fun `labeled NIP with bad checksum is kept with low confidence`() {
        val nip = extract("NIP: 123-456-32-19").single { it.type == EntityType.NIP }
        assertEquals("1234563219", nip.value)
        assertTrue(nip.confidence < 0.5f)
    }

    @Test
    fun `unlabeled number is a NIP only when grouped and valid`() {
        assertEquals(listOf("5260250995"), extract("526-025-09-95").values(EntityType.NIP))
        assertTrue(extract("5260250995").values(EntityType.NIP).isEmpty())
        assertTrue(extract("123-456-32-19").values(EntityType.NIP).isEmpty())
    }

    @Test
    fun `NIP is not a phone and phone is not a NIP`() {
        val found = extract("NIP: 526-025-09-95", "tel. +48 600 123 456")
        assertEquals(listOf("+48600123456"), found.values(EntityType.PHONE))
        assertEquals(listOf("5260250995"), found.values(EntityType.NIP))
    }

    // --- name ---

    @Test
    fun `Polish name with diacritics`() {
        val found = extract("Łukasz Żółkiewski", "Dyrektor ds. sprzedaży", "lukasz.zolkiewski@acme.pl")
        assertEquals("Łukasz", found.value(EntityType.FIRST_NAME))
        assertEquals("Żółkiewski", found.value(EntityType.LAST_NAME))
    }

    @Test
    fun `titles are removed and capital surname comes last`() {
        val found = extract("dr inż. KOWALSKA Małgorzata")
        assertEquals("Małgorzata", found.value(EntityType.FIRST_NAME))
        assertEquals("Kowalska", found.value(EntityType.LAST_NAME))
    }

    @Test
    fun `double-barrelled surname and English name with degree`() {
        assertEquals("Nowak-Jeziorańska", extract("Anna Nowak-Jeziorańska").value(EntityType.LAST_NAME))
        val en = extract("John Smith, MBA", "Sales Manager")
        assertEquals("John", en.value(EntityType.FIRST_NAME))
        assertEquals("Smith", en.value(EntityType.LAST_NAME))
    }

    @Test
    fun `name confirmed by e-mail wins over other capitalized lines`() {
        val found = extract("Nowe Technologie", "Grażyna Wiśniewska", "g.wisniewska@nt.pl")
        assertEquals("Grażyna", found.value(EntityType.FIRST_NAME))
        assertEquals("Wiśniewska", found.value(EntityType.LAST_NAME))
    }

    @Test
    fun `largest text is preferred for unknown first names`() {
        val page = OcrPage(
            listOf(
                OcrBlock(
                    listOf(
                        OcrLine("Zenobiusz Pąk", 1f, OcrBox(0, 0, 400, 60)),
                        OcrLine("Bartłomiej Kos", 1f, OcrBox(0, 70, 300, 100)),
                    ),
                ),
            ),
        )
        val found = extractor.extract(page, Side.FRONT)
        assertEquals("Zenobiusz", found.value(EntityType.FIRST_NAME))
        assertEquals("Pąk", found.value(EntityType.LAST_NAME))
    }

    @Test
    fun `company words, addresses and one-word lines are not names`() {
        val found = extract("Global Solutions", "ul. Złota 44", "00-120 Warszawa", "Polska")
        assertNull(found.value(EntityType.FIRST_NAME))
        assertNull(found.value(EntityType.LAST_NAME))
    }

    // --- position, department, company ---

    @Test
    fun `position and department in Polish and English`() {
        val found = extract("Jan Kowalski", "Kierownik projektu", "Dział Logistyki")
        assertEquals("Kierownik projektu", found.value(EntityType.POSITION))
        assertEquals("Dział Logistyki", found.value(EntityType.DEPARTMENT))

        val en = extract("Jane Doe", "Chief Financial Officer", "Finance Department")
        assertEquals("Chief Financial Officer", en.value(EntityType.POSITION))
        assertEquals("Finance Department", en.value(EntityType.DEPARTMENT))
    }

    @Test
    fun `company by legal form`() {
        assertEquals("ACME Polska Sp. z o.o.", extract("ACME Polska Sp. z o.o.").value(EntityType.COMPANY))
        assertEquals("Żabka Polska S.A.", extract("Żabka Polska S.A.").value(EntityType.COMPANY))
        assertEquals("Contoso Ltd", extract("Contoso Ltd").value(EntityType.COMPANY))
    }

    @Test
    fun `company by e-mail domain when there is no legal form`() {
        val found = extract("Marek Lis", "Kwiatex", "marek@kwiatex.pl")
        assertEquals("Kwiatex", found.value(EntityType.COMPANY))
        assertEquals("Marek", found.value(EntityType.FIRST_NAME))
    }

    @Test
    fun `language-dependent fields carry the fragment language`() {
        val page = OcrPage(
            listOf(
                OcrBlock(listOf(OcrLine("Dyrektor handlowy", 1f)), FragmentLanguage(Language.PL, 0.9f)),
                OcrBlock(listOf(OcrLine("Sales Director", 1f)), FragmentLanguage(Language.EN, 0.9f)),
                OcrBlock(listOf(OcrLine("ul. Polna 5", 1f), OcrLine("00-625 Warszawa", 1f)), FragmentLanguage(Language.PL, 0.9f)),
                OcrBlock(listOf(OcrLine("ACME Sp. z o.o.", 1f)), FragmentLanguage(Language.PL, 0.9f)),
            ),
        )
        val found = extractor.extract(page, Side.FRONT)
        assertEquals(
            mapOf("Dyrektor handlowy" to Language.PL, "Sales Director" to Language.EN),
            found.filter { it.type == EntityType.POSITION }.associate { it.value to it.language },
        )
        assertEquals(Language.PL, found.single { it.type == EntityType.ADDRESS }.language)
        assertNull(found.single { it.type == EntityType.COMPANY }.language)
    }

    @Test
    fun `full Polish card`() {
        val found = extract(
            "mgr Katarzyna Wójcik",
            "Specjalista ds. kadr",
            "Biuro Rachunkowe Bilans",
            "ul. Długa 12/3",
            "80-831 Gdańsk",
            "tel. 58 301 22 33",
            "kom. 601 234 567",
            "k.wojcik@bilans.pl",
            "NIP 583-000-00-00",
        )
        assertEquals("Katarzyna", found.value(EntityType.FIRST_NAME))
        assertEquals("Wójcik", found.value(EntityType.LAST_NAME))
        assertEquals("Specjalista ds. kadr", found.value(EntityType.POSITION))
        assertEquals("Biuro Rachunkowe Bilans", found.value(EntityType.COMPANY))
        assertEquals("ul. Długa 12/3, 80-831 Gdańsk", found.value(EntityType.ADDRESS))
        assertEquals(listOf("+48583012233", "+48601234567"), found.values(EntityType.PHONE))
        assertEquals(EntityType.FIRST_NAME, found.first().type)
    }
}
