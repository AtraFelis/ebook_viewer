package dev.example.ebookviewer

import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.Executors

enum class OrientationMode { AUTO, PORTRAIT, LANDSCAPE }

/** Application-owned queue: settings remain ordered across overlapping Activity lifetimes. */
class ReaderSettingsRepository(private val file: () -> File) : java.io.Closeable {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var stored: OrientationMode? = null

    fun load(complete: (Result<OrientationMode>) -> Unit) = submit(complete) { read() }

    private fun read(): OrientationMode = stored ?: run {
        val path = file()
        try {
            AtomicFile(path).openRead().use { input ->
                val data = DataInputStream(input)
                require(data.readInt() == MAGIC && data.readInt() == 1)
                val mode = OrientationMode.valueOf(data.readUTF())
                require(data.read() == -1)
                mode
            }
        } catch (error: FileNotFoundException) {
            if (path.exists()) throw error
            OrientationMode.AUTO
        }
    }.also { stored = it }

    fun save(mode: OrientationMode, complete: (Result<OrientationMode>) -> Unit) =
        submit(complete) {
            // Read first so unknown/corrupt versions cannot silently be overwritten by a new selection.
            read()
            val atomic = AtomicFile(file())
            val stream = atomic.startWrite()
            try {
                val data = DataOutputStream(stream)
                data.writeInt(MAGIC)
                data.writeInt(1)
                data.writeUTF(mode.name)
                data.flush()
                atomic.finishWrite(stream)
                stored = mode
                mode
            } catch (error: Exception) {
                atomic.failWrite(stream)
                throw error
            }
        }

    private fun submit(complete: (Result<OrientationMode>) -> Unit, task: () -> OrientationMode) {
        executor.execute {
            val result = try {
                Result.success(task())
            } catch (
                error: Exception
            ) {
                Result.failure(error)
            }
            main.post { complete(result) }
        }
    }

    companion object {
        private const val MAGIC = 0x45565331
    }

    override fun close() {
        executor.shutdown()
    }
}
