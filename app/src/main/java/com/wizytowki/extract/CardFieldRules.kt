package com.wizytowki.extract

import com.wizytowki.ocr.Language
import com.wizytowki.ocr.OcrLine
import com.wizytowki.ocr.OcrPage
import java.text.Normalizer

/**
 * Rules for fields with no fixed format (WF-12, WF-13): NIP, first and last name, position,
 * department and company. Run per card side after the contact entities, which they use as hints
 * (an e-mail "jan.kowalski@acme.pl" confirms the name and the company).
 */
internal object CardFieldRules {

    fun extract(page: OcrPage, side: Side, contacts: List<Entity>): List<Entity> {
        val lines = page.blocks.flatMap { block -> block.lines.map { Line(it, block.language?.language) } }
        val result = mutableListOf<Entity>()
        result += nips(lines, side)

        val emailLocals = contacts.filter { it.type == EntityType.EMAIL }.map { fold(it.value.substringBefore('@')) }
        val domains = contacts.filter { it.type == EntityType.EMAIL || it.type == EntityType.WEBSITE }
            .map { fold(domainName(it.value)) }.filter { it.length >= 3 }.toSet()

        val free = lines.filter { isFreeText(it.text) }
        val companies = companies(free, side, domains)
        result += companies
        val companyTexts = companies.map { it.raw }.toSet()

        free.filter { it.text !in companyTexts }.forEach { line ->
            val text = line.text.trim()
            when {
                POSITION.containsMatchIn(text) -> result += fieldEntity(EntityType.POSITION, text, side, line, POSITION_CERTAINTY)
                DEPARTMENT.containsMatchIn(text) -> result += fieldEntity(EntityType.DEPARTMENT, text, side, line, DEPARTMENT_CERTAINTY)
            }
        }
        val taken = result.map { it.raw }.toSet()
        result += name(free.filter { it.text.trim() !in taken }, side, emailLocals, lines)
        return result
    }

    private data class Line(val ocr: OcrLine, val language: Language?) {
        val text: String get() = ocr.text
    }

    // --- NIP --------------------------------------------------------------------------------

    private fun nips(lines: List<Line>, side: Side): List<Entity> = lines.flatMap { line ->
        val labeled = NIP_LABEL.containsMatchIn(line.text)
        NIP_NUMBER.findAll(line.text).mapNotNull { m ->
            val digits = m.value.filter { it.isDigit() }
            if (digits.length != 10) return@mapNotNull null
            val valid = isValidNip(digits)
            val grouped = NIP_GROUPED.matches(m.value.trim().removePrefix("PL").trim())
            val certainty = when {
                labeled && valid -> 0.98f
                labeled -> NIP_BAD_CHECKSUM_CERTAINTY
                valid && grouped -> 0.7f
                else -> return@mapNotNull null
            }
            Entity(EntityType.NIP, digits, m.value.trim(), side, ocrConfidence(line) * certainty)
        }.toList()
    }

    /** Polish NIP checksum: weights 6,5,7,2,3,4,5,6,7, sum mod 11 equals the last digit. */
    fun isValidNip(digits: String): Boolean {
        if (digits.length != 10 || digits.any { !it.isDigit() }) return false
        val sum = NIP_WEIGHTS.indices.sumOf { NIP_WEIGHTS[it] * (digits[it] - '0') }
        val check = sum % 11
        return check != 10 && check == digits[9] - '0'
    }

    // --- company ----------------------------------------------------------------------------

    private fun companies(lines: List<Line>, side: Side, domains: Set<String>): List<Entity> {
        val legal = lines.filter { LEGAL_FORM.containsMatchIn(it.text) || COMPANY_WORD.containsMatchIn(it.text) }
        if (legal.isNotEmpty()) {
            return legal.map { fieldEntity(EntityType.COMPANY, it.text.trim(), side, it, COMPANY_CERTAINTY) }
        }
        val byDomain = lines.firstOrNull { line -> fold(line.text).replace(" ", "").let { it.length >= 3 && it in domains } }
        return listOfNotNull(byDomain?.let { fieldEntity(EntityType.COMPANY, it.text.trim(), side, it, COMPANY_DOMAIN_CERTAINTY) })
    }

    private fun domainName(value: String): String {
        val host = value.substringAfter('@').substringAfter("://").removePrefix("www.").substringBefore('/')
        val parts = host.split('.')
        // "acme.com.pl" → "acme", "acme.pl" → "acme"
        return parts.dropLast(1).lastOrNull { it !in SECOND_LEVEL } ?: parts.first()
    }

    // --- name -------------------------------------------------------------------------------

