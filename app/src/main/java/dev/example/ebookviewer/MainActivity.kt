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
import android.widget.CheckBox
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
    private lateinit var root: FrameLayout
    private var settingsDialog: AlertDialog? = null
    private var navigationDialog: AlertDialog? = null
    private lateinit var pageIndicator: TextView
    private lateinit var pageJumpButton: Button
    private lateinit var bookmarksButton: Button
    private var pageNumbersVisible = false
    private data class PageTarget(val page: Int, val y: Double? = null)
    private var pendingNavigation: PageTarget? = null
    private var reading = false
    private lateinit var libraryView: LinearLayout
    private lateinit var libraryScroll: ScrollView
    private lateinit var homeButton: Button
    private val library get() = (application as ReaderApplication).library
    private val settings get() = (application as ReaderApplication).settings
    private val bookmarks get() = (application as ReaderApplication).bookmarks
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
                applyPendingNavigation()
                updatePageIndicator()
                setStatus(
                    if (durableAccess) {
                        getString(R.string.document_ready, it.size)
                    } else {
                        getString(R.string.temporary_access, it.size)
                    }
                )
                if (pendingNavigation == null) {
                    if (first) saveReading() else scheduleSave()
                }
            },
            onTile = viewer::putTile,
            onError = {
                // Restoring an existing record scans all page sizes before publishing a new position.
                loaded = false
                pendingNavigation = null
                updatePageIndicator()
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
        viewer.onPositionChanged = {
            updatePageIndicator()
            scheduleSave()
        }
        viewer.onUserInteraction = { pendingNavigation = null }
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
        pageJumpButton = Button(this).apply {
            setText(R.string.go_to_page)
            setOnClickListener { showPageJump() }
        }
        bookmarksButton = Button(this).apply {
            setText(R.string.bookmarks)
            setOnClickListener { showBookmarks() }
        }
        readerTools.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(pageJumpButton, LinearLayout.LayoutParams(0, -2, 1f))
                addView(bookmarksButton, LinearLayout.LayoutParams(0, -2, 1f))
            }
        )
        pageIndicator = TextView(this).apply {
            id = R.id.page_indicator
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(170, 0, 0, 0))
            val padding = (6 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            visibility = View.GONE
            textSize = 12f
        }
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.DKGRAY)
            addView(viewer, FrameLayout.LayoutParams(-1, -1))
            addView(homeScreen, FrameLayout.LayoutParams(-1, -1))
            addView(pageIndicator, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END))
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
                    val numberLayout = pageIndicator.layoutParams as FrameLayout.LayoutParams
                    numberLayout.setMargins(safe.left, 0, safe.right, safe.bottom)
                    pageIndicator.layoutParams = numberLayout
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
        viewer.onBackgroundColorChanged = { if (reading) root.setBackgroundColor(it) }
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
        settings.loadBackground { result ->
            if (isDestroyed) return@loadBackground
            result.onSuccess(viewer::setBackgroundSettings)
                .onFailure { reportError(R.string.settings_error) }
        }
        settings.loadPageNumbers { result ->
            if (isDestroyed) return@loadPageNumbers
            result.onSuccess {
                pageNumbersVisible = it
                updatePageIndicator()
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
                openDocument(
                    Uri.parse(uri),
                    savedInstanceState.getBundle("viewport"),
                    relink = savedInstanceState.getString("replacedUri")
                )
            }
        }
        if (savedInstanceState?.containsKey("pendingPage") == true) {
            pendingNavigation = PageTarget(
                savedInstanceState.getInt("pendingPage"),
                if (savedInstanceState.containsKey("pendingPageY")) {
                    savedInstanceState.getDouble("pendingPageY")
                } else {
                    null
                }
            )
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
        pendingNavigation = null
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
        pendingNavigation = null
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
        root.setBackgroundColor(viewer.outsideBackgroundColor())
        homeScreen.visibility = View.GONE
        viewer.visibility = View.VISIBLE
        homeButton.visibility = View.VISIBLE
        hideTools()
        updatePageIndicator()
    }

    private fun showLibrary() {
        saveReading()
        pendingNavigation = null
        cancelSave()
        opening++
        loaded = false
        sourceUri = null
        activeRecord = null
        synthetic = false
        reading = false
        root.setBackgroundColor(Color.DKGRAY)
        worker.closeDocument()
        viewer.reset()
        viewer.restoreViewport(null)
        viewer.visibility = View.GONE
        homeButton.visibility = View.GONE
        homeScreen.visibility = View.VISIBLE
        readerTools.visibility = View.GONE
        readingWindow.apply(false)
        setStatus(R.string.ready)
        updatePageIndicator()
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
        if (!loaded || activeRecord == null || savePending || pendingNavigation != null) return
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
        if (replacement != null && replacement != updated.uri) {
            // Migrate only after a PDF was opened successfully. Keep the old record until it succeeds.
            bookmarks.execute({ it.relink(replacement, updated.uri) }) { migrated ->
                migrated.onSuccess { persistReadingRecord(updated, replacement) }
                    .onFailure { if (!isDestroyed) reportError(R.string.bookmarks_error) }
            }
        } else {
            persistReadingRecord(updated, replacement)
        }
    }

    private fun persistReadingRecord(updated: ReadingRecord, replacement: String?) {
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

    private fun updatePageIndicator() {
        if (!::pageIndicator.isInitialized) return
        val position = viewer.readingPosition()
        val show = reading && loaded && pageNumbersVisible && position != null
        pageIndicator.visibility = if (show) {
            View.VISIBLE
        } else {
            View.GONE
        }
        if (position != null) {
            val text = getString(R.string.current_page, position.page + 1, expectedPages)
            if (pageIndicator.text != text) pageIndicator.text = text
        }
        pageJumpButton.isEnabled = loaded && position != null
        bookmarksButton.isEnabled = loaded && activeRecord != null && position != null
    }

    private fun showPageJump() {
        if (!loaded || navigationDialog != null) return
        val current = viewer.readingPosition() ?: return
        trackNavigationDialog(pageJumpDialog(current.page, expectedPages) { requestPage(it) })
    }

    private fun requestPage(page: Int, y: Double? = null) {
        if (page !in 0 until expectedPages) {
            reportError(R.string.page_unavailable)
            return
        }
        pendingNavigation = PageTarget(page, y)
        hideTools()
        applyPendingNavigation()
        if (pendingNavigation != null) {
            setStatus(R.string.page_preparing)
            Toast.makeText(this, R.string.page_preparing, Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyPendingNavigation() {
        val target = pendingNavigation ?: return
        if (expectedPages > 0 && target.page !in 0 until expectedPages) {
            pendingNavigation = null
            reportError(R.string.page_unavailable)
        } else if (viewer.goToPage(target.page, target.y)) {
            pendingNavigation = null
            cancelSave()
            saveReading()
        }
    }

    private fun trackNavigationDialog(dialog: AlertDialog) {
        navigationDialog?.dismiss()
        navigationDialog = dialog
        dialog.setOnDismissListener {
            if (navigationDialog === dialog) navigationDialog = null
            hideTools()
        }
        dialog.show()
    }

    private fun showBookmarks() {
        if (navigationDialog != null) return
        val record = activeRecord ?: return
        val position = viewer.readingPosition() ?: return
        val generation = opening
        bookmarks.execute({ it.list(record.uri) }) { result ->
            if (isDestroyed || generation != opening || navigationDialog != null) return@execute
            result.onSuccess { entries ->
                val labels = entries.map { getString(R.string.bookmark_page, it.page + 1) }
                val builder = AlertDialog.Builder(this)
                    .setTitle(R.string.bookmarks)
                    .setPositiveButton(R.string.add_bookmark) { _, _ ->
                        bookmarks.execute({
                            it.put(Bookmark(record.uri, position.page, position.y))
                        }) { saved ->
                            if (!isDestroyed) {
                                saved.onSuccess {
                                    Toast.makeText(
                                        this,
                                        R.string.bookmark_saved,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }.onFailure { reportError(R.string.bookmarks_error) }
                            }
                        }
                    }
                    .setNegativeButton(R.string.close_settings, null)
                if (entries.isEmpty()) {
                    builder.setMessage(R.string.no_bookmarks)
                } else {
                    builder.setItems(labels.toTypedArray()) { _, index ->
                        val entry = entries[index]
                        requestPage(entry.page, entry.y)
                    }
                    builder.setView(TextView(this).apply { setText(R.string.bookmark_delete_hint) })
                }
                val dialog = builder.create()
                trackNavigationDialog(dialog)
                dialog.listView?.setOnItemLongClickListener { _, _, index, _ ->
                    val entry = entries[index]
                    dialog.dismiss()
                    val confirmation = AlertDialog.Builder(this)
                        .setMessage(getString(R.string.delete_bookmark_message, entry.page + 1))
                        .setPositiveButton(R.string.delete_bookmark) { _, _ ->
                            bookmarks.execute({ it.remove(record.uri, entry.page) }) { removed ->
                                if (!isDestroyed) {
                                    removed.onFailure { reportError(R.string.bookmarks_error) }
                                }
                            }
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .create()
                    trackNavigationDialog(confirmation)
                    true
                }
            }.onFailure { reportError(R.string.bookmarks_error) }
        }
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
                    .setTitle(R.string.reader_settings_title)
                    .setView(settingsControls())
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

    private fun showBackgroundSettings() {
        settings.loadBackground { result ->
            if (isDestroyed || settingsDialog != null) return@loadBackground
            result.onSuccess { current ->
                val dialog = backgroundSettingsDialog(current) { selected ->
                    settings.saveBackground(selected) { saved ->
                        if (isDestroyed) return@saveBackground
                        saved.onSuccess(viewer::setBackgroundSettings)
                            .onFailure { reportError(R.string.settings_error) }
                    }
                }
                settingsDialog = dialog
                dialog.setOnDismissListener {
                    settingsDialog = null
                    hideTools()
                }
                dialog.show()
            }.onFailure { reportError(R.string.settings_error) }
        }
    }

    private fun settingsControls(): LinearLayout {
        val backgroundButton = Button(this).apply {
            setText(R.string.background_settings)
            setOnClickListener {
                settingsDialog?.dismiss()
                showBackgroundSettings()
            }
        }
        var binding = false
        val checkbox = CheckBox(this).apply {
            setText(R.string.show_page_numbers)
            isChecked = pageNumbersVisible
            setOnCheckedChangeListener { _, checked ->
                if (!binding) {
                    isEnabled = false
                    settings.savePageNumbers(checked) { saved ->
                        if (isDestroyed) return@savePageNumbers
                        isEnabled = true
                        saved.onSuccess {
                            pageNumbersVisible = it
                            updatePageIndicator()
                        }.onFailure {
                            binding = true
                            isChecked = pageNumbersVisible
                            binding = false
                            reportError(R.string.settings_error)
                        }
                    }
                }
            }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(backgroundButton)
            addView(checkbox)
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
        navigationDialog?.dismiss()
        navigationDialog = null
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
        pendingNavigation?.let {
            outState.putInt("pendingPage", it.page)
            it.y?.let { fraction -> outState.putDouble("pendingPageY", fraction) }
        }
        outState.putString("sourceUri", sourceUri)
        outState.putBoolean("synthetic", synthetic)
        outState.putString("reselectUri", reselectRecord?.uri)
        outState.putString("replacedUri", replacedUri)
        outState.putBundle("viewport", viewer.saveViewport())
        super.onSaveInstanceState(outState)
    }

    companion object {
        private const val OPEN_PDF = 1
    }
}
