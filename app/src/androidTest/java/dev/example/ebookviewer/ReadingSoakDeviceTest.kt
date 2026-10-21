package dev.example.ebookviewer

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import android.util.AtomicFile
import android.view.View
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Opt-in twenty-minute run; excluded from the normal device suite and never opens user PDFs. */
class ReadingSoakDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation as P0Instrumentation
    private val ui get() = DeviceGestures(instrumentation)
    private val context get() = instrumentation.targetContext
    private val application get() = context.applicationContext as ReaderApplication
    private lateinit var activity: Activity
    private lateinit var viewer: PdfCanvasView
    private var started = 0L
    private var cycle = 0
    private var switches = 0
    private var returns = 0
    private var recreations = 0
    private var cachePeak = 0
    private val samples = JSONArray()
    private val positions = mutableMapOf<Int, ReadingPosition>()
    private val layouts = mutableMapOf<Int, PageLayoutSettings>()

    @Test
    fun twentyMinutesOfReadingKeepStateAndBoundedResources() {
        val files = List(2) { File.createTempFile("soak-fixture-", ".pdf", context.cacheDir) }
        val uris = files.map(Uri::fromFile)
        var active = 0
        var passed = false
        files.forEach(SyntheticDocument::write)
        try {
            activity = ui.launch()
            open(uris[active], active)
            started = SystemClock.elapsedRealtime()
            progress("running")
            repeat(40) { index ->
                cycle = index + 1
                guard()
                ui.onMain {
                    if (!activity.findViewById<View>(R.id.reader_tools).isShown) {
                        viewer.performClick()
                    }
                    activity.findViewById<View>(R.id.horizontal_pan_lock).performClick()
                }
                ui.await("Lock save did not finish") {
                    activity.findViewById<View>(R.id.horizontal_pan_lock).isEnabled
                }
                val forward = requireNotNull(ui.onMain { viewer.readingPosition() }).page < 90
                repeat(2) { ui.drag(viewer, forward) }
                ui.pinch(viewer, outward = ui.state(viewer).getDouble("zoom") < 2.0)
                ui.onMain { viewer.stopScrolling() }
                ui.awaitReady(activity)
                boundCache()
                if (cycle % 4 == 0) {
                    remember(active)
                    active = 1 - active
                    open(uris[active], active)
                    switches++
                }
                if (cycle % 8 == 0) recreate()
                while (SystemClock.elapsedRealtime() < started + cycle * 30_000L) {
                    SystemClock.sleep(500)
                }
                if (cycle % 2 == 0) backgroundSample(cycle / 2)
                progress("running")
            }
            remember(active)
            ui.onMain { activity.finish() }
            ui.await("Final reader did not close") { activity.isDestroyed }
            val stored = library {
                DocumentLibrary(File(context.filesDir, "reading-records.bin")).list()
            }
            for ((index, expected) in positions) {
                val record = stored.single { it.uri == uris[index].toString() }
                same(expected, requireNotNull(record.position))
                assertEquals(layouts[index], record.pageLayout)
            }
            assertEquals(20, samples.length())
            val warm = (3..7).map { samples.getJSONObject(it).getInt("pssKiB") }.sorted()[2]
            val late = (15..19).map { samples.getJSONObject(it).getInt("pssKiB") }.sorted()[2]
            val warmFd = (3..7).map { samples.getJSONObject(it).getInt("fdCount") }.sorted()[2]
            val lateFd = (15..19).map { samples.getJSONObject(it).getInt("fdCount") }.sorted()[2]
            // Background phases have empty tile caches; tolerate normal allocator/framework warming.
            assertTrue(
                "Background PSS growth exceeded observation budget",
                late - warm <= maxOf(32 * 1024, warm / 4)
            )
            assertTrue(
                "Background descriptor growth exceeded observation budget",
                lateFd - warmFd <= 16
            )
            assertTrue(SystemClock.elapsedRealtime() - started >= 1_200_000L)
            passed = true
        } finally {
            if (::activity.isInitialized) {
                ui.onMain { if (!activity.isDestroyed) activity.finish() }
                ui.await("Soak Activity did not close") { activity.isDestroyed }
            }
            library { store -> uris.forEach { store.remove(it.toString()) } }
            files.forEach(File::delete)
            progress(if (passed) "passed" else "failed")
        }
    }

    private fun open(uri: Uri, index: Int) {
        ui.onMain { (activity as MainActivity).openDocument(uri) }
        viewer = ui.awaitReady(activity)
        positions[index]?.let { same(it, requireNotNull(ui.onMain { viewer.readingPosition() })) }
        layouts[index]?.let { assertEquals(it, ui.onMain { viewer.pageLayoutSettings() }) }
    }

    private fun remember(index: Int) {
        ui.onMain {
            viewer.stopScrolling()
            positions[index] = requireNotNull(viewer.readingPosition())
            layouts[index] = viewer.pageLayoutSettings()
        }
    }

    private fun recreate() {
        val before = requireNotNull(ui.onMain { viewer.readingPosition() })
        val layout = ui.onMain { viewer.pageLayoutSettings() }
        val old = activity
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        try {
            ui.onMain { old.recreate() }
            ui.await("Soak Activity did not recreate") {
                val next = monitor.lastActivity
                if (next != null && next !== old && old.isDestroyed) {
                    activity = next
                    true
                } else {
                    false
                }
            }
            viewer = ui.awaitReady(activity)
            same(before, requireNotNull(ui.onMain { viewer.readingPosition() }))
            assertEquals(layout, ui.onMain { viewer.pageLayoutSettings() })
            recreations++
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun backgroundSample(minute: Int) {
        val before = requireNotNull(ui.onMain { viewer.readingPosition() })
        val layout = ui.onMain { viewer.pageLayoutSettings() }
        context.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        ui.await("Background reader retained tiles") {
            !activity.hasWindowFocus() && viewer.renderState().cachedBytes == 0
        }
        SystemClock.sleep(2_000)
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        val heap = Runtime.getRuntime()
        val fds = requireNotNull(File("/proc/self/fd").list()).size
        samples.put(
            JSONObject().put("minute", minute).put("elapsedSeconds", elapsed())
                .put("pssKiB", info.totalPss)
                .put("javaHeapBytes", heap.totalMemory() - heap.freeMemory())
                .put("nativeHeapBytes", Debug.getNativeHeapAllocatedSize()).put("fdCount", fds)
                .put("cacheBytes", ui.onMain { viewer.renderState().cachedBytes })
        )
        progress("background")
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        )
        ui.await("Soak reader did not resume") { activity.hasWindowFocus() }
        viewer = ui.awaitReady(activity)
        same(before, requireNotNull(ui.onMain { viewer.readingPosition() }))
        assertEquals(layout, ui.onMain { viewer.pageLayoutSettings() })
        boundCache()
        returns++
        guard()
    }

    private fun guard() {
        val memory = ActivityManager.MemoryInfo()
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        manager.getMemoryInfo(memory)
        assertTrue("System reported low memory; soak stopped", !memory.lowMemory)
        if (Build.VERSION.SDK_INT >= 29) {
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            assertTrue("Severe thermal status; soak stopped", power.currentThermalStatus < 3)
        }
    }

    private fun boundCache() {
        ui.onMain {
            val bytes = viewer.renderState().cachedBytes
            cachePeak = maxOf(cachePeak, bytes)
            assertTrue(bytes <= 64 * 1024 * 1024)
        }
    }

    private fun same(expected: ReadingPosition, actual: ReadingPosition) {
        assertEquals("Reading page changed", expected.page, actual.page)
        assertTrue("Reading X changed", abs(expected.x - actual.x) < 0.00001)
        assertTrue("Reading Y changed", abs(expected.y - actual.y) < 0.00001)
        assertTrue("Reading zoom changed", abs(expected.zoom - actual.zoom) < 0.00001)
    }

    private fun elapsed(): Long =
        if (started == 0L) 0 else (SystemClock.elapsedRealtime() - started) / 1_000

    private fun progress(phase: String) {
        val report = JSONObject().put("run", instrumentation.soakRun).put("phase", phase)
            .put("elapsedSeconds", elapsed()).put("cycles", cycle).put("documentSwitches", switches)
            .put("backgroundReturns", returns).put("recreations", recreations)
            .put("cachePeakBytes", cachePeak).put("samples", samples)
        val file = AtomicFile(File(context.cacheDir, "p3-reading-soak-progress.json"))
        val stream = file.startWrite()
        try {
            stream.write(report.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    private fun <T> library(action: (DocumentLibrary) -> T): T {
        val done = CountDownLatch(1)
        var result: Result<T>? = null
        application.library.execute(action) {
            result = it
            done.countDown()
        }
        assertTrue("Soak library operation did not finish", done.await(10, TimeUnit.SECONDS))
        return requireNotNull(result).getOrThrow()
    }
}
