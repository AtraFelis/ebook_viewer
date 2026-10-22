package dev.example.ebookviewer

/** Reverse modes share the device shape; their existing labels distinguish the direction. */
internal fun OrientationMode.iconResource(): Int = when (this) {
    OrientationMode.AUTO -> R.drawable.reader_orientation
    OrientationMode.PORTRAIT, OrientationMode.REVERSE_PORTRAIT ->
        R.drawable.reader_orientation_portrait
    OrientationMode.LANDSCAPE, OrientationMode.REVERSE_LANDSCAPE ->
        R.drawable.reader_orientation_landscape
}
