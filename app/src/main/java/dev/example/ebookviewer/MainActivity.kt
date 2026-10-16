package dev.example.ebookviewer

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {
    private lateinit var worker: PdfWorker
    private lateinit var viewer: PdfCanvasView
    private lateinit var status: TextView
    private lateinit var homeStatus: TextView
    private lateinit var homeScreen: LinearLayout
    private lateinit var readerTools: LinearLayout
    private lateinit var readingWindow: ReadingWindow
    private var settingsDialog: AlertDialog? = null
    private var reading = false
    private lateinit var libraryView: LinearLayout
    private lateinit var libraryScroll: ScrollView
    private lateinit var homeButton: Button
    private val library get() = (application as ReaderApplication).library
    private val settings get() = (application as ReaderApplication).settings
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
        readingWindow = ReadingWindow(window)
        status = TextView(this).apply { setText(R.string.ready) }
        homeStatus = TextView(this).apply { setText(R.string.ready) }
        viewer = PdfCanvasView(this) { worker.request(it) }
        worker = PdfWorker(
            onLoaded = {
                val first = !loaded
                loaded = true
                activeRecord = activeRecord?.copy(pageCount = expectedPages)
                viewer.setPages(it)
                setStatus(
                    if (durableAccess) {
                        getString(R.string.document_ready, it.size)
                    } else {
                        getString(R.string.temporary_access, it.size)
                    }
                )
                if (first) saveReading() else scheduleSave()
            },
            onTile = viewer::putTile,
            onError = {
                // Restoring an existing record scans all page sizes before publishing a new position.
                loaded = false
                cancelSave()
                setStatus(R.string.load_error)
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
        viewer.setOnClickListener {
            if (reading) {
                readerTools.visibility =
                    if (readerTools.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        libraryView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        libraryScroll = ScrollView(this).apply { addView(libraryView) }
        homeButton = Button(this).apply {
            setText(R.string.document_list)
            setOnClickListener { showLibrary() }
            visibility = View.GONE
        }
        homeScreen = LinearLayout(this).apply {
            id = R.id.home_screen
            orientation = LinearLayout.VERTICAL
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
            addView(
                Button(this@MainActivity).apply {
                    setText(R.string.settings)
                    setOnClickListener { showSettings() }
                }
            )
            addView(TextView(this@MainActivity).apply { setText(R.string.reader_gesture_hint) })
            addView(homeStatus)
            addView(libraryScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        readerTools = LinearLayout(this).apply {
            id = R.id.reader_tools
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(245, 255, 255, 255))
            visibility = View.GONE
            addView(status)
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(
                        Button(this@MainActivity).apply {
                            setText(R.string.settings)
                            setOnClickListener { showSettings() }
                        },
                        LinearLayout.LayoutParams(0, -2, 1f)
                    )
                    addView(homeButton, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(
                        Button(this@MainActivity).apply {
                            setText(R.string.resume_reading)
                            setOnClickListener { hideTools() }
                        },
                        LinearLayout.LayoutParams(0, -2, 1f)
                    )
                }
            )
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.DKGRAY)
            addView(viewer, FrameLayout.LayoutParams(-1, -1))
            addView(homeScreen, FrameLayout.LayoutParams(-1, -1))
            addView(readerTools, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
            setOnApplyWindowInsetsListener { _, insets ->
                // Only physical cutouts reserve PDF space; transient system bars never resize it.
                if (Build.VERSION.SDK_INT >= 28) {
                    val cutout = insets.displayCutout
                    val margins = listOf(
                        cutout?.safeInsetLeft ?: 0,
                        cutout?.safeInsetTop ?: 0,
                        cutout?.safeInsetRight ?: 0,
                        cutout?.safeInsetBottom ?: 0
                    )
                    val parameters = viewer.layoutParams as FrameLayout.LayoutParams
                    if (listOf(
                            parameters.leftMargin,
                            parameters.topMargin,
                            parameters.rightMargin,
                            parameters.bottomMargin
                        ) != margins
                    ) {
                        parameters.setMargins(margins[0], margins[1], margins[2], margins[3])
                        viewer.layoutParams = parameters
                    }
                    if (Build.VERSION.SDK_INT <
                        30
                    ) {
                        readerTools.setPadding(margins[0], margins[1], margins[2], 0)
                    }
                }
                if (Build.VERSION.SDK_INT >= 30) {
                    val safe = insets.getInsets(
                        WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                    )
                    homeScreen.setPadding(safe.left, safe.top, safe.right, safe.bottom)
                    readerTools.setPadding(safe.left, safe.top, safe.right, 0)
                } else {
                    @Suppress("DEPRECATION")
                    homeScreen.setPadding(
                        insets.systemWindowInsetLeft,
                        insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight,
                        insets.systemWindowInsetBottom
                    )
                }
                insets
            }
        }
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                handleBack()
            }
        }
        viewer.visibility = View.GONE
        readingWindow.apply(false)
        settings.load { result ->
            if (isDestroyed) return@load
            result.onSuccess {
                // AUTO preserves the platform's rotation policy across Activity recreation.
                if (it != OrientationMode.AUTO) applyOrientation(it)
            }.onFailure { reportError(R.string.settings_error) }
        }
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
        setStatus(R.string.loading)
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
            }.onFailure { reportError(R.string.library_error) }
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
        setStatus(R.string.loading)
        worker.openForRestore(viewer.hasRestoredViewport()) { createSample() }
    }

    private fun showReader() {
        reading = true
        homeScreen.visibility = View.GONE
        viewer.visibility = View.VISIBLE
        homeButton.visibility = View.VISIBLE
        hideTools()
    }

    private fun showLibrary() {
        saveReading()
        cancelSave()
        opening++
        loaded = false
        sourceUri = null
        activeRecord = null
        synthetic = false
        reading = false
        worker.closeDocument()
        viewer.reset()
        viewer.restoreViewport(null)
        viewer.visibility = View.GONE
        homeButton.visibility = View.GONE
        homeScreen.visibility = View.VISIBLE
        readerTools.visibility = View.GONE
        readingWindow.apply(false)
        setStatus(R.string.ready)
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
                                                            reportError(R.string.save_error)
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
            }.onFailure { reportError(R.string.library_error) }
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
            }.onFailure { reportError(R.string.save_error) }
        }
    }

    private fun setStatus(message: CharSequence) {
        status.text = message
        homeStatus.text = message
    }

    private fun setStatus(resource: Int) = setStatus(getString(resource))

    private fun reportError(resource: Int) {
        setStatus(resource)
        Toast.makeText(this, resource, Toast.LENGTH_LONG).show()
    }

    private fun hideTools() {
        readerTools.visibility = View.GONE
        readingWindow.apply(reading)
    }

    private fun showSettings() {
        if (settingsDialog != null) return
        settings.load { result ->
            if (isDestroyed || settingsDialog != null) return@load
            result.onSuccess { mode ->
                val labels =
                    arrayOf(
                        getString(R.string.orientation_auto),
                        getString(R.string.orientation_portrait),
                        getString(R.string.orientation_landscape)
                    )
                val dialog = AlertDialog.Builder(this)
                    .setTitle(R.string.orientation_title)
                    .setSingleChoiceItems(labels, mode.ordinal) { selectedDialog, index ->
                        selectedDialog.dismiss()
                        val selected = OrientationMode.entries[index]
                        settings.save(selected) { saved ->
                            if (isDestroyed) return@save
                            saved.onSuccess {
                                saveReading()
                                hideTools()
                                applyOrientation(it)
                            }.onFailure { reportError(R.string.settings_error) }
                        }
                    }
                    .setNegativeButton(R.string.close_settings, null)
                    .create()
                settingsDialog = dialog
                dialog.setOnDismissListener {
                    settingsDialog = null
                    hideTools()
                }
                dialog.show()
            }.onFailure { reportError(R.string.settings_error) }
        }
    }

    private fun applyOrientation(mode: OrientationMode) {
        val requested = when (mode) {
            OrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        if (requestedOrientation != requested) requestedOrientation = requested
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

    @Suppress("DEPRECATION")
    @Deprecated("Legacy device back handling; Android 13+ uses OnBackInvokedDispatcher")
    override fun onBackPressed() {
        handleBack()
    }

    private fun handleBack() {
        when {
            reading && readerTools.visibility == View.VISIBLE -> hideTools()
            reading -> showLibrary()
            else -> finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::readingWindow.isInitialized) readingWindow.apply(reading)
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
        settingsDialog?.setOnDismissListener(null)
        settingsDialog?.dismiss()
        settingsDialog = null
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
