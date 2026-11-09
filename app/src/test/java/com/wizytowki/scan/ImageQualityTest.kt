package com.wizytowki.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageQualityTest {
    private val size = 64

    private fun image(pixel: (x: Int, y: Int) -> Int) = IntArray(size * size) { pixel(it % size, it / size) }

    /** Sharp synthetic "text": dark 2-px strokes on a light card. */
    private val sharpCard = image { x, y -> if ((x / 2 + y / 8) % 3 == 0) 30 else 220 }

    /** Same card after a strong blur: only a gentle gradient remains, almost no edges. */
    private val blurredCard = image { x, _ -> 150 + (x % 8) }

    @Test
    fun sharpWellLitCard_hasNoIssues() {
        val report = analyzeLuma(sharpCard, size, size)
        assertTrue("sharpness=${report.sharpness}", report.issues.isEmpty())
    }

    @Test
    fun blurredCard_isBlurry() {
        val report = analyzeLuma(blurredCard, size, size)
        assertEquals(setOf(QualityIssue.BLURRY), report.issues)
    }

    @Test
    fun darkCard_isTooDark() {
        val dark = IntArray(sharpCard.size) { sharpCard[it] / 6 }
        assertTrue(QualityIssue.TOO_DARK in analyzeLuma(dark, size, size).issues)
    }

    @Test
    fun mostlyClippedCard_isTooBright() {
        val bright = image { x, y -> if (x < 8 && y % 4 == 0) 40 else 255 }
        assertTrue(QualityIssue.TOO_BRIGHT in analyzeLuma(bright, size, size).issues)
    }

    @Test
    fun whiteCardWithText_isNotTooBright() {
        // Typical white card: lots of paper, but below the clipping level.
        val white = image { x, y -> if ((x / 2 + y / 8) % 3 == 0) 20 else 235 }
        assertTrue(QualityIssue.TOO_BRIGHT !in analyzeLuma(white, size, size).issues)
    }

    @Test
    fun thresholds_areConfigurable() {
        val strict = QualityThresholds(minSharpness = Double.MAX_VALUE)
        assertTrue(QualityIssue.BLURRY in analyzeLuma(sharpCard, size, size, strict).issues)
    }

    @Test
    fun lumaOf_usesBt601Weights() {
        assertEquals(255, lumaOf(0xFFFFFFFF.toInt()))
        assertEquals(0, lumaOf(0xFF000000.toInt()))
        assertEquals(76, lumaOf(0xFFFF0000.toInt()))
        assertEquals(149, lumaOf(0xFF00FF00.toInt()))
        assertEquals(29, lumaOf(0xFF0000FF.toInt()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrongSize_isRejected() {
        analyzeLuma(IntArray(10), size, size)
    }
}
