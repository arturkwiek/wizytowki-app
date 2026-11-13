package com.wizytowki.domain

/** A company already in the database, with what identifies it (WF-33). */
data class SavedCompany(
    val id: Long,
    val name: String,
    val nip: String = "",
    val website: String = "",
    val emailDomain: String = "",
)

/**
 * Finds the saved company a contact works for (WF-31, WF-33), so many people share one company
 * record instead of duplicating it. In order of strength: the same NIP; the same website domain;
 * the same e-mail domain (not a public mailbox like gmail.com); the same name without legal form,
 * case, Polish letters and punctuation ("ACME Sp. z o.o." = "Acme sp. z o. o."). Two different NIPs
 * always mean two companies.
 */
object CompanyMatcher {

    fun find(contact: ContactDraft, companies: List<SavedCompany>): SavedCompany? =
        companies.mapNotNull { company -> score(contact, company)?.let { company to it } }
            .maxByOrNull { it.second }?.first

    fun matches(contact: ContactDraft, company: SavedCompany): Boolean = score(contact, company) != null

    private fun score(contact: ContactDraft, company: SavedCompany): Int? {
        val nip = contact.nip.filter { it.isDigit() }
        val savedNip = company.nip.filter { it.isDigit() }
        if (nip.isNotEmpty() && savedNip.isNotEmpty()) return if (nip == savedNip) NIP else null

        var score = 0
        val sites = contact.websites.map { domain(it) }.filter { it.isNotEmpty() }
        if (domain(company.website).let { it.isNotEmpty() && it in sites }) score += WEBSITE
        val mailDomains = contact.emails.map { it.substringAfter('@', "").lowercase() }.filter { it.isNotEmpty() && it !in PUBLIC_MAIL }
        if (company.emailDomain.lowercase().let { it.isNotEmpty() && it !in PUBLIC_MAIL && it in mailDomains }) score += EMAIL
        val name = normalizeName(contact.company)
        if (name.isNotEmpty() && name == normalizeName(company.name)) score += NAME
        return score.takeIf { it > 0 }
    }

    /** "ACME Polska Sp. z o.o." → "acmepolska": no legal form, case, Polish letters or punctuation. */
    fun normalizeName(name: String): String {
        val words = fold(name).replace(Regex("[^a-z0-9]+"), " ").split(' ').filter { it.isNotEmpty() }
        // "S.A." and "z o.o." split into single letters: join each run of them ("sa", "zoo").
        val joined = mutableListOf<String>()
        var run = false
        for (word in words) {
            if (word.length == 1 && run) {
                joined[joined.lastIndex] = joined.last() + word
            } else {
                joined += word
            }
            run = word.length == 1
        }
        return joined.filter { it !in LEGAL_FORM_WORDS }.joinToString("")
    }

    /** "https://www.Acme.pl/kontakt" → "acme.pl". */
    fun domain(url: String): String =
        url.trim().lowercase().substringAfter("://").removePrefix("www.").substringBefore('/').substringBefore('?')

    private const val NIP = 100
    private const val WEBSITE = 8
    private const val EMAIL = 4
    private const val NAME = 2

    private val LEGAL_FORM_WORDS = setOf(
        "sp", "z", "o", "oo", "zoo", "spolka", "sa", "sc", "sk", "spk", "spj", "ska", "akcyjna", "jawna", "komandytowa",
        "ltd", "llc", "inc", "gmbh", "ag", "plc", "corp", "corporation", "limited", "co",
    )

    private val PUBLIC_MAIL = setOf(
        "gmail.com", "googlemail.com", "outlook.com", "hotmail.com", "live.com", "yahoo.com", "icloud.com", "me.com",
        "proton.me", "protonmail.com", "wp.pl", "o2.pl", "onet.pl", "op.pl", "interia.pl", "poczta.fm", "gazeta.pl",
        "tlen.pl", "vp.pl", "int.pl", "onet.eu", "buziaczek.pl",
    )
}
