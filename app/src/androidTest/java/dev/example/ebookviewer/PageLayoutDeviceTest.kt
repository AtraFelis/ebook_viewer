package dev.example.ebookviewer

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageLayoutDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation
    private val ui get() = DeviceGestures(instrumentation)
    private val application
        get() = instrumentation.targetContext.applicationContext as ReaderApplication

    @Test
    fun modeChangesAndProgressiveWidthsKeepAnchorZoomAndBoundedRequests() {
        ui.onMain {
            val view = PdfCanvasView(instrumentation.targetContext)
            view.layout(0, 0, 400, 800)
            view.restorePosition(ReadingPosition(10, 0.6, 0.4, 4.0))
            val pages = List(30) { PageSize(if (it % 2 == 0) 400 else 800, 800) }
            view.setPages(pages)
            val expected = requireNotNull(view.readingPosition())
            repeat(20) { index ->
                view.setPageLayoutSettings(
                    PageLayoutSettings(
                        PageSizeMode.entries[index % 2],
                        if (index % 2 == 0) 0 else 24
                    )
                )
                assertPosition(expected, requireNotNull(view.readingPosition()))
                assertTrue(view.renderState().visibleTiles < 16)
            }
            view.setPages(pages + PageSize(1200, 800))
            assertPosition(expected, requireNotNull(view.readingPosition()))
            repeat(20) { index ->
                assertTrue(view.goToPage(if (index % 2 == 0) 20 else 10, 0.4))
                assertEquals(expected.zoom, requireNotNull(view.readingPosition()).zoom, 0.0)
            }
            assertPosition(expected, requireNotNull(view.readingPosition()))
            val saved = view.saveViewport()
            val restored = PdfCanvasView(instrumentation.targetContext)
            restored.layout(0, 0, 800, 400)
            restored.restoreViewport(saved)
            restored.setPages(pages + PageSize(1200, 800))
            assertPosition(expected, requireNotNull(restored.readingPosition()))
            assertEquals(view.pageLayoutSettings(), restored.pageLayoutSettings())
            view.reset()
            restored.reset()
        }
    }

    @Test
    fun uniformPagePixelsAndGapArePlacedInDocumentCoordinates() {
        ui.onMain {
            var requests = emptyList<Tile>()
            val view = PdfCanvasView(instrumentation.targetContext) { requests = it }
            view.layout(0, 0, 800, 800)
            view.setBackgroundSettings(BackgroundSettings(Color.MAGENTA))
            view.setPageLayoutSettings(PageLayoutSettings(PageSizeMode.UNIFORM_WIDTH, 24))
            view.setPages(listOf(PageSize(400, 300), PageSize(800, 800)))
            val output = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
            val bitmaps = ArrayList<Bitmap>()
            try {
                for (tile in requests) {
                    val bitmap = Bitmap.createBitmap(
                        tile.width,
                        tile.height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.eraseColor(if (tile.page == 0) Color.BLUE else Color.GREEN)
                    bitmaps.add(bitmap)
                    view.putTile(tile, bitmap)
                }
                view.draw(Canvas(output))
                assertEquals(Color.BLUE, output.getPixel(5, 500))
                assertEquals(Color.BLUE, output.getPixel(790, 500))
                assertEquals(Color.MAGENTA, output.getPixel(400, 612))
                assertEquals(Color.GREEN, output.getPixel(400, 700))
                assertTrue(view.renderState().cachedBytes <= 64 * 1024 * 1024)
            } finally {
                view.reset()
                bitmaps.forEach(Bitmap::recycle)
                output.recycle()
            }
        }
    }

    @Test
    fun documentSettingsUiValidatesCancelsAndRestoresIndependentlyAfterRelaunch() {
        val first = File.createTempFile(
            "layout-first-",
            ".pdf",
            instrumentation.targetContext.cacheDir
        )
        val second = File.createTempFile(
            "layout-second-",
            ".pdf",
            instrumentation.targetContext.cacheDir
        )
        SyntheticDocument.write(first)
        SyntheticDocument.write(second)
        val firstUri = Uri.fromFile(first)
        val secondUri = Uri.fromFile(second)
        var activity = ui.launch()
        try {
            ui.onMain { (activity as MainActivity).openDocument(firstUri) }
            var view = ui.awaitReady(activity)
            ui.onMain {
                view.performClick()
                activity.findViewById<View>(R.id.horizontal_pan_lock).performClick()
            }
            ui.await("Document pan lock did not save") {
                view.pageLayoutSettings().horizontalPanLocked
            }
            val lockedDefault = PageLayoutSettings(horizontalPanLocked = true)
            ui.pinch(view)
            ui.await("Target page metadata did not arrive") { view.goToPage(20, 0.4) }
            val expected = requireNotNull(ui.onMain { view.readingPosition() })
            openSettings(activity, view)
            action(activity.getString(R.string.uniform_page_width))
            gap(activity, "73")
            action(activity.getString(R.string.apply_settings))
            assertEquals(lockedDefault, ui.onMain { view.pageLayoutSettings() })
            gap(activity, "24")
            action(activity.getString(android.R.string.cancel))
            assertEquals(lockedDefault, ui.onMain { view.pageLayoutSettings() })
            openSettings(activity, view)
            action(activity.getString(R.string.uniform_page_width))
            gap(activity, "24")
            action(activity.getString(R.string.apply_settings))
            val selected = PageLayoutSettings(PageSizeMode.UNIFORM_WIDTH, 24, true)
            ui.await("Layout did not apply") { view.pageLayoutSettings() == selected }
            assertPosition(expected, requireNotNull(ui.onMain { view.readingPosition() }))
            ui.awaitReady(activity)
            ui.assertCacheBound(view)
            ui.onMain { (activity as MainActivity).openDocument(secondUri) }
            view = ui.awaitReady(activity)
            assertEquals(PageLayoutSettings(), ui.onMain { view.pageLayoutSettings() })
            val records = library { it.list() }
            assertEquals(selected, records.single { it.uri == firstUri.toString() }.pageLayout)
            assertEquals(
                PageLayoutSettings(),
                records.single { it.uri == secondUri.toString() }.pageLayout
            )
            ui.onMain { activity.finish() }
            activity = ui.launch()
            ui.onMain { (activity as MainActivity).openDocument(firstUri) }
            view = ui.awaitReady(activity)
            assertEquals(selected, ui.onMain { view.pageLayoutSettings() })
            assertPosition(expected, requireNotNull(ui.onMain { view.readingPosition() }))
            val stored = library {
                DocumentLibrary(
                    File(instrumentation.targetContext.filesDir, "reading-records.bin")
                ).list()
            }
            assertEquals(selected, stored.single { it.uri == firstUri.toString() }.pageLayout)
        } finally {
            ui.onMain { activity.finish() }
            ui.await("Synthetic layout Activity did not close") { activity.isDestroyed }
            library {
                it.remove(firstUri.toString())
                it.remove(secondUri.toString())
            }
            first.delete()
            second.delete()
        }
    }

    private fun assertPosition(expected: ReadingPosition, actual: ReadingPosition) {
        assertEquals(expected.page, actual.page)
        assertEquals(expected.x, actual.x, 0.000001)
        assertEquals(expected.y, actual.y, 0.000001)
        assertEquals(expected.zoom, actual.zoom, 0.000001)
    }

    private fun openSettings(activity: Activity, view: PdfCanvasView) {
        ui.onMain {
            if (!activity.findViewById<View>(R.id.reader_tools).isShown) view.performClick()
            ui.views(activity.window.decorView).filterIsInstance<Button>()
                .single {
                    it.isShown && it.text == activity.getString(R.string.settings)
                }.performClick()
        }
        action(activity.getString(R.string.page_layout_settings))
    }

    private fun gap(activity: Activity, text: String) {
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        awaitAction {
            instrumentation.uiAutomation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByText(activity.getString(R.string.page_gap_hint))
                ?.firstOrNull { it.className == "android.widget.EditText" }
                ?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments) == true
        }
    }

    private fun action(text: String) {
        awaitAction {
            instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)
                ?.firstOrNull { it.text?.toString() == text }
                ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
        SystemClock.sleep(150)
    }

    private fun awaitAction(action: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (action()) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Page layout dialog action failed")
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
