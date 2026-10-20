package dev.example.ebookviewer

import android.app.Activity
import android.app.AlertDialog
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.EditText
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNavigationDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation
    private val ui get() = DeviceGestures(instrumentation)
    private val repository get() =
        (instrumentation.targetContext.applicationContext as ReaderApplication).settings

    @Test
    fun lateInitialLoadKeepsPendingPageNumberChoiceUntilSaveCompletes() {
        val original = setting(repository::loadPageNumbers)
        val activity = ui.launch()
        val release = CountDownLatch(1)
        try {
            val parent = openRoot(activity)
            // Drain startup loads, then hold the real save queue to reproduce a late UI binding.
            setting(repository::loadPageNumbers)
            val field = ReaderSettingsRepository::class.java.getDeclaredField("executor")
            field.isAccessible = true
            val queue = field.get(repository) as ExecutorService
            val entered = CountDownLatch(1)
            queue.execute {
                entered.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
            assertTrue("Settings queue did not reach barrier", entered.await(5, TimeUnit.SECONDS))
            val choice = ui.onMain {
                ui.views(requireNotNull(parent.window).decorView)
                    .filterIsInstance<android.widget.Switch>().single()
            }
            ui.onMain {
                choice.isChecked = !original
                assertFalse("Save must still be pending", choice.isEnabled)
                val bindingField = MainActivity::class.java
                    .getDeclaredField("bindPageNumberSetting")
                bindingField.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val initialBinding = bindingField.get(activity) as (Boolean) -> Unit
                initialBinding(original)
                assertEquals("Late load overwrote user choice", !original, choice.isChecked)
                assertFalse(choice.isEnabled)
            }
            release.countDown()
            ui.await("Saved switch did not preserve user choice") {
                choice.isEnabled && choice.isChecked == !original
            }
            assertEquals(!original, setting(repository::loadPageNumbers))
            ui.onMain { parent.dismiss() }
            ui.await("Settings did not close") { current(activity) == null }
            val reopened = openRoot(activity)
            ui.onMain {
                val savedChoice = ui.views(requireNotNull(reopened.window).decorView)
                    .filterIsInstance<android.widget.Switch>().single()
                assertEquals(!original, savedChoice.isChecked)
            }
        } finally {
            release.countDown()
            ui.onMain { activity.finish() }
            setting { repository.savePageNumbers(original, it) }
        }
    }

    @Test
    fun homeKeepsOpaqueParentAttachedAcrossSubsettingsAndBack() {
        val activity = ui.launch()
        var exposed = false
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { if (it) exposed = true }
        try {
            val parent = openRoot(activity)
            ui.onMain {
                activity.window.decorView.viewTreeObserver.addOnWindowFocusChangeListener(listener)
            }
            for (label in listOf(
                R.string.orientation_settings,
                R.string.background_settings,
                R.string.display_settings
            )) {
                val child = openChild(activity, parent, label)
                assertTrue(
                    ui.onMain {
                        val decor = requireNotNull(parent.window).decorView
                        parent.isShowing && decor.isAttachedToWindow
                    }
                )
                assertNotSame(parent, child)
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                awaitParent(activity, parent)
                assertFalse("Activity exposed between settings Windows", ui.onMain { exposed })
            }
            ui.onMain {
                val observer = activity.window.decorView.viewTreeObserver
                observer.removeOnWindowFocusChangeListener(listener)
                parent.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            ui.await("Settings did not close to home") {
                current(activity) == null && activity.hasWindowFocus()
            }
        } finally {
            ui.onMain {
                val observer = activity.window.decorView.viewTreeObserver
                observer.removeOnWindowFocusChangeListener(listener)
                activity.finish()
            }
        }
    }

    @Test
    fun readingApplyReturnsToParentAndRotationRestoresSettingsWithAnchor() {
        val oldBackground = setting(repository::loadBackground)
        val oldDisplay = setting(repository::loadDisplay)
        val oldOrientation = setting(repository::load)
        val oldPageNumbers = setting(repository::loadPageNumbers)
        setting { repository.savePageNumbers(true, it) }
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        var activity = ui.launch()
        try {
            var viewer = ui.sample(activity)
            ui.pinch(viewer)
            val before = ui.state(viewer)
            val parent = openRoot(activity, viewer)
            val background = openChild(activity, parent, R.string.background_settings)
            ui.onMain {
                background.findViewById<EditText>(R.id.background_color_input).setText("#123456")
                background.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            awaitParent(activity, parent)
            assertEquals(0xff123456.toInt(), setting(repository::loadBackground).customColor)
            val display = openChild(activity, parent, R.string.display_settings)
            ui.onMain { display.getButton(AlertDialog.BUTTON_POSITIVE).performClick() }
            awaitParent(activity, parent)
            assertEquals(oldDisplay, setting(repository::loadDisplay))
            val layout = openChild(activity, parent, R.string.page_layout_settings)
            ui.onMain { layout.getButton(AlertDialog.BUTTON_POSITIVE).performClick() }
            awaitParent(activity, parent)
            assertAnchor(before, ui.state(viewer))
            val orientation = openChild(activity, parent, R.string.orientation_settings)
            val portrait = activity.resources.configuration.orientation ==
                Configuration.ORIENTATION_PORTRAIT
            val target = if (portrait) {
                OrientationMode.LANDSCAPE
            } else {
                OrientationMode.PORTRAIT
            }
            ui.onMain {
                val list = ui.views(requireNotNull(orientation.window).decorView)
                    .filterIsInstance<android.widget.ListView>().single()
                list.performItemClick(null, target.ordinal, target.ordinal.toLong())
            }
            ui.await("Settings did not survive rotation", 30_000) {
                val latest = monitor.lastActivity ?: activity
                val root = root(latest)
                if (latest !== activity && root?.window?.decorView?.hasWindowFocus() == true) {
                    activity = latest
                    true
                } else {
                    false
                }
            }
            viewer = ui.awaitReady(activity)
            val restored = ui.onMain { requireNotNull(root(activity)) }
            assertSame(restored, ui.onMain { current(activity) })
            ui.await("Restored settings lost saved page number switch") {
                ui.views(requireNotNull(restored.window).decorView)
                    .filterIsInstance<android.widget.Switch>().single().isChecked
            }
            assertAnchor(before, ui.state(viewer))
            ui.onMain { restored.getButton(AlertDialog.BUTTON_NEGATIVE).performClick() }
            ui.await("Settings did not close to reader") {
                current(activity) == null && activity.hasWindowFocus()
            }
        } finally {
            ui.onMain {
                (monitor.lastActivity ?: activity).finish()
                activity.finish()
            }
            instrumentation.removeMonitor(monitor)
            setting { repository.saveBackground(oldBackground, it) }
            setting { repository.saveDisplay(oldDisplay, it) }
            setting { repository.save(oldOrientation, it) }
            setting { repository.savePageNumbers(oldPageNumbers, it) }
        }
    }

    private fun openRoot(activity: Activity, viewer: PdfCanvasView? = null): AlertDialog {
        ui.onMain {
            viewer?.performClick()
            ui.views(activity.window.decorView).filterIsInstance<Button>().first {
                it.isShown && it.text == activity.getString(R.string.settings)
            }.performClick()
        }
        ui.await("Main settings missing") {
            root(activity)?.window?.decorView?.hasWindowFocus() == true
        }
        return ui.onMain { requireNotNull(root(activity)) }
    }

    private fun openChild(activity: Activity, parent: AlertDialog, label: Int): AlertDialog {
        ui.onMain {
            ui.views(requireNotNull(parent.window).decorView).filterIsInstance<Button>().single {
                it.text == activity.getString(label)
            }.performClick()
            assertTrue("Parent dismissed before child was ready", parent.isShowing)
        }
        ui.await("Subsettings missing") {
            current(activity) !== parent &&
                current(activity)?.window?.decorView?.hasWindowFocus() == true
        }
        return ui.onMain { requireNotNull(current(activity)) }
    }

    private fun awaitParent(activity: Activity, parent: AlertDialog) {
        ui.await("Subsettings did not return to same parent") {
            current(activity) === parent && parent.window?.decorView?.hasWindowFocus() == true
        }
    }

    private fun current(activity: Activity) = dialog(activity, "settingsDialog")
    private fun root(activity: Activity) = dialog(activity, "settingsHomeDialog")

    private fun dialog(activity: Activity, name: String): AlertDialog? {
        val field = MainActivity::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(activity) as AlertDialog?
    }

    private fun assertAnchor(before: Bundle, after: Bundle) {
        assertEquals(before.getDouble("zoom"), after.getDouble("zoom"), 0.00001)
        assertEquals(before.getDouble("centerX"), after.getDouble("centerX"), 2.0)
        assertEquals(before.getDouble("centerY"), after.getDouble("centerY"), 2.0)
    }

    private fun <T> setting(action: ((Result<T>) -> Unit) -> Unit): T {
        val done = CountDownLatch(1)
        var value: Result<T>? = null
        action {
            value = it
            done.countDown()
        }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        return requireNotNull(value).getOrThrow()
    }
}