    private fun name(candidates: List<Line>, side: Side, emailLocals: List<String>, all: List<Line>): List<Entity> {
        val maxHeight = candidates.mapNotNull { it.ocr.box?.height }.maxOrNull()
        val best = candidates.mapNotNull { line ->
            val tokens = nameTokens(line.text) ?: return@mapNotNull null
            val folded = tokens.map { fold(it) }
            var score = NAME_BASE
            if (folded.any { it in FIRST_NAMES }) score += 0.25f
            if (emailLocals.any { local -> folded.any { it.length >= 3 && local.contains(it) } }) score += 0.2f
            val next = all.getOrNull(all.indexOf(line) + 1)?.text
            if (next != null && POSITION.containsMatchIn(next)) score += 0.05f
            if (maxHeight != null && line.ocr.box?.height == maxHeight) score += 0.1f
            Triple(line, tokens, score)
        }.maxByOrNull { it.third } ?: return emptyList()

        val (line, tokens, score) = best
        val (first, last) = splitName(tokens)
        val confidence = ocrConfidence(line) * minOf(score, MAX_NAME_CERTAINTY)
        return listOf(
            Entity(EntityType.FIRST_NAME, first, line.text.trim(), side, confidence),
            Entity(EntityType.LAST_NAME, last, line.text.trim(), side, confidence),
        )
    }

    /** 2–3 capitalized words after removing titles (dr, mgr inż., PhD), or null when it is not a name. */
    private fun nameTokens(text: String): List<String>? {
        var t = text.trim().replace(NAME_SUFFIX, "")
        while (true) {
            val stripped = t.replace(TITLE_PREFIX, "")
            if (stripped == t) break
            t = stripped
        }
        val tokens = t.trim().split(Regex("\\s+"))
        if (tokens.size !in 2..3) return null
        if (!tokens.all { NAME_WORD.matches(it) }) return null
        if (tokens.any { fold(it) in NOT_NAME_WORDS }) return null
        return tokens
    }

    /** First name first, unless the surname is written in capitals ("KOWALSKI Jan") or only the second word is a known first name. */
    private fun splitName(tokens: List<String>): Pair<String, String> {
        val caps = tokens.map { it.length > 1 && it == it.uppercase() }
        val ordered = when {
            caps.first() && !caps.last() -> tokens.drop(1) + tokens.first()
            fold(tokens.first()) !in FIRST_NAMES && fold(tokens.last()) in FIRST_NAMES -> listOf(tokens.last()) + tokens.dropLast(1)
            else -> tokens
        }
        val pretty = ordered.map { word -> if (word == word.uppercase()) titleCase(word) else word }
        return pretty.first() to pretty.drop(1).joinToString(" ")
    }

    private fun titleCase(word: String) = word.split('-').joinToString("-") { part ->
        part.lowercase().replaceFirstChar { it.uppercase() }
    }

    // --- helpers ----------------------------------------------------------------------------

    /** Lines that are not contact data: no digits, e-mail or URL. */
    private fun isFreeText(text: String): Boolean {
        val t = text.trim()
        return t.isNotEmpty() && t.none { it.isDigit() } && '@' !in t && !URL_LIKE.containsMatchIn(t) &&
            !COUNTRY.matches(t)
    }

    private fun fieldEntity(type: EntityType, value: String, side: Side, line: Line, certainty: Float) = Entity(
        type, value.trimEnd(',', ';'), line.text.trim(), side, ocrConfidence(line) * certainty,
        language = if (type.languageDependent) line.language else null,
    )

    private fun ocrConfidence(line: Line) = line.ocr.confidence ?: 0.8f

