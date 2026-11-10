package com.wizytowki.ocr

/** Axis-aligned bounding box in image pixels. */
data class OcrBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height: Int get() = bottom - top
}

/**
 * One recognized text line (WF-10). [confidence] is the recognizer's score in 0..1,
 * null when the engine does not report one.
 */
data class OcrLine(val text: String, val confidence: Float? = null, val box: OcrBox? = null)

/**
 * A block of lines the recognizer grouped together (e.g. an address spanning two lines) –
 * the "fragment" whose [language] is detected (WF-11); null before detection.
 */
data class OcrBlock(val lines: List<OcrLine>, val language: FragmentLanguage? = null) {
    val text: String get() = lines.joinToString("\n") { it.text }
}

/** Recognized text of one side of a card. */
data class OcrPage(val blocks: List<OcrBlock>) {
    val lines: List<OcrLine> get() = blocks.flatMap { it.lines }
    val text: String get() = blocks.joinToString("\n\n") { it.text }
    val isEmpty: Boolean get() = lines.none { it.text.isNotBlank() }

    companion object {
        val EMPTY = OcrPage(emptyList())
    }
}

/** OCR of both sides of one card (WF-10); [back] is null when the back was skipped. */
data class CardOcr(val front: OcrPage, val back: OcrPage?, val durationMs: Long) {
    /** Raw OCR text stored with the card (data model: "surowy tekst OCR"). */
    val rawText: String
        get() = listOfNotNull(front.text, back?.text?.takeIf { it.isNotBlank() }).joinToString("\n\n---\n\n")
}
