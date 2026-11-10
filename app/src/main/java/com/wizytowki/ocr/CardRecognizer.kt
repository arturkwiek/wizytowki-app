package com.wizytowki.ocr

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * Runs OCR and fragment language detection on both sides of a card in parallel
 * (WF-10, WF-11, WNF-02: under 3 s to the preview).
 */
class CardRecognizer @Inject constructor(
    private val recognizer: TextRecognizer,
    private val languageDetector: FragmentLanguageDetector,
) {
    /** Clock in milliseconds; replaceable in tests. */
    internal var clock: () -> Long = System::currentTimeMillis

    suspend fun recognize(frontUri: String, backUri: String?): CardOcr = coroutineScope {
        val start = clock()
        val front = async { analyze(frontUri) }
        val back = backUri?.let { async { analyze(it) } }
        CardOcr(front = front.await(), back = back?.await(), durationMs = clock() - start)
    }

    private suspend fun analyze(uri: String): OcrPage {
        val page = recognizer.recognize(uri)
        return OcrPage(page.blocks.map { it.copy(language = languageDetector.detect(it.text)) })
    }
}
