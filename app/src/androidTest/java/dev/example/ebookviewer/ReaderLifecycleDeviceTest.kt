package dev.example.ebookviewer

import android.content.ComponentCallbacks2
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderLifecycleDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation
    private val ui get() = DeviceGestures(instrumentation)

    @Test
    fun boundedCacheStaysEmptyWhileSuspendedAndRestoresTheViewport() {
        ui.onMain {
            val requests = mutableListOf<List<Tile>>()
            val view = PdfCanvasView(instrumentation.targetContext) { requests.add(it) }
            view.layout(0, 0, 600, 800)
            view.setPages(List(100) { PageSize(600, 800) })
            view.goToPage(20, 0.4)
            val before = requireNotNull(view.readingPosition())
            try {
                repeat(70) { index ->
                    val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                    view.putTile(Tile(index, 1.0, 0, 0, 512, 512), bitmap)
                    assertTrue(view.renderState().cachedBytes <= CACHE_BYTES)
                }
                assertEquals(CACHE_BYTES, view.renderState().cachedBytes)
                view.setRenderingEnabled(false)
                assertTrue(requests.last().isEmpty())
                assertEquals(0, view.renderState().cachedBytes)
                requests.clear()
                view.trimCache()
                view.setPages(List(100) { PageSize(600, 800) })
                assertTrue(requests.all { it.isEmpty() })
                assertEquals(before, view.readingPosition())
                assertEquals(0, view.renderState().visibleTiles)
                view.setRenderingEnabled(true)
                assertTrue(requests.last().isNotEmpty())
                assertEquals(before, view.readingPosition())
                view.trimCache()
                assertTrue(requests[requests.lastIndex - 1].isEmpty())
                assertTrue(requests.last().isNotEmpty())
                assertEquals(before, view.readingPosition())
            } finally {
                view.reset()
            }
        }
    }

    @Test
    fun cancelledQueuedTileIsRejectedEvenWhenTheSameTileIsRequestedAgain() {
        val file = coloredFixture(Color.RED)
        val loaded = CountDownLatch(1)
        val delivered = CountDownLatch(1)
        val count = AtomicInteger()
        val failures = AtomicInteger()
        val bitmaps = mutableListOf<Bitmap>()
        val worker = PdfWorker(
            { loaded.countDown() },
            { _, bitmap ->
                bitmaps.add(bitmap)
                count.incrementAndGet()
                delivered.countDown()
            },
            { failures.incrementAndGet() }
        )
        val queue = queue(worker)
        try {
            ui.onMain { worker.open { descriptor(file) } }
            assertTrue(loaded.await(10, TimeUnit.SECONDS))
            ui.onMain {
                val tile = Tile(0, 1.0, 0, 0, 64, 64)
                worker.request(listOf(tile))
                fence(queue)
                // The old delivery is queued on this blocked UI thread, before cancellation.
                worker.cancelTiles()
                worker.request(listOf(tile))
                fence(queue)
            }
            assertTrue(delivered.await(5, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            assertEquals(1, count.get())
            assertEquals(0, failures.get())
            ui.onMain { assertEquals(Color.RED, bitmaps.single().getPixel(32, 32)) }
        } finally {
            ui.onMain { worker.close() }
            assertTrue(queue.awaitTermination(5, TimeUnit.SECONDS))
            ui.onMain { bitmaps.forEach(Bitmap::recycle) }
            file.delete()
        }
    }

    @Test
    fun replacementAndCloseReleaseDescriptorsAndDropQueuedTiles() {
        val first = coloredFixture(Color.RED)
        val second = coloredFixture(Color.BLUE)
        val loaded = CountDownLatch(1)
        val delivered = CountDownLatch(1)
        val failures = AtomicInteger()
        val bitmaps = mutableListOf<Bitmap>()
        val descriptors = mutableListOf<java.io.FileDescriptor>()
        val worker = PdfWorker(
            { loaded.countDown() },
            { _, bitmap ->
                bitmaps.add(bitmap)
                delivered.countDown()
            },
            { failures.incrementAndGet() }
        )
        val queue = queue(worker)
        fun source(file: File): ParcelFileDescriptor = descriptor(file).also {
            descriptors.add(it.fileDescriptor)
        }
        try {
            ui.onMain { worker.open { source(first) } }
            assertTrue(loaded.await(10, TimeUnit.SECONDS))
            ui.onMain {
                val tile = Tile(0, 1.0, 0, 0, 64, 64)
                worker.request(listOf(tile))
                fence(queue)
                worker.open { source(second) }
                fence(queue)
                assertFalse(descriptors.first().valid())
                worker.request(listOf(tile))
                fence(queue)
            }
            assertTrue(delivered.await(5, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            ui.onMain {
                assertEquals(1, bitmaps.size)
                assertEquals(Color.BLUE, bitmaps.single().getPixel(32, 32))
                repeat(6) { index ->
                    worker.open { source(if (index % 2 == 0) first else second) }
                    fence(queue)
                    worker.closeDocument()
                    fence(queue)
                    assertTrue(descriptors.all { !it.valid() })
                }
                worker.open { source(first) }
                fence(queue)
                worker.request(listOf(Tile(0, 1.0, 0, 0, 64, 64)))
                fence(queue)
                worker.close()
            }
            assertTrue(queue.awaitTermination(5, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            assertTrue(descriptors.all { !it.valid() })
            assertEquals(1, bitmaps.size)
            assertEquals(0, failures.get())
        } finally {
            ui.onMain { worker.close() }
            assertTrue(queue.awaitTermination(5, TimeUnit.SECONDS))
            ui.onMain { bitmaps.forEach(Bitmap::recycle) }
            first.delete()
            second.delete()
        }
    }

    @Suppress("DEPRECATION") // Inject an API 33 notification, not real system memory pressure.
    @Test
    fun homeAndTrimKeepTheCacheEmptyUntilTheSameActivityReturns() {
        val file = File.createTempFile(
            "lifecycle-fixture-",
            ".pdf",
            instrumentation.targetContext.cacheDir
        )
        SyntheticDocument.write(file)
        val activity = ui.launch()
        try {
            ui.onMain {
                (activity as MainActivity).openDocument(Uri.fromFile(file))
            }
            val view = ui.awaitReady(activity)
            ui.pinch(view)
            ui.onMain {
                view.setPageLayoutSettings(PageLayoutSettings(PageSizeMode.UNIFORM_WIDTH, 24))
            }
            ui.await("Fixture page not prepared") { view.goToPage(20, 0.4) }
            ui.awaitReady(activity)
            val before = ui.onMain { requireNotNull(view.readingPosition()) }
            val layout = ui.onMain { view.pageLayoutSettings() }
            instrumentation.targetContext.startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            ui.await("Stopped reader retained cached tiles") {
                !activity.hasWindowFocus() &&
                    view.renderState().cachedBytes == 0 &&
                    view.renderState().visibleTiles == 0
            }
            ui.onMain { activity.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) }
            SystemClock.sleep(500)
            ui.onMain {
                assertEquals(0, view.renderState().cachedBytes)
                assertEquals(0, view.renderState().visibleTiles)
                assertEquals(before, view.readingPosition())
            }
            instrumentation.targetContext.startActivity(
                Intent(instrumentation.targetContext, MainActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    )
            )
            ui.await("Reader did not return") { activity.hasWindowFocus() }
            assertSame(view, ui.awaitReady(activity))
            ui.onMain {
                assertEquals(before, view.readingPosition())
                assertEquals(layout, view.pageLayoutSettings())
                assertTrue(view.renderState().cachedBytes in 1..CACHE_BYTES)
                activity.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
                assertEquals(0, view.renderState().cachedBytes)
            }
            ui.awaitReady(activity)
            ui.onMain {
                assertEquals(before, view.readingPosition())
                activity.finish()
            }
            ui.await("Destroyed reader retained cache") {
                activity.isDestroyed && view.renderState().cachedBytes == 0
            }
        } finally {
            ui.onMain { if (!activity.isDestroyed) activity.finish() }
            removeRecord(file)
            file.delete()
        }
    }

    private fun coloredFixture(color: Int): File {
        val file = File.createTempFile(
            "lifecycle-color-",
            ".pdf",
            instrumentation.targetContext.cacheDir
        )
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(600, 800, 1).create())
            page.canvas.drawColor(color)
            document.finishPage(page)
            file.outputStream().use(document::writeTo)
            return file
        } finally {
            document.close()
        }
    }

    private fun descriptor(file: File): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

    private fun queue(worker: PdfWorker): ExecutorService =
        // A barrier on our own executor makes the delivery/cancellation race deterministic.
        PdfWorker::class.java.getDeclaredField("executor").apply { isAccessible = true }
            .get(worker) as ExecutorService

    private fun fence(queue: ExecutorService) {
        queue.submit {}.get(5, TimeUnit.SECONDS)
    }

    private fun removeRecord(file: File) {
        val application = instrumentation.targetContext.applicationContext as ReaderApplication
        val done = CountDownLatch(1)
        application.library.execute({ store ->
            store.remove(Uri.fromFile(file).toString())
        }) { done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
    }

    companion object {
        private const val CACHE_BYTES = 64 * 1024 * 1024
    }
}
