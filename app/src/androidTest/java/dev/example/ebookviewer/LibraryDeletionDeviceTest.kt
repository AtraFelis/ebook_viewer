package dev.example.ebookviewer

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDeletionDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation
    private val ui get() = DeviceGestures(instrumentation)

    @Test
    fun warningsUseCenteredDialogsAndCancellationAndDeletionPreserveOriginals() {
        val activity = ui.launch()
        val path = File.createTempFile("public-delete-records-", ".bin", activity.cacheDir).apply {
            delete()
        }
        val originals = (0 until 3).map {
            File.createTempFile("public-delete-original-", ".pdf", activity.cacheDir).apply {
                writeText("public synthetic original")
            }
        }
        val records = originals.mapIndexed { index, file ->
            ReadingRecord(file.toURI().toString(), "Public $index", index.toLong())
        }
        DocumentLibrary(path).apply {
            records.forEach(::put)
            addShelf(Bookshelf("first", "Public shelf"))
            addShelf(Bookshelf("second", "Other shelf"))
            addToShelves(records.map { it.uri }.toSet(), setOf("first", "second"))
        }
        val repository = LibraryRepository { path }
        var workflow: LibraryWorkflow? = null
        fun current() = requireNotNull(workflow)
        fun dialog() = requireNotNull(
            LibraryWorkflow::class.java.getDeclaredField("dialog").apply {
                isAccessible = true
            }.get(current()) as? Dialog
        )
        fun views(): List<View> = ui.views(requireNotNull(dialog().window).decorView)
        fun click(label: Int) = views().filterIsInstance<Button>().first {
            val confirmation = current().capture()?.getString("mode") in setOf("confirm", "remove")
            val text = if (confirmation &&
                label != android.R.string.cancel
            ) {
                R.string.library_confirm_delete
            } else {
                label
            }
            it.text == activity.getString(text)
        }.performClick()
        fun awaitMode(mode: String) = ui.await("Deletion workflow did not reach $mode") {
            runCatching {
                current().capture()?.getString("mode") == mode &&
                    dialog().isShowing &&
                    views().filterIsInstance<Button>().all { it.width > 0 }
            }.getOrDefault(false)
        }
        fun assertCenteredDialog() {
            val window = requireNotNull(dialog().window)
            assertEquals(Gravity.CENTER, window.attributes.gravity)
            assertTrue(window.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0)
            assertTrue(window.attributes.dimAmount >= 0.4f)
            val content = window.decorView.findViewById<ViewGroup>(
                android.R.id.content
            ).getChildAt(0)
            assertTrue(
                content.height > 0 &&
                    content.height < activity.resources.displayMetrics.heightPixels
            )
            assertTrue(content.width < activity.resources.displayMetrics.widthPixels)
            val buttons = views().filterIsInstance<Button>()
            assertEquals(2, buttons.size)
            assertEquals(activity.getString(android.R.string.cancel), buttons[0].text)
            assertEquals(activity.getString(R.string.library_confirm_delete), buttons[1].text)
            assertTrue(buttons.all { it.compoundDrawables.all { drawable -> drawable == null } })
            val location = IntArray(2)
            content.getLocationOnScreen(location)
            val display = activity.resources.displayMetrics
            val tolerance = ReaderStyle(activity).dp(40)
            assertTrue(
                kotlin.math.abs(location[0] + content.width / 2 - display.widthPixels / 2) <
                    tolerance
            )
            assertTrue(
                kotlin.math.abs(location[1] + content.height / 2 - display.heightPixels / 2) <
                    tolerance
            )
        }
        try {
            ui.onMain {
                workflow = LibraryWorkflow(activity, repository, { true }, {}).also {
                    it.resume()
                    it.remove(setOf(records.first().uri), single = true)
                }
            }
            awaitMode("remove")
            val image = ui.onMain {
                val content = requireNotNull(dialog().window).decorView
                    .findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
                Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888).also {
                    content.draw(Canvas(it))
                }
            }
            try {
                File(
                    requireNotNull(activity.getExternalFilesDir("verification")),
                    "public-centered-delete-dialog.png"
                ).outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally {
                image.recycle()
            }
            ui.onMain {
                assertCenteredDialog()
                click(android.R.string.cancel)
            }
            assertEquals(3, DocumentLibrary(path).list().size)
            ui.onMain { current().remove(setOf(records[0].uri, records[2].uri)) }
            awaitMode("remove")
            val draft = ui.onMain { current().capture() }
            ui.onMain {
                current().close()
                workflow = LibraryWorkflow(activity, repository, { true }, {}).also {
                    it.restore(draft)
                    it.resume()
                }
            }
            awaitMode("remove")
            ui.onMain {
                assertCenteredDialog()
                click(R.string.remove_selected)
            }
            ui.await("Document deletion did not finish") { current().capture() == null }
            val remaining = DocumentLibrary(path).snapshot()
            assertEquals(listOf(records[1]), remaining.records)
            assertEquals(setOf("first", "second"), remaining.memberships[records[1].uri])
            assertTrue(originals.all { it.exists() })

            ui.onMain { current().edit() }
            awaitMode("edit")
            ui.onMain {
                views().filterIsInstance<CheckBox>().first().performClick()
                views().filterIsInstance<ImageButton>().first {
                    it.contentDescription == activity.getString(R.string.delete_bookshelf)
                }.performClick()
            }
            awaitMode("confirm")
            ui.onMain {
                assertCenteredDialog()
                click(android.R.string.cancel)
            }
            awaitMode("edit")
            assertEquals(2, DocumentLibrary(path).snapshot().shelves.size)
            ui.onMain { current().actions(Bookshelf("first", "Public shelf")) }
            awaitMode("actions")
            ui.onMain { click(R.string.delete_bookshelf) }
            awaitMode("confirm")
            ui.onMain {
                assertCenteredDialog()
                click(R.string.delete_bookshelf)
            }
            ui.await("Shelf deletion did not finish") { current().capture() == null }
            val removedShelf = DocumentLibrary(path).snapshot()
            assertEquals(listOf("second"), removedShelf.shelves.map { it.id })
            assertEquals(listOf(records[1]), removedShelf.records)
            assertEquals(setOf("second"), removedShelf.memberships[records[1].uri])
            assertTrue(originals.all { it.exists() })
        } finally {
            ui.onMain {
                workflow?.close()
                activity.finish()
            }
            originals.forEach { it.delete() }
            path.delete()
        }
    }
}
