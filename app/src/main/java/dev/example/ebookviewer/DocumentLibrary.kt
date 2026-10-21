package dev.example.ebookviewer

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.Executors

/** All methods belong to the repository's single IO thread, including construction. */
class DocumentLibrary(file: File) {
    private val atomic = AtomicFile(file)
    private var records = try {
        atomic.openRead().use(ReadingRecordsCodec::read)
    } catch (error: FileNotFoundException) {
        // A missing file is an empty library; unreadable existing data must not be overwritten.
        if (file.exists()) throw error
        emptyList()
    }

    fun list(): List<ReadingRecord> = records.toList()

    fun put(record: ReadingRecord, replacedUri: String? = null) {
        val updated = (records.filter { it.uri != record.uri && it.uri != replacedUri } + record)
            .sortedByDescending { it.openedAt }
        write(updated)
    }

    fun remove(uri: String) {
        write(records.filter { it.uri != uri })
    }

    private fun write(updated: List<ReadingRecord>) {
        val stream = atomic.startWrite()
        try {
            ReadingRecordsCodec.write(stream, updated)
            atomic.finishWrite(stream)
            records = updated
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
    }
}

/** Application ownership serializes IO even while old/new Activities overlap during rotation. */
class LibraryRepository(private val file: () -> File) {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var library: DocumentLibrary? = null

    fun <T> execute(task: (DocumentLibrary) -> T, complete: (Result<T>) -> Unit) {
        executor.execute {
            val result = try {
                val current = library ?: DocumentLibrary(file()).also { library = it }
                Result.success(task(current))
            } catch (error: Exception) {
                Result.failure(error)
            }
            main.post { complete(result) }
        }
    }
}

class ReaderApplication : Application() {
    internal val readingHint = ReadingHintState()
    val bookmarks: BookmarkRepository by lazy {
        BookmarkRepository { File(filesDir, "bookmarks.bin") }
    }
    val settings: ReaderSettingsRepository by lazy {
        ReaderSettingsRepository { File(filesDir, "reader-settings.bin") }
    }
    val library: LibraryRepository by lazy {
        LibraryRepository { File(filesDir, "reading-records.bin") }
    }
}
