package dev.example.ebookviewer

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.TextView
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentAccessDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation
    private val ui get() = DeviceGestures(instrumentation)
    private val application
        get() = instrumentation.targetContext.applicationContext as ReaderApplication

    @Test
    fun seekableSourceKeepsDescriptorAndEmptyFileClosesIt() {
        val file = fixture()
        try {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val prepared = PdfDocumentSource(requireNotNull(file.parentFile))
                .prepare(descriptor, PdfOpenRequest())
            assertTrue(prepared === descriptor)
            PdfRenderer(prepared).use { assertEquals(100, it.pageCount) }
            file.writeBytes(byteArrayOf())
            val empty = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val fd = empty.fileDescriptor
            val error = assertThrows(PdfSourceException::class.java) {
                PdfDocumentSource(requireNotNull(file.parentFile)).prepare(empty, PdfOpenRequest())
            }
            assertEquals(PdfFailure.EMPTY, error.failure)
            assertFalse(fd.valid())
        } finally {
            file.delete()
        }
    }

    @Test
    fun pipeCopyIsSeekableAndUnlinkedBeforeReturning() {
        val file = fixture()
        val directory = directory()
        val pipe = pipe(file.readBytes())
        val fd = pipe.first.fileDescriptor
        try {
            val prepared = PdfDocumentSource(directory).prepare(pipe.first, PdfOpenRequest())
            assertTrue(requireNotNull(directory.list()).isEmpty())
            assertFalse(fd.valid())
            PdfRenderer(prepared).use { assertEquals(100, it.pageCount) }
            pipe.second.join(2_000)
            assertFalse(pipe.second.isAlive)
        } finally {
            pipe.first.close()
            file.delete()
            directory.deleteRecursively()
        }
    }

    @Test
    fun pipeLimitsTimeoutProviderErrorAndStorageFailureLeaveNoCopy() {
        val directory = directory()
        try {
            fun fails(bytes: ByteArray, expected: PdfFailure, maximum: Long = 1024) {
                val pipe = pipe(bytes)
                val fd = pipe.first.fileDescriptor
                val error = assertThrows(PdfSourceException::class.java) {
                    PdfDocumentSource(directory, maximum).prepare(pipe.first, PdfOpenRequest())
                }
                assertEquals(expected, error.failure)
                assertFalse(fd.valid())
                pipe.second.join(2_000)
                assertFalse(pipe.second.isAlive)
                assertTrue(requireNotNull(directory.list()).isEmpty())
            }
            fails(byteArrayOf(), PdfFailure.EMPTY)
            fails(ByteArray(1025), PdfFailure.COPY_LIMIT)
            val stalled = ParcelFileDescriptor.createReliablePipe()
            try {
                val error = assertThrows(PdfSourceException::class.java) {
                    PdfDocumentSource(directory, idleTimeoutMs = 200)
                        .prepare(stalled[0], PdfOpenRequest())
                }
                assertEquals(PdfFailure.TIMEOUT, error.failure)
            } finally {
                stalled.forEach { it.close() }
            }
            val broken = ParcelFileDescriptor.createReliablePipe()
            broken[1].closeWithError("Synthetic provider error")
            assertEquals(
                PdfFailure.READ,
                assertThrows(PdfSourceException::class.java) {
                    PdfDocumentSource(directory).prepare(broken[0], PdfOpenRequest())
                }.failure
            )
            val notDirectory = File(directory, "not-a-directory").apply { writeText("fixture") }
            val blocked = pipe(byteArrayOf(1))
            assertEquals(
                PdfFailure.STORAGE,
                assertThrows(PdfSourceException::class.java) {
                    PdfDocumentSource(notDirectory).prepare(blocked.first, PdfOpenRequest())
                }.failure
            )
            blocked.second.join(2_000)
            notDirectory.delete()
            assertTrue(requireNotNull(directory.list()).isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun cancellingStalledCopyLetsLatestDocumentLoadWithoutStaleError() {
        val directory = directory()
        val file = fixture()
        val stalled = ParcelFileDescriptor.createReliablePipe()
        val entered = CountDownLatch(1)
        val ready = CountDownLatch(1)
        val loaded = AtomicInteger()
        val failure = AtomicReference<PdfFailure?>()
        val worker = ui.onMain {
            PdfWorker(
                onLoaded = {
                    loaded.incrementAndGet()
                    ready.countDown()
                },
                onTile = { _, bitmap -> bitmap.recycle() },
                onError = { failure.set(it) }
            )
        }
        val fd = stalled[0].fileDescriptor
        try {
            ui.onMain {
                worker.openCancelable(false) {
                    entered.countDown()
                    PdfDocumentSource(directory).prepare(stalled[0], it)
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            SystemClock.sleep(100)
            ui.onMain {
                worker.open {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                }
            }
            assertTrue("Cancelled stream blocked the next open", ready.await(5, TimeUnit.SECONDS))
            assertFalse(fd.valid())
            assertEquals(1, loaded.get())
            assertEquals(null, failure.get())
            assertTrue(requireNotNull(directory.list()).isEmpty())
        } finally {
            ui.onMain { worker.close() }
            stalled.forEach { it.close() }
            file.delete()
            directory.deleteRecursively()
        }
    }

    @Test
    fun errorUiKeepsRecordAndRetryRestoresPositionAndLayout() {
        val file = File.createTempFile(
            "access-retry-",
            ".pdf",
            instrumentation.targetContext.cacheDir
        )
        file.writeText("Invalid synthetic PDF")
        val uri = Uri.fromFile(file)
        val position = ReadingPosition(20, 0.5, 0.4, 4.0)
        val record = ReadingRecord(
            uri.toString(),
            "Retry fixture",
            1,
            100,
            position,
            PageLayoutSettings(PageSizeMode.UNIFORM_WIDTH, 24)
        )
        library { it.put(record) }
        val activity = ui.launch()
        try {
            ui.onMain { (activity as MainActivity).openDocument(uri) }
            awaitError(activity, R.string.pdf_invalid_error)
            assertEquals(record, savedRecord(uri))
            SyntheticDocument.write(file)
            click(activity, R.string.retry_document)
            val view = ui.awaitReady(activity)
            assertEquals(record.pageLayout, ui.onMain { view.pageLayoutSettings() })
            val restored = requireNotNull(ui.onMain { view.readingPosition() })
            assertEquals(position.page, restored.page)
            assertEquals(position.y, restored.y, 0.000001)
            assertEquals(position.zoom, restored.zoom, 0.000001)
            ui.assertCacheBound(view)
            val absent = File(file.parentFile, "${file.name}.absent")
            ui.onMain { (activity as MainActivity).openDocument(Uri.fromFile(absent)) }
            awaitError(activity, R.string.pdf_unavailable_error)
            click(activity, R.string.sample_pdf)
            ui.awaitReady(activity)
        } finally {
            ui.onMain { activity.finish() }
            instrumentation.waitForIdleSync()
            library { it.remove(uri.toString()) }
            file.delete()
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun crossUidPipeProviderPersistsGrantAndRevocationPreservesRecord() {
        val resolver = instrumentation.targetContext.contentResolver
        val authority = Uri.parse("content://dev.example.ebookviewer.p0.test.documents")
        val granted = requireNotNull(resolver.call(authority, "grant", null, null))
        val uri = Uri.parse(granted.getString("uri"))
        var activity = ui.launch()
        try {
            val result = Intent().setData(uri).addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
            val picker = instrumentation.addMonitor(
                IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    addDataType("application/pdf")
                },
                Instrumentation.ActivityResult(Activity.RESULT_OK, result),
                true
            )
            val firstView = try {
                click(activity, R.string.open_pdf)
                try {
                    ui.awaitReady(activity)
                } catch (error: AssertionError) {
                    val detail = ui.onMain {
                        val resources = listOf(
                            R.string.pdf_access_error,
                            R.string.pdf_read_error,
                            R.string.pdf_invalid_error,
                            R.string.pdf_empty_error,
                            R.string.pdf_storage_error,
                            R.string.pdf_timeout_error,
                            R.string.pdf_unavailable_error
                        )
                        val labels = ui.views(activity.window.decorView)
                            .filterIsInstance<TextView>()
                        val visible = labels.filter { it.isShown }.map { it.text.toString() }
                        val reason = resources.firstOrNull { activity.getString(it) in visible }
                        "pickerHits=${picker.hits}, home=" +
                            activity.findViewById<View>(R.id.home_screen).isShown +
                            ", reason=" + reason?.let(activity.resources::getResourceEntryName)
                    }
                    throw AssertionError(detail, error)
                }
            } finally {
                instrumentation.removeMonitor(picker)
            }
            var view = firstView
            assertEquals(1, picker.hits)
            assertTrue(
                resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
            )
            ui.pinch(view)
            ui.await("Provider pages were not prepared") { view.goToPage(20, 0.4) }
            val position = requireNotNull(ui.onMain { view.readingPosition() })
            ui.onMain { activity.finish() }
            instrumentation.waitForIdleSync()
            activity = ui.launch()
            ui.onMain { (activity as MainActivity).openDocument(uri) }
            view = ui.awaitReady(activity)
            assertEquals(position, ui.onMain { view.readingPosition() })
            ui.onMain { activity.finish() }
            instrumentation.waitForIdleSync()
            val previous = library { it.list().single { entry -> entry.uri == uri.toString() } }
            assertTrue(previous.title.contains("Synthetic provider"))
            resolver.call(authority, "revoke", null, null)
            activity = ui.launch()
            ui.onMain { (activity as MainActivity).openDocument(uri) }
            awaitError(activity, R.string.pdf_access_error)
            assertEquals(previous, savedRecord(uri))
            resolver.call(authority, "grant", null, null)
            click(activity, R.string.retry_document)
            view = ui.awaitReady(activity)
            assertEquals(position, ui.onMain { view.readingPosition() })
            ui.assertCacheBound(view)
        } finally {
            ui.onMain { activity.finish() }
            instrumentation.waitForIdleSync()
            library { it.remove(uri.toString()) }
            resolver.call(authority, "cleanup", null, null)
        }
    }

    private fun awaitError(activity: Activity, resource: Int) {
        ui.await("Expected document error was not shown") {
            activity.findViewById<View>(R.id.home_screen).isShown &&
                ui.views(activity.window.decorView).filterIsInstance<TextView>().any {
                    it.isShown && it.text == activity.getString(resource)
                }
        }
        ui.onMain {
            assertTrue(
                ui.views(activity.window.decorView).filterIsInstance<Button>().any {
                    it.isShown && it.text == activity.getString(R.string.retry_document)
                }
            )
        }
    }

    private fun click(activity: Activity, resource: Int) = ui.onMain {
        ui.views(activity.window.decorView).filterIsInstance<Button>()
            .single { it.isShown && it.text == activity.getString(resource) }.performClick()
    }

    private fun savedRecord(uri: Uri): ReadingRecord = library { store ->
        store.list().single { it.uri == uri.toString() }
    }

    private fun fixture(): File = File.createTempFile(
        "access-fixture-",
        ".pdf",
        instrumentation.targetContext.cacheDir
    ).also(SyntheticDocument::write)

    private fun directory(): File = File.createTempFile(
        "access-directory-",
        "",
        instrumentation.targetContext.cacheDir
    ).apply {
        delete()
        mkdirs()
    }

    private fun pipe(bytes: ByteArray): Pair<ParcelFileDescriptor, Thread> {
        val descriptors = ParcelFileDescriptor.createReliablePipe()
        val producer = Thread {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(descriptors[1]).use { it.write(bytes) }
            } catch (_: IOException) {
                // A bounded import intentionally closes its reader early.
            }
        }.also(Thread::start)
        return descriptors[0] to producer
    }

    private fun <T> library(action: (DocumentLibrary) -> T): T {
        val done = CountDownLatch(1)
        var result: Result<T>? = null
        application.library.execute(action) {
            result = it
            done.countDown()
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        return requireNotNull(result).getOrThrow()
    }
}
