package dev.example.ebookviewer

import android.app.Activity
import android.net.Uri
import android.util.AtomicFile
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation
    private val ui get() = DeviceGestures(instrumentation)

    @Test
    fun atomicRecoveryDeduplicationAndRelinkKeepTheOriginalPdf() {
        val directory = File.createTempFile(
            "library-fixture-",
            "",
            instrumentation.targetContext.cacheDir
        )
        directory.delete()
        directory.mkdirs()
        try {
            val file = File(directory, "records.bin")
            val original = File(directory, "original.pdf").apply { writeText("synthetic fixture") }
            val first =
                ReadingRecord(
                    original.toURI().toString(),
                    "A",
                    10,
                    100,
                    ReadingPosition(30, 0.7, 0.2, 2.0)
                )
            DocumentLibrary(file).put(first)
            AtomicFile(file).startWrite().use { it.write(byteArrayOf(1, 2, 3)) }
            val recovered = DocumentLibrary(file)
            assertEquals(listOf(first), recovered.list())
            val newLocation = first.copy(uri = "content://fixture/new", openedAt = 20)
            recovered.put(newLocation, first.uri)
            recovered.put(newLocation.copy(openedAt = 30))
            assertEquals(1, DocumentLibrary(file).list().size)
            assertEquals(first.position, DocumentLibrary(file).list().single().position)
            recovered.remove(newLocation.uri)
            assertTrue(DocumentLibrary(file).list().isEmpty())
            assertTrue(original.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun corruptLibraryIsNotSilentlyReplaced() {
        val file = File.createTempFile(
            "bad-library-",
            ".bin",
            instrumentation.targetContext.cacheDir
        )
        try {
            val original = byteArrayOf(4, 3, 2, 1, 0)
            file.writeBytes(original)
            assertThrows(Exception::class.java) { DocumentLibrary(file) }
            assertTrue(original.contentEquals(file.readBytes()))
        } finally {
            file.delete()
        }
    }

    @Test
    fun twoDocumentsRestoreIndependentlyFromListAfterActivityRelaunch() {
        val first = fixture()
        val second = fixture()
        var activity: Activity = ui.launch()
        try {
            open(activity, first)
            var view = ui.awaitReady(activity)
            ui.pinch(view)
            repeat(7) { ui.drag(view, true) }
            repeat(5) { ui.pinch(view) }
            val firstPosition = position(view)
            assertTrue(firstPosition.page > 0)
            assertTrue(firstPosition.zoom > 8.0)
            open(activity, second)
            view = ui.awaitReady(activity)
            ui.pinch(view)
            repeat(3) { ui.drag(view, true) }
            val secondPosition = position(view)
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
            activity = ui.launch()
            selectRecent(activity, first.name)
            assertPosition(firstPosition, position(ui.awaitReady(activity)))
            open(activity, second)
            assertPosition(secondPosition, position(ui.awaitReady(activity)))
            // Reopening and saving A/B must not create duplicate records.
            val entries = records(activity).filter {
                it.uri in
                    listOf(Uri.fromFile(first).toString(), Uri.fromFile(second).toString())
            }
            assertEquals(2, entries.size)
            assertNotNull(entries[0].position)
            assertNotNull(entries[1].position)
            ui.assertCacheBound(ui.awaitReady(activity))
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
            removeFixtures(activity, first, second)
        }
    }

    @Test
    fun missingDocumentDoesNotOverwriteItsSavedAnchor() {
        val file = fixture()
        val activity = ui.launch()
        try {
            open(activity, file)
            val view = ui.awaitReady(activity)
            ui.pinch(view)
            repeat(4) { ui.drag(view, true) }
            instrumentation.runOnMainSync {
                activity.findViewById<android.view.View>(R.id.reader_home).performClick()
            }
            val previous = records(activity).single { it.uri == Uri.fromFile(file).toString() }
            assertTrue(file.delete())
            open(activity, file)
            ui.await("Missing document error was not displayed") {
                ui.views(activity.window.decorView).filterIsInstance<android.widget.TextView>()
                    .any { it.text == activity.getString(R.string.pdf_unavailable_error) }
            }
            assertEquals(previous, records(activity).single { it.uri == previous.uri })
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
            removeFixtures(activity, file)
        }
    }

    private fun fixture(): File =
        File.createTempFile("p1-reading-", ".pdf", instrumentation.targetContext.cacheDir)
            .also(SyntheticDocument::write)

    private fun open(activity: Activity, file: File) {
        instrumentation.runOnMainSync {
            (activity as MainActivity).openDocument(Uri.fromFile(file))
        }
    }

    private fun selectRecent(activity: Activity, title: String) {
        ui.await("Saved document not shown in list") {
            ui.views(activity.window.decorView).any {
                it.id == R.id.home_record && it.contentDescription?.startsWith(title) == true
            }
        }
        instrumentation.runOnMainSync {
            ui.views(activity.window.decorView)
                .first {
                    it.id == R.id.home_record && it.contentDescription?.startsWith(title) == true
                }
                .performClick()
        }
    }

    private fun position(view: PdfCanvasView): ReadingPosition {
        var position: ReadingPosition? = null
        instrumentation.runOnMainSync { position = view.readingPosition() }
        return requireNotNull(position)
    }

    private fun assertPosition(expected: ReadingPosition, actual: ReadingPosition) {
        assertEquals(expected.page, actual.page)
        assertEquals(expected.x, actual.x, 0.003)
        assertEquals(expected.y, actual.y, 0.003)
        assertEquals(expected.zoom, actual.zoom, 0.000001)
    }

    private fun records(activity: Activity): List<ReadingRecord> {
        val done = CountDownLatch(1)
        var result: Result<List<ReadingRecord>>? = null
        (activity.application as ReaderApplication).library.execute({ it.list() }) {
            result = it
            done.countDown()
        }
        assertTrue("Library IO timed out", done.await(10, TimeUnit.SECONDS))
        return requireNotNull(result).getOrThrow()
    }

    private fun removeFixtures(activity: Activity, vararg files: File) {
        val done = CountDownLatch(1)
        (activity.application as ReaderApplication).library.execute({ store ->
            files.forEach { store.remove(Uri.fromFile(it).toString()) }
        }) {
            done.countDown()
        }
        assertTrue("Cleanup timed out", done.await(10, TimeUnit.SECONDS))
        files.forEach {
            it.delete()
            assertFalse(it.exists())
        }
    }
}
