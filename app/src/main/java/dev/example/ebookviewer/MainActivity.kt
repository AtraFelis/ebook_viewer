package dev.example.ebookviewer

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

class MainActivity : Activity() {
    private lateinit var worker: PdfWorker
    private lateinit var viewer: PdfCanvasView
    private lateinit var status: TextView
    private lateinit var libraryView: LinearLayout
    private lateinit var libraryScroll: ScrollView
    private lateinit var homeButton: Button
    private val library get() = (application as ReaderApplication).library
    private val main = Handler(Looper.getMainLooper())
    private var sourceUri: String? = null
    private var synthetic = false
    private var loaded = false
    private var expectedPages = 0
    private var opening = 0
    private var activeRecord: ReadingRecord? = null
    private var replacedUri: String? = null
    private var reselectRecord: ReadingRecord? = null
    private var durableAccess = true
    private var savePending = false
    private val saveTask = Runnable {
        savePending = false
        saveReading()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { setText(R.string.ready) }
        viewer = PdfCanvasView(this) { worker.request(it) }
        worker = PdfWorker(
            onLoaded = {
                val first = !loaded
                loaded = true
                activeRecord = activeRecord?.copy(pageCount = expectedPages)
                viewer.setPages(it)
                status.text = if (durableAccess) {
                    getString(R.string.document_ready, it.size)
                } else {
                    getString(R.string.temporary_access, it.size)
                }
                if (first) saveReading() else scheduleSave()
            },
            onTile = viewer::putTile,
            onError = {
                // Restoring an existing record scans all page sizes before publishing a new position.
                loaded = false
                cancelSave()
                status.setText(R.string.load_error)
                if (!isFinishing) {
                    AlertDialog.Builder(this)
                        .setMessage(R.string.load_error)
                        .setPositiveButton(R.string.select_again) { _, _ ->
                            reselectRecord = activeRecord
                            pickDocument()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            },
            onPageCount = { expectedPages = it }
        )
        viewer.onPositionChanged = ::scheduleSave
        libraryView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        libraryScroll = ScrollView(this).apply { addView(libraryView) }
        homeButton = Button(this).apply {
            setText(R.string.document_list)
            setOnClickListener { showLibrary() }
            visibility = View.GONE
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setOnApplyWindowInsetsListener { view, insets ->
                @Suppress("DEPRECATION")
                view.setPadding(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom
                )
                insets
            }
            addView(
                Button(this@MainActivity).apply {
                    setText(R.string.open_pdf)
                    setOnClickListener {
                        reselectRecord = null
                        pickDocument()
                    }
                }
            )
            addView(
                Button(this@MainActivity).apply {
                    setText(R.string.sample_pdf)
                    setOnClickListener { openSample() }
                }
            )
            addView(homeButton)
            addView(status)
            addView(libraryScroll, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(viewer, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(root)
        viewer.visibility = View.GONE
        reselectRecord = savedInstanceState?.getString("reselectUri")?.let { uri ->
            ReadingRecord(uri, getString(R.string.untitled_pdf), 0)
        }
        if (savedInstanceState?.getBoolean("synthetic") == true) {
            openSample(savedInstanceState.getBundle("viewport"))
        } else {
            val uri = savedInstanceState?.getString("sourceUri")
            if (uri == null) {
                refreshLibrary()
            } else {
                openDocument(Uri.parse(uri), savedInstanceState.getBundle("viewport"))
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun pickDocument() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "application/pdf"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            },
            OPEN_PDF
        )
    }

    @Deprecated("Platform Activity result API avoids a new runtime dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OPEN_PDF && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            openDocument(uri, grantFlags = data.flags, relink = reselectRecord?.uri)
            reselectRecord = null
        }
    }

    fun openDocument(uri: Uri) = openDocument(uri, null)

    private fun openDocument(
        uri: Uri,
        viewport: Bundle? = null,
        grantFlags: Int = 0,
        relink: String? = null
    ) {
        saveReading()
        cancelSave()
        worker.closeDocument()
        loaded = false
        expectedPages = 0
        activeRecord = null
        sourceUri = uri.toString()
        synthetic = false
        replacedUri = relink
        viewer.reset()
        viewer.restoreViewport(viewport)
        showReader()
        status.setText(R.string.loading)
        val generation = ++opening
        val resolver = applicationContext.contentResolver
        val fallbackTitle = getString(R.string.untitled_pdf)
        library.execute({ store ->
            val records = store.list()
            val previous = records.find { it.uri == uri.toString() }
                ?: records.find { it.uri == relink }
            if (uri.scheme == "content" &&
                grantFlags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0
            ) {
                try {
                    resolver.takePersistableUriPermission(
                        uri,
                        grantFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // Some providers offer only temporary access; display that limitation below.
                }
            }
            val durable = uri.scheme == "file" ||
                resolver.persistedUriPermissions.any {
                    it.uri == uri && it.isReadPermission
                }
            val title = try {
                if (uri.scheme == "content") {
                    resolver.query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null
                    )?.use {
                        if (it.moveToFirst()) it.getString(0)?.takeIf(String::isNotBlank) else null
                    }
                } else {
                    uri.lastPathSegment
                }
            } catch (_: Exception) {
                // Display-name metadata is optional; opening the descriptor still decides readability.
                null
            }
            ReadingRecord(
                uri.toString(),
                title ?: previous?.title ?: fallbackTitle,
                System.currentTimeMillis(),
                previous?.pageCount ?: 0,
                previous?.position
            ) to durable
        }) { result ->
            if (isDestroyed || generation != opening) return@execute
            result.onSuccess { (record, durable) ->
                activeRecord = record
                durableAccess = durable
                if (viewport !=
                    null
                ) {
                    viewer.restoreViewport(viewport)
                } else {
                    viewer.restorePosition(record.position)
                }
                worker.openForRestore(viewer.hasRestoredViewport()) {
                    requireNotNull(resolver.openFileDescriptor(uri, "r"))
                }
            }.onFailure { status.setText(R.string.library_error) }
        }
    }

    private fun openSample(viewport: Bundle? = null) {
        saveReading()
        cancelSave()
        opening++
        sourceUri = null
        activeRecord = null
        replacedUri = null
        synthetic = true
        durableAccess = true
        loaded = false
        expectedPages = 0
        viewer.reset()
        viewer.restoreViewport(viewport)
        showReader()
        status.setText(R.string.loading)
        worker.openForRestore(viewer.hasRestoredViewport()) { createSample() }
    }

    private fun showReader() {
        libraryScroll.visibility = View.GONE
        viewer.visibility = View.VISIBLE
        homeButton.visibility = View.VISIBLE
    }

    private fun showLibrary() {
        saveReading()
        cancelSave()
        opening++
        loaded = false
        sourceUri = null
        activeRecord = null
        synthetic = false
        worker.closeDocument()
        viewer.reset()
        viewer.restoreViewport(null)
        viewer.visibility = View.GONE
        homeButton.visibility = View.GONE
        libraryScroll.visibility = View.VISIBLE
        status.setText(R.string.ready)
        refreshLibrary()
    }

    private fun refreshLibrary() {
        library.execute({ it.list() }) { result ->
            if (isDestroyed || viewer.visibility == View.VISIBLE) return@execute
            result.onSuccess { records ->
                libraryView.removeAllViews()
                fun section(label: Int, entries: List<ReadingRecord>) {
                    libraryView.addView(TextView(this).apply { setText(label) })
                    if (entries.isEmpty()) {
                        libraryView.addView(TextView(this).apply { setText(R.string.no_documents) })
                    }
                    for (record in entries) {
                        libraryView.addView(
                            Button(this).apply {
                                isAllCaps = false
                                val position = record.position
                                text = if (position == null) {
                                    record.title
                                } else {
                                    getString(
                                        R.string.reading_entry,
                                        record.title,
                                        position.page + 1,
                                        record.pageCount
                                    )
                                }
                                setOnClickListener { openDocument(Uri.parse(record.uri)) }
                                setOnLongClickListener {
                                    AlertDialog.Builder(this@MainActivity)
                                        .setMessage(R.string.remove_record_message)
                                        .setPositiveButton(R.string.remove_record) { _, _ ->
                                            library.execute({ it.remove(record.uri) }) { removed ->
                                                if (!isDestroyed) {
                                                    removed.onSuccess { refreshLibrary() }
                                                        .onFailure {
                                                            status.setText(R.string.save_error)
                                                        }
                                                }
                                            }
                                        }
                                        .setNegativeButton(android.R.string.cancel, null)
                                        .show()
                                    true
                                }
                            }
                        )
                    }
                }
                section(R.string.continue_reading, records.filter { it.position != null })
                section(R.string.recent_documents, records)
                libraryView.addView(TextView(this).apply { setText(R.string.remove_record_hint) })
            }.onFailure { status.setText(R.string.library_error) }
        }
    }

    private fun scheduleSave() {
        if (!loaded || activeRecord == null || savePending) return
        savePending = true
        main.postDelayed(saveTask, 1_000)
    }

    private fun cancelSave() {
        main.removeCallbacks(saveTask)
        savePending = false
    }

    private fun saveReading() {
        if (!loaded) return
        val record = activeRecord ?: return
        val position = viewer.readingPosition() ?: return
        val updated = record.copy(position = position)
        activeRecord = updated
        val replacement = replacedUri
        library.execute({ it.put(updated, replacement) }) { result ->
            if (isDestroyed) return@execute
            result.onSuccess {
                if (activeRecord?.uri == updated.uri &&
                    replacedUri == replacement
                ) {
                    replacedUri = null
                }
            }.onFailure { status.setText(R.string.save_error) }
        }
    }

    /** Synthetic fixture only. File IO and generation run on the PDF worker. */
    private fun createSample(): ParcelFileDescriptor {
        val file = File.createTempFile("p0-synthetic-", ".pdf", cacheDir)
        try {
            SyntheticDocument.write(file)
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } finally {
            file.delete()
        }
    }

    @Deprecated("Platform back handling until the P1 immersive navigation slice")
    override fun onBackPressed() {
        if (viewer.visibility == View.VISIBLE) showLibrary() else super.onBackPressed()
    }

    override fun onPause() {
        saveReading()
        cancelSave()
        super.onPause()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        viewer.trimCache()
    }

    override fun onDestroy() {
        cancelSave()
        opening++
        worker.close()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        saveReading()
        outState.putString("sourceUri", sourceUri)
        outState.putBoolean("synthetic", synthetic)
        outState.putString("reselectUri", reselectRecord?.uri)
        outState.putBundle("viewport", viewer.saveViewport())
        super.onSaveInstanceState(outState)
    }

    companion object {
        private const val OPEN_PDF = 1
    }
}
