package dev.example.ebookviewer

import org.junit.Assert.assertEquals
import org.junit.Test

class PageBackgroundTest {
    @Test
    fun scannedPaperIgnoresSparseDarkTextAndKeepsItsColor() {
        val paper = -0xb1328
        val samples = IntArray(128) { if (it % 8 == 0) -0x1000000 else paper }
        assertEquals(paper, estimatePageBackground(samples, DEFAULT_BACKGROUND_COLOR))
    }

    @Test
    fun darkAndWhitePagesStayDarkAndWhite() {
        for (color in listOf(-0x1000000, -1, -0xeeeeef)) {
            assertEquals(color, estimatePageBackground(IntArray(128) { color }, 0))
        }
    }

    @Test
    fun transparentInsufficientAndPhotographicEdgesUseTheConfiguredFallback() {
        val fallback = -0xed54cc
        assertEquals(fallback, estimatePageBackground(IntArray(128), fallback))
        assertEquals(fallback, estimatePageBackground(IntArray(31) { -1 }, fallback))
        val photo = IntArray(128) { (255 shl 24) or (it * 131071) }
        assertEquals(fallback, estimatePageBackground(photo, fallback))
    }

    @Test
    fun nearIdenticalPaperShadesAverageWithoutChoosingText() {
        val samples = IntArray(128) { if (it % 2 == 0) -0x10102 else -1 }
        assertEquals(-0x10102, estimatePageBackground(samples, 0))
    }

    @Test
    fun pageBoundaryHasSymmetricDeadBandAndGapUsesNearestPage() {
        val document = DocumentLayout(listOf(PageSize(400, 800), PageSize(400, 800)))
        val boundary = (800.0 + document.tops[1]) / 2
        assertEquals(0, backgroundPage(document, boundary + 10, 0, 24.0))
        assertEquals(1, backgroundPage(document, boundary - 10, 1, 24.0))
        assertEquals(1, backgroundPage(document, boundary + 25, 0, 24.0))
        assertEquals(0, backgroundPage(document, boundary - 25, 1, 24.0))
        assertEquals(1, backgroundPage(document, boundary + 1, -1, 24.0))
    }

    @Test
    fun distantJumpAndChangedUniformLayoutDoNotKeepAnOldPage() {
        val document = DocumentLayout(
            listOf(PageSize(400, 800), PageSize(800, 800), PageSize(400, 800)),
            PageLayoutSettings(PageSizeMode.UNIFORM_WIDTH, 48)
        )
        assertEquals(2, backgroundPage(document, document.tops[2] + 100, 0, 24.0))
        assertEquals(0, backgroundPage(document, 0.0, 50, 24.0))
    }
}
