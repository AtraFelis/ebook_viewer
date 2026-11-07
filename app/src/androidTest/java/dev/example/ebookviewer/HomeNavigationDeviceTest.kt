package dev.example.ebookviewer

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Opt-in home regressions; only test-created files/metadata are added to the existing catalog. */
class HomeNavigationDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation
    private val ui get() = DeviceGestures(instrumentation)
    private val application get() =
        instrumentation.targetContext.applicationContext as ReaderApplication

    @Test
    fun readerLockIsReleasedOnHomeAndReturnsWithTheSavedDocumentAnchor() {
        val previous = setting<OrientationMode>(application.settings::load)
        setting<OrientationMode> { application.settings.save(OrientationMode.LANDSCAPE, it) }
        val file = File.createTempFile("home-orientation-", ".pdf", application.cacheDir)
            .also(SyntheticDocument::write)
        val uri = Uri.fromFile(file)
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        var activity = ui.launch()
        try {
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)
            ui.onMain { (activity as MainActivity).openDocument(uri) }
            activity = awaitDestination(activity, monitor, home = false)
            val viewer = ui.awaitReady(activity)
            ui.pinch(viewer)
            repeat(2) { ui.drag(viewer, true) }
            val before = ui.onMain { requireNotNull(viewer.readingPosition()) }
            ui.onMain { activity.findViewById<View>(R.id.reader_home).performClick() }
            activity = awaitDestination(activity, monitor, home = true)
            assertEquals(
                OrientationMode.LANDSCAPE,
                setting<OrientationMode>(application.settings::load)
            )
            val previousActivity = activity
            ui.onMain { activity.recreate() }
            ui.await("Home recreation did not complete") {
                val latest = monitor.lastActivity ?: activity
                latest !== previousActivity && !latest.isDestroyed && latest.hasWindowFocus()
            }
            activity = awaitDestination(activity, monitor, home = true)
            ui.await("Recent document did not return after home recreation") {
                activity.findViewById<View>(R.id.recent_documents)?.let { recents ->
                    ui.views(recents).any {
                        it.id == R.id.home_record &&
                            it.contentDescription?.contains(file.name) == true
                    }
                } == true
            }
            ui.onMain {
                ui.views(activity.findViewById(R.id.recent_documents)).first {
                    it.id == R.id.home_record &&
                        it.contentDescription?.contains(file.name) == true
                }.performClick()
            }
            activity = awaitDestination(activity, monitor, home = false)
            val restored = ui.awaitReady(activity)
            val after = ui.onMain { requireNotNull(restored.readingPosition()) }
            assertEquals(before.page, after.page)
            assertEquals(before.zoom, after.zoom, 0.000001)
            assertEquals(before.y, after.y, 0.02)
        } finally {
            ui.onMain {
                (monitor.lastActivity ?: activity).finish()
                activity.finish()
            }
            instrumentation.removeMonitor(monitor)
            setting<Unit> { done ->
                application.library.execute({ it.remove(uri.toString()) }, done)
            }
            file.delete()
            setting<OrientationMode> { application.settings.save(previous, it) }
        }
    }

    @Test
    fun changingTheReaderChoiceOnHomeKeepsTheHomePolicyAndItsSettingsOpen() {
        val previous = setting<OrientationMode>(application.settings::load)
        setting<OrientationMode> { application.settings.save(OrientationMode.AUTO, it) }
        val activity = ui.launch()
        try {
            val original = activity.resources.configuration.orientation
            val target = if (original == Configuration.ORIENTATION_PORTRAIT) {
                OrientationMode.LANDSCAPE
            } else {
                OrientationMode.PORTRAIT
            }
            ui.onMain {
                ui.views(activity.window.decorView).filterIsInstance<ImageButton>().single {
                    it.isShown && it.contentDescription == activity.getString(R.string.settings)
                }.performClick()
            }
            ui.await("Home settings did not open") {
                dialog(activity, "settingsHomeDialog") != null
            }
            ui.onMain {
                val parent = requireNotNull(dialog(activity, "settingsHomeDialog"))
                val buttons = ui.views(requireNotNull(parent.window).decorView)
                    .filterIsInstance<Button>()
                buttons.single {
                    it.text == activity.getString(R.string.orientation_settings)
                }.performClick()
            }
            var child: AlertDialog? = null
            ui.await("Orientation settings did not open") {
                child = dialog(activity, "settingsDialog")
                child?.isShowing == true && child !== dialog(activity, "settingsHomeDialog")
            }
            ui.onMain {
                ui.views(requireNotNull(requireNotNull(child).window).decorView)
                    .filterIsInstance<ListView>().single()
                    .performItemClick(null, target.ordinal, target.ordinal.toLong())
            }
            ui.await("Home choice did not finish saving") {
                val list = ui.views(requireNotNull(requireNotNull(child).window).decorView)
                    .filterIsInstance<ListView>().single()
                list.isEnabled && list.checkedItemPosition == target.ordinal
            }
            assertEquals(target, setting<OrientationMode>(application.settings::load))
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)
            assertEquals(original, activity.resources.configuration.orientation)
            assertTrue(requireNotNull(child).isShowing)
        } finally {
            ui.onMain { activity.finish() }
            setting<OrientationMode> { application.settings.save(previous, it) }
        }
    }

    @Test
    fun recentRowsKeepFiveTitlesAndCallbacksAtLargeFontsAndBothWidths() {
        val activity = ui.launch()
        val records = (1..7).map {
            ReadingRecord(
                "fixture:$it",
                "Public recent $it",
                it.toLong(),
                100,
                ReadingPosition(it, 0.5, 0.5, 2.0)
            )
        }
        val unread = ReadingRecord("unread", "Unread fixture", 100)
        try {
            for (scale in listOf(1f, 1.5f, 2f)) {
                for (width in listOf(320, 640)) {
                    ui.onMain {
                        val configuration = Configuration(activity.resources.configuration).apply {
                            fontScale = scale
                            screenWidthDp = width
                            screenHeightDp = if (width == 320) 640 else 320
                        }
                        val context = ContextThemeWrapper(activity, R.style.ReaderLight).apply {
                            applyOverrideConfiguration(configuration)
                        }
                        var opened: ReadingRecord? = null
                        var removed: ReadingRecord? = null
                        val header = ReaderMainHeader(
                            context,
                            {},
                            {},
                            { opened = it },
                            { removed = it }
                        )
                        header.render(LibraryCatalog(listOf(unread) + records))
                        val scroll = ScrollView(context).apply { addView(header.view) }
                        val style = ReaderStyle(context)
                        scroll.measure(
                            View.MeasureSpec.makeMeasureSpec(
                                style.dp(width),
                                View.MeasureSpec.EXACTLY
                            ),
                            View.MeasureSpec.makeMeasureSpec(
                                style.dp(320),
                                View.MeasureSpec.EXACTLY
                            )
                        )
                        scroll.layout(0, 0, scroll.measuredWidth, scroll.measuredHeight)
                        val buttons = ui.views(header.view).filterIsInstance<Button>()
                            .filter { it.id == R.id.home_record }
                        assertEquals(5, buttons.size)
                        assertTrue(buttons.first().contentDescription.contains("Public recent 7"))
                        assertTrue(buttons.last().text.contains("Public recent 3"))
                        assertTrue(buttons.all { it.height >= style.dp(48) && it.width > 0 })
                        buttons.last().performClick()
                        assertEquals(records[2], opened)
                        buttons.last().performLongClick()
                        assertEquals(records[2], removed)
                        header.render(LibraryCatalog(records).remove(setOf("fixture:7")))
                        val updated = ui.views(header.view).filter { it.id == R.id.home_record }
                        assertEquals(5, updated.size)
                        assertTrue(updated.first().contentDescription.contains("Public recent 6"))
                    }
                }
            }
        } finally {
            ui.onMain { activity.finish() }
        }
    }

    @Test
    fun allDocumentsAndShelfMenusHideTheSampleWhileTheInternalFixtureStillOpens() {
        val previous = setting<OrientationMode>(application.settings::load)
        setting<OrientationMode> { application.settings.save(OrientationMode.AUTO, it) }
        val activity = ui.launch()
        var home: ReaderHomeView? = null
        try {
            val catalog = LibraryCatalog().addShelf(Bookshelf("fixture", "Public shelf"))
            for (filter in listOf(null, "fixture")) {
                ui.onMain {
                    home?.close()
                    home = ReaderHomeView(
                        activity, TextView(activity), Button(activity), Button(activity),
                        {}, {}, {}, {}, {}, {}, { _, _, _ -> }, {}
                    ).also {
                        it.restoreState(
                            Bundle().apply {
                                putBoolean("home", false)
                                putString("shelf", filter)
                            }
                        )
                        it.render(catalog)
                        activity.setContentView(it.view)
                    }
                }
                ui.await("Document menu is not ready") {
                    ui.views(requireNotNull(home).view).filterIsInstance<ImageButton>().any {
                        it.isShown &&
                            it.contentDescription == activity.getString(R.string.library_more)
                    }
                }
                ui.onMain {
                    ui.views(requireNotNull(home).view).filterIsInstance<ImageButton>().single {
                        it.isShown &&
                            it.contentDescription == activity.getString(R.string.library_more)
                    }.performClick()
                }
                ui.await("Library popup did not open") {
                    val text = activity.getString(R.string.library_refresh_covers)
                    instrumentation.uiAutomation.rootInActiveWindow
                        ?.findAccessibilityNodeInfosByText(text)
                        ?.isNotEmpty() == true
                }
                assertTrue(
                    instrumentation.uiAutomation.rootInActiveWindow
                        ?.findAccessibilityNodeInfosByText(activity.getString(R.string.sample_pdf))
                        .orEmpty().isEmpty()
                )
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            }
        } finally {
            ui.onMain {
                home?.close()
                activity.finish()
            }
            setting<OrientationMode> { application.settings.save(previous, it) }
        }
        setting<OrientationMode> { application.settings.save(OrientationMode.AUTO, it) }
        val reader = ui.launch()
        try {
            assertTrue(ui.sample(reader).isShown)
        } finally {
            ui.onMain { reader.finish() }
            setting<OrientationMode> { application.settings.save(previous, it) }
        }
    }

    private fun awaitDestination(
        initial: Activity,
        monitor: android.app.Instrumentation.ActivityMonitor,
        home: Boolean
    ): Activity {
        var latest = initial
        var stableSince = SystemClock.uptimeMillis()
        ui.await("Destination orientation did not settle", 30_000) {
            val current = monitor.lastActivity ?: initial
            if (latest !== current) stableSince = SystemClock.uptimeMillis()
            latest = current
            val requested = if (home) {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            val landscape = latest.resources.configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE
            !latest.isDestroyed &&
                latest.hasWindowFocus() &&
                latest.requestedOrientation == requested &&
                latest.findViewById<View>(R.id.home_screen).isShown == home &&
                (home || landscape) &&
                SystemClock.uptimeMillis() - stableSince >= 250
        }
        return latest
    }

    private fun dialog(activity: Activity, name: String): AlertDialog? =
        MainActivity::class.java.getDeclaredField(name).let {
            it.isAccessible = true
            it.get(activity) as AlertDialog?
        }

    private fun <T> setting(action: ((Result<T>) -> Unit) -> Unit): T {
        val done = CountDownLatch(1)
        var result: Result<T>? = null
        action {
            result = it
            done.countDown()
        }
        assertTrue(done.await(15, TimeUnit.SECONDS))
        return requireNotNull(result).getOrThrow()
    }
}
