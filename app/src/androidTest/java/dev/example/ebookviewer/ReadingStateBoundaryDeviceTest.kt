package dev.example.ebookviewer

import android.app.Activity
import android.net.Uri
import android.view.View
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real exit boundaries with a test-only IO barrier; never alter the user's storage permissions. */
class ReadingStateBoundaryDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation
    private val ui get() = DeviceGestures(instrumentation)
    private val application
        get() = instrumentation.targetContext.applicationContext as ReaderApplication

    @Test
    fun pendingLayoutSaveAndDocumentSwitchRetainLatestPosition() = exitWhileSaving(Exit.DOCUMENT)

    @Test
    fun pendingLayoutSaveAndHomeReturnRetainLatestPosition() = exitWhileSaving(Exit.LIBRARY)

    @Test
    fun pendingLayoutSaveAndActivityFinishRetainLatestPosition() = exitWhileSaving(Exit.FINISH)

    @Test
    fun positionUpdateKeepsRolledBackSettingsAndNeverRecreatesRemovedRecords() {
        val directory = File.createTempFile("state-store-", "", application.cacheDir)
        assertTrue(directory.delete())
        assertTrue(directory.mkdir())
        try {
            val file = File(directory, "records.bin")
            val store = DocumentLibrary(file)
            for (locked in listOf(false, true)) {
                val original = ReadingRecord(
                    "content://fixture/state",
                    "State fixture",
                    1,
                    100,
                    ReadingPosition(8, 0.5, 0.4, 2.0),
                    PageLayoutSettings(PageSizeMode.UNIFORM_WIDTH, 24, locked)
                )
                store.put(original)
                val before = file.readBytes()
                // Force a codec failure in this isolated AtomicFile, not the user's library.
                assertThrows(java.io.UTFDataFormatException::class.java) {
                    store.put(
                        original.copy(
                            title = "x".repeat(70_000),
                            pageLayout = original.pageLayout.copy(horizontalPanLocked = !locked)
                        )
                    )
                }
                assertTrue(before.contentEquals(file.readBytes()))
                val latest = ReadingPosition(60, 0.6, 0.7, 4.0)
                store.updatePosition(original.uri, latest)
                val expected = original.copy(position = latest)
                assertEquals(listOf(expected), store.list())
                assertEquals(listOf(expected), DocumentLibrary(file).list())
                store.remove(original.uri)
                val removed = file.readBytes()
                store.updatePosition(original.uri, latest)
                assertTrue(store.list().isEmpty())
                assertTrue(removed.contentEquals(file.readBytes()))
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun exitWhileSaving(exit: Exit) {
        val files = List(2) { File.createTempFile("state-boundary-", ".pdf", application.cacheDir) }
        files.forEach(SyntheticDocument::write)
        val uris = files.map(Uri::fromFile)
        var activity: Activity = ui.launch()
        val release = CountDownLatch(1)
        try {
            ui.onMain { (activity as MainActivity).openDocument(uris[0]) }
            var viewer = ui.awaitReady(activity)
            ui.pinch(viewer)
            ui.await("Fixture catalog did not reach the target") { viewer.goToPage(60, 0.6) }
            ui.onMain { assertTrue(viewer.goToPage(8, 0.4)) }
            ui.awaitReady(activity)
            library { it.list() }
            val entered = CountDownLatch(1)
            val field = LibraryRepository::class.java.getDeclaredField("executor").apply {
                isAccessible = true
            }
            (field.get(application.library) as ExecutorService).execute {
                entered.countDown()
                release.await(20, TimeUnit.SECONDS)
            }
            assertTrue("Library barrier did not start", entered.await(5, TimeUnit.SECONDS))
            val expected = ui.onMain {
                if (!activity.findViewById<View>(R.id.reader_tools).isShown) viewer.performClick()
                val lock = activity.findViewById<View>(R.id.horizontal_pan_lock)
                assertTrue(lock.isEnabled)
                lock.performClick()
                assertFalse("Settings write is not pending", lock.isEnabled)
                assertTrue(viewer.goToPage(60, 0.6))
                val position = requireNotNull(viewer.readingPosition())
                when (exit) {
                    Exit.DOCUMENT -> (activity as MainActivity).openDocument(uris[1])
                    Exit.LIBRARY -> activity.findViewById<View>(R.id.reader_home).performClick()
                    Exit.FINISH -> activity.finish()
                }
                position
            }
            if (exit == Exit.FINISH) {
                ui.await("Reader did not finish") { activity.isDestroyed }
            }
            release.countDown()
            if (exit == Exit.DOCUMENT) ui.awaitReady(activity)
            val stored = library { store -> store.list().single { it.uri == uris[0].toString() } }
            assertTrue(
                "A later position save reverted the new lock",
                stored.pageLayout.horizontalPanLocked
            )
            assertPosition(expected, requireNotNull(stored.position))
            if (exit == Exit.FINISH) activity = ui.launch()
            ui.onMain { (activity as MainActivity).openDocument(uris[0]) }
            viewer = ui.awaitReady(activity)
            ui.onMain {
                assertTrue(viewer.pageLayoutSettings().horizontalPanLocked)
                assertPosition(expected, requireNotNull(viewer.readingPosition()))
            }
            ui.assertCacheBound(viewer)
        } finally {
            release.countDown()
            ui.onMain { if (!activity.isDestroyed) activity.finish() }
            ui.await("Synthetic reader did not close") { activity.isDestroyed }
            library { store -> uris.forEach { store.remove(it.toString()) } }
            files.forEach(File::delete)
        }
    }

    private fun assertPosition(expected: ReadingPosition, actual: ReadingPosition) {
        assertEquals("Latest page was lost at the exit boundary", expected.page, actual.page)
        assertEquals(expected.x, actual.x, 0.000001)
        assertEquals(expected.y, actual.y, 0.000001)
        assertEquals(expected.zoom, actual.zoom, 0.000001)
    }

    private fun <T> library(action: (DocumentLibrary) -> T): T {
        val done = CountDownLatch(1)
        var result: Result<T>? = null
        application.library.execute(action) {
            result = it
            done.countDown()
        }
        assertTrue("Library operation did not finish", done.await(10, TimeUnit.SECONDS))
        return requireNotNull(result).getOrThrow()
    }

    private enum class Exit { DOCUMENT, LIBRARY, FINISH }
}
