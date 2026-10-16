package dev.example.ebookviewer

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureDeviceTest {
    @Test
    fun pinchAndTwentyPageRoundTripPreserveZoom() {
        val instrumentation = PdfRendererDeviceTest.instrumentation
        val ui = DeviceGestures(instrumentation)
        val activity = ui.launch()
        try {
            val view = ui.sample(activity)
            val initial = ui.state(view).getDouble("zoom")
            ui.pinch(view)
            val zoom = ui.state(view).getDouble("zoom")
            assertTrue("Pinch did not zoom in", zoom > initial * 1.5)
            val startY = ui.state(view).getDouble("centerY")
            var drags = 0
            while (ui.state(view).getDouble("centerY") < startY + 14_000 && drags < 80) {
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
            instrumentation.runOnMainSync { activity.finish() }
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
            ui.pinch(view)
            repeat(4) { ui.drag(view, true) }
            val before = ui.state(view)
            val requested = if (view.width <
                view.height
            ) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            instrumentation.runOnMainSync { activity.requestedOrientation = requested }
            activity = requireNotNull(instrumentation.waitForMonitorWithTimeout(monitor, 10_000))
            val restored = ui.awaitReady(activity)
            val after = ui.state(restored)
            assertEquals(before.getDouble("zoom"), after.getDouble("zoom"), 0.000001)
            assertEquals(before.getDouble("centerY"), after.getDouble("centerY"), 2.0)
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
