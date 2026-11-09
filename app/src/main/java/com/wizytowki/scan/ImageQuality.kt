package com.wizytowki.scan

/** Problems that make a card photo likely unreadable for OCR (WF-04). */
enum class QualityIssue { BLURRY, TOO_DARK, TOO_BRIGHT }

data class QualityReport(
    /** Variance of the Laplacian of the luma image; low = blurry. */
    val sharpness: Double,
    /** Mean luma, 0..255. */
    val brightness: Double,
    /** Share of pixels at >= [QualityThresholds.clippedLuma], 0..1. */
    val clippedShare: Double,
    val issues: Set<QualityIssue>,
)

/**
 * Deliberately conservative defaults: a false alarm costs one tap ("Użyj mimo to"),
 * but a missed blurry photo costs a failed OCR. To be calibrated on real cards (Etap 1 phone test).
 */
data class QualityThresholds(
    val minSharpness: Double = 30.0,
    val minBrightness: Double = 55.0,
    val clippedLuma: Int = 250,
    val maxClippedShare: Double = 0.6,
)

/**
 * Analyses a grayscale image given as luma values (0..255, row-major).
 * Pure Kotlin so it is unit-testable; the Android side only converts a bitmap to luma.
 */
fun analyzeLuma(luma: IntArray, width: Int, height: Int, thresholds: QualityThresholds = QualityThresholds()): QualityReport {
    require(width >= 3 && height >= 3) { "Image too small: ${width}x$height" }
    require(luma.size == width * height) { "luma.size ${luma.size} != ${width}x$height" }

    var sum = 0.0
    var clipped = 0
    for (v in luma) {
        sum += v
        if (v >= thresholds.clippedLuma) clipped++
    }
    val brightness = sum / luma.size
    val clippedShare = clipped.toDouble() / luma.size

    // 4-neighbour Laplacian over interior pixels; variance via running sums.
    var lapSum = 0.0
    var lapSqSum = 0.0
    var n = 0
    for (y in 1 until height - 1) {
        val row = y * width
        for (x in 1 until width - 1) {
            val i = row + x
            val lap = (luma[i - 1] + luma[i + 1] + luma[i - width] + luma[i + width] - 4 * luma[i]).toDouble()
            lapSum += lap
            lapSqSum += lap * lap
            n++
        }
    }
    val mean = lapSum / n
    val sharpness = lapSqSum / n - mean * mean

    val issues = buildSet {
        if (sharpness < thresholds.minSharpness) add(QualityIssue.BLURRY)
        if (brightness < thresholds.minBrightness) add(QualityIssue.TOO_DARK)
        if (clippedShare > thresholds.maxClippedShare) add(QualityIssue.TOO_BRIGHT)
    }
    return QualityReport(sharpness, brightness, clippedShare, issues)
}

/** ITU-R BT.601 luma from an ARGB pixel. */
fun lumaOf(argb: Int): Int {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return (299 * r + 587 * g + 114 * b) / 1000
}
