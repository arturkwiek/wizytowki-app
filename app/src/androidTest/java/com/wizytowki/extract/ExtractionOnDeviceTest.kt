package com.wizytowki.extract

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wizytowki.domain.CardMerger
import com.wizytowki.ocr.OcrBlock
import com.wizytowki.ocr.OcrLine
import com.wizytowki.ocr.OcrPage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Extraction rules on Android's regex engine (ICU), which differs from the JVM one used by the
 * unit tests: it rejects the (?U) flag (the preview crashed after #16) and its \b and \w are
 * Unicode-aware. Every rule group runs here at least once, with Polish letters.
 */
@RunWith(AndroidJUnit4::class)
class ExtractionOnDeviceTest {

    @Test
    fun polishCardIsExtractedAndMerged() {
        val lines = listOf(
            "dr inż. Łukasz Żółkiewski",
            "Kierownik działu sprzedaży",
            "DZIAŁ HANDLOWY",
            "Przykład Sp. z o.o.",
            "ul. Długa 5",
            "00-001 Warszawa",
            "tel. +48 600 123 456",
            "lukasz.zolkiewski@przyklad.pl",
            "www.przyklad.pl",
            "NIP 123-456-32-18",
        )
        val page = OcrPage(listOf(OcrBlock(lines.map { OcrLine(it, 1f) })))

        val card = CardMerger.merge(EntityExtractor().extract(page, Side.FRONT))

        assertEquals("Łukasz", card.field(EntityType.FIRST_NAME)?.value)
        assertEquals("Żółkiewski", card.field(EntityType.LAST_NAME)?.value)
        assertEquals("Kierownik działu sprzedaży", card.field(EntityType.POSITION)?.value)
        assertEquals("DZIAŁ HANDLOWY", card.field(EntityType.DEPARTMENT)?.value)
        assertEquals("Przykład Sp. z o.o.", card.field(EntityType.COMPANY)?.value)
        assertEquals("1234563218", card.field(EntityType.NIP)?.value)
        assertEquals(listOf("+48600123456"), card.all(EntityType.PHONE).map { it.value })
        assertEquals(listOf("lukasz.zolkiewski@przyklad.pl"), card.all(EntityType.EMAIL).map { it.value })
        assertEquals(listOf("www.przyklad.pl"), card.all(EntityType.WEBSITE).map { it.value })
        assertEquals(listOf("ul. Długa 5, 00-001 Warszawa"), card.all(EntityType.ADDRESS).map { it.value })
    }
}
