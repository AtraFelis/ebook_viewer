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
    fun orientationChoiceKeepsChildUntilExplicitCloseAndRestoresAfterRecreation() {
        val previous = setting(repository::load)
        setting { repository.save(OrientationMode.AUTO, it) }
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        var activity = ui.launch()
        try {
            val parent = openRoot(activity)
            val child = openChild(activity, parent, R.string.orientation_settings)
            val target = ui.onMain {
                val portrait = activity.resources.configuration.orientation ==
                    Configuration.ORIENTATION_PORTRAIT
                if (portrait) {
                    OrientationMode.PORTRAIT
                } else {
                    OrientationMode.LANDSCAPE
                }
            }
            ui.onMain {
                val list = orientationList(child)
                list.performItemClick(null, target.ordinal, target.ordinal.toLong())
            }
            assertEquals(target, setting(repository::load))
            ui.await("Orientation selection dismissed its child") {
                current(activity) === child &&
                    child.window?.decorView?.hasWindowFocus() == true &&
                    orientationList(child).isEnabled
            }
            assertEquals(target.ordinal, ui.onMain { orientationList(child).checkedItemPosition })
            val oldActivity = activity
            ui.onMain { activity.recreate() }
            ui.await("Orientation child did not survive recreation", 30_000) {
                val latest = monitor.lastActivity ?: activity
                if (latest !== oldActivity &&
                    current(latest) !== root(latest) &&
                    current(latest)?.window?.decorView?.hasWindowFocus() == true
                ) {
                    activity = latest
                    true
                } else {
                    false
                }
            }
            val restoredParent = ui.onMain { requireNotNull(root(activity)) }
            val restoredChild = ui.onMain { requireNotNull(current(activity)) }
            assertEquals(
                target.ordinal,
                ui.onMain { orientationList(restoredChild).checkedItemPosition }
            )
            ui.onMain { restoredChild.getButton(AlertDialog.BUTTON_NEGATIVE).performClick() }
            awaitParent(activity, restoredParent)
            val reopened = openChild(activity, restoredParent, R.string.orientation_settings)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            awaitParent(activity, restoredParent)
            assertFalse(ui.onMain { reopened.isShowing })
        } finally {
            ui.onMain {
                (monitor.lastActivity ?: activity).finish()
                activity.finish()
            }
            instrumentation.removeMonitor(monitor)
            setting { repository.save(previous, it) }
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
            assertTopEdgeAnchor(before, ui.state(viewer), viewer)
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
                val child = current(latest)
                if (latest !== activity &&
                    child !== root(latest) &&
                    child?.window?.decorView?.hasWindowFocus() == true
                ) {
                    activity = latest
                    true
                } else {
                    false
                }
            }
            viewer = ui.awaitReady(activity)
            val restored = ui.onMain { requireNotNull(root(activity)) }
            val restoredChild = ui.onMain { requireNotNull(current(activity)) }
            assertNotSame(restored, restoredChild)
            assertEquals(
                target.ordinal,
                ui.onMain { orientationList(restoredChild).checkedItemPosition }
            )
            ui.await("Restored settings lost saved page number switch") {
                ui.views(requireNotNull(restored.window).decorView)
                    .filterIsInstance<android.widget.Switch>().single().isChecked
            }
            assertTopEdgeAnchor(before, ui.state(viewer), viewer)
            ui.onMain { restoredChild.getButton(AlertDialog.BUTTON_NEGATIVE).performClick() }
            awaitParent(activity, restored)
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
        try {
            ui.await("Subsettings did not return to same parent") {
                current(activity) === parent && parent.window?.decorView?.hasWindowFocus() == true
            }
        } catch (error: AssertionError) {
            val state = ui.onMain {
                "destroyed=${activity.isDestroyed}, finishing=${activity.isFinishing}, " +
                    "same=${current(activity) === parent}, showing=${parent.isShowing}, " +
                    "focus=${parent.window?.decorView?.hasWindowFocus()}"
            }
            throw AssertionError("Settings parent state: $state", error)
        }
    }

    private fun current(activity: Activity) = dialog(activity, "settingsDialog")
    private fun root(activity: Activity) = dialog(activity, "settingsHomeDialog")

    private fun orientationList(dialog: AlertDialog) =
        ui.views(requireNotNull(dialog.window).decorView)
            .filterIsInstance<android.widget.ListView>().single()

    private fun dialog(activity: Activity, name: String): AlertDialog? {
        val field = MainActivity::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(activity) as AlertDialog?
    }

    private fun assertTopEdgeAnchor(before: Bundle, after: Bundle, viewer: PdfCanvasView) {
        assertEquals("This fixture starts at the document top", 0, before.getInt("page"))
        val minimumY = ui.onMain { viewer.height / (2 * after.getDouble("zoom")) }
        assertEquals(before.getDouble("zoom"), after.getDouble("zoom"), 0.00001)
        assertEquals(before.getDouble("centerX"), after.getDouble("centerX"), 2.0)
        assertEquals(
            // A taller cutout-adjusted viewport cannot keep its center above the document top.
            "Synthetic viewport minimum center Y=$minimumY",
            before.getDouble("centerY").coerceAtLeast(minimumY),
            after.getDouble("centerY"),
            2.0
        )
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
