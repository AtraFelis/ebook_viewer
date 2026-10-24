package dev.example.ebookviewer

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/** Page index and fractions are independent of screen pixels and document prefix lengths. */
data class ReadingPosition(val page: Int, val x: Double, val y: Double, val zoom: Double) {
    init {
        require(page >= 0 && x.isFinite() && y.isFinite() && y in 0.0..1.0)
        require(zoom.isFinite() && zoom in ZoomLimits.MIN_SAVED..ZoomLimits.MAX)
    }
}

data class ReadingRecord(
    val uri: String,
    val title: String,
    val openedAt: Long,
    val pageCount: Int = 0,
    val position: ReadingPosition? = null,
    val pageLayout: PageLayoutSettings = PageLayoutSettings()
) {
    init {
        require(uri.isNotBlank() && title.isNotBlank() && openedAt >= 0 && pageCount >= 0)
    }
}

/** Versioned, bounded metadata only. PDFs and page pixels never enter this file. */
object ReadingRecordsCodec {
    fun read(input: InputStream): List<ReadingRecord> = readCatalog(input).records

    fun readCatalog(input: InputStream): LibraryCatalog {
        val data = DataInputStream(input)
        require(data.readInt() == MAGIC) { "Unsupported reading records" }
        val version = data.readInt()
        require(version in 1..6) { "Unsupported reading records" }
        val count = data.readInt()
        require(count in 0..10_000)
        val records = List(count) {
            ReadingRecord(
                data.readUTF(),
                data.readUTF(),
                data.readLong(),
                data.readInt(),
                if (data.readBoolean()) {
                    ReadingPosition(
                        data.readInt(),
                        data.readDouble(),
                        data.readDouble(),
                        data.readDouble()
                    )
                } else {
                    null
                },
                if (version >= 2) {
                    PageLayoutSettings(
                        PageSizeMode.valueOf(data.readUTF()),
                        data.readInt(),
                        if (version >= 3) {
                            data.readUnsignedByte().also { require(it in 0..1) } == 1
                        } else {
                            false
                        },
                        if (version >= 4) {
                            PageCrop(
                                data.readUnsignedByte(),
                                data.readUnsignedByte(),
                                data.readUnsignedByte(),
                                data.readUnsignedByte()
                            )
                        } else {
                            PageCrop()
                        }
                    )
                } else {
                    PageLayoutSettings()
                }
            )
        }
        val catalog = if (version >= 5) {
            val shelfCount = data.readInt().also { require(it in 0..1_000) }
            val shelves = List(shelfCount) { Bookshelf(data.readUTF(), data.readUTF()) }
            val orderCount = data.readInt().also { require(it == count) }
            val order = List(orderCount) { data.readUTF() }
            val memberCount = data.readInt().also { require(it in 0..count) }
            var total = 0
            val entries = List(memberCount) {
                val uri = data.readUTF()
                val size = if (version == 5) 1 else data.readInt()
                require(size in 1..shelfCount)
                total += size
                require(total <= 100_000)
                val ids = List(size) { data.readUTF() }
                require(ids.distinct().size == size)
                uri to ids.toSet()
            }
            require(entries.map { it.first }.distinct().size == entries.size)
            LibraryCatalog(
                records.sortedByDescending {
                    it.openedAt
                },
                shelves,
                order,
                entries.toMap()
            )
        } else {
            LibraryCatalog(records.sortedByDescending { it.openedAt })
        }
        require(data.read() == -1)
        return catalog
    }

    fun write(output: OutputStream, records: List<ReadingRecord>) {
        writeCatalog(output, LibraryCatalog(records))
    }

    fun writeCatalog(output: OutputStream, catalog: LibraryCatalog) {
        val records = catalog.records
        val data = DataOutputStream(output)
        data.writeInt(MAGIC)
        data.writeInt(6)
        data.writeInt(records.size)
        for (record in records) {
            data.writeUTF(record.uri)
            data.writeUTF(record.title)
            data.writeLong(record.openedAt)
            data.writeInt(record.pageCount)
            data.writeBoolean(record.position != null)
            record.position?.let {
                data.writeInt(it.page)
                data.writeDouble(it.x)
                data.writeDouble(it.y)
                data.writeDouble(it.zoom)
            }
            data.writeUTF(record.pageLayout.mode.name)
            data.writeInt(record.pageLayout.gap)
            data.writeByte(if (record.pageLayout.horizontalPanLocked) 1 else 0)
            record.pageLayout.crop.let {
                data.writeByte(it.left)
                data.writeByte(it.top)
                data.writeByte(it.right)
                data.writeByte(it.bottom)
            }
        }
        data.writeInt(catalog.shelves.size)
        catalog.shelves.forEach {
            data.writeUTF(it.id)
            data.writeUTF(it.name)
        }
        data.writeInt(catalog.order.size)
        catalog.order.forEach(data::writeUTF)
        data.writeInt(catalog.memberships.size)
        catalog.memberships.forEach { (uri, shelves) ->
            data.writeUTF(uri)
            data.writeInt(shelves.size)
            shelves.sorted().forEach(data::writeUTF)
        }
        data.flush()
    }

    private const val MAGIC = 0x45565231
}
