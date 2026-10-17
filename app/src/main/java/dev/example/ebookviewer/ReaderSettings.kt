package dev.example.ebookviewer

import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.Executors

enum class OrientationMode { AUTO, PORTRAIT, LANDSCAPE, REVERSE_PORTRAIT, REVERSE_LANDSCAPE }

/** Application-owned queue: settings remain ordered across overlapping Activity lifetimes. */
class ReaderSettingsRepository(private val file: () -> File) : java.io.Closeable {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var stored: OrientationMode? = null
    private var storedBackground: BackgroundSettings? = null
    private var storedPageNumbers: Boolean? = null

    private fun pageNumbersFile(): File = file().let { File(it.parentFile, "${it.name}.pages") }

    fun loadPageNumbers(complete: (Result<Boolean>) -> Unit) =
        submit(complete) { readPageNumbers() }

    private fun readPageNumbers(): Boolean = storedPageNumbers ?: run {
        val path = pageNumbersFile()
        try {
            AtomicFile(path).openRead().use { input ->
                val data = DataInputStream(input)
                require(data.readInt() == 0x45564e31 && data.readInt() == 1)
                val value = data.readUnsignedByte()
                require(value in 0..1 && data.read() == -1)
                value == 1
            }
        } catch (error: FileNotFoundException) {
            if (path.exists()) throw error
            false
        }
    }.also { storedPageNumbers = it }

    fun savePageNumbers(value: Boolean, complete: (Result<Boolean>) -> Unit) = submit(complete) {
        readPageNumbers()
        val atomic = AtomicFile(pageNumbersFile())
        val stream = atomic.startWrite()
        try {
            val data = DataOutputStream(stream)
            data.writeInt(0x45564e31)
            data.writeInt(1)
            data.writeBoolean(value)
            data.flush()
            atomic.finishWrite(stream)
            storedPageNumbers = value
            value
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
    }

    private fun backgroundFile(): File = file().let { File(it.parentFile, "${it.name}.background") }

    fun loadBackground(complete: (Result<BackgroundSettings>) -> Unit) =
        submit(complete) { readBackground() }

    private fun readBackground(): BackgroundSettings = storedBackground ?: run {
        val path = backgroundFile()
        try {
            AtomicFile(path).openRead().use(BackgroundSettingsCodec::read)
        } catch (error: FileNotFoundException) {
            if (path.exists()) throw error
            BackgroundSettings()
        }
    }.also { storedBackground = it }

    fun saveBackground(value: BackgroundSettings, complete: (Result<BackgroundSettings>) -> Unit) =
        submit(complete) {
            readBackground()
            val atomic = AtomicFile(backgroundFile())
            val stream = atomic.startWrite()
            try {
                BackgroundSettingsCodec.write(stream, value)
                atomic.finishWrite(stream)
                storedBackground = value
                value
            } catch (error: Exception) {
                atomic.failWrite(stream)
                throw error
            }
        }

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

    private fun <T> submit(complete: (Result<T>) -> Unit, task: () -> T) {
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
