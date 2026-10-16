package dev.example.ebookviewer

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import org.junit.runner.Description
import org.junit.runner.JUnitCore
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener

/** Small JUnit bridge for this fixed P0 suite; no extra application runtime dependencies. */
class P0Instrumentation : Instrumentation() {
    private var probe = false
    private var immersiveOnly = false
    var restartPhase: String? = null
        private set
    var probeSeconds = 30
        private set
    var probeRuns = 3
        private set
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        probe = arguments?.getString("probe") == "true"
        val suite = arguments?.getString("suite")
        require(suite == null || suite == "immersive")
        immersiveOnly = suite == "immersive"
        restartPhase = arguments?.getString("libraryRestart")
        require(restartPhase == null || restartPhase in listOf("seed", "verify"))
        require(!probe || restartPhase == null)
        require(!immersiveOnly || (!probe && restartPhase == null))
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
        val result = if (immersiveOnly) {
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
                ImmersiveDeviceTest::class.java
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
