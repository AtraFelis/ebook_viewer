package dev.example.ebookviewer

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Service
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.PdfRendererPreV
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.RemoteException
import android.os.ext.SdkExtensions
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** One native request per isolated process. Cancel/deadline terminate it without concurrent close. */
class PageTextService : Service() {
    private val started = AtomicBoolean()
    private val worker = Executors.newSingleThreadExecutor()
    private val guard = Executors.newSingleThreadScheduledExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val deadline = Runnable { terminate() }
    private val incoming = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            if (message.what == CANCEL) {
                terminate()
            } else if (message.what == READ) {
                @Suppress("DEPRECATION")
                val descriptor = message.data.getParcelable<ParcelFileDescriptor>("source")
                if (descriptor != null) {
                    if (started.compareAndSet(false, true)) {
                        val rssLimit = message.data
                            .getLong("rssLimitKiB", PageTextLimits.MAX_RSS_KIB)
                            .coerceIn(1, PageTextLimits.MAX_RSS_KIB)
                        extract(descriptor, message.arg1, message.replyTo, rssLimit)
                    } else {
                        descriptor.close()
                    }
                }
            }
            true
        }
    )

    override fun onBind(intent: Intent): IBinder {
        // Independent of sampling, which may be waiting on a system Binder call.
        main.postDelayed(deadline, PageTextLimits.TIMEOUT_MS)
        return incoming.binder
    }

    private fun extract(
        source: ParcelFileDescriptor,
        page: Int,
        reply: Messenger?,
        rssLimit: Long
    ) {
        guard.scheduleWithFixedDelay({
            if (!resourcesAllowed(rssLimit)) terminate()
        }, 0, 100, TimeUnit.MILLISECONDS)
        worker.execute {
            if (!resourcesAllowed(rssLimit)) {
                source.close()
                terminate()
                return@execute
            }
            val result = Bundle()
            result.putInt("workerUid", Process.myUid())
            try {
                val text = source.use { readPage(it, page) }
                result.putInt("status", if (text == null) LIMIT else OK)
                if (text != null) result.putString("text", text)
            } catch (_: Exception) {
                result.putInt("status", FAILED)
            } catch (_: OutOfMemoryError) {
                terminate()
                return@execute
            }
            try {
                reply?.send(Message.obtain().apply { data = result })
            } catch (_: RemoteException) {
                // The owning reader has already gone away; no text is retained.
                terminate()
            }
        }
    }

    private fun resourcesAllowed(rssLimit: Long): Boolean = try {
        val rss = File("/proc/self/status").useLines { lines ->
            lines.first { it.startsWith("VmRSS:") }
                .split(Regex("\\s+")).filter(String::isNotBlank)[1].toLong()
        }
        val memory = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        rss <= rssLimit &&
            PageTextLimits.memoryAllowed(memory.availMem, memory.threshold, memory.lowMemory)
    } catch (_: Exception) {
        // If either observation fails, never continue an unmonitored native operation.
        false
    }

    @SuppressLint("NewApi")
    private fun readPage(source: ParcelFileDescriptor, page: Int): String? {
        // Capability guards keep extension-only classes off unsupported execution paths.
        check(supported())
        return if (Build.VERSION.SDK_INT >= 35) {
            PdfRenderer(source).use { renderer ->
                require(page in 0 until renderer.pageCount)
                renderer.openPage(page).use { opened ->
                    PageTextLimits.bounded(opened.textContents.asSequence().map { it.text })
                }
            }
        } else {
            PdfRendererPreV(source).use { renderer ->
                require(page in 0 until renderer.pageCount)
                renderer.openPage(page).use { opened ->
                    PageTextLimits.bounded(opened.textContents.asSequence().map { it.text })
                }
            }
        }
    }

    private fun terminate() {
        // Manifest isolation is also asserted on device; never terminate the reader process.
        if (Process.myUid() != applicationInfo.uid) Process.killProcess(Process.myPid())
    }

    override fun onUnbind(intent: Intent): Boolean {
        terminate()
        return false
    }

    override fun onDestroy() {
        main.removeCallbacks(deadline)
        guard.shutdownNow()
        worker.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val READ = 1
        const val CANCEL = 2
        const val OK = 0
        const val LIMIT = 1
        const val FAILED = 2

        fun supported(): Boolean {
            if (Build.VERSION.SDK_INT >= 35) return true
            if (Build.VERSION.SDK_INT < 30) return false
            return SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13
        }
    }
}
