package com.wizytowki.scan

/**
 * State of scanning a single two-sided business card (WF-01, WF-02, WF-05, UI-05).
 *
 * Pure Kotlin so it can be unit-tested; images are referenced by URI strings.
 * Retaking one side never discards the other one.
 */
data class ScanSession(
    val frontUri: String? = null,
    val backUri: String? = null,
    val backSkipped: Boolean = false,
) {
    enum class Step { FRONT, BACK, DONE }

    val step: Step
        get() = when {
            frontUri == null -> Step.FRONT
            backUri == null && !backSkipped -> Step.BACK
            else -> Step.DONE
        }

    /** Stores the photo for the side the session is currently waiting for. */
    fun withCapture(uri: String): ScanSession = when (step) {
        Step.FRONT -> copy(frontUri = uri)
        Step.BACK -> copy(backUri = uri, backSkipped = false)
        Step.DONE -> this
    }

    /** "Pomiń – tył pusty"; only allowed once the front exists. */
    fun skipBack(): ScanSession = if (step == Step.BACK) copy(backSkipped = true) else this

    fun retakeFront(): ScanSession = copy(frontUri = null)

    fun retakeBack(): ScanSession = if (frontUri != null) copy(backUri = null, backSkipped = false) else this
}
