package com.wizytowki.domain

/**
 * Confidence of a merged field (0..1). An entity's own confidence is the OCR score of its source
 * line times the extraction rule's certainty (e.g. a labeled NIP with a valid checksum 0.98,
 * a name only matching the "two capitalized words" shape 0.45). Merging then adjusts it:
 * - the same value read on both sides counts as two independent readings: 1 − (1 − a)(1 − b);
 * - a value contradicted by the other side is lowered, so the user checks it.
 * Fields below [LOW] are marked as low confidence (WF-22, UI-06).
 */
object FieldConfidence {
    /** Threshold for marking a field; to be checked against the test-data set (#19). */
    const val LOW = 0.6f

    private const val CONFLICT_FACTOR = 0.6f

    /** Combined confidence of independent readings of one value, one per card side. */
    fun repeated(perSide: List<Float>): Float =
        (1f - perSide.fold(1f) { miss, c -> miss * (1f - c.coerceIn(0f, 1f)) }).coerceIn(0f, 1f)

    fun conflicted(confidence: Float): Float = confidence * CONFLICT_FACTOR
}
