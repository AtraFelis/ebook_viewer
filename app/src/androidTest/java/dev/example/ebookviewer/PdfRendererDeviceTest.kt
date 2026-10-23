package dev.example.ebookviewer

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PdfRendererDeviceTest {
    @Test
    fun pagePreviewRemainsVisibleOutsideRefinedTile() {
        val colors = IntArray(2)
        val state = AtomicReference<RenderState>()
        instrumentation.runOnMainSync {
            var requests = emptyList<Tile>()
            val view = PdfCanvasView(instrumentation.targetContext) { requests = it }
            view.layout(0, 0, 600, 800)
            view.setPages(listOf(PageSize(600, 800)))
            val preview = requests.first()
            val refined = requests.first { it.scale == 1.0 && it.x == 0 && it.y == 0 }
            val previewBitmap = Bitmap.createBitmap(
                preview.width,
                preview.height,
                Bitmap.Config.ARGB_8888
            )
            val tileBitmap = Bitmap.createBitmap(
                refined.width,
                refined.height,
                Bitmap.Config.ARGB_8888
            )
            val output = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
            try {
                previewBitmap.eraseColor(Color.BLUE)
                tileBitmap.eraseColor(Color.RED)
                view.putTile(preview, previewBitmap)
                view.putTile(refined, tileBitmap)
                view.draw(android.graphics.Canvas(output))
                colors[0] = output.getPixel(300, 400)
                colors[1] = output.getPixel(100, 700)
                state.set(view.renderState())
            } finally {
                view.reset()
                previewBitmap.recycle()
                tileBitmap.recycle()
                output.recycle()
            }
        }
        assertEquals(Color.RED, colors[0])
        assertEquals(Color.BLUE, colors[1])
        assertEquals(1, state.get().coveredPages)
        assertTrue(state.get().readyTiles < state.get().visibleTiles)
        assertTrue(state.get().cachedBytes <= 64 * 1024 * 1024)
    }

    @Test
    fun initialPagesPublishBeforeFullCatalogWithoutResettingViewport() {
        val file = File.createTempFile(
            "p0-progressive-",
            ".pdf",
            instrumentation.targetContext.cacheDir
        )
        val complete = CountDownLatch(1)
        val counts = ArrayList<Int>()
        val states = ArrayList<Bundle>()
        val error = AtomicReference<String?>()
        lateinit var view: PdfCanvasView
        instrumentation.runOnMainSync {
            view = PdfCanvasView(instrumentation.targetContext)
            view.layout(0, 0, 1080, 2100)
            view.restoreViewport(
                Bundle().apply {
                    putDouble("zoom", 2.0)
                    putDouble("centerX", 400.0)
                    putDouble("centerY", 600.0)
                }
            )
        }
        val worker = PdfWorker(
            onLoaded = { pages ->
                view.setPages(pages)
                counts.add(pages.size)
                states.add(requireNotNull(view.saveViewport()))
                if (pages.size == 100) complete.countDown()
            },
            onTile = { _, bitmap -> bitmap.recycle() },
            onError = {
                error.set("Progressive open failed")
                complete.countDown()
            }
        )
        try {
            SyntheticDocument.write(file)
            instrumentation.runOnMainSync {
                worker.open { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }
            }
            assertTrue("Full catalog timeout", complete.await(30, TimeUnit.SECONDS))
            assertEquals(null, error.get())
            assertEquals(4, counts.first())
            assertEquals(100, counts.last())
            assertTrue(counts.zipWithNext().all { (before, after) -> after > before })
            states.forEach {
                assertEquals(2.0, it.getDouble("zoom"), 0.000001)
                assertEquals(400.0, it.getDouble("centerX"), 0.000001)
                assertEquals(600.0, it.getDouble("centerY"), 0.000001)
            }
        } finally {
            instrumentation.runOnMainSync { worker.close() }
            file.delete()
        }
    }

    @Test
    fun rasterDocumentRendersOffsetTileAtZoom() {
        val file = File.createTempFile("p0-raster-", ".pdf", instrumentation.targetContext.cacheDir)
        val scan = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888)
        val tile = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        try {
            val canvas = android.graphics.Canvas(scan)
            canvas.drawColor(Color.WHITE)
            canvas.drawRect(400f, 400f, 800f, 800f, Paint().apply { color = Color.BLUE })
            val pdf = PdfDocument()
            try {
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(600, 800, 1).create())
                page.canvas.drawBitmap(scan, null, Rect(0, 0, 600, 800), null)
                pdf.finishPage(page)
                file.outputStream().use(pdf::writeTo)
            } finally {
                pdf.close()
            }
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = try {
                PdfRenderer(descriptor)
            } catch (error: Exception) {
                descriptor.close()
                throw error
            }
            renderer.use {
                it.openPage(0).use { page ->
                    page.render(
                        tile,
                        Rect(0, 0, 256, 256),
                        Matrix().apply {
                            setScale(2f, 2f)
                            postTranslate(-400f, -400f)
                        },
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )
                    assertEquals(Color.BLUE, tile.getPixel(128, 128))
                }
            }
        } finally {
            scan.recycle()
            tile.recycle()
            file.delete()
        }
    }

    @Test
    fun fiveHundredMixedPagesHaveCorrectLastPageAndLayout() {
        val file = File.createTempFile("p0-large-", ".pdf", instrumentation.targetContext.cacheDir)
        try {
            val pdf = PdfDocument()
            try {
                repeat(500) { index ->
                    val width = if (index % 2 == 0) 600 else 800
                    val height = if (index % 2 == 0) 800 else 600
                    val page = pdf.startPage(
                        PdfDocument.PageInfo.Builder(width, height, index + 1).create()
                    )
                    page.canvas.drawColor(Color.WHITE)
                    pdf.finishPage(page)
                }
                file.outputStream().use(pdf::writeTo)
            } finally {
                pdf.close()
            }
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = try {
                PdfRenderer(descriptor)
            } catch (error: Exception) {
                descriptor.close()
                throw error
            }
            renderer.use {
                assertEquals(500, it.pageCount)
                val pages = (0 until it.pageCount).map { index ->
                    it.openPage(index).use { page -> PageSize(page.width, page.height) }
                }
                assertEquals(PageSize(800, 600), pages.last())
                val layout = DocumentLayout(pages)
                assertEquals(350_000.0, layout.height, 0.0)
                assertEquals(499, layout.pageAt(layout.height - 1))
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun supersededOpenClosesDescriptorAndPublishesOnlyLatestDocument() {
        val file = fixture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val loaded = CountDownLatch(1)
        val count = AtomicInteger()
        val oldDescriptor = AtomicReference<FileDescriptor>()
        val error = AtomicReference<String?>()
        val worker = PdfWorker(
            onLoaded = {
                count.incrementAndGet()
                loaded.countDown()
            },
            onTile = { _, bitmap -> bitmap.recycle() },
            onError = {
                error.set("Open failed")
                loaded.countDown()
            }
        )
        try {
            instrumentation.runOnMainSync {
                worker.open {
                    val descriptor = ParcelFileDescriptor.open(
                        file,
                        ParcelFileDescriptor.MODE_READ_ONLY
                    )
                    oldDescriptor.set(descriptor.fileDescriptor)
                    entered.countDown()
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        descriptor.close()
                        throw IOException("Test release timeout")
                    }
                    descriptor
                }
            }
            assertTrue("Worker never opened source", entered.await(5, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                worker.open { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }
            }
            release.countDown()
            assertTrue("Latest document did not publish", loaded.await(10, TimeUnit.SECONDS))
            assertEquals(null, error.get())
            assertEquals(1, count.get())
            assertTrue("Superseded descriptor is still open", !oldDescriptor.get().valid())
        } finally {
            release.countDown()
            instrumentation.runOnMainSync { worker.close() }
            file.delete()
        }
    }

    private fun descendants(view: View): List<View> {
        val result = arrayListOf(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                result.addAll(
                    descendants(view.getChildAt(index))
                )
            }
        }
        return result
    }

    private fun awaitSynthetic(activity: Activity): PdfCanvasView {
        val expected = activity.getString(R.string.document_ready, 100)
        val deadline = SystemClock.uptimeMillis() + 15_000
        var viewer: PdfCanvasView? = null
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync {
                val views = descendants(activity.window.decorView)
                if (views.filterIsInstance<TextView>().any { it.text.toString() == expected }) {
                    viewer = views.filterIsInstance<PdfCanvasView>().single()
                }
            }
            viewer?.let { return it }
            SystemClock.sleep(50)
        }
        throw AssertionError("Synthetic document did not load in the Activity")
    }

    @Test
    fun syntheticDocumentSurvivesActivityRecreation() {
        var activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        try {
            instrumentation.runOnMainSync {
                (activity as MainActivity).openSample()
            }
            val viewer = awaitSynthetic(activity)
            var before = 0.0
            instrumentation.runOnMainSync {
                before = requireNotNull(viewer.saveViewport()).getDouble("zoom")
                activity.recreate()
            }
            activity = requireNotNull(instrumentation.waitForMonitorWithTimeout(monitor, 10_000))
            val restored = awaitSynthetic(activity)
            instrumentation.runOnMainSync {
                assertEquals(
                    before,
                    requireNotNull(restored.saveViewport()).getDouble("zoom"),
                    0.000001
                )
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun fixture(): File {
        val file = File.createTempFile("p0-test-", ".pdf", instrumentation.targetContext.cacheDir)
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(512, 512, 1).create())
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawRect(256f, 256f, 512f, 512f, Paint().apply { color = Color.BLUE })
            pdf.finishPage(page)
            file.outputStream().use(pdf::writeTo)
        } finally {
            pdf.close()
        }
        return file
    }

    @Test
    fun matrixRendersOnlyTheRequestedTile() {
        val file = fixture()
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        try {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = try {
                PdfRenderer(descriptor)
            } catch (
                error: Exception
            ) {
                descriptor.close()
                throw error
            }
            renderer.use {
                assertEquals(1, it.pageCount)
                it.openPage(0).use { page ->
                    val matrix = Matrix().apply {
                        setScale(2f, 2f)
                        postTranslate(-512f, -512f)
                    }
                    page.render(
                        bitmap,
                        Rect(0, 0, 128, 128),
                        matrix,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )
                    assertEquals(Color.BLUE, bitmap.getPixel(64, 64))
                    page.render(
                        bitmap,
                        Rect(0, 0, 128, 128),
                        Matrix(),
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )
                    assertEquals(Color.WHITE, bitmap.getPixel(64, 64))
                }
            }
        } finally {
            bitmap.recycle()
            file.delete()
        }
    }

    @Test
    fun corruptPdfFailsWithoutLeakingTheCallerDescriptor() {
        val file = File.createTempFile(
            "p0-corrupt-",
            ".pdf",
            instrumentation.targetContext.cacheDir
        )
        file.writeText("not a PDF")
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                try {
                    PdfRenderer(descriptor).close()
                    fail("Corrupt PDF unexpectedly opened")
                } catch (_: IOException) {
                    // Caller retains descriptor responsibility when construction fails.
                }
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun productionWorkerPublishesRenderedTile() {
        val file = fixture()
        val loaded = CountDownLatch(1)
        val rendered = CountDownLatch(1)
        val bitmap = AtomicReference<Bitmap?>()
        val error = AtomicReference<String?>()
        val worker = PdfWorker(
            onLoaded = { loaded.countDown() },
            onTile = { _, value ->
                bitmap.set(value)
                rendered.countDown()
            },
            onError = {
                error.set("Worker reported an error")
                loaded.countDown()
                rendered.countDown()
            }
        )
        try {
            instrumentation.runOnMainSync {
                worker.open { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }
            }
            assertTrue("Open timeout", loaded.await(10, TimeUnit.SECONDS))
            assertEquals(null, error.get())
            instrumentation.runOnMainSync {
                worker.request(listOf(Tile(0, 2.0, 512, 512, 128, 128)))
            }
            assertTrue("Render timeout", rendered.await(10, TimeUnit.SECONDS))
            assertEquals(null, error.get())
            assertNotNull(bitmap.get())
            assertEquals(Color.BLUE, requireNotNull(bitmap.get()).getPixel(64, 64))
        } finally {
            instrumentation.runOnMainSync { worker.close() }
            bitmap.get()?.recycle()
            file.delete()
        }
    }

    companion object {
        lateinit var instrumentation: Instrumentation
    }
}
