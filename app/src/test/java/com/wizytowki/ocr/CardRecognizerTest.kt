package com.wizytowki.ocr

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardRecognizerTest {
    private fun page(vararg lines: String) = OcrPage(listOf(OcrBlock(lines.map { OcrLine(it, 0.9f) })))

    private val pages = mapOf(
        "front" to page("Łukasz Żółć", "Kierownik działu sprzedaży"),
        "back" to page("Sales Department Manager"),
    )
    private val calls = mutableListOf<String>()
    private val recognizer = CardRecognizer(TextRecognizer { uri -> calls += uri; pages.getValue(uri) })

    @Test
    fun `WF-10 recognizes both sides and keeps Polish characters`() = runTest {
        val ocr = recognizer.recognize("front", "back")

        assertEquals("Łukasz Żółć\nKierownik działu sprzedaży", ocr.front.text)
        assertEquals("Sales Department Manager", ocr.back?.text)
        assertEquals(setOf("front", "back"), calls.toSet())
    }

    @Test
    fun `skipped back is not recognized`() = runTest {
        val ocr = recognizer.recognize("front", null)

        assertNull(ocr.back)
        assertEquals(listOf("front"), calls)
    }

    @Test
    fun `measures analysis time`() = runTest {
        var now = 1_000L
        recognizer.clock = { now.also { now += 450 } }

        assertEquals(450L, recognizer.recognize("front", "back").durationMs)
    }

    @Test
    fun `raw text joins sides and omits an empty back`() {
        val both = CardOcr(page("Jan Kowalski"), page("John Kowalski"), 0)
        val emptyBack = CardOcr(page("Jan Kowalski"), OcrPage.EMPTY, 0)

        assertEquals("Jan Kowalski\n\n---\n\nJohn Kowalski", both.rawText)
        assertEquals("Jan Kowalski", emptyBack.rawText)
    }

    @Test
    fun `page with only blank lines counts as empty`() {
        assertTrue(page("  ", "").isEmpty)
        assertFalse(page("ą").isEmpty)
        assertEquals(2, OcrPage(listOf(OcrBlock(listOf(OcrLine("a"))), OcrBlock(listOf(OcrLine("b"))))).lines.size)
    }
}
