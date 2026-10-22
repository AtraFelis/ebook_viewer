package dev.example.ebookviewer

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.ListView
import android.widget.SeekBar
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Opt-in regression cases; only synthetic pages/files enter these tests. */
class ReaderUsabilityDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation
    private val ui get() = DeviceGestures(instrumentation)
    private val application get() =
        instrumentation.targetContext.applicationContext as ReaderApplication

    @Test
    fun pendingPageColorsRetainLastColorUntilAnActualResultOrExplicitManualChoice() {
        var requests = emptyList<Tile>()
        val view = ui.onMain {
            PdfCanvasView(instrumentation.targetContext) { requests = it }.apply {
                layout(0, 0, 800, 800)
                setPages(List(4) { PageSize(800, 800) })
                setBackgroundSettings(BackgroundSettings(Color.MAGENTA, true))
            }
        }
        val bitmaps = mutableListOf<Bitmap>()
        fun deliver(color: Int) = ui.onMain {
            val tile = requests.first()
            val bitmap = Bitmap.createBitmap(tile.width, tile.height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(color)
            bitmaps.add(bitmap)
            view.putTile(tile, bitmap)
        }
        try {
            // The preview cannot be analyzed until the first stable-page commit.
            deliver(Color.BLUE)
            ui.await("Initial page color not ready") { view.outsideBackgroundColor() == Color.BLUE }
            for (page in 1..2) {
                ui.onMain { view.goToPage(page, 0.5) }
                SystemClock.sleep(250)
                ui.onMain { assertEquals(Color.BLUE, view.outsideBackgroundColor()) }
            }
            deliver(Color.GREEN)
            ui.await("New page color not applied") { view.outsideBackgroundColor() == Color.GREEN }
            ui.onMain { view.goToPage(3, 0.5) }
            SystemClock.sleep(250)
            ui.onMain { assertEquals(Color.GREEN, view.outsideBackgroundColor()) }
            // Transparent analysis is a genuine fallback, unlike a missing bitmap.
            deliver(Color.TRANSPARENT)
            ui.await("Actual extraction failure did not fall back") {
                view.outsideBackgroundColor() == Color.MAGENTA
            }
            ui.onMain {
                view.setBackgroundSettings(BackgroundSettings(Color.RED))
                assertEquals(Color.RED, view.outsideBackgroundColor())
                view.reset()
                assertEquals(Color.RED, view.outsideBackgroundColor())
            }
        } finally {
            ui.onMain { view.reset() }
            bitmaps.forEach(Bitmap::recycle)
        }
    }

    @Test
    fun cropClipsPreviewsAndTilesAndRetainsOriginalAnchorAcrossLayoutAndRotation() {
        ui.onMain {
            var requests = emptyList<Tile>()
            val view = PdfCanvasView(instrumentation.targetContext) { requests = it }
            val pages = List(30) { PageSize(800, 1000) }
            val bitmaps = mutableListOf<Bitmap>()
            val output = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
            try {
                view.layout(0, 0, 800, 800)
                view.restorePosition(ReadingPosition(10, 0.5, 0.5, 4.0))
                view.setPages(pages)
                val before = requireNotNull(view.readingPosition())
                val selected = PageLayoutSettings(
                    gap = 20,
                    horizontalPanLocked = true,
                    crop = PageCrop(10, 5, 20, 15)
                )
                view.setPageLayoutSettings(selected)
                assertPosition(before, requireNotNull(view.readingPosition()))
                val restored = PdfCanvasView(instrumentation.targetContext)
                restored.layout(0, 0, 1000, 400)
                restored.restoreViewport(view.saveViewport())
                restored.setPages(pages)
                assertEquals(selected, restored.pageLayoutSettings())
                assertPosition(before, requireNotNull(restored.readingPosition()))
                restored.reset()
                view.setPageLayoutSettings(selected.copy(mode = PageSizeMode.UNIFORM_WIDTH))
                assertPosition(before, requireNotNull(view.readingPosition()))
                view.setPageLayoutSettings(selected)
                view.reset()
                view.restorePosition(ReadingPosition(0, 0.5, 0.05, 1.0))
                view.setPages(pages)
                assertTrue(view.goToPage(0))
                for (tile in requests) {
                    val bitmap = Bitmap.createBitmap(
                        tile.width,
                        tile.height,
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(if (tile.page == 0) Color.BLUE else Color.GREEN)
                    val paint = Paint().apply { color = Color.RED }
                    canvas.save()
                    canvas.translate(-tile.x.toFloat(), -tile.y.toFloat())
                    canvas.scale(tile.scale.toFloat(), tile.scale.toFloat())
                    canvas.drawRect(0f, 0f, 800f, 50f, paint)
                    canvas.drawRect(0f, 850f, 800f, 1000f, paint)
                    canvas.drawRect(0f, 0f, 80f, 1000f, paint)
                    canvas.drawRect(640f, 0f, 800f, 1000f, paint)
                    canvas.restore()
                    bitmaps.add(bitmap)
                    view.putTile(tile, bitmap)
                }
                view.setBackgroundSettings(BackgroundSettings(Color.MAGENTA))
                view.draw(Canvas(output))
                assertEquals(Color.BLUE, output.getPixel(200, 100))
                assertEquals(Color.BLUE, output.getPixel(600, 799))
                assertEquals(Color.MAGENTA, output.getPixel(10, 100))
                assertTrue(view.renderState().cachedBytes <= 64 * 1024 * 1024)
                assertTrue(view.renderState().visibleTiles < 20)
            } finally {
                view.reset()
                bitmaps.forEach(Bitmap::recycle)
                output.recycle()
            }
        }
    }

    @Test
    fun cropFailureKeepsDraftAndRetryAndCancelDoNotChangeOtherLayoutSettings() {
        val activity = ui.launch()
        val original = PageLayoutSettings(PageSizeMode.UNIFORM_WIDTH, 24, true)
        var attempts = 0
        var applied: PageLayoutSettings? = null
        lateinit var form: EditableSettingsForm
        var opened: AlertDialog? = null
        try {
            ui.onMain {
                form = activity.pageCropDialog(original) { selected, done ->
                    attempts++
                    if (attempts > 1) applied = selected
                    done(attempts > 1)
                }
                opened = form.dialog
                form.dialog.show()
                form.dialog.findViewById<SeekBar>(R.id.crop_bottom).progress = 7
                form.dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                assertTrue(form.dialog.isShowing)
                assertEquals(7, form.dialog.findViewById<SeekBar>(R.id.crop_bottom).progress)
                val draft = form.capture()
                assertTrue(draft.getBoolean("failed"))
                form.dialog.dismiss()
                form = activity.pageCropDialog(original, draft) { selected, done ->
                    applied = selected
                    done(true)
                }
                opened = form.dialog
                form.dialog.show()
                assertEquals(7, form.dialog.findViewById<SeekBar>(R.id.crop_bottom).progress)
                form.dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                assertEquals(original.copy(crop = PageCrop(bottom = 7)), applied)
                assertFalse(form.dialog.isShowing)
            }
        } finally {
            ui.onMain {
                opened?.dismiss()
                activity.finish()
            }
        }
    }

    @Test
    fun documentCropPersistsAndDoesNotLeakToAnotherDocumentOrCancel() {
        val file = File.createTempFile(
            "crop-fixture-",
            ".pdf",
            instrumentation.targetContext.cacheDir
        )
        SyntheticDocument.write(file)
        val uri = Uri.fromFile(file)
        val hash = file.readBytes().contentHashCode()
        var activity = ui.launch()
        try {
            ui.onMain { (activity as MainActivity).openDocument(uri) }
            var view = ui.awaitReady(activity)
            ui.onMain { view.performClick() }
            click(activity, R.string.settings)
            val parent = ui.onMain { requireNotNull(current(activity)) }
            click(activity, R.string.page_crop_settings)
            val crop = ui.onMain { requireNotNull(current(activity)) }
            val preview = ui.onMain { crop.findViewById<PageCropPreview>(R.id.crop_page_preview) }
            ui.await("Actual PDF preview not ready") {
                PageCropPreview::class.java.getDeclaredField("bitmap").apply {
                    isAccessible = true
                }.get(preview) != null
            }
            ui.onMain {
                crop.findViewById<SeekBar>(R.id.crop_bottom).progress = 6
                assertEquals(6, preview.crop.bottom)
                assertEquals(PageCrop(), view.pageLayoutSettings().crop)
                crop.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            ui.await("Crop did not save and return to settings") {
                view.pageLayoutSettings().crop.bottom == 6 && current(activity) === parent
            }
            click(activity, R.string.page_crop_settings)
            ui.onMain {
                val dialog = requireNotNull(current(activity))
                dialog.findViewById<SeekBar>(R.id.crop_bottom).progress = 30
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
                assertEquals(6, view.pageLayoutSettings().crop.bottom)
                activity.finish()
            }
            activity = ui.launch()
            ui.onMain { (activity as MainActivity).openDocument(uri) }
            view = ui.awaitReady(activity)
            ui.onMain { assertEquals(6, view.pageLayoutSettings().crop.bottom) }
            ui.onMain { activity.finish() }
            activity = ui.launch()
            view = ui.sample(activity)
            ui.onMain { assertEquals(PageCrop(), view.pageLayoutSettings().crop) }
            assertEquals(hash, file.readBytes().contentHashCode())
        } finally {
            ui.onMain { activity.finish() }
            val latch = CountDownLatch(1)
            application.library.execute({ it.remove(uri.toString()) }) { latch.countDown() }
            assertTrue(latch.await(5, TimeUnit.SECONDS))
            file.delete()
        }
    }

    @Test
    fun quickOrientationRestoresItsOwnRouteWithoutOpeningSettingsHome() {
        val previous = setting(application.settings::load)
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        var activity = ui.launch()
        try {
            val view = ui.sample(activity)
            ui.onMain {
                view.performClick()
                activity.findViewById<View>(R.id.reader_orientation).performClick()
            }
            ui.await("Quick orientation did not open") { popup(activity)?.isShowing == true }
            ui.onMain {
                assertNull(field(activity, "settingsHomeDialog"))
                val list = ui.views(requireNotNull(popup(activity)).contentView)
                    .filterIsInstance<ListView>().single()
                val portrait = activity.resources.configuration.orientation ==
                    android.content.res.Configuration.ORIENTATION_PORTRAIT
                val target = if (portrait) OrientationMode.PORTRAIT else OrientationMode.LANDSCAPE
                list.performItemClick(null, target.ordinal, target.ordinal.toLong())
                assertTrue(requireNotNull(popup(activity)).isShowing)
                assertNull(current(activity))
            }
            ui.await("Direction write did not finish") {
                val list = ui.views(requireNotNull(popup(activity)).contentView)
                    .filterIsInstance<ListView>().single()
                list.isEnabled
            }
            val old = activity
            ui.onMain { activity.recreate() }
            ui.await("Quick orientation route did not survive recreation", 30_000) {
                val latest = monitor.lastActivity
                if (latest != null &&
                    latest !== old &&
                    popup(latest)?.contentView?.hasWindowFocus() == true
                ) {
                    activity = latest
                    true
                } else {
                    false
                }
            }
            ui.awaitReady(activity)
            ui.onMain {
                assertNull(field(activity, "settingsHomeDialog"))
                requireNotNull(popup(activity)).dismiss()
            }
            ui.await("Quick orientation did not return to reader tools") {
                popup(activity) == null &&
                    activity.findViewById<View>(R.id.reader_tools).isShown &&
                    activity.hasWindowFocus()
            }
        } finally {
            ui.onMain { activity.finish() }
            instrumentation.removeMonitor(monitor)
            setting { application.settings.save(previous, it) }
        }
    }

    private fun field(activity: Activity, name: String): Any? =
        MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(activity)

    private fun popup(activity: Activity): android.widget.PopupWindow? =
        field(activity, "orientationPopup") as android.widget.PopupWindow?

    private fun current(activity: Activity): AlertDialog? =
        field(activity, "settingsDialog") as AlertDialog?

    private fun click(activity: Activity, label: Int) {
        ui.await("Requested control is unavailable") {
            val root = current(activity)?.window?.decorView ?: activity.window.decorView
            val button = ui.views(root).filterIsInstance<Button>()
                .firstOrNull { it.text == activity.getString(label) && it.isEnabled }
            button?.performClick() == true
        }
    }

    private fun assertPosition(expected: ReadingPosition, actual: ReadingPosition) {
        assertEquals(expected.page, actual.page)
        assertEquals(expected.x, actual.x, 1e-6)
        assertEquals(expected.y, actual.y, 1e-6)
        assertEquals(expected.zoom, actual.zoom, 0.0)
    }

    private fun <T> setting(start: ((Result<T>) -> Unit) -> Unit): T {
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
    fun gapEditorTapShowsNumericKeyboardAndKeepsApplyReachable() {
        val activity = ui.launch()
        try {
            val view = ui.sample(activity)
            ui.onMain { view.performClick() }
            click(activity, R.string.settings)
            click(activity, R.string.page_layout_settings)
            val dialog = ui.onMain { requireNotNull(current(activity)) }
            val input = ui.onMain {
                dialog.findViewById<android.widget.EditText>(R.id.page_gap_input)
            }
            ui.await("Gap editor window not focused") { input.hasWindowFocus() }
            ui.onMain {
                val flags = requireNotNull(dialog.window).attributes.flags
                assertEquals(
                    0,
                    flags and android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                )
                input.requestRectangleOnScreen(
                    android.graphics.Rect(0, 0, input.width, input.height),
                    true
                )
            }
            ui.tap(input)
            ui.await("Gap editor did not show the keyboard") {
                android.os.Build.VERSION.SDK_INT >= 30 &&
                    dialog.window?.decorView?.rootWindowInsets
                        ?.isVisible(android.view.WindowInsets.Type.ime()) == true
            }
            ui.onMain { input.selectAll() }
            instrumentation.sendStringSync("24")
            val apply = ui.onMain { dialog.getButton(AlertDialog.BUTTON_POSITIVE) }
            ui.onMain {
                apply.requestRectangleOnScreen(
                    android.graphics.Rect(0, 0, apply.width, apply.height),
                    true
                )
            }
            ui.await("Apply button is clipped by keyboard") {
                val bounds = android.graphics.Rect()
                apply.getGlobalVisibleRect(bounds) && bounds.height() == apply.height
            }
            ui.onMain { apply.performClick() }
            ui.await("Typed gap did not apply") { view.pageLayoutSettings().gap == 24 }
        } finally {
            ui.onMain { activity.finish() }
        }
    }

    @Test
    fun actualPreviewUsesBoundedWorkerTileAndMasksWithoutChangingSourcePixels() {
        ui.onMain {
            var requests = emptyList<Tile>()
            val view = PdfCanvasView(instrumentation.targetContext) { requests = it }
            view.layout(0, 0, 400, 800)
            view.setPages(listOf(PageSize(800, 1000)))
            var received: Bitmap? = null
            view.observeCropPreview { received = it }
            val tile = requests.single { it.scale == 0.512 }
            assertTrue(tile.width <= 512 && tile.height <= 512)
            val page = Bitmap.createBitmap(tile.width, tile.height, Bitmap.Config.ARGB_8888)
            val output = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
            try {
                page.eraseColor(Color.GREEN)
                view.putTile(tile, page)
                assertTrue(received === page)
                val preview = PageCropPreview(instrumentation.targetContext)
                preview.layout(0, 0, 400, 400)
                preview.showPage(received)
                preview.crop = PageCrop(bottom = 40)
                preview.draw(Canvas(output))
                assertEquals(Color.GREEN, output.getPixel(200, 100))
                assertTrue(output.getPixel(200, 350) != Color.GREEN)
                assertEquals(Color.GREEN, page.getPixel(page.width / 2, page.height - 1))
                view.observeCropPreview(null)
                assertFalse(requests.any { it.scale == 0.512 })
                assertTrue(view.renderState().cachedBytes <= 64 * 1024 * 1024)
            } finally {
                view.reset()
                page.recycle()
                output.recycle()
            }
        }
    }
}
