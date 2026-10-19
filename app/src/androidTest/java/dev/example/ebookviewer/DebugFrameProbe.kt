package dev.example.ebookviewer

import android.annotation.TargetApi
import android.content.pm.ApplicationInfo
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-process debug/profile timing; first-tile readiness is not first presented-frame timing. */
@TargetApi(24)
class DebugFrameProbe {
    @Test
    fun warmGestureRuns() {
        require(Build.VERSION.SDK_INT >= 24) { "FrameMetrics requires API 24" }
        val instrumentation = PdfRendererDeviceTest.instrumentation as P0Instrumentation
        val ui = DeviceGestures(instrumentation)
        val context = instrumentation.targetContext
        val fixture = File(context.cacheDir, "p0-probe-fixture.pdf")
        if (!fixture.exists()) SyntheticDocument.write(fixture)
        val application = context.applicationContext as ReaderApplication
        val fixtureUri = Uri.fromFile(fixture).toString()
        var savedRecord: ReadingRecord? = null
        var librarySucceeded = false
        val snapshotDone = CountDownLatch(1)
        application.library.execute({ it.list().singleOrNull { row -> row.uri == fixtureUri } }) {
            librarySucceeded = it.isSuccess
            savedRecord = it.getOrNull()
            snapshotDone.countDown()
        }
        assertTrue("Synthetic record snapshot timeout", snapshotDone.await(5, TimeUnit.SECONDS))
        assertTrue("Synthetic record snapshot failed", librarySucceeded)
        val sha = MessageDigest.getInstance(
            "SHA-256"
        ).digest(fixture.readBytes()).joinToString("") {
            "%02x".format(it)
        }
        val runs = JSONArray()
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val report = JSONObject().put("kind", "in-process instrumentation; not cold start")
            .put("debuggable", debuggable).put("secondsPerRun", instrumentation.probeSeconds)
            .put("model", Build.MODEL).put("api", Build.VERSION.SDK_INT)
            .put("fixtureSha256", sha).put("fixtureBytes", fixture.length()).put("runs", runs)
            .put("autoBackground", instrumentation.autoBackground)
        val descriptor = ParcelFileDescriptor.open(fixture, ParcelFileDescriptor.MODE_READ_ONLY)
        val nativeStart = SystemClock.elapsedRealtime()
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (error: Exception) {
            descriptor.close()
            throw error
        }
        val nativeOpened = SystemClock.elapsedRealtime()
        renderer.use {
            repeat(it.pageCount) { index -> it.openPage(index).use { page -> page.width } }
        }
        report.put("isolatedNativeOpenMs", nativeOpened - nativeStart)
            .put("isolatedMetadataScanAndCloseMs", SystemClock.elapsedRealtime() - nativeOpened)
        val output = File(context.filesDir, "p0-debug-probe.json")
        output.writeText(report.toString(2))
        var primaryFailure: Throwable? = null
        try {
            repeat(instrumentation.probeRuns) { run ->
                val activity = ui.launch()
                val thread = HandlerThread("p0-frame-metrics").apply { start() }
                val handler = Handler(thread.looper)
                val durations = Collections.synchronizedList(ArrayList<Long>())
                val dropped = AtomicInteger()
                var listenerAttached = false
                val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropCount ->
                    durations.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION))
                    dropped.addAndGet(dropCount)
                }
                try {
                    val openStart = SystemClock.elapsedRealtime()
                    instrumentation.runOnMainSync {
                        (activity as MainActivity).openDocument(Uri.fromFile(fixture))
                    }
                    ui.await("Document layout not ready") {
                        ui.views(activity.window.decorView).filterIsInstance<PdfCanvasView>()
                            .single().renderState().visibleTiles > 0
                    }
                    val layoutMs = SystemClock.elapsedRealtime() - openStart
                    ui.await("First tile not ready") {
                        ui.views(activity.window.decorView).filterIsInstance<PdfCanvasView>()
                            .single().renderState().readyTiles > 0
                    }
                    val firstTileMs = SystemClock.elapsedRealtime() - openStart
                    val view = ui.awaitReady(activity)
                    instrumentation.runOnMainSync {
                        view.setBackgroundSettings(
                            BackgroundSettings(automatic = instrumentation.autoBackground)
                        )
                    }
                    val firstVisibleMs = SystemClock.elapsedRealtime() - openStart
                    ui.pinch(view)
                    repeat(4) { ui.drag(view, it < 2) }
                    ui.awaitReady(activity)
                    var refreshHz = 0f
                    instrumentation.runOnMainSync {
                        @Suppress("DEPRECATION")
                        refreshHz = activity.windowManager.defaultDisplay.refreshRate
                        activity.window.addOnFrameMetricsAvailableListener(listener, handler)
                        listenerAttached = true
                    }
                    val before = memory()
                    val fdBefore = File("/proc/self/fd").list()?.size ?: -1
                    var maxCache = 0
                    var missingSamples = 0
                    var uncoveredSamples = 0
                    var samples = 0
                    val memorySamples = JSONArray()
                    var nextMemoryAt = 0L
                    val start = SystemClock.elapsedRealtime()
                    while (SystemClock.elapsedRealtime() - start <
                        instrumentation.probeSeconds * 1_000L
                    ) {
                        ui.drag(view, samples % 8 < 4, fling = true)
                        if (samples % 8 == 0) ui.pinch(view, outward = samples % 16 == 0)
                        instrumentation.runOnMainSync {
                            val state = view.renderState()
                            maxCache = maxOf(maxCache, state.cachedBytes)
                            if (state.readyTiles < state.visibleTiles) missingSamples++
                            if (state.coveredPages < state.visiblePages) uncoveredSamples++
                        }
                        samples++
                        val now = SystemClock.elapsedRealtime() - start
                        if (now >= nextMemoryAt) {
                            memorySamples.put(memory().put("elapsedMs", now))
                            nextMemoryAt = now + 30_000
                        }
                    }
                    val elapsed = SystemClock.elapsedRealtime() - start
                    instrumentation.runOnMainSync {
                        activity.window.removeOnFrameMetricsAvailableListener(listener)
                        listenerAttached = false
                    }
                    thread.quitSafely()
                    thread.join(5_000)
                    assertTrue("Metrics callback thread did not stop", !thread.isAlive)
                    val values = synchronized(durations) { durations.filter { it >= 0 }.sorted() }
                    assertTrue("No rendered frames recorded", values.isNotEmpty())
                    fun percentile(fraction: Double): Double =
                        values[(ceil(values.size * fraction).toInt() - 1).coerceAtLeast(0)] /
                            1_000_000.0
                    val result = JSONObject().put("run", run + 1).put("elapsedMs", elapsed)
                        .put(
                            "refreshHz",
                            refreshHz
                        ).put("frames", values.size).put("droppedReports", dropped.get())
                        .put("p95Ms", percentile(0.95)).put("p99Ms", percentile(0.99))
                        .put("over50Ms", values.count { it > 50_000_000 })
                        .put("overRefreshBudget", values.count { it > 1_000_000_000.0 / refreshHz })
                        .put("layoutReadyMs", layoutMs).put("firstTileCachedMs", firstTileMs)
                        .put(
                            "firstVisibleTilesMs",
                            firstVisibleMs
                        ).put("memoryBefore", before).put("memoryAfter", memory())
                        .put("fdBefore", fdBefore).put(
                            "fdAfter",
                            File("/proc/self/fd").list()?.size ?: -1
                        )
                        .put(
                            "maxCacheBytes",
                            maxCache
                        ).put("gestureSamples", samples).put("missingTileSamples", missingSamples)
                        .put("uncoveredPageSamples", uncoveredSamples)
                        .put("frameDurationNsSorted", JSONArray(values))
                        .put("memorySamples", memorySamples)
                    runs.put(result)
                    output.writeText(report.toString(2))
                    assertTrue("Cache limit exceeded", maxCache <= 64 * 1024 * 1024)
                } finally {
                    instrumentation.runOnMainSync {
                        if (listenerAttached) {
                            activity.window.removeOnFrameMetricsAvailableListener(
                                listener
                            )
                        }
                        activity.finish()
                    }
                    thread.quitSafely()
                    ui.await("Probe Activity did not finish") { activity.isDestroyed }
                }
            }
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            finishProbe(
                primaryFailure,
                {
                    val restoreDone = CountDownLatch(1)
                    application.library.execute({ store ->
                        val previous = savedRecord
                        if (previous == null) store.remove(fixtureUri) else store.put(previous)
                    }) {
                        librarySucceeded = it.isSuccess
                        restoreDone.countDown()
                    }
                    assertTrue(
                        "Synthetic record restore timeout",
                        restoreDone.await(5, TimeUnit.SECONDS)
                    )
                    assertTrue("Synthetic record restore failed", librarySucceeded)
                },
                {
                    // Keep this generated fixture so before/after runs use identical bytes and SHA.
                    output.writeText(report.toString(2))
                },
                {
                    val compact = JSONObject(report.toString())
                    val compactRuns = compact.getJSONArray("runs")
                    for (index in 0 until compactRuns.length()) {
                        compactRuns.getJSONObject(index).remove("frameDurationNsSorted")
                    }
                    instrumentation.sendStatus(
                        2,
                        Bundle().apply {
                            putString("p0Report", compact.toString())
                        }
                    )
                }
            )
        }
    }

    private fun memory(): JSONObject {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        return JSONObject().put("totalPssKb", info.totalPss).put("dalvikPssKb", info.dalvikPss)
            .put("nativePssKb", info.nativePss).put("otherPssKb", info.otherPss)
    }
}

/** Attempt every cleanup/report step without replacing an in-flight measurement failure. */
internal fun finishProbe(primaryFailure: Throwable?, vararg steps: () -> Unit) {
    var failure = primaryFailure
    for (step in steps) {
        try {
            step()
        } catch (error: Throwable) {
            val previous = failure
            if (previous == null) {
                failure = error
            } else if (previous !== error) {
                previous.addSuppressed(error)
            }
        }
    }
    if (primaryFailure == null) failure?.let { throw it }
}
