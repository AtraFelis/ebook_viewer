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
    private var catalog = try {
        atomic.openRead().use(ReadingRecordsCodec::readCatalog)
    } catch (error: FileNotFoundException) {
        // A missing file is an empty library; unreadable existing data must not be overwritten.
        if (file.exists()) throw error
        LibraryCatalog()
    }

    fun list(): List<ReadingRecord> = catalog.records.toList()

    fun snapshot(): LibraryCatalog = catalog

    fun put(record: ReadingRecord, replacedUri: String? = null) {
        write(catalog.put(record, replacedUri))
    }

    fun addAll(records: List<ReadingRecord>, shelf: String?) {
        // A destination removed while the system picker was open falls back to uncategorized.
        write(catalog.addAll(records, shelf?.takeIf { id -> catalog.shelves.any { it.id == id } }))
    }

    fun remove(uri: String) = removeAll(setOf(uri))

    fun removeAll(uris: Set<String>) {
        write(catalog.remove(uris))
    }

    fun moveToShelf(uris: Set<String>, shelf: String?) {
        write(catalog.moveToShelf(uris, shelf))
    }

    fun moveBy(uri: String, direction: Int, filter: String?) {
        write(catalog.moveBy(uri, direction, filter))
    }

    fun addShelf(shelf: Bookshelf) {
        write(catalog.addShelf(shelf))
    }

    fun renameShelf(id: String, name: String) {
        write(catalog.renameShelf(id, name))
    }

    fun removeShelf(id: String) {
        write(catalog.removeShelf(id))
    }

    fun updatePosition(uri: String, position: ReadingPosition) {
        val current = catalog.records.firstOrNull { it.uri == uri } ?: return
        // Merge on the IO queue after pending settings succeed or roll back; never resurrect deletion.
        put(current.copy(position = position))
    }

    private fun write(updated: LibraryCatalog) {
        val stream = atomic.startWrite()
        try {
            ReadingRecordsCodec.writeCatalog(stream, updated)
            atomic.finishWrite(stream)
            catalog = updated
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
