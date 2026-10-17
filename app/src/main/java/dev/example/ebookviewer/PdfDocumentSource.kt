package dev.example.ebookviewer

import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

class PdfOpenRequest {
    val signal = CancellationSignal()
    private val cancelled = AtomicBoolean()

    internal fun cancel(executor: Executor) {
        cancelled.set(true)
        // Provider Binder cancellation must not block the UI or the PDF worker being cancelled.
        executor.execute { signal.cancel() }
    }

    fun throwIfCanceled() {
        if (cancelled.get()) throw OperationCanceledException()
        signal.throwIfCanceled()
    }
}

/** Worker-only. Takes ownership on entry, returning exactly one owned, seekable descriptor. */
class PdfDocumentSource(
    private val directory: File,
    private val maximumBytes: Long = 256L * 1024 * 1024,
    private val idleTimeoutMs: Long = 30_000
) {
    init {
        require(maximumBytes > 0 && idleTimeoutMs > 0)
    }

    fun prepare(source: ParcelFileDescriptor, request: PdfOpenRequest): ParcelFileDescriptor {
        var prepared: ParcelFileDescriptor? = null
        try {
            request.throwIfCanceled()
            val seekable = try {
                Os.lseek(source.fileDescriptor, 0, OsConstants.SEEK_CUR)
                true
            } catch (error: ErrnoException) {
                if (error.errno != OsConstants.ESPIPE) throw error
                false
            }
            if (seekable) {
                if (source.statSize == 0L) throw PdfSourceException(PdfFailure.EMPTY)
                return source
            }
            prepared = copy(source, request)
            source.close()
            return prepared
        } catch (error: Exception) {
            // Preserve the original safe reason while closing both sides of an interrupted handoff.
            for (descriptor in listOfNotNull(prepared, source)) {
                try {
                    descriptor.close()
                } catch (closeError: Exception) {
                    error.addSuppressed(closeError)
                }
            }
            throw error
        }
    }

    private fun copy(source: ParcelFileDescriptor, request: PdfOpenRequest): ParcelFileDescriptor {
        val file = try {
            File.createTempFile("pdf-import-", ".tmp", directory)
        } catch (_: IOException) {
            throw PdfSourceException(PdfFailure.STORAGE)
        }
        var owned: ParcelFileDescriptor? = null
        try {
            FileOutputStream(file).use { output ->
                owned = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                // Unlink before copying. Even process death cannot leave a named PDF copy behind.
                if (!file.delete()) throw PdfSourceException(PdfFailure.STORAGE)
                copyPipe(source, output, request)
            }
            request.throwIfCanceled()
            return requireNotNull(owned).also { owned = null }
        } catch (error: OperationCanceledException) {
            throw error
        } catch (error: PdfSourceException) {
            throw error
        } catch (_: IOException) {
            throw PdfSourceException(PdfFailure.STORAGE)
        } finally {
            owned?.close()
            file.delete()
        }
    }

    private fun copyPipe(
        source: ParcelFileDescriptor,
        output: FileOutputStream,
        request: PdfOpenRequest
    ) {
        val poll = StructPollfd().apply {
            fd = source.fileDescriptor
            events = (OsConstants.POLLIN or OsConstants.POLLHUP).toShort()
        }
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        var lastRead = SystemClock.elapsedRealtime()
        while (true) {
            request.throwIfCanceled()
            if (SystemClock.elapsedRealtime() - lastRead >= idleTimeoutMs) {
                throw PdfSourceException(PdfFailure.TIMEOUT)
            }
            val count = try {
                if (Os.poll(arrayOf(poll), 100) == 0) continue
                request.throwIfCanceled()
                if (poll.revents.toInt() and (OsConstants.POLLERR or OsConstants.POLLNVAL) != 0) {
                    throw PdfSourceException(PdfFailure.READ)
                }
                Os.read(source.fileDescriptor, buffer, 0, buffer.size)
            } catch (error: ErrnoException) {
                if (error.errno == OsConstants.EINTR) continue
                throw PdfSourceException(PdfFailure.READ)
            }
            if (count == 0) break
            if (count > maximumBytes - total) throw PdfSourceException(PdfFailure.COPY_LIMIT)
            // The byte limit bounds the import; write failure handles actual allocation availability.
            output.write(buffer, 0, count)
            total += count
            lastRead = SystemClock.elapsedRealtime()
        }
        // Reliable pipes can report a provider error after EOF rather than throwing on read.
        try {
            source.checkError()
        } catch (_: IOException) {
            throw PdfSourceException(PdfFailure.READ)
        }
        if (total == 0L) throw PdfSourceException(PdfFailure.EMPTY)
    }
}
