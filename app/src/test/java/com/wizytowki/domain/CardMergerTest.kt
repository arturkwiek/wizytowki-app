package com.wizytowki.domain

import com.wizytowki.extract.Entity
import com.wizytowki.extract.EntityExtractor
import com.wizytowki.extract.EntityType
import com.wizytowki.extract.Side
import com.wizytowki.ocr.CardOcr
import com.wizytowki.ocr.FragmentLanguage
import com.wizytowki.ocr.Language
import com.wizytowki.ocr.OcrBlock
import com.wizytowki.ocr.OcrLine
import com.wizytowki.ocr.OcrPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardMergerTest {

    private fun e(type: EntityType, value: String, side: Side, confidence: Float = 0.9f, language: Language? = null) =
        Entity(type, value, value, side, confidence, language = language)

    @Test
    fun `WF-15 value repeated on both sides is stored once with both sides`() {
        val card = CardMerger.merge(
            listOf(
                e(EntityType.PHONE, "+48600100200", Side.FRONT, 0.8f),
                e(EntityType.PHONE, "+48600100200", Side.BACK, 0.95f),
                e(EntityType.EMAIL, "jan@acme.pl", Side.FRONT),
                e(EntityType.EMAIL, "Jan@ACME.pl", Side.BACK),
            ),
        )
        val phone = card.all(EntityType.PHONE).single()
        assertEquals(setOf(Side.FRONT, Side.BACK), phone.sides)
        assertEquals(0.95f, phone.confidence)
        assertEquals(1, card.all(EntityType.EMAIL).size)
        assertTrue(card.conflicts.isEmpty())
    }

    @Test
    fun `multi-valued fields keep every distinct value`() {
        val card = CardMerger.merge(
            listOf(
                e(EntityType.PHONE, "+48600100200", Side.FRONT),
                e(EntityType.PHONE, "+48221234567", Side.BACK),
                e(EntityType.WEBSITE, "https://www.acme.pl/", Side.FRONT),
                e(EntityType.WEBSITE, "acme.pl", Side.BACK),
            ),
        )
        assertEquals(2, card.all(EntityType.PHONE).size)
        assertEquals(1, card.all(EntityType.WEBSITE).size)
        assertTrue(card.conflicts.isEmpty())
    }

    @Test
    fun `WF-15 different single values on the two sides are a conflict`() {
        val card = CardMerger.merge(
            listOf(
                e(EntityType.NIP, "5260250995", Side.FRONT, 0.98f),
                e(EntityType.NIP, "5260250996", Side.BACK, 0.4f),
                e(EntityType.COMPANY, "ACME Sp. z o.o.", Side.FRONT),
                e(EntityType.COMPANY, "ACME sp. z o. o.", Side.BACK),
            ),
        )
        val nip = card.field(EntityType.NIP)!!
        assertEquals("5260250995", nip.value)
        assertTrue(nip.conflict)
        assertEquals(listOf("5260250996"), nip.alternatives.map { it.value })
        assertFalse(card.field(EntityType.COMPANY)!!.conflict)
        assertEquals(listOf(nip), card.conflicts)
    }

    @Test
    fun `two candidates on the same side are not a conflict`() {
        val company = CardMerger.merge(
            listOf(
                e(EntityType.COMPANY, "ACME S.A.", Side.FRONT, 0.9f),
                e(EntityType.COMPANY, "Grupa ACME", Side.FRONT, 0.7f),
            ),
        ).field(EntityType.COMPANY)!!
        assertEquals("ACME S.A.", company.value)
        assertFalse(company.conflict)
        assertEquals(listOf("Grupa ACME"), company.alternatives.map { it.value })
    }

    @Test
    fun `version with Polish letters wins when OCR lost them on one side`() {
        val card = CardMerger.merge(
            listOf(
                e(EntityType.FIRST_NAME, "Lukasz", Side.FRONT, 0.95f),
                e(EntityType.FIRST_NAME, "Łukasz", Side.BACK, 0.8f),
                e(EntityType.LAST_NAME, "ZOLTOWSKI", Side.FRONT),
                e(EntityType.LAST_NAME, "Żółtowski", Side.BACK),
            ),
        )
        assertEquals("Łukasz", card.field(EntityType.FIRST_NAME)!!.value)
        assertEquals("Żółtowski", card.field(EntityType.LAST_NAME)!!.value)
        assertTrue(card.conflicts.isEmpty())
    }

    @Test
    fun `WF-16 Polish front and English back give both language versions`() {
        val card = CardMerger.merge(
            listOf(
                e(EntityType.POSITION, "Dyrektor handlowy", Side.FRONT, language = Language.PL),
                e(EntityType.POSITION, "Sales Director", Side.BACK, language = Language.EN),
                e(EntityType.ADDRESS, "ul. Polna 5, 00-625 Warszawa", Side.FRONT, language = Language.PL),
                e(EntityType.ADDRESS, "5 Polna Street, 00-625 Warsaw", Side.BACK, language = Language.EN),
                e(EntityType.POSITION, "Kierownik", Side.BACK, language = Language.UNKNOWN),
            ),
        )
        assertEquals("Dyrektor handlowy", card.field(EntityType.POSITION, Language.PL)!!.value)
        assertEquals("Sales Director", card.field(EntityType.POSITION, Language.EN)!!.value)
        assertEquals("Kierownik", card.field(EntityType.POSITION, null)!!.value)
        assertEquals(2, card.all(EntityType.ADDRESS).size)
        assertTrue(card.conflicts.isEmpty())
    }

    @Test
    fun `WF-17 language-independent fields ignore the fragment language`() {
        val card = CardMerger.merge(
            listOf(
                e(EntityType.COMPANY, "ACME Ltd", Side.FRONT, language = Language.EN),
                e(EntityType.COMPANY, "ACME Ltd", Side.BACK, language = Language.PL),
            ),
        )
        val company = card.all(EntityType.COMPANY).single()
        assertNull(company.language)
        assertEquals(setOf(Side.FRONT, Side.BACK), company.sides)
    }

    @Test
    fun `WF-14 full card from both sides`() {
        fun block(language: Language?, vararg lines: String) =
            OcrBlock(lines.map { OcrLine(it, 1f) }, language?.let { FragmentLanguage(it, 0.9f) })
        val front = OcrPage(
            listOf(
                block(null, "Jan Kowalski"),
                block(Language.PL, "Kierownik projektu"),
                block(Language.PL, "ACME Polska Sp. z o.o."),
                block(null, "tel. +48 600 123 456", "jan.kowalski@acme.pl"),
            ),
        )
        val back = OcrPage(
            listOf(
                block(null, "Jan Kowalski"),
                block(Language.EN, "Project Manager"),
                block(null, "ACME Polska Sp. z o.o."),
                block(null, "mobile 600 123 456", "NIP 526-025-09-95"),
            ),
        )
        val card = CardMerger.merge(EntityExtractor().extract(CardOcr(front, back, 0)))

        assertEquals(setOf(Side.FRONT, Side.BACK), card.field(EntityType.FIRST_NAME)!!.sides)
        assertEquals("Kowalski", card.field(EntityType.LAST_NAME)!!.value)
        assertEquals("Kierownik projektu", card.field(EntityType.POSITION, Language.PL)!!.value)
        assertEquals("Project Manager", card.field(EntityType.POSITION, Language.EN)!!.value)
        assertEquals("+48600123456", card.all(EntityType.PHONE).single().value)
        assertEquals(setOf(Side.BACK), card.field(EntityType.NIP)!!.sides)
        assertTrue(card.conflicts.isEmpty())
    }
}
