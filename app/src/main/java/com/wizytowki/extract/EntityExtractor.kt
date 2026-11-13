package com.wizytowki.extract

import com.wizytowki.ocr.CardOcr
import com.wizytowki.ocr.Language
import com.wizytowki.ocr.OcrLine
import com.wizytowki.ocr.OcrPage
import javax.inject.Inject

/**
 * Extracts contact data from OCR text (WF-12, WF-13): phones, e-mails, websites, social profiles and
 * postal addresses here; NIP, name, position, department and company in [CardFieldRules].
 *
 * Own offline rules instead of ML Kit Entity Extraction: that library has no bundled model and
 * downloads it from the app process, which needs the INTERNET permission the app does not have
 * (WNF-01, AGENTS.md) – see docs/decyzje/0002-ekstrakcja-encji.md.
 */
class EntityExtractor @Inject constructor() {

    fun extract(card: CardOcr): List<Entity> =
        extract(card.front, Side.FRONT) + (card.back?.let { extract(it, Side.BACK) } ?: emptyList())

    fun extract(page: OcrPage, side: Side): List<Entity> {
        val found = mutableListOf<Entity>()
        for (block in page.blocks) {
            block.lines.forEach { line ->
                val text = line.text
                val emails = emails(text)
                found += emails.map { (value, raw, certain) -> entity(EntityType.EMAIL, value, raw, side, line, certain) }
                val emailSpans = EMAIL.findAll(normalizeAt(text)).map { it.range }.toList()
                found += websites(normalizeAt(text), emailSpans).map { (type, value, raw, certain) ->
                    entity(type, value, raw, side, line, certain)
                }
                if (!NOT_PHONE_LABEL.containsMatchIn(text)) {
                    found += phones(text).map { (value, raw, kind) ->
                        entity(EntityType.PHONE, value, raw, side, line, PHONE_CERTAINTY, kind)
                    }
                }
            }
        }
        found += addresses(page, side)
        val contacts = found
            .groupBy { it.type to it.value }
            .map { (_, same) -> same.maxBy { it.confidence } }
        return (CardFieldRules.extract(page, side, contacts) + contacts).sortedBy { it.type.ordinal }
    }

    // --- phones -----------------------------------------------------------------------------

    private fun phones(text: String): List<Triple<String, String, PhoneKind>> {
        val result = mutableListOf<Triple<String, String, PhoneKind>>()
        var labelFrom = 0
        PHONE.findAll(text).forEach { match ->
            val raw = match.value.trim()
            val number = normalizePhone(raw)
            if (number != null) {
                val label = text.substring(labelFrom, match.range.first)
                result += Triple(number, raw, phoneKind(number, label))
            }
            labelFrom = match.range.last + 1
        }
        return result
    }

    /** E.164 form, or null when the digits are not a plausible phone (postal code, NIP, account, date). */
    internal fun normalizePhone(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        val international = raw.trimStart('(').startsWith("+") || digits.startsWith("00")
        return when {
            international -> {
                val number = if (digits.startsWith("00")) digits.drop(2) else digits
                when {
                    number.startsWith("48") && number.length != 11 -> null
                    number.length !in 8..15 -> null
                    else -> "+$number"
                }
            }
            digits.length == 9 && digits[0] != '0' -> "+48$digits"
            digits.length == 10 && digits[0] == '0' && digits[1] != '0' -> "+48${digits.drop(1)}"
            digits.length == 11 && digits.startsWith("48") -> "+$digits"
            else -> null
        }
    }

    private fun phoneKind(number: String, label: String): PhoneKind {
        val lastLabel = PHONE_LABEL.findAll(label).lastOrNull()?.value?.lowercase()?.trimEnd('.', ':')
        return when {
            lastLabel != null && lastLabel in FAX_LABELS -> PhoneKind.FAX
            lastLabel != null && lastLabel in MOBILE_LABELS -> PhoneKind.MOBILE
            number.startsWith("+48") -> if (number.substring(3, 5) in PL_MOBILE_PREFIXES) PhoneKind.MOBILE else PhoneKind.LANDLINE
            else -> PhoneKind.UNKNOWN
        }
    }

    // --- e-mails and websites ---------------------------------------------------------------

    private data class Found(val value: String, val raw: String, val certainty: Float)

    /** OCR often puts spaces around "@" ("jan @ firma.pl"). */
    private fun normalizeAt(text: String) = text.replace(SPACED_AT, "@")

    private fun emails(text: String): List<Found> {
        val fixed = normalizeAt(text)
        val certainty = if (fixed != text) EMAIL_FIXED_CERTAINTY else EMAIL_CERTAINTY
        return EMAIL.findAll(fixed).map { Found(it.value.trimEnd('.').lowercase(), it.value, certainty) }.toList()
    }

