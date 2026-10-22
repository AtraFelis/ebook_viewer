package dev.example.ebookviewer

import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PageCropTest {
    @Test
    fun croppedGeometryClosesRemovedSpaceAndRetainsOriginalPointMapping() {
        val layout = DocumentLayout(
            listOf(PageSize(600, 1000), PageSize(1200, 600)),
            PageLayoutSettings(gap = 20, crop = PageCrop(10, 5, 20, 15))
        )
        assertEquals(840.0, layout.width, 0.0)
        assertEquals(800.0, layout.pageHeight(0), 0.0)
        assertEquals(820.0, layout.tops[1], 0.0)
        assertEquals(1300.0, layout.height, 0.0)
        assertEquals(1, layout.pageAt(820.0))
        for (page in layout.pages.indices) {
            for (fraction in listOf(0.0, 0.1, 0.5, 1.0)) {
                assertEquals(fraction, layout.originalX(page, layout.pageX(page, fraction)), 1e-12)
                assertEquals(fraction, layout.originalY(page, layout.pageY(page, fraction)), 1e-12)
            }
        }
        assertEquals(layout.left(0), layout.pageX(0, 0.1), 0.0)
        assertEquals(layout.tops[0], layout.pageY(0, 0.05), 0.0)
    }

    @Test
    fun cropCannotEmptyPagesAndUniformWidthsUseRemainingContent() {
        assertThrows(IllegalArgumentException::class.java) { PageCrop(bottom = 46) }
        assertThrows(IllegalArgumentException::class.java) { PageCrop(top = -1) }
        val layout = DocumentLayout(
            listOf(PageSize(400, 600), PageSize(800, 400)),
            PageLayoutSettings(PageSizeMode.UNIFORM_WIDTH, crop = PageCrop(45, 45, 45, 45))
        )
        assertEquals(80.0, layout.pageWidth(0), 0.0)
        assertEquals(80.0, layout.pageWidth(1), 0.0)
        assertEquals(120.0, layout.pageHeight(0), 0.0)
        assertEquals(40.0, layout.pageHeight(1), 0.0)
    }

    @Test
    fun visibleTileCoverageMatchesRemainingRectanglesAtEveryZoomAndPlacement() {
        for (mode in PageSizeMode.entries) {
            val layout = DocumentLayout(
                listOf(PageSize(600, 1000), PageSize(1200, 600), PageSize(400, 800)),
                PageLayoutSettings(mode, 20, true, PageCrop(10, 5, 20, 15))
            )
            for (zoom in listOf(0.1, 0.75, 1.3, 4.0, 32.0)) {
                for (fraction in listOf(0.0, 0.4, 0.9)) {
                    val viewport = Viewport(zoom, -100.0, -layout.height * fraction * zoom)
                    viewport.constrain(layout, 800, 600)
                    val tiles = layout.visibleTiles(viewport, 800, 600, rasterScale(zoom))
                    val actual = tiles.sumOf { it.visibleArea(layout, viewport, 800, 600) }
                    val expected = layout.pages.indices.sumOf { page ->
                        val left = viewport.x + layout.left(page) * zoom
                        val top = viewport.y + layout.tops[page] * zoom
                        val right = min(800.0, left + layout.pageWidth(page) * zoom)
                        val bottom = min(600.0, top + layout.pageHeight(page) * zoom)
                        max(0.0, right - max(0.0, left)) * max(0.0, bottom - max(0.0, top))
                    }
                    assertEquals("$mode/$zoom/$fraction", expected, actual, 1e-6)
                    assertTrue(tiles.all { it.visibleArea(layout, viewport, 800, 600) > 0.0 })
                    assertTrue(tiles.size < 20)
                }
            }
        }
    }

    @Test
    fun bookmarksInHiddenMarginsClampToRetainedEdgeWithoutResettingZoom() {
        val layout = DocumentLayout(
            List(20) { PageSize(600, 1000) },
            PageLayoutSettings(crop = PageCrop(10, 5, 20, 15))
        )
        val viewport = Viewport(4.0)
        viewport.moveToPage(layout, 10, 0.0, 400, 600)
        assertEquals(layout.tops[10], (300.0 - viewport.y) / viewport.zoom, 0.0)
        viewport.moveToPage(layout, 10, 1.0, 400, 600)
        assertEquals(
            layout.tops[10] + layout.pageHeight(10),
            (300.0 - viewport.y) / viewport.zoom,
            0.0
        )
        assertEquals(4.0, viewport.zoom, 0.0)
    }
}
