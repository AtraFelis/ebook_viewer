package dev.example.ebookviewer

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Cache resolution changes in half-octaves; user zoom remains continuous. */
fun rasterScale(zoom: Double): Double = 2.0.pow(ceil(log2(zoom) * 2.0) / 2.0)

data class Tile(
    val page: Int,
    val scale: Double,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

fun DocumentLayout.visibleTiles(
    viewport: Viewport,
    screenWidth: Int,
    screenHeight: Int,
    renderScale: Double = viewport.zoom
): List<Tile> {
    if (screenWidth <= 0 || screenHeight <= 0) return emptyList()
    val visible = ArrayList<Tile>()
    val zoom = viewport.zoom
    val first = pageAt(-viewport.y / zoom)
    val last = pageAt((screenHeight - viewport.y) / zoom)
    for (index in first..last) {
        val page = pages[index]
        val left = viewport.x + left(index) * zoom
        val top = viewport.y + tops[index] * zoom
        val ratio = renderScale / zoom
        val pixelWidth = ceil(page.width * renderScale).toInt()
        val pixelHeight = ceil(page.height * renderScale).toInt()
        val x0 = max(0, floor(-left * ratio / TILE_SIZE).toInt())
        val y0 = max(0, floor(-top * ratio / TILE_SIZE).toInt())
        val x1 = min(
            (pixelWidth - 1) / TILE_SIZE,
            ceil((screenWidth - left) * ratio / TILE_SIZE).toInt() - 1
        )
        val y1 = min(
            (pixelHeight - 1) / TILE_SIZE,
            ceil((screenHeight - top) * ratio / TILE_SIZE).toInt() - 1
        )
        for (row in y0..y1) {
            for (column in x0..x1) {
                val x = column * TILE_SIZE
                val y = row * TILE_SIZE
                visible.add(
                    Tile(
                        index,
                        renderScale,
                        x,
                        y,
                        min(TILE_SIZE, pixelWidth - x),
                        min(
                            TILE_SIZE,
                            pixelHeight - y
                        )
                    )
                )
            }
        }
    }
    return visible
}

private const val TILE_SIZE = 512

/** PDF points and document coordinates are independent of screen pixels. */
data class PageSize(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0)
    }
}

class DocumentLayout(val pages: List<PageSize>) {
    val width = pages.maxOf { it.width }.toDouble()
    val tops = DoubleArray(pages.size + 1)

    init {
        pages.forEachIndexed { index, page -> tops[index + 1] = tops[index] + page.height }
    }

    val height: Double get() = tops.last()

    fun left(index: Int): Double = (width - pages[index].width) / 2

    fun pageAt(y: Double): Int {
        val match = tops.binarySearch(y.coerceIn(0.0, height))
        return (if (match >= 0) match else -match - 2).coerceIn(0, pages.lastIndex)
    }
}

/** Absolute scale is screen pixels per PDF point, independent of the current fit width. */
object ZoomLimits {
    const val MIN_SAVED = 0.1
    const val MAX = 32.0

    fun gestureMinimum(documentWidth: Double, width: Int, height: Int): Double =
        (min(width, height) * 0.75 / documentWidth).coerceIn(MIN_SAVED, MAX)
}

class Viewport(var zoom: Double = 1.0, var x: Double = 0.0, var y: Double = 0.0) {
    fun zoomAt(
        factor: Double,
        focusX: Double,
        focusY: Double,
        minimumZoom: Double = ZoomLimits.MIN_SAVED
    ) {
        // Preserve old small saved scales and allow gradual zoom-in without a sudden jump.
        val next = (zoom * factor).coerceIn(min(zoom, minimumZoom), ZoomLimits.MAX)
        val ratio = next / zoom
        x = focusX - (focusX - x) * ratio
        y = focusY - (focusY - y) * ratio
        zoom = next
    }

    fun constrain(layout: DocumentLayout, width: Int, height: Int) {
        val scaledWidth = layout.width * zoom
        x =
            if (scaledWidth <
                width
            ) {
                (width - scaledWidth) / 2
            } else {
                x.coerceIn(width - scaledWidth, 0.0)
            }
        val scaledHeight = layout.height * zoom
        y = if (scaledHeight < height) 0.0 else y.coerceIn(height - scaledHeight, 0.0)
    }
}
