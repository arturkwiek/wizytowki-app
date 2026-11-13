package com.wizytowki.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardOcrRawTextTest {

    @Test
    fun `stored raw text gives back sides, blocks and lines`() {
        val card = CardOcr(
            front = OcrPage(listOf(OcrBlock(listOf(OcrLine("Łukasz Żółkiewski"), OcrLine("Prezes"))), OcrBlock(listOf(OcrLine("tel. 600 100 200"))))),
            back = OcrPage(listOf(OcrBlock(listOf(OcrLine("ACME S.A."))))),
            durationMs = 10,
        )

        val read = CardOcr.fromRawText(card.rawText)

        assertEquals(listOf(listOf("Łukasz Żółkiewski", "Prezes"), listOf("tel. 600 100 200")), read.front.blocks.map { b -> b.lines.map { it.text } })
        assertEquals("ACME S.A.", read.back!!.text)
        assertEquals(card.rawText, read.rawText)
    }

    @Test
    fun `front only`() {
        val read = CardOcr.fromRawText("Anna Nowak")
        assertEquals("Anna Nowak", read.front.text)
        assertNull(read.back)
    }
}
