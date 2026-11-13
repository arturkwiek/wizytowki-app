package com.wizytowki.domain

import com.wizytowki.extract.Entity
import com.wizytowki.extract.EntityType
import com.wizytowki.extract.Side
import com.wizytowki.ocr.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactDraftTest {

    private fun e(type: EntityType, value: String, language: Language? = null, side: Side = Side.FRONT) =
        Entity(type, value, value, side, 0.9f, language = language)

    @Test
    fun `merged card becomes a contact with both language versions`() {
        val card = CardMerger.merge(
            listOf(
                e(EntityType.FIRST_NAME, "Łukasz"),
                e(EntityType.LAST_NAME, "Żółkiewski"),
                e(EntityType.PHONE, "+48600100200"),
                e(EntityType.PHONE, "+48221234567", side = Side.BACK),
                e(EntityType.EMAIL, "l.zolkiewski@acme.pl"),
                e(EntityType.COMPANY, "ACME Sp. z o.o."),
                e(EntityType.NIP, "5260250995"),
                e(EntityType.POSITION, "Dyrektor handlowy", Language.PL),
                e(EntityType.POSITION, "Sales Director", Language.EN, Side.BACK),
                e(EntityType.ADDRESS, "ul. Długa 5, 00-001 Warszawa", Language.PL),
            ),
        )

        val contact = ContactDraft.from(card)

        assertEquals("Łukasz", contact.firstName)
        assertEquals("Żółkiewski", contact.lastName)
        assertEquals(setOf("+48600100200", "+48221234567"), contact.phones.toSet())
        assertEquals(listOf("l.zolkiewski@acme.pl"), contact.emails)
        assertEquals("ACME Sp. z o.o.", contact.company)
        assertEquals("5260250995", contact.nip)
        assertEquals("Dyrektor handlowy", contact.positionPl)
        assertEquals("Sales Director", contact.positionEn)
        assertEquals("ul. Długa 5, 00-001 Warszawa", contact.addressPl)
        assertEquals("", contact.addressEn)
        assertTrue(contact.hasCompanyData)
    }

    @Test
    fun `value of unknown language fills the free version, Polish first`() {
        val onlyUnknown = ContactDraft.from(CardMerger.merge(listOf(e(EntityType.POSITION, "Kierownik", Language.UNKNOWN))))
        assertEquals("Kierownik", onlyUnknown.positionPl)

        val withPolish = ContactDraft.from(
            CardMerger.merge(
                listOf(
                    e(EntityType.DEPARTMENT, "Dział handlowy", Language.PL),
                    e(EntityType.DEPARTMENT, "Sales", Language.UNKNOWN),
                ),
            ),
        )
        assertEquals("Dział handlowy", withPolish.departmentPl)
        assertEquals("Sales", withPolish.departmentEn)
    }

    @Test
    fun `contact without company data`() {
        val contact = ContactDraft.from(CardMerger.merge(listOf(e(EntityType.FIRST_NAME, "Anna"), e(EntityType.PHONE, "+48600100200"))))
        assertFalse(contact.hasCompanyData)
    }

    @Test
    fun `WF-23 dictation is added to the comment`() {
        val typed = ContactDraft(comment = "Spotkanie na targach.").withDictation("  oddzwonić w piątek ")
        assertEquals("Spotkanie na targach. oddzwonić w piątek", typed.comment)
        assertEquals("call back", ContactDraft().withDictation("call back").comment)
        assertEquals(typed, typed.withDictation("   "))
        assertEquals("notatka", ContactDraft(comment = "  notatka \n").cleaned().comment)
    }
}
