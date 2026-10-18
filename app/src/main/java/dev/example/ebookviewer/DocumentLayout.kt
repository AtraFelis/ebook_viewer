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
        if (top + pageHeight(index) * zoom <= 0 ||
            top >= screenHeight ||
            left + pageWidth(index) * zoom <= 0 ||
            left >= screenWidth
        ) {
            continue
        }
        val pageScale = renderScale * scale(index)
        val ratio = renderScale / zoom
        val pixelWidth = ceil(page.width * pageScale).toInt()
        val pixelHeight = ceil(page.height * pageScale).toInt()
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
                        pageScale,
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

/** Screen area after both viewport and page clipping; previews never count as sharp tiles. */
fun Tile.visibleArea(
    document: DocumentLayout,
    viewport: Viewport,
    width: Int,
    height: Int
): Double {
    val left = viewport.x + document.left(page) * viewport.zoom
    val top = viewport.y + document.tops[page] * viewport.zoom
    val ratio = viewport.zoom * document.scale(page) / scale
    val x0 = max(0.0, max(left, left + x * ratio))
    val y0 = max(0.0, max(top, top + y * ratio))
    val x1 = min(
        width.toDouble(),
        min(left + document.pageWidth(page) * viewport.zoom, left + (x + this.width) * ratio)
    )
    val y1 = min(
        height.toDouble(),
        min(top + document.pageHeight(page) * viewport.zoom, top + (y + this.height) * ratio)
    )
    return (x1 - x0).coerceAtLeast(0.0) * (y1 - y0).coerceAtLeast(0.0)
}

/** PDF points and document coordinates are independent of screen pixels. */
data class PageSize(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0)
    }
}

class DocumentLayout(
    val pages: List<PageSize>,
    val settings: PageLayoutSettings = PageLayoutSettings()
) {
    val width = pages.maxOf { it.width }.toDouble()
    val tops = DoubleArray(pages.size + 1)

    init {
        pages.forEachIndexed { index, _ ->
            tops[index + 1] = tops[index] + pageHeight(index) +
                if (index < pages.lastIndex) settings.gap else 0
        }
    }

    val height: Double get() = tops.last()

    fun scale(index: Int): Double =
        if (settings.mode == PageSizeMode.UNIFORM_WIDTH) width / pages[index].width else 1.0

    fun pageWidth(index: Int): Double = pages[index].width * scale(index)

    fun pageHeight(index: Int): Double = pages[index].height * scale(index)

    fun left(index: Int): Double = (width - pageWidth(index)) / 2

    fun pageAt(y: Double): Int {
        val match = tops.binarySearch(y.coerceIn(0.0, height))
        return (if (match >= 0) match else -match - 2).coerceIn(0, pages.lastIndex)
    }
}

/** Screen pixels per document point; page placement scale is independent of user zoom. */
object ZoomLimits {
    const val MIN_SAVED = 0.1
    const val MAX = 32.0

    fun gestureMinimum(documentWidth: Double, width: Int, height: Int): Double =
        (min(width, height) * 0.75 / documentWidth).coerceIn(MIN_SAVED, MAX)
}

class Viewport(var zoom: Double = 1.0, var x: Double = 0.0, var y: Double = 0.0) {
    fun moveToPage(layout: DocumentLayout, page: Int, fractionY: Double?, width: Int, height: Int) {
        require(page in layout.pages.indices)
        require(fractionY == null || (fractionY.isFinite() && fractionY in 0.0..1.0))
        val left = layout.left(page) * zoom
        val pageWidth = layout.pageWidth(page) * zoom
        x = if (pageWidth < width) {
            (width - pageWidth) / 2.0 - left
        } else {
            x.coerceIn(width - left - pageWidth, -left)
        }
        y = if (fractionY == null) {
            -layout.tops[page] * zoom
        } else {
            height / 2.0 - (layout.tops[page] + fractionY * layout.pageHeight(page)) * zoom
        }
        constrain(layout, width, height)
    }

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
