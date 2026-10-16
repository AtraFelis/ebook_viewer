package dev.example.ebookviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentLayoutTest {
    @Test
    fun resolutionBucketsKeepUserZoomContinuousAndMapVisibleArea() {
        assertEquals(rasterScale(1.1), rasterScale(1.3), 0.0)
        assertTrue(rasterScale(1.3) >= 1.3)
        val viewport = Viewport(1.0, -512.0, -512.0)
        val document = DocumentLayout(listOf(PageSize(1200, 1600)))
        val tiles = document.visibleTiles(viewport, 512, 512, 2.0)
        assertEquals(4, tiles.size)
        assertEquals(Tile(0, 2.0, 1024, 1024, 512, 512), tiles.first())
        assertEquals(Tile(0, 2.0, 1536, 1536, 512, 512), tiles.last())
        assertEquals(1.0, viewport.zoom, 0.0)
    }

    @Test
    fun highZoomRequestsOnlyVisibleTilesAndClipsLastTile() {
        val document = DocumentLayout(listOf(PageSize(1200, 1600)))
        val tiles = document.visibleTiles(Viewport(4.0, -1024.0, -2048.0), 512, 512)
        assertEquals(listOf(Tile(0, 4.0, 1024, 2048, 512, 512)), tiles)
        val edge = DocumentLayout(listOf(PageSize(600, 800))).visibleTiles(Viewport(), 600, 800)
        assertEquals(4, edge.size)
        assertEquals(Tile(0, 1.0, 512, 512, 88, 288), edge.last())
    }

    @Test
    fun pageBoundaryAndEmptyViewportDoNotRequestOffscreenTiles() {
        val document = DocumentLayout(List(500) { PageSize(512, 512) })
        val tiles = document.visibleTiles(Viewport(1.0, 0.0, -512.0), 512, 512)
        assertEquals(listOf(Tile(1, 1.0, 0, 0, 512, 512)), tiles)
        assertTrue(document.visibleTiles(Viewport(), 0, 512).isEmpty())
    }

    private val layout =
        DocumentLayout(listOf(PageSize(600, 800), PageSize(800, 600), PageSize(400, 900)))

    @Test
    fun mixedPagesHaveNoGapAndBoundaryBelongsToNextPage() {
        assertEquals(0, layout.pageAt(-10.0))
        assertEquals(0, layout.pageAt(799.99))
        assertEquals(1, layout.pageAt(800.0))
        assertEquals(2, layout.pageAt(1400.0))
        assertEquals(2, layout.pageAt(2300.0))
        assertEquals(100.0, layout.left(0), 0.0)
        assertEquals(2300.0, layout.height, 0.0)
    }

    @Test
    fun pinchKeepsDocumentPointUnderFocus() {
        val viewport = Viewport(1.5, -100.0, -500.0)
        val docX = (250.0 - viewport.x) / viewport.zoom
        val docY = (400.0 - viewport.y) / viewport.zoom
        viewport.zoomAt(1.8, 250.0, 400.0)
        assertEquals(docX, (250.0 - viewport.x) / viewport.zoom, 0.000001)
        assertEquals(docY, (400.0 - viewport.y) / viewport.zoom, 0.000001)
    }

    @Test
    fun crossingPagesAndResizingNeverChangesZoom() {
        val viewport = Viewport(2.0, -100.0, 0.0)
        repeat(20) {
            viewport.y -= 250.0
            viewport.constrain(layout, 400, 800)
            viewport.y += 250.0
            viewport.constrain(layout, 800, 400)
        }
        assertEquals(2.0, viewport.zoom, 0.0)
    }
}