    private data class Site(val type: EntityType, val value: String, val raw: String, val certainty: Float)

    private fun websites(text: String, emailSpans: List<IntRange>): List<Site> =
        URL.findAll(text)
            .filter { m -> emailSpans.none { m.range.first in it || m.range.last in it } }
            .filter { m -> m.range.first == 0 || text[m.range.first - 1] != '@' }
            .map { m ->
                val raw = m.value.trimEnd('.', ',', ';', ')')
                val value = lowercaseHost(raw)
                val host = host(value).removePrefix("www.")
                val social = SOCIAL_HOSTS.any { host == it || host.endsWith(".$it") }
                val explicit = value.startsWith("http") || value.startsWith("www.")
                Site(
                    if (social) EntityType.SOCIAL else EntityType.WEBSITE,
                    value,
                    raw,
                    if (explicit || social) URL_CERTAINTY else URL_BARE_CERTAINTY,
                )
            }.toList()

    private fun hostRange(url: String): IntRange {
        val schemeEnd = url.indexOf("://").let { if (it < 0) 0 else it + 3 }
        val pathStart = url.indexOf('/', schemeEnd).let { if (it < 0) url.length else it }
        return schemeEnd until pathStart
    }

    private fun host(url: String) = url.substring(hostRange(url))

    private fun lowercaseHost(url: String): String {
        val end = hostRange(url).last + 1
        return url.substring(0, end).lowercase() + url.substring(end)
    }

    // --- addresses --------------------------------------------------------------------------

    /** A line of the page with its OCR block, so an address can be joined across blocks. */
    private class PageLine(val line: OcrLine, val block: Int, val language: Language?) {
        val text: String get() = line.text
    }

    /**
     * Addresses: a line with a postal code (00-000, 5-digit, UK) together with the street line
     * above it; a lone street line ("ul. Polna 5", "10 Baker Street") counts with lower certainty.
     * A following country line (Polska, Poland, …) is appended. Lines are joined within an OCR
     * block, and across blocks only when one sits right under the other ([joinable]) – ML Kit
     * often puts the street and the postal code into separate blocks. The address takes the
     * language of its parts' blocks (WF-16), PL or EN when any part has one.
     */
    private fun addresses(page: OcrPage, side: Side): List<Entity> {
        val lines = page.blocks.flatMapIndexed { b, block -> block.lines.map { PageLine(it, b, block.language?.language) } }
        val result = mutableListOf<Entity>()
        val used = mutableSetOf<Int>()
        lines.forEachIndexed { i, line ->
            if (i in used) return@forEachIndexed
            val text = line.text.replace(ADDRESS_LABEL, "").trim()
            val hasPostal = hasPostalCode(text)
            val isStreet = looksLikeStreet(text)
            if (!hasPostal && !isStreet) return@forEachIndexed
            if (isContact(text)) return@forEachIndexed

            val parts = mutableListOf(i)
            if (hasPostal && !isStreet && i > 0 && i - 1 !in used && looksLikeStreet(lines[i - 1].text) &&
                joinable(lines[i - 1], line)
            ) {
                parts.add(0, i - 1)
            }
            if (!hasPostal && i + 1 < lines.size && joinable(line, lines[i + 1])) {
                val next = lines[i + 1].text
                if (hasPostalCode(next) && !isContact(next)) parts += i + 1
            }
            val last = parts.last()
            if (last + 1 < lines.size && joinable(lines[last], lines[last + 1]) && COUNTRY.matches(lines[last + 1].text.trim())) {
                parts += last + 1
            }

            used += parts
            val partLines = parts.map { lines[it] }
            val value = partLines.joinToString(", ") { it.text.replace(ADDRESS_LABEL, "").trim().trimEnd(',') }
            val complete = partLines.any { hasPostalCode(it.text) }
            val ocr = partLines.minOf { it.line.confidence ?: DEFAULT_OCR_CONFIDENCE }
            val language = partLines.map { it.language }.firstOrNull { it == Language.PL || it == Language.EN }
                ?: partLines.first().language
            result += Entity(
                EntityType.ADDRESS, value, partLines.joinToString("\n") { it.text }, side,
                ocr * if (complete) ADDRESS_CERTAINTY else STREET_ONLY_CERTAINTY,
                language = language,
            )
        }
        return result
    }

    private fun hasPostalCode(text: String) = PL_POSTAL.containsMatchIn(text) || FOREIGN_POSTAL_CITY.containsMatchIn(text)

