package dev.example.ebookviewer

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.Switch
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingGuidanceDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation
    private val ui get() = DeviceGestures(instrumentation)

    @Test
    fun realBackgroundWriteFailureRetainsDraftAndRetryPersistsOnlyOnSuccess() {
        val directory = File.createTempFile(
            "retry-fixture-",
            "",
            instrumentation.targetContext.cacheDir
        )
        directory.delete()
        directory.mkdirs()
        val base = File(directory, "settings.bin")
        val unavailable = AtomicBoolean(false)
        val activity = ui.launch()
        var form: EditableSettingsForm? = null
        val original = BackgroundSettings(0xffabcdef.toInt(), false)
        try {
            ReaderSettingsRepository {
                if (unavailable.get()) throw IOException("Synthetic storage failure")
                base
            }.use { store ->
                setting { store.saveBackground(original, it) }
                val bytes = File("$base.background").readBytes()
                var applied = original
                fun create(draft: Bundle? = null): EditableSettingsForm =
                    activity.backgroundSettingsDialog(original, draft) { value, done ->
                        store.saveBackground(value) { saved ->
                            saved.onSuccess { applied = it }
                            done(saved.isSuccess)
                        }
                    }
                form = ui.onMain { create().also { it.dialog.show() } }
                unavailable.set(true)
                ui.onMain {
                    val dialog = requireNotNull(form).dialog
                    dialog.input(R.id.background_color_input).setText("#123456")
                    dialog.toggle(R.id.background_automatic).isChecked = true
                    dialog.applySettings()
                }
                awaitFailure(requireNotNull(form).dialog)
                captureSynthetic(
                    activity,
                    requireNotNull(requireNotNull(form).dialog.window).decorView,
                    "design-retry-synthetic.png"
                )
                assertEquals(original, ui.onMain { applied })
                assertTrue(bytes.contentEquals(File("$base.background").readBytes()))
                val draft = ui.onMain { requireNotNull(form).capture() }
                ui.onMain { requireNotNull(form).dialog.dismiss() }
                form = ui.onMain { create(draft).also { it.dialog.show() } }
                ui.onMain {
                    val dialog = requireNotNull(form).dialog
                    assertEquals(
                        "#123456",
                        dialog.input(R.id.background_color_input).text.toString()
                    )
                    assertTrue(dialog.toggle(R.id.background_automatic).isChecked)
                    assertTrue(dialog.findViewById<View>(R.id.settings_save_error).isShown)
                }
                unavailable.set(false)
                ui.onMain { requireNotNull(form).dialog.applySettings() }
                ui.await("Successful retry did not close background form") {
                    !requireNotNull(form).dialog.isShowing
                }
                val expected = BackgroundSettings(0xff123456.toInt(), true)
                assertEquals(expected, setting(store::loadBackground))
                assertEquals(expected, ui.onMain { applied })
            }
        } finally {
            ui.onMain {
                form?.dialog?.dismiss()
                activity.finish()
            }
            directory.deleteRecursively()
        }
    }

    @Test
    fun displayFailureKeepsAllInputsAndBlocksDuplicatePendingRequests() {
        val activity = ui.launch()
        val pending = mutableListOf<(Boolean) -> Unit>()
        val values = mutableListOf<DisplaySettings>()
        val form = ui.onMain {
            activity.displaySettingsDialog(DisplaySettings()) { value, done ->
                values.add(value)
                pending.add(done)
            }.also { it.dialog.show() }
        }
        try {
            ui.onMain {
                val dialog = form.dialog
                dialog.findViewById<RadioButton>(R.id.theme_dark).isChecked = true
                dialog.toggle(R.id.system_brightness).isChecked = false
                dialog.findViewById<SeekBar>(R.id.reading_brightness).progress = 69
                dialog.toggle(R.id.keep_screen_on).isChecked = true
                repeat(2) { dialog.applySettings() }
                assertEquals(1, pending.size)
                assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
                pending[0](false)
            }
            awaitFailure(form.dialog)
            val draft = ui.onMain { form.capture() }
            assertEquals("DARK", draft.getString("theme"))
            assertEquals(70, draft.getInt("brightness"))
            assertFalse(draft.getBoolean("systemBrightness"))
            assertTrue(draft.getBoolean("keepScreenOn"))
            ui.onMain {
                form.dialog.applySettings()
                assertEquals(2, pending.size)
                pending[0](true)
                assertTrue("Old completion closed the new attempt", form.dialog.isShowing)
                pending[1](true)
            }
            ui.await("Successful display retry did not close") { !form.dialog.isShowing }
            assertEquals(values[0], values[1])
        } finally {
            ui.onMain {
                form.dialog.dismiss()
                activity.finish()
            }
        }
    }

    @Test
    fun pageLayoutFailureRetainsSelectionAndInvalidGapDoesNotWrite() {
        val activity = ui.launch()
        var attempts = 0
        var chosen: PageLayoutSettings? = null
        val form = ui.onMain {
            activity.pageLayoutDialog(PageLayoutSettings()) { value, done ->
                attempts++
                if (attempts > 1) chosen = value
                done(attempts > 1)
            }.also { it.dialog.show() }
        }
        try {
            ui.onMain {
                val input = form.dialog.input(R.id.page_gap_input)
                input.setText("999")
                form.dialog.applySettings()
                assertEquals(0, attempts)
                input.setText("45")
                form.dialog.findViewById<RadioButton>(R.id.uniform_page_width).isChecked = true
                form.dialog.applySettings()
            }
            awaitFailure(form.dialog)
            ui.onMain {
                assertEquals("45", form.dialog.input(R.id.page_gap_input).text.toString())
                assertEquals(null, chosen)
                form.dialog.applySettings()
            }
            ui.await("Page layout retry did not close") { !form.dialog.isShowing }
            assertEquals(PageLayoutSettings(PageSizeMode.UNIFORM_WIDTH, 45), chosen)
        } finally {
            ui.onMain {
                form.dialog.dismiss()
                activity.finish()
            }
        }
    }

    @Test
    fun editableSettingsDraftsSurviveActualActivityRecreation() {
        var activity = ui.launch()
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        try {
            ui.sample(activity)
            for ((label, inputId, text) in listOf(
                Triple(R.string.background_settings, R.id.background_color_input, "#102030"),
                Triple(R.string.display_settings, R.id.keep_screen_on, ""),
                Triple(R.string.page_layout_settings, R.id.page_gap_input, "45")
            )) {
                ui.awaitReady(activity)
                ui.onMain {
                    val viewer = ui.views(activity.window.decorView)
                        .filterIsInstance<PdfCanvasView>().single()
                    if (!activity.findViewById<View>(R.id.reader_tools).isShown) {
                        viewer.performClick()
                    }
                    ui.views(activity.window.decorView).filterIsInstance<Button>().first {
                        it.isShown && it.text == activity.getString(R.string.settings)
                    }.performClick()
                }
                ui.await("Main settings missing") {
                    current(activity)?.window?.decorView?.hasWindowFocus() == true
                }
                ui.await("Settings entry not ready: $label") {
                    val decor = current(activity)?.window?.decorView ?: return@await false
                    ui.views(decor).filterIsInstance<Button>().singleOrNull {
                        it.text == activity.getString(label)
                    }?.isEnabled == true
                }
                ui.onMain {
                    val decor = requireNotNull(requireNotNull(current(activity)).window).decorView
                    ui.views(decor).filterIsInstance<Button>().single {
                        it.text == activity.getString(label)
                    }.performClick()
                }
                ui.await("Editable settings missing: $label") {
                    current(activity)?.findViewById<View>(inputId) != null &&
                        current(activity)?.window?.decorView?.hasWindowFocus() == true
                }
                ui.onMain {
                    val dialog = requireNotNull(current(activity))
                    if (text.isNotEmpty()) {
                        dialog.input(inputId).setText(text)
                    } else {
                        dialog.toggle(inputId).isChecked = true
                    }
                    // Recreation fixture; real write failure is exercised in the isolated store test.
                    dialog.findViewById<View>(R.id.settings_save_error).visibility = View.VISIBLE
                }
                val previous = activity
                ui.onMain { activity.recreate() }
                ui.await("Draft settings did not restore", 30_000) {
                    val next = monitor.lastActivity ?: activity
                    if (next !== previous &&
                        current(next)?.findViewById<View>(inputId) != null &&
                        current(next)?.window?.decorView?.hasWindowFocus() == true
                    ) {
                        activity = next
                        true
                    } else {
                        false
                    }
                }
                ui.onMain {
                    val dialog = requireNotNull(current(activity))
                    if (text.isNotEmpty()) {
                        assertEquals(text, dialog.input(inputId).text.toString())
                    } else {
                        assertTrue(dialog.toggle(inputId).isChecked)
                    }
                    assertTrue(dialog.findViewById<View>(R.id.settings_save_error).isShown)
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
                }
                ui.await("Draft did not close to parent") {
                    current(activity)?.findViewById<View>(inputId) == null
                }
                ui.onMain {
                    requireNotNull(current(activity)).getButton(AlertDialog.BUTTON_NEGATIVE)
                        .performClick()
                }
                ui.await("Main settings did not close") { current(activity) == null }
            }
        } finally {
            ui.onMain {
                (monitor.lastActivity ?: activity).finish()
                activity.finish()
            }
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun pendingLayoutWriteSurvivesRecreationWithoutStaleLifecycleOverwrite() {
        val application = instrumentation.targetContext.applicationContext as ReaderApplication
        val file = File.createTempFile("layout-retry-", ".pdf", application.cacheDir)
        SyntheticDocument.write(file)
        val uri = Uri.fromFile(file)
        var activity = ui.launch()
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        val release = CountDownLatch(1)
        fun <T> library(action: (DocumentLibrary) -> T): T =
            setting { complete -> application.library.execute(action, complete) }
        try {
            ui.onMain { (activity as MainActivity).openDocument(uri) }
            val viewer = ui.awaitReady(activity)
            library { it.list() }
            ui.onMain {
                viewer.performClick()
                ui.views(activity.window.decorView).filterIsInstance<Button>().single {
                    it.isShown && it.text == activity.getString(R.string.settings)
                }.performClick()
            }
            ui.await("Layout parent not focused") {
                current(activity)?.window?.decorView?.hasWindowFocus() == true
            }
            ui.onMain {
                val decor = requireNotNull(requireNotNull(current(activity)).window).decorView
                ui.views(decor).filterIsInstance<Button>().single {
                    it.text == activity.getString(R.string.page_layout_settings)
                }.performClick()
            }
            ui.await("Layout editor not ready") {
                current(activity)?.findViewById<View>(R.id.page_gap_input) != null
            }
            val field = LibraryRepository::class.java.getDeclaredField("executor").apply {
                isAccessible = true
            }
            val entered = CountDownLatch(1)
            (field.get(application.library) as ExecutorService).execute {
                entered.countDown()
                release.await(20, TimeUnit.SECONDS)
            }
            assertTrue("Library barrier missing", entered.await(5, TimeUnit.SECONDS))
            ui.onMain {
                val dialog = requireNotNull(current(activity))
                dialog.input(R.id.page_gap_input).setText("45")
                dialog.findViewById<RadioButton>(R.id.uniform_page_width).isChecked = true
                dialog.applySettings()
                assertEquals(PageLayoutSettings(), viewer.pageLayoutSettings())
                activity.recreate()
            }
            val previous = activity
            ui.await("Activity did not recreate during pending write") {
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
            val expected = PageLayoutSettings(PageSizeMode.UNIFORM_WIDTH, 45)
            assertEquals(expected, ui.onMain { restored.pageLayoutSettings() })
            assertEquals(
                expected,
                library { store ->
                    store.list().single { it.uri == uri.toString() }.pageLayout
                }
            )
            ui.await("Pending layout draft not restored") {
                current(activity)?.findViewById<View>(R.id.page_gap_input) != null
            }
            assertEquals(
                "45",
                ui.onMain {
                    requireNotNull(current(activity)).input(R.id.page_gap_input).text.toString()
                }
            )
        } finally {
            release.countDown()
            ui.onMain { activity.finish() }
            ui.await("Layout retry fixture did not close") { activity.isDestroyed }
            library { it.remove(uri.toString()) }
            instrumentation.removeMonitor(monitor)
            file.delete()
        }
    }

    @Test
    fun firstCentralTapDismissesHintAndOpensToolsInOneGestureWithoutMovingPdf() {
        withFreshHint { activity ->
            val viewer = ui.sample(activity)
            ui.await("First reading hint missing") {
                activity.findViewById<View>(R.id.reading_hint).isShown
            }
            captureSynthetic(activity, viewer.parent as View, "design-hint-synthetic.png")
            val before = requireNotNull(ui.onMain { viewer.readingPosition() })
            ui.tap(viewer.parent as View)
            ui.await("First central tap did not open tools") {
                activity.findViewById<View>(R.id.reader_tools).isShown &&
                    !activity.findViewById<View>(R.id.reading_hint).isShown
            }
            assertEquals(before, ui.onMain { viewer.readingPosition() })
            ui.sample(activity)
            assertFalse(ui.onMain { activity.findViewById<View>(R.id.reading_hint).isShown })
        }
    }

    @Test
    fun hintSurfacePassesTouchThroughAndFirstScrollDismissesIt() {
        withFreshHint { activity ->
            val viewer = ui.sample(activity)
            val hint = activity.findViewById<View>(R.id.reading_hint)
            ui.await("First hint missing") { hint.isShown }
            val root = ui.onMain { viewer.parent as View }
            val before = requireNotNull(ui.onMain { viewer.readingPosition() })
            val start = ui.onMain { hint.top + hint.height / 2f }
            val down = SystemClock.uptimeMillis()
            for ((index, action) in listOf(
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP
            ).withIndex()) {
                val event = MotionEvent.obtain(
                    down,
                    SystemClock.uptimeMillis(),
                    action,
                    root.width / 2f,
                    start - index * 30 * activity.resources.displayMetrics.density,
                    0
                )
                ui.onMain { root.dispatchTouchEvent(event) }
                event.recycle()
                SystemClock.sleep(40)
            }
            assertFalse(ui.onMain { hint.isShown })
            assertTrue(ui.onMain { viewer.readingPosition() != before })
        }
    }

    private fun withFreshHint(test: (Activity) -> Unit) {
        val application = instrumentation.targetContext.applicationContext as ReaderApplication
        val state = application.readingHint
        val offered = ReadingHintState::class.java.getDeclaredField("offered").apply {
            isAccessible = true
        }
        val visible = ReadingHintState::class.java.getDeclaredField("visible").apply {
            isAccessible = true
        }
        val old = ui.onMain { offered.getBoolean(state) to visible.getBoolean(state) }
        ui.onMain {
            offered.setBoolean(state, false)
            visible.setBoolean(state, false)
        }
        val activity = ui.launch()
        try {
            test(activity)
        } finally {
            ui.onMain {
                activity.finish()
                offered.setBoolean(state, old.first)
                visible.setBoolean(state, old.second)
            }
        }
    }

    private fun AlertDialog.input(id: Int): EditText = findViewById(id)
    private fun AlertDialog.toggle(id: Int): Switch = findViewById(id)
    private fun AlertDialog.applySettings() = getButton(AlertDialog.BUTTON_POSITIVE).performClick()

    private fun captureSynthetic(activity: Activity, view: View, name: String) {
        val bitmap = ui.onMain {
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
                view.draw(Canvas(it))
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

    private fun awaitFailure(dialog: AlertDialog) {
        ui.await("Failed save did not retain form and draft") {
            dialog.isShowing &&
                dialog.findViewById<View>(R.id.settings_save_error).isShown &&
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled
        }
    }

    private fun current(activity: Activity): AlertDialog? {
        val field = MainActivity::class.java.getDeclaredField("settingsDialog").apply {
            isAccessible = true
        }
        return field.get(activity) as AlertDialog?
    }

    private fun <T> setting(action: ((Result<T>) -> Unit) -> Unit): T {
        val done = CountDownLatch(1)
        var result: Result<T>? = null
        action {
            result = it
            done.countDown()
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        return requireNotNull(result).getOrThrow()
    }
}
