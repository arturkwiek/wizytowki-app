package com.wizytowki.ocr

/**
 * Fixes a systematic ML Kit Latin-model error: Polish acute/dot letters read as caron letters
 * (Ż/Ź → Ž, Ć → Č, Ś → Š, Ń → Ň, Ę → Ě). Found on the emulator: "Łukasz Żółć" → "Łukasz Žółć".
 *
 * Applied only to a page that already contains letters unique to Polish (ą ę ł ń ś ć ż ź),
 * so Czech or Slovak names on foreign cards stay untouched.
 */
object PolishDiacritics {
    private val polishOnly = Regex("[ąęłńśćżźĄĘŁŃŚĆŻŹ]")
    private val caron = Regex("[ŽžČčŠšŇňĚě]")

    /** ź is the likely reading before r, l, dz (źródło, źle, jeździć); ż everywhere else (żona, każdy, też). */
    private val zAcute = Regex("[Žž](?=[rRlL]|[dD][zZžŽźŹ])")

    fun fix(page: OcrPage): OcrPage {
        if (!polishOnly.containsMatchIn(page.text) || !caron.containsMatchIn(page.text)) return page
        return OcrPage(page.blocks.map { block -> OcrBlock(block.lines.map { it.copy(text = fix(it.text)) }) })
    }

    internal fun fix(text: String): String = zAcute.replace(text) { if (it.value == "Ž") "Ź" else "ź" }
        .map { c ->
            when (c) {
                'Ž' -> 'Ż'; 'ž' -> 'ż'
                'Č' -> 'Ć'; 'č' -> 'ć'
                'Š' -> 'Ś'; 'š' -> 'ś'
                'Ň' -> 'Ń'; 'ň' -> 'ń'
                'Ě' -> 'Ę'; 'ě' -> 'ę'
                else -> c
            }
        }.joinToString("")
}
