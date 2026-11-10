package com.wizytowki.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PolishDiacriticsTest {
    private fun page(vararg lines: String) = OcrPage(listOf(OcrBlock(lines.map { OcrLine(it, 0.8f) })))

    @Test
    fun `caron letters on a Polish card become Polish letters`() {
        val fixed = PolishDiacritics.fix(page("Łukasz Žółć", "ul. Žródlana 5, 00-950 Wrocław", "Kierownik działu"))

        assertEquals("Łukasz Żółć\nul. Źródlana 5, 00-950 Wrocław\nKierownik działu", fixed.text)
        assertEquals(0.8f, fixed.lines.first().confidence)
    }

    @Test
    fun `z with caron before r l dz is z acute, elsewhere z dot`() {
        assertEquals("źle źródło jeździć", PolishDiacritics.fix("žle žródło ježdzić"))
        assertEquals("żona też każdy", PolishDiacritics.fix("žona tež každy"))
    }

    @Test
    fun `other caron letters map to acute ones`() {
        assertEquals("Ćma Śląsk Gdańsk Kęty", PolishDiacritics.fix("Čma Šląsk Gdaňsk Kěty"))
    }

    @Test
    fun `pages without Polish-only letters stay untouched`() {
        val czech = page("Jiří Žižka", "Obchodní ředitel")
        val english = page("John Smith", "Sales Manager")

        assertSame(czech, PolishDiacritics.fix(czech))
        assertSame(english, PolishDiacritics.fix(english))
    }
}
