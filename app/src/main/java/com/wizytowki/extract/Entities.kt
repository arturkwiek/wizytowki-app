package com.wizytowki.extract

/** Kind of contact data found in the OCR text (WF-12). NIP, name and position arrive in #16. */
enum class EntityType { PHONE, EMAIL, WEBSITE, SOCIAL, ADDRESS }

enum class PhoneKind { MOBILE, LANDLINE, FAX, UNKNOWN }

enum class Side { FRONT, BACK }

/**
 * One extracted value.
 * - [value]: normalized form to store (phone in E.164 `+48600123456`, lowercase e-mail, URL without
 *   trailing punctuation, address lines joined with ", ").
 * - [raw]: text as read by OCR, for display and for conflict detection (#17).
 * - [confidence]: 0..1 – the OCR confidence of the source lines times the rule's own certainty (WF-19 in #19).
 * - [phoneKind]: only for [EntityType.PHONE].
 */
data class Entity(
    val type: EntityType,
    val value: String,
    val raw: String,
    val side: Side,
    val confidence: Float,
    val phoneKind: PhoneKind? = null,
)
