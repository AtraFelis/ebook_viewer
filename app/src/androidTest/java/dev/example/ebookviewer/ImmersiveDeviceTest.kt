package dev.example.ebookviewer

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation
    private val ui get() = DeviceGestures(instrumentation)

    @Test
    fun outsideTapReachesPdfThroughOverlayAndDoesNotReopenHiddenTools() {
        val activity = ui.launch()
        try {
            val view = ui.sample(activity)
            ui.pinch(view)
            val root = ui.onMain { view.parent as View }
            val before = ui.state(view)
            val dimensions = ui.onMain { view.width to view.height }
            ui.tap(root)
            assertTools(activity, true)
            val y = blankY(activity, root)
            val bitmap = ui.onMain {
                android.graphics.Bitmap.createBitmap(
                    root.width,
                    root.height,
                    android.graphics.Bitmap.Config.ARGB_8888
                ).also { root.draw(android.graphics.Canvas(it)) }
            }
            try {
                // Only this synthetic PDF screen is captured, never the user's library or document.
                File(activity.cacheDir, "design-tools-synthetic.png").outputStream().use {
                    assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally {
                bitmap.recycle()
            }
            ui.tap(root, 0.1f, y)
            assertTools(activity, false)
            assertAnchor(before, ui.state(view))
            assertEquals(dimensions, ui.onMain { view.width to view.height })
            ui.tap(root, 0.1f, y)
            assertTools(activity, false)
            ui.tap(root)
            assertTools(activity, true)
            ui.onMain { activity.findViewById<View>(R.id.reader_home).performClick() }
            ui.await("Home icon did not return to library") {
                activity.findViewById<View>(R.id.home_screen).isShown
            }
        } finally {
            ui.onMain { activity.finish() }
        }
    }

    @Test
    fun overlayAllowsPdfGesturesAndOnlyBlankSingleTapClosesTools() {
        val activity = ui.launch()
        try {
            val view = ui.sample(activity)
            val root = ui.onMain { view.parent as View }
            ui.tap(root)
            assertTools(activity, true)
            val before = ui.state(view)
            // The real root dispatch path must pass the two-pointer gesture through its empty middle.
            ui.pinch(root)
            assertTrue(ui.state(view).getDouble("zoom") > before.getDouble("zoom"))
            assertTools(activity, true)
            val afterPinch = ui.state(view)
            val y = blankY(activity, root) * root.height
            val down = SystemClock.uptimeMillis()
            val step = 16 * activity.resources.displayMetrics.density
            for ((index, action) in listOf(
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP
            ).withIndex()) {
                val event = MotionEvent.obtain(
                    down,
                    SystemClock.uptimeMillis(),
                    action,
                    root.width * 0.5f,
                    y - index.coerceAtMost(4) * step,
                    0
                )
                ui.onMain { root.dispatchTouchEvent(event) }
                event.recycle()
                SystemClock.sleep(100)
            }
            assertTools(activity, true)
            val afterScroll = ui.state(view)
            assertTrue(
                "Scroll did not move PDF: ${afterPinch.getDouble("centerY")} " +
                    "to ${afterScroll.getDouble("centerY")}",
                afterScroll.getDouble("centerY") > afterPinch.getDouble("centerY")
            )
            val header = activity.findViewById<View>(R.id.reader_header)
            val rect = android.graphics.Rect()
            ui.onMain { header.getGlobalVisibleRect(rect) }
            ui.tap(root, 0.95f, (rect.centerY() - root.top) / root.height.toFloat())
            assertTools(activity, false)
            ui.assertCacheBound(view)
        } finally {
            ui.onMain { activity.finish() }
        }
    }

    private fun blankY(activity: Activity, root: View): Float = ui.onMain {
        val header = activity.findViewById<View>(R.id.reader_header)
        val footerScroll = activity.findViewById<View>(R.id.reader_footer).parent as View
        val tools = activity.findViewById<View>(R.id.reader_tools)
        assertTrue("Overlay has no document gap", footerScroll.top > header.bottom)
        (tools.top + (header.bottom + footerScroll.top) / 2f) / root.height
    }

    @Test
    fun centralTapOverlaysToolsWithoutResizingOrMovingPdf() {
        val activity = ui.launch()
        try {
            val view = ui.sample(activity)
            ui.pinch(view)
            repeat(3) { ui.drag(view, true) }
            assertImmersive(activity)
            val before = ui.state(view)
            val width = view.width
            val height = view.height
            ui.tap(view)
            assertTools(activity, true)
            assertEquals(width, view.width)
            assertEquals(height, view.height)
            assertAnchor(before, ui.state(view))
            ui.tap(view)
            assertTools(activity, false)
            assertAnchor(before, ui.state(view))
            ui.tap(view)
            assertTools(activity, true)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            ui.await("Back did not hide tools") {
                !activity.findViewById<View>(R.id.reader_tools).isShown
            }
            assertAnchor(before, ui.state(view))
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            ui.await("Back did not return to document list") {
                activity.findViewById<View>(R.id.home_screen).isShown
            }
            if (Build.VERSION.SDK_INT >= 30) {
                ui.await("Home system bars not restored") {
                    activity.window.decorView.rootWindowInsets?.isVisible(
                        WindowInsets.Type.statusBars()
                    ) ==
                        true
                }
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test
    fun pinchScrollOutsideTapAndFlingStopDoNotOpenTools() {
        val activity = ui.launch()
        try {
            val view = ui.sample(activity)
            ui.pinch(view)
            assertTools(activity, false)
            ui.drag(view, true)
            assertTools(activity, false)
            ui.tap(view, 0.1f, 0.8f)
            assertTools(activity, false)
            ui.drag(view, true, fling = true)
            ui.tap(view)
            assertTools(activity, false)
            ui.tap(view)
            assertTools(activity, true)
            ui.assertCacheBound(view)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test
    fun orientationSettingsPreserveAnchorAndSurviveRelaunch() {
        val application = instrumentation.targetContext.applicationContext as ReaderApplication
        val repository = application.settings
        val previousMode = setting { repository.load(it) }
        setting { repository.save(OrientationMode.AUTO, it) }
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        var activity = ui.launch()
        try {
            var view = ui.sample(activity)
            ui.pinch(view)
            ui.pinch(view)
            repeat(4) { ui.drag(view, true) }
            val before = ui.state(view)
            ui.tap(view)
            clickSettings(activity)
            clickDialogText(activity.getString(R.string.orientation_landscape))
            activity =
                awaitOrientation(activity, monitor, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            view = ui.awaitReady(activity)
            assertAnchor(before, ui.state(view))
            assertImmersive(activity)
            assertEquals(OrientationMode.LANDSCAPE, setting { repository.load(it) })
            ui.tap(view)
            clickSettings(activity)
            clickDialogText(activity.getString(R.string.orientation_portrait))
            activity = awaitOrientation(activity, monitor, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            view = ui.awaitReady(activity)
            assertAnchor(before, ui.state(view))
            assertImmersive(activity)
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
            activity = ui.launch()
            activity = awaitOrientation(activity, monitor, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            assertTrue(activity.findViewById<View>(R.id.home_screen).isShown)
            clickSettings(activity)
            clickDialogText(activity.getString(R.string.orientation_auto))
            activity =
                awaitOrientation(activity, monitor, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
            assertEquals(OrientationMode.AUTO, setting { repository.load(it) })
        } finally {
            val latest = monitor.lastActivity ?: activity
            instrumentation.runOnMainSync {
                latest.finish()
                activity.finish()
            }
            instrumentation.removeMonitor(monitor)
            setting { repository.save(previousMode, it) }
        }
    }

    @Test
    fun reverseDirectionsRotateTheDisplayAndKeepAnchorAndSavedChoice() {
        val application = instrumentation.targetContext.applicationContext as ReaderApplication
        val repository = application.settings
        val previousMode = setting { repository.load(it) }
        setting { repository.save(OrientationMode.PORTRAIT, it) }
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        var activity = ui.launch()
        try {
            activity = awaitOrientation(activity, monitor, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            val portraitRotation = ui.onMain { rotation(activity) }
            var view = ui.sample(activity)
            repeat(2) { ui.pinch(view) }
            repeat(2) { ui.drag(view, true) }
            val before = ui.state(view)
            ui.tap(view)
            clickSettings(activity)
            clickDialogText(activity.getString(R.string.orientation_reverse_portrait))
            activity = awaitOrientation(
                activity,
                monitor,
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            )
            ui.await("Reverse portrait did not rotate the display by 180 degrees") {
                rotation(activity) == (portraitRotation + 2) % 4
            }
            view = ui.awaitReady(activity)
            assertAnchor(before, ui.state(view))
            assertImmersive(activity)
            assertEquals(OrientationMode.REVERSE_PORTRAIT, setting { repository.load(it) })
            ui.tap(view)
            clickSettings(activity)
            clickDialogText(activity.getString(R.string.orientation_landscape))
            activity =
                awaitOrientation(activity, monitor, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            view = ui.awaitReady(activity)
            assertAnchor(before, ui.state(view))
            val landscapeRotation = ui.onMain { rotation(activity) }
            ui.tap(view)
            clickSettings(activity)
            clickDialogText(activity.getString(R.string.orientation_reverse_landscape))
            activity = awaitOrientation(
                activity,
                monitor,
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            )
            ui.await("Reverse landscape did not rotate the display by 180 degrees") {
                rotation(activity) == (landscapeRotation + 2) % 4
            }
            view = ui.awaitReady(activity)
            assertAnchor(before, ui.state(view))
            assertImmersive(activity)
            assertEquals(OrientationMode.REVERSE_LANDSCAPE, setting { repository.load(it) })
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
            activity = ui.launch()
            activity = awaitOrientation(
                activity,
                monitor,
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            )
            ui.await("Saved reverse direction was not restored") {
                rotation(activity) == (landscapeRotation + 2) % 4
            }
        } finally {
            val latest = monitor.lastActivity ?: activity
            instrumentation.runOnMainSync {
                latest.finish()
                activity.finish()
            }
            instrumentation.removeMonitor(monitor)
            setting { repository.save(previousMode, it) }
        }
    }

    @Suppress("DEPRECATION") // Public API retained for the minSdk 23 display rotation check.
    private fun rotation(activity: Activity): Int = activity.windowManager.defaultDisplay.rotation

    @Test
    fun settingsRoundTripFromDiskAndUnknownVersionIsPreserved() {
        val file = File.createTempFile(
            "settings-fixture-",
            ".bin",
            instrumentation.targetContext.cacheDir
        )
        file.delete()
        try {
            ReaderSettingsRepository { file }.use { first ->
                assertEquals(OrientationMode.AUTO, setting { first.load(it) })
            }
            for (mode in OrientationMode.entries) {
                ReaderSettingsRepository { file }.use { writer ->
                    setting { writer.save(mode, it) }
                }
                ReaderSettingsRepository { file }.use { reopened ->
                    assertEquals(mode, setting { reopened.load(it) })
                }
            }
            file.outputStream().use {
                DataOutputStream(it).apply {
                    writeInt(0x45565331)
                    writeInt(99)
                }
            }
            val previousBytes = file.readBytes()
            ReaderSettingsRepository { file }.use { unknown ->
                val done = CountDownLatch(1)
                var failed = false
                unknown.save(OrientationMode.PORTRAIT) {
                    failed = it.isFailure
                    done.countDown()
                }
                assertTrue(done.await(10, TimeUnit.SECONDS))
                assertTrue(failed)
            }
            assertTrue(previousBytes.contentEquals(file.readBytes()))
        } finally {
            file.delete()
        }
    }

    private fun clickSettings(activity: Activity) {
        instrumentation.runOnMainSync {
            ui.views(activity.window.decorView).filterIsInstance<Button>()
                .single {
                    it.isShown && it.text == activity.getString(R.string.settings)
                }.performClick()
        }
        clickDialogText(activity.getString(R.string.orientation_settings))
    }

    private fun clickDialogText(text: String) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        var forward = true
        while (SystemClock.uptimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val node = root?.findAccessibilityNodeInfosByText(text)?.firstOrNull {
                it.packageName == instrumentation.targetContext.packageName &&
                    it.text?.toString() == text
            }
            if (node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return
            // Five choices may need scrolling in landscape; exercise the same list a user sees.
            if (root != null && !scrollDialogList(root, forward)) forward = !forward
            SystemClock.sleep(50)
        }
        throw AssertionError("Settings dialog item not found")
    }

    private fun scrollDialogList(node: AccessibilityNodeInfo, forward: Boolean): Boolean {
        if (node.className == "android.widget.ListView") {
            return node.performAction(
                if (forward) {
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                } else {
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                }
            )
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            if (scrollDialogList(child, forward)) return true
        }
        return false
    }

    private fun awaitOrientation(
        initial: Activity,
        monitor: android.app.Instrumentation.ActivityMonitor,
        requested: Int
    ): Activity {
        var latest = initial
        val target = when (requested) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE -> Configuration.ORIENTATION_LANDSCAPE
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT -> Configuration.ORIENTATION_PORTRAIT
            else -> null
        }
        val recreate = target != null && initial.resources.configuration.orientation != target
        var stableSince = SystemClock.uptimeMillis()
        var previous = initial
        ui.await("Orientation setting was not applied") {
            latest = monitor.lastActivity ?: initial
            val decor = latest.window.decorView
            val aspectMatches = when (target) {
                Configuration.ORIENTATION_LANDSCAPE -> decor.width > decor.height
                Configuration.ORIENTATION_PORTRAIT -> decor.height > decor.width
                else -> true
            }
            val ready =
                !latest.isDestroyed &&
                    !latest.isFinishing &&
                    decor.hasWindowFocus() &&
                    aspectMatches &&
                    (!recreate || latest !== initial) &&
                    latest.requestedOrientation == requested &&
                    (target == null || latest.resources.configuration.orientation == target)
            if (!ready || previous !== latest) stableSince = SystemClock.uptimeMillis()
            previous = latest
            ready && SystemClock.uptimeMillis() - stableSince >= 250
        }
        return latest
    }

    private fun assertImmersive(activity: Activity) {
        assertTools(activity, false)
        ui.onMain {
            assertFalse(activity.findViewById<View>(R.id.home_screen).isShown)
            assertTrue(
                ui.views(activity.window.decorView).filterIsInstance<Button>().none {
                    it.isShown
                }
            )
        }
        if (Build.VERSION.SDK_INT >= 30) {
            ui.await("Reading system bars not hidden") {
                val insets = activity.window.decorView.rootWindowInsets
                insets != null &&
                    !insets.isVisible(WindowInsets.Type.statusBars()) &&
                    !insets.isVisible(WindowInsets.Type.navigationBars())
            }
        }
    }

    private fun assertTools(activity: Activity, expected: Boolean) {
        ui.onMain {
            assertEquals(expected, activity.findViewById<View>(R.id.reader_tools).isShown)
        }
    }

    private fun assertAnchor(before: android.os.Bundle, after: android.os.Bundle) {
        assertEquals(before.getDouble("zoom"), after.getDouble("zoom"), 0.000001)
        assertEquals(before.getDouble("centerX"), after.getDouble("centerX"), 2.0)
        assertEquals(before.getDouble("centerY"), after.getDouble("centerY"), 2.0)
    }

    private fun setting(action: ((Result<OrientationMode>) -> Unit) -> Unit): OrientationMode {
        val done = CountDownLatch(1)
        var result: Result<OrientationMode>? = null
        action {
            result = it
            done.countDown()
        }
        assertTrue("Settings IO timed out", done.await(10, TimeUnit.SECONDS))
        return requireNotNull(result).getOrThrow()
    }
}
