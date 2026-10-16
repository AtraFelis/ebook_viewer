package dev.example.ebookviewer

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/** Page index and fractions are independent of screen pixels and document prefix lengths. */
data class ReadingPosition(val page: Int, val x: Double, val y: Double, val zoom: Double) {
    init {
        require(page >= 0 && x.isFinite() && y.isFinite() && y in 0.0..1.0)
        require(zoom.isFinite() && zoom in 0.1..8.0)
    }
}

data class ReadingRecord(
    val uri: String,
    val title: String,
    val openedAt: Long,
    val pageCount: Int = 0,
    val position: ReadingPosition? = null
) {
    init {
        require(uri.isNotBlank() && title.isNotBlank() && openedAt >= 0 && pageCount >= 0)
    }
}

/** Versioned, bounded metadata only. PDFs and page pixels never enter this file. */
object ReadingRecordsCodec {
    fun read(input: InputStream): List<ReadingRecord> {
        val data = DataInputStream(input)
        require(data.readInt() == MAGIC && data.readInt() == 1) { "Unsupported reading records" }
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
                }
            )
        }
        require(records.map { it.uri }.distinct().size == records.size && data.read() == -1)
        return records.sortedByDescending { it.openedAt }
    }

    fun write(output: OutputStream, records: List<ReadingRecord>) {
        require(records.size <= 10_000 && records.map { it.uri }.distinct().size == records.size)
        val data = DataOutputStream(output)
        data.writeInt(MAGIC)
        data.writeInt(1)
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
        }
        data.flush()
    }

    private const val MAGIC = 0x45565231
}
