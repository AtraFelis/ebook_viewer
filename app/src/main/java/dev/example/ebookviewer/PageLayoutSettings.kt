package dev.example.ebookviewer

enum class PageSizeMode { ORIGINAL, UNIFORM_WIDTH }

/** Gap is measured in document points, and therefore follows the common user zoom. */
data class PageLayoutSettings(val mode: PageSizeMode = PageSizeMode.ORIGINAL, val gap: Int = 0) {
    init {
        require(gap in 0..72)
    }
}
