package com.wizytowki.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanyMatcherTest {

    private val acme = SavedCompany(1, "ACME Polska Sp. z o.o.", nip = "5260250995", website = "www.acme.pl", emailDomain = "acme.pl")
    private val other = SavedCompany(2, "Żabka Polska S.A.", website = "zabka.pl", emailDomain = "zabka.pl")

    @Test
    fun `WF-33 same company by NIP, website, e-mail domain or name`() {
        assertEquals(acme, CompanyMatcher.find(ContactDraft(nip = "526-025-09-95"), listOf(other, acme)))
        assertEquals(acme, CompanyMatcher.find(ContactDraft(websites = listOf("https://acme.pl/kontakt")), listOf(other, acme)))
        assertEquals(acme, CompanyMatcher.find(ContactDraft(emails = listOf("jan@acme.pl")), listOf(other, acme)))
        assertEquals(acme, CompanyMatcher.find(ContactDraft(company = "Acme Polska sp. z o. o."), listOf(other, acme)))
        assertEquals(other, CompanyMatcher.find(ContactDraft(company = "ŻABKA POLSKA SA"), listOf(acme, other)))
    }

    @Test
    fun `different NIP means a different company even with the same name`() {
        assertNull(CompanyMatcher.find(ContactDraft(company = "ACME Polska Sp. z o.o.", nip = "1234563218"), listOf(acme)))
    }

    @Test
    fun `public mailbox and empty data do not link companies`() {
        val gmail = SavedCompany(3, "", emailDomain = "gmail.com")
        assertNull(CompanyMatcher.find(ContactDraft(emails = listOf("anna@gmail.com")), listOf(gmail)))
        assertNull(CompanyMatcher.find(ContactDraft(positionPl = "Kierownik"), listOf(SavedCompany(4, ""))))
        assertFalse(CompanyMatcher.matches(ContactDraft(company = "ACME Logistics"), acme))
    }

    @Test
    fun `strongest evidence wins`() {
        val byName = SavedCompany(5, "Kwiatex")
        val bySite = SavedCompany(6, "Kwiatex Hurt", website = "kwiatex.pl")
        assertEquals(bySite, CompanyMatcher.find(ContactDraft(company = "Kwiatex", websites = listOf("www.kwiatex.pl")), listOf(byName, bySite)))
    }

    @Test
    fun `name and domain normalization`() {
        assertEquals("acmepolska", CompanyMatcher.normalizeName("ACME Polska Sp. z o.o."))
        assertEquals("zolwhandel", CompanyMatcher.normalizeName("Żółw-Handel S.C."))
        assertEquals("acme.pl", CompanyMatcher.domain("HTTPS://www.Acme.pl/kontakt?x=1"))
        assertTrue(CompanyMatcher.normalizeName("Sp. z o.o.").isEmpty())
    }
}
