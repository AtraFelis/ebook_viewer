package dev.example.ebookviewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import kotlin.math.max
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Preview-first native reuse must not replace high-resolution output with cached blurry pixels. */
class SharpnessPixelsDeviceTest {
    @Test
    fun previewThenDifferentScalesMatchFreshPagePixels() {
        val instrumentation = PdfRendererDeviceTest.instrumentation
        for (name in listOf("vector", "gray.pdf", "rgb.pdf")) {
            val file = File(instrumentation.targetContext.cacheDir, "sharpness-pixels.pdf")
            if (name == "vector") {
                SyntheticDocument.write(file)
            } else {
                instrumentation.context.assets.open(name).use { input ->
                    file.outputStream().use { input.copyTo(it) }
                }
            }
            try {
                val expected = LinkedHashMap<Tile, IntArray>()
                PdfRenderer(descriptor(file)).use { renderer ->
                    val size = renderer.openPage(0).use { Pair(it.width, it.height) }
                    val scale = 512.0 / max(size.first, size.second)
                    val tiles = listOf(
                        Tile(0, scale, 0, 0, ceil(size.first * scale).toInt(), 512),
                        Tile(0, 3.0, 0, 0, 512, 512),
                        Tile(0, 8.0, 256, 256, 512, 512),
                        Tile(0, 32.0, 1024, 1024, 512, 512)
                    )
                    for (tile in tiles) {
                        val bitmap = Bitmap.createBitmap(
                            tile.width,
                            tile.height,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.eraseColor(Color.WHITE)
                        renderer.openPage(0).use { page ->
                            val matrix = Matrix().apply {
                                setScale(tile.scale.toFloat(), tile.scale.toFloat())
                                postTranslate(-tile.x.toFloat(), -tile.y.toFloat())
                            }
                            page.render(
                                bitmap,
                                Rect(0, 0, tile.width, tile.height),
                                matrix,
                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                            )
                        }
                        expected[tile] = pixels(bitmap)
                        bitmap.recycle()
                    }
                }
                val remaining = CountDownLatch(expected.size)
                val requested = AtomicBoolean()
                val failure = AtomicReference<Throwable?>()
                val verification = Executors.newSingleThreadExecutor()
                lateinit var worker: PdfWorker
                worker = PdfWorker(
                    onLoaded = {
                        if (requested.compareAndSet(false, true)) {
                            worker.request(expected.keys.toList())
                        }
                    },
                    onTile = { tile, bitmap ->
                        verification.execute {
                            try {
                                assertArrayEquals(
                                    "Fresh/reused synthetic pixels differ",
                                    expected[tile],
                                    pixels(bitmap)
                                )
                            } catch (error: Throwable) {
                                failure.compareAndSet(null, error)
                            } finally {
                                bitmap.recycle()
                                remaining.countDown()
                            }
                        }
                    },
                    onError = { failure.set(AssertionError("Synthetic render failed")) }
                )
                val queue = PdfWorker::class.java.getDeclaredField("executor")
                    .apply { isAccessible = true }.get(worker) as ExecutorService
                try {
                    instrumentation.runOnMainSync { worker.open { descriptor(file) } }
                    assertTrue(
                        "Synthetic pixel comparison timeout",
                        remaining.await(20, TimeUnit.SECONDS)
                    )
                    failure.get()?.let { throw it }
                } finally {
                    instrumentation.runOnMainSync { worker.close() }
                    assertTrue(
                        "Native page owner did not close",
                        queue.awaitTermination(5, TimeUnit.SECONDS)
                    )
                    verification.shutdown()
                    assertTrue(
                        "Pixel verifier did not close",
                        verification.awaitTermination(5, TimeUnit.SECONDS)
                    )
                }
            } finally {
                file.delete()
            }
        }
    }

    private fun descriptor(file: File): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

    private fun pixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }
}
