package dev.example.ebookviewer

import android.app.ActivityManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.RemoteException
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** One first-page render per isolated process; no original path or URI enters this service. */
class BookCoverService : Service() {
    private val started = AtomicBoolean()
    private val worker = Executors.newSingleThreadExecutor()
    private val guard = Executors.newSingleThreadScheduledExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val deadline = Runnable { terminate() }
    private val incoming = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            if (message.what == CANCEL) terminate()
            if (message.what == RENDER) {
                @Suppress("DEPRECATION")
                val source = message.data.getParcelable<ParcelFileDescriptor>("source")

                @Suppress("DEPRECATION")
                val target = message.data.getParcelable<ParcelFileDescriptor>("target")
                if (source != null && target != null && started.compareAndSet(false, true)) {
                    render(source, target, message.replyTo)
                } else {
                    source?.close()
                    target?.close()
                }
            }
            true
        }
    )

    override fun onBind(intent: Intent): IBinder {
        main.postDelayed(deadline, PageTextLimits.TIMEOUT_MS)
        return incoming.binder
    }

    private fun allowed(): Boolean = try {
        val rss = File("/proc/self/status").useLines { lines ->
            lines.first { it.startsWith("VmRSS:") }.split(Regex("\\s+"))[1].toLong()
        }
        val memory = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        Process.myUid() != applicationInfo.uid &&
            rss <= PageTextLimits.MAX_RSS_KIB &&
            PageTextLimits.memoryAllowed(memory.availMem, memory.threshold, memory.lowMemory)
    } catch (_: Exception) {
        // Missing measurements mean that this optional background render is not permitted.
        false
    }

    private fun render(
        source: ParcelFileDescriptor,
        target: ParcelFileDescriptor,
        reply: Messenger?
    ) {
        guard.scheduleWithFixedDelay({ if (!allowed()) terminate() }, 0, 100, TimeUnit.MILLISECONDS)
        worker.execute {
            var ok = false
            try {
                source.use { descriptor ->
                    target.use { output ->
                        if (!allowed()) return@use
                        PdfRenderer(descriptor).use { pdf ->
                            pdf.openPage(0).use { page ->
                                val scale = minOf(384.0 / page.width, 512.0 / page.height)
                                val width = (page.width * scale).toInt().coerceIn(1, 384)
                                val height = (page.height * scale).toInt().coerceIn(1, 512)
                                val bitmap = Bitmap.createBitmap(
                                    width,
                                    height,
                                    Bitmap.Config.ARGB_8888
                                )
                                try {
                                    bitmap.eraseColor(Color.WHITE)
                                    page.render(
                                        bitmap,
                                        null,
                                        null,
                                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                    )
                                    ParcelFileDescriptor.AutoCloseOutputStream(
                                        ParcelFileDescriptor.dup(output.fileDescriptor)
                                    ).use {
                                        ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                                    }
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Bad, protected or non-seekable PDFs keep the default document cover.
                ok = false
            } catch (_: OutOfMemoryError) {
                terminate()
                return@execute
            }
            try {
                reply?.send(Message.obtain(null, if (ok) OK else FAILED))
            } catch (_: RemoteException) {
                terminate()
            }
        }
    }

    private fun terminate() {
        if (Process.myUid() != applicationInfo.uid) Process.killProcess(Process.myPid())
    }

    override fun onDestroy() {
        main.removeCallbacks(deadline)
        worker.shutdownNow()
        guard.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val RENDER = 1
        const val CANCEL = 2
        const val OK = 3
        const val FAILED = 4
    }
}
