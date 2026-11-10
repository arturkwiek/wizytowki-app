package com.wizytowki.ocr

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/** Runs OCR on both sides of a card in parallel (WF-10, WNF-02: under 3 s to the preview). */
class CardRecognizer @Inject constructor(
    private val recognizer: TextRecognizer,
) {
    /** Clock in milliseconds; replaceable in tests. */
    internal var clock: () -> Long = System::currentTimeMillis

    suspend fun recognize(frontUri: String, backUri: String?): CardOcr = coroutineScope {
        val start = clock()
        val front = async { recognizer.recognize(frontUri) }
        val back = backUri?.let { async { recognizer.recognize(it) } }
        CardOcr(front = front.await(), back = back?.await(), durationMs = clock() - start)
    }
}