    /** Lowercase without diacritics, for comparing names with e-mail addresses and dictionaries. */
    fun fold(text: String): String = Normalizer.normalize(text.lowercase().replace('ł', 'l'), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

    private const val POSITION_CERTAINTY = 0.85f
    private const val DEPARTMENT_CERTAINTY = 0.8f
    private const val COMPANY_CERTAINTY = 0.9f
    private const val COMPANY_DOMAIN_CERTAINTY = 0.75f
    private const val NIP_BAD_CHECKSUM_CERTAINTY = 0.4f
    private const val NAME_BASE = 0.45f
    private const val MAX_NAME_CERTAINTY = 0.95f

    private val NIP_WEIGHTS = intArrayOf(6, 5, 7, 2, 3, 4, 5, 6, 7)
    private val NIP_LABEL = Regex("(?iU)\\b(nip|vat|vat[- ]?id|vat no|tax id)\\b")
    private val NIP_NUMBER = Regex("(?iU)(?<![\\d-])(?:PL\\s?)?\\d[\\d \\-]{8,13}\\d(?![\\d-])")
    private val NIP_GROUPED = Regex("\\d{3}-\\d{3}-\\d{2}-\\d{2}|\\d{3}-\\d{2}-\\d{2}-\\d{3}")

    private val URL_LIKE = Regex("(?iU)www\\.|https?://|\\.(pl|com|eu|net|org)\\b")
    private val COUNTRY = Regex("(?iU)polska|poland|germany|deutschland|niemcy|united kingdom|uk|usa|united states")

    private val LEGAL_FORM = Regex(
        "(?iU)\\bsp\\.?\\s*z\\s*o\\.?\\s*o\\.?|\\bs\\.\\s?a\\.|\\bsp\\.\\s?[kjp]\\.|\\bs\\.c\\.|\\bspółka\\b|" +
            "\\b(ltd|llc|inc|gmbh|ag|plc|corp|corporation|limited|s\\.r\\.o)\\b\\.?",
    )
    private val COMPANY_WORD = Regex("(?iU)^(kancelaria|biuro rachunkowe|grupa|fundacja|stowarzyszenie|instytut|uniwersytet|politechnika)\\b")

    private val POSITION = Regex(
        "(?iU)\\b(dyrektor\\w*|kierownik\\w*|prezes\\w*|wiceprezes\\w*|członek zarządu|czlonek zarzadu|prokurent\\w*|" +
            "specjalist\\w*|menedżer\\w*|menadżer\\w*|manager|director|head of|chief|ceo|cto|cfo|coo|cmo|vp|" +
            "vice president|president|owner|właściciel\\w*|wlasciciel\\w*|założyciel\\w*|founder|co-founder|" +
            "konsultant\\w*|consultant|doradca|doradczyni|advisor|adviser|przedstawiciel\\w*|representative|" +
            "inżynier\\w*|inzynier\\w*|engineer|developer|programist\\w*|architekt\\w*|architect|analityk\\w*|analyst|" +
            "koordynator\\w*|coordinator|asystent\\w*|assistant|sekretar\\w*|secretary|księgow\\w*|accountant|" +
            "radca prawny|adwokat|attorney|lawyer|partner|officer|executive|specialist|lead|leader|lider\\w*|" +
            "handlowiec|opiekun\\w* klienta|account manager|sales|projektant\\w*|designer|technik|technician)\\b",
    )
    private val DEPARTMENT = Regex("(?iU)\\b(dział\\w*|dzial\\w*|departament\\w*|pion\\w*|zespół|zespol|wydział\\w*|department|division|team|unit)\\b")

    private val TITLE_PREFIX = Regex("(?iU)^(dr|mgr|inż|inz|prof|hab|lek|med|mr|mrs|ms|miss|sir)\\.?\\s+")
    private val NAME_SUFFIX = Regex("(?iU),?\\s+(phd|mba|msc|bsc|ma|ba|cpa|acca)\\.?$")
    private val NAME_WORD = Regex("\\p{Lu}\\p{Ll}+(-\\p{Lu}\\p{Ll}+)?|\\p{Lu}{2,}(-\\p{Lu}{2,})?")

    private val SECOND_LEVEL = setOf("com", "co", "net", "org", "gov", "edu", "waw", "krakow")

    private val NOT_NAME_WORDS = setOf(
        "solutions", "consulting", "group", "grupa", "systems", "technologies", "technology", "studio", "services",
        "software", "partners", "holding", "polska", "poland", "office", "biuro", "centrum", "center", "centre",
        "international", "global", "media", "marketing", "design", "logistics", "logistyka", "serwis", "service",
        "zarzad", "board", "management", "sales", "sprzedaz", "the", "and", "of", "ul", "al", "street", "road",
    )

    val FIRST_NAMES = setOf(
        // Polish
        "adam", "adrian", "agata", "agnieszka", "aleksander", "aleksandra", "alicja", "andrzej", "aneta", "anna",
        "antoni", "arkadiusz", "artur", "barbara", "bartosz", "beata", "bogdan", "bozena", "cezary", "czeslaw",
        "damian", "daniel", "dariusz", "dawid", "dominik", "dorota", "edyta", "elzbieta", "emilia", "ewa",
        "filip", "grazyna", "grzegorz", "halina", "hanna", "helena", "henryk", "hubert", "irena", "iwona",
        "izabela", "jacek", "jadwiga", "jakub", "jan", "janina", "janusz", "jaroslaw", "jerzy", "joanna",
        "jolanta", "jozef", "julia", "julian", "justyna", "kamil", "kamila", "karol", "karolina", "katarzyna",
        "kazimierz", "kinga", "konrad", "krystyna", "krzysztof", "leszek", "lukasz", "maciej", "magdalena", "malgorzata",
        "marcin", "marek", "maria", "marian", "mariusz", "marta", "marzena", "mateusz", "michal", "michalina",
        "mikolaj", "milena", "miroslaw", "monika", "natalia", "nikola", "norbert", "oliwia", "oskar", "patrycja",
        "patryk", "paulina", "pawel", "piotr", "przemyslaw", "radoslaw", "rafal", "renata", "robert", "roman",
        "ryszard", "sebastian", "slawomir", "stanislaw", "stefan", "sylwia", "szymon", "tadeusz", "teresa", "tomasz",
        "urszula", "wanda", "weronika", "wiktor", "wiktoria", "witold", "wojciech", "zbigniew", "zofia", "zuzanna",
        // English
        "alice", "andrew", "anne", "benjamin", "charles", "charlotte", "chris", "christopher", "daniel", "david",
        "elizabeth", "emily", "emma", "george", "hannah", "harry", "jack", "james", "jane", "jennifer",
        "jessica", "john", "jonathan", "joseph", "kate", "laura", "lucy", "mark", "mary", "matthew",
        "michael", "oliver", "patricia", "paul", "peter", "richard", "robert", "sarah", "sophie", "steven",
        "susan", "thomas", "william",
    )
}
