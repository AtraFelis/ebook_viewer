package dev.example.ebookviewer

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.Instrumentation
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.SystemClock
import android.system.Os
import android.view.View
import android.widget.Button
import android.widget.GridView
import android.widget.ImageButton
import android.widget.ListView
import java.io.DataOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentHistoryDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation
    private val ui get() = DeviceGestures(instrumentation)
    private val app get() = instrumentation.targetContext.applicationContext as ReaderApplication

    @Test
    fun homeLongPressOnlyForgetsRecentsAndRegisteredDocumentsStillResume() = automaticOrientation {
        library { it.snapshot() }
        val originals = List(2) {
            File.createTempFile("recent-fixture-", ".pdf", app.cacheDir)
                .also(SyntheticDocument::write)
        }
        val records = originals.mapIndexed { index, file ->
            val token = file.inputStream().use { app.managedPdfs.copy(it) {} }
            ReadingRecord(
                Uri.fromFile(file).toString(),
                file.name,
                System.currentTimeMillis() + 10_000 - index,
                100,
                ReadingPosition(3, 0.5, 0.3, 2.0),
                managedCopy = token
            )
        }
        val uris = records.map { it.uri }.toSet()
        val shelf = "recent-${UUID.randomUUID()}"
        library {
            it.addShelf(Bookshelf(shelf, "Recent fixture"))
            it.addAll(records, shelf)
        }
        records.forEach { record -> bookmark { it.put(Bookmark(record.uri, 3, 0.3)) } }
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        var activity = ui.launch()
        try {
            ui.await("Both fixture recents were not visible") {
                records.all { recentButton(activity, it) != null }
            }
            ui.onMain { requireNotNull(recentButton(activity, records[0])).performLongClick() }
            awaitDialog(activity, "libraryWorkflow")
            click(workflow(activity), activity, android.R.string.cancel)
            assertFalse(records[0].uri in library { it.snapshot().hiddenRecents })
            // Exercise the compact row first, while the larger resume card is still present.
            for (record in records.reversed()) {
                ui.onMain { requireNotNull(recentButton(activity, record)).performLongClick() }
                awaitDialog(activity, "libraryWorkflow")
                click(workflow(activity), activity, R.string.library_confirm_delete)
                ui.await("Forgotten recent did not disappear") {
                    recentButton(activity, record) == null
                }
                val preserved = library { store -> store.list().single { it.uri == record.uri } }
                assertEquals(record, preserved)
                assertTrue(app.managedPdfs.file(requireNotNull(record.managedCopy)).exists())
                assertEquals(listOf(Bookmark(record.uri, 3, 0.3)), bookmark { it.list(record.uri) })
            }
            val disk = File(app.filesDir, "reading-records.bin").inputStream()
                .use(ReadingRecordsCodec::readCatalog)
            assertTrue(disk.hiddenRecents.containsAll(uris))
            assertTrue(disk.visible(shelf).map { it.uri }.containsAll(uris))
            val previous = activity
            ui.onMain { activity.recreate() }
            ui.await("Home did not recreate") {
                val latest = monitor.lastActivity ?: activity
                latest !== previous && !latest.isDestroyed && latest.hasWindowFocus()
            }
            activity = requireNotNull(monitor.lastActivity)
            ui.await("Recents returned unexpectedly after recreation") {
                activity.findViewById<View>(R.id.recent_documents) != null &&
                    records.none { recentButton(activity, it) != null }
            }
            ui.onMain {
                ui.views(activity.window.decorView).filterIsInstance<Button>().single {
                    it.isShown && it.text == activity.getString(R.string.all_documents)
                }.performClick()
            }
            val record = records[1]
            ui.await("Registered document disappeared from all documents") {
                val grid = activity.findViewById<GridView>(R.id.library_grid)
                grid != null &&
                    (0 until grid.count).any {
                        (grid.adapter.getItem(it) as? ReadingRecord)?.uri == record.uri
                    }
            }
            ui.onMain {
                val grid = activity.findViewById<GridView>(R.id.library_grid)
                val index = (0 until grid.count).first {
                    (grid.adapter.getItem(it) as? ReadingRecord)?.uri == record.uri
                }
                grid.setSelection(index)
            }
            ui.await("Registered document cell was not attached") {
                ui.views(activity.window.decorView).any {
                    it.id == R.id.home_record &&
                        it.isShown &&
                        it.contentDescription?.startsWith(record.title) == true
                }
            }
            ui.onMain {
                ui.views(activity.window.decorView).first {
                    it.id == R.id.home_record &&
                        it.isShown &&
                        it.contentDescription?.startsWith(record.title) == true
                }.performClick()
            }
            val view = ui.awaitReady(activity)
            assertEquals(3, ui.onMain { requireNotNull(view.readingPosition()).page })
            assertEquals(2.0, ui.onMain { requireNotNull(view.readingPosition()).zoom }, 0.000001)
            ui.onMain { activity.findViewById<View>(R.id.reader_home).performClick() }
            assertFalse(record.uri in library { it.snapshot().hiddenRecents })
            // Returning to the library preserves its all-documents screen; recents live on home.
            ui.onMain {
                ui.views(activity.window.decorView).filterIsInstance<ImageButton>().single {
                    it.isShown && it.contentDescription == activity.getString(R.string.library_back)
                }.performClick()
            }
            ui.await("Actual reopening did not restore the recent") {
                recentButton(activity, record) != null
            }
            assertEquals(setOf(shelf), library { it.snapshot().memberships[record.uri] })
        } finally {
            ui.onMain {
                (monitor.lastActivity ?: activity).finish()
                activity.finish()
            }
            instrumentation.waitForIdleSync()
            instrumentation.removeMonitor(monitor)
            library {
                it.removeAll(uris)
                it.removeShelf(shelf)
            }
            records.forEach { record -> bookmark { it.remove(record.uri, 3) } }
            originals.forEach { it.delete() }
        }
    }

    @Test
    fun hidingRecentsBacksUpVersionSevenAndFailedWritesKeepThemVisible() {
        val directory = File(app.cacheDir, "recent-store-${UUID.randomUUID()}").apply {
            mkdirs()
        }
        val path = File(directory, "records.bin")
        try {
            DataOutputStream(path.outputStream()).use {
                it.writeInt(0x45565231)
                it.writeInt(7)
                it.writeInt(0)
                repeat(3) { _ -> it.writeInt(0) }
            }
            val legacy = path.readBytes()
            val store = DocumentLibrary(path)
            val original = File(directory, "original.pdf").also(SyntheticDocument::write)
            val token = original.inputStream().use { store.copies.copy(it) {} }
            val record = ReadingRecord(
                Uri.fromFile(original).toString(),
                original.name,
                1,
                100,
                ReadingPosition(3, 0.5, 0.3, 2.0),
                managedCopy = token
            )
            store.put(record)
            assertArrayEquals(legacy, File(directory, "records.bin.before-v8").readBytes())
            Os.chmod(directory.path, 320)
            try {
                assertThrows(Exception::class.java) { store.hideRecent(setOf(record.uri)) }
                assertEquals(listOf(record), store.snapshot().recentlyRead())
                assertTrue(store.copies.file(token).exists())
            } finally {
                Os.chmod(directory.path, 448)
            }
            store.hideRecent(setOf(record.uri))
            store.saveReading(record, null)
            val restored = DocumentLibrary(path)
            assertTrue(restored.snapshot().recentlyRead().isEmpty())
            assertEquals(listOf(record), restored.list())
            assertTrue(restored.copies.file(token).exists())
            restored.remove(record.uri)
            assertFalse(restored.copies.file(token).exists())
            assertTrue(original.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun readerSettingsUseTheSystemPolicyAndClosingRestoresTheReaderAnchor() = automaticOrientation {
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        var activity = ui.launch()
        try {
            val systemOrientation = activity.resources.configuration.orientation
            val target = if (systemOrientation == Configuration.ORIENTATION_PORTRAIT) {
                OrientationMode.LANDSCAPE
            } else {
                OrientationMode.PORTRAIT
            }
            openSettings(activity)
            openOrientationChild(activity)
            choose(activity, target)
            click(dialog(activity, "settingsDialog"), activity, R.string.close_settings)
            click(dialog(activity, "settingsHomeDialog"), activity, R.string.close_settings)
            ui.onMain { (activity as MainActivity).openSample() }
            activity = awaitReader(activity, monitor, target)
            val view = ui.awaitReady(activity)
            ui.pinch(view)
            repeat(2) { ui.drag(view, true) }
            val before = ui.onMain { requireNotNull(view.readingPosition()) }
            ui.tap(view)
            openSettings(activity)
            activity = awaitSettings(activity, monitor, systemOrientation)
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)
            openOrientationChild(activity)
            choose(activity, target)
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)
            val previous = activity
            ui.onMain { activity.recreate() }
            ui.await("Settings child did not recreate under the system policy") {
                val latest = monitor.lastActivity ?: activity
                latest !== previous &&
                    !latest.isDestroyed &&
                    latest.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED &&
                    dialog(latest, "settingsDialog") !== dialog(latest, "settingsHomeDialog") &&
                    dialog(latest, "settingsDialog")?.window?.decorView?.hasWindowFocus() == true
            }
            activity = requireNotNull(monitor.lastActivity)
            click(dialog(activity, "settingsDialog"), activity, R.string.close_settings)
            click(dialog(activity, "settingsHomeDialog"), activity, R.string.close_settings)
            activity = awaitReader(activity, monitor, target)
            val restoredView = ui.awaitReady(activity)
            val restored = ui.onMain { requireNotNull(restoredView.readingPosition()) }
            assertEquals(before.page, restored.page)
            assertEquals(before.zoom, restored.zoom, 0.000001)
            assertEquals(before.y, restored.y, 0.03)
        } finally {
            ui.onMain {
                (monitor.lastActivity ?: activity).finish()
                activity.finish()
            }
            instrumentation.waitForIdleSync()
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun recentButton(activity: Activity, record: ReadingRecord): Button? =
        activity.findViewById<View>(R.id.recent_documents)?.let { root ->
            ui.views(root).filterIsInstance<Button>().firstOrNull {
                it.isShown && it.contentDescription?.contains(record.title) == true
            }
        }

    private fun workflow(activity: Activity): Dialog? {
        val value = MainActivity::class.java.getDeclaredField("libraryWorkflow")
            .apply { isAccessible = true }.get(activity)
        return LibraryWorkflow::class.java.getDeclaredField("dialog")
            .apply { isAccessible = true }.get(value) as? Dialog
    }

    private fun dialog(activity: Activity, name: String): AlertDialog? = MainActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(activity) as? AlertDialog

    private fun awaitDialog(activity: Activity, field: String) {
        ui.await("Recent history confirmation was not shown") {
            (if (field == "libraryWorkflow") workflow(activity) else dialog(activity, field))
                ?.isShowing == true
        }
    }

    private fun click(value: Dialog?, activity: Activity, resource: Int) = ui.onMain {
        ui.views(requireNotNull(requireNotNull(value).window).decorView).filterIsInstance<Button>()
            .single { it.isShown && it.text == activity.getString(resource) }.performClick()
    }

    private fun openSettings(activity: Activity) = ui.onMain {
        val label = activity.getString(R.string.settings)
        ui.views(activity.window.decorView).first {
            it.isShown &&
                when (it) {
                    is Button -> it.text == label
                    is ImageButton -> it.contentDescription == label
                    else -> false
                }
        }.performClick()
    }

    private fun openOrientationChild(activity: Activity) {
        ui.await("Settings root missing") {
            dialog(activity, "settingsHomeDialog")?.isShowing == true
        }
        click(dialog(activity, "settingsHomeDialog"), activity, R.string.orientation_settings)
        ui.await("Orientation child missing") {
            dialog(activity, "settingsDialog") !== dialog(activity, "settingsHomeDialog") &&
                dialog(activity, "settingsDialog")?.isShowing == true
        }
    }

    private fun choose(activity: Activity, mode: OrientationMode) {
        ui.onMain {
            val window = requireNotNull(dialog(activity, "settingsDialog")).window
            val root = requireNotNull(window).decorView
            ui.views(root).filterIsInstance<ListView>().single()
                .performItemClick(null, mode.ordinal, mode.ordinal.toLong())
        }
        ui.await("Orientation selection did not save") {
            val root = dialog(activity, "settingsDialog")?.window?.decorView ?: return@await false
            ui.views(root).filterIsInstance<ListView>().single().let {
                it.isEnabled && it.checkedItemPosition == mode.ordinal
            }
        }
    }

    private fun awaitSettings(
        initial: Activity,
        monitor: Instrumentation.ActivityMonitor,
        expectedOrientation: Int
    ): Activity {
        var latest = initial
        ui.await("Settings did not use the system screen orientation") {
            latest = monitor.lastActivity ?: initial
            !latest.isDestroyed &&
                latest.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED &&
                latest.resources.configuration.orientation == expectedOrientation &&
                dialog(latest, "settingsHomeDialog")?.window?.decorView?.hasWindowFocus() == true
        }
        return latest
    }

    private fun awaitReader(
        initial: Activity,
        monitor: Instrumentation.ActivityMonitor,
        mode: OrientationMode
    ): Activity {
        val requested = if (mode == OrientationMode.LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        val expected = if (mode == OrientationMode.LANDSCAPE) {
            Configuration.ORIENTATION_LANDSCAPE
        } else {
            Configuration.ORIENTATION_PORTRAIT
        }
        var latest = initial
        var stable = SystemClock.uptimeMillis()
        ui.await("Reader lock was not restored") {
            val next = monitor.lastActivity ?: initial
            val ready = !next.isDestroyed &&
                next.requestedOrientation == requested &&
                next.resources.configuration.orientation == expected &&
                next.hasWindowFocus()
            if (!ready || next !== latest) stable = SystemClock.uptimeMillis()
            latest = next
            ready && SystemClock.uptimeMillis() - stable >= 250
        }
        return latest
    }

    private fun automaticOrientation(action: () -> Unit) {
        val original = result<OrientationMode>(app.settings::load)
        result<OrientationMode> { app.settings.save(OrientationMode.AUTO, it) }
        try {
            action()
        } finally {
            result<OrientationMode> { app.settings.save(original, it) }
        }
    }

    private fun <T> library(action: (DocumentLibrary) -> T): T = result { done ->
        app.library.execute(action, done)
    }
    private fun <T> bookmark(action: (BookmarkStore) -> T): T = result { done ->
        app.bookmarks.execute(action, done)
    }

    private fun <T> result(start: ((Result<T>) -> Unit) -> Unit): T {
        var value: Result<T>? = null
        val latch = CountDownLatch(1)
        start {
            value = it
            latch.countDown()
        }
        assertTrue(latch.await(20, TimeUnit.SECONDS))
        return requireNotNull(value).getOrThrow()
    }
}
