package com.wizytowki.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactComparisonTest {

    private val saved = ContactDraft(
        firstName = "Lukasz",
        lastName = "Żółkiewski",
        phones = listOf("+48600100200"),
        emails = listOf("l.zolkiewski@acme.pl"),
        company = "ACME Sp. z o.o.",
        positionPl = "Kierownik projektu",
    )

    private val rescan = ContactDraft(
        firstName = "Łukasz",
        lastName = "ZOLKIEWSKI",
        phones = listOf("+48600100200", "+48221234567"),
        emails = listOf("L.Zolkiewski@ACME.pl"),
        company = "ACME sp. z o. o.",
        positionPl = "Dyrektor projektów",
        positionEn = "Project Director",
    )

    private fun SavedContact(contact: ContactDraft, id: Long = 1) = SavedContact(id, null, contact)

    // --- finding the duplicate ---

    @Test
    fun `WF-32 same person is found by e-mail, phone and name`() {
        assertEquals(
            setOf(MatchReason.EMAIL, MatchReason.PHONE, MatchReason.NAME),
            DuplicateFinder.find(rescan, listOf(SavedContact(saved)))!!.reasons,
        )
    }

    @Test
    fun `each criterion alone is enough`() {
        val other = ContactDraft(firstName = "Anna", lastName = "Nowak")
        assertEquals(
            setOf(MatchReason.PHONE),
            DuplicateFinder.reasons(other.copy(phones = listOf("+48600100200")), saved),
        )
        assertEquals(setOf(MatchReason.EMAIL), DuplicateFinder.reasons(other.copy(emails = listOf("l.zolkiewski@acme.pl")), saved))
        assertEquals(setOf(MatchReason.NAME), DuplicateFinder.reasons(ContactDraft(firstName = "ŁUKASZ", lastName = "zolkiewski"), saved))
    }

    @Test
    fun `different person or empty name is not a duplicate`() {
        assertNull(DuplicateFinder.find(ContactDraft(firstName = "Anna", lastName = "Nowak", phones = listOf("+48500000000")), listOf(SavedContact(saved))))
        assertNull(DuplicateFinder.find(ContactDraft(company = "ACME Sp. z o.o."), listOf(SavedContact(ContactDraft(company = "ACME Sp. z o.o.")))))
    }

    @Test
    fun `strongest match wins`() {
        val byName = SavedContact(ContactDraft(firstName = "Łukasz", lastName = "Żółkiewski"), id = 1)
        val byEmail = SavedContact(ContactDraft(firstName = "Ł.", emails = listOf("l.zolkiewski@acme.pl")), id = 2)
        assertEquals(2L, DuplicateFinder.find(rescan, listOf(byName, byEmail))!!.saved.personId)
    }

    // --- comparing fields ---

    @Test
    fun `differences and proposed choices`() {
        val diff = ContactComparison.diff(saved, rescan).associateBy { it.field }

        assertTrue(diff.getValue(ContactField.FIRST_NAME).same)
        assertEquals(Choice.NEW, diff.getValue(ContactField.FIRST_NAME).default) // keeps "Ł"
        assertTrue(diff.getValue(ContactField.LAST_NAME).same)
        assertEquals(Choice.SAVED, diff.getValue(ContactField.LAST_NAME).default)
        assertTrue(diff.getValue(ContactField.EMAILS).same)
        assertTrue(diff.getValue(ContactField.COMPANY).same)
        assertFalse(diff.getValue(ContactField.POSITION_PL).same)
        assertEquals(Choice.NEW, diff.getValue(ContactField.POSITION_PL).default)
        assertEquals(Choice.BOTH, diff.getValue(ContactField.PHONES).default)
        assertEquals(Choice.NEW, diff.getValue(ContactField.POSITION_EN).default)
        assertFalse(ContactField.ADDRESS_PL in diff)
    }

    @Test
    fun `default resolution updates the saved entry with the new data`() {
        val result = ContactComparison.resolve(saved, rescan)

        assertEquals("Łukasz", result.firstName)
        assertEquals("Żółkiewski", result.lastName)
        assertEquals(listOf("+48600100200", "+48221234567"), result.phones)
        assertEquals(listOf("l.zolkiewski@acme.pl"), result.emails)
        assertEquals("Dyrektor projektów", result.positionPl)
        assertEquals("Project Director", result.positionEn)
    }

    @Test
    fun `choices per field`() {
        val result = ContactComparison.resolve(
            saved,
            rescan,
            mapOf(ContactField.POSITION_PL to Choice.SAVED, ContactField.PHONES to Choice.NEW, ContactField.POSITION_EN to Choice.SAVED),
        )
        assertEquals("Kierownik projektu", result.positionPl)
        assertEquals(listOf("+48600100200", "+48221234567"), result.phones)
        assertEquals("", result.positionEn)
    }

    @Test
    fun `whole entry kept or replaced`() {
        assertEquals(saved, ContactComparison.resolve(saved, rescan, ContactComparison.all(saved, rescan, Choice.SAVED)))
        val taken = ContactComparison.resolve(saved, rescan, ContactComparison.all(saved, rescan, Choice.NEW))
        assertEquals(rescan.copy(emails = rescan.emails), taken)
    }
}
