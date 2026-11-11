package com.wizytowki.domain

import com.wizytowki.extract.EntityExtractor
import com.wizytowki.extract.EntityType
import com.wizytowki.extract.Side
import com.wizytowki.ocr.CardOcr
import com.wizytowki.ocr.OcrBlock
import com.wizytowki.ocr.OcrLine
import com.wizytowki.ocr.OcrPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldConfidenceTest {

    @Test
    fun `repeated readings raise confidence and stay within 0 to 1`() {
        assertEquals(0.9f, FieldConfidence.repeated(listOf(0.9f)), 0.0001f)
        assertEquals(0.96f, FieldConfidence.repeated(listOf(0.8f, 0.8f)), 0.0001f)
        assertEquals(1f, FieldConfidence.repeated(listOf(1.2f)), 0.0001f)
        assertEquals(0f, FieldConfidence.repeated(emptyList()), 0.0001f)
    }

    @Test
    fun `conflict lowers confidence below the threshold`() {
        assertTrue(FieldConfidence.conflicted(0.95f) < FieldConfidence.LOW)
    }

    private fun card(vararg lines: Pair<String, Float>) = CardMerger.merge(
        EntityExtractor().extract(CardOcr(OcrPage(listOf(OcrBlock(lines.map { OcrLine(it.first, it.second) }))), null, 0)),
    )

    @Test
    fun `WF-22 every field has a confidence and doubtful ones are marked`() {
        val card = card(
            "Zenobiusz Pąk" to 0.95f,
            "ul. Lipowa 3" to 0.95f,
            "NIP: 123-456-32-19" to 0.95f,
            "tel. +48 600 123 456" to 0.95f,
            "jan@acme.pl" to 0.5f,
        )
        assertTrue(card.fields.all { it.confidence in 0f..1f })
        // Unknown first name, street without postal code, NIP with a bad checksum, blurry e-mail.
        assertTrue(card.field(EntityType.FIRST_NAME)!!.lowConfidence)
        assertTrue(card.all(EntityType.ADDRESS).single().lowConfidence)
        assertTrue(card.field(EntityType.NIP)!!.lowConfidence)
        assertTrue(card.all(EntityType.EMAIL).single().lowConfidence)
        assertFalse(card.all(EntityType.PHONE).single().lowConfidence)
    }

    @Test
    fun `known name confirmed by e-mail is not marked`() {
        val card = card("Agnieszka Wróbel" to 0.95f, "a.wrobel@acme.pl" to 0.95f)
        assertFalse(card.field(EntityType.FIRST_NAME)!!.lowConfidence)
        assertEquals(setOf(Side.FRONT), card.field(EntityType.LAST_NAME)!!.sides)
    }
}
