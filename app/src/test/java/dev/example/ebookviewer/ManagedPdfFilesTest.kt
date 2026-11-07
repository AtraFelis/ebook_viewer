package dev.example.ebookviewer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ManagedPdfFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun completeCopyIsIndependentAndDeletesOnlyItsOwnFile() {
        val original = temporary.newFile("original.pdf").apply {
            writeBytes(ByteArray(70_000) { 3 })
        }
        val folder = temporary.newFolder("owned")
        val files = ManagedPdfFiles(folder) { Long.MAX_VALUE }
        val token = original.inputStream().use { files.copy(it) {} }
        assertArrayEquals(original.readBytes(), files.file(token).readBytes())
        original.writeText("External document changed")
        assertEquals(70_000, files.file(token).length().toInt())
        assertTrue(folder.listFiles().orEmpty().none { it.extension == "pending" })
        files.remove(token)
        assertFalse(files.file(token).exists())
        assertEquals("External document changed", original.readText())
    }

    @Test
    fun cancellationAndReadFailureLeaveNoPublishedOrPartialFiles() {
        val folder = temporary.newFolder("cancel")
        val files = ManagedPdfFiles(folder) { Long.MAX_VALUE }
        var checks = 0
        assertThrows(InterruptedException::class.java) {
            files.copy(ByteArrayInputStream(ByteArray(100_000))) {
                if (++checks == 2) throw InterruptedException()
            }
        }
        val broken = object : InputStream() {
            override fun read(): Int = throw IOException("Synthetic provider failure")
        }
        assertThrows(IOException::class.java) { files.copy(broken) {} }
        assertTrue(folder.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun diskReserveAndPathTraversalAreRejectedWithoutTouchingTheOriginal() {
        val folder = temporary.newFolder("full")
        val files = ManagedPdfFiles(folder) { 0 }
        assertThrows(ManagedStorageException::class.java) {
            files.copy(ByteArrayInputStream(byteArrayOf(1))) {}
        }
        assertThrows(IllegalArgumentException::class.java) { files.remove("../original") }
        assertThrows(IllegalArgumentException::class.java) { files.file("/absolute/path") }
        assertTrue(folder.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun spaceExhaustionMidCopyRemovesItsPartialOutput() {
        val folder = temporary.newFolder("mid-copy-full")
        var queries = 0
        val files = ManagedPdfFiles(folder) { if (++queries < 3) Long.MAX_VALUE else 0 }
        assertThrows(ManagedStorageException::class.java) {
            files.copy(ByteArrayInputStream(ByteArray(100_000))) {}
        }
        assertTrue(folder.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun startupRecoveryKeepsReferencedCopiesAndUnrelatedFiles() {
        val folder = temporary.newFolder("recovery")
        val files = ManagedPdfFiles(folder) { Long.MAX_VALUE }
        val retained = files.copy(ByteArrayInputStream(byteArrayOf(1))) {}
        val orphan = files.copy(ByteArrayInputStream(byteArrayOf(2))) {}
        val unrelated = java.io.File(folder, "unrelated.txt").apply { writeText("Keep") }
        files.recover(setOf(retained))
        assertTrue(files.file(retained).exists())
        assertFalse(files.file(orphan).exists())
        assertEquals("Keep", unrelated.readText())
    }

    @Test
    fun currentCodecPreservesCopyAndAllOrganizationWhileRejectingUnsafeTokens() {
        val record = ReadingRecord(
            "content://fixture/original",
            "Public fixture",
            9,
            100,
            ReadingPosition(12, 0.4, 0.5, 3.0),
            managedCopy = "01234567-89ab-4cde-8fab-0123456789ab"
        )
        val catalog = LibraryCatalog(
            listOf(record),
            listOf(Bookshelf("s", "Shelf")),
            memberships = mapOf(record.uri to setOf("s"))
        )
        val bytes = ByteArrayOutputStream().also {
            ReadingRecordsCodec.writeCatalog(it, catalog)
        }
        assertEquals(catalog, ReadingRecordsCodec.readCatalog(bytes.toByteArray().inputStream()))
        assertThrows(IllegalArgumentException::class.java) {
            record.copy(managedCopy = "../source")
        }
    }

    @Test
    fun fixedVersionSixReadsWithoutInventingACopy() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply {
            writeInt(0x45565231)
            writeInt(6)
            writeInt(1)
            writeUTF("content://fixture/v6")
            writeUTF("Version six")
            writeLong(9)
            writeInt(100)
            writeBoolean(false)
            writeUTF(PageSizeMode.UNIFORM_WIDTH.name)
            writeInt(24)
            writeByte(1)
            repeat(4) { writeByte(0) }
            writeInt(1)
            writeUTF("s")
            writeUTF("Shelf")
            writeInt(1)
            writeUTF("content://fixture/v6")
            writeInt(1)
            writeUTF("content://fixture/v6")
            writeInt(1)
            writeUTF("s")
        }
        val read = ReadingRecordsCodec.readCatalog(bytes.toByteArray().inputStream())
        assertNull(read.records.single().managedCopy)
        assertEquals(setOf("s"), read.memberships[read.records.single().uri])
        assertTrue(read.records.single().pageLayout.horizontalPanLocked)
    }
}
