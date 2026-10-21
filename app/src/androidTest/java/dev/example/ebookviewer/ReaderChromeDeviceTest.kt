package dev.example.ebookviewer

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderChromeDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation
    private val ui get() = DeviceGestures(instrumentation)
    private val settings get() =
        (instrumentation.targetContext.applicationContext as ReaderApplication).settings

    @Test
    fun transparentPageGlyphsContrastWithLightDarkAndColoredSurfacesAtTwoFontScales() {
        for (scale in listOf(1f, 2f)) {
            val config = Configuration(
                instrumentation.targetContext.resources.configuration
            ).apply {
                fontScale = scale
            }
            val context = instrumentation.targetContext.createConfigurationContext(config)
            val view = ui.onMain {
                PageNumberView(context).apply {
                    text = "20 / 416"
                    measure(0, 0)
                    layout(0, 0, measuredWidth, measuredHeight)
                }
            }
            val output = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                ui.onMain {
                    assertNull(view.background)
                    assertFalse(view.isClickable)
                    view.draw(Canvas(output))
                }
                val transparent = pixels(output).count { Color.alpha(it) == 0 }
                assertTrue("An opaque rectangle obscures the PDF", transparent > output.width)
                assertEquals(0, Color.alpha(output.getPixel(0, 0)))
                for (surface in listOf(Color.WHITE, Color.BLACK, Color.GRAY, 0xff285bc5.toInt())) {
                    output.eraseColor(surface)
                    ui.onMain { view.draw(Canvas(output)) }
                    val contrasting = pixels(output).count {
                        abs(brightness(it) - brightness(surface)) > 96
                    }
                    assertTrue(
                        "Page glyphs lack contrasting pixels at font=$scale",
                        contrasting > 10
                    )
                }
            } finally {
                output.recycle()
            }
        }
    }

    @Test
    fun centeredPageIndicatorKeepsViewportAndPassesScrollingThroughItsOwnSurface() {
        val original = setting(settings::loadPageNumbers)
        setting { settings.savePageNumbers(true, it) }
        var launched: android.app.Activity? = null
        try {
            val activity = ui.launch().also { launched = it }
            val viewer = ui.sample(activity)
            val label = activity.findViewById<PageNumberView>(R.id.page_indicator)
            ui.await("Page indicator not laid out") { label.isShown && label.width > 0 }
            val root = ui.onMain { viewer.parent as View }
            val dimensions = ui.onMain { viewer.width to viewer.height }
            val before = requireNotNull(ui.onMain { viewer.readingPosition() })
            ui.await("First hint overlaps the page indicator") {
                val hint = activity.findViewById<View>(R.id.reading_hint)
                !hint.isShown || hint.bottom <= label.top
            }
            ui.onMain {
                val params = label.layoutParams as FrameLayout.LayoutParams
                assertEquals(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, params.gravity)
                val center = (root.width + params.leftMargin - params.rightMargin) / 2.0
                assertEquals(center, label.left + label.width / 2.0, 1.0)
                assertTrue(params.bottomMargin >= ReaderStyle(activity).dp(12))
                assertTrue(label.bottom < root.height)
                assertNull(label.background)
            }
            ui.tap(root)
            ui.tap(root)
            ui.await("Reader tools did not close") {
                !activity.findViewById<View>(R.id.reader_tools).isShown
            }
            assertEquals(before, ui.onMain { viewer.readingPosition() })
            assertEquals(dimensions, ui.onMain { viewer.width to viewer.height })
            capture(root, File(activity.cacheDir, "design-centered-number-synthetic.png"))
            dragFromIndicator(root, label)
            ui.await("Indicator intercepted the scroll") { viewer.readingPosition() != before }
            assertEquals(before.zoom, ui.onMain { viewer.readingPosition()?.zoom })
            ui.tap(root)
            ui.await("Tools did not open") {
                activity.findViewById<View>(R.id.reader_tools).isShown
            }
            capture(root, File(activity.cacheDir, "design-tinted-tools-synthetic.png"))
        } finally {
            ui.onMain { launched?.finish() }
            setting { settings.savePageNumbers(original, it) }
        }
    }

    @Test
    fun readingActionsHaveDistinctTintedEnabledAndDisabledFillsInBothThemes() {
        val activity = ui.launch()
        try {
            for (theme in listOf(R.style.ReaderLight, R.style.ReaderDark)) {
                val context = ContextThemeWrapper(activity, theme)
                val tools = ui.onMain {
                    ReaderToolsView(
                        context,
                        TextView(context),
                        {}, {}, {}, {}, {}, {}, {}, {}
                    ).also {
                        it.view.visibility = View.VISIBLE
                        activity.setContentView(it.view)
                    }
                }
                ui.await("Theme fixture did not become visible") {
                    tools.view.isAttachedToWindow && tools.view.isShown && tools.view.width > 0
                }
                ui.onMain { checkButtonFills(tools, ReaderStyle(context)) }
            }
        } finally {
            ui.onMain { activity.finish() }
        }
    }

    private fun checkButtonFills(tools: ReaderToolsView, style: ReaderStyle) {
        val context = tools.view.context
        val buttons = listOf(tools.page, tools.bookmarks, tools.readText) +
            ui.views(tools.view).filterIsInstance<Button>().filter {
                it.text == context.getString(R.string.settings)
            }
        assertEquals(4, buttons.size)
        for (button in buttons) {
            assertTrue("New action unexpectedly disabled", button.isEnabled)
            button.refreshDrawableState()
            button.jumpDrawablesToCurrentState()
            button.measure(
                View.MeasureSpec.makeMeasureSpec(style.dp(180), View.MeasureSpec.EXACTLY),
                0
            )
            button.layout(0, 0, button.measuredWidth, button.measuredHeight)
            val bitmap = Bitmap.createBitmap(
                button.width,
                button.height,
                Bitmap.Config.ARGB_8888
            )
            try {
                button.draw(Canvas(bitmap))
                val enabled = bitmap.getPixel(button.width / 2, style.dp(4))
                assertEquals(
                    "Action=${button.text} state=${button.drawableState.contentToString()}",
                    style.accentSoft,
                    enabled
                )
                assertTrue("Tint blends into the footer", enabled != style.surface)
                button.isEnabled = false
                button.jumpDrawablesToCurrentState()
                button.draw(Canvas(bitmap))
                assertTrue(enabled != bitmap.getPixel(button.width / 2, style.dp(4)))
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun dragFromIndicator(root: View, label: View) {
        val down = android.os.SystemClock.uptimeMillis()
        val x = ui.onMain { label.left + label.width / 2f }
        val y = ui.onMain { label.top + label.height / 2f }
        val density = root.resources.displayMetrics.density
        for ((index, action) in listOf(
            android.view.MotionEvent.ACTION_DOWN,
            android.view.MotionEvent.ACTION_MOVE,
            android.view.MotionEvent.ACTION_MOVE,
            android.view.MotionEvent.ACTION_UP
        ).withIndex()) {
            val event = android.view.MotionEvent.obtain(
                down,
                android.os.SystemClock.uptimeMillis(),
                action,
                x,
                y - index * 48 * density,
                0
            )
            ui.onMain { root.dispatchTouchEvent(event) }
            event.recycle()
            android.os.SystemClock.sleep(40)
        }
    }

    private fun pixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun brightness(color: Int): Int =
        (Color.red(color) + Color.green(color) + Color.blue(color)) / 3

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
