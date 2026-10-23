package dev.example.ebookviewer

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.GridView
import android.widget.ImageButton
import android.widget.TextView
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryInteractionDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation
    private val ui get() = DeviceGestures(instrumentation)

    @Test
    fun referenceEditorAndSheetsKeepControlsDistinctAndContentSized() {
        val activity = ui.launch()
        val path = File(activity.cacheDir, "public-reference-${System.nanoTime()}.bin")
        DocumentLibrary(path).apply {
            put(ReadingRecord("fixture:reference", "Public reference", 1))
            addShelf(Bookshelf("first", "Public bookshelf"))
            addToShelves(setOf("fixture:reference"), setOf("first"))
        }
        var workflow: LibraryWorkflow? = null
        fun current() = requireNotNull(workflow)
        fun dialog() = requireNotNull(
            LibraryWorkflow::class.java.getDeclaredField("dialog").apply {
                isAccessible = true
            }.get(current()) as? Dialog
        )
        fun root(): View = requireNotNull(dialog().window).decorView
        fun delete() = ui.views(root()).filterIsInstance<ImageButton>().single {
            it.contentDescription == activity.getString(R.string.delete_bookshelf)
        }
        fun snapshot(name: String) {
            val bitmap = ui.onMain {
                val content = root().findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
                Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888).also {
                    content.draw(Canvas(it))
                }
            }
            try {
                File(
                    requireNotNull(activity.getExternalFilesDir("verification")),
                    name
                ).outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally {
                bitmap.recycle()
            }
        }
        try {
            ui.onMain {
                // All exported pixels belong to this public fixture, never the personal main screen.
                activity.setContentView(
                    TextView(activity).apply {
                        text =
                            "Public reference backdrop"
                    }
                )
                activity.setTheme(R.style.ReaderLight)
                workflow =
                    LibraryWorkflow(activity, LibraryRepository { path }, { true }, {}).also {
                        it.resume()
                        it.edit()
                    }
            }
            ui.await("Reference editor did not settle") {
                runCatching { delete().width > 0 && !delete().isEnabled }.getOrDefault(false)
            }
            ui.onMain {
                val style = ReaderStyle(activity)
                assertEquals(style.dp(64), delete().width)
                assertEquals(style.dp(20), delete().drawable.bounds.width())
                assertEquals(style.dp(20), delete().drawable.bounds.height())
                val tabs = ui.views(root()).filterIsInstance<Button>().filter {
                    it.text == activity.getString(R.string.delete_bookshelf) ||
                        it.text == activity.getString(R.string.library_reorder_shelves)
                }
                assertEquals(2, tabs.size)
                assertTrue(tabs[0].isSelected)
                assertFalse(tabs[1].isSelected)
                assertEquals(0, tabs[0].compoundDrawables.filterNotNull().size)
                val choice = ui.views(root()).filterIsInstance<CheckBox>().single {
                    it.contentDescription == "Public bookshelf"
                }
                assertEquals(choice, (choice.parent as ViewGroup).getChildAt(0))
            }
            snapshot("public-reference-editor-unselected.png")
            ui.onMain {
                ui.views(root()).filterIsInstance<CheckBox>().first {
                    it.text == activity.getString(R.string.select_all_documents)
                }.performClick()
            }
            ui.await("Selected delete control was not laid out") {
                delete().isEnabled &&
                    delete().width > 0
            }
            snapshot("public-reference-editor-selected.png")
            ui.onMain { current().actions(Bookshelf("first", "Public bookshelf")) }
            ui.await("Content-sized action sheet did not appear") {
                runCatching {
                    current().capture()?.getString("mode") == "actions" &&
                        root().findViewById<ViewGroup>(android.R.id.content).getChildAt(0).height >
                        0 &&
                        ui.views(root()).filterIsInstance<Button>().any {
                            it.text ==
                                activity.getString(R.string.library_shelf_information)
                        }
                }.getOrDefault(false)
            }
            ui.onMain {
                val window = requireNotNull(dialog().window)
                assertTrue(
                    window.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0
                )
                assertTrue(window.attributes.dimAmount >= 0.4f)
                val content = root().findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
                assertTrue(content.height < activity.resources.displayMetrics.heightPixels / 2)
                val last = ui.views(content).filterIsInstance<Button>().first {
                    it.text ==
                        activity.getString(R.string.delete_bookshelf)
                }
                val location = IntArray(2)
                last.getLocationInWindow(location)
                val panel = IntArray(2)
                content.getLocationInWindow(panel)
                val bottomGap = panel[1] + content.height - location[1] - last.height
                assertTrue(
                    "Action sheet kept an oversized bottom gap",
                    bottomGap in 0..ReaderStyle(activity).dp(64)
                )
            }
            snapshot("public-reference-action-sheet.png")
        } finally {
            ui.onMain {
                workflow?.close()
                activity.finish()
            }
            path.delete()
        }
    }

    @Test
    fun compactListKeepsTitlesAndScopeSpecificSelectionFooter() {
        val activity = ui.launch()
        val path = File.createTempFile("public-library-prefs-", ".bin", activity.cacheDir)
        path.outputStream().use {
            LibraryPreferencesCodec.write(it, LibraryPreferences(view = LibraryViewMode.LIST))
        }
        val preferences = LibraryPreferencesRepository { path }
        val records = (0 until 100).map {
            ReadingRecord("fixture:$it", "Public document title $it", it.toLong())
        }
        val catalog = LibraryCatalog(records).addShelf(Bookshelf("source", "Public source"))
            .addToShelves(setOf("fixture:0", "fixture:1"), setOf("source"))
        var view: BookshelfLibraryView? = null
        var opened = 0
        var moved = emptySet<String>()
        try {
            ui.onMain {
                view =
                    BookshelfLibraryView(
                        activity,
                        TextView(activity).apply {
                            visibility = View.GONE
                        },
                        Button(activity).apply { visibility = View.GONE },
                        Button(activity).apply {
                            visibility =
                                View.GONE
                        },
                        {
                        }, {
                        }, {
                        }, {
                            opened++
                        }, {}, {}, { moved = it }, { _, _, _ -> }, {}, preferences = preferences
                    ).also {
                        it.restoreState(Bundle().apply { putBoolean("home", false) })
                        it.render(catalog)
                        activity.setContentView(it.view)
                    }
            }
            fun root() = requireNotNull(view).view
            ui.await("List titles have no usable width") {
                val grid = root().findViewById<GridView>(R.id.library_grid)
                val cell = grid.getChildAt(0)
                grid.numColumns == 1 &&
                    cell != null &&
                    ui.views(cell).filterIsInstance<TextView>().any {
                        it.text.toString().startsWith("Public document title") && it.width > 120
                    }
            }
            ui.onMain {
                val grid = root().findViewById<GridView>(R.id.library_grid)
                assertTrue(grid.getChildAt(0).height <= ReaderStyle(activity).dp(88))
                assertTrue(grid.childCount < records.size)
                assertEquals(
                    2,
                    ui.views(root()).filterIsInstance<ImageButton>().count {
                        it.isShown
                    }
                )
                assertTrue(ui.views(root()).filterIsInstance<CheckBox>().none { it.isShown })
                assertTrue(
                    ui.views(root()).filterIsInstance<TextView>().none {
                        it.isShown &&
                            it.text == activity.getString(R.string.last_read_document)
                    }
                )
                grid.getChildAt(0).performLongClick()
                val footer = root().findViewById<View>(R.id.library_document_actions)
                assertTrue(footer.isShown)
                assertFalse(
                    ui.views(footer).filterIsInstance<TextView>().any {
                        it.text ==
                            activity.getString(R.string.move_to_bookshelf)
                    }
                )
                grid.getChildAt(0).performClick()
                assertEquals(View.GONE, footer.visibility)
                assertEquals(0, opened)
                assertTrue(requireNotNull(view).finishManaging())
                requireNotNull(view).restoreState(
                    Bundle().apply {
                        putBoolean("home", false)
                        putString("shelf", "source")
                    }
                )
                requireNotNull(view).render(catalog)
            }
            ui.await("Source shelf documents did not appear") {
                root().findViewById<GridView>(R.id.library_grid).count ==
                    2
            }
            ui.onMain {
                root().findViewById<GridView>(R.id.library_grid).getChildAt(0).performLongClick()
            }
            ui.await("Selection actions were not laid out") {
                val actions = root().findViewById<ViewGroup>(R.id.library_document_actions)
                actions.childCount == 2 &&
                    actions.getChildAt(0).width > 0 &&
                    actions.getChildAt(1).width > 0
            }
            ui.onMain {
                val actions = root().findViewById<ViewGroup>(R.id.library_document_actions)
                val move = actions.getChildAt(0) as Button
                val remove = actions.getChildAt(1) as ImageButton
                val style = ReaderStyle(activity)
                assertEquals(style.dp(64), remove.width)
                assertEquals(style.dp(64), remove.height)
                assertTrue(move.right < remove.left)
                assertTrue(move.elevation > 0f && remove.elevation > 0f)
                move.performClick()
                assertEquals(
                    requireNotNull(view).saveState().getStringArrayList("selected")?.toSet(),
                    moved
                )
                assertTrue(
                    ui.views(
                        root().findViewById(R.id.library_document_actions)
                    ).filterIsInstance<TextView>().any {
                        it.text == activity.getString(R.string.move_to_bookshelf)
                    }
                )
                val state = requireNotNull(view).saveState()
                assertEquals(1, state.getStringArrayList("selected")?.size)
            }
            val image = ui.onMain {
                val root = root()
                Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also {
                    root.draw(Canvas(it))
                }
            }
            try {
                File(
                    requireNotNull(activity.getExternalFilesDir("verification")),
                    "public-document-selection-actions.png"
                ).outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally {
                image.recycle()
            }
            ui.onMain {
                ui.views(root()).filterIsInstance<CheckBox>().single().performClick()
                assertEquals(
                    2,
                    requireNotNull(view).saveState().getStringArrayList("selected")?.size
                )
                requireNotNull(view).finishManaging()
                assertEquals(
                    View.GONE,
                    root().findViewById<View>(R.id.library_document_actions).visibility
                )
            }
        } finally {
            ui.onMain {
                view?.close()
                activity.finish()
            }
            path.delete()
        }
    }

    @Test
    fun shelfDragCancelAndFailedSaveKeepDraftAndOriginalRecords() {
        val activity = ui.launch()
        val directory = File(activity.cacheDir, "public-edit-${System.nanoTime()}")
        assertTrue(directory.mkdir())
        val backup = File(activity.cacheDir, "public-edit-held-${System.nanoTime()}")
        val path = File(directory, "records.bin")
        val record = ReadingRecord("fixture:edit", "Public edit document", 1)
        DocumentLibrary(path).apply {
            put(record)
            listOf("first", "second", "third").forEach { addShelf(Bookshelf(it, it)) }
            addToShelves(setOf(record.uri), setOf("first", "second"))
        }
        var workflow: LibraryWorkflow? = null
        fun current() = requireNotNull(workflow)
        fun decor(): View = requireNotNull(
            LibraryWorkflow::class.java.getDeclaredField("dialog").apply {
                isAccessible = true
            }.get(current()) as? Dialog
        ).let { requireNotNull(it.window).decorView }
        fun click(label: Int) = ui.views(decor()).filterIsInstance<Button>().first {
            it.isShown && it.text == activity.getString(label)
        }.performClick()
        try {
            ui.onMain {
                workflow =
                    LibraryWorkflow(activity, LibraryRepository { path }, { true }, {}).also {
                        it.resume()
                        it.edit()
                    }
            }
            ui.await("Shelf editor missing") { runCatching { decor().isShown }.getOrDefault(false) }
            ui.onMain { click(R.string.library_reorder_shelves) }
            ui.await("Drag handles were not laid out") {
                val handles = ui.views(decor()).filterIsInstance<ImageButton>().filter {
                    it.contentDescription == activity.getString(R.string.library_drag_shelf)
                }
                handles.size == 3 && handles.all { it.isShown && it.width > 0 && it.height > 0 }
            }
            val points = ui.onMain {
                val handles = ui.views(decor()).filterIsInstance<ImageButton>().filter {
                    it.contentDescription == activity.getString(R.string.library_drag_shelf)
                }
                assertEquals(3, handles.size)
                handles.map { handle ->
                    val position = IntArray(2)
                    handle.getLocationOnScreen(position)
                    (position[0] + handle.width / 2).toFloat() to
                        (position[1] + handle.height / 2).toFloat()
                }
            }
            val down = SystemClock.uptimeMillis()
            fun pointer(action: Int, x: Float, y: Float) {
                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                try {
                    // The system owns the drag surface after a long press; targeted injection rejects it.
                    assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
                } finally {
                    event.recycle()
                }
            }
            pointer(MotionEvent.ACTION_DOWN, points[0].first, points[0].second)
            SystemClock.sleep(700)
            repeat(12) {
                pointer(
                    MotionEvent.ACTION_MOVE,
                    points[0].first,
                    points[0].second + (points[2].second - points[0].second) * (it + 1) / 12
                )
                SystemClock.sleep(25)
            }
            pointer(MotionEvent.ACTION_UP, points[2].first, points[2].second)
            ui.await("Native drag did not change the order draft") {
                current().capture()?.getStringArrayList("order")?.first() != "first"
            }
            val draft = ui.onMain { current().capture() }
            assertEquals(
                listOf("first", "second", "third"),
                DocumentLibrary(path).snapshot().shelves.map {
                    it.id
                }
            )
            ui.onMain {
                ui.views(decor()).filterIsInstance<ImageButton>().first {
                    it.contentDescription == activity.getString(R.string.library_back)
                }.performClick()
                assertEquals(null, current().capture())
                current().restore(draft)
                current().resume()
            }
            ui.await("Recreated order draft missing") {
                runCatching { decor().isShown }.getOrDefault(false)
            }
            assertTrue(directory.renameTo(backup))
            assertTrue(directory.createNewFile())
            ui.onMain { click(R.string.library_save) }
            ui.await("Save failure did not retain a retryable draft") {
                runCatching {
                    ui.views(decor()).filterIsInstance<TextView>().any {
                        it.text == activity.getString(R.string.library_change_error)
                    }
                }.getOrDefault(false)
            }
            assertEquals(
                draft?.getStringArrayList("order"),
                ui.onMain {
                    current().capture()?.getStringArrayList("order")
                }
            )
            assertTrue(directory.delete())
            assertTrue(backup.renameTo(directory))
            ui.onMain { click(R.string.library_save) }
            ui.await("Order retry did not complete") { current().capture() == null }
            val saved = DocumentLibrary(path).snapshot()
            assertEquals(draft?.getStringArrayList("order"), saved.shelves.map { it.id })
            assertEquals(record, saved.records.single())
            assertEquals(setOf("first", "second"), saved.memberships[record.uri])
        } finally {
            ui.onMain {
                workflow?.close()
                activity.finish()
            }
            if (directory.isFile) directory.delete()
            if (backup.exists()) backup.renameTo(directory)
            path.delete()
            directory.delete()
        }
    }

    @Test
    fun moveAndRegisteredDocumentPickerPreserveOtherShelvesAndDrafts() {
        val activity = ui.launch()
        val path = File(activity.cacheDir, "public-interaction-${System.nanoTime()}.bin")
        val record =
            ReadingRecord(
                "fixture:move",
                "Public move document",
                1,
                10,
                ReadingPosition(3, 0.2, 0.5, 2.0)
            )
        DocumentLibrary(path).apply {
            put(record)
            listOf("source", "other", "target").forEach { addShelf(Bookshelf(it, it)) }
            addToShelves(setOf(record.uri), setOf("source", "other"))
        }
        val repository = LibraryRepository { path }
        var workflow: LibraryWorkflow? = null
        fun current() = requireNotNull(workflow)
        fun decor(): View = requireNotNull(
            LibraryWorkflow::class.java.getDeclaredField("dialog").apply {
                isAccessible = true
            }.get(current()) as? Dialog
        ).let { requireNotNull(it.window).decorView }
        fun click(label: Int) = ui.views(decor()).filterIsInstance<Button>().first {
            it.isShown && it.text == activity.getString(label)
        }.performClick()
        try {
            ui.onMain {
                workflow = LibraryWorkflow(activity, repository, { true }, {}).also {
                    it.resume()
                    it.move(setOf(record.uri), "source")
                }
            }
            ui.await("Move sheet not ready") {
                current().capture()?.getString("mode") == "move" &&
                    runCatching { decor().isShown }.getOrDefault(false)
            }
            val saved = ui.onMain {
                val keep = ui.views(decor()).filterIsInstance<CheckBox>().single()
                assertTrue(keep.isChecked)
                keep.performClick()
                current().capture()
            }
            ui.onMain {
                current().close()
                workflow =
                    LibraryWorkflow(activity, repository, { true }, {}).also {
                        it.restore(saved)
                        it.resume()
                    }
            }
            ui.await("Move draft did not restore") {
                runCatching {
                    ui.views(decor()).filterIsInstance<CheckBox>().single().isChecked ==
                        false
                }.getOrDefault(false)
            }
            ui.onMain {
                ui.views(decor()).filterIsInstance<TextView>().first {
                    it.text == "target"
                }.parent.let {
                    (it as View).parent.let { row -> (row as View).performClick() }
                }
            }
            ui.await("Move did not complete") { current().capture() == null }
            val moved = DocumentLibrary(path).snapshot()
            assertEquals(setOf("other", "target"), moved.memberships[record.uri])
            assertEquals(record, moved.records.single())
            ui.onMain { current().actions(Bookshelf("source", "source")) }
            ui.await("Shelf actions not ready") {
                runCatching { decor().isShown }.getOrDefault(false)
            }
            ui.onMain { click(R.string.library_add_documents) }
            ui.await("Registered picker missing") {
                current().capture()?.getString("mode") == "add"
            }
            ui.onMain {
                val checks = ui.views(decor()).filterIsInstance<CheckBox>()
                assertTrue(checks.size >= 2)
                checks.first().performClick()
                click(R.string.library_add_documents)
            }
            ui.await("Registered addition did not complete") { current().capture() == null }
            assertEquals(
                setOf("source", "other", "target"),
                DocumentLibrary(path).snapshot().memberships[record.uri]
            )
        } finally {
            ui.onMain {
                workflow?.close()
                activity.finish()
            }
            path.delete()
        }
    }
}
