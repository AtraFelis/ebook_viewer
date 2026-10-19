package dev.example.ebookviewer

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import org.junit.runner.Description
import org.junit.runner.JUnitCore
import org.junit.runner.Request
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener

/** Small JUnit bridge for this fixed P0 suite; no extra application runtime dependencies. */
class P0Instrumentation : Instrumentation() {
    private var probe = false
    private var immersiveOnly = false
    private var backgroundOnly = false
    private var navigationOnly = false
    private var layoutOnly = false
    private var accessOnly = false
    private var lifecycleOnly = false
    private var sharpnessOnly = false
    private var sharpnessPixelsOnly = false
    private var accessMethod: String? = null
    var restartPhase: String? = null
        private set
    var probeSeconds = 30
        private set
    var probeRuns = 3
        private set
    var autoBackground = false
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
        navigationOnly = suite == "navigation"
        layoutOnly = suite == "layout"
        accessOnly = suite == "access"
        lifecycleOnly = suite == "lifecycle"
        sharpnessOnly = suite == "sharpness"
        sharpnessPixelsOnly = suite == "sharpnessPixels"
        autoBackground = arguments?.getString("autoBackground") == "true"
        require(!autoBackground || probe)
        accessMethod = arguments?.getString("method")
        require(accessMethod == null || accessOnly)
        val validMethod = DocumentAccessDeviceTest::class.java.declaredMethods.any {
            it.name == accessMethod && it.isAnnotationPresent(org.junit.Test::class.java)
        }
        require(accessMethod == null || validMethod)
        restartPhase = arguments?.getString("libraryRestart")
        require(restartPhase == null || restartPhase in listOf("seed", "verify"))
        require(!probe || restartPhase == null)
        require(!immersiveOnly || (!probe && restartPhase == null))
        require(!backgroundOnly || (!probe && restartPhase == null))
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
        val result = if (sharpnessPixelsOnly) {
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
        } else if (layoutOnly) {
            core.run(PageLayoutDeviceTest::class.java)
        } else if (navigationOnly) {
            core.run(NavigationDeviceTest::class.java)
        } else if (backgroundOnly) {
            core.run(BackgroundDeviceTest::class.java)
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
                ImmersiveDeviceTest::class.java,
                BackgroundDeviceTest::class.java,
                NavigationDeviceTest::class.java,
                PageLayoutDeviceTest::class.java,
                DocumentAccessDeviceTest::class.java,
                ReaderLifecycleDeviceTest::class.java
            )
        }
        finish(
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
}
