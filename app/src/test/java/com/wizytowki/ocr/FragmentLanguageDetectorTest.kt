package com.wizytowki.ocr

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FragmentLanguageDetectorTest {
    private val asked = mutableListOf<String>()
    private var guesses: List<LanguageGuess> = emptyList()
    private val detector = FragmentLanguageDetector(LanguageIdentifier { asked += it; guesses })

    private suspend fun lang(text: String) = detector.detect(text).language

    @Test
    fun `WF-11 contacts and numbers are language neutral`() = runTest {
        assertEquals(Language.NEUTRAL, lang("+48 600 123 456"))
        assertEquals(Language.NEUTRAL, lang("tel. (22) 123-45-67\nj.kowalski@firma.pl"))
        assertEquals(Language.NEUTRAL, lang("www.firma.com.pl"))
        assertEquals(Language.NEUTRAL, lang("NIP: 123-456-78-90"))
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `Polish letters decide PL without the identifier`() = runTest {
        assertEquals(Language.PL, lang("Kierownik działu sprzedaży"))
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `confident identifier guess is used`() = runTest {
        guesses = listOf(LanguageGuess("en", 0.92f), LanguageGuess("pl", 0.05f))

        val result = detector.detect("Sales Department Manager")

        assertEquals(FragmentLanguage(Language.EN, 0.92f), result)
        assertEquals(listOf("Sales Department Manager"), asked)
    }

    @Test
    fun `identifier gets the text without contacts`() = runTest {
        guesses = listOf(LanguageGuess("en", 0.9f))

        detector.detect("Head Office\n+48 22 123 45 67")

        assertEquals("Head Office", asked.single())
    }

    @Test
    fun `other languages from the identifier are ignored`() = runTest {
        guesses = listOf(LanguageGuess("de", 0.95f))

        assertEquals(Language.UNKNOWN, lang("Jan Kowalski"))
    }

    @Test
    fun `typical card words decide when the identifier is unsure`() = runTest {
        guesses = listOf(LanguageGuess("en", 0.3f))

        assertEquals(Language.PL, lang("ul. Marszalkowska 10"))
        assertEquals(Language.PL, lang("Firma Sp. z o.o."))
        assertEquals(Language.EN, lang("Acme Ltd"))
        assertEquals(Language.EN, lang("Head of Sales"))
    }

    @Test
    fun `a bare name is unknown`() = runTest {
        assertEquals(FragmentLanguage(Language.UNKNOWN, 0f), detector.detect("Jan Kowalski"))
    }

    @Test
    fun `mixed card - Polish front, English back`() = runTest {
        guesses = listOf(LanguageGuess("en", 0.8f))
        val page = OcrPage(
            listOf(
                OcrBlock(listOf(OcrLine("Dyrektor ds. sprzedaży"))),
                OcrBlock(listOf(OcrLine("Sales Director"))),
                OcrBlock(listOf(OcrLine("+48 600 100 200"))),
            ),
        )

        assertEquals(listOf(Language.PL, Language.EN, Language.NEUTRAL), detector.detect(page).map { it.language })
    }
}