    /**
     * Two consecutive lines can belong to one address: they are in the same OCR block, or the
     * lower one starts at most 1.5 line heights below the upper one and they overlap horizontally
     * (not a neighbouring column). Without positions (tests) the reading order is trusted.
     */
    private fun joinable(upper: PageLine, lower: PageLine): Boolean {
        if (upper.block == lower.block) return true
        val a = upper.line.box ?: return true
        val b = lower.line.box ?: return true
        val height = maxOf(a.height, b.height)
        val gap = b.top - a.bottom
        val overlap = minOf(a.right, b.right) - maxOf(a.left, b.left)
        return gap >= -height / 2 && gap <= height * 3 / 2 && overlap > 0
    }

    private fun looksLikeStreet(text: String): Boolean {
        val t = text.replace(ADDRESS_LABEL, "").trim()
        if (isContact(t)) return false
        return PL_STREET.containsMatchIn(t) || FOREIGN_STREET.containsMatchIn(t)
    }

    private fun isContact(text: String) =
        EMAIL.containsMatchIn(normalizeAt(text)) || CONTACT_LABEL.containsMatchIn(text) ||
            phones(text).isNotEmpty() && !PL_POSTAL.containsMatchIn(text)

    private fun entity(
        type: EntityType,
        value: String,
        raw: String,
        side: Side,
        line: OcrLine,
        certainty: Float,
        kind: PhoneKind? = null,
    ) = Entity(type, value, raw, side, (line.confidence ?: DEFAULT_OCR_CONFIDENCE) * certainty, kind)

    private companion object {
        const val DEFAULT_OCR_CONFIDENCE = 0.8f
        const val PHONE_CERTAINTY = 0.95f
        const val EMAIL_CERTAINTY = 0.98f
        const val EMAIL_FIXED_CERTAINTY = 0.85f
        const val URL_CERTAINTY = 0.95f
        const val URL_BARE_CERTAINTY = 0.75f
        const val ADDRESS_CERTAINTY = 0.9f
        const val STREET_ONLY_CERTAINTY = 0.6f

        val PHONE = Regex("\\(?(?:\\+|00)?\\(?\\d[\\d \\-.()]{6,}\\d")
        val PHONE_LABEL = Regex("(?i)\\b(tel|telefon|phone|mob|mobile|kom|komórka|gsm|cell|fax|faks|[tmfp])\\b\\.?:?")
        val FAX_LABELS = setOf("fax", "faks", "f")
        val MOBILE_LABELS = setOf("mob", "mobile", "kom", "komórka", "gsm", "cell", "m")
        val NOT_PHONE_LABEL = Regex("(?i)\\b(nip|regon|krs|pesel|iban|konto|nr konta|bdo|vat)\\b")
        val PL_MOBILE_PREFIXES = setOf("45", "50", "51", "53", "57", "60", "66", "69", "72", "73", "78", "79", "88")

        val SPACED_AT = Regex("\\s*@\\s*")
        val EMAIL = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(?:\\.[A-Za-z0-9\\-]+)*\\.[A-Za-z]{2,}")
        val URL = Regex(
            "(?i)\\b(?:https?://)?(?:www\\.)?[a-z0-9][a-z0-9\\-]*(?:\\.[a-z0-9\\-]+)*" +
                "\\.(?:pl|com|eu|net|org|io|info|biz|de|uk|co|online|tech|app|dev|gov|edu)\\b(?:/[^\\s,;]*)?",
        )
        val SOCIAL_HOSTS = listOf(
            "linkedin.com", "facebook.com", "fb.com", "instagram.com", "twitter.com", "x.com", "github.com", "youtube.com",
        )

        val ADDRESS_LABEL = Regex("(?i)^\\s*(adres|address|siedziba|office)\\s*:?\\s*")
        val CONTACT_LABEL = Regex("(?i)\\b(tel|mob|kom|fax|e-mail|email|www|nip|regon|krs)\\b")
        val PL_POSTAL = Regex("(?<![\\d-])\\d{2}-\\d{3}(?![\\d-])")
        val FOREIGN_POSTAL_CITY = Regex(
            "(?<![\\d-])\\d{5}\\s+\\p{Lu}\\p{L}+|\\b[A-Z]{1,2}\\d[A-Z\\d]?\\s?\\d[A-Z]{2}\\b",
        )
        val PL_STREET = Regex(
            "(?i)(^|\\s)(ul|al|pl|os|ulica|aleja|plac|osiedle|rondo)\\.?\\s+\\p{L}.*\\d",
        )
        val FOREIGN_STREET = Regex(
            "(?i)\\d+\\w?\\s+.*\\b(street|st|road|rd|avenue|ave|lane|ln|boulevard|blvd|drive|dr|way|square|sq)\\b\\.?" +
                "|\\b\\p{L}+(straße|strasse|str\\.)\\s*\\d+",
        )
        val COUNTRY = Regex("(?i)polska|poland|germany|deutschland|niemcy|united kingdom|uk|usa|united states|czech republic|czechy")
    }
}
