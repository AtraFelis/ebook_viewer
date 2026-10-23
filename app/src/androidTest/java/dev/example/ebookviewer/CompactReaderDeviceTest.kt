package dev.example.ebookviewer

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Opt-in checks for compact chrome; not added to the default device suite. */
class CompactReaderDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation
    private val ui get() = DeviceGestures(instrumentation)
    private val settings get() =
        (instrumentation.targetContext.applicationContext as ReaderApplication).settings

    @Test
    fun controlsLeaveLandscapeContentVisibleAtNormalAndLargeFontSizes() {
        ui.onMain {
            for (font in listOf(1f, 2f)) {
                val config = Configuration(instrumentation.targetContext.resources.configuration)
                    .apply { fontScale = font }
                val context = ContextThemeWrapper(
                    instrumentation.targetContext.createConfigurationContext(config),
                    R.style.ReaderLight
                )
                val style = ReaderStyle(context)
                for ((width, height) in listOf(400 to 800, 800 to 360)) {
                    val tools = ReaderToolsView(
                        context, TextView(context), {}, {}, {}, {}, {}, {}, {}, {}
                    )
                    tools.view.visibility = View.VISIBLE
                    tools.updatePages(24, 416, true)
                    tools.view.measure(
                        View.MeasureSpec.makeMeasureSpec(style.dp(width), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(style.dp(height), View.MeasureSpec.EXACTLY)
                    )
                    tools.view.layout(0, 0, style.dp(width), style.dp(height))
                    val header = tools.view.findViewById<View>(R.id.reader_header)
                    val footer = tools.view.findViewById<View>(R.id.reader_footer)
                    assertTrue(
                        "Chrome obscures half the reader at $width x $height, font=$font",
                        header.height + footer.height < tools.view.height / 2
                    )
                    assertFalse(
                        ui.views(footer).filterIsInstance<Button>().any {
                            it.text == context.getString(R.string.go_to_page)
                        }
                    )
                    assertEquals(4, ui.views(footer).filterIsInstance<Button>().size)
                    for (button in ui.views(footer).filterIsInstance<Button>()) {
                        assertTrue(button.width >= style.dp(48))
                        assertTrue(button.height >= style.dp(48))
                        assertTrue(button.bottom <= footer.height)
                    }
                    assertEquals(style.dp(48), tools.scrubber.height)
                    val sliderWidth = tools.scrubber.width
                    val event = MotionEvent.obtain(
                        0,
                        0,
                        MotionEvent.ACTION_DOWN,
                        style.dp(9).toFloat(),
                        24f,
                        0
                    )
                    try {
                        tools.scrubber.dispatchTouchEvent(event)
                        tools.view.measure(
                            View.MeasureSpec.makeMeasureSpec(
                                style.dp(width),
                                View.MeasureSpec.EXACTLY
                            ),
                            View.MeasureSpec.makeMeasureSpec(
                                style.dp(height),
                                View.MeasureSpec.EXACTLY
                            )
                        )
                        tools.view.layout(0, 0, style.dp(width), style.dp(height))
                        assertEquals(sliderWidth, tools.scrubber.width)
                        assertEquals(0, tools.scrubber.previewPage)
                    } finally {
                        event.recycle()
                        tools.scrubber.cancelPreview()
                    }
                }
            }
        }
    }

    @Test
    fun quickPanelPersistsControlsWithoutMovingPdfAndReturnsToTools() {
        val originalBackground = setting(settings::loadBackground)
        val originalDisplay = setting(settings::loadDisplay)
        val originalInversion = setting(settings::loadInversion)
        val activity = ui.launch()
        try {
            val viewer = ui.sample(activity)
            val before = ui.onMain { viewer.readingPosition() }
            ui.onMain {
                viewer.performClick()
                activity.findViewById<View>(R.id.reader_view_settings).performClick()
            }
            fun panel(): ViewSettingsDialog? =
                MainActivity::class.java.getDeclaredField("viewSettingsDialog").apply {
                    isAccessible = true
                }.get(activity) as ViewSettingsDialog?
            ui.await("Quick view panel did not open") { panel()?.isShowing == true }
            val dialog = ui.onMain { requireNotNull(panel()) }
            val root = ui.onMain { requireNotNull(dialog.window).decorView }
            ui.await("Quick view panel not measured") {
                root.findViewById<View>(R.id.view_settings_panel).height > 0
            }
            ui.onMain {
                val content = root.findViewById<View>(R.id.view_settings_panel)
                assertTrue(content.height <= (activity.window.decorView.height * 0.66).toInt())
            }
            for ((label, color) in listOf(
                R.string.color_soft_green to 0xffdcebd6.toInt(),
                R.string.color_sky_blue to 0xffdeeff7.toInt()
            )) {
                ui.onMain {
                    ui.views(root).filterIsInstance<Button>().single {
                        it.contentDescription == activity.getString(label)
                    }.performClick()
                }
                ui.await("New background preset did not save") {
                    root.findViewById<View>(R.id.background_automatic).isEnabled &&
                        viewer.outsideBackgroundColor() == color
                }
                assertEquals(color, setting(settings::loadBackground).customColor)
            }
            ui.onMain {
                ui.views(root).filterIsInstance<Button>().first {
                    it.contentDescription == activity.getString(R.string.color_black)
                }.performClick()
            }
            ui.await("Background save did not finish") {
                root.findViewById<View>(R.id.background_automatic).isEnabled &&
                    viewer.outsideBackgroundColor() == Color.BLACK
            }
            ui.onMain { root.findViewById<Switch>(R.id.invert_pdf).performClick() }
            ui.await("Inversion save did not finish") {
                root.findViewById<Switch>(R.id.invert_pdf).let {
                    it.isEnabled && it.isChecked == !originalInversion
                }
            }
            ui.onMain {
                val system = root.findViewById<Button>(R.id.system_brightness)
                if (system.isSelected) system.performClick()
            }
            ui.await("Manual brightness unavailable") {
                root.findViewById<SeekBar>(R.id.reading_brightness).isEnabled
            }
            val bar = ui.onMain { root.findViewById<SeekBar>(R.id.reading_brightness) }
            ui.tap(bar, 0.65f)
            ui.await("Brightness save did not finish") { bar.isEnabled }
            ui.onMain { root.findViewById<Switch>(R.id.keep_screen_on).performClick() }
            ui.await("Keep-screen setting did not save") {
                root.findViewById<Switch>(R.id.keep_screen_on).isEnabled
            }
            val saved = setting(settings::loadDisplay)
            ui.onMain {
                for (id in listOf(R.id.invert_pdf, R.id.keep_screen_on)) {
                    val background = root.findViewById<View>(id).background as ColorDrawable
                    assertEquals(Color.TRANSPARENT, background.color)
                }
            }
            assertFalse(saved.systemBrightness)
            assertEquals(!originalDisplay.keepScreenOn, saved.keepScreenOn)
            assertEquals(Color.BLACK, setting(settings::loadBackground).customColor)
            assertEquals(!originalInversion, setting(settings::loadInversion))
            ui.onMain {
                assertEquals(
                    saved.brightnessPercent / 100f,
                    requireNotNull(dialog.window).attributes.screenBrightness,
                    0.001f
                )
            }
            capture(root, File(activity.cacheDir, "compact-view-settings-synthetic.png"))
            ui.onMain {
                assertEquals(before, viewer.readingPosition())
                dialog.onBackPressed()
            }
            ui.await("Closing quick view did not restore tools") {
                !dialog.isShowing && activity.findViewById<View>(R.id.reader_tools).isShown
            }
            ui.onMain {
                ui.views(activity.window.decorView).filterIsInstance<Button>().single {
                    it.isShown && it.text == activity.getString(R.string.settings)
                }.performClick()
            }
            ui.await("General settings route changed") {
                MainActivity::class.java.getDeclaredField("settingsDialog").apply {
                    isAccessible = true
                }.get(activity) != null
            }
        } finally {
            ui.onMain { activity.finish() }
            setting { settings.saveBackground(originalBackground, it) }
            setting { settings.saveDisplay(originalDisplay, it) }
            setting { settings.saveInversion(originalInversion, it) }
        }
    }

    @Test
    fun importFailureDuringReadingKeepsStatusAndShowsResultOnHome() {
        val activity = ui.launch()
        try {
            val viewer = ui.sample(activity)
            val pending = MainActivity::class.java.getDeclaredField("pendingImportMessage").apply {
                isAccessible = true
            }
            val statusField = MainActivity::class.java.getDeclaredField("status").apply {
                isAccessible = true
            }
            val before = ui.onMain { viewer.readingPosition() }
            val message = ui.onMain {
                (activity as MainActivity).importDocuments(listOf(Uri.parse("invalid:fixture")), 0)
                (statusField.get(activity) as TextView).text.toString()
            }
            ui.await("Import failure was discarded during reading") {
                pending.get(activity) != null
            }
            val expected = activity.getString(R.string.pdf_import_result, 0, 0, 1, 0)
            ui.onMain {
                assertEquals(message, (statusField.get(activity) as TextView).text.toString())
                assertEquals(before, viewer.readingPosition())
                assertEquals(expected, pending.get(activity))
                viewer.performClick()
                activity.findViewById<View>(R.id.reader_home).performClick()
            }
            ui.await("Home lost the deferred import result") {
                ui.views(activity.window.decorView).filterIsInstance<TextView>().any {
                    it.isShown && it.text.toString() == expected
                }
            }
        } finally {
            ui.onMain { activity.finish() }
        }
    }

    private fun capture(view: View, file: File) {
        val bitmap = ui.onMain {
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
                view.draw(Canvas(it))
            }
        }
        try {
            file.outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
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
