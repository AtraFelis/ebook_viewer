package dev.example.ebookviewer

import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors

/** One page bookmark per document; y is the normalized page-internal reading anchor. */
data class Bookmark(val uri: String, val page: Int, val y: Double) {
    init {
        require(uri.isNotBlank() && page >= 0 && y.isFinite() && y in 0.0..1.0)
    }
}

object BookmarksCodec {
    fun read(input: InputStream): List<Bookmark> {
        val data = DataInputStream(input)
        require(data.readInt() == 0x45564d31 && data.readInt() == 1)
        val count = data.readInt()
        require(count in 0..10_000)
        val entries = List(count) { Bookmark(data.readUTF(), data.readInt(), data.readDouble()) }
        val unique = entries.map { it.uri to it.page }.distinct().size == entries.size
        require(unique && data.read() == -1)
        return entries
    }

    fun write(output: OutputStream, entries: List<Bookmark>) {
        val unique = entries.map { it.uri to it.page }.distinct().size == entries.size
        require(entries.size <= 10_000 && unique)
        val data = DataOutputStream(output)
        data.writeInt(0x45564d31)
        data.writeInt(1)
        data.writeInt(entries.size)
        for (entry in entries) {
            data.writeUTF(entry.uri)
            data.writeInt(entry.page)
            data.writeDouble(entry.y)
        }
        data.flush()
    }
}

/** IO-thread owned; a failed/unsupported file never becomes an empty replacement. */
class BookmarkStore(file: File) {
    private val atomic = AtomicFile(file)
    private var entries = try {
        atomic.openRead().use(BookmarksCodec::read)
    } catch (error: FileNotFoundException) {
        if (file.exists()) throw error
        emptyList()
    }

    fun list(uri: String): List<Bookmark> = entries.filter { it.uri == uri }.sortedBy { it.page }

    fun put(bookmark: Bookmark) = write(
        entries.filter { it.uri != bookmark.uri || it.page != bookmark.page } + bookmark
    )

    fun remove(uri: String, page: Int) = write(entries.filter { it.uri != uri || it.page != page })

    fun relink(previous: String, next: String) {
        if (previous == next) return
        // Existing bookmarks at the new URI win; preserve all other old page anchors.
        val migrated = entries.filter { it.uri == previous }.map { it.copy(uri = next) }
        if (migrated.isEmpty()) return
        write((entries.filter { it.uri != previous } + migrated).distinctBy { it.uri to it.page })
    }

    private fun write(updated: List<Bookmark>) {
        val stream = atomic.startWrite()
        try {
            BookmarksCodec.write(stream, updated)
            atomic.finishWrite(stream)
            entries = updated
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
    }
}

class BookmarkRepository(private val file: () -> File) {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var store: BookmarkStore? = null

    fun <T> execute(task: (BookmarkStore) -> T, complete: (Result<T>) -> Unit) {
        executor.execute {
            val result = try {
                val current = store ?: BookmarkStore(file()).also { store = it }
                Result.success(task(current))
            } catch (error: Exception) {
                Result.failure(error)
            }
            main.post { complete(result) }
        }
    }
}
