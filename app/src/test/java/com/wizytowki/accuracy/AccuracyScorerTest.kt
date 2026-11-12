package com.wizytowki.accuracy

import com.wizytowki.domain.CardMerger
import com.wizytowki.domain.MergedCard
import com.wizytowki.extract.Entity
import com.wizytowki.extract.EntityType
import com.wizytowki.extract.Side
import com.wizytowki.ocr.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccuracyScorerTest {

    private fun card(vararg fields: Triple<EntityType, String, Language?>): MergedCard =
        CardMerger.merge(fields.map { (type, value, language) -> Entity(type, value, value, Side.FRONT, 0.9f, language = language) })

    private fun f(type: EntityType, value: String, language: Language? = null) = Triple(type, value, language)

    /** Same content as test-data/expected/001.json. */
    private val expected = ExpectedCard(
        firstName = "Jan",
        lastName = "Kowalski",
        phones = listOf("+48 600 123 456"),
        emails = listOf("jan.kowalski@przyklad.pl"),
        websites = listOf("www.przyklad.pl"),
        companyName = "Przykład Sp. z o.o.",
        nip = "1234563218",
        position = mapOf(Language.PL to "Dyrektor sprzedaży", Language.EN to "Sales Director"),
        address = mapOf(Language.PL to "ul. Długa 5, 00-001 Warszawa"),
    )

    private val perfect = arrayOf(
        f(EntityType.FIRST_NAME, "Jan"),
        f(EntityType.LAST_NAME, "Kowalski"),
        f(EntityType.PHONE, "+48600123456"),
        f(EntityType.EMAIL, "jan.kowalski@przyklad.pl"),
        f(EntityType.WEBSITE, "https://przyklad.pl/"),
        f(EntityType.COMPANY, "Przykład Sp. z o.o."),
        f(EntityType.NIP, "1234563218"),
        f(EntityType.POSITION, "Dyrektor sprzedaży", Language.PL),
        f(EntityType.POSITION, "Sales Director", Language.EN),
        f(EntityType.ADDRESS, "ul. Długa 5 00-001 Warszawa", Language.PL),
    )

    @Test
    fun `all fields recognized gives 100 percent`() {
        val report = AccuracyScorer.score("001", expected, card(*perfect))
        assertEquals(10, report.total)
        assertEquals(1.0, report.accuracy, 0.0)
    }

    @Test
    fun `lost Polish letters count as an error`() {
        val fields = perfect.map { if (it.first == EntityType.COMPANY) f(EntityType.COMPANY, "Przyklad Sp. z o.o.") else it }
        val report = AccuracyScorer.score("001", expected, card(*fields.toTypedArray()))
        assertEquals(listOf("company"), report.errors.map { it.field })
        assertEquals(0.9, report.accuracy, 0.0001)
    }

    @Test
    fun `missing, wrong-language and invented fields`() {
        val report = AccuracyScorer.score(
            "002",
            ExpectedCard(firstName = "Jan", lastName = "Kowalski", position = mapOf(Language.EN to "Sales Director")),
            card(
                f(EntityType.FIRST_NAME, "Jan"),
                f(EntityType.POSITION, "Sales Director", Language.PL),
                f(EntityType.COMPANY, "Najlepsze Usługi"),
            ),
        )
        assertEquals(
            setOf("lastName", "position.EN", "position.PL", "company"),
            report.errors.map { it.field }.toSet(),
        )
        assertEquals(1, report.correct)
    }

    @Test
    fun `position of unknown language matches either version`() {
        val report = AccuracyScorer.score(
            "003",
            ExpectedCard(position = mapOf(Language.PL to "Kierownik")),
            card(f(EntityType.POSITION, "Kierownik", Language.UNKNOWN)),
        )
        assertTrue(report.errors.isEmpty())
    }

    @Test
    fun `phone and url normalization`() {
        assertEquals(AccuracyScorer.phone("+48 600 123 456"), AccuracyScorer.phone("600-123-456"))
        assertEquals(AccuracyScorer.phone("0048 22 123 45 67"), AccuracyScorer.phone("(022) 123 45 67"))
        assertEquals(AccuracyScorer.url("https://www.acme.pl/"), AccuracyScorer.url("acme.pl"))
        assertFalse(AccuracyScorer.text("Łódź") == AccuracyScorer.text("Lodz"))
    }

    @Test
    fun `reports add up and describe errors`() {
        val a = AccuracyScorer.score("001", expected, card(*perfect))
        val b = AccuracyScorer.score("002", ExpectedCard(firstName = "Anna"), card())
        val sum = a + b
        assertEquals(11, sum.total)
        assertTrue(sum.describe().contains("002 firstName: expected \"Anna\""))
    }
}
