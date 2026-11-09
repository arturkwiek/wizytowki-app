package com.wizytowki.scan

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Checks a scanned card photo; null when the image cannot be read (treated as "no warning"). */
fun interface QualityChecker {
    suspend fun check(uri: String): QualityReport?
}

class AndroidQualityChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) : QualityChecker {
    override suspend fun check(uri: String): QualityReport? = withContext(Dispatchers.Default) {
        val decoded = decodePreview(context, Uri.parse(uri), maxSize = ANALYSIS_SIZE) ?: return@withContext null
        val bitmap = decoded.bitmap
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()
        analyzeLuma(IntArray(pixels.size) { lumaOf(pixels[it]) }, width, height)
    }

    private companion object {
        /** Analysis resolution: enough for text-edge sharpness, fast (< 100 ms on a phone). */
        const val ANALYSIS_SIZE = 512
    }
}
