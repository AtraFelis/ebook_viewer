package dev.example.ebookviewer

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Process
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Main-thread owner. At most one running and one pending, already-published 512px preview. */
class PageBackgroundAnalyzer(private val result: (Int, Int) -> Unit) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicLong()
    private var closed = false
    private val executor = ThreadPoolExecutor(
        1,
        1,
        0,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(1),
        { runnable ->
            Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                runnable.run()
            }, "page-background")
        }
    )

    fun submit(page: Int, bitmap: Bitmap, fallback: Int) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (closed) return
        val id = generation.incrementAndGet()
        executor.queue.clear()
        executor.execute {
            val color = sample(bitmap, fallback) ?: return@execute
            if (generation.get() != id) return@execute
            main.post {
                if (!closed && generation.get() == id) result(page, color)
            }
        }
    }

    private fun sample(bitmap: Bitmap, fallback: Int): Int? {
        if (bitmap.width > 512 || bitmap.height > 512 || bitmap.isRecycled) return fallback
        val samples = IntArray(128)
        val insetX = (bitmap.width / 50).coerceAtMost((bitmap.width - 1) / 2)
        val insetY = (bitmap.height / 50).coerceAtMost((bitmap.height - 1) / 2)
        try {
            for (index in 0 until 32) {
                if (Thread.currentThread().isInterrupted) return null
                val x = insetX + (bitmap.width - 1 - 2 * insetX) * index / 31
                val y = insetY + (bitmap.height - 1 - 2 * insetY) * index / 31
                samples[index * 4] = bitmap.getPixel(x, insetY)
                samples[index * 4 + 1] = bitmap.getPixel(x, bitmap.height - 1 - insetY)
                samples[index * 4 + 2] = bitmap.getPixel(insetX, y)
                samples[index * 4 + 3] = bitmap.getPixel(bitmap.width - 1 - insetX, y)
            }
        } catch (_: IllegalStateException) {
            // A recycled/hardware bitmap is not a usable sample. The analyzer never recycles it.
            return fallback
        }
        return estimatePageBackground(samples, fallback)
    }

    fun isTerminated(): Boolean = executor.isTerminated

    override fun close() {
        check(Looper.myLooper() == Looper.getMainLooper())
        closed = true
        generation.incrementAndGet()
        executor.queue.clear()
        executor.shutdownNow()
    }
}
