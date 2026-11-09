package com.wizytowki.scan

/**
 * Text format of a saved scan draft (UI-09). Pure Kotlin so it is unit-testable.
 *
 * ```
 * version=1
 * front=file:///data/.../front_1.jpg
 * back=
 * backSkipped=false
 * ```
 * Values are URI strings; line breaks are never part of a file URI, so no escaping is needed.
 */
object DraftCodec {
    private const val VERSION = "1"

    fun encode(session: ScanSession): String = buildString {
        append("version=").append(VERSION).append('\n')
        append("front=").append(session.frontUri.orEmpty()).append('\n')
        append("back=").append(session.backUri.orEmpty()).append('\n')
        append("backSkipped=").append(session.backSkipped).append('\n')
    }

    /** Returns null for an unknown version or a corrupt file (the draft is then dropped). */
    fun decode(text: String): ScanSession? {
        val values = text.lineSequence()
            .filter { '=' in it }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        if (values["version"] != VERSION) return null
        val front = values["front"]?.takeIf { it.isNotEmpty() }
        val back = values["back"]?.takeIf { it.isNotEmpty() }
        val backSkipped = values["backSkipped"]?.toBooleanStrictOrNull() ?: return null
        return ScanSession(frontUri = front, backUri = back, backSkipped = backSkipped && back == null)
    }
}

/** True when the draft holds anything worth resuming. */
val ScanSession.hasContent: Boolean
    get() = frontUri != null || backUri != null
