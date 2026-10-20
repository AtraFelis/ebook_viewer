package dev.example.ebookviewer

import kotlin.math.roundToInt

/** Unconfirmed targets never become reading positions or survive a document/range change. */
internal class PageScrubState {
    var current: Int? = null
        private set
    var count = 0
        private set
    var target: Int? = null
        private set
    var enabled = false
        private set

    fun update(page: Int?, pages: Int, available: Boolean) {
        val valid = page?.takeIf { it in 0 until pages }
        if (current != valid || count != pages || !available) cancel()
        current = valid
        count = pages.coerceAtLeast(0)
        enabled = available && valid != null && pages > 1
    }

    fun begin(fraction: Double): Boolean {
        if (!enabled) return false
        target = pageAt(fraction)
        return true
    }

    fun move(fraction: Double) {
        if (target != null) target = pageAt(fraction)
    }

    fun cancel(): Boolean {
        val active = target != null
        target = null
        return active
    }

    fun commit(): Int? {
        val selected = target
        cancel()
        return selected?.takeIf { enabled && it != current }
    }

    private fun pageAt(fraction: Double): Int =
        ((if (fraction.isFinite()) fraction else 0.0).coerceIn(0.0, 1.0) * (count - 1))
            .roundToInt()
}
