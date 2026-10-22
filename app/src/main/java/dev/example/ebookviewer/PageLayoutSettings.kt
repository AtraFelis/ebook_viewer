package dev.example.ebookviewer

enum class PageSizeMode { ORIGINAL, UNIFORM_WIDTH }

/** Gap is measured in document points, and therefore follows the common user zoom. */
data class PageLayoutSettings(
    val mode: PageSizeMode = PageSizeMode.ORIGINAL,
    val gap: Int = 0,
    val horizontalPanLocked: Boolean = false,
    val crop: PageCrop = PageCrop()
) {
    init {
        require(gap in 0..72)
    }
}

/** Original-page percentages, independent of screen density, zoom and page placement. */
data class PageCrop(val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0) {
    init {
        require(listOf(left, top, right, bottom).all { it in 0..45 })
    }

    val widthFraction: Double get() = (100 - left - right) / 100.0
    val heightFraction: Double get() = (100 - top - bottom) / 100.0
}
