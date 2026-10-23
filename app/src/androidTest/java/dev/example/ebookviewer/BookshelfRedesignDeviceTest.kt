package dev.example.ebookviewer

import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.Button
import android.widget.GridView
import android.widget.ListView
import android.widget.TextView
import java.io.DataOutputStream
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfRedesignDeviceTest {
    private val instrumentation get() = PdfRendererDeviceTest.instrumentation
    private val ui get() = DeviceGestures(instrumentation)

    @Test
    fun uncategorizedCardAndEditingFollowTheHomePresentationRules() {
        val activity = ui.launch()
        val records = (0 until 3).map { ReadingRecord("public:$it", "Public $it", it.toLong()) }
        val catalog = LibraryCatalog(records).addShelf(Bookshelf("s", "학습"))
            .addToShelves(setOf("public:0"), setOf("s"))
        var home: ReaderHomeView? = null
        var added = 0
        var managed = 0
        fun tile(name: String) = ui.views(requireNotNull(home).view).firstOrNull {
            it.id == R.id.bookshelf_tile && it.isShown && it.contentDescription == name
        }
        fun persistentBackground(view: View): Boolean {
            val bitmap = Bitmap.createBitmap(96, 48, Bitmap.Config.ARGB_8888)
            try {
                val drawable = requireNotNull(view.background)
                val previous = android.graphics.Rect(drawable.bounds)
                drawable.setBounds(0, 0, bitmap.width, bitmap.height)
                drawable.draw(Canvas(bitmap))
                drawable.bounds = previous
                return Color.alpha(bitmap.getPixel(48, 24)) > 0
            } finally {
                bitmap.recycle()
            }
        }
        try {
            ui.onMain {
                home = ReaderHomeView(
                    activity, TextView(activity).apply { visibility = View.GONE },
                    Button(activity).apply { visibility = View.GONE },
                    Button(activity).apply { visibility = View.GONE },
                    {}, {}, {}, {}, {}, {}, {}, { _, _, _ -> }, { added++ },
                    onShelfAction = { managed++ }
                ).also {
                    it.render(catalog)
                    activity.setContentView(it.view)
                }
                assertEquals(null, requireNotNull(home).view.findViewById<View>(R.id.bookshelf_add))
            }
            ui.await("Main bookshelf list was not laid out") {
                requireNotNull(home).view.findViewById<ListView>(R.id.main_bookshelf_list).height >
                    0
            }
            ui.onMain {
                requireNotNull(home).view.findViewById<ListView>(R.id.main_bookshelf_list)
                    .setSelection(1)
            }
            val uncategorized = activity.getString(R.string.uncategorized)
            ui.await("Uncategorized documents did not get a bookshelf card") {
                tile(uncategorized) != null && tile("학습") != null
            }
            val snapshot = ui.onMain {
                val root = requireNotNull(home).view
                Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also {
                    root.draw(Canvas(it))
                }
            }
            File(
                requireNotNull(activity.getExternalFilesDir("verification")),
                "public-bookshelf-card-feedback.png"
            ).outputStream().use {
                snapshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            snapshot.recycle()
            ui.onMain {
                val root = requireNotNull(home).view
                assertTrue(!persistentBackground(root.findViewById(R.id.bookshelf_edit)))
                val shelf = requireNotNull(tile("학습"))
                val more = shelf.findViewById<View>(R.id.bookshelf_more)
                assertTrue(!persistentBackground(more))
                more.performClick()
                assertEquals(1, managed)
                assertEquals(
                    View.GONE,
                    requireNotNull(tile(uncategorized))
                        .findViewById<View>(R.id.bookshelf_more).visibility
                )
                requireNotNull(tile(uncategorized)).performClick()
            }
            ui.await("Uncategorized card did not open only its documents") {
                requireNotNull(home).shelfFilter == "" &&
                    requireNotNull(home).view.findViewById<GridView>(R.id.library_grid).count == 2
            }
            ui.onMain {
                assertTrue(requireNotNull(home).finishManaging())
                requireNotNull(home).render(
                    catalog.addToShelves(
                        records.map {
                            it.uri
                        }.toSet(),
                        setOf("s")
                    )
                )
            }
            ui.await("Empty uncategorized card remained visible") {
                tile(uncategorized) == null && tile("학습") != null
            }
            ui.onMain {
                val root = requireNotNull(home).view
                root.findViewById<View>(R.id.bookshelf_edit).performClick()
                assertEquals(1, added)
                assertEquals(null, root.findViewById<View>(R.id.bookshelf_add))
                requireNotNull(home).render(LibraryCatalog())
            }
            ui.await("An empty catalog displayed a virtual shelf") {
                ui.views(requireNotNull(home).view).none {
                    it.id == R.id.bookshelf_tile &&
                        it.isShown
                }
            }
            ui.onMain {
                requireNotNull(home).view.findViewById<View>(R.id.bookshelf_edit).performClick()
                assertEquals(2, added)
                assertEquals(null, requireNotNull(home).view.findViewById<View>(R.id.bookshelf_add))
            }
        } finally {
            ui.onMain {
                home?.close()
                activity.finish()
            }
        }
    }

    @Test
    fun originalHeaderAndEmbeddedBookshelvesRestoreTogether() {
        val activity = ui.launch()
        var home: ReaderHomeView? = null
        var restored: ReaderHomeView? = null
        var retried = 0
        val records = (0 until 20).map {
            ReadingRecord("fixture:$it", "Fixture $it", it.toLong())
        }
        var catalog = LibraryCatalog(records)
        repeat(20) {
            catalog = catalog.addShelf(Bookshelf("s$it", "학습 $it"))
                .addToShelves(setOf("fixture:$it"), setOf("s$it"))
        }
        try {
            lateinit var status: TextView
            lateinit var retry: Button
            ui.onMain {
                status = TextView(activity).apply { visibility = View.GONE }
                retry = Button(activity).apply {
                    visibility = View.GONE
                    setOnClickListener { retried++ }
                }
                home = ReaderHomeView(
                    activity, status, retry, Button(activity).apply { visibility = View.GONE },
                    {}, {}, {}, {}, {}, {}, {}, { _, _, _ -> }, {}
                ).also {
                    it.render(catalog)
                    activity.setContentView(it.view)
                }
            }
            ui.await("Original main header and embedded bookshelves did not appear") {
                val root = requireNotNull(home).view
                root.findViewById<View>(R.id.main_bookshelf_list).isShown &&
                    !root.findViewById<View>(R.id.library_grid).isShown &&
                    ui.views(root).filterIsInstance<TextView>().any {
                        it.isShown && it.text == activity.getString(R.string.home_welcome)
                    } &&
                    ui.views(root).filterIsInstance<TextView>().any {
                        it.isShown && it.text == activity.getString(R.string.bookshelves)
                    }
            }
            ui.onMain {
                requireNotNull(home).view.findViewById<ListView>(R.id.main_bookshelf_list)
                    .setSelectionFromTop(2, -10)
            }
            ui.await("Bookshelf rows were not recycled") {
                requireNotNull(home).view.findViewById<ListView>(R.id.main_bookshelf_list)
                    .firstVisiblePosition == 2
            }
            val previous = ui.onMain {
                val list = requireNotNull(
                    home
                ).view.findViewById<ListView>(R.id.main_bookshelf_list)
                val first = list.firstVisiblePosition to list.getChildAt(0).top
                ui.views(list).first { it.id == R.id.bookshelf_tile }.performClick()
                assertTrue(requireNotNull(home).view.findViewById<View>(R.id.library_grid).isShown)
                status.text = "Public fixture retry"
                status.visibility = View.VISIBLE
                retry.visibility = View.VISIBLE
                assertTrue(status.isShown)
                retry.performClick()
                assertEquals(1, retried)
                assertTrue(!requireNotNull(home).saveState().getBoolean("home"))
                first
            }
            ui.onMain {
                assertTrue(requireNotNull(home).finishManaging())
                assertEquals(null, requireNotNull(home).shelfFilter)
                status.visibility = View.GONE
                retry.visibility = View.GONE
            }
            ui.await("Main bookshelf position was lost") {
                val list = requireNotNull(
                    home
                ).view.findViewById<ListView>(R.id.main_bookshelf_list)
                list.isShown &&
                    list.firstVisiblePosition == previous.first &&
                    list.getChildAt(0).top == previous.second
            }
            ui.onMain {
                val state = requireNotNull(home).saveState()
                restored = ReaderHomeView(
                    activity, TextView(activity).apply { visibility = View.GONE },
                    Button(activity).apply { visibility = View.GONE },
                    Button(activity).apply { visibility = View.GONE },
                    {}, {}, {}, {}, {}, {}, {}, { _, _, _ -> }, {}
                ).also {
                    it.restoreState(state)
                    it.render(catalog)
                    activity.setContentView(it.view)
                }
            }
            ui.await("Main scroll did not survive presentation recreation") {
                val list = requireNotNull(
                    restored
                ).view.findViewById<ListView>(R.id.main_bookshelf_list)
                list.firstVisiblePosition == previous.first &&
                    list.getChildAt(0).top == previous.second
            }
        } finally {
            ui.onMain {
                home?.close()
                restored?.close()
                activity.finish()
            }
        }
    }

    @Test
    fun gridSelectionRestoresAndShelfHomeUsesRecycledCells() {
        val activity = ui.launch()
        var home: BookshelfLibraryView? = null
        var restored: BookshelfLibraryView? = null
        var opened = 0
        fun create() = BookshelfLibraryView(
            activity,
            TextView(activity),
            Button(activity).apply { visibility = View.GONE },
            Button(activity).apply { visibility = View.GONE },
            {}, {}, {}, { opened++ }, {}, {}, {}, { _, _, _ -> }, {}
        )
        val records = (0 until 200).map { ReadingRecord("fixture:$it", "Fixture $it", it.toLong()) }
        val catalog = LibraryCatalog(records).addShelf(Bookshelf("s", "학습"))
            .addToShelves(records.take(4).map { it.uri }.toSet(), setOf("s"))
        try {
            ui.onMain {
                home = create()
                home?.render(catalog)
                activity.setContentView(requireNotNull(home).view)
            }
            try {
                ui.await("Shelf grid was not laid out") {
                    val grid = requireNotNull(home).view.findViewById<GridView>(R.id.library_grid)
                    grid.childCount > 0
                }
            } catch (error: AssertionError) {
                val details = ui.onMain {
                    val root = requireNotNull(home).view
                    val grid = root.findViewById<GridView>(R.id.library_grid)
                    val sample = grid.adapter.getView(0, null, grid)
                    "root=${root.width}x${root.height}, attached=${root.isAttachedToWindow}, " +
                        "grid=${grid.width}x${grid.height}, count=${grid.count}, " +
                        "children=${grid.childCount}, first=${grid.firstVisiblePosition}, " +
                        "columns=${grid.numColumns}, shown=${grid.isShown}, " +
                        "empty=${grid.adapter.isEmpty}, destroyed=${activity.isDestroyed}, " +
                        "requested=${grid.isLayoutRequested}, window=${grid.windowVisibility}, " +
                        "sample=${sample.javaClass.simpleName}"
                }
                throw AssertionError(details, error)
            }
            ui.onMain {
                val grid = requireNotNull(home).view.findViewById<GridView>(R.id.library_grid)
                grid.setSelection(
                    (0 until grid.count).first {
                        (grid.adapter.getItem(it) as? Bookshelf)?.id == "s"
                    }
                )
            }
            ui.await("Named bookshelf was not visible") {
                ui.views(requireNotNull(home).view).any {
                    it.id == R.id.bookshelf_tile && it.isShown && it.contentDescription == "학습"
                }
            }
            ui.onMain {
                ui.views(requireNotNull(home).view).first {
                    it.id == R.id.bookshelf_tile && it.isShown && it.contentDescription == "학습"
                }.performClick()
            }
            ui.await("Document grid did not open") {
                val grid = requireNotNull(home).view.findViewById<GridView>(R.id.library_grid)
                grid.count == 4 && grid.childCount > 0 && grid.getChildAt(0).id == R.id.home_record
            }
            val image = ui.onMain {
                val root = requireNotNull(home).view
                Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also {
                    root.draw(Canvas(it))
                }
            }
            val directory = requireNotNull(activity.getExternalFilesDir("verification"))
            File(directory, "public-bookshelf-grid.png").outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            image.recycle()
            val state = ui.onMain {
                val grid = requireNotNull(home).view.findViewById<GridView>(R.id.library_grid)
                grid.getChildAt(0).performLongClick()
                grid.getChildAt(1).performClick()
                assertEquals(0, opened)
                requireNotNull(home).saveState()
            }
            assertEquals(2, state.getStringArrayList("selected")?.size)
            ui.onMain {
                restored = create()
                restored?.restoreState(state)
                restored?.render(catalog)
                assertEquals(
                    state.getStringArrayList("selected"),
                    restored?.saveState()?.getStringArrayList("selected")
                )
                assertTrue(requireNotNull(restored).finishManaging())
                assertTrue(requireNotNull(restored).finishManaging())
                assertTrue(requireNotNull(restored).saveState().getBoolean("home"))
            }
        } finally {
            ui.onMain {
                home?.close()
                restored?.close()
                activity.finish()
            }
        }
    }

    @Test
    fun isolatedCoverProducesBoundedPixelsAndInvalidPdfFallsBack() {
        val context = instrumentation.targetContext

        @Suppress("DEPRECATION")
        val service = context.packageManager.getServiceInfo(
            android.content.ComponentName(context, BookCoverService::class.java),
            0
        )
        assertTrue(service.flags and ServiceInfo.FLAG_ISOLATED_PROCESS != 0)
        assertTrue(!service.exported)
        val pdf = File.createTempFile("public-cover-", ".pdf", context.cacheDir)
        val invalid = File.createTempFile("public-bad-cover-", ".pdf", context.cacheDir)
        SyntheticDocument.write(pdf)
        invalid.writeText("invalid public fixture")
        val uri = Uri.fromFile(pdf).toString()
        val bad = Uri.fromFile(invalid).toString()
        var loader: BookCoverLoader? = null
        try {
            ui.onMain {
                loader = BookCoverLoader(context) {}
                loader?.request(listOf(ReadingRecord(uri, "Public fixture", 1)))
            }
            ui.await("Isolated cover did not complete", 15_000) { loader?.get(uri) != null }
            ui.onMain {
                val bitmap = requireNotNull(loader?.get(uri))
                assertTrue(bitmap.width in 1..384 && bitmap.height in 1..512)
                loader?.request(listOf(ReadingRecord(bad, "Invalid fixture", 1)))
            }
            ui.await("Failed render did not release its request", 15_000) {
                BookCoverLoader::class.java.getDeclaredField("attempt").apply {
                    isAccessible = true
                }.get(loader) == null
            }
            ui.onMain {
                assertEquals(null, loader?.get(bad))
                assertNotNull(loader?.get(uri))
                loader?.cancel()
            }
        } finally {
            ui.onMain { loader?.close() }
            pdf.delete()
            invalid.delete()
        }
    }

    @Test
    fun legacyMigrationBacksUpPrivatelyAndPositionWritesKeepBothShelves() {
        val context = instrumentation.targetContext
        val directory = File(context.cacheDir, "public-migration-${System.nanoTime()}")
        assertTrue(directory.mkdir())
        val original = File(directory, "original.pdf").apply { writeText("public fixture") }
        val uri = Uri.fromFile(original).toString()
        val path = File(directory, "records.bin")
        try {
            DataOutputStream(path.outputStream()).use {
                it.writeInt(0x45565231)
                it.writeInt(5)
                it.writeInt(1)
                it.writeUTF(uri)
                it.writeUTF("Public fixture")
                it.writeLong(10)
                it.writeInt(20)
                it.writeBoolean(true)
                it.writeInt(3)
                it.writeDouble(0.2)
                it.writeDouble(0.6)
                it.writeDouble(4.0)
                it.writeUTF("ORIGINAL")
                it.writeInt(0)
                repeat(5) { _ -> it.writeByte(0) }
                it.writeInt(2)
                it.writeUTF("one")
                it.writeUTF("First")
                it.writeUTF("two")
                it.writeUTF("Second")
                it.writeInt(1)
                it.writeUTF(uri)
                it.writeInt(1)
                it.writeUTF(uri)
                it.writeUTF("one")
            }
            val legacy = path.readBytes()
            val store = DocumentLibrary(path)
            store.addToShelves(setOf(uri), setOf("two"))
            store.updatePosition(uri, ReadingPosition(7, 0.3, 0.4, 5.0))
            val read = DocumentLibrary(path).snapshot()
            assertEquals(setOf("one", "two"), read.memberships[uri])
            assertEquals(7, read.records.single().position?.page)
            assertArrayEquals(legacy, File(directory, "records.bin.before-v6").readBytes())
            store.removeShelf("one")
            assertEquals(setOf("two"), DocumentLibrary(path).snapshot().memberships[uri])
            assertTrue(original.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun largeFontsAndLandscapeKeepDocumentGridUsable() {
        val activity = ui.launch()
        try {
            ui.onMain {
                for (font in listOf(1f, 1.5f, 2f)) {
                    for ((width, height) in listOf(320 to 640, 640 to 320)) {
                        val configuration = Configuration(activity.resources.configuration).apply {
                            fontScale = font
                            screenWidthDp = width
                            screenHeightDp = height
                        }
                        val context = ContextThemeWrapper(activity, R.style.ReaderLight).apply {
                            applyOverrideConfiguration(configuration)
                        }
                        val style = ReaderStyle(context)
                        val home = BookshelfLibraryView(
                            context, TextView(context), Button(context), Button(context),
                            {}, {}, {}, {}, {}, {}, {}, { _, _, _ -> }, {}
                        )
                        try {
                            val records = listOf(ReadingRecord("public", "긴 문서 이름".repeat(15), 1))
                            val shelf = Bookshelf("long", "아주 긴 책장 ".repeat(8))
                            home.restoreState(
                                Bundle().apply {
                                    putBoolean("home", false)
                                    putString("shelf", shelf.id)
                                    putBoolean("managing", true)
                                    putStringArrayList("selected", arrayListOf("public"))
                                }
                            )
                            home.render(
                                LibraryCatalog(records).addShelf(shelf)
                                    .addToShelves(setOf("public"), setOf(shelf.id))
                            )
                            home.view.measure(
                                View.MeasureSpec.makeMeasureSpec(
                                    style.dp(width),
                                    View.MeasureSpec.EXACTLY
                                ),
                                View.MeasureSpec.makeMeasureSpec(
                                    style.dp(height),
                                    View.MeasureSpec.EXACTLY
                                )
                            )
                            home.view.layout(0, 0, style.dp(width), style.dp(height))
                            val grid = home.view.findViewById<GridView>(R.id.library_grid)
                            assertTrue(
                                "No reading list area at $font/$width",
                                grid.height >= style.dp(48)
                            )
                            assertTrue("No document cell at $font/$width", grid.childCount > 0)
                            val actions = home.view.findViewById<android.view.ViewGroup>(
                                R.id.library_document_actions
                            )
                            assertEquals(View.VISIBLE, actions.visibility)
                            assertEquals(2, actions.childCount)
                            val move = actions.getChildAt(0)
                            val remove = actions.getChildAt(1)
                            assertTrue(
                                "Selection controls overlap at $font/$width",
                                move.right < remove.left
                            )
                            assertTrue(
                                "Selection controls overflow at $font/$width",
                                remove.right <= actions.width - actions.paddingRight
                            )
                            assertTrue(
                                "Selection controls cover the list at $font/$width",
                                grid.bottom <= actions.top
                            )
                        } finally {
                            home.close()
                        }
                    }
                }
            }
        } finally {
            ui.onMain { activity.finish() }
        }
    }
}
