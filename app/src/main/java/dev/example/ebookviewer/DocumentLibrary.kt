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
    internal val copies = managedPdfFiles(File(file.parentFile, "documents"))
    private val deleted = mutableSetOf<String>()
    private val atomic = AtomicFile(file)
    private var catalog = try {
        atomic.openRead().use(ReadingRecordsCodec::readCatalog)
    } catch (error: FileNotFoundException) {
        // A missing file is an empty library; unreadable existing data must not be overwritten.
        if (file.exists()) throw error
        LibraryCatalog()
    }
    private val beforeVersionSix = AtomicFile(File(file.parentFile, "${file.name}.before-v6"))
    private val beforeVersionSeven = AtomicFile(File(file.parentFile, "${file.name}.before-v7"))
    private val beforeVersionEight = AtomicFile(File(file.parentFile, "${file.name}.before-v8"))
    private var versionEightBackupRequired = try {
        atomic.openRead().use {
            val data = DataInputStream(it)
            data.readInt()
            data.readInt() in 1..7
        }
    } catch (_: FileNotFoundException) {
        false
    }
    private var versionSevenBackupRequired = try {
        atomic.openRead().use {
            val data = DataInputStream(it)
            data.readInt()
            data.readInt() in 1..6
        }
    } catch (_: FileNotFoundException) {
        false
    }
    private var backupRequired = try {
        atomic.openRead().use {
            val data = DataInputStream(it)
            data.readInt()
            data.readInt() in 1..5
        }
    } catch (_: FileNotFoundException) {
        false
    }

    init {
        copies.recover(catalog.records.mapNotNull { it.managedCopy }.toSet())
    }

    fun list(): List<ReadingRecord> = catalog.records.toList()

    fun snapshot(): LibraryCatalog = catalog

    fun hideRecent(uris: Set<String>) {
        write(catalog.hideRecent(uris))
    }

    fun revealRecent(uris: Set<String>) {
        if (catalog.hiddenRecents.any { it in uris }) write(catalog.revealRecent(uris))
    }

    fun put(record: ReadingRecord, replacedUri: String? = null, replaceCopy: Boolean = false) {
        val previous = catalog.records.filter { it.uri == replacedUri || it.uri == record.uri }
            .mapNotNull { it.managedCopy }
        val current = catalog.records.firstOrNull { it.uri == record.uri }
        val token = current?.managedCopy?.takeIf { !replaceCopy && copies.file(it).exists() }
            ?: record.managedCopy
        write(catalog.put(record.copy(managedCopy = token), replacedUri))
        ((previous + listOfNotNull(record.managedCopy)).distinct() - listOfNotNull(token))
            .forEach(::removeUnreferencedCopy)
    }

    internal fun allowReading(uri: String) {
        deleted.remove(uri)
    }

    internal fun saveReading(record: ReadingRecord, replacedUri: String?) {
        if (record.uri !in deleted && (replacedUri == null || replacedUri !in deleted)) {
            put(record, replacedUri, replaceCopy = replacedUri == record.uri)
        }
    }

    internal fun removeUnreferencedCopy(token: String) {
        if (catalog.records.none { it.managedCopy == token }) copies.remove(token)
    }

    fun addAll(records: List<ReadingRecord>, shelf: String?) {
        // A destination removed while the system picker was open falls back to uncategorized.
        var updated = catalog.addAll(
            records,
            shelf?.takeIf { id -> catalog.shelves.any { it.id == id } }
        )
        records.forEach { record ->
            val current = updated.records.first { it.uri == record.uri }
            if (current.managedCopy == null && record.managedCopy != null) {
                updated = updated.put(current.copy(managedCopy = record.managedCopy))
            }
        }
        write(updated)
        deleted.removeAll(records.map { it.uri }.toSet())
    }

    fun remove(uri: String) = removeAll(setOf(uri))

    fun removeAll(uris: Set<String>) {
        val tokens = catalog.records.filter { it.uri in uris }.mapNotNull { it.managedCopy }.toSet()
        write(catalog.remove(uris))
        deleted.addAll(uris)
        tokens.forEach(::removeUnreferencedCopy)
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
        if (versionEightBackupRequired) {
            if (!beforeVersionEight.baseFile.exists()) {
                val backup = beforeVersionEight.startWrite()
                try {
                    atomic.openRead().use { it.copyTo(backup) }
                    beforeVersionEight.finishWrite(backup)
                } catch (error: Exception) {
                    beforeVersionEight.failWrite(backup)
                    throw error
                }
            }
            versionEightBackupRequired = false
        }
        if (versionSevenBackupRequired) {
            if (!beforeVersionSeven.baseFile.exists()) {
                val backup = beforeVersionSeven.startWrite()
                try {
                    atomic.openRead().use { it.copyTo(backup) }
                    beforeVersionSeven.finishWrite(backup)
                } catch (error: Exception) {
                    beforeVersionSeven.failWrite(backup)
                    throw error
                }
            }
            versionSevenBackupRequired = false
        }
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
    internal val managedPdfs by lazy { managedPdfFiles(File(filesDir, "documents")) }
    internal val cancellation = Executors.newSingleThreadExecutor()
    internal val readingHint = ReadingHintState()
    internal val bookCovers: BookCoverStore by lazy { BookCoverStore(this) }
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
