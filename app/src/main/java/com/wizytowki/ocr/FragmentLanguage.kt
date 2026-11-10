package com.wizytowki.ocr

import javax.inject.Inject

/** Language of one text fragment (WF-11). */
enum class Language {
    PL,
    EN,

    /** No language: phone, e-mail, URL, numbers (WF-17 fields). */
    NEUTRAL,

    /** Too little text to tell, e.g. a bare name "Jan Kowalski". */
    UNKNOWN,
}

/** A candidate language with the identifier's confidence in 0..1. */
data class LanguageGuess(val tag: String, val confidence: Float)

/** Statistical language identification of a text (ML Kit Language Identification in the app). */
fun interface LanguageIdentifier {
    /** Possible languages as BCP-47 tags ("pl", "en", …), best first; empty when undetermined. */
    suspend fun identify(text: String): List<LanguageGuess>
}

data class FragmentLanguage(val language: Language, val confidence: Float)

/**
 * Detects the language of each OCR fragment (WF-11). Card lines are short, so the statistical
 * identifier is combined with rules, applied in order:
 * 1. no letters left after removing contacts and numbers → [Language.NEUTRAL];
 * 2. letters unique to Polish → PL;
 * 3. the identifier's PL/EN guess if confident enough;
 * 4. typical card words (ul., sp. z o.o., dyrektor / street, manager, ltd) → PL or EN;
 * 5. otherwise [Language.UNKNOWN].
 */
class FragmentLanguageDetector @Inject constructor(
    private val identifier: LanguageIdentifier,
) {
    suspend fun detect(text: String): FragmentLanguage {
        val words = NEUTRAL_TOKENS.replace(text, " ")
        if (words.none { it.isLetter() }) return FragmentLanguage(Language.NEUTRAL, 1f)
        if (POLISH_LETTERS.containsMatchIn(words)) return FragmentLanguage(Language.PL, 0.95f)

        val guess = identifier.identify(words.trim())
            .filter { it.tag == "pl" || it.tag == "en" }
            .maxByOrNull { it.confidence }
        if (guess != null && guess.confidence >= MIN_CONFIDENCE) {
            return FragmentLanguage(if (guess.tag == "pl") Language.PL else Language.EN, guess.confidence)
        }

        val tokens = WORD.findAll(words.lowercase()).map { it.value }.toSet()
        val pl = tokens.count { it in PL_WORDS }
        val en = tokens.count { it in EN_WORDS }
        return when {
            pl > en -> FragmentLanguage(Language.PL, KEYWORD_CONFIDENCE)
            en > pl -> FragmentLanguage(Language.EN, KEYWORD_CONFIDENCE)
            else -> FragmentLanguage(Language.UNKNOWN, 0f)
        }
    }

    /** Language of every block of a page, in block order. */
    suspend fun detect(page: OcrPage): List<FragmentLanguage> = page.blocks.map { detect(it.text) }

    companion object {
        const val MIN_CONFIDENCE = 0.5f
        const val KEYWORD_CONFIDENCE = 0.6f

        private val POLISH_LETTERS = Regex("[ąęłńśćżźĄĘŁŃŚĆŻŹ]")
        private val WORD = Regex("\\p{L}+")

        /** E-mails, URLs, phone-like and other digit runs – language-independent. */
        private val NEUTRAL_TOKENS = Regex(
            "\\S+@\\S+|(?i)(https?://|www\\.)\\S+|\\S+\\.(pl|com|eu|net|org|io)\\b\\S*|" +
                "(?i)\\b(tel|mob|fax|e-mail|email|nip|regon|krs)\\b\\.?:?|[+\\d][\\d\\s().\\-/]*\\d|\\d+",
        )

        private val PL_WORDS = setOf(
            "ul", "al", "sp", "spółka", "spolka", "akcyjna", "jawna",
            "dyrektor", "kierownik", "prezes", "zarządu", "zarzadu", "specjalista", "starszy", "młodszy",
            "dział", "dzial", "handlowy", "sprzedaży", "sprzedazy", "biuro", "oddział", "oddzial",
            "ulica", "aleja", "polska", "doradca", "właściciel", "wlasciciel", "inżynier", "inzynier",
            "członek", "czlonek", "księgowa", "ksiegowa", "kadr", "obsługi", "obslugi", "klienta",
        )

        private val EN_WORDS = setOf(
            "street", "st", "road", "rd", "avenue", "ave", "suite", "floor", "ltd", "llc", "inc", "plc",
            "manager", "director", "head", "of", "and", "the", "senior", "junior", "officer", "chief",
            "executive", "sales", "department", "engineer", "consultant", "owner", "board", "member",
            "customer", "service", "account", "specialist", "poland", "office", "branch", "partner",
        )
    }
}
