package dev.example.ebookviewer

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.runner.Description
import org.junit.runner.JUnitCore
import org.junit.runner.Request
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener

/** Small JUnit bridge for this fixed P0 suite; no extra application runtime dependencies. */
class P0Instrumentation : Instrumentation() {
    private val finished = AtomicBoolean()
    private val watchdog = Executors.newSingleThreadScheduledExecutor()
    private var deadline: ScheduledFuture<*>? = null
    private var probe = false
    private var immersiveOnly = false
    private var backgroundOnly = false
    private var displayOnly = false
    private var settingsOnly = false
    private var sliderOnly = false
    private var guidanceOnly = false
    private var chromeOnly = false
    private var panLockOnly = false
    private var stateBoundaryOnly = false
    private var readingSoakOnly = false
    var soakRun = ""
        private set
    private var textOnly = false
    private var fontOnly = false
    private var navigationOnly = false
    private var layoutOnly = false
    private var accessOnly = false
    private var lifecycleOnly = false
    private var sharpnessOnly = false
    private var sharpnessPixelsOnly = false
    private var accessMethod: String? = null
    private var methodClass: Class<*>? = null
    var restartPhase: String? = null
        private set
    var probeSeconds = 30
        private set
    var probeRuns = 3
        private set
    var autoBackground = false
        private set
    var sharpnessMode = "normal"
        private set
    var complexSharpness = false
        private set
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        probe = arguments?.getString("probe") == "true"
        val suite = arguments?.getString("suite")
        require(
            suite == null ||
                suite in listOf(
                    "immersive",
                    "background",
                    "display",
                    "settings",
                    "slider",
                    "guidance",
                    "chrome",
                    "panLock",
                    "stateBoundary",
                    "readingSoak",
                    "text",
                    "font",
                    "navigation",
                    "layout",
                    "access",
                    "lifecycle",
                    "sharpness",
                    "sharpnessPixels"
                )
        )
        immersiveOnly = suite == "immersive"
        backgroundOnly = suite == "background"
        displayOnly = suite == "display"
        settingsOnly = suite == "settings"
        sliderOnly = suite == "slider"
        guidanceOnly = suite == "guidance"
        chromeOnly = suite == "chrome"
        panLockOnly = suite == "panLock"
        stateBoundaryOnly = suite == "stateBoundary"
        readingSoakOnly = suite == "readingSoak"
        soakRun = arguments?.getString("soakRun") ?: ""
        require(!readingSoakOnly || soakRun.matches(Regex("[a-f0-9-]{36}")))
        require(readingSoakOnly || soakRun.isEmpty())
        textOnly = suite == "text"
        fontOnly = suite == "font"
        navigationOnly = suite == "navigation"
        layoutOnly = suite == "layout"
        accessOnly = suite == "access"
        lifecycleOnly = suite == "lifecycle"
        sharpnessOnly = suite == "sharpness"
        sharpnessPixelsOnly = suite == "sharpnessPixels"
        sharpnessMode = arguments?.getString("sharpnessMode") ?: "normal"
        require(sharpnessMode in listOf("normal", "fling", "zoom32"))
        require(sharpnessMode == "normal" || sharpnessOnly)
        complexSharpness = arguments?.getString("complexSharpness") == "true"
        require(!complexSharpness || sharpnessOnly)
        autoBackground = arguments?.getString("autoBackground") == "true"
        require(!autoBackground || probe)
        accessMethod = arguments?.getString("method")
        methodClass = arguments?.getString("methodClass")?.let {
            when (it) {
                "gestures" -> GestureDeviceTest::class.java
                "settings" -> SettingsNavigationDeviceTest::class.java
                "chrome" -> ReaderChromeDeviceTest::class.java
                "usability" -> ReaderUsabilityDeviceTest::class.java
                "libraryFeatures" -> LibraryFeaturesDeviceTest::class.java
                "compact" -> CompactReaderDeviceTest::class.java
                else -> error("Unsupported individual test class")
            }
        }
        require(methodClass == null || (suite == null && !probe && accessMethod != null))
        require(accessMethod == null || accessOnly || methodClass != null)
        val allowedClass = methodClass ?: DocumentAccessDeviceTest::class.java
        val validMethod = allowedClass.declaredMethods.any {
            it.name == accessMethod && it.isAnnotationPresent(org.junit.Test::class.java)
        }
        require(accessMethod == null || validMethod)
        restartPhase = arguments?.getString("libraryRestart")
        require(restartPhase == null || restartPhase in listOf("seed", "verify"))
        require(methodClass == null || restartPhase == null)
        require(!probe || restartPhase == null)
        require(!immersiveOnly || (!probe && restartPhase == null))
        require(!backgroundOnly || (!probe && restartPhase == null))
        require(!displayOnly || (!probe && restartPhase == null))
        require(!settingsOnly || (!probe && restartPhase == null))
        require(!sliderOnly || (!probe && restartPhase == null))
        require(!guidanceOnly || (!probe && restartPhase == null))
        require(!chromeOnly || (!probe && restartPhase == null))
        require(!panLockOnly || (!probe && restartPhase == null))
        require(!stateBoundaryOnly || (!probe && restartPhase == null))
        require(!readingSoakOnly || (!probe && restartPhase == null))
        require(!textOnly || (!probe && restartPhase == null))
        require(!fontOnly || (!probe && restartPhase == null))
        require(!navigationOnly || (!probe && restartPhase == null))
        require(!layoutOnly || (!probe && restartPhase == null))
        require(!accessOnly || (!probe && restartPhase == null))
        require(!lifecycleOnly || (!probe && restartPhase == null))
        require(!sharpnessOnly || (!probe && restartPhase == null))
        require(!sharpnessPixelsOnly || (!probe && restartPhase == null))
        probeSeconds = (arguments?.getString("seconds")?.toIntOrNull() ?: 30).also {
            require(it in 10..600)
        }
        probeRuns = (arguments?.getString("runs")?.toIntOrNull() ?: 3).also {
            require(it in 1..3)
        }
        val maximumSeconds = when {
            probe -> probeSeconds * probeRuns + 120
            sharpnessOnly -> 1_200
            readingSoakOnly -> 1_320
            // The expanded 91-test UI suite outgrew the former 12-minute whole-run budget.
            suite == null && methodClass == null && restartPhase == null -> 900
            else -> 720
        }
        val deadlineSeconds = arguments?.getString("deadlineSeconds")?.let {
            requireNotNull(it.toIntOrNull()).also { value -> require(value in 1..maximumSeconds) }
        } ?: maximumSeconds
        // A lost host connection must not leave instrumentation launching more Activities.
        deadline = watchdog.schedule({
            complete(
                Activity.RESULT_CANCELED,
                Bundle().apply {
                    putInt("p0Failures", 1)
                    putBoolean("p0DeadlineExceeded", true)
                    putString(
                        REPORT_KEY_STREAMRESULT,
                        "Instrumentation deadline exceeded; incomplete\n"
                    )
                }
            )
        }, deadlineSeconds.toLong(), TimeUnit.SECONDS)
        start()
    }

    override fun onStart() {
        PdfRendererDeviceTest.instrumentation = this
        val core = JUnitCore()
        core.addListener(object : RunListener() {
            private var count = 0
            private var total = 0
            private var failure: Failure? = null

            override fun testRunStarted(description: Description) {
                total = description.testCount()
            }

            private fun status(description: Description): Bundle = Bundle().apply {
                putString("id", "P0Instrumentation")
                putString("class", description.className)
                putString("test", description.methodName)
                putInt("numtests", total)
                putInt("current", count)
            }

            override fun testStarted(description: Description) {
                count++
                failure = null
                sendStatus(1, status(description))
            }

            override fun testFailure(value: Failure) {
                failure = value
            }

            override fun testFinished(description: Description) {
                val result = status(description)
                failure?.let {
                    result.putString("stack", it.trace)
                    result.putString(REPORT_KEY_STREAMRESULT, it.trace)
                }
                sendStatus(if (failure == null) 0 else -2, result)
            }
        })
        val individualClass = methodClass
        val result = if (individualClass != null) {
            core.run(Request.method(individualClass, requireNotNull(accessMethod)))
        } else if (chromeOnly) {
            core.run(ReaderChromeDeviceTest::class.java)
        } else if (guidanceOnly) {
            core.run(ReadingGuidanceDeviceTest::class.java)
        } else if (sliderOnly) {
            core.run(PageScrubberDeviceTest::class.java)
        } else if (settingsOnly) {
            core.run(SettingsNavigationDeviceTest::class.java)
        } else if (fontOnly) {
            core.run(LargeFontDeviceTest::class.java)
        } else if (textOnly) {
            core.run(PageTextDeviceTest::class.java)
        } else if (sharpnessPixelsOnly) {
            core.run(SharpnessPixelsDeviceTest::class.java)
        } else if (sharpnessOnly) {
            core.run(SharpnessProbe::class.java)
        } else if (lifecycleOnly) {
            core.run(ReaderLifecycleDeviceTest::class.java)
        } else if (accessOnly) {
            val method = accessMethod
            if (method == null) {
                core.run(DocumentAccessDeviceTest::class.java)
            } else {
                core.run(Request.method(DocumentAccessDeviceTest::class.java, method))
            }
        } else if (readingSoakOnly) {
            core.run(ReadingSoakDeviceTest::class.java)
        } else if (stateBoundaryOnly) {
            core.run(ReadingStateBoundaryDeviceTest::class.java)
        } else if (panLockOnly) {
            core.run(HorizontalPanLockDeviceTest::class.java)
        } else if (layoutOnly) {
            core.run(PageLayoutDeviceTest::class.java)
        } else if (navigationOnly) {
            core.run(NavigationDeviceTest::class.java)
        } else if (backgroundOnly) {
            core.run(BackgroundDeviceTest::class.java)
        } else if (displayOnly) {
            core.run(DisplayDeviceTest::class.java)
        } else if (immersiveOnly) {
            core.run(ImmersiveDeviceTest::class.java)
        } else if (restartPhase != null) {
            core.run(LibraryRestartDeviceTest::class.java)
        } else if (probe) {
            core.run(
                DebugFrameProbe::class.java
            )
        } else {
            core.run(
                PdfRendererDeviceTest::class.java,
                GestureDeviceTest::class.java,
                LibraryDeviceTest::class.java,
                LibraryFeaturesDeviceTest::class.java,
                ImmersiveDeviceTest::class.java,
                BackgroundDeviceTest::class.java,
                DisplayDeviceTest::class.java,
                SettingsNavigationDeviceTest::class.java,
                PageScrubberDeviceTest::class.java,
                ReadingGuidanceDeviceTest::class.java,
                ReaderChromeDeviceTest::class.java,
                HorizontalPanLockDeviceTest::class.java,
                ReadingStateBoundaryDeviceTest::class.java,
                PageTextDeviceTest::class.java,
                LargeFontDeviceTest::class.java,
                NavigationDeviceTest::class.java,
                PageLayoutDeviceTest::class.java,
                DocumentAccessDeviceTest::class.java,
                ReaderLifecycleDeviceTest::class.java
            )
        }
        complete(
            Activity.RESULT_OK,
            Bundle().apply {
                putInt("p0Failures", result.failureCount)
                putInt("p0Tests", result.runCount)
                putString(
                    REPORT_KEY_STREAMRESULT,
                    "P0 tests=${result.runCount}, failures=${result.failureCount}\n"
                )
            }
        )
    }

    private fun complete(code: Int, result: Bundle) {
        if (!finished.compareAndSet(false, true)) return
        deadline?.cancel(false)
        watchdog.shutdown()
        finish(code, result)
    }
}
