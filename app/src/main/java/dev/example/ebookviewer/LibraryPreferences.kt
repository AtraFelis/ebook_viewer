package dev.example.ebookviewer

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.Collator
import java.util.Locale

enum class LibraryViewMode { GRID, LIST }
enum class DocumentSort { RECENT, NAME, MANUAL }
enum class BookshelfSort { CUSTOM, NAME }

data class LibraryPreferences(
    val view: LibraryViewMode = LibraryViewMode.GRID,
    val documents: DocumentSort = DocumentSort.RECENT,
    val shelves: BookshelfSort = BookshelfSort.CUSTOM
)

object LibraryPreferencesCodec {
    fun read(input: InputStream): LibraryPreferences {
        val data = DataInputStream(input)
        require(data.readInt() == 0x45564C50 && data.readInt() == 1)
        val result = LibraryPreferences(
            LibraryViewMode.valueOf(data.readUTF()),
            DocumentSort.valueOf(data.readUTF()),
            BookshelfSort.valueOf(data.readUTF())
        )
        require(data.read() == -1)
        return result
    }

    fun write(output: OutputStream, value: LibraryPreferences) {
        DataOutputStream(output).apply {
            writeInt(0x45564C50)
            writeInt(1)
            writeUTF(value.view.name)
            writeUTF(value.documents.name)
            writeUTF(value.shelves.name)
            flush()
        }
    }
}

/** Sorting is presentation only: neither the catalog nor its saved manual order is rewritten. */
internal fun LibraryCatalog.sortedRecords(
    filter: String?,
    sort: DocumentSort
): List<ReadingRecord> {
    val values = visible(filter)
    if (sort == DocumentSort.MANUAL) return values
    val collator = Collator.getInstance(Locale.KOREAN).apply { strength = Collator.SECONDARY }
    val name = Comparator<ReadingRecord> { a, b ->
        collator.compare(a.title, b.title).takeIf { it != 0 } ?: a.uri.compareTo(b.uri)
    }
    return values.sortedWith(
        if (sort == DocumentSort.NAME) {
            name
        } else {
            compareByDescending<ReadingRecord> { it.position != null }
                .thenByDescending { if (it.position == null) 0 else it.openedAt }
                .then(name)
        }
    )
}

internal fun LibraryCatalog.sortedShelves(sort: BookshelfSort): List<Bookshelf> {
    if (sort == BookshelfSort.CUSTOM) return shelves
    val collator = Collator.getInstance(Locale.KOREAN)
    return shelves.sortedWith { a, b ->
        collator.compare(a.name, b.name).takeIf { it != 0 } ?: a.id.compareTo(b.id)
    }
}
