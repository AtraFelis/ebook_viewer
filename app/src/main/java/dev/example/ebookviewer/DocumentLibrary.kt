package dev.example.ebookviewer

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import java.io.DataInputStream
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
    private val beforeVersionSix = AtomicFile(File(file.parentFile, "${file.name}.before-v6"))
    private var backupRequired = try {
        atomic.openRead().use {
            val data = DataInputStream(it)
            data.readInt()
            data.readInt() in 1..5
        }
    } catch (_: FileNotFoundException) {
        false
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

    fun addToShelves(uris: Set<String>, shelves: Set<String>) {
        write(catalog.addToShelves(uris, shelves))
    }

    fun transferToShelf(uris: Set<String>, source: String, target: String, keepSource: Boolean) {
        write(catalog.transferToShelf(uris, source, target, keepSource))
    }

    fun reorderShelves(ids: List<String>) {
        write(catalog.reorderShelves(ids))
    }

    fun removeShelves(ids: Set<String>) {
        write(catalog.removeShelves(ids))
    }

    fun removeFromShelf(uris: Set<String>, shelf: String) {
        write(catalog.removeFromShelf(uris, shelf))
    }

    fun moveShelfBy(id: String, direction: Int) {
        write(catalog.moveShelfBy(id, direction))
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
        if (backupRequired) {
            // Preserve the pre-migration bytes inside app-private storage; never export them.
            if (!beforeVersionSix.baseFile.exists()) {
                val backup = beforeVersionSix.startWrite()
                try {
                    atomic.openRead().use { it.copyTo(backup) }
                    beforeVersionSix.finishWrite(backup)
                } catch (error: Exception) {
                    beforeVersionSix.failWrite(backup)
                    throw error
                }
            }
            backupRequired = false
        }
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
    internal val libraryPreferences: LibraryPreferencesRepository by lazy {
        LibraryPreferencesRepository { File(filesDir, "library-preferences.bin") }
    }
}
