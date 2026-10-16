package dev.example.ebookviewer

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.util.AtomicFile
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation
    private val ui get() = DeviceGestures(instrumentation)

    @Test
    fun backgroundChangesOnlyOutsidePdfWithoutMovingAnchorOrRequestingTiles() {
        ui.onMain {
            var requests = emptyList<Tile>()
            var requestCount = 0
            val view = PdfCanvasView(instrumentation.targetContext) {
                requests = it
                requestCount++
            }
            view.layout(0, 0, 800, 800)
            view.setPages(listOf(PageSize(400, 800), PageSize(800, 800)))
            val preview = requests.first()
            val page = Bitmap.createBitmap(preview.width, preview.height, Bitmap.Config.ARGB_8888)
            val output = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
            try {
                page.eraseColor(Color.WHITE)
                view.putTile(preview, page)
                val before = requireNotNull(view.saveViewport())
                val cachedBytes = view.renderState().cachedBytes
                val requestsBefore = requestCount
                view.setBackgroundSettings(BackgroundSettings(Color.MAGENTA))
                view.draw(Canvas(output))
                assertEquals(Color.MAGENTA, output.getPixel(50, 400))
                assertEquals(Color.WHITE, output.getPixel(400, 400))
                val after = requireNotNull(view.saveViewport())
                for (key in listOf("zoom", "centerX", "centerY")) {
                    assertEquals(before.getDouble(key), after.getDouble(key), 0.0)
                }
                assertEquals(cachedBytes, view.renderState().cachedBytes)
                assertEquals(requestsBefore, requestCount)
                view.reset()
                view.setPages(listOf(PageSize(600, 800)))
                assertEquals(Color.MAGENTA, view.outsideBackgroundColor())
            } finally {
                view.reset()
                page.recycle()
                output.recycle()
            }
        }
    }

    @Test
    fun backgroundFileRecoversAndDoesNotOverwriteUnknownVersionOrOrientation() {
        val directory = File.createTempFile(
            "background-store-",
            "",
            instrumentation.targetContext.cacheDir
        )
        directory.delete()
        directory.mkdirs()
        val file = File(directory, "settings.bin")
        val background = File(directory, "settings.bin.background")
        try {
            val choice = BackgroundSettings(Color.BLUE)
            ReaderSettingsRepository { file }.use { repository ->
                val done = CountDownLatch(1)
                repository.save(OrientationMode.LANDSCAPE) { done.countDown() }
                assertTrue(done.await(10, TimeUnit.SECONDS))
                val orientationBytes = file.readBytes()
                setting { repository.saveBackground(choice, it) }
                assertTrue(orientationBytes.contentEquals(file.readBytes()))
            }
            AtomicFile(background).startWrite().use { it.write(byteArrayOf(1, 2, 3)) }
            ReaderSettingsRepository { file }.use {
                assertEquals(choice, setting(it::loadBackground))
            }
            val bytes = background.readBytes().also { it[7] = 99 }
            background.writeBytes(bytes)
            ReaderSettingsRepository { file }.use { repository ->
                val done = CountDownLatch(1)
                var failed = false
                repository.saveBackground(BackgroundSettings()) {
                    failed = it.isFailure
                    done.countDown()
                }
                assertTrue(done.await(10, TimeUnit.SECONDS))
                assertTrue(failed)
            }
            assertTrue(bytes.contentEquals(background.readBytes()))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun customColorUiValidatesAndPersistsAcrossRelaunchWithoutMovingPdf() {
        val application = instrumentation.targetContext.applicationContext as ReaderApplication
        val repository = application.settings
        val previous = setting(repository::loadBackground)
        var activity = ui.launch()
        try {
            var view = ui.sample(activity)
            ui.pinch(view)
            ui.drag(view, true)
            val before = ui.state(view)
            openBackground(activity, view)
            setColor("#123")
            click(activity.getString(R.string.apply_settings))
            assertEquals(previous, setting(repository::loadBackground))
            setColor("#12AB34")
            click(activity.getString(R.string.apply_settings))
            ui.await("Custom background was not applied") {
                view.outsideBackgroundColor() == Color.rgb(18, 171, 52)
            }
            assertEquals(before.getDouble("zoom"), ui.state(view).getDouble("zoom"), 0.000001)
            assertEquals(before.getDouble("centerY"), ui.state(view).getDouble("centerY"), 0.000001)
            ui.onMain { activity.finish() }
            activity = ui.launch()
            assertEquals(
                BackgroundSettings(Color.rgb(18, 171, 52)),
                setting(repository::loadBackground)
            )
            openBackground(activity, null)
            click(activity.getString(R.string.color_paper))
            click(activity.getString(android.R.string.cancel))
            assertEquals(Color.rgb(18, 171, 52), setting(repository::loadBackground).customColor)
            openBackground(activity, null)
            click(activity.getString(R.string.color_gray))
            click(activity.getString(R.string.apply_settings))
            view = ui.sample(activity)
            ui.await("Default gray preset was not applied") {
                view.outsideBackgroundColor() == DEFAULT_BACKGROUND_COLOR
            }
        } finally {
            ui.onMain { activity.finish() }
            setting { repository.saveBackground(previous, it) }
        }
    }

    private fun openBackground(activity: Activity, view: PdfCanvasView?) {
        if (view != null) ui.tap(view)
        ui.onMain {
            ui.views(activity.window.decorView).filterIsInstance<Button>()
                .single {
                    it.isShown && it.text == activity.getString(R.string.settings)
                }.performClick()
        }
        click(activity.getString(R.string.background_settings))
    }

    private fun click(text: String) {
        awaitAction {
            instrumentation.uiAutomation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByText(text)
                ?.firstOrNull {
                    it.packageName == instrumentation.targetContext.packageName &&
                        it.text?.toString() == text
                }?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
        SystemClock.sleep(150)
    }

    private fun setColor(text: String) {
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val hint = instrumentation.targetContext.getString(R.string.background_color_hint)
        awaitAction {
            instrumentation.uiAutomation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByText(hint)
                ?.firstOrNull {
                    it.packageName == instrumentation.targetContext.packageName &&
                        it.className == "android.widget.EditText"
                }?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments) == true
        }
    }

    private fun awaitAction(action: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (action()) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Background dialog action failed")
    }

    private fun setting(
        action: ((Result<BackgroundSettings>) -> Unit) -> Unit
    ): BackgroundSettings {
        val done = CountDownLatch(1)
        var result: Result<BackgroundSettings>? = null
        action {
            result = it
            done.countDown()
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        return requireNotNull(result).getOrThrow()
    }
}
