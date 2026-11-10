package com.wizytowki.ocr

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import javax.inject.Inject
import javax.inject.Singleton

/** ML Kit Language Identification with the bundled model – offline, nothing is downloaded (WF-11, WNF-01). */
@Singleton
class MlKitLanguageIdentifier @Inject constructor() : LanguageIdentifier {
    private val client by lazy {
        LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.1f).build(),
        )
    }

    override suspend fun identify(text: String): List<LanguageGuess> =
        client.identifyPossibleLanguages(text).await()
            .filter { it.languageTag != UNDETERMINED }
            .map { LanguageGuess(it.languageTag, it.confidence) }

    private companion object {
        const val UNDETERMINED = "und"
    }
}
