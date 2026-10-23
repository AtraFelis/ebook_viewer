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
    fun legacyVersionOneMigratesAndCurrentVersionKeepsIndependentLayouts() {
        val legacy = ByteArrayOutputStream()
        DataOutputStream(legacy).apply {
            writeInt(0x45565231)
            writeInt(1)
            writeInt(1)
            writeUTF("legacy")
            writeUTF("Legacy")
            writeLong(1)
            writeInt(100)
            writeBoolean(true)
            writeInt(8)
            writeDouble(0.4)
            writeDouble(0.7)
            writeDouble(2.0)
        }
        val migrated = ReadingRecordsCodec.read(ByteArrayInputStream(legacy.toByteArray())).single()
        assertEquals(PageLayoutSettings(), migrated.pageLayout)
        assertEquals(ReadingPosition(8, 0.4, 0.7, 2.0), migrated.position)
        val records = listOf(
            migrated,
            migrated.copy(
                uri = "new",
                pageLayout = PageLayoutSettings(PageSizeMode.UNIFORM_WIDTH, 24, true)
            )
        )
        val output = ByteArrayOutputStream()
        ReadingRecordsCodec.write(output, records)
        assertEquals(records, ReadingRecordsCodec.read(ByteArrayInputStream(output.toByteArray())))
        val invalid = output.toByteArray().also { it[it.lastIndex] = 73 }
        assertThrows(IllegalArgumentException::class.java) {
            ReadingRecordsCodec.read(ByteArrayInputStream(invalid))
        }
    }

    @Test
    fun legacyVersionTwoRetainsLayoutAndDefaultsToUnlocked() {
        val legacy = ByteArrayOutputStream()
        DataOutputStream(legacy).apply {
            writeInt(0x45565231)
            writeInt(2)
            writeInt(1)
            writeUTF("content://fixture/v2")
            writeUTF("Version two")
            writeLong(10)
            writeInt(20)
            writeBoolean(true)
            writeInt(4)
            writeDouble(0.3)
            writeDouble(0.6)
            writeDouble(3.0)
            writeUTF(PageSizeMode.UNIFORM_WIDTH.name)
            writeInt(24)
        }
        val records = ReadingRecordsCodec.read(ByteArrayInputStream(legacy.toByteArray()))
        assertEquals(
            PageLayoutSettings(PageSizeMode.UNIFORM_WIDTH, 24, false),
            records.single().pageLayout
        )
        assertEquals(ReadingPosition(4, 0.3, 0.6, 3.0), records.single().position)
        val output = ByteArrayOutputStream()
        ReadingRecordsCodec.write(output, records)
        assertEquals(records, ReadingRecordsCodec.read(ByteArrayInputStream(output.toByteArray())))
    }

    @Test
    fun invalidLockFlagAndGapAreRejected() {
        val output = ByteArrayOutputStream()
        writeLegacyVersionFour(output, listOf(ReadingRecord("content://fixture/lock", "Lock", 1)))
        for (flag in listOf(2, 255)) {
            val invalid = output.toByteArray().also { it[it.lastIndex - 4] = flag.toByte() }
            assertThrows(IllegalArgumentException::class.java) {
                ReadingRecordsCodec.read(ByteArrayInputStream(invalid))
            }
        }
        val invalidGap = output.toByteArray().also { it[it.lastIndex - 5] = 73 }
        assertThrows(IllegalArgumentException::class.java) {
            ReadingRecordsCodec.read(ByteArrayInputStream(invalidGap))
        }
    }

    @Test
    fun versionThreeKeepsLockAndOriginalAnchorWhileDefaultingToNoCrop() {
        val record = ReadingRecord(
            "content://fixture/v3",
            "V3",
            1,
            20,
            ReadingPosition(10, 0.3, 0.4, 4.0),
            PageLayoutSettings(PageSizeMode.UNIFORM_WIDTH, 24, true)
        )
        val output = ByteArrayOutputStream()
        writeLegacyVersionFour(output, listOf(record))
        val legacy = output.toByteArray().dropLast(4).toByteArray().also { it[7] = 3 }
        assertEquals(listOf(record), ReadingRecordsCodec.read(ByteArrayInputStream(legacy)))
    }

    @Test
    fun cropRoundTripsPerDocumentAndInvalidOrTrailingDataIsRejected() {
        val record = ReadingRecord(
            "content://fixture/crop",
            "Crop",
            2,
            20,
            ReadingPosition(10, 0.3, 0.4, 4.0),
            PageLayoutSettings(PageSizeMode.UNIFORM_WIDTH, 24, true, PageCrop(3, 5, 7, 9))
        )
        val records = listOf(record, record.copy(uri = "other", pageLayout = PageLayoutSettings()))
        val output = ByteArrayOutputStream()
        writeLegacyVersionFour(output, records)
        assertEquals(records, ReadingRecordsCodec.read(ByteArrayInputStream(output.toByteArray())))
        for (offset in 0..3) {
            val invalid = output.toByteArray().also { it[it.lastIndex - offset] = 46 }
            assertThrows(IllegalArgumentException::class.java) {
                ReadingRecordsCodec.read(ByteArrayInputStream(invalid))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReadingRecordsCodec.read(ByteArrayInputStream(output.toByteArray() + byteArrayOf(0)))
        }
    }

    @Test
    fun expandedZoomAndLegacySmallZoomRoundTrip() {
        val records = listOf(0.1, 8.0, 32.0).mapIndexed { index, zoom ->
            ReadingRecord(
                "content://fixture/$index",
                "Zoom $index",
                10,
                100,
                ReadingPosition(42, 0.71, 0.23, zoom)
            )
        }
        val output = ByteArrayOutputStream()
        ReadingRecordsCodec.write(output, records)
        assertEquals(records, ReadingRecordsCodec.read(ByteArrayInputStream(output.toByteArray())))
        assertThrows(IllegalArgumentException::class.java) { ReadingPosition(0, 0.5, 0.5, 32.1) }
    }

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
