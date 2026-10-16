package dev.example.ebookviewer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.EOFException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReadingRecordsTest {
    @Test
    fun independentDocumentsRoundTripWithNormalizedPositionsAndRecentOrdering() {
        val first =
            ReadingRecord(
                "content://fixture/a",
                "가 책.pdf",
                10,
                100,
                ReadingPosition(42, 0.71, 0.23, 2.4)
            )
        val second =
            ReadingRecord(
                "content://fixture/b",
                "나 책.pdf",
                20,
                3,
                ReadingPosition(1, 0.3, 0.9, 1.7)
            )
        val output = ByteArrayOutputStream()
        ReadingRecordsCodec.write(output, listOf(first, second))
        assertEquals(
            listOf(second, first),
            ReadingRecordsCodec.read(ByteArrayInputStream(output.toByteArray()))
        )
    }

    @Test
    fun interruptedOrUnknownDataFailsInsteadOfBecomingAnEmptyLibrary() {
        val output = ByteArrayOutputStream()
        ReadingRecordsCodec.write(output, listOf(ReadingRecord("content://fixture/a", "A", 10)))
        assertThrows(EOFException::class.java) {
            ReadingRecordsCodec.read(
                ByteArrayInputStream(output.toByteArray().dropLast(1).toByteArray())
            )
        }
        val unknown = ByteArrayOutputStream()
        DataOutputStream(unknown).apply {
            writeInt(0x45565231)
            writeInt(99)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReadingRecordsCodec.read(ByteArrayInputStream(unknown.toByteArray()))
        }
    }

    @Test
    fun duplicateDocumentIdentifiersAndInvalidCoordinatesAreRejected() {
        val record = ReadingRecord("content://fixture/a", "A", 10)
        assertThrows(IllegalArgumentException::class.java) {
            ReadingRecordsCodec.write(ByteArrayOutputStream(), listOf(record, record))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReadingPosition(0, Double.NaN, 0.5, 1.0)
        }
        assertThrows(IllegalArgumentException::class.java) { ReadingPosition(-1, 0.5, 0.5, 1.0) }
        assertThrows(IllegalArgumentException::class.java) { ReadingPosition(0, 0.5, 0.5, 0.0) }
    }
}
