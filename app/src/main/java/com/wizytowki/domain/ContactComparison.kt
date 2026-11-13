package com.wizytowki.domain

import com.wizytowki.extract.EntityType
import java.text.Normalizer

/** A contact already in the database (#98). */
data class SavedContact(val personId: Long, val companyId: Long?, val contact: ContactDraft)

enum class MatchReason { EMAIL, PHONE, NAME }

data class DuplicateMatch(val saved: SavedContact, val reasons: Set<MatchReason>)

/**
 * Finds the saved person a new scan belongs to (WF-32): the same e-mail, the same phone or the
 * same first and last name (ignoring case and Polish letters). E-mail weighs most, then phone,
 * then name; with several candidates the one matching on most criteria wins.
 */
object DuplicateFinder {

    fun find(new: ContactDraft, saved: List<SavedContact>): DuplicateMatch? = saved
        .map { DuplicateMatch(it, reasons(new, it.contact)) }
        .filter { it.reasons.isNotEmpty() }
        .maxByOrNull { match -> match.reasons.sumOf { WEIGHTS.getValue(it) } }

    fun reasons(a: ContactDraft, b: ContactDraft): Set<MatchReason> = buildSet {
        val emails = a.emails.map { it.trim().lowercase() }.toSet()
        if (b.emails.any { it.trim().lowercase() in emails }) add(MatchReason.EMAIL)
        if (a.phones.toSet().intersect(b.phones.toSet()).isNotEmpty()) add(MatchReason.PHONE)
        val nameA = fold(a.firstName) to fold(a.lastName)
        if (nameA.first.isNotEmpty() && nameA.second.isNotEmpty() && nameA == fold(b.firstName) to fold(b.lastName)) {
            add(MatchReason.NAME)
        }
    }

    private val WEIGHTS = mapOf(MatchReason.EMAIL to 4, MatchReason.PHONE to 2, MatchReason.NAME to 1)
}

/** Contact fields shown side by side on the duplicate screen. */
enum class ContactField(val type: EntityType, val multi: Boolean = false) {
    FIRST_NAME(EntityType.FIRST_NAME),
    LAST_NAME(EntityType.LAST_NAME),
    COMPANY(EntityType.COMPANY),
    POSITION_PL(EntityType.POSITION),
    POSITION_EN(EntityType.POSITION),
    DEPARTMENT_PL(EntityType.DEPARTMENT),
    DEPARTMENT_EN(EntityType.DEPARTMENT),
    PHONES(EntityType.PHONE, multi = true),
    EMAILS(EntityType.EMAIL, multi = true),
    WEBSITES(EntityType.WEBSITE, multi = true),
    SOCIAL(EntityType.SOCIAL, multi = true),
    ADDRESS_PL(EntityType.ADDRESS),
    ADDRESS_EN(EntityType.ADDRESS),
    NIP(EntityType.NIP),
}

/** Which value a field keeps: the saved one, the new one or – for lists – both. */
enum class Choice { SAVED, NEW, BOTH }

/**
 * One field of the comparison. [same] when the values only differ in case, spacing,
 * punctuation or lost Polish letters; [default] is what the screen proposes.
 */
data class FieldDiff(val field: ContactField, val saved: List<String>, val new: List<String>) {
    val same: Boolean
        get() = saved.map { key(it) }.toSet() == new.map { key(it) }.toSet()

    val default: Choice
        get() = when {
            // Same value, but the new scan kept the Polish letters the saved one lost.
            same && !this.field.multi && diacritics(new) > diacritics(saved) -> Choice.NEW
            same || new.isEmpty() -> Choice.SAVED
            saved.isEmpty() -> Choice.NEW
            this.field.multi -> Choice.BOTH
            // A newer card usually carries the current data, e.g. after a job change (WF-32).
            else -> Choice.NEW
        }

    private fun key(value: String) = CardMerger.key(field.type, value)

    private fun diacritics(values: List<String>) = values.sumOf { v -> v.count { it.code > 127 } }
}

/** Comparing a saved contact with a new scan and building the result of the user's choices (WF-15, WF-32). */
object ContactComparison {

    /** All fields that have a value on either side, in screen order. */
    fun diff(saved: ContactDraft, new: ContactDraft): List<FieldDiff> =
        ContactField.entries.map { FieldDiff(it, values(saved, it), values(new, it)) }
            .filter { it.saved.isNotEmpty() || it.new.isNotEmpty() }

    /** The contact after applying [choices]; fields without a choice take [FieldDiff.default]. */
    fun resolve(saved: ContactDraft, new: ContactDraft, choices: Map<ContactField, Choice> = emptyMap()): ContactDraft {
        var result = saved
        for (diff in diff(saved, new)) {
            val chosen = when (choices[diff.field] ?: diff.default) {
                Choice.SAVED -> diff.saved
                Choice.NEW -> diff.new
                Choice.BOTH -> (diff.saved + diff.new).distinctBy { CardMerger.key(diff.field.type, it) }
            }
            result = result.with(diff.field, chosen)
        }
        return result
    }

    /** The same choice for every field: keep the saved entry or take the new one as a whole. */
    fun all(saved: ContactDraft, new: ContactDraft, choice: Choice): Map<ContactField, Choice> =
        diff(saved, new).associate { it.field to choice }

    fun values(contact: ContactDraft, field: ContactField): List<String> = when (field) {
        ContactField.FIRST_NAME -> listOf(contact.firstName)
        ContactField.LAST_NAME -> listOf(contact.lastName)
        ContactField.COMPANY -> listOf(contact.company)
        ContactField.POSITION_PL -> listOf(contact.positionPl)
        ContactField.POSITION_EN -> listOf(contact.positionEn)
        ContactField.DEPARTMENT_PL -> listOf(contact.departmentPl)
        ContactField.DEPARTMENT_EN -> listOf(contact.departmentEn)
        ContactField.PHONES -> contact.phones
        ContactField.EMAILS -> contact.emails
        ContactField.WEBSITES -> contact.websites
        ContactField.SOCIAL -> contact.social
        ContactField.ADDRESS_PL -> listOf(contact.addressPl)
        ContactField.ADDRESS_EN -> listOf(contact.addressEn)
        ContactField.NIP -> listOf(contact.nip)
    }.filter { it.isNotBlank() }

    private fun ContactDraft.with(field: ContactField, values: List<String>): ContactDraft {
        val one = values.firstOrNull().orEmpty()
        return when (field) {
            ContactField.FIRST_NAME -> copy(firstName = one)
            ContactField.LAST_NAME -> copy(lastName = one)
            ContactField.COMPANY -> copy(company = one)
            ContactField.POSITION_PL -> copy(positionPl = one)
            ContactField.POSITION_EN -> copy(positionEn = one)
            ContactField.DEPARTMENT_PL -> copy(departmentPl = one)
            ContactField.DEPARTMENT_EN -> copy(departmentEn = one)
            ContactField.PHONES -> copy(phones = values)
            ContactField.EMAILS -> copy(emails = values)
            ContactField.WEBSITES -> copy(websites = values)
            ContactField.SOCIAL -> copy(social = values)
            ContactField.ADDRESS_PL -> copy(addressPl = one)
            ContactField.ADDRESS_EN -> copy(addressEn = one)
            ContactField.NIP -> copy(nip = one)
        }
    }
}

/** Lowercase without diacritics, for comparing names. */
internal fun fold(text: String): String =
    Normalizer.normalize(text.trim().lowercase().replace('ł', 'l'), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
