package dev.example.ebookviewer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Public generated fixtures only. No private PDF, global accessibility setting, or text logging. */
class PageTextDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation
    private val ui get() = DeviceGestures(instrumentation)
    private val context get() = instrumentation.targetContext

    @Test
    fun isolatedUidAndOnlyRequestedPageText() {
        assertTrue(PageTextService.supported())
        val file = fixture(false)
        try {
            val result = extract(file, 1)
            assertEquals(PageTextService.OK, result.getInt("status"))
            assertNotEquals(context.applicationInfo.uid, result.getInt("workerUid"))
            val text = result.getString("text").orEmpty()
            assertTrue("Expected public second page", text.contains("public beta"))
            assertTrue(
                "Expected public Korean text",
                text.replace(Regex("\\s"), "").contains("공개합성본문")
            )
            assertFalse("Unexpected first page text", text.contains("public alpha"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun imageOnly430PageDocumentReadsOnlyLastPageAndReturnsEmpty() {
        val file = fixture(true)
        try {
            val result = extract(file, 429)
            assertEquals(PageTextService.OK, result.getInt("status"))
            assertTrue(result.getString("text").isNullOrBlank())
        } finally {
            file.delete()
        }
    }

    @Test
    fun invalidPdfAndInvalidPageFailWithoutKillingReader() {
        val file = File.createTempFile("public-text-invalid-", ".pdf", context.cacheDir)
        try {
            file.writeText("public invalid synthetic data")
            assertEquals(PageTextService.FAILED, extract(file, 0).getInt("status"))
        } finally {
            file.delete()
        }
        val valid = fixture(false)
        try {
            assertEquals(PageTextService.FAILED, extract(valid, 2).getInt("status"))
            assertEquals(PageTextService.OK, extract(valid, 0).getInt("status"))
        } finally {
            valid.delete()
        }
    }

    @Test
    fun cancelDropsLateDescriptorAndNeverDeliversStaleText() {
        val client = PageTextClient(context)
        var deliver: ((ParcelFileDescriptor?) -> Unit)? = null
        val callbacks = AtomicInteger()
        val file = fixture(false)
        try {
            ui.onMain {
                client.read(0, { deliver = it }) { _, _ -> callbacks.incrementAndGet() }
                client.cancel()
            }
            assertTrue("Source must be requested", deliver != null)
            val source = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            ui.onMain { requireNotNull(deliver)(source) }
            assertFalse("Late source descriptor leaked", source.fileDescriptor.valid())
            assertEquals(0, callbacks.get())
        } finally {
            ui.onMain { client.cancel() }
            file.delete()
        }
    }

    @Test
    fun missingSourceTimesOutOnceAndCanBeCancelledAgain() {
        val client = PageTextClient(context)
        val done = CountDownLatch(1)
        val callbacks = AtomicInteger()
        var error = 0
        try {
            ui.onMain {
                client.read(0, {}) { reason, _ ->
                    error = reason
                    callbacks.incrementAndGet()
                    done.countDown()
                }
            }
            assertTrue("Client deadline did not complete", done.await(9, TimeUnit.SECONDS))
            assertEquals(R.string.page_text_stopped, error)
            ui.onMain { client.cancel() }
            assertEquals(1, callbacks.get())
        } finally {
            ui.onMain { client.cancel() }
        }
    }

    @Test
    fun accessibilityPageActionsPreserveZoomAndBodyClosesOnBack() {
        val activity = ui.launch()
        try {
            val viewer = ui.sample(activity)
            val before = ui.state(viewer)
            ui.onMain {
                val info = viewer.createAccessibilityNodeInfo()
                assertTrue(info.actionList.any { it.id == R.id.read_page_text })
                assertTrue(viewer.isFocusable)
            }
            val accessibility = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
                as AccessibilityManager
            ui.await("Accessibility connection not ready") {
                val window = instrumentation.uiAutomation.rootInActiveWindow
                window?.packageName == context.packageName && accessibility.isEnabled
            }
            instrumentation.waitForIdleSync()
            val observedPages = ArrayList<Int>()
            val pageEvent = try {
                instrumentation.uiAutomation.executeAndWaitForEvent(
                    {
                        ui.onMain {
                            assertTrue(
                                viewer.performAccessibilityAction(
                                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
                                    null
                                )
                            )
                        }
                    },
                    { event ->
                        val pageEvent = event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
                            event.packageName == context.packageName
                        if (pageEvent && observedPages.size < 10) observedPages.add(event.fromIndex)
                        pageEvent && event.fromIndex == before.getInt("page") + 1
                    },
                    5_000
                )
            } catch (_: TimeoutException) {
                // Platform timeout text may include unrelated windows; never log those events.
                val after = ui.state(viewer)
                throw AssertionError(
                    "Synthetic PDF page event missing: before=${before.getInt("page")} " +
                        "after=${after.getInt("page")} zoom=${after.getDouble("zoom")} " +
                        "size=${ui.onMain { "${viewer.width}x${viewer.height}" }} " +
                        "observedPages=$observedPages"
                )
            }
            assertTrue(pageEvent.itemCount > pageEvent.fromIndex)
            ui.onMain {
                assertTrue(
                    viewer.performAccessibilityAction(
                        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
                        null
                    )
                )
            }
            assertEquals(before.getDouble("zoom"), ui.state(viewer).getDouble("zoom"), 0.0001)
            ui.onMain {
                val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_SCROLLED)
                viewer.onInitializeAccessibilityEvent(event)
                assertEquals(before.getInt("page"), event.fromIndex)
                event.recycle()
            }
            ui.onMain { viewer.performAccessibilityAction(R.id.read_page_text, null) }
            ui.await("Public sample text did not reach accessible dialog") {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                root?.packageName == context.packageName &&
                    root.findAccessibilityNodeInfosByText("P0 synthetic page").isNotEmpty()
            }
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            ui.await("Body dialog did not close") {
                instrumentation.uiAutomation.rootInActiveWindow
                    ?.findAccessibilityNodeInfosByText("P0 synthetic page")?.isEmpty() == true
            }
            ui.awaitReady(activity)
        } finally {
            ui.onMain { activity.finish() }
        }
    }

    private fun fixture(images: Boolean): File {
        val file = File.createTempFile("public-page-text-", ".pdf", context.cacheDir)
        val pdf = PdfDocument()
        try {
            val paint = Paint().apply {
                textSize = 20f
                color = Color.BLACK
            }
            val image = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            try {
                image.eraseColor(Color.GRAY)
                repeat(if (images) 430 else 2) { index ->
                    val info = PdfDocument.PageInfo.Builder(300, 400, index + 1).create()
                    val page = pdf.startPage(info)
                    if (images) {
                        page.canvas.drawBitmap(image, 10f, 10f, paint)
                    } else {
                        val text = if (index == 0) "public alpha" else "public beta 공개 합성 본문"
                        page.canvas.drawText(text, 10f, 30f, paint)
                    }
                    pdf.finishPage(page)
                }
                file.outputStream().use(pdf::writeTo)
            } finally {
                image.recycle()
            }
        } finally {
            pdf.close()
        }
        return file
    }

    @Test
    fun replacementUsesANewInstanceAndDropsThePreviousSourceAndResult() {
        val file = fixture(false)
        val first = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val second = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val client = PageTextClient(context)
        val done = CountDownLatch(1)
        val callbacks = AtomicInteger()
        var text: String? = null
        var error = -1
        try {
            ui.onMain {
                client.read(0, { it(first) }) { _, _ -> callbacks.incrementAndGet() }
                client.read(1, { it(second) }) { reason, value ->
                    error = reason
                    text = value
                    callbacks.incrementAndGet()
                    done.countDown()
                }
            }
            assertTrue("Replacement did not finish", done.await(9, TimeUnit.SECONDS))
            assertEquals(0, error)
            assertEquals(1, callbacks.get())
            assertTrue(text.orEmpty().contains("public beta"))
            assertFalse(text.orEmpty().contains("public alpha"))
            assertFalse(first.fileDescriptor.valid())
            assertFalse(second.fileDescriptor.valid())
        } finally {
            ui.onMain { client.cancel() }
            first.close()
            second.close()
            file.delete()
        }
    }

    @Test
    fun shortDocumentCanChooseFirstPageBodyWithoutChangingZoomOrViewport() {
        val file = fixture(false)
        val activity = ui.launch()
        try {
            ui.onMain { (activity as MainActivity).openDocument(Uri.fromFile(file)) }
            val viewer = ui.awaitReady(activity)
            ui.onMain {
                val saved = requireNotNull(viewer.saveViewport())
                saved.putDouble("zoom", 0.25)
                viewer.reset()
                viewer.restoreViewport(saved)
                viewer.setPages(listOf(PageSize(300, 400), PageSize(300, 400)))
            }
            val before = ui.state(viewer)
            assertEquals(
                "Whole short document fits; center is on its last page",
                1,
                before.getInt("page")
            )
            ui.onMain { viewer.performAccessibilityAction(R.id.read_page_text, null) }
            awaitBody("public beta")
            click(context.getString(R.string.choose_page_text))
            ui.await("Body page input missing") {
                val root = instrumentation.uiAutomation.rootInActiveWindow ?: return@await false
                fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                    if (node.className == "android.widget.EditText") return node
                    for (index in 0 until node.childCount) {
                        find(node.getChild(index) ?: continue)?.let { return it }
                    }
                    return null
                }
                val input = find(root) ?: return@await false
                input.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            "1"
                        )
                    }
                )
            }
            click(context.getString(R.string.navigate))
            awaitBody("public alpha")
            val after = ui.state(viewer)
            for (key in listOf("zoom", "centerX", "centerY")) {
                assertEquals(before.getDouble(key), after.getDouble(key), 0.0001)
            }
            click(context.getString(R.string.close_settings))
            for ((action, page, body) in listOf(
                Triple(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, 0, "public alpha"),
                Triple(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, 1, "public beta")
            )) {
                ui.onMain {
                    assertTrue(viewer.performAccessibilityAction(action, null))
                    val node = viewer.createAccessibilityNodeInfo()
                    assertEquals(
                        context.getString(R.string.accessible_page, page + 1, 2),
                        node.contentDescription
                    )
                    assertFalse(
                        viewer.performAccessibilityAction(
                            if (page == 0) {
                                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                            } else {
                                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                            },
                            null
                        )
                    )
                    if (page == 0) {
                        assertTrue(
                            activity.findViewById<android.view.View>(R.id.read_page_text)
                                .performClick()
                        )
                    } else {
                        assertTrue(viewer.performAccessibilityAction(R.id.read_page_text, null))
                    }
                }
                awaitBody(body)
                assertEquals(before.getDouble("zoom"), ui.state(viewer).getDouble("zoom"), 0.0001)
                click(context.getString(R.string.close_settings))
            }
        } finally {
            ui.onMain { activity.finish() }
            instrumentation.waitForIdleSync()
            ui.await("Synthetic document Activity did not close") { activity.isDestroyed }
            val removed = CountDownLatch(1)
            var success = false
            (context.applicationContext as ReaderApplication).library.execute({
                it.remove(Uri.fromFile(file).toString())
            }) {
                success = it.isSuccess
                removed.countDown()
            }
            assertTrue("Synthetic record cleanup timed out", removed.await(5, TimeUnit.SECONDS))
            assertTrue("Synthetic record cleanup failed", success)
            file.delete()
        }
    }

    private fun awaitBody(text: String) {
        ui.await("Expected public body not displayed") {
            instrumentation.uiAutomation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByText(text)?.isNotEmpty() == true
        }
    }

    private fun click(text: String) {
        ui.await("Body control missing") {
            val root = instrumentation.uiAutomation.rootInActiveWindow ?: return@await false
            root.findAccessibilityNodeInfosByText(text).any {
                it.text == text &&
                    it.isVisibleToUser &&
                    it.isClickable &&
                    it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
        instrumentation.waitForIdleSync()
    }

    @Test
    fun serviceDeadlineTerminatesSessionWithoutARequest() {
        expectGuardDeath(null)
    }

    @Test
    fun stricterRssBudgetStopsWorkerWithoutAllocatingLargeMemory() {
        val file = fixture(false)
        try {
            expectGuardDeath(file)
            assertEquals(PageTextService.OK, extract(file, 0).getInt("status"))
        } finally {
            file.delete()
        }
    }

    private fun expectGuardDeath(file: File?) {
        val connected = CountDownLatch(1)
        val died = CountDownLatch(1)
        var binder: IBinder? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                binder = service
                service.linkToDeath({ died.countDown() }, 0)
                connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        val intent = Intent(context, PageTextService::class.java)
        assertTrue(context.bindService(intent, connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue("Guard service bind failed", connected.await(5, TimeUnit.SECONDS))
            if (file != null) {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { source ->
                    Messenger(requireNotNull(binder)).send(
                        Message.obtain(null, PageTextService.READ, 0, 0).apply {
                            data = Bundle().apply {
                                putParcelable("source", source)
                                // The protocol can tighten, never increase, the production budget.
                                putLong("rssLimitKiB", 1)
                            }
                        }
                    )
                }
            }
            assertTrue(
                "Service guard did not terminate its process",
                died.await(8, TimeUnit.SECONDS)
            )
        } finally {
            context.unbindService(connection)
        }
    }

    private fun extract(file: File, page: Int): Bundle {
        val connected = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val died = CountDownLatch(1)
        var binder: IBinder? = null
        var result: Bundle? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                binder = service
                service.linkToDeath({ died.countDown() }, 0)
                connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        assertTrue(
            context.bindService(
                Intent(context, PageTextService::class.java),
                connection,
                Context.BIND_AUTO_CREATE
            )
        )
        try {
            assertTrue("Isolated service bind timed out", connected.await(5, TimeUnit.SECONDS))
            val reply = Messenger(
                Handler(Looper.getMainLooper()) { message ->
                    result = message.data
                    completed.countDown()
                    true
                }
            )
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { source ->
                Messenger(requireNotNull(binder)).send(
                    Message.obtain(null, PageTextService.READ, page, 0).apply {
                        data = Bundle().apply { putParcelable("source", source) }
                        replyTo = reply
                    }
                )
            }
            assertTrue("Isolated extraction did not complete", completed.await(7, TimeUnit.SECONDS))
        } finally {
            binder?.takeIf { it.isBinderAlive }?.let {
                Messenger(it).send(Message.obtain(null, PageTextService.CANCEL))
            }
            context.unbindService(connection)
            assertTrue("Cancelled isolated process remained alive", died.await(5, TimeUnit.SECONDS))
        }
        return requireNotNull(result)
    }
}
