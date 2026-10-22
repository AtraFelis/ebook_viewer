package dev.example.ebookviewer

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFeaturesDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation
    private val ui get() = DeviceGestures(instrumentation)

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
                requireNotNull(before).keySet().forEach { key ->
                    @Suppress("DEPRECATION")
                    assertEquals(before.get(key), requireNotNull(after).get(key))
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
        val activity = ui.launch()
        try {
            ui.onMain {
                val records = listOf(ReadingRecord("a", "A", 1), ReadingRecord("b", "B", 2))
                var opened = false
                var removed = emptySet<String>()
                fun createHome() = ReaderHomeView(
                    activity, TextView(activity), Button(activity), Button(activity),
                    {}, {}, {}, { opened = true }, {}, { removed = it }, {}, { _, _, _ -> }, {}
                )
                val home = createHome()
                assertEquals(
                    View.GONE,
                    home.view.findViewById<View>(R.id.library_selection).visibility
                )
                home.render(LibraryCatalog(records))
                ui.views(home.view).filterIsInstance<Button>().first {
                    it.text == activity.getString(R.string.manage_documents)
                }.performClick()
                ui.views(home.view).filterIsInstance<CheckBox>().forEach { it.performClick() }
                assertTrue(!opened)
                val restored = createHome()
                restored.restoreState(home.saveState())
                restored.render(LibraryCatalog(records))
                assertTrue(
                    ui.views(restored.view).filterIsInstance<CheckBox>().all {
                        it.isChecked
                    }
                )
                ui.views(restored.view).filterIsInstance<Button>().first {
                    it.text == activity.getString(R.string.remove_selected)
                }.performClick()
                assertEquals(setOf("a", "b"), removed)
            }
        } finally {
            ui.onMain { activity.finish() }
        }
    }
}
