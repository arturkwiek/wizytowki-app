package com.wizytowki.domain

import com.wizytowki.extract.EntityType
import com.wizytowki.ocr.Language

/**
 * A contact as it is saved and compared (#98): the person, their company and current position.
 * Language-dependent fields keep both versions (WF-16); phones, e-mails, websites and profiles
 * may have several values, the rest one.
 */
data class ContactDraft(
    val firstName: String = "",
    val lastName: String = "",
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val websites: List<String> = emptyList(),
    val social: List<String> = emptyList(),
    val company: String = "",
    val nip: String = "",
    val positionPl: String = "",
    val positionEn: String = "",
    val departmentPl: String = "",
    val departmentEn: String = "",
    val addressPl: String = "",
    val addressEn: String = "",
    /** The user's own note, typed or dictated (WF-23). Not compared between scans: notes add up. */
    val comment: String = "",
) {
    /** Anything belonging to the company record: name, NIP, address or position there. */
    val hasCompanyData: Boolean
        get() = listOf(company, nip, addressPl, addressEn, positionPl, positionEn, departmentPl, departmentEn)
            .any { it.isNotBlank() }

    /** Trimmed values without empty list entries, as saved after editing (WF-20). */
    fun cleaned(): ContactDraft = copy(
        firstName = firstName.trim(),
        lastName = lastName.trim(),
        phones = phones.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
        emails = emails.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
        websites = websites.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
        social = social.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
        company = company.trim(),
        nip = nip.trim(),
        positionPl = positionPl.trim(),
        positionEn = positionEn.trim(),
        departmentPl = departmentPl.trim(),
        departmentEn = departmentEn.trim(),
        addressPl = addressPl.trim(),
        addressEn = addressEn.trim(),
        comment = comment.trim(),
    )

    /** Dictated text added to the comment (WF-23), separated by a space. */
    fun withDictation(text: String): ContactDraft {
        val spoken = text.trim()
        if (spoken.isEmpty()) return this
        return copy(comment = if (comment.isBlank()) spoken else comment.trimEnd() + " " + spoken)
    }

    companion object {
        /**
         * The recognized card as a contact. A value of unknown language goes to the free version,
         * Polish first – editing and moving it between languages comes in Etap 3 (WF-20, WF-21).
         */
        fun from(card: MergedCard): ContactDraft {
            val (positionPl, positionEn) = versions(card, EntityType.POSITION)
            val (departmentPl, departmentEn) = versions(card, EntityType.DEPARTMENT)
            val (addressPl, addressEn) = versions(card, EntityType.ADDRESS)
            return ContactDraft(
                firstName = card.field(EntityType.FIRST_NAME)?.value.orEmpty(),
                lastName = card.field(EntityType.LAST_NAME)?.value.orEmpty(),
                phones = card.all(EntityType.PHONE).map { it.value },
                emails = card.all(EntityType.EMAIL).map { it.value },
                websites = card.all(EntityType.WEBSITE).map { it.value },
                social = card.all(EntityType.SOCIAL).map { it.value },
                company = card.field(EntityType.COMPANY)?.value.orEmpty(),
                nip = card.field(EntityType.NIP)?.value.orEmpty(),
                positionPl = positionPl,
                positionEn = positionEn,
                departmentPl = departmentPl,
                departmentEn = departmentEn,
                addressPl = addressPl,
                addressEn = addressEn,
            )
        }

        private fun versions(card: MergedCard, type: EntityType): Pair<String, String> {
            val fields = card.all(type)
            var pl = fields.firstOrNull { it.language == Language.PL }?.value.orEmpty()
            var en = fields.firstOrNull { it.language == Language.EN }?.value.orEmpty()
            val unknown = fields.firstOrNull { it.language == null }?.value.orEmpty()
            if (unknown.isNotEmpty()) {
                if (pl.isEmpty()) pl = unknown else if (en.isEmpty()) en = unknown
            }
            return pl to en
        }
    }
}
