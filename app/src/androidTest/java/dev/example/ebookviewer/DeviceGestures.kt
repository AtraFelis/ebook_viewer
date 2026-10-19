package dev.example.ebookviewer

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import org.junit.Assert.assertTrue

class DeviceGestures(
    private val instrumentation: Instrumentation,
    private val keepScreenAwake: Boolean = true
) {
    fun views(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                addAll(
                    views(view.getChildAt(index))
                )
            }
        }
    }

    fun launch(): Activity {
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        try {
            var activity = instrumentation.startActivitySync(
                Intent(instrumentation.targetContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
            )
            keepAwake(activity)
            var stableSince = SystemClock.uptimeMillis()
            await("Activity did not settle after launch") {
                val latest = monitor.lastActivity ?: activity
                if (latest !== activity) {
                    activity = latest
                    stableSince = SystemClock.uptimeMillis()
                    if (keepScreenAwake) {
                        activity.window.addFlags(
                            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        )
                    }
                }
                !activity.isDestroyed &&
                    !activity.isFinishing &&
                    activity.window.decorView.hasWindowFocus() &&
                    SystemClock.uptimeMillis() - stableSince >= 250
            }
            return activity
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun keepAwake(activity: Activity) {
        if (!keepScreenAwake) return
        // Test-window lifetime only; do not alter the user's device timeout or app preference.
        instrumentation.runOnMainSync {
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun sample(activity: Activity): PdfCanvasView {
        instrumentation.runOnMainSync {
            views(activity.window.decorView).filterIsInstance<Button>()
                .single {
                    it.text.toString() == activity.getString(R.string.sample_pdf)
                }.performClick()
        }
        return awaitReady(activity)
    }

    fun awaitReady(activity: Activity): PdfCanvasView {
        keepAwake(activity)
        var viewer: PdfCanvasView? = null
        // Restoring a saved viewport scans the complete page catalog before rendering.
        await("Visible tiles not ready", timeoutMs = 30_000) {
            viewer = views(activity.window.decorView).filterIsInstance<PdfCanvasView>().single()
            val state = requireNotNull(viewer).renderState()
            state.visibleTiles > 0 && state.visibleTiles == state.readyTiles
        }
        return requireNotNull(viewer)
    }

    fun await(message: String, timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            onMain { ready = condition() }
            if (ready) return
            SystemClock.sleep(25)
        }
        throw AssertionError(message)
    }

    fun <T> onMain(action: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(action) }
        return requireNotNull(result).getOrThrow()
    }

    fun state(view: PdfCanvasView): Bundle {
        var result: Bundle? = null
        instrumentation.runOnMainSync { result = view.saveViewport() }
        return requireNotNull(result)
    }

    fun tap(view: PdfCanvasView, xFraction: Float = 0.5f, yFraction: Float = 0.5f) {
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(
                down,
                SystemClock.uptimeMillis(),
                action,
                view.width * xFraction,
                view.height * yFraction,
                0
            )
            instrumentation.runOnMainSync { view.dispatchTouchEvent(event) }
            event.recycle()
            SystemClock.sleep(25)
        }
    }

    fun drag(view: PdfCanvasView, forward: Boolean, fling: Boolean = false) {
        val down = SystemClock.uptimeMillis()
        val x = view.width * 0.5f
        val start = view.height * if (forward) 0.85f else 0.15f
        val end = view.height * if (forward) 0.15f else 0.85f
        fun event(action: Int, y: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            instrumentation.runOnMainSync { view.dispatchTouchEvent(event) }
            event.recycle()
        }
        event(MotionEvent.ACTION_DOWN, start)
        repeat(12) { index ->
            SystemClock.sleep(16)
            event(MotionEvent.ACTION_MOVE, start + (end - start) * (index + 1) / 12)
        }
        if (!fling) {
            SystemClock.sleep(150)
            event(MotionEvent.ACTION_MOVE, end)
        }
        event(MotionEvent.ACTION_UP, end)
    }

    fun pinch(view: PdfCanvasView, outward: Boolean = true) {
        val down = SystemClock.uptimeMillis()
        val cx = view.width / 2f
        val cy = view.height / 2f
        val small = view.width * 0.12f
        val large = view.width * 0.28f
        fun event(action: Int, span: Float, count: Int = 2) {
            val properties = Array(count) { index ->
                MotionEvent.PointerProperties().apply {
                    id = index
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }
            val coordinates = Array(count) { index ->
                MotionEvent.PointerCoords().apply {
                    x = cx + if (index == 0) -span else span
                    y = cy
                    pressure = 1f
                    size = 1f
                }
            }
            val event = MotionEvent.obtain(
                down, SystemClock.uptimeMillis(), action, count, properties,
                coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
            )
            instrumentation.runOnMainSync { view.dispatchTouchEvent(event) }
            event.recycle()
        }
        val start = if (outward) small else large
        val end = if (outward) large else small
        event(MotionEvent.ACTION_DOWN, start, 1)
        event(
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            start
        )
        repeat(12) { index ->
            SystemClock.sleep(16)
            event(MotionEvent.ACTION_MOVE, start + (end - start) * (index + 1) / 12)
        }
        event(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), end)
        event(MotionEvent.ACTION_UP, end, 1)
    }

    fun assertCacheBound(view: PdfCanvasView) {
        onMain {
            assertTrue("Cache exceeded 64 MiB", view.renderState().cachedBytes <= 64 * 1024 * 1024)
        }
    }
}
