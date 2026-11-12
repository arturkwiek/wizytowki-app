package com.wizytowki.accuracy

import com.wizytowki.domain.MergedCard
import com.wizytowki.domain.MergedField
import com.wizytowki.extract.EntityType
import com.wizytowki.ocr.Language

/** Expected result for one test card – the `test-data/expected/NNN.json` format. */
data class ExpectedCard(
    val firstName: String? = null,
    val lastName: String? = null,
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val websites: List<String> = emptyList(),
    val social: List<String> = emptyList(),
    val companyName: String? = null,
    val nip: String? = null,
    val position: Map<Language, String> = emptyMap(),
    val department: Map<Language, String> = emptyMap(),
    val address: Map<Language, String> = emptyMap(),
)

/** One checked field: [expected] null means the card has no such field but something was recognized. */
data class FieldCheck(val card: String, val field: String, val expected: String?, val actual: String?, val ok: Boolean)

data class AccuracyReport(val checks: List<FieldCheck>) {
    val correct: Int get() = checks.count { it.ok }
    val total: Int get() = checks.size
    val accuracy: Double get() = if (total == 0) 0.0 else correct.toDouble() / total
    val errors: List<FieldCheck> get() = checks.filter { !it.ok }

    operator fun plus(other: AccuracyReport) = AccuracyReport(checks + other.checks)

    fun describe(): String = buildString {
        appendLine("Accuracy: $correct/$total = ${"%.1f".format(accuracy * 100)}%")
        errors.forEach { appendLine("  ${it.card} ${it.field}: expected \"${it.expected}\", got \"${it.actual}\"") }
    }
}

/**
 * Field accuracy of the recognized card against the expected one (Etap 2 criterion: ≥ 90 %).
 *
 * Every expected value is one point (each phone and e-mail separately). A single-valued field
 * recognized although the card does not have it (e.g. a slogan taken for the company) costs a
 * point as well; extra phones or e-mails do not. Comparison ignores case, spacing and
 * punctuation but not Polish letters – "Łódź" read as "Lodz" is an error (WF-10).
 * Language versions (WF-16): the expected PL text must be in the PL slot or in the slot of
 * unknown language, the same for EN.
 */
object AccuracyScorer {

    fun score(name: String, expected: ExpectedCard, card: MergedCard): AccuracyReport {
        val checks = mutableListOf<FieldCheck>()
        fun single(field: String, type: EntityType, want: String?, key: (String) -> String = ::text) {
            val got = card.field(type)?.value
            when {
                want != null -> checks += FieldCheck(name, field, want, got, got != null && key(got) == key(want))
                got != null -> checks += FieldCheck(name, field, null, got, false)
            }
        }
        fun many(field: String, type: EntityType, want: List<String>, key: (String) -> String) {
            val got = card.all(type).map { it.value }
            want.forEach { w -> checks += FieldCheck(name, field, w, got.joinToString(), got.any { key(it) == key(w) }) }
        }
        fun versions(field: String, type: EntityType, want: Map<Language, String>, multi: Boolean) {
            for (language in listOf(Language.PL, Language.EN)) {
                val slot = card.all(type).filter { it.language == language || it.language == null }
                val w = want[language]
                when {
                    w != null -> checks += FieldCheck(name, "$field.$language", w, slot.values(), slot.any { text(it.value) == text(w) })
                    !multi && card.all(type).any { it.language == language } ->
                        checks += FieldCheck(name, "$field.$language", null, card.field(type, language)?.value, false)
                }
            }
        }

        single("firstName", EntityType.FIRST_NAME, expected.firstName)
        single("lastName", EntityType.LAST_NAME, expected.lastName)
        single("company", EntityType.COMPANY, expected.companyName)
        single("nip", EntityType.NIP, expected.nip, ::digits)
        many("phone", EntityType.PHONE, expected.phones, ::phone)
        many("email", EntityType.EMAIL, expected.emails) { it.trim().lowercase() }
        many("website", EntityType.WEBSITE, expected.websites, ::url)
        many("social", EntityType.SOCIAL, expected.social, ::url)
        versions("position", EntityType.POSITION, expected.position, multi = false)
        versions("department", EntityType.DEPARTMENT, expected.department, multi = false)
        versions("address", EntityType.ADDRESS, expected.address, multi = true)
        return AccuracyReport(checks)
    }

    private fun List<MergedField>.values(): String? = joinToString(" | ") { it.value }.ifEmpty { null }

    internal fun text(value: String) = value.lowercase().filter { it.isLetterOrDigit() }

    private fun digits(value: String) = value.filter { it.isDigit() }

    /** "+48 600 123 456", "600-123-456" and "+48600123456" are the same number. */
    internal fun phone(value: String): String {
        val d = digits(value)
        return when {
            value.trim().startsWith("+") -> d
            d.startsWith("00") -> d.drop(2)
            d.length == 9 -> "48$d"
            d.length == 10 && d.startsWith("0") -> "48${d.drop(1)}"
            else -> d
        }
    }

    internal fun url(value: String) = value.trim().lowercase()
        .removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
}
