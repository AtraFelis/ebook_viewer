package dev.example.ebookviewer

import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.Executors

/** One application-owned queue prevents a rotating Activity from racing another settings write. */
internal class LibraryPreferencesRepository(private val file: () -> File) {
    private val queue = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun load(done: (Result<LibraryPreferences>) -> Unit) = execute(done) {
        val source = file()
        try {
            AtomicFile(source).openRead().use(LibraryPreferencesCodec::read)
        } catch (error: FileNotFoundException) {
            if (source.exists()) throw error
            LibraryPreferences()
        }
    }

    fun save(value: LibraryPreferences, done: (Result<LibraryPreferences>) -> Unit) =
        execute(done) {
            val atomic = AtomicFile(file())
            val output = atomic.startWrite()
            try {
                LibraryPreferencesCodec.write(output, value)
                atomic.finishWrite(output)
                value
            } catch (error: Exception) {
                atomic.failWrite(output)
                throw error
            }
        }

    private fun execute(
        done: (Result<LibraryPreferences>) -> Unit,
        action: () -> LibraryPreferences
    ) {
        queue.execute {
            val result = runCatching(action)
            main.post { done(result) }
        }
    }
}
