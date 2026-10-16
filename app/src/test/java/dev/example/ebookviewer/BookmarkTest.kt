package dev.example.ebookviewer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BookmarkTest {
    @Test
    fun multipleDocumentsAndInternalPageAnchorsRoundTrip() {
        val entries = listOf(Bookmark("content://a", 42, 0.7), Bookmark("content://b", 42, 0.1))
        val output = ByteArrayOutputStream()
        BookmarksCodec.write(output, entries)
        assertEquals(entries, BookmarksCodec.read(ByteArrayInputStream(output.toByteArray())))
    }

    @Test
    fun duplicatePageInvalidAnchorsAndUnknownDataAreRejected() {
        val bookmark = Bookmark("content://a", 0, 0.5)
        assertThrows(IllegalArgumentException::class.java) {
            BookmarksCodec.write(ByteArrayOutputStream(), listOf(bookmark, bookmark))
        }
        assertThrows(IllegalArgumentException::class.java) { Bookmark("a", 0, Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { Bookmark("a", -1, 0.0) }
        val output = ByteArrayOutputStream()
        BookmarksCodec.write(output, listOf(bookmark))
        val bytes = output.toByteArray().also { it[7] = 99 }
        assertThrows(IllegalArgumentException::class.java) {
            BookmarksCodec.read(ByteArrayInputStream(bytes))
        }
    }

    @Test
    fun navigationPreservesZoomAndValidHorizontalPosition() {
        val layout = DocumentLayout(List(100) { PageSize(800, 800) })
        val viewport = Viewport(4.0, -500.0, -100.0)
        viewport.moveToPage(layout, 50, null, 400, 800)
        assertEquals(4.0, viewport.zoom, 0.0)
        assertEquals(-500.0, viewport.x, 0.0)
        assertEquals(-160_000.0, viewport.y, 0.0)
        viewport.moveToPage(layout, 25, 0.7, 400, 800)
        assertEquals(25 * 800 + 0.7 * 800, (400 - viewport.y) / viewport.zoom, 0.000001)
        assertEquals(-500.0, viewport.x, 0.0)
    }

    @Test
    fun narrowTargetClampsHorizontalPositionWithoutChangingZoom() {
        val layout = DocumentLayout(listOf(PageSize(800, 800), PageSize(100, 800)))
        val viewport = Viewport(2.0, 0.0, 0.0)
        viewport.moveToPage(layout, 1, null, 400, 800)
        assertEquals(2.0, viewport.zoom, 0.0)
        assertEquals(-600.0, viewport.x, 0.0)
        assertEquals(1, layout.pageAt((400 - viewport.y) / viewport.zoom))
    }
}
