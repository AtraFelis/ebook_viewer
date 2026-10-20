package dev.example.ebookviewer

import android.annotation.TargetApi
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.util.AtomicFile
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@TargetApi(30)
class DisplayDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation

    // This suite checks the app's real Window flags, so the fixture must not force screen-on.
    private val ui get() = DeviceGestures(instrumentation, keepScreenAwake = false)
    private val repository get() =
        (instrumentation.targetContext.applicationContext as ReaderApplication).settings

    @Test
    fun displayFileRecoversAndPreservesUnknownDataAndOtherSettings() {
        val directory = File.createTempFile(
            "display-store-",
            "",
            instrumentation.targetContext.cacheDir
        )
        directory.delete()
        directory.mkdirs()
        val base = File(directory, "settings.bin")
        val display = File(directory, "settings.bin.display")
        val choice = DisplaySettings(ReaderTheme.DARK, false, 25, true)
        try {
            ReaderSettingsRepository { base }.use { store ->
                assertEquals(DisplaySettings(), setting(store::loadDisplay))
                setting { store.save(OrientationMode.LANDSCAPE, it) }
                setting { store.saveBackground(BackgroundSettings(Color.BLUE, true), it) }
                setting { store.savePageNumbers(true, it) }
                val otherFiles = listOf(base, File("$base.background"), File("$base.pages"))
                val before = otherFiles.map(File::readBytes)
                setting { store.saveDisplay(choice, it) }
                otherFiles.forEachIndexed { index, file ->
                    assertTrue(before[index].contentEquals(file.readBytes()))
                }
            }
            AtomicFile(display).startWrite().use { it.write(byteArrayOf(1, 2, 3)) }
            ReaderSettingsRepository { base }.use {
                assertEquals(choice, setting(it::loadDisplay))
            }
            val unknown = display.readBytes().also { it[7] = 99 }
            display.writeBytes(unknown)
            ReaderSettingsRepository { base }.use { store ->
                val done = CountDownLatch(1)
                var failed = false
                store.saveDisplay(DisplaySettings()) {
                    failed = it.isFailure
                    done.countDown()
                }
                assertTrue(done.await(5, TimeUnit.SECONDS))
                assertTrue(failed)
                assertTrue(unknown.contentEquals(display.readBytes()))
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun brightnessAndScreenOnClearAtHomePauseAndDestructionAndReturnOnResume() {
        val previous = setting(repository::loadDisplay)
        val choice = DisplaySettings(ReaderTheme.LIGHT, false, 25, true)
        setting { repository.saveDisplay(choice, it) }
        val activity = ui.launch()
        try {
            assertWindow(activity, -1f, false)
            val view = ui.sample(activity)
            assertWindow(activity, 0.25f, true)
            ui.pinch(view)
            val before = ui.state(view)
            instrumentation.targetContext.startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            ui.await("Reader did not leave foreground") { !activity.hasWindowFocus() }
            assertWindow(activity, -1f, false)
            instrumentation.targetContext.startActivity(
                Intent(instrumentation.targetContext, MainActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    )
            )
            ui.await("Reader did not resume") { activity.hasWindowFocus() }
            assertSame(view, ui.awaitReady(activity))
            assertWindow(activity, 0.25f, true)
            assertAnchor(before, ui.state(view))
            ui.tap(view)
            ui.onMain {
                activity.findViewById<View>(R.id.reader_home).performClick()
            }
            assertWindow(activity, -1f, false)
            ui.sample(activity)
            assertWindow(activity, 0.25f, true)
            ui.onMain { activity.finish() }
            ui.await("Reader did not finish") { activity.isDestroyed }
            assertWindow(activity, -1f, false)
        } finally {
            ui.onMain { activity.finish() }
            setting { repository.saveDisplay(previous, it) }
        }
    }

    @Test
    fun lightDarkAndSystemThemesPreservePdfPixelsAnchorAndSavedChoice() {
        val previous = setting(repository::loadDisplay)
        val background = setting(repository::loadBackground)
        setting { repository.saveDisplay(DisplaySettings(theme = ReaderTheme.LIGHT), it) }
        setting { repository.saveBackground(BackgroundSettings(Color.MAGENTA), it) }
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        var activity = ui.launch()
        var beforePixels: Bitmap? = null
        try {
            var view = ui.sample(activity)
            ui.pinch(view)
            ui.await("Page not prepared") { view.goToPage(10, 0.4) }
            ui.awaitReady(activity)
            val before = ui.state(view)
            beforePixels = capture(view)
            for ((label, selected) in listOf(
                R.string.theme_dark to ReaderTheme.DARK,
                R.string.theme_light to ReaderTheme.LIGHT,
                R.string.theme_system to ReaderTheme.SYSTEM
            )) {
                openDisplay(activity, view)
                click(activity.getString(label))
                click(activity.getString(R.string.apply_settings))
                val expectedDark = selected.isDark(
                    activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                        Configuration.UI_MODE_NIGHT_YES
                )
                ui.await("Theme did not settle") {
                    activity = (monitor.lastActivity ?: activity)
                    !activity.isDestroyed &&
                        settingsRoot(activity)?.window?.decorView?.hasWindowFocus() == true &&
                        isDark(activity) == expectedDark
                }
                click(activity.getString(R.string.close_settings))
                view = ui.awaitReady(activity)
                assertAnchor(before, ui.state(view))
                assertEquals(Color.MAGENTA, view.outsideBackgroundColor())
                capture(view).let { after ->
                    try {
                        assertTrue(
                            "Theme changed PDF pixels",
                            requireNotNull(beforePixels).sameAs(after)
                        )
                    } finally {
                        after.recycle()
                    }
                }
                assertEquals(selected, setting(repository::loadDisplay).theme)
            }
            ui.onMain { activity.finish() }
            activity = ui.launch()
            assertEquals(ReaderTheme.SYSTEM, setting(repository::loadDisplay).theme)
            val expectedDark = activity.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            ui.await("System theme not restored") { isDark(activity) == expectedDark }
        } finally {
            ui.onMain {
                (monitor.lastActivity ?: activity).finish()
                activity.finish()
            }
            instrumentation.removeMonitor(monitor)
            beforePixels?.recycle()
            setting { repository.saveDisplay(previous, it) }
            setting { repository.saveBackground(background, it) }
        }
    }

    @Test
    fun displayDialogCancelDoesNotApplyAndSavedBrightnessSurvivesRelaunch() {
        val previous = setting(repository::loadDisplay)
        val initial = DisplaySettings(theme = ReaderTheme.LIGHT)
        setting { repository.saveDisplay(initial, it) }
        var activity = ui.launch()
        try {
            openDisplay(activity, null)
            editBrightness(activity)
            click(activity.getString(android.R.string.cancel))
            assertEquals(initial, setting(repository::loadDisplay))
            assertWindow(activity, -1f, false)
            openDisplay(activity, null)
            editBrightness(activity)
            click(activity.getString(R.string.apply_settings))
            val expected = DisplaySettings(ReaderTheme.LIGHT, false, 25, true)
            assertEquals(expected, setting(repository::loadDisplay))
            assertWindow(activity, -1f, false)
            ui.sample(activity)
            assertWindow(activity, 0.25f, true)
            ui.onMain { activity.finish() }
            activity = ui.launch()
            assertEquals(expected, setting(repository::loadDisplay))
            assertWindow(activity, -1f, false)
            ui.sample(activity)
            assertWindow(activity, 0.25f, true)
        } finally {
            ui.onMain { activity.finish() }
            setting { repository.saveDisplay(previous, it) }
        }
    }

    private fun editBrightness(activity: Activity) {
        click(activity.getString(R.string.system_brightness))
        click(activity.getString(R.string.keep_screen_on))
        ui.await("Brightness slider not available") {
            val root = instrumentation.uiAutomation.rootInActiveWindow ?: return@await false
            if (root.packageName != instrumentation.targetContext.packageName) return@await false
            fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                if (node.className == "android.widget.SeekBar") return node
                for (index in 0 until node.childCount) {
                    val child = node.getChild(index) ?: continue
                    find(child)?.let { return it }
                }
                return null
            }
            val slider = find(root) ?: return@await false
            slider.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
            if (!slider.isVisibleToUser) return@await false
            slider.performAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
                Bundle().apply {
                    putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 24f)
                }
            )
        }
    }

    private fun openDisplay(activity: Activity, view: PdfCanvasView?) {
        if (view != null) ui.tap(view)
        ui.onMain {
            ui.views(activity.window.decorView).filterIsInstance<Button>()
                .first {
                    it.isShown && it.text == activity.getString(R.string.settings)
                }.performClick()
        }
        click(activity.getString(R.string.display_settings))
        ui.await("Display controls missing") {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            root?.packageName == instrumentation.targetContext.packageName &&
                root.findAccessibilityNodeInfosByText(activity.getString(R.string.theme_system))
                    .any { it.isVisibleToUser }
        }
    }

    private fun settingsRoot(activity: Activity): android.app.AlertDialog? {
        val field = MainActivity::class.java.getDeclaredField("settingsHomeDialog")
        field.isAccessible = true
        return field.get(activity) as android.app.AlertDialog?
    }

    private fun click(text: String) {
        ui.await("Test control not available") {
            val root = instrumentation.uiAutomation.rootInActiveWindow ?: return@await false
            if (root.packageName != instrumentation.targetContext.packageName) return@await false
            root.findAccessibilityNodeInfosByText(text).any {
                if (it.text == text && !it.isVisibleToUser) {
                    it.performAction(
                        AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id
                    )
                }
                it.text == text &&
                    it.isVisibleToUser &&
                    it.isClickable &&
                    it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
        instrumentation.waitForIdleSync()
    }

    private fun isDark(activity: Activity): Boolean {
        val attributes = activity.obtainStyledAttributes(
            intArrayOf(android.R.attr.colorBackground, android.R.attr.forceDarkAllowed)
        )
        val dark = try {
            assertFalse("Automatic inversion must be disabled", attributes.getBoolean(1, true))
            val color = attributes.getColor(0, Color.BLACK)
            Color.red(color) + Color.green(color) + Color.blue(color) < 384
        } finally {
            attributes.recycle()
        }
        val appearance = activity.window.insetsController?.systemBarsAppearance ?: 0
        assertEquals(
            !dark,
            appearance and WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS != 0
        )
        return dark
    }

    private fun capture(view: PdfCanvasView): Bitmap = ui.onMain {
        Bitmap.createBitmap(view.width / 4, view.height / 4, Bitmap.Config.ARGB_8888).also {
            val canvas = Canvas(it)
            canvas.scale(0.25f, 0.25f)
            view.draw(canvas)
        }
    }

    private fun assertWindow(activity: Activity, brightness: Float, keep: Boolean) {
        ui.await("Reading Window overrides did not match lifecycle") {
            val attributes = activity.window.attributes
            attributes.screenBrightness == brightness &&
                (attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0) == keep
        }
    }

    private fun assertAnchor(before: Bundle, after: Bundle) {
        for (key in listOf("zoom", "centerX", "centerY")) {
            assertEquals(before.getDouble(key), after.getDouble(key), 0.003)
        }
    }

    private fun <T> setting(action: ((Result<T>) -> Unit) -> Unit): T {
        val done = CountDownLatch(1)
        var result: Result<T>? = null
        action {
            result = it
            done.countDown()
        }
        assertTrue("Settings operation timed out", done.await(5, TimeUnit.SECONDS))
        return requireNotNull(result).getOrThrow()
    }
}
