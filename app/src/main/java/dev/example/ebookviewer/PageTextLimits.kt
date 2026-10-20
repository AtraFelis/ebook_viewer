package dev.example.ebookviewer

/** Sampling guards bound exposure, not instantaneous native allocations. Never scan a document. */
object PageTextLimits {
    const val MAX_CHARACTERS = 16_000
    const val MIN_AVAILABLE_BYTES = 512L * 1024 * 1024
    const val MAX_RSS_KIB = 192L * 1024
    const val TIMEOUT_MS = 6_000L

    fun memoryAllowed(available: Long, threshold: Long, lowMemory: Boolean): Boolean =
        !lowMemory && available >= maxOf(MIN_AVAILABLE_BYTES, threshold * 2)

    fun bounded(parts: Sequence<String>): String? {
        val text = StringBuilder()
        for (part in parts) {
            if (part.length > MAX_CHARACTERS - text.length) return null
            text.append(part)
            if (text.length < MAX_CHARACTERS) text.append('\n')
        }
        return text.toString().trim()
    }
}
