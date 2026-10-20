package dev.example.ebookviewer

import android.net.Uri
import android.os.Process
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Run seed, force-stop the target package from adb, then run verify in a fresh process. */
class LibraryRestartDeviceTest {
    @Test
    fun readingPositionSurvivesASeparateProcess() {
        val instrumentation = PdfRendererDeviceTest.instrumentation
        val phase = (instrumentation as P0Instrumentation).restartPhase
        val context = instrumentation.targetContext
        val directory = File(context.cacheDir, "p1-test-restart").apply { mkdirs() }
        val pdf = File(directory, "restart-fixture.pdf")
        val expectedFile = File(directory, "expected.bin")
        val pidFile = File(directory, "process.txt")
        val uri = Uri.fromFile(pdf).toString()
        val ui = DeviceGestures(instrumentation)
        val activity = ui.launch()
        val repository = (activity.application as ReaderApplication).library
        var success = false
        try {
            if (phase == "seed") {
                SyntheticDocument.write(pdf)
                instrumentation.runOnMainSync {
                    (activity as MainActivity).openDocument(Uri.fromFile(pdf))
                }
                val view = ui.awaitReady(activity)
                ui.pinch(view)
                instrumentation.runOnMainSync {
                    view.setPageLayoutSettings(PageLayoutSettings(PageSizeMode.UNIFORM_WIDTH, 24))
                }
                repeat(8) { ui.drag(view, true) }
                instrumentation.runOnMainSync { activity.finish() }
                instrumentation.waitForIdleSync()
                val stored = io(repository) { it.list().single { record -> record.uri == uri } }
                assertTrue(requireNotNull(stored.position).page > 0)
                assertEquals(
                    PageLayoutSettings(PageSizeMode.UNIFORM_WIDTH, 24),
                    stored.pageLayout
                )
                expectedFile.outputStream().use { ReadingRecordsCodec.write(it, listOf(stored)) }
                pidFile.writeText(Process.myPid().toString())
            } else {
                assertEquals("verify", phase)
                assertNotEquals(pidFile.readText().toInt(), Process.myPid())
                val expectedRecord =
                    expectedFile.inputStream().use(ReadingRecordsCodec::read).single()
                val expected = requireNotNull(expectedRecord.position)
                ui.await("Restarted library entry missing") {
                    ui.views(activity.window.decorView).any {
                        it.id == R.id.home_record &&
                            it.contentDescription?.startsWith(pdf.name) == true
                    }
                }
                instrumentation.runOnMainSync {
                    ui.views(activity.window.decorView)
                        .first {
                            it.id == R.id.home_record &&
                                it.contentDescription?.startsWith(pdf.name) == true
                        }.performClick()
                }
                val view = ui.awaitReady(activity)
                val restored = ui.onMain {
                    assertEquals(expectedRecord.pageLayout, view.pageLayoutSettings())
                    requireNotNull(view.readingPosition())
                }
                assertEquals(expected.page, restored.page)
                assertEquals(expected.x, restored.x, 0.003)
                assertEquals(expected.y, restored.y, 0.003)
                assertEquals(expected.zoom, restored.zoom, 0.000001)
            }
            success = true
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
            if (phase == "verify" || !success) {
                io(repository) { it.remove(uri) }
                directory.deleteRecursively()
            }
        }
    }

    private fun <T> io(repository: LibraryRepository, task: (DocumentLibrary) -> T): T {
        val done = CountDownLatch(1)
        var result: Result<T>? = null
        repository.execute(task) {
            result = it
            done.countDown()
        }
        assertTrue("Library IO timed out", done.await(10, TimeUnit.SECONDS))
        return requireNotNull(result).getOrThrow()
    }
}
