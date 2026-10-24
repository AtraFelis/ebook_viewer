package dev.example.ebookviewer

import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.EditText
import android.widget.ListView
import android.widget.Switch
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFeaturesDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation
    private val ui get() = DeviceGestures(instrumentation)

    @Test
    fun inversionToggleSurvivesActivityRecreationAndPreservesOutsideColor() {
        val application = instrumentation.targetContext.applicationContext as ReaderApplication
        val settings = application.settings
        val previous = awaitResult(settings::loadInversion)
        awaitResult<Boolean> { settings.saveInversion(false, it) }
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        var activity = ui.launch()
        try {
            val viewer = ui.sample(activity)
            ui.onMain {
                viewer.performClick()
                activity.findViewById<View>(R.id.reader_view_settings).performClick()
            }
            ui.await("View settings did not open") { viewSettings(activity)?.isShowing == true }
            ui.await("Inversion setting did not load") {
                inversionControl(activity).isEnabled
            }
            val outside = ui.onMain { viewer.outsideBackgroundColor() }
            val before = ui.state(viewer)
            ui.onMain { inversionControl(activity).performClick() }
            ui.await("Inversion did not save") {
                inversionControl(activity).let { it.isEnabled && it.isChecked }
            }
            assertTrue(awaitResult(settings::loadInversion))
            val after = ui.state(viewer)
            ui.onMain {
                assertTrue(viewer.colorsInverted)
                assertEquals(outside, viewer.outsideBackgroundColor())
                assertEquals(before.getDouble("zoom"), after.getDouble("zoom"), 0.0)
            }
            val old = activity
            ui.onMain { activity.recreate() }
            ui.await("Reader did not recreate", 30_000) {
                val latest = monitor.lastActivity
                if (latest != null && latest !== old && viewSettings(latest)?.isShowing == true) {
                    activity = latest
                    true
                } else {
                    false
                }
            }
            val restored = ui.awaitReady(activity)
            ui.await("Saved inversion did not restore") {
                viewSettings(activity)?.isShowing == true &&
                    restored.colorsInverted &&
                    inversionControl(activity).isChecked
            }
        } finally {
            ui.onMain { activity.finish() }
            instrumentation.removeMonitor(monitor)
            awaitResult<Boolean> { settings.saveInversion(previous, it) }
        }
    }

    @Test
    fun sharedManagementDialogsSaveRenameMoveAndRemoveWithoutDeletingFiles() {
        val directory = File.createTempFile("shelf-ui-", "", instrumentation.targetContext.cacheDir)
        assertTrue(directory.delete() && directory.mkdir())
        val source = File(directory, "public-fixture.pdf").apply { writeText("public fixture") }
        val uri = Uri.fromFile(source).toString()
        val repository = LibraryRepository { File(directory, "records.bin") }
        awaitResult<Unit> { done ->
            repository.execute({ it.put(ReadingRecord(uri, "Fixture", 1)) }, done)
        }
        val activity = ui.launch()
        var manager: LibraryManagement? = null
        fun dialog(): AlertDialog? =
            LibraryManagement::class.java.getDeclaredField("dialog").apply {
                isAccessible = true
            }.get(manager) as AlertDialog?
        fun current(): AlertDialog = requireNotNull(dialog())
        fun choose(label: String) {
            ui.await("Shelf list unavailable") {
                val dialog = dialog() ?: return@await false
                val list = ui.views(requireNotNull(dialog.window).decorView)
                    .filterIsInstance<ListView>().singleOrNull() ?: return@await false
                val index = (0 until list.count).first { list.adapter.getItem(it) == label }
                assertEquals(
                    ReaderStyle(activity).accent,
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.currentTextColor
                )
                list.performItemClick(null, index, index.toLong())
                true
            }
        }
        fun name(value: String) {
            ui.onMain {
                val dialog = current()
                val input = dialog.findViewById<EditText>(R.id.bookshelf_name)
                input.setText(value)
                requireNotNull(dialog.getButton(AlertDialog.BUTTON_POSITIVE)).performClick()
            }
            ui.await("Shelf save did not close") {
                LibraryManagement::class.java.getDeclaredField("dialog").apply {
                    isAccessible = true
                }.get(manager) == null
            }
        }
        fun snapshot(): LibraryCatalog = awaitResult { done ->
            repository.execute({ it.snapshot() }, done)
        }
        try {
            ui.onMain {
                manager = LibraryManagement(
                    activity,
                    repository,
                    { true },
                    {},
                    { error("UI failure") }
                )
                manager?.shelves()
            }
            choose(activity.getString(R.string.add_bookshelf))
            ui.onMain {
                requireNotNull(current().getButton(AlertDialog.BUTTON_POSITIVE)).performClick()
                assertTrue(current().findViewById<EditText>(R.id.bookshelf_name).error != null)
            }
            name("Shelf A")
            val id = snapshot().shelves.single().id
            ui.onMain { manager?.shelves() }
            choose("Shelf A")
            choose(activity.getString(R.string.rename_bookshelf))
            name("Shelf B")
            assertEquals(id, snapshot().shelves.single().id)
            ui.onMain { manager?.move(setOf(uri)) }
            choose("Shelf B")
            assertEquals(setOf(id), snapshot().memberships[uri])
            ui.onMain { manager?.shelves() }
            choose("Shelf B")
            choose(activity.getString(R.string.delete_bookshelf))
            ui.onMain {
                requireNotNull(current().getButton(AlertDialog.BUTTON_POSITIVE)).performClick()
            }
            assertTrue(snapshot().shelves.isEmpty())
            assertTrue(snapshot().memberships.isEmpty())
            assertEquals(listOf(uri), snapshot().order)
            ui.onMain {
                manager?.remove(setOf(uri))
                requireNotNull(current().getButton(AlertDialog.BUTTON_POSITIVE)).performClick()
            }
            assertTrue(snapshot().records.isEmpty())
            assertTrue(source.exists())
        } finally {
            ui.onMain {
                manager?.close()
                activity.finish()
            }
            directory.deleteRecursively()
        }
    }

    private fun inversionControl(activity: android.app.Activity): Switch =
        requireNotNull(viewSettings(activity)).findViewById(R.id.invert_pdf)

    private fun viewSettings(activity: android.app.Activity): ViewSettingsDialog? =
        MainActivity::class.java.getDeclaredField("viewSettingsDialog").apply {
            isAccessible = true
        }.get(activity) as ViewSettingsDialog?

    private fun <T> awaitResult(start: ((Result<T>) -> Unit) -> Unit): T {
        val latch = CountDownLatch(1)
        var result: Result<T>? = null
        start {
            result = it
            latch.countDown()
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        return requireNotNull(result).getOrThrow()
    }

    @Test
    fun inversionChangesOnlyPagePixelsWithoutRerenderOrViewportReset() {
        ui.onMain {
            var requests = emptyList<Tile>()
            var requestCount = 0
            val view =
                PdfCanvasView(instrumentation.targetContext) {
                    requests = it
                    requestCount++
                }
            view.layout(0, 0, 800, 800)
            view.setBackgroundSettings(BackgroundSettings(0xff123456.toInt(), false))
            view.setPages(listOf(PageSize(400, 800), PageSize(800, 800)))
            val preview = requests.first { it.page == 0 }
            val page = Bitmap.createBitmap(preview.width, preview.height, Bitmap.Config.ARGB_8888)
            page.eraseColor(Color.rgb(24, 100, 231))
            view.putTile(preview, page)
            val before = view.saveViewport()
            val cache = view.renderState().cachedBytes
            val count = requestCount
            val output = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(output))
                assertEquals(Color.rgb(24, 100, 231), output.getPixel(400, 400))
                assertEquals(0xff123456.toInt(), output.getPixel(1, 400))
                view.setColorsInverted(true)
                view.draw(Canvas(output))
                assertEquals(Color.rgb(231, 155, 24), output.getPixel(400, 400))
                assertEquals(0xff123456.toInt(), output.getPixel(1, 400))
                assertEquals(count, requestCount)
                assertEquals(cache, view.renderState().cachedBytes)
                val after = view.saveViewport()
                assertEquals(requireNotNull(before).keySet(), requireNotNull(after).keySet())
                before.keySet().forEach { key ->
                    if (key == "pageCrop") {
                        assertArrayEquals(before.getIntArray(key), after.getIntArray(key))
                    } else {
                        @Suppress("DEPRECATION")
                        assertEquals(before.get(key), after.get(key))
                    }
                }
                view.setColorsInverted(false)
                view.draw(Canvas(output))
                assertEquals(Color.rgb(24, 100, 231), output.getPixel(400, 400))
            } finally {
                view.reset()
                page.recycle()
                output.recycle()
            }
        }
    }

    @Test
    fun clipDataBatchDeduplicatesAndCatalogReloadKeepsOrganization() {
        val directory = File.createTempFile(
            "batch-library-",
            "",
            instrumentation.targetContext.cacheDir
        )
        directory.delete()
        directory.mkdirs()
        try {
            val first = File(directory, "a.pdf").apply { writeText("public synthetic placeholder") }
            val second = File(directory, "b.pdf").apply {
                writeText("public synthetic placeholder")
            }
            val a = Uri.fromFile(first)
            val b = Uri.fromFile(second)
            val intent = Intent().apply {
                data = a
                clipData = ClipData.newRawUri("PDF", a).apply {
                    addItem(ClipData.Item(b))
                    addItem(ClipData.Item(a))
                }
            }
            assertEquals(listOf(a, b), intent.selectedPdfUris())
            val path = File(directory, "records.bin")
            val library = DocumentLibrary(path)
            library.addShelf(Bookshelf("s", "여러 권"))
            val result =
                importPdfs(
                    library,
                    instrumentation.targetContext.contentResolver,
                    intent.selectedPdfUris(),
                    0,
                    "s",
                    "PDF"
                )
            assertEquals(PdfImportResult(2, 0, 0, 0), result)
            val repeated =
                importPdfs(
                    library,
                    instrumentation.targetContext.contentResolver,
                    listOf(a, b),
                    0,
                    null,
                    "PDF"
                )
            assertEquals(PdfImportResult(0, 2, 0, 0), repeated)
            library.moveBy(b.toString(), -1, "s")
            val reopened = DocumentLibrary(path)
            assertEquals(listOf(b.toString(), a.toString()), reopened.snapshot().order)
            assertEquals(2, reopened.snapshot().visible("s").size)
            reopened.removeAll(setOf(a.toString(), b.toString()))
            assertTrue(DocumentLibrary(path).list().isEmpty())
            assertTrue(first.exists() && second.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun managementSelectsMultipleWithoutOpeningAndRestoresSelection() {
        // Keep the regression at the new grid entry point instead of the removed checkbox form.
        BookshelfRedesignDeviceTest().gridSelectionRestoresAndShelfHomeUsesRecycledCells()
    }
}
