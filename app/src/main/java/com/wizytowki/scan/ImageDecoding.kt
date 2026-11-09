package com.wizytowki.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/** Size of a decoded preview plus the original image dimensions (for comparing scanner outputs). */
class DecodedImage(val bitmap: Bitmap, val width: Int, val height: Int)

/**
 * Largest power-of-two sample size that keeps both dimensions >= [maxSize]
 * (same contract as BitmapFactory.Options.inSampleSize).
 */
fun calculateInSampleSize(width: Int, height: Int, maxSize: Int): Int {
    require(maxSize > 0) { "maxSize must be positive" }
    var sample = 1
    while (width / (sample * 2) >= maxSize && height / (sample * 2) >= maxSize) {
        sample *= 2
    }
    return sample
}

/** Decodes a downsampled preview of [uri]; returns null if the image cannot be read. Call off the main thread. */
fun decodePreview(context: Context, uri: Uri, maxSize: Int = 600): DecodedImage? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    // With inJustDecodeBounds decodeStream always returns null; only the filled-in bounds matter.
    val stream = resolver.openInputStream(uri) ?: return null
    stream.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSize)
    }
    val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
    return DecodedImage(bitmap, bounds.outWidth, bounds.outHeight)
}
