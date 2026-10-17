package dev.example.ebookviewer

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.AtomicFile
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation
    private val ui get() = DeviceGestures(instrumentation)
    private val application
        get() = instrumentation.targetContext.applicationContext as ReaderApplication

    @Test
    fun unloadedPageWaitsAndLoadedNavigationKeepsZoomAndCacheBound() {
        ui.onMain {
            val view = PdfCanvasView(instrumentation.targetContext)
            view.layout(0, 0, 400, 800)
            view.restorePosition(ReadingPosition(1, 0.5, 0.5, 4.0))
            view.setPages(List(4) { PageSize(800, 800) })
            val before = requireNotNull(view.saveViewport())
            assertFalse(view.goToPage(50))
            assertEquals(before.getDouble("centerY"), view.saveViewport()?.getDouble("centerY"))
            view.setPages(List(100) { PageSize(800, 800) })
            assertTrue(view.goToPage(50, 0.7))
            val after = requireNotNull(view.saveViewport())
            assertEquals(4.0, after.getDouble("zoom"), 0.0)
            assertEquals(before.getDouble("centerX"), after.getDouble("centerX"), 0.0)
            assertEquals(50, requireNotNull(view.readingPosition()).page)
            assertTrue(view.renderState().visibleTiles < 16)
            view.reset()
        }
    }

    @Test
    fun bookmarkFileDeduplicatesRelinksRecoversAndPreservesUnknownBytes() {
        val directory = File.createTempFile(
            "bookmarks-test-",
            "",
            instrumentation.targetContext.cacheDir
        )
        directory.delete()
        directory.mkdirs()
        val file = File(directory, "bookmarks.bin")
        try {
            val store = BookmarkStore(file)
            store.put(Bookmark("a", 10, 0.4))
            store.put(Bookmark("a", 10, 0.7))
            store.put(Bookmark("b", 10, 0.3))
            assertEquals(listOf(Bookmark("a", 10, 0.7)), store.list("a"))
            AtomicFile(file).startWrite().use { it.write(byteArrayOf(1, 2, 3)) }
            val recovered = BookmarkStore(file)
            recovered.relink("a", "new")
            recovered.remove("b", 10)
            assertTrue(BookmarkStore(file).list("a").isEmpty())
            assertEquals(listOf(Bookmark("new", 10, 0.7)), BookmarkStore(file).list("new"))
            val bytes = file.readBytes().also { it[7] = 99 }
            file.writeBytes(bytes)
            assertThrows(IllegalArgumentException::class.java) { BookmarkStore(file) }
            assertTrue(bytes.contentEquals(file.readBytes()))
            val settingsFile = File(directory, "settings.bin")
            ReaderSettingsRepository { settingsFile }.use { repository ->
                assertTrue(visibility { repository.savePageNumbers(true, it) })
            }
            val pages = File(directory, "settings.bin.pages")
            val original = pages.readBytes().also { it[7] = 99 }
            pages.writeBytes(original)
            ReaderSettingsRepository { settingsFile }.use { repository ->
                val done = CountDownLatch(1)
                var failed = false
                repository.savePageNumbers(false) {
                    failed = it.isFailure
                    done.countDown()
                }
                assertTrue(done.await(10, TimeUnit.SECONDS))
                assertTrue(failed)
            }
            assertTrue(original.contentEquals(pages.readBytes()))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun pageNumberVisibilityCanBeEnabledHiddenAndRestoredAfterRelaunch() {
        val previous = visibility { application.settings.loadPageNumbers(it) }
        visibility { application.settings.savePageNumbers(false, it) }
        var activity = ui.launch()
        try {
            var view = ui.sample(activity)
            ui.onMain { assertFalse(activity.findViewById<View>(R.id.page_indicator).isShown) }
            controls(activity, view, R.string.settings)
            action(activity.getString(R.string.show_page_numbers))
            ui.await("Page indicator did not become visible") {
                activity.findViewById<View>(R.id.page_indicator).isShown
            }
            action(activity.getString(R.string.close_settings))
            val file = File(instrumentation.targetContext.filesDir, "reader-settings.bin")
            ReaderSettingsRepository { file }.use { repository ->
                assertTrue(visibility(repository::loadPageNumbers))
            }
            ui.onMain { activity.finish() }
            activity = ui.launch()
            view = ui.sample(activity)
            ui.await("Visible setting was not restored") {
                activity.findViewById<View>(R.id.page_indicator).isShown
            }
            controls(activity, view, R.string.settings)
            action(activity.getString(R.string.show_page_numbers))
            action(activity.getString(R.string.close_settings))
            ui.await("Page indicator did not hide") {
                !activity.findViewById<View>(R.id.page_indicator).isShown
            }
        } finally {
            ui.onMain { activity.finish() }
            visibility { application.settings.savePageNumbers(previous, it) }
        }
    }

    @Test
    fun pageJumpAndBookmarksKeepCurrentZoomAndSurviveActivityRelaunch() {
        val file = File.createTempFile(
            "navigation-pdf-",
            ".pdf",
            instrumentation.targetContext.cacheDir
        )
        SyntheticDocument.write(file)
        val uri = Uri.fromFile(file)
        var activity = ui.launch()
        try {
            ui.onMain { (activity as MainActivity).openDocument(uri) }
            var view = ui.awaitReady(activity)
            ui.pinch(view)
            val zoom = ui.state(view).getDouble("zoom")
            jump(activity, view, "0")
            setPage("70", activity)
            ui.await("Did not navigate to selected page", 30_000) {
                view.readingPosition()?.page == 69
            }
            assertEquals(zoom, ui.state(view).getDouble("zoom"), 0.000001)
            ui.drag(view, true)
            val bookmark = requireNotNull(ui.onMain { view.readingPosition() })
            controls(activity, view, R.string.bookmarks)
            action(activity.getString(R.string.add_bookmark))
            val saved = records { it.list(uri.toString()) }
            assertEquals(1, saved.size)
            assertEquals(bookmark.page, saved.single().page)
            ui.onMain { activity.finish() }
            activity = ui.launch()
            ui.onMain { (activity as MainActivity).openDocument(uri) }
            view = ui.awaitReady(activity)
            jump(activity, view, "10")
            ui.await("Did not jump away from bookmark") { view.readingPosition()?.page == 9 }
            ui.pinch(view)
            val currentZoom = ui.state(view).getDouble("zoom")
            controls(activity, view, R.string.bookmarks)
            val label = activity.getString(R.string.bookmark_page, bookmark.page + 1)
            action(label)
            ui.await("Bookmark did not restore internal anchor") {
                val position = view.readingPosition()
                position?.page == bookmark.page && kotlin.math.abs(position.y - bookmark.y) < 0.003
            }
            assertEquals(currentZoom, ui.state(view).getDouble("zoom"), 0.000001)
            controls(activity, view, R.string.bookmarks)
            action(label, AccessibilityNodeInfo.ACTION_LONG_CLICK)
            action(activity.getString(R.string.delete_bookmark))
            assertTrue(records { it.list(uri.toString()) }.isEmpty())
            assertTrue(file.exists())
            ui.assertCacheBound(view)
        } finally {
            ui.onMain { activity.finish() }
            records { store ->
                store.list(uri.toString()).forEach { store.remove(it.uri, it.page) }
            }
            val done = CountDownLatch(1)
            application.library.execute({ it.remove(uri.toString()) }) { done.countDown() }
            assertTrue(done.await(10, TimeUnit.SECONDS))
            file.delete()
        }
    }

    private fun jump(activity: Activity, view: PdfCanvasView, value: String) {
        controls(activity, view, R.string.go_to_page)
        setPage(value, activity)
    }

    private fun setPage(value: String, activity: Activity) {
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        val hint = activity.getString(R.string.page_number_hint, 100)
        awaitAction {
            instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(hint)
                ?.firstOrNull { it.className == "android.widget.EditText" }
                ?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments) == true
        }
        action(activity.getString(R.string.navigate))
    }

    private fun controls(activity: Activity, view: PdfCanvasView, resource: Int) {
        ui.tap(view)
        ui.onMain {
            ui.views(activity.window.decorView).filterIsInstance<Button>()
                .single { it.isShown && it.text == activity.getString(resource) }.performClick()
        }
    }

    private fun action(text: String, action: Int = AccessibilityNodeInfo.ACTION_CLICK) {
        awaitAction {
            instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)
                ?.firstOrNull {
                    it.packageName == instrumentation.targetContext.packageName &&
                        it.text?.toString() == text
                }?.performAction(action) == true
        }
        SystemClock.sleep(150)
    }

    private fun awaitAction(action: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (action()) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Navigation dialog action failed")
    }

    private fun visibility(action: ((Result<Boolean>) -> Unit) -> Unit): Boolean {
        val done = CountDownLatch(1)
        var result: Result<Boolean>? = null
        action {
            result = it
            done.countDown()
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        return requireNotNull(result).getOrThrow()
    }

    private fun <T> records(action: (BookmarkStore) -> T): T {
        val done = CountDownLatch(1)
        var result: Result<T>? = null
        application.bookmarks.execute(action) {
            result = it
            done.countDown()
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        return requireNotNull(result).getOrThrow()
    }
}
