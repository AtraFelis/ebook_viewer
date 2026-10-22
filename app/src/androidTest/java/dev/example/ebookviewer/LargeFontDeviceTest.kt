package dev.example.ebookviewer

import android.app.Activity
import android.app.AlertDialog
import android.content.res.Configuration
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.EditText
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test

/** Temporarily changes font scale only; always restores the user's original setting. */
class LargeFontDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation
    private val ui get() = DeviceGestures(instrumentation)

    @Test
    fun homeToolsAndSettingsRemainReachableAtThreeFontScalesAndBothOrientations() {
        val original = Settings.System.getString(
            instrumentation.targetContext.contentResolver,
            Settings.System.FONT_SCALE
        )
        val application = instrumentation.targetContext.applicationContext as ReaderApplication
        val settings = application.settings
        val originalOrientation = setting<OrientationMode> { settings.load(it) }
        var activity: Activity? = null
        try {
            for (scale in listOf(1f, 1.5f, 2f)) {
                shell("settings put system font_scale $scale")
                for (landscape in listOf(false, true)) {
                    val mode = if (landscape) {
                        OrientationMode.LANDSCAPE
                    } else {
                        OrientationMode.PORTRAIT
                    }
                    setting<OrientationMode> { settings.save(mode, it) }
                    activity = ui.launch()
                    val monitor = instrumentation.addMonitor(
                        MainActivity::class.java.name,
                        null,
                        false
                    )
                    try {
                        var stableSince = SystemClock.uptimeMillis()
                        ui.await("Font/orientation configuration did not settle") {
                            val latest = monitor.lastActivity ?: activity
                            if (latest !== activity) stableSince = SystemClock.uptimeMillis()
                            activity = latest
                            val config = requireNotNull(activity).resources.configuration
                            val expected = if (landscape) {
                                Configuration.ORIENTATION_LANDSCAPE
                            } else {
                                Configuration.ORIENTATION_PORTRAIT
                            }
                            config.fontScale == scale &&
                                config.orientation == expected &&
                                requireNotNull(activity).window.decorView.hasWindowFocus() &&
                                SystemClock.uptimeMillis() - stableSince >= 750
                        }
                        try {
                            checkActivity(requireNotNull(activity))
                        } catch (failure: AssertionError) {
                            throw AssertionError(
                                "font=$scale landscape=$landscape " +
                                    "destroyed=${activity?.isDestroyed} " +
                                    "recreated=${monitor.lastActivity !== activity}",
                                failure
                            )
                        }
                    } finally {
                        instrumentation.removeMonitor(monitor)
                        ui.onMain { activity?.finish() }
                        activity = null
                    }
                }
            }
        } finally {
            ui.onMain { activity?.finish() }
            setting<OrientationMode> { settings.save(originalOrientation, it) }
            if (original == null) {
                shell("settings delete system font_scale")
            } else {
                // Settings provider supplies a number; do not interpolate arbitrary setting text.
                shell("settings put system font_scale ${original.toFloat()}")
            }
        }
    }

    private fun checkActivity(activity: Activity) {
        val home = activity.findViewById<View>(R.id.home_screen)
        checkControls(home, 3)
        val viewer = ui.sample(activity)
        ui.onMain { viewer.performClick() }
        val tools = activity.findViewById<View>(R.id.reader_tools)
        checkControls(tools)
        val menus = listOf(
            R.string.orientation_settings,
            R.string.display_settings,
            R.string.page_layout_settings,
            R.string.page_crop_settings,
            R.string.background_settings
        )
        for (label in menus) {
            val settings = ui.onMain {
                ui.views(tools).filterIsInstance<Button>().single {
                    it.text == activity.getString(R.string.settings)
                }
            }
            reveal(settings)
            ui.onMain { settings.performClick() }
            var dialog: AlertDialog? = null
            ui.await("Settings dialog missing") {
                dialog = settingsDialog(activity)
                dialog?.isShowing == true
            }
            checkControls(requireNotNull(requireNotNull(dialog).window).decorView)
            val menu = ui.onMain {
                ui.views(requireNotNull(requireNotNull(dialog).window).decorView)
                    .filterIsInstance<Button>()
                    .single { it.text == activity.getString(label) }
            }
            reveal(menu)
            ui.onMain { menu.performClick() }
            val previous = dialog
            ui.await("Settings submenu missing") {
                dialog = settingsDialog(activity)
                dialog !== previous && dialog?.isShowing == true
            }
            try {
                checkControls(requireNotNull(requireNotNull(dialog).window).decorView)
            } catch (failure: AssertionError) {
                throw AssertionError("Settings section=${activity.getString(label)}", failure)
            }
            ui.onMain { requireNotNull(dialog).dismiss() }
            ui.await("Subsettings did not return to parent") {
                settingsDialog(activity) === previous
            }
            ui.onMain { requireNotNull(previous).dismiss() }
            instrumentation.waitForIdleSync()
            ui.onMain { viewer.performClick() }
        }
        ui.onMain { viewer.performAccessibilityAction(R.id.read_page_text, null) }
        var body: AlertDialog? = null
        ui.await("Body dialog missing") {
            val field = MainActivity::class.java.getDeclaredField("pageTextDialog")
            field.isAccessible = true
            body = field.get(activity) as AlertDialog?
            body?.isShowing == true
        }
        checkControls(requireNotNull(requireNotNull(body).window).decorView)
        ui.onMain { requireNotNull(body).dismiss() }
    }

    private fun settingsDialog(activity: Activity): AlertDialog? {
        val field = MainActivity::class.java.getDeclaredField("settingsDialog")
        field.isAccessible = true
        return field.get(activity) as AlertDialog?
    }

    private fun checkControls(root: View, limit: Int = Int.MAX_VALUE) {
        val controls = ui.onMain {
            ui.views(root).filter {
                it.isShown &&
                    (
                        it is Button ||
                            it is CheckedTextView ||
                            it is EditText ||
                            it is android.widget.SeekBar ||
                            it.id == R.id.home_record ||
                            it.id == R.id.reader_home ||
                            it.id == R.id.horizontal_pan_lock ||
                            it.id == R.id.reader_orientation ||
                            it.id == R.id.page_scrubber
                        )
            }.take(limit)
        }
        assertTrue("No actionable controls", controls.isNotEmpty())
        for (control in controls) reveal(control)
        for (row in controls.filterIsInstance<CheckedTextView>()) {
            ui.onMain {
                val textHeight = row.height - row.compoundPaddingTop - row.compoundPaddingBottom
                assertTrue(
                    "Orientation text clipped",
                    textHeight >= requireNotNull(row.layout).height
                )
            }
        }
    }

    private fun reveal(view: View) {
        ui.onMain {
            view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true)
        }
        instrumentation.waitForIdleSync()
        ui.await("Control clipped or unreachable at configured font size") {
            val bounds = Rect()
            val visible = view.getGlobalVisibleRect(bounds)
            visible && bounds.height() >= view.height && bounds.width() >= view.width
        }
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)
        ).use { it.readBytes() }
        instrumentation.waitForIdleSync()
    }

    private fun <T> setting(action: ((Result<T>) -> Unit) -> Unit): T {
        val done = CountDownLatch(1)
        var result: Result<T>? = null
        action {
            result = it
            done.countDown()
        }
        assertTrue("Orientation setting operation timed out", done.await(5, TimeUnit.SECONDS))
        return requireNotNull(result).getOrThrow()
    }
}
