package dev.example.ebookviewer

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentHistoryTest {
    private val token = "01234567-89ab-4cde-8fab-0123456789ab"
    private val a = ReadingRecord(
        "a",
        "A",
        1,
        100,
        ReadingPosition(3, 0.5, 0.2, 2.0),
        managedCopy = token
    )
    private val b = a.copy(uri = "b", title = "B", openedAt = 2)

    @Test
    fun forgettingARecentKeepsRegistrationCopiesPositionsAndShelvesAndFillsTheFifthSlot() {
        val records = (1..7).map { a.copy(uri = "read:$it", openedAt = it.toLong()) }
        val original = LibraryCatalog(records).addShelf(Bookshelf("s", "Shelf"))
            .addToShelves(setOf("read:7"), setOf("s"))
        val hidden = original.hideRecent(setOf("read:7", "unknown"))
        assertEquals((6 downTo 2).map { "read:$it" }, hidden.recentlyRead().map { it.uri })
        assertEquals(original.records, hidden.records)
        assertEquals(original.order, hidden.order)
        assertEquals(original.memberships, hidden.memberships)
        assertEquals(original.visible("s"), hidden.visible("s"))
        assertEquals(setOf("read:7"), hidden.hiddenRecents)
    }

    @Test
    fun latePositionSavesKeepTheRecentHiddenAndAnExplicitReadRevealsIt() {
        val hidden = LibraryCatalog(listOf(b, a)).hideRecent(setOf("a"))
        val saved = hidden.put(a.copy(position = ReadingPosition(8, 0.4, 0.6, 3.0)))
        assertEquals(listOf("b"), saved.recentlyRead().map { it.uri })
        assertEquals(8, saved.records.single { it.uri == "a" }.position?.page)
        val opened = saved.revealRecent(setOf("a")).put(a.copy(openedAt = 50))
        assertEquals(listOf("a", "b"), opened.recentlyRead().map { it.uri })
        assertEquals(token, opened.records.single { it.uri == "a" }.managedCopy)
    }

    @Test
    fun relinkAndActualRegistrationDeletionKeepHiddenKeysConsistent() {
        val hidden = LibraryCatalog(listOf(b, a)).hideRecent(setOf("a"))
        val relinked = hidden.put(a.copy(uri = "new"), "a")
        assertEquals(setOf("new"), relinked.hiddenRecents)
        assertFalse(relinked.recentlyRead().any { it.uri == "new" })
        val existing = hidden.put(a.copy(uri = "b"), "a")
        assertTrue(existing.hiddenRecents.isEmpty())
        assertTrue(relinked.remove(setOf("new")).hiddenRecents.isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            hidden.copy(hiddenRecents = setOf("unknown"))
        }
    }

    @Test
    fun codecKeepsHiddenRecentsAndRejectsDuplicateUnknownAndUnboundedKeys() {
        val original = LibraryCatalog(listOf(b, a)).hideRecent(setOf("a"))
        val output = ByteArrayOutputStream().also { ReadingRecordsCodec.writeCatalog(it, original) }
        assertEquals(original, ReadingRecordsCodec.readCatalog(output.toByteArray().inputStream()))
        val base = ByteArrayOutputStream().also {
            ReadingRecordsCodec.writeCatalog(it, original.revealRecent(setOf("a")))
        }.toByteArray().let { it.copyOf(it.size - 4) }
        for (keys in listOf(listOf("a", "a"), listOf("unknown"), listOf("a", "b", "extra"))) {
            val malformed = ByteArrayOutputStream().apply { write(base) }
            DataOutputStream(malformed).apply {
                writeInt(keys.size)
                keys.forEach(::writeUTF)
            }
            assertThrows(IllegalArgumentException::class.java) {
                ReadingRecordsCodec.readCatalog(malformed.toByteArray().inputStream())
            }
        }
    }

    @Test
    fun fixedVersionSevenKeepsItsCopyAnchorAndShelfAndDefaultsToVisible() {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).apply {
            writeInt(0x45565231)
            writeInt(7)
            writeInt(1)
            writeUTF("a")
            writeUTF("A")
            writeLong(1)
            writeInt(100)
            writeBoolean(true)
            writeInt(3)
            writeDouble(0.5)
            writeDouble(0.2)
            writeDouble(2.0)
            writeUTF(PageSizeMode.ORIGINAL.name)
            writeInt(0)
            writeByte(0)
            repeat(4) { writeByte(0) }
            writeBoolean(true)
            writeUTF(token)
            writeInt(1)
            writeUTF("s")
            writeUTF("Shelf")
            writeInt(1)
            writeUTF("a")
            writeInt(1)
            writeUTF("a")
            writeInt(1)
            writeUTF("s")
        }
        val read = ReadingRecordsCodec.readCatalog(output.toByteArray().inputStream())
        assertEquals(a, read.records.single())
        assertEquals(setOf("s"), read.memberships["a"])
        assertEquals(listOf(a), read.recentlyRead())
        assertTrue(read.hiddenRecents.isEmpty())
    }
}
