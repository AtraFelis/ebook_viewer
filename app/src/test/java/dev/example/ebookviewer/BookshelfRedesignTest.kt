package dev.example.ebookviewer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfRedesignTest {
    private val a = ReadingRecord("a", "가이드.pdf", 10, 20, ReadingPosition(3, 0.2, 0.6, 4.0))
    private val b = ReadingRecord("b", "노트.pdf", 100)
    private fun catalog() = LibraryCatalog(listOf(a, b))
        .addShelf(Bookshelf("study", "학습"))
        .addShelf(Bookshelf("reference", "자료"))

    @Test
    fun transferOnlyRemovesSourceAndKeepsOtherMembershipsAndReadingAnchor() {
        val original = catalog().addShelf(Bookshelf("third", "별도"))
            .addToShelves(setOf("a"), setOf("study", "third"))
        val moved = original.transferToShelf(setOf("a", "b"), "study", "reference", false)
        assertEquals(setOf("reference", "third"), moved.memberships["a"])
        assertEquals(null, moved.memberships["b"])
        assertEquals(original.records, moved.records)
        assertEquals(original.order, moved.order)
        assertEquals(
            setOf("study", "third", "reference"),
            original.transferToShelf(setOf("a"), "study", "reference", true).memberships["a"]
        )
        assertEquals(moved, moved.transferToShelf(setOf("a"), "study", "reference", false))
        assertEquals(
            setOf("reference"),
            original.transferToShelf(setOf("b"), "", "reference", false).memberships["b"]
        )
        assertThrows(IllegalArgumentException::class.java) {
            original.transferToShelf(setOf("a"), "study", "study", true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            original.transferToShelf(setOf("a"), "study", "missing", true)
        }
    }

    @Test
    fun completeShelfOrderIsValidatedAndBatchDeletionPreservesDocumentsAndOtherRelations() {
        val original = catalog().addShelf(Bookshelf("third", "별도"))
            .addToShelves(setOf("a"), setOf("study", "reference", "third"))
        val ordered = original.reorderShelves(listOf("third", "study", "reference"))
        assertEquals(listOf("third", "study", "reference"), ordered.shelves.map { it.id })
        assertEquals(original.memberships, ordered.memberships)
        assertThrows(IllegalArgumentException::class.java) {
            original.reorderShelves(listOf("study", "study", "third"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            original.reorderShelves(listOf("study", "reference"))
        }
        val removed = ordered.removeShelves(setOf("study", "third"))
        assertEquals(setOf("reference"), removed.memberships["a"])
        assertEquals(original.records, removed.records)
        assertEquals(original.order, removed.order)
    }

    @Test
    fun multipleShelvesAreDeduplicatedAndRemovalKeepsOtherMembershipAndAnchor() {
        val shared = catalog().addToShelves(setOf("a"), setOf("study", "reference"))
        assertEquals(shared, shared.addToShelves(setOf("a"), setOf("study")))
        assertEquals(1, shared.visible("study").size)
        assertEquals(1, shared.visible("reference").size)
        assertEquals(listOf(b), shared.visible(""))
        val removed = shared.removeFromShelf(setOf("a"), "study")
        assertEquals(setOf("reference"), removed.memberships["a"])
        assertEquals(a.position, removed.records.first().position)
        assertEquals(listOf(a), removed.removeShelf("study").visible("reference"))
        assertEquals(2, removed.removeShelf("reference").visible("").size)
    }

    @Test
    fun stalePositionSaveAndRelinkKeepAllMembershipsAndShelfOrder() {
        val shared = catalog().addToShelves(setOf("a"), setOf("study", "reference"))
            .moveShelfBy("study", 1)
        val saved = shared.put(a.copy(openedAt = 200))
        assertEquals(shared.memberships, saved.memberships)
        assertEquals(shared.shelves, saved.shelves)
        assertEquals(
            shared.memberships["a"],
            saved.put(a.copy(uri = "new"), "a").memberships["new"]
        )
    }

    @Test
    fun versionSixRoundTripPreservesEveryMembershipAndManualOrder() {
        val value = catalog().addToShelves(setOf("a"), setOf("study", "reference"))
            .moveBy("a", 1, null).moveShelfBy("study", 1)
        val output = ByteArrayOutputStream()
        ReadingRecordsCodec.writeCatalog(output, value)
        assertEquals(
            value.copy(records = value.records.sortedByDescending { it.openedAt }),
            ReadingRecordsCodec.readCatalog(ByteArrayInputStream(output.toByteArray()))
        )
    }

    @Test
    fun fixedVersionFiveMigratesSingleMembershipWithoutLosingCropOrPosition() {
        val output = ByteArrayOutputStream()
        writeLegacyVersionFour(output, listOf(a, b))
        val legacy = output.toByteArray().also { it[7] = 5 }
        val v5 = ByteArrayOutputStream().apply { write(legacy) }
        DataOutputStream(v5).apply {
            writeInt(1)
            writeUTF("study")
            writeUTF("학습")
            writeInt(2)
            writeUTF("b")
            writeUTF("a")
            writeInt(1)
            writeUTF("a")
            writeUTF("study")
        }
        val read = ReadingRecordsCodec.readCatalog(ByteArrayInputStream(v5.toByteArray()))
        assertEquals(setOf("study"), read.memberships["a"])
        assertEquals(listOf("b", "a"), read.order)
        assertEquals(a, read.records.single { it.uri == "a" })
    }

    @Test
    fun invalidOrEmptyMembershipsAreRejectedInsteadOfBecomingUncategorized() {
        assertThrows(IllegalArgumentException::class.java) {
            catalog().copy(memberships = mapOf("a" to emptySet()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            catalog().addToShelves(setOf("a"), setOf("unknown"))
        }
        assertThrows(IllegalArgumentException::class.java) { catalog().moveShelfBy("study", 2) }
    }

    @Test
    fun presentationSortDoesNotRewriteManualOrderAndUnreadDocumentsStayLast() {
        val value = catalog().moveBy("a", 1, null)
        assertEquals(
            listOf("a", "b"),
            value.sortedRecords(null, DocumentSort.RECENT).map {
                it.uri
            }
        )
        assertEquals(listOf("a", "b"), value.sortedRecords(null, DocumentSort.NAME).map { it.uri })
        assertEquals(
            listOf("b", "a"),
            value.sortedRecords(null, DocumentSort.MANUAL).map {
                it.uri
            }
        )
        assertEquals(listOf("b", "a"), value.order)
        assertEquals(
            listOf("reference", "study"),
            value.sortedShelves(BookshelfSort.NAME).map {
                it.id
            }
        )
        assertEquals(
            listOf("study", "reference"),
            value.sortedShelves(BookshelfSort.CUSTOM).map {
                it.id
            }
        )
    }

    @Test
    fun sortingIsStableForEqualNamesDatesAndKoreanOrNumericTitles() {
        val values =
            LibraryCatalog(listOf(b.copy(title = "같음"), a.copy(title = "같음", position = null)))
        assertEquals(listOf("a", "b"), values.sortedRecords(null, DocumentSort.NAME).map { it.uri })
        val mixed = LibraryCatalog(listOf(b.copy(title = "10장"), a.copy(title = "2장")))
        assertEquals(
            listOf("10장", "2장"),
            mixed.sortedRecords(null, DocumentSort.NAME).map { it.title }
        )
    }

    @Test
    fun preferenceCodecRoundTripsAndRejectsUnknownOrTrailingData() {
        val value = LibraryPreferences(LibraryViewMode.LIST, DocumentSort.NAME, BookshelfSort.NAME)
        val output = ByteArrayOutputStream()
        LibraryPreferencesCodec.write(output, value)
        assertEquals(
            value,
            LibraryPreferencesCodec.read(ByteArrayInputStream(output.toByteArray()))
        )
        assertThrows(IllegalArgumentException::class.java) {
            LibraryPreferencesCodec.read(ByteArrayInputStream(output.toByteArray() + 1))
        }
        assertTrue(catalog().moveShelfBy("reference", 1) == catalog())
    }
}
