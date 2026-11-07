package dev.example.ebookviewer

import android.content.pm.ActivityInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GestureDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation
    private val application
        get() = instrumentation.targetContext.applicationContext as ReaderApplication
    private val settings get() = application.settings
    private var previousOrientation: OrientationMode? = null

    @Before
    fun allowTestControlledRotation() {
        previousOrientation = setting(settings::load)
        // A persisted fixed mode otherwise overrides requestedOrientation after recreation.
        setting { settings.save(OrientationMode.AUTO, it) }
    }

    @After
    fun restoreUserOrientation() {
        previousOrientation?.let { previous -> setting { settings.save(previous, it) } }
    }

    private fun setting(action: ((Result<OrientationMode>) -> Unit) -> Unit): OrientationMode {
        val done = CountDownLatch(1)
        var result: Result<OrientationMode>? = null
        action {
            result = it
            done.countDown()
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        return requireNotNull(result).getOrThrow()
    }

    @Test
    fun repeatedPinchesReachLargerZoomAndStopExcessiveReduction() {
        val instrumentation = PdfRendererDeviceTest.instrumentation
        val ui = DeviceGestures(instrumentation)
        val activity = ui.launch()
        try {
            val view = ui.sample(activity)
            // SyntheticDocument alternates 600pt and 800pt wide pages.
            val documentWidth = 800.0
            repeat(8) { ui.pinch(view) }
            assertEquals(32.0, ui.state(view).getDouble("zoom"), 0.000001)
            ui.awaitReady(activity)
            ui.assertCacheBound(view)
            repeat(12) { ui.pinch(view, outward = false) }
            val minimum = ZoomLimits.gestureMinimum(documentWidth, view.width, view.height)
            assertEquals(minimum, ui.state(view).getDouble("zoom"), 0.000001)
            ui.pinch(view, outward = false)
            assertEquals(minimum, ui.state(view).getDouble("zoom"), 0.000001)
            ui.awaitReady(activity)
            ui.assertCacheBound(view)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test
    fun pinchAndTwentyPageRoundTripPreserveZoom() {
        val instrumentation = PdfRendererDeviceTest.instrumentation
        val ui = DeviceGestures(instrumentation)
        setting { settings.save(OrientationMode.LANDSCAPE, it) }
        var activity = ui.launch()
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        try {
            instrumentation.runOnMainSync { (activity as MainActivity).openSample() }
            ui.await("Reader landscape did not settle") {
                activity = monitor.lastActivity ?: activity
                !activity.isDestroyed &&
                    activity.hasWindowFocus() &&
                    activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE &&
                    activity.resources.configuration.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
            }
            val view = ui.awaitReady(activity)
            val initial = ui.state(view).getDouble("zoom")
            ui.pinch(view)
            val zoom = ui.state(view).getDouble("zoom")
            assertTrue("Pinch did not zoom in", zoom > initial * 1.5)
            val startY = ui.state(view).getDouble("centerY")
            // A landscape viewport traverses fewer document points per swipe at the same zoom.
            val maximumDrags = ceil(14_000 * zoom / (view.height * 0.5)).toInt() + 20
            var drags = 0
            while (ui.state(view).getDouble("centerY") < startY + 14_000 && drags < maximumDrags) {
                ui.drag(view, true)
                drags++
                assertEquals(zoom, ui.state(view).getDouble("zoom"), 0.000001)
            }
            assertTrue(
                "Did not traverse twenty mixed pages",
                ui.state(view).getDouble("centerY") >= startY + 14_000
            )
            repeat(drags) {
                ui.drag(view, false)
                assertEquals(zoom, ui.state(view).getDouble("zoom"), 0.000001)
            }
            ui.awaitReady(activity)
            ui.assertCacheBound(view)
        } finally {
            instrumentation.runOnMainSync { (monitor.lastActivity ?: activity).finish() }
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun orientationChangePreservesUserZoomAndDocumentAnchor() {
        val instrumentation = PdfRendererDeviceTest.instrumentation
        val ui = DeviceGestures(instrumentation)
        var activity = ui.launch()
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        try {
            val view = ui.sample(activity)
            repeat(6) { ui.pinch(view) }
            repeat(4) { ui.drag(view, true) }
            val before = ui.state(view)
            val beforeMotion = ui.onMain { scrollFinished(view) }
            val beforeBounds = ui.onMain { "${view.width}x${view.height}" }
            assertTrue(before.getDouble("zoom") > 8.0)
            val requested = if (view.width <
                view.height
            ) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            // Use the actual reader choice; direct Activity requests are not persisted app settings.
            instrumentation.runOnMainSync {
                view.performClick()
                activity.findViewById<android.view.View>(R.id.reader_orientation).performClick()
            }
            var popup: android.widget.PopupWindow? = null
            ui.await("Reader direction menu did not open") {
                val field = MainActivity::class.java.getDeclaredField("orientationPopup")
                field.isAccessible = true
                popup = field.get(activity) as android.widget.PopupWindow?
                popup?.isShowing == true
            }
            val mode = if (requested == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                OrientationMode.LANDSCAPE
            } else {
                OrientationMode.PORTRAIT
            }
            instrumentation.runOnMainSync {
                (requireNotNull(popup).contentView as android.widget.ListView)
                    .performItemClick(null, mode.ordinal, mode.ordinal.toLong())
            }
            activity = requireNotNull(instrumentation.waitForMonitorWithTimeout(monitor, 10_000))
            val restored = ui.awaitReady(activity)
            val after = ui.state(restored)
            val diagnostic = ui.onMain {
                val beforeMode = before.getString("pageSizeMode")
                val afterMode = after.getString("pageSizeMode")
                "Synthetic rotation: bounds=$beforeBounds->${restored.width}x${restored.height}, " +
                    "scrollFinished=$beforeMotion->${scrollFinished(restored)}, " +
                    "page=${before.getInt("page")}->${after.getInt("page")}, " +
                    "mode=$beforeMode->$afterMode, " +
                    "gap=${before.getInt("pageGap")}->${after.getInt("pageGap")}, " +
                    "zoom=${before.getDouble("zoom")}->${after.getDouble("zoom")}, " +
                    "y=${before.getDouble("centerY")}->${after.getDouble("centerY")}, " +
                    "focus=${restored.hasWindowFocus()}"
            }
            assertEquals(diagnostic, before.getDouble("zoom"), after.getDouble("zoom"), 0.000001)
            assertEquals(diagnostic, before.getDouble("centerY"), after.getDouble("centerY"), 2.0)
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun scrollFinished(view: PdfCanvasView): Boolean {
        val field = PdfCanvasView::class.java.getDeclaredField("scroller")
        field.isAccessible = true
        return (field.get(view) as android.widget.OverScroller).isFinished
    }
}
