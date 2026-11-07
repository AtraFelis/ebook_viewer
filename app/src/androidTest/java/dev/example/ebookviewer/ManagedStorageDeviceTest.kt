package dev.example.ebookviewer

import android.app.Activity
import android.app.Dialog
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.system.Os
import android.view.View
import android.widget.Button
import android.widget.TextView
import java.io.ByteArrayInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** All destructive cases use generated PDFs and exact test-owned records, never personal documents. */
class ManagedStorageDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation
    private val ui get() = DeviceGestures(instrumentation)
    private val app get() = instrumentation.targetContext.applicationContext as ReaderApplication
    private var previousOrientation: OrientationMode? = null

    @Before
    fun keepTheFixtureActivityInTheSystemOrientation() {
        previousOrientation = result<OrientationMode>(app.settings::load)
        result<OrientationMode> { app.settings.save(OrientationMode.AUTO, it) }
    }

    @After
    fun restoreTheUserOrientationChoice() {
        previousOrientation?.let { mode ->
            result<OrientationMode> { app.settings.save(mode, it) }
        }
    }

    @Test
    fun retryPopupKeepsTheLegacyPositionAndLayout() {
        DocumentAccessDeviceTest().errorUiKeepsRecordAndRetryRestoresPositionAndLayout()
    }

    @Test
    fun copiedProviderDocumentSurvivesRevokedPermission() {
        DocumentAccessDeviceTest().crossUidPipeProviderCopyRemainsReadableAfterGrantRevocation()
    }

    @Test
    fun batchCopyDeduplicatesAndKeepsTheOriginalFiles() {
        LibraryFeaturesDeviceTest().clipDataBatchDeduplicatesAndCatalogReloadKeepsOrganization()
    }

    @Test
    fun copiedLegacyDocumentSurvivesOriginalMoveWithItsAnchorShelfBookmarkAndCover() {
        val file = fixture()
        val moved = File(file.parentFile, "${file.name}.moved")
        val uri = Uri.fromFile(file).toString()
        val shelf = "managed-${UUID.randomUUID()}"
        val anchor = ReadingPosition(8, 0.5, 0.4, 2.0)
        val record = ReadingRecord(uri, file.name, 1, 100, anchor)
        library {
            it.put(record)
            it.addShelf(Bookshelf(shelf, "Public fixture shelf"))
            it.addToShelves(setOf(uri), setOf(shelf))
        }
        bookmark { it.put(Bookmark(uri, 8, 0.4)) }
        val activity = ui.launch()
        var covers: BookCoverLoader? = null
        try {
            ui.onMain { (activity as MainActivity).openDocument(Uri.parse(uri)) }
            ui.awaitReady(activity)
            ui.onMain { activity.findViewById<View>(R.id.reader_home).performClick() }
            val saved = library { it.list().single { entry -> entry.uri == uri } }
            val token = requireNotNull(saved.managedCopy)
            assertArrayEquals(file.readBytes(), app.managedPdfs.file(token).readBytes())
            assertTrue(file.renameTo(moved))
            ui.onMain { (activity as MainActivity).openDocument(Uri.parse(uri)) }
            val view = ui.awaitReady(activity)
            val restored = requireNotNull(ui.onMain { view.readingPosition() })
            assertEquals(anchor.page, restored.page)
            assertEquals(anchor.y, restored.y, 0.003)
            assertEquals(anchor.zoom, restored.zoom, 0.000001)
            assertEquals(setOf(shelf), library { it.snapshot().memberships[uri] })
            assertEquals(listOf(Bookmark(uri, 8, 0.4)), bookmark { it.list(uri) })
            if (Build.VERSION.SDK_INT >= 30) {
                ui.onMain {
                    covers = BookCoverLoader(app) {}
                    requireNotNull(covers).request(listOf(saved))
                }
                ui.await("Managed PDF cover did not render after the original moved") {
                    covers?.get(uri) != null
                }
            }
        } finally {
            ui.onMain {
                covers?.close()
                activity.finish()
            }
            instrumentation.waitForIdleSync()
            library {
                it.remove(uri)
                it.removeShelf(shelf)
            }
            bookmark { it.remove(uri, 8) }
            file.delete()
            moved.delete()
        }
    }

    @Test
    fun recoveryDialogRestoresAndReselectPreservesTheReadingRecordAndBookmark() {
        val original = fixture()
        val replacement = fixture()
        val uri = Uri.fromFile(original).toString()
        val next = Uri.fromFile(replacement).toString()
        val shelf = "repair-${UUID.randomUUID()}"
        library { it.snapshot() }
        val token = app.managedPdfs.copy(ByteArrayInputStream(byteArrayOf(1, 2, 3))) {}
        val anchor = ReadingPosition(3, 0.5, 0.2, 2.0)
        library {
            it.put(ReadingRecord(uri, original.name, 1, 100, anchor, managedCopy = token))
            it.addShelf(Bookshelf(shelf, "Recovery fixture"))
            it.addToShelves(setOf(uri), setOf(shelf))
        }
        bookmark { it.put(Bookmark(uri, 3, 0.2)) }
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        var activity = ui.launch()
        try {
            ui.onMain { (activity as MainActivity).openDocument(Uri.parse(uri)) }
            awaitRecovery(activity)
            assertTrue(
                ui.views(activity.window.decorView).none {
                    it is Button &&
                        it.isShown &&
                        it.text == activity.getString(R.string.retry_document)
                }
            )
            val previous = activity
            ui.onMain { activity.recreate() }
            ui.await("Recovery Activity did not recreate") {
                val latest = monitor.lastActivity
                latest != null && latest !== previous && !latest.isDestroyed
            }
            activity = requireNotNull(monitor.lastActivity)
            awaitRecovery(activity)
            SyntheticDocument.write(app.managedPdfs.file(token))
            click(recovery(activity), activity, R.string.retry_document)
            ui.awaitReady(activity)
            ui.onMain { activity.findViewById<View>(R.id.reader_home).performClick() }
            assertTrue(app.managedPdfs.file(token).delete())
            ui.onMain { (activity as MainActivity).openDocument(Uri.parse(uri)) }
            awaitRecovery(activity)
            val picker = instrumentation.addMonitor(
                IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    addDataType("application/pdf")
                },
                Instrumentation.ActivityResult(
                    Activity.RESULT_OK,
                    Intent().setData(Uri.parse(next))
                ),
                true
            )
            try {
                click(recovery(activity), activity, R.string.select_again)
                val view = ui.awaitReady(activity)
                assertEquals(anchor.page, ui.onMain { requireNotNull(view.readingPosition()).page })
                assertEquals(1, picker.hits)
            } finally {
                instrumentation.removeMonitor(picker)
            }
            ui.onMain { activity.findViewById<View>(R.id.reader_home).performClick() }
            val catalog = library { it.snapshot() }
            assertFalse(catalog.records.any { it.uri == uri })
            assertNotNull(catalog.records.single { it.uri == next }.managedCopy)
            assertEquals(setOf(shelf), catalog.memberships[next])
            assertEquals(listOf(Bookmark(next, 3, 0.2)), bookmark { it.list(next) })
            assertTrue(original.exists() && replacement.exists())
        } finally {
            ui.onMain {
                (monitor.lastActivity ?: activity).finish()
                activity.finish()
            }
            instrumentation.waitForIdleSync()
            instrumentation.removeMonitor(monitor)
            library {
                it.removeAll(setOf(uri, next))
                it.removeShelf(shelf)
            }
            bookmark {
                it.remove(uri, 3)
                it.remove(next, 3)
            }
            original.delete()
            replacement.delete()
        }
    }

    @Test
    fun recoveryDeleteConfirmationPreservesOriginalAndCancelPreservesTheCopy() {
        val original = fixture()
        val uri = Uri.fromFile(original).toString()
        val bytes = original.readBytes()
        library { it.snapshot() }
        val token = app.managedPdfs.copy(ByteArrayInputStream(byteArrayOf(1))) {}
        library { it.put(ReadingRecord(uri, original.name, 1, managedCopy = token)) }
        val activity = ui.launch()
        try {
            ui.onMain { (activity as MainActivity).openDocument(Uri.parse(uri)) }
            awaitRecovery(activity)
            click(recovery(activity), activity, R.string.remove_document_recovery)
            var confirmation = workflowDialog(activity)
            ui.await("Delete confirmation did not appear") {
                confirmation = workflowDialog(activity)
                confirmation?.isShowing == true
            }
            click(confirmation, activity, android.R.string.cancel)
            assertTrue(app.managedPdfs.file(token).exists())
            assertTrue(library { it.list().any { record -> record.uri == uri } })
            ui.onMain { (activity as MainActivity).openDocument(Uri.parse(uri)) }
            awaitRecovery(activity)
            click(recovery(activity), activity, R.string.remove_document_recovery)
            ui.await("Second delete confirmation did not appear") {
                workflowDialog(activity)?.isShowing == true
            }
            click(workflowDialog(activity), activity, R.string.library_confirm_delete)
            ui.await("Document deletion did not finish") {
                workflowDialog(activity)?.isShowing != true
            }
            assertTrue(library { it.list().none { record -> record.uri == uri } })
            assertFalse(app.managedPdfs.file(token).exists())
            assertArrayEquals(bytes, original.readBytes())
        } finally {
            ui.onMain { activity.finish() }
            instrumentation.waitForIdleSync()
            library { it.remove(uri) }
            original.delete()
        }
    }

    @Test
    fun versionSixBackupAndBatchDeletionKeepOriginalsAndRejectLatePositionSaves() {
        val directory = File(app.cacheDir, "managed-catalog-${UUID.randomUUID()}").apply {
            mkdirs()
        }
        val path = File(directory, "records.bin")
        try {
            DataOutputStream(path.outputStream()).use {
                it.writeInt(0x45565231)
                it.writeInt(6)
                it.writeInt(0)
                repeat(3) { _ -> it.writeInt(0) }
            }
            val legacy = path.readBytes()
            val store = DocumentLibrary(path)
            val original = File(directory, "original.pdf").also(SyntheticDocument::write)
            val uri = Uri.fromFile(original).toString()
            val imported = importPdfs(
                store,
                app.contentResolver,
                listOf(Uri.parse(uri)),
                0,
                null,
                "PDF",
                directory
            )
            assertEquals(PdfImportResult(1, 0, 0), imported)
            assertArrayEquals(legacy, File(directory, "records.bin.before-v7").readBytes())
            val saved = store.list().single()
            val copy = store.copies.file(requireNotNull(saved.managedCopy))
            store.addShelf(Bookshelf("s", "Shelf"))
            store.addToShelves(setOf(uri), setOf("s"))
            store.removeShelf("s")
            assertTrue(copy.exists())
            store.remove(uri)
            store.saveReading(saved, null)
            assertTrue(store.list().isEmpty())
            assertFalse(copy.exists())
            assertTrue(original.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedCatalogWriteKeepsTheCopyAndThePreviousMetadata() {
        val directory = File(app.cacheDir, "managed-write-${UUID.randomUUID()}").apply {
            mkdirs()
        }
        val path = File(directory, "records.bin")
        try {
            val store = DocumentLibrary(path)
            val token = store.copies.copy(ByteArrayInputStream(byteArrayOf(1))) {}
            val record = ReadingRecord(
                "content://fixture/write",
                "Write fixture",
                1,
                managedCopy = token
            )
            store.put(record)
            val bytes = path.readBytes()
            Os.chmod(directory.path, 320)
            try {
                assertThrows(Exception::class.java) { store.remove(record.uri) }
                assertEquals(listOf(record), store.list())
                assertTrue(store.copies.file(token).exists())
                assertArrayEquals(bytes, path.readBytes())
            } finally {
                Os.chmod(directory.path, 448)
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun cleanupOnlyTheMissingUnorganizedHomeFixtureFromThePreviousTest() {
        val before = library { it.snapshot() }
        val owned = before.records.filter { record ->
            val uri = Uri.parse(record.uri)
            val file = uri.path?.let(::File)
            uri.scheme == "file" &&
                file != null &&
                file.parentFile?.canonicalFile == app.cacheDir.canonicalFile &&
                file.name.matches(Regex("home-orientation-[0-9]+\\.pdf")) &&
                record.title == file.name &&
                record.pageCount == 100 &&
                record.managedCopy == null &&
                !file.exists() &&
                record.uri !in before.memberships &&
                bookmark { it.list(record.uri).isEmpty() }
        }.map { it.uri }.toSet()
        library { it.removeAll(owned) }
        assertTrue(before.remove(owned) == library { it.snapshot() })
    }

    @Test
    fun reselectingTheSameUriReplacesOnlyItsBrokenAppCopy() {
        val original = fixture()
        val uri = Uri.fromFile(original).toString()
        library { it.snapshot() }
        val token = app.managedPdfs.copy(ByteArrayInputStream(byteArrayOf(1))) {}
        val position = ReadingPosition(4, 0.5, 0.3, 2.0)
        library { it.put(ReadingRecord(uri, original.name, 1, 100, position, managedCopy = token)) }
        val activity = ui.launch()
        val picker = instrumentation.addMonitor(
            IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                addDataType("application/pdf")
            },
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.parse(uri))),
            true
        )
        try {
            ui.onMain { (activity as MainActivity).openDocument(Uri.parse(uri)) }
            awaitRecovery(activity)
            click(recovery(activity), activity, R.string.select_again)
            val view = ui.awaitReady(activity)
            assertEquals(position.page, ui.onMain { requireNotNull(view.readingPosition()).page })
            ui.onMain { activity.findViewById<View>(R.id.reader_home).performClick() }
            val saved = library { it.list().single { record -> record.uri == uri } }
            assertTrue(saved.managedCopy != null && saved.managedCopy != token)
            assertFalse(app.managedPdfs.file(token).exists())
            assertTrue(original.exists())
        } finally {
            instrumentation.removeMonitor(picker)
            ui.onMain { activity.finish() }
            instrumentation.waitForIdleSync()
            library { it.remove(uri) }
            original.delete()
        }
    }

    private fun fixture(): File = File.createTempFile("owned-fixture-", ".pdf", app.cacheDir)
        .also(SyntheticDocument::write)

    private fun recovery(activity: Activity): Dialog? = MainActivity::class.java
        .getDeclaredField("recoveryDialog").apply { isAccessible = true }.get(activity) as? Dialog

    private fun workflowDialog(activity: Activity): Dialog? {
        val workflow = MainActivity::class.java.getDeclaredField("libraryWorkflow")
            .apply { isAccessible = true }.get(activity)
        return LibraryWorkflow::class.java.getDeclaredField("dialog")
            .apply { isAccessible = true }.get(workflow) as? Dialog
    }

    private fun awaitRecovery(activity: Activity) {
        ui.await("File recovery popup did not appear") { recovery(activity)?.isShowing == true }
        ui.onMain {
            val root = requireNotNull(requireNotNull(recovery(activity)).window).decorView
            assertNotNull(root.findViewById<View>(R.id.document_recovery))
            val labels = ui.views(root).filterIsInstance<TextView>().map { it.text.toString() }
            listOf(
                R.string.retry_document,
                R.string.select_again,
                R.string.remove_document_recovery
            )
                .forEach { assertTrue(activity.getString(it) in labels) }
        }
    }

    private fun click(dialog: Dialog?, activity: Activity, resource: Int) = ui.onMain {
        ui.views(requireNotNull(requireNotNull(dialog).window).decorView)
            .filterIsInstance<Button>().single {
                it.isShown && it.text == activity.getString(resource)
            }.performClick()
    }

    private fun <T> library(action: (DocumentLibrary) -> T): T = result { done ->
        app.library.execute(action, done)
    }

    private fun <T> bookmark(action: (BookmarkStore) -> T): T = result { done ->
        app.bookmarks.execute(action, done)
    }

    private fun <T> result(start: ((Result<T>) -> Unit) -> Unit): T {
        var value: Result<T>? = null
        val latch = CountDownLatch(1)
        start {
            value = it
            latch.countDown()
        }
        assertTrue(latch.await(20, TimeUnit.SECONDS))
        return requireNotNull(value).getOrThrow()
    }
}
