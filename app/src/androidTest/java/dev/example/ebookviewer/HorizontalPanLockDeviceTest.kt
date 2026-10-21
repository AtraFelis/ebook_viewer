package dev.example.ebookviewer

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HorizontalPanLockDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation
    private val ui get() = DeviceGestures(instrumentation)
    private val application
        get() = instrumentation.targetContext.applicationContext as ReaderApplication

    @Test
    fun diagonalScrollAndVerticalFlingRetainFractionalXAcrossMixedPages() {
        val view = fixture()
        try {
            val start = ui.state(view)
            repeat(4) {
                diagonal(view)
                settle(view)
            }
            val end = ui.state(view)
            assertEquals(start.getDouble("centerX"), end.getDouble("centerX"), 0.000001)
            assertEquals(start.getDouble("zoom"), end.getDouble("zoom"), 0.0)
            assertTrue(
                "Mixed page boundaries not crossed",
                end.getInt("page") > start.getInt("page") + 1
            )
            lock(view, false)
            assertEquals(end.getDouble("centerX"), ui.state(view).getDouble("centerX"), 0.0)
            diagonal(view)
            val moving = ui.state(view)
            assertTrue(
                "Unlock did not restore horizontal dragging",
                moving.getDouble("centerX") > end.getDouble("centerX") + 1
            )
            lock(view, true)
            val locked = ui.state(view)
            settle(view)
            assertEquals(locked.getDouble("centerX"), ui.state(view).getDouble("centerX"), 0.0)
            assertEquals(locked.getDouble("centerY"), ui.state(view).getDouble("centerY"), 0.0)
            ui.onMain {
                val restored = PdfCanvasView(instrumentation.targetContext)
                restored.layout(0, 0, 800, 400)
                restored.restoreViewport(locked)
                restored.setPages(pages())
                assertTrue(restored.pageLayoutSettings().horizontalPanLocked)
                val state = requireNotNull(restored.saveViewport())
                assertEquals(locked.getDouble("zoom"), state.getDouble("zoom"), 0.0)
                assertEquals(locked.getDouble("centerX"), state.getDouble("centerX"), 0.000001)
                restored.reset()
            }
        } finally {
            ui.onMain { view.reset() }
        }
    }

    @Test
    fun lockedPinchBothDirectionsAndEitherRemainingFingerKeepNewAnchor() {
        val view = fixture()
        var clicks = 0
        ui.onMain { view.setOnClickListener { clicks++ } }
        try {
            for (lifted in listOf(0, 1)) {
                val initial = ui.state(view).getDouble("zoom")
                pinchThenScroll(view, lifted)
                assertTrue(
                    "Locked pinch failed to enlarge",
                    ui.state(view).getDouble("zoom") > initial * 1.3
                )
                val enlarged = ui.state(view).getDouble("zoom")
                ui.pinch(view, outward = false)
                assertTrue(
                    "Locked pinch failed to reduce",
                    ui.state(view).getDouble("zoom") < enlarged * 0.8
                )
            }
            assertEquals("Pinch became a tools tap", 0, clicks)
            val before = ui.state(view)
            diagonal(view, cancel = true)
            val canceled = ui.state(view)
            settle(view)
            assertEquals(canceled.getDouble("centerY"), ui.state(view).getDouble("centerY"), 0.0)
            assertEquals(before.getDouble("centerX"), canceled.getDouble("centerX"), 0.000001)
            diagonal(view)
            val released = ui.state(view).getDouble("centerY")
            settle(view)
            assertTrue(
                "A fresh gesture lost vertical fling",
                ui.state(view).getDouble("centerY") > released
            )
        } finally {
            ui.onMain { view.reset() }
        }
    }

    @Test
    fun headerIconKeepsViewportAndRestoresIndependentDocumentChoice() {
        withDocuments { first, second ->
            var activity = ui.launch()
            try {
                ui.onMain { (activity as MainActivity).openDocument(first) }
                var view = ui.awaitReady(activity)
                ui.pinch(view)
                ui.await("Page catalog not ready") { view.goToPage(10, 0.4) }
                val before = ui.state(view)
                val icon = ui.onMain { icon(activity, view) }
                ui.await("Lock icon not laid out") { icon.isShown && icon.width > 0 }
                ui.onMain {
                    val title = activity.findViewById<View>(R.id.reader_title)
                    assertTrue("Lock is not right of title", icon.left >= title.right)
                    val minimum = (48 * activity.resources.displayMetrics.density).toInt()
                    assertTrue(icon.width >= minimum && icon.height >= minimum)
                    icon.performClick()
                }
                ui.await("Header did not apply saved lock") { icon.isSelected && icon.isEnabled }
                assertEquals(before.getDouble("zoom"), ui.state(view).getDouble("zoom"), 0.0)
                val after = ui.state(view)
                assertEquals(before.getDouble("centerX"), after.getDouble("centerX"), 0.000001)
                capture(activity, "design-pan-locked-synthetic.png")
                ui.onMain { (activity as MainActivity).openDocument(second) }
                view = ui.awaitReady(activity)
                assertFalse(ui.onMain { view.pageLayoutSettings().horizontalPanLocked })
                assertFalse(ui.onMain { icon(activity, view).isSelected })
                ui.onMain { activity.finish() }
                ui.await("First Activity did not close") { activity.isDestroyed }
                activity = ui.launch()
                ui.onMain { (activity as MainActivity).openDocument(first) }
                view = ui.awaitReady(activity)
                assertTrue(ui.onMain { icon(activity, view).isSelected })
                assertTrue(storedLock(first))
                ui.onMain { icon(activity, view).performClick() }
                ui.await("Unlock did not apply") { !view.pageLayoutSettings().horizontalPanLocked }
                capture(activity, "design-pan-unlocked-synthetic.png")
            } finally {
                ui.onMain { activity.finish() }
                ui.await("Synthetic Activity did not close") { activity.isDestroyed }
            }
        }
    }

    @Test
    fun pendingHeaderWriteSurvivesRecreationAndBlocksDuplicateToggle() {
        withDocuments { first, _ ->
            var activity = ui.launch()
            val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
            val release = CountDownLatch(1)
            try {
                ui.onMain { (activity as MainActivity).openDocument(first) }
                val view = ui.awaitReady(activity)
                val button = ui.onMain { icon(activity, view) }
                library { it.list() }
                val entered = CountDownLatch(1)
                val field = LibraryRepository::class.java.getDeclaredField("executor").apply {
                    isAccessible = true
                }
                (field.get(application.library) as ExecutorService).execute {
                    entered.countDown()
                    release.await(20, TimeUnit.SECONDS)
                }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                ui.onMain {
                    button.performClick()
                    assertFalse(button.isEnabled)
                    button.performClick()
                    assertFalse(view.pageLayoutSettings().horizontalPanLocked)
                    activity.recreate()
                }
                val previous = activity
                ui.await("Activity did not recreate") {
                    val next = monitor.lastActivity ?: previous
                    if (next !== previous && previous.isDestroyed) {
                        activity = next
                        true
                    } else {
                        false
                    }
                }
                release.countDown()
                val restored = ui.awaitReady(activity)
                ui.await("Pending lock was overwritten by lifecycle state") {
                    restored.pageLayoutSettings().horizontalPanLocked &&
                        icon(activity, restored).isSelected
                }
                assertTrue(storedLock(first))
            } finally {
                release.countDown()
                ui.onMain { (monitor.lastActivity ?: activity).finish() }
                ui.await("Recreated Activity did not close") {
                    (monitor.lastActivity ?: activity).isDestroyed
                }
                instrumentation.removeMonitor(monitor)
            }
        }
    }

    private fun pages() = List(30) { PageSize(if (it % 2 == 0) 400 else 800, 800) }

    private fun lock(view: PdfCanvasView, locked: Boolean) {
        ui.onMain {
            view.setPageLayoutSettings(view.pageLayoutSettings().copy(horizontalPanLocked = locked))
        }
    }

    private fun storedLock(uri: Uri): Boolean = library { store ->
        store.list().single { it.uri == uri.toString() }.pageLayout.horizontalPanLocked
    }

    private fun fixture(): PdfCanvasView = ui.onMain {
        PdfCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 400, 800)
            setPageLayoutSettings(PageLayoutSettings(horizontalPanLocked = true))
            restorePosition(ReadingPosition(2, 0.60013, 0.5, 2.0))
            setPages(pages())
        }
    }

    private fun diagonal(view: PdfCanvasView, cancel: Boolean = false) {
        val down = SystemClock.uptimeMillis()
        fun event(action: Int, fraction: Float) {
            val event = MotionEvent.obtain(
                down,
                SystemClock.uptimeMillis(),
                action,
                280 - 120 * fraction,
                700 - 600 * fraction,
                0
            )
            ui.onMain { view.dispatchTouchEvent(event) }
            event.recycle()
        }
        event(MotionEvent.ACTION_DOWN, 0f)
        repeat(12) {
            SystemClock.sleep(16)
            event(MotionEvent.ACTION_MOVE, (it + 1) / 12f)
        }
        event(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, 1f)
    }

    private fun settle(view: PdfCanvasView) {
        repeat(35) {
            SystemClock.sleep(16)
            ui.onMain { view.computeScroll() }
        }
    }

    private fun pinchThenScroll(view: PdfCanvasView, lifted: Int) {
        val down = SystemClock.uptimeMillis()
        fun event(action: Int, span: Float, remaining: Boolean = false, offset: Float = 0f) {
            val indices = when {
                remaining -> listOf(1 - lifted)
                action == MotionEvent.ACTION_DOWN -> listOf(0)
                else -> listOf(0, 1)
            }
            val properties = indices.map { index ->
                MotionEvent.PointerProperties().apply {
                    id = index
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }.toTypedArray()
            val coords = indices.map { index ->
                MotionEvent.PointerCoords().apply {
                    x = 230 + (if (index == 0) -span else span) - offset / 4
                    y = 400 - offset
                    pressure = 1f
                    size = 1f
                }
            }.toTypedArray()
            val event = MotionEvent.obtain(
                down, SystemClock.uptimeMillis(), action, indices.size, properties,
                coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
            )
            ui.onMain { view.dispatchTouchEvent(event) }
            event.recycle()
        }
        event(MotionEvent.ACTION_DOWN, 48f)
        event(
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            48f
        )
        repeat(12) {
            SystemClock.sleep(16)
            event(MotionEvent.ACTION_MOVE, 48 + (it + 1) * 5f)
        }
        val enlarged = ui.state(view)
        event(
            MotionEvent.ACTION_POINTER_UP or (lifted shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            108f
        )
        assertEquals(enlarged.getDouble("centerX"), ui.state(view).getDouble("centerX"), 0.000001)
        repeat(12) {
            SystemClock.sleep(16)
            event(MotionEvent.ACTION_MOVE, 108f, true, (it + 1) * 10f)
        }
        val scrolled = ui.state(view)
        assertEquals(enlarged.getDouble("centerX"), scrolled.getDouble("centerX"), 0.000001)
        assertTrue(scrolled.getDouble("centerY") > enlarged.getDouble("centerY"))
        event(MotionEvent.ACTION_UP, 108f, true, 120f)
        settle(view)
        assertEquals(scrolled.getDouble("centerY"), ui.state(view).getDouble("centerY"), 0.000001)
    }

    private fun icon(activity: Activity, viewer: PdfCanvasView): ImageButton {
        if (!activity.findViewById<View>(R.id.reader_tools).isShown) viewer.performClick()
        return activity.findViewById(R.id.horizontal_pan_lock)
    }

    private fun withDocuments(test: (Uri, Uri) -> Unit) {
        val files = List(2) { File.createTempFile("pan-lock-", ".pdf", application.cacheDir) }
        files.forEach(SyntheticDocument::write)
        val uris = files.map(Uri::fromFile)
        try {
            test(uris[0], uris[1])
        } finally {
            library { store -> uris.forEach { store.remove(it.toString()) } }
            files.forEach(File::delete)
        }
    }

    private fun capture(activity: Activity, name: String) {
        instrumentation.waitForIdleSync()
        val bitmap = ui.onMain {
            val root = activity.window.decorView
            Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also {
                root.draw(Canvas(it))
            }
        }
        try {
            File(activity.cacheDir, name).outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
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
