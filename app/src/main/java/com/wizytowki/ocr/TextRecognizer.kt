package com.wizytowki.ocr

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Recognizes text on one card photo (WF-10). */
fun interface TextRecognizer {
    suspend fun recognize(uri: String): OcrPage
}

/**
 * ML Kit Text Recognition v2, Latin script (covers ą, ć, ę, ł, ń, ó, ś, ź, ż).
 * The bundled model ships inside the APK, so recognition works offline from the first launch
 * and never downloads anything (WNF-01).
 */
@Singleton
class MlKitTextRecognizer @Inject constructor(
    @ApplicationContext private val context: Context,
) : TextRecognizer {
    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun recognize(uri: String): OcrPage {
        val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, Uri.parse(uri)) }
        return PolishDiacritics.fix(client.process(image).await().toPage())
    }
}

private fun Text.toPage() = OcrPage(
    textBlocks.map { block ->
        OcrBlock(block.lines.map { OcrLine(it.text, it.confidence, it.boundingBox?.toBox()) })
    },
)

private fun Rect.toBox() = OcrBox(left, top, right, bottom)

internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
