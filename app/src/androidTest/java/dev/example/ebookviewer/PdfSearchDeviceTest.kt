package dev.example.ebookviewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfSearchDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation
    private val ui get() = DeviceGestures(instrumentation)

    @Test
    fun documentSearchFindsEnglishAndKoreanAndEmptyResults() {
        val file = fixture()
        val worker = open(file)
        try {
            for (query in listOf("needle", "NEEDLE", "한글")) {
                val result = search(worker, query)
                if (!PdfTextAccess.isSupported()) {
                    assertEquals(PdfSearchStatus.UNAVAILABLE, result.status)
                    return
                }
                assertEquals(PdfSearchStatus.COMPLETE, result.status)
                assertEquals(3, result.pagesSearched)
                assertEquals(listOf(0, 2), result.hits.map { it.page })
                assertTrue(
                    result.hits.all { hit ->
                        hit.bounds.isNotEmpty() &&
                            hit.bounds.all {
                                !it.isEmpty && it.left.isFinite() && it.top.isFinite()
                            }
                    }
                )
            }
            assertTrue(search(worker, "missing").hits.isEmpty())
            assertTrue(search(worker, "").hits.isEmpty())
        } finally {
            worker.close()
            file.delete()
        }
    }

    @Test
    fun rotatedTextOnMixedSizePagesHasPageBoundsAndScannedPagesHaveNoMatches() {
        val file = File.createTempFile(
            "rotated-search-",
            ".pdf",
            instrumentation.targetContext.cacheDir
        )
        val document = PdfDocument()
        val image = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        try {
            image.eraseColor(Color.WHITE)
            Canvas(image).drawText("needle", 20f, 80f, Paint().apply { textSize = 20f })
            val first = document.startPage(PdfDocument.PageInfo.Builder(800, 400, 0).create())
            first.canvas.translate(200f, 80f)
            first.canvas.rotate(90f)
            first.canvas.drawText("needle", 0f, 0f, Paint().apply { textSize = 18f })
            document.finishPage(first)
            val second = document.startPage(PdfDocument.PageInfo.Builder(400, 700, 1).create())
            second.canvas.drawBitmap(image, 20f, 100f, null)
            document.finishPage(second)
            file.outputStream().use(document::writeTo)
        } finally {
            image.recycle()
            document.close()
        }
        val worker = open(file)
        try {
            val result = search(worker, "needle")
            if (PdfTextAccess.isSupported()) {
                assertEquals(PdfSearchStatus.COMPLETE, result.status)
                assertEquals(2, result.pagesSearched)
                assertEquals(listOf(0), result.hits.map { it.page })
                assertTrue(
                    result.hits.single().bounds.all {
                        it.left >= 0f && it.top >= 0f && it.right <= 800f && it.bottom <= 400f
                    }
                )
            } else {
                assertEquals(PdfSearchStatus.UNAVAILABLE, result.status)
            }
        } finally {
            worker.close()
            file.delete()
        }
    }

    @Test
    fun replacedSearchCannotDeliverStaleResultsOrCloseTheReadingDescriptor() {
        val file = fixture()
        val loaded = CountDownLatch(1)
        val rendered = CountDownLatch(1)
        val worker = PdfWorker(
            onLoaded = { loaded.countDown() },
            onTile = { _, bitmap ->
                bitmap.recycle()
                rendered.countDown()
            },
            onError = { throw AssertionError("Synthetic PDF could not be read: $it") }
        )
        try {
            worker.open { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }
            assertTrue(loaded.await(10, TimeUnit.SECONDS))
            val stale = AtomicInteger()
            val done = CountDownLatch(1)
            val result = AtomicReference<PdfSearchUpdate>()
            // Main-thread delivery cannot run until all three generation changes have occurred.
            ui.onMain {
                worker.search("needle") { stale.incrementAndGet() }
                worker.cancelSearch()
                worker.search("other") {
                    if (it.status != PdfSearchStatus.SEARCHING) {
                        result.set(it)
                        done.countDown()
                    }
                }
            }
            assertTrue(done.await(20, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            assertEquals(0, stale.get())
            if (PdfTextAccess.isSupported()) {
                assertEquals(listOf(1), result.get().hits.map { it.page })
            }
            worker.request(listOf(Tile(0, 1.0, 0, 0, 512, 512)))
            assertTrue("Search closed the reading descriptor", rendered.await(10, TimeUnit.SECONDS))
        } finally {
            worker.close()
            file.delete()
        }
    }

    @Test
    fun searchResultsAreBoundedAndClosedDocumentsCannotSearchOldText() {
        val file = fixture(dense = true)
        val worker = open(file)
        try {
            val result = search(worker, "needle")
            if (PdfTextAccess.isSupported()) {
                assertEquals(PdfSearchStatus.LIMITED, result.status)
                assertEquals(PdfTextAccess.MAX_HITS, result.hits.size)
            } else {
                assertEquals(PdfSearchStatus.UNAVAILABLE, result.status)
            }
            worker.closeDocument()
            val closed = search(worker, "needle")
            assertTrue(closed.hits.isEmpty())
            assertEquals(
                if (PdfTextAccess.isSupported()) {
                    PdfSearchStatus.ERROR
                } else {
                    PdfSearchStatus.UNAVAILABLE
                },
                closed.status
            )
        } finally {
            worker.close()
            file.delete()
        }
    }

    @Test
    fun searchNavigationKeepsZoomAndDrawsOnlyTheSelectedHighlight() {
        ui.onMain {
            val view = PdfCanvasView(instrumentation.targetContext)
            view.layout(0, 0, 400, 800)
            view.restorePosition(ReadingPosition(0, 0.5, 0.9, 4.0))
            view.setPages(List(3) { PageSize(600, 800) })
            assertTrue(view.showSearchHit(PdfSearchHit(2, listOf(RectF(40f, 100f, 100f, 130f)))))
            val position = requireNotNull(view.readingPosition())
            assertEquals(2, position.page)
            assertEquals(4.0, position.zoom, 0.0)
            val bitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                assertTrue(hasHighlight(bitmap))
                view.showSearchHit(null)
                view.draw(Canvas(bitmap))
                assertFalse(hasHighlight(bitmap))
                assertEquals(position, view.readingPosition())
            } finally {
                bitmap.recycle()
                view.reset()
            }
        }
    }

    @Test
    fun searchDialogNavigatesResultsAndReturnsToImmersiveReading() {
        val file = fixture()
        val uri = Uri.fromFile(file)
        val activity = ui.launch()
        try {
            ui.onMain { (activity as MainActivity).openDocument(uri) }
            val view = ui.awaitReady(activity)
            ui.pinch(view)
            val zoom = ui.state(view).getDouble("zoom")
            ui.onMain {
                if (!activity.findViewById<View>(R.id.reader_tools).isShown) view.performClick()
                activity.findViewById<View>(R.id.pdf_search_button).performClick()
            }
            val arguments = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    "needle"
                )
            }
            awaitAction {
                instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(
                    activity.getString(R.string.pdf_search_hint)
                )?.firstOrNull { it.className == "android.widget.EditText" }
                    ?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments) == true
            }
            action(activity.getString(R.string.pdf_search_start))
            val status = if (PdfTextAccess.isSupported()) {
                activity.getString(R.string.pdf_search_complete, 2)
            } else {
                activity.getString(R.string.pdf_search_unavailable)
            }
            awaitAction {
                instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(
                    status
                )
                    ?.any { it.text?.toString() == status } == true
            }
            if (PdfTextAccess.isSupported()) {
                action(activity.getString(R.string.pdf_search_next))
                ui.await("Search did not navigate to the second matching page") {
                    view.readingPosition()?.page == 2
                }
                assertEquals(zoom, ui.state(view).getDouble("zoom"), 0.000001)
                action(activity.getString(R.string.pdf_search_previous))
                ui.await("Search did not return to the first result") {
                    view.readingPosition()?.page == 0
                }
            }
            action(activity.getString(R.string.resume_reading))
            ui.await("Search tools did not hide after returning to reading") {
                !activity.findViewById<View>(R.id.reader_tools).isShown
            }
            assertEquals(zoom, ui.state(view).getDouble("zoom"), 0.000001)
            ui.onMain {
                view.performClick()
                activity.findViewById<View>(R.id.pdf_search_button).performClick()
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                try {
                    view.draw(Canvas(bitmap))
                    assertFalse("A new search kept the previous highlight", hasHighlight(bitmap))
                } finally {
                    bitmap.recycle()
                }
            }
            action(activity.getString(R.string.resume_reading))
        } finally {
            ui.onMain { activity.finish() }
            instrumentation.waitForIdleSync()
            val done = CountDownLatch(1)
            val application = activity.application as ReaderApplication
            application.library.execute({ it.remove(uri.toString()) }) {
                done.countDown()
            }
            assertTrue(done.await(10, TimeUnit.SECONDS))
            file.delete()
        }
    }

    private fun open(file: File): PdfWorker {
        val loaded = CountDownLatch(1)
        val worker = PdfWorker(
            onLoaded = { loaded.countDown() },
            onTile = { _, bitmap -> bitmap.recycle() },
            onError = { throw AssertionError("Synthetic PDF could not be read: $it") }
        )
        worker.open { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }
        assertTrue(loaded.await(10, TimeUnit.SECONDS))
        return worker
    }

    private fun search(worker: PdfWorker, query: String): PdfSearchUpdate {
        val done = CountDownLatch(1)
        val result = AtomicReference<PdfSearchUpdate>()
        worker.search(query) {
            if (it.status != PdfSearchStatus.SEARCHING) {
                result.set(it)
                done.countDown()
            }
        }
        assertTrue("Synthetic PDF search timed out", done.await(20, TimeUnit.SECONDS))
        return requireNotNull(result.get())
    }

    private fun fixture(dense: Boolean = false): File {
        val file = File.createTempFile(
            "search-test-",
            ".pdf",
            instrumentation.targetContext.cacheDir
        )
        val document = PdfDocument()
        try {
            repeat(if (dense) 1 else 3) { index ->
                val info = PdfDocument.PageInfo.Builder(600, 800, index).create()
                val page = document.startPage(info)
                val paint = Paint().apply { textSize = if (dense) 8f else 18f }
                if (dense) {
                    repeat(30) { row ->
                        page.canvas.drawText("needle ".repeat(20), 10f, 20f + row * 20f, paint)
                    }
                } else {
                    page.canvas.drawText(if (index == 1) "other" else "needle", 100f, 120f, paint)
                    page.canvas.drawText(if (index == 1) "text" else "한글", 100f, 160f, paint)
                }
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
        return file
    }

    private fun hasHighlight(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.any {
            Color.red(it) > 240 && Color.green(it) in 180..240 && Color.blue(it) < 200
        }
    }

    private fun action(text: String) = awaitAction {
        instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)
            ?.firstOrNull { it.text?.toString() == text }
            ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun awaitAction(action: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (action()) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Synthetic PDF search dialog action failed")
    }
}
