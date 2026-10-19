package dev.example.ebookviewer

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.SystemClock

/** Main-thread owner. Holds one bounded result only; every finish unbinds the isolated process. */
class PageTextClient(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var attempt: Attempt? = null

    private inner class Attempt(val complete: (Int, String?) -> Unit) : ServiceConnection {
        var bound = false
        var remote: Messenger? = null
        var descriptor: ParcelFileDescriptor? = null
        var page = 0
        var sent = false
        val timeout = Runnable { finish(this, R.string.page_text_stopped, null) }
        val reply = Messenger(
            Handler(Looper.getMainLooper()) { message ->
                if (attempt === this) {
                    val data = message.data
                    val text = data.getString("text")
                    val valid =
                        data.getInt("status", PageTextService.FAILED) == PageTextService.OK &&
                            text != null &&
                            text.length <= PageTextLimits.MAX_CHARACTERS
                    finish(
                        this,
                        if (valid) 0 else R.string.page_text_stopped,
                        if (valid) text else null
                    )
                }
                true
            }
        )

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (attempt !== this) return
            remote = Messenger(service)
            send()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            finish(this, R.string.page_text_stopped, null)
        }

        override fun onBindingDied(name: ComponentName) {
            finish(this, R.string.page_text_stopped, null)
        }

        override fun onNullBinding(name: ComponentName) {
            finish(this, R.string.page_text_stopped, null)
        }

        fun send() {
            if (attempt !== this || sent) return
            val service = remote ?: return
            val source = descriptor ?: return
            sent = true
            try {
                service.send(
                    Message.obtain(null, PageTextService.READ, page, 0).apply {
                        data = Bundle().apply { putParcelable("source", source) }
                        replyTo = reply
                    }
                )
            } catch (_: RemoteException) {
                finish(this, R.string.page_text_stopped, null)
            } finally {
                source.close()
                descriptor = null
            }
        }
    }

    fun read(
        page: Int,
        source: ((ParcelFileDescriptor?) -> Unit) -> Unit,
        complete: (Int, String?) -> Unit
    ) {
        cancel()
        if (!PageTextService.supported()) {
            complete(R.string.page_text_unsupported, null)
            return
        }
        val memory = ActivityManager.MemoryInfo()
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        manager.getMemoryInfo(memory)
        if (!PageTextLimits.memoryAllowed(memory.availMem, memory.threshold, memory.lowMemory)) {
            complete(R.string.page_text_memory, null)
            return
        }
        val current = Attempt(complete).also { it.page = page }
        attempt = current
        main.postDelayed(current.timeout, PageTextLimits.TIMEOUT_MS + 1_000)
        try {
            // A cancelled instance must never terminate or consume the next request's source.
            current.bound = if (Build.VERSION.SDK_INT >= 30) {
                context.bindIsolatedService(
                    Intent(context, PageTextService::class.java),
                    Context.BIND_AUTO_CREATE or Context.BIND_NOT_FOREGROUND,
                    "page_text_${SystemClock.elapsedRealtimeNanos()}",
                    { task -> main.post(task) },
                    current
                )
            } else {
                false
            }
            if (!current.bound) {
                finish(current, R.string.page_text_stopped, null)
                return
            }
            source { descriptor ->
                if (attempt !== current) {
                    descriptor?.close()
                } else if (descriptor == null) {
                    finish(current, R.string.page_text_stopped, null)
                } else {
                    current.descriptor = descriptor
                    current.send()
                }
            }
        } catch (_: SecurityException) {
            finish(current, R.string.page_text_stopped, null)
        }
    }

    fun cancel() {
        val current = attempt ?: return
        attempt = null
        release(current)
    }

    private fun finish(current: Attempt, error: Int, text: String?) {
        if (attempt !== current) return
        attempt = null
        release(current)
        current.complete(error, text)
    }

    private fun release(current: Attempt) {
        main.removeCallbacks(current.timeout)
        current.descriptor?.close()
        current.descriptor = null
        try {
            current.remote?.send(Message.obtain(null, PageTextService.CANCEL))
        } catch (_: RemoteException) {
            // Process death is a terminal outcome, not a retry trigger.
        }
        if (current.bound) context.unbindService(current)
        current.bound = false
        current.remote = null
    }
}
