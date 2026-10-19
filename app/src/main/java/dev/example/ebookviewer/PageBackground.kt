package dev.example.ebookviewer

import kotlin.math.abs

/** Ignore transparent edges and use a dominant 4-bit RGB bin; photos without one use the fallback. */
fun estimatePageBackground(samples: IntArray, fallback: Int): Int {
    val bins = IntArray(4096)
    var count = 0
    for (color in samples) {
        if (color ushr 24 < 224) continue
        bins[backgroundBin(color)]++
        count++
    }
    if (count < 32) return fallback
    val best = bins.indices.maxBy { bins[it] }
    if (bins[best] * 100 < count * 35) return fallback
    var red = 0
    var green = 0
    var blue = 0
    for (color in samples) {
        if (color ushr 24 < 224 || backgroundBin(color) != best) continue
        red += color ushr 16 and 255
        green += color ushr 8 and 255
        blue += color and 255
    }
    return (255 shl 24) or (red / bins[best] shl 16) or
        (green / bins[best] shl 8) or (blue / bins[best])
}

private fun backgroundBin(color: Int): Int =
    (color ushr 12 and 0xf00) or (color ushr 8 and 0xf0) or (color ushr 4 and 0xf)

/** Closest vertical page rectangle, with a dead band around adjacent page boundaries. */
fun backgroundPage(
    document: DocumentLayout,
    centerY: Double,
    previous: Int,
    deadBand: Double
): Int {
    var candidate = document.pageAt(centerY)
    if (candidate < document.pages.lastIndex) {
        val boundary = (
            document.tops[candidate] + document.pageHeight(candidate) +
                document.tops[candidate + 1]
            ) / 2
        if (centerY > boundary) candidate++
    }
    if (previous !in document.pages.indices || abs(candidate - previous) != 1) return candidate
    val first = minOf(candidate, previous)
    val boundary =
        (document.tops[first] + document.pageHeight(first) + document.tops[first + 1]) / 2
    return if (abs(centerY - boundary) <= deadBand) previous else candidate
}
