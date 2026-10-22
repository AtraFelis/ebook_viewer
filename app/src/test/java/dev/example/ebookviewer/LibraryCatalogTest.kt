package dev.example.ebookviewer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCatalogTest {
    private val a = ReadingRecord("a", "A.pdf", 1, 30, ReadingPosition(5, 0.3, 0.6, 4.0))
    private val b = ReadingRecord("b", "B.pdf", 2)
    private val c = ReadingRecord("c", "C.pdf", 3)
    private val shelf = Bookshelf("s1", "내 책장")

    @Test
    fun roundTripRetainsShelfNamesOrderAssignmentsAndReadingAnchors() {
        val value = LibraryCatalog(listOf(c, b, a))
            .addShelf(shelf).addShelf(Bookshelf("s2", "빈 책장"))
            .moveToShelf(setOf("a", "b"), shelf.id)
            .moveBy("a", -1, null)
            .renameShelf("s1", "이름을 바꾼 책장")
        val output = ByteArrayOutputStream()
        ReadingRecordsCodec.writeCatalog(output, value)
        val restored = ReadingRecordsCodec.readCatalog(ByteArrayInputStream(output.toByteArray()))
        assertEquals(value, restored)
        assertEquals(listOf("c"), restored.visible("").map { it.uri })
        assertEquals(listOf("a", "b"), restored.visible("s1").map { it.uri })
        assertEquals(a.position, restored.records.single { it.uri == "a" }.position)
    }

    @Test
    fun repeatedBatchAddsDoNotReplaceExistingPositionsOrDuplicateUris() {
        val initial = LibraryCatalog(listOf(a)).addShelf(shelf)
        val updated = initial.addAll(listOf(a.copy(position = null), b, b, c), shelf.id)
        assertEquals(listOf("a", "b", "c"), updated.order)
        assertEquals(a, updated.records.single { it.uri == "a" })
        assertEquals(mapOf("b" to "s1", "c" to "s1"), updated.memberships)
        assertEquals(updated, updated.addAll(listOf(a, b, c), null))
    }

    @Test
    fun staleReadingSaveCannotUndoMovesAndRecencyDoesNotChangeManualOrder() {
        val initial = LibraryCatalog(listOf(a, b)).addShelf(shelf)
        val organized = initial.moveToShelf(setOf("a"), shelf.id).moveBy("a", 1, null)
        val saved = organized.put(
            a.copy(openedAt = 100, position = ReadingPosition(10, 0.4, 0.8, 3.0))
        )
        assertEquals(listOf("b", "a"), saved.order)
        assertEquals(mapOf("a" to "s1"), saved.memberships)
        assertEquals("a", saved.records.first().uri)
    }

    @Test
    fun relinkKeepsPlacementAndExistingDestinationWinsWithoutDuplicateOrder() {
        val initial = LibraryCatalog(listOf(a, b)).addShelf(shelf).moveToShelf(setOf("a"), shelf.id)
        val relinked = initial.put(a.copy(uri = "new"), "a")
        assertEquals(listOf("new", "b"), relinked.order)
        assertEquals(mapOf("new" to "s1"), relinked.memberships)
        val deduped = initial.put(b.copy(openedAt = 100), "a")
        assertEquals(listOf("b"), deduped.order)
        assertTrue(deduped.memberships.isEmpty())
    }

    @Test
    fun filteredReorderSwapsVisibleNeighborsAndEdgesAreNoOps() {
        val initial = LibraryCatalog(listOf(a, b, c)).addShelf(shelf)
            .moveToShelf(setOf("a", "c"), shelf.id)
        assertEquals(listOf("c", "b", "a"), initial.moveBy("a", 1, "s1").order)
        assertEquals(initial, initial.moveBy("a", -1, "s1"))
        assertEquals(initial, initial.moveBy("b", 1, "s1"))
        assertEquals(initial, initial.moveBy("missing", 1, null))
    }

    @Test
    fun bulkRemoveAndShelfRemovalKeepOtherRecordsAndAnchors() {
        val initial = LibraryCatalog(listOf(a, b, c)).addShelf(shelf)
            .moveToShelf(setOf("a", "b"), shelf.id)
        val removed = initial.remove(setOf("b", "c", "missing"))
        assertEquals(listOf(a), removed.records)
        assertEquals(listOf("a"), removed.order)
        val uncategorized = removed.removeShelf(shelf.id)
        assertEquals(listOf(a), uncategorized.visible(""))
        assertTrue(uncategorized.shelves.isEmpty())
        assertTrue(uncategorized.memberships.isEmpty())
    }

    @Test
    fun legacyVersionFourMigratesWithCropLockAndRecencyBasedInitialOrder() {
        val output = ByteArrayOutputStream()
        val cropped = a.copy(
            pageLayout = PageLayoutSettings(
                PageSizeMode.UNIFORM_WIDTH,
                12,
                true,
                PageCrop(10, 5, 15, 20)
            )
        )
        writeLegacyVersionFour(output, listOf(cropped, b))
        val migrated = ReadingRecordsCodec.readCatalog(ByteArrayInputStream(output.toByteArray()))
        assertEquals(listOf(b, cropped), migrated.records)
        assertEquals(listOf("b", "a"), migrated.order)
        assertTrue(migrated.shelves.isEmpty())
        assertEquals(a.position, migrated.records.last().position)
    }

    @Test
    fun invalidMembershipsDuplicateOrderAndTrailingDataAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            LibraryCatalog(listOf(a), memberships = mapOf("a" to "missing"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LibraryCatalog(listOf(a, b), order = listOf("a", "a"))
        }
        assertThrows(IllegalArgumentException::class.java) { Bookshelf("s", " ") }
        assertThrows(IllegalArgumentException::class.java) { Bookshelf("s", "a".repeat(101)) }
        val output = ByteArrayOutputStream()
        ReadingRecordsCodec.writeCatalog(output, LibraryCatalog(listOf(a)))
        assertThrows(IllegalArgumentException::class.java) {
            ReadingRecordsCodec.readCatalog(ByteArrayInputStream(output.toByteArray() + 0))
        }
    }
}

/** Fixed old on-disk layout, independent of the current writer; also used for byte corruption tests. */
internal fun writeLegacyVersionFour(output: ByteArrayOutputStream, records: List<ReadingRecord>) {
    DataOutputStream(output).apply {
        writeInt(0x45565231)
        writeInt(4)
        writeInt(records.size)
        records.forEach { record ->
            writeUTF(record.uri)
            writeUTF(record.title)
            writeLong(record.openedAt)
            writeInt(record.pageCount)
            writeBoolean(record.position != null)
            record.position?.let {
                writeInt(it.page)
                writeDouble(it.x)
                writeDouble(it.y)
                writeDouble(it.zoom)
            }
            writeUTF(record.pageLayout.mode.name)
            writeInt(record.pageLayout.gap)
            writeBoolean(record.pageLayout.horizontalPanLocked)
            writeByte(record.pageLayout.crop.left)
            writeByte(record.pageLayout.crop.top)
            writeByte(record.pageLayout.crop.right)
            writeByte(record.pageLayout.crop.bottom)
        }
    }
}
