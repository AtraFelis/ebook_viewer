package dev.example.ebookviewer

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic fixtures only; these checks never inspect the user's document list or PDF pixels. */
class PageScrubberDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation
    private val ui get() = DeviceGestures(instrumentation)
    private val library get() =
        (instrumentation.targetContext.applicationContext as ReaderApplication).library

    @Test
    fun dragOnlyPreviewsThenCommitsOnceWithZoomAndHorizontalAnchorPreserved() {
        val file = File.createTempFile(
            "slider-fixture-",
            ".pdf",
            instrumentation.targetContext.cacheDir
        )
        SyntheticDocument.write(file)
        val uri = Uri.fromFile(file)
        val activity = ui.launch()
        try {
            ui.onMain { (activity as MainActivity).openDocument(uri) }
            val viewer = ui.awaitReady(activity)
            awaitCatalog(viewer)
            ui.pinch(viewer)
            ui.onMain { viewer.performClick() }
            ui.onMain { invoke(activity, "saveReading") }
            val saved = record(uri)
            val before = ui.state(viewer)
            var moves = 0
            ui.onMain {
                val callback = viewer.onPositionChanged
                viewer.onPositionChanged = {
                    moves++
                    callback?.invoke()
                }
            }
            val scrubber = activity.findViewById<PageScrubberView>(R.id.page_scrubber)
            touch(scrubber, MotionEvent.ACTION_DOWN, 0.3)
            touch(scrubber, MotionEvent.ACTION_MOVE, 72.0 / 99)
            assertEquals(72, ui.onMain { scrubber.previewPage })
            assertEquals(before.getDouble("centerY"), ui.state(viewer).getDouble("centerY"), 0.0)
            assertEquals(saved, record(uri))
            assertEquals(0, ui.onMain { moves })
            val label = activity.findViewById<TextView>(R.id.reader_page_count)
            assertTrue(ui.onMain { label.text.toString().contains("73") })
            touch(scrubber, MotionEvent.ACTION_UP, 72.0 / 99)
            ui.await("Slider did not navigate to the confirmed page") {
                viewer.readingPosition()?.page == 72
            }
            val after = ui.state(viewer)
            assertEquals(before.getDouble("zoom"), after.getDouble("zoom"), 0.0)
            assertEquals(before.getDouble("centerX"), after.getDouble("centerX"), 0.0)
            assertEquals(1, ui.onMain { moves })
            assertEquals(72, record(uri)?.page)
            assertTrue(ui.onMain { activity.findViewById<View>(R.id.reader_tools).isShown })
            touch(scrubber, MotionEvent.ACTION_DOWN, 72.0 / 99)
            touch(scrubber, MotionEvent.ACTION_UP, 72.0 / 99)
            assertEquals(1, ui.onMain { moves })
            ui.assertCacheBound(viewer)
            // Capture only this known synthetic document for local layout inspection.
            ui.onMain {
                val root = viewer.parent as View
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                try {
                    root.draw(Canvas(bitmap))
                    File(activity.cacheDir, "design-slider-synthetic.png").outputStream().use {
                        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                } finally {
                    bitmap.recycle()
                }
            }
        } finally {
            ui.onMain { activity.finish() }
            val done = CountDownLatch(1)
            library.execute({ it.remove(uri.toString()) }) { done.countDown() }
            assertTrue(done.await(10, TimeUnit.SECONDS))
            file.delete()
        }
    }

    @Test
    fun backCancelSecondPointerPauseAndDocumentReplacementDiscardPreview() {
        val activity = ui.launch()
        try {
            val viewer = ui.sample(activity)
            awaitCatalog(viewer)
            ui.onMain { viewer.performClick() }
            val scrubber = activity.findViewById<PageScrubberView>(R.id.page_scrubber)
            val before = requireNotNull(ui.onMain { viewer.readingPosition() })
            for (reason in listOf("cancel", "back", "pointer", "pause", "focus")) {
                touch(scrubber, MotionEvent.ACTION_DOWN, 0.8)
                assertNotNull(ui.onMain { scrubber.previewPage })
                ui.onMain {
                    when (reason) {
                        "back" -> invoke(activity, "handleBack")
                        "pause" -> {
                            instrumentation.callActivityOnPause(activity)
                            instrumentation.callActivityOnResume(activity)
                        }
                        "focus" -> activity.onWindowFocusChanged(false)
                    }
                }
                if (reason == "cancel") touch(scrubber, MotionEvent.ACTION_CANCEL, 0.8)
                if (reason == "pointer") {
                    // Full Activity dispatch catches pointers even if split to another child.
                    ui.onMain { secondPointer(activity, scrubber) }
                }
                touch(scrubber, MotionEvent.ACTION_UP, 0.8)
                assertNull(ui.onMain { scrubber.previewPage })
                assertEquals(before, ui.onMain { viewer.readingPosition() })
                assertTrue(ui.onMain { activity.findViewById<View>(R.id.reader_tools).isShown })
            }
            touch(scrubber, MotionEvent.ACTION_DOWN, 0.8)
            ui.sample(activity)
            assertNull(ui.onMain { scrubber.previewPage })
            touch(scrubber, MotionEvent.ACTION_UP, 0.8)
            assertEquals(0, ui.onMain { viewer.readingPosition()?.page })
        } finally {
            ui.onMain { activity.finish() }
        }
    }

    @Test
    fun recreationNeverRestoresUnconfirmedTarget() {
        var activity = ui.launch()
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        try {
            var viewer = ui.sample(activity)
            awaitCatalog(viewer)
            ui.pinch(viewer)
            ui.onMain { viewer.performClick() }
            val before = requireNotNull(ui.onMain { viewer.readingPosition() })
            touch(activity.findViewById(R.id.page_scrubber), MotionEvent.ACTION_DOWN, 0.8)
            val previous = activity
            ui.onMain { activity.recreate() }
            ui.await("Activity did not recreate") {
                val next = monitor.lastActivity
                if (next != null && next !== previous && !next.isDestroyed) activity = next
                activity !== previous && activity.window.decorView.hasWindowFocus()
            }
            viewer = ui.awaitReady(activity)
            assertEquals(before.page, ui.onMain { viewer.readingPosition()?.page })
            assertEquals(before.zoom, ui.state(viewer).getDouble("zoom"), 0.000001)
            assertNull(
                ui.onMain {
                    activity.findViewById<PageScrubberView>(R.id.page_scrubber).previewPage
                }
            )
        } finally {
            instrumentation.removeMonitor(monitor)
            ui.onMain { activity.finish() }
        }
    }

    @Test
    fun pendingNavigationShowsActualPositionAndBlocksDuplicateCommit() {
        val activity = ui.launch()
        try {
            val viewer = ui.sample(activity)
            awaitCatalog(viewer)
            ui.pinch(viewer)
            ui.onMain { viewer.performClick() }
            // Model the real progressive catalog: expected count is known, only four sizes are ready.
            ui.onMain { viewer.setPages(List(4) { PageSize(600, 800) }) }
            val scrubber = activity.findViewById<PageScrubberView>(R.id.page_scrubber)
            touch(scrubber, MotionEvent.ACTION_DOWN, 1.0)
            touch(scrubber, MotionEvent.ACTION_UP, 1.0)
            assertFalse(ui.onMain { scrubber.isEnabled })
            assertTrue(ui.onMain { viewer.readingPosition()?.page != 99 })
            touch(scrubber, MotionEvent.ACTION_DOWN, 0.3)
            touch(scrubber, MotionEvent.ACTION_UP, 0.3)
            assertNull(ui.onMain { scrubber.previewPage })
            ui.onMain {
                viewer.setPages(List(100) { PageSize(600, 800) })
                invoke(activity, "applyPendingNavigation")
            }
            assertEquals(99, ui.onMain { viewer.readingPosition()?.page })
            assertTrue(ui.onMain { scrubber.isEnabled })
        } finally {
            ui.onMain { activity.finish() }
        }
    }

    @Test
    fun singlePageUnknownRangeAndWindowExitNeverNavigate() {
        ui.onMain {
            var commits = 0
            val view = PageScrubberView(instrumentation.targetContext, {}, { commits++ })
            view.layout(0, 0, 400, 48)
            for ((page, count) in listOf(null to 0, 0 to 1)) {
                view.update(page, count, true)
                assertFalse(view.isEnabled)
                touch(view, MotionEvent.ACTION_DOWN, 0.8)
                touch(view, MotionEvent.ACTION_UP, 0.8)
            }
            view.update(0, 100, true)
            touch(view, MotionEvent.ACTION_DOWN, 0.8)
            // A raw pointer outside the app window cancels rather than clamping to a page.
            val outside = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 500f, 24f, 0)
            try {
                view.dispatchTouchEvent(outside)
            } finally {
                outside.recycle()
            }
            touch(view, MotionEvent.ACTION_UP, 0.8)
            assertNull(view.previewPage)
            assertEquals(0, commits)
        }
    }

    private fun awaitCatalog(viewer: PdfCanvasView) {
        val field = PdfCanvasView::class.java.getDeclaredField("layout").apply {
            isAccessible = true
        }
        ui.await("Synthetic page catalog did not finish") {
            (field.get(viewer) as? DocumentLayout)?.pages?.size == 100
        }
    }

    private fun invoke(activity: Activity, name: String) {
        MainActivity::class.java.getDeclaredMethod(name).apply {
            isAccessible = true
        }.invoke(activity)
    }

    private fun record(uri: Uri): ReadingPosition? {
        val done = CountDownLatch(1)
        var position: Result<ReadingPosition?>? = null
        library.execute({ store ->
            store.list().find { it.uri == uri.toString() }?.position
        }) {
            position = it
            done.countDown()
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        return requireNotNull(position).getOrThrow()
    }

    private fun touch(view: View, action: Int, fraction: Double) {
        val dispatch = {
            val inset = 9 * view.resources.displayMetrics.density
            val origin = IntArray(2)
            val rootOrigin = IntArray(2)
            val root = view.rootView
            view.getLocationOnScreen(origin)
            root.getLocationOnScreen(rootOrigin)
            val event = MotionEvent.obtain(
                SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(),
                action,
                origin[0] + (inset + (view.width - inset * 2) * fraction).toFloat(),
                origin[1] + view.height / 2f,
                0
            )
            try {
                event.offsetLocation(-rootOrigin[0].toFloat(), -rootOrigin[1].toFloat())
                root.dispatchTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            dispatch()
        } else {
            ui.onMain(dispatch)
        }
    }

    private fun secondPointer(activity: Activity, view: View) {
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val properties = Array(2) { index -> MotionEvent.PointerProperties().apply { id = index } }
        val coordinates = Array(2) { index ->
            MotionEvent.PointerCoords().apply {
                x = location[0] + view.width * 0.8f
                y = if (index == 0) location[1] + view.height / 2f else 100f
            }
        }
        val event = MotionEvent.obtain(
            0, 0, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2, properties, coordinates, 0, 0, 1f, 1f, 0, 0, 0, 0
        )
        try {
            activity.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }
}
