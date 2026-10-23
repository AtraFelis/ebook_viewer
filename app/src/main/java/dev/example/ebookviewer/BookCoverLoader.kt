package dev.example.ebookviewer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.SystemClock
import android.util.LruCache
import java.io.Closeable
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Main-thread scheduler with bounded local pixels; file access and decoding belong to IO. */
internal class BookCoverLoader(context: Context, private val changed: () -> Unit) : Closeable {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val directory = File(context.cacheDir, "book-covers-v1")
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val failed = linkedSetOf<String>()
    private var requested = emptyList<String>()
    private var attempt: Attempt? = null
    private var closed = false
    private var preparing = false
    private val attempted = linkedSetOf<String>()

    fun get(uri: String): Bitmap? = cache.get(uri)

    private inner class Attempt(val uri: String) : ServiceConnection {
        val signal = CancellationSignal()
        var finishing = false
        var bound = false
        var remote: Messenger? = null
        var source: ParcelFileDescriptor? = null
        var target: ParcelFileDescriptor? = null
        var file: File? = null
        var destination: File? = null
        var sent = false
        val timeout = Runnable { finish(this, false) }
        val reply = Messenger(
            Handler(Looper.getMainLooper()) { message ->
                finish(this, message.what == BookCoverService.OK)
                true
            }
        )
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (attempt !== this) return
            remote = Messenger(binder)
            send()
        }
        override fun onServiceDisconnected(name: ComponentName) = finish(this, false)
        override fun onBindingDied(name: ComponentName) = finish(this, false)
        override fun onNullBinding(name: ComponentName) = finish(this, false)
        fun send() {
            val service = remote ?: return
            val input = source ?: return
            val output = target ?: return
            if (attempt !== this || sent) return
            sent = true
            try {
                service.send(
                    Message.obtain(null, BookCoverService.RENDER).apply {
                        data =
                            Bundle().apply {
                                putParcelable("source", input)
                                putParcelable("target", output)
                            }
                        replyTo = reply
                    }
                )
            } catch (_: RemoteException) {
                finish(this, false)
            } finally {
                input.close()
                output.close()
                source = null
                target = null
            }
        }
    }

    fun request(records: List<ReadingRecord>) {
        if (closed || Build.VERSION.SDK_INT < 30) return
        val visible = records.map { it.uri }.distinct().take(32)
        if (visible != requested) {
            attempted.retainAll(visible.toSet())
            attempted.removeAll(visible.filter { it !in requested }.toSet())
        }
        requested = visible
        if (attempt?.uri !in requested) cancelAttempt()
        next()
    }

    fun cancel() {
        requested = emptyList()
        attempted.clear()
        cancelAttempt()
    }

    fun clear() {
        cancel()
        cache.evictAll()
        failed.clear()
        io.execute {
            directory.listFiles()?.forEach { it.delete() }
            main.post { if (!closed) changed() }
        }
    }

    private fun next() {
        if (closed || attempt != null || preparing) return
        val uri = requested.firstOrNull {
            cache.get(it) == null && it !in failed && it !in attempted
        } ?: return
        attempted.add(uri)
        val current = Attempt(uri)
        attempt = current
        preparing = true
        main.postDelayed(current.timeout, PageTextLimits.TIMEOUT_MS + 1_000)
        io.execute {
            var source: ParcelFileDescriptor? = null
            var target: ParcelFileDescriptor? = null
            var temporary: File? = null
            try {
                check(directory.isDirectory || directory.mkdirs())
                prune()
                // Some providers omit reliable change metadata; refresh invalidates all covers.
                val key = MessageDigest.getInstance(
                    "SHA-256"
                ).digest(uri.toByteArray()).joinToString("") {
                    "%02x".format(it)
                }
                val destination = File(directory, "$key.png")
                val cached = decode(destination)
                if (cached != null) {
                    destination.setLastModified(System.currentTimeMillis())
                    main.post {
                        preparing = false
                        if (attempt ===
                            current
                        ) {
                            cache.put(uri, cached)
                            finish(current, true, cached = true)
                        }
                        next()
                    }
                    return@execute
                }
                source =
                    context.contentResolver.openFileDescriptor(Uri.parse(uri), "r", current.signal)
                requireNotNull(source)
                temporary = File.createTempFile("cover-", ".pending", directory)
                target =
                    ParcelFileDescriptor.open(
                        temporary,
                        ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE
                    )
                val input = source
                val output = target
                val file = temporary
                main.post {
                    preparing = false
                    if (attempt !== current || current.finishing) {
                        io.execute {
                            input.close()
                            output.close()
                            file.delete()
                        }
                        next()
                    } else {
                        current.source = input
                        current.target = output
                        current.file = file
                        current.destination = destination
                        bind(current)
                    }
                }
            } catch (_: Exception) {
                source?.close()
                target?.close()
                temporary?.delete()
                main.post {
                    preparing = false
                    finish(current, false)
                    next()
                }
            }
        }
    }

    private fun bind(current: Attempt) {
        if (Build.VERSION.SDK_INT < 30) {
            finish(current, false)
            return
        }
        try {
            current.bound = context.bindIsolatedService(
                Intent(context, BookCoverService::class.java),
                Context.BIND_AUTO_CREATE or Context.BIND_NOT_FOREGROUND,
                "book_cover_${SystemClock.elapsedRealtimeNanos()}",
                { task -> main.post(task) },
                current
            )
            if (!current.bound) finish(current, false)
        } catch (_: SecurityException) {
            finish(current, false)
        }
    }

    private fun decode(file: File): Bitmap? {
        if (!file.isFile || file.length() !in 1..1_048_576) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        if (options.outWidth !in 1..384 || options.outHeight !in 1..512) return null
        return try {
            BitmapFactory.decodeFile(file.path)
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun finish(current: Attempt, success: Boolean, cached: Boolean = false) {
        if (attempt !== current || current.finishing) return
        current.finishing = true
        main.removeCallbacks(current.timeout)
        release(current)
        if (cached) {
            attempt = null
            changed()
            next()
            return
        }
        val file = current.file
        val destination = current.destination
        io.execute {
            val bitmap = if (success && file != null) decode(file) else null
            if (bitmap != null && destination != null && file != null) {
                if (!file.renameTo(destination)) file.delete()
            } else {
                file?.delete()
            }
            prune()
            main.post {
                if (attempt === current) {
                    if (bitmap != null) {
                        cache.put(current.uri, bitmap)
                    } else {
                        failed.add(current.uri)
                        if (failed.size > 256) failed.remove(failed.first())
                    }
                    attempt = null
                    changed()
                    next()
                }
            }
        }
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - 15_000
        directory.listFiles()?.filter {
            it.extension == "pending" && it.lastModified() < cutoff
        }?.forEach { it.delete() }
        val files = directory.listFiles()?.filter { it.extension == "png" }.orEmpty()
        var total = files.sumOf { it.length() }
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= 64L * 1024 * 1024) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    private fun release(current: Attempt) {
        current.signal.cancel()
        main.removeCallbacks(current.timeout)
        try {
            current.remote?.send(Message.obtain(null, BookCoverService.CANCEL))
        } catch (
            _: RemoteException
        ) {
            // A dead isolated worker has no remaining ownership of the request.
        }
        if (current.bound) context.unbindService(current)
        current.bound = false
        current.remote = null
        current.source?.close()
        current.target?.close()
        current.source = null
        current.target = null
    }

    private fun cancelAttempt() {
        val current = attempt ?: return
        attempt = null
        release(current)
        val file = current.file
        io.execute { file?.delete() }
    }

    override fun close() {
        cancel()
        closed = true
        cache.evictAll()
        // Preparation callbacks must release their descriptors before this queue stops.
        io.execute { main.post { io.shutdown() } }
    }
}
