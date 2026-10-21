package dev.example.ebookviewer

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.OperationCanceledException
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {
    private lateinit var worker: PdfWorker
    private lateinit var viewer: PdfCanvasView
    private lateinit var status: TextView
    private lateinit var homeStatus: TextView
    private lateinit var retryButton: Button
    private lateinit var reselectButton: Button
    private data class FailedOpen(
        val uri: String?,
        val viewport: Bundle?,
        val record: ReadingRecord?,
        val relink: String?,
        val synthetic: Boolean
    )
    private var failedOpen: FailedOpen? = null
    private lateinit var homeScreen: LinearLayout
    private lateinit var homePresentation: ReaderHomeView
    private lateinit var homeScroll: ScrollView
    private lateinit var readerTools: LinearLayout
    private lateinit var toolsPresentation: ReaderToolsView
    private lateinit var readingWindow: ReadingWindow
    private lateinit var displayWindow: DisplayWindow
    private var displaySettings = DisplaySettings()
    private var appliedDarkTheme = false
    private var resumed = false
    private lateinit var root: FrameLayout
    private var settingsDialog: AlertDialog? = null
    private var settingsHomeDialog: AlertDialog? = null
    private var settingsChildLoading = false
    private var orientationSettingsOpen = false
    private enum class SettingsFormKind { BACKGROUND, DISPLAY, LAYOUT }
    private var settingsFormKind: SettingsFormKind? = null
    private var editableSettingsForm: EditableSettingsForm? = null
    private var pendingLayoutDraft: Bundle? = null
    private var pendingLayoutWrites = 0
    private lateinit var readingHintLabel: TextView
    private val hintState get() = (application as ReaderApplication).readingHint
    private var documentLayoutSetting: Button? = null
    private var bindPageNumberSetting: ((Boolean) -> Unit)? = null
    private var navigationDialog: AlertDialog? = null
    private var pageTextDialog: AlertDialog? = null
    private lateinit var pageTextClient: PageTextClient
    private lateinit var readPageButton: Button
    private lateinit var pageIndicator: PageNumberView
    private lateinit var pageJumpButton: Button
    private lateinit var bookmarksButton: Button
    private var pageNumbersVisible = false
    private data class PageTarget(val page: Int, val y: Double? = null)
    private var pendingNavigation: PageTarget? = null
    private var reading = false
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
        val restoredTheme = savedInstanceState?.getString("displayTheme")?.let { name ->
            ReaderTheme.entries.firstOrNull { it.name == name }
        } ?: ReaderTheme.SYSTEM
        displaySettings = DisplaySettings(theme = restoredTheme)
        appliedDarkTheme = restoredTheme.isDark(systemDarkTheme())
        setTheme(
            if (appliedDarkTheme) {
                R.style.ReaderDark
            } else {
                R.style.ReaderLight
            }
        )
        super.onCreate(savedInstanceState)
        pageTextClient = PageTextClient(this)
        readingWindow = ReadingWindow(window, !appliedDarkTheme, themeBackground())
        displayWindow = DisplayWindow(window)
        status = TextView(this).apply { setText(R.string.ready) }
        homeStatus = TextView(this).apply {
            setText(R.string.ready)
            visibility = View.GONE
        }
        status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        homeStatus.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        viewer = PdfCanvasView(this) { worker.request(it) }
        worker = PdfWorker(
            onLoaded = {
                val first = !loaded
                loaded = true
                updateDisplayWindow()
                activeRecord = activeRecord?.copy(pageCount = expectedPages)
                viewer.setPages(it)
                if (first) hideTools()
                if (first) {
                    hintState.documentOpened()
                    updateReadingHint()
                    pendingLayoutDraft?.let { draft ->
                        pendingLayoutDraft = null
                        showPageLayoutSettings(draft)
                    }
                }
                applyPendingNavigation()
                updatePageIndicator()
                setStatus(
                    if (pendingNavigation != null) {
                        getString(R.string.page_preparing)
                    } else if (durableAccess) {
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
            onError = ::documentFailed,
            onPageCount = { expectedPages = it }
        )
        viewer.setRenderingEnabled(false)
        viewer.onPositionChanged = {
            updatePageIndicator()
            scheduleSave()
        }
        viewer.onUserInteraction = {
            dismissReadingHint()
            toolsPresentation.scrubber.cancelPreview()
            pendingNavigation = null
            updatePageIndicator()
        }
        viewer.onReadPageText = { page -> showPageText(page) }
        viewer.setOnClickListener {
            if (reading) {
                if (readerTools.visibility == View.VISIBLE) hideTools() else showTools()
            }
        }
        retryButton = Button(this).apply {
            setText(R.string.retry_document)
            visibility = View.GONE
            setOnClickListener {
                val failed = failedOpen ?: return@setOnClickListener
                if (failed.synthetic) {
                    openSample(failed.viewport)
                } else {
                    failed.uri?.let {
                        openDocument(Uri.parse(it), failed.viewport, relink = failed.relink)
                    }
                }
            }
        }
        reselectButton = Button(this).apply {
            setText(R.string.select_again)
            visibility = View.GONE
            setOnClickListener {
                val failed = failedOpen ?: return@setOnClickListener
                reselectRecord = failed.record?.let {
                    it.copy(uri = failed.relink ?: it.uri)
                }
                pickDocument()
            }
        }
        homePresentation = ReaderHomeView(
            this,
            homeStatus,
            retryButton,
            reselectButton,
            onSettings = ::showSettings,
            onPick = {
                reselectRecord = null
                pickDocument()
            },
            onSample = { openSample() },
            onOpen = { openDocument(Uri.parse(it.uri)) },
            onRemove = ::confirmRemoveRecord
        )
        homeScreen = homePresentation.view
        toolsPresentation = ReaderToolsView(
            this,
            status,
            onHome = ::showLibrary,
            onSettings = ::showSettings,
            onPage = ::showPageJump,
            onBookmarks = ::showBookmarks,
            onReadText = { showPageText() },
            onDismiss = ::hideTools,
            onScrubBegin = viewer::stopScrolling,
            onScrubPage = { requestPage(it, keepTools = true) },
            onPanLock = ::toggleHorizontalPanLock
        )
        readerTools = toolsPresentation.view
        pageJumpButton = toolsPresentation.page
        bookmarksButton = toolsPresentation.bookmarks
        readPageButton = toolsPresentation.readText
        pageIndicator = PageNumberView(this).apply {
            id = R.id.page_indicator
            visibility = View.GONE
        }
        readingHintLabel = ReaderStyle(this).label(R.string.first_reading_hint, 14f).apply {
            id = R.id.reading_hint
            val style = ReaderStyle(this@MainActivity)
            setPadding(style.dp(16), style.dp(12), style.dp(16), style.dp(12))
            background = style.shape(style.surface, 12)
            visibility = View.GONE
            isClickable = false
            isFocusable = false
        }
        pageIndicator.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            positionReadingHint()
        }
        homeScroll = ScrollView(this).apply { addView(homeScreen) }
        root = FrameLayout(this).apply {
            setBackgroundColor(themeBackground())
            addView(viewer, FrameLayout.LayoutParams(-1, -1))
            addView(readingHintLabel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            addView(homeScroll, FrameLayout.LayoutParams(-1, -1))
            addView(
                pageIndicator,
                FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            )
            addView(readerTools, FrameLayout.LayoutParams(-1, -1))
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
                        readerTools.setPadding(margins[0], margins[1], margins[2], margins[3])
                    }
                }
                if (Build.VERSION.SDK_INT >= 30) {
                    val safe = insets.getInsets(
                        WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                    )
                    homeScreen.setPadding(safe.left, safe.top, safe.right, safe.bottom)
                    readerTools.setPadding(safe.left, safe.top, safe.right, safe.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    homeScreen.setPadding(
                        insets.systemWindowInsetLeft,
                        insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight,
                        insets.systemWindowInsetBottom
                    )
                }
                val numberLayout = pageIndicator.layoutParams as FrameLayout.LayoutParams
                val numberGap = ReaderStyle(this@MainActivity).dp(12)
                if (Build.VERSION.SDK_INT >= 30) {
                    val numberSafe = insets.getInsetsIgnoringVisibility(
                        WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                    )
                    numberLayout.setMargins(
                        numberSafe.left + numberGap,
                        0,
                        numberSafe.right + numberGap,
                        numberSafe.bottom + numberGap
                    )
                } else {
                    @Suppress("DEPRECATION")
                    numberLayout.setMargins(
                        insets.stableInsetLeft + numberGap,
                        0,
                        insets.stableInsetRight + numberGap,
                        insets.stableInsetBottom + numberGap
                    )
                }
                pageIndicator.layoutParams = numberLayout
                positionReadingHint()
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
        settings.loadDisplay { result ->
            if (isDestroyed || isFinishing) return@loadDisplay
            result.onSuccess(::applyDisplaySettings)
                .onFailure { reportError(R.string.settings_error) }
        }
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
                bindPageNumberSetting?.invoke(it)
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
        if (savedInstanceState?.getBoolean("settingsOpen") == true) {
            showSettings()
            if (savedInstanceState.getBoolean("orientationSettingsOpen")) {
                showOrientationSettings()
            } else {
                val draft = savedInstanceState.getBundle("settingsDraft")
                when (savedInstanceState.getString("settingsForm")) {
                    SettingsFormKind.BACKGROUND.name -> showBackgroundSettings(draft)
                    SettingsFormKind.DISPLAY.name -> showDisplaySettings(draft)
                    SettingsFormKind.LAYOUT.name -> pendingLayoutDraft = draft ?: Bundle()
                }
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
        if (requestCode == OPEN_PDF && resultCode != RESULT_OK) reselectRecord = null
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
        closePageText()
        saveReading()
        clearFailedOpen()
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
            ReadingRecord(
                uri.toString(),
                previous?.title ?: fallbackTitle,
                System.currentTimeMillis(),
                previous?.pageCount ?: 0,
                previous?.position,
                previous?.pageLayout ?: PageLayoutSettings()
            )
        }) { result ->
            if (isDestroyed || generation != opening) return@execute
            result.onSuccess { record ->
                activeRecord = record
                toolsPresentation.setTitle(record.title)
                viewer.setPageLayoutSettings(record.pageLayout)
                if (viewport !=
                    null
                ) {
                    viewer.restoreViewport(viewport)
                } else {
                    viewer.restorePosition(record.position)
                }
                worker.openCancelable(viewer.hasRestoredViewport()) { request ->
                    request.throwIfCanceled()
                    if (uri.scheme == "content" &&
                        grantFlags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0
                    ) {
                        try {
                            resolver.takePersistableUriPermission(
                                uri,
                                grantFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (_: SecurityException) {
                            // Some providers offer temporary access; report that after opening.
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
                                null,
                                request.signal
                            )?.use {
                                if (it.moveToFirst()) {
                                    it.getString(0)?.takeIf(String::isNotBlank)
                                } else {
                                    null
                                }
                            }
                        } else {
                            uri.lastPathSegment
                        }
                    } catch (error: OperationCanceledException) {
                        throw error
                    } catch (_: Exception) {
                        // Optional metadata never determines whether the PDF itself is readable.
                        null
                    }
                    request.throwIfCanceled()
                    main.post {
                        if (!isDestroyed && generation == opening) {
                            durableAccess = durable
                            if (title != null) {
                                activeRecord = activeRecord?.copy(title = title)
                                toolsPresentation.setTitle(title)
                            }
                        }
                    }
                    val descriptor = resolver.openFileDescriptor(uri, "r", request.signal)
                        ?: throw PdfSourceException(PdfFailure.UNAVAILABLE)
                    PdfDocumentSource(cacheDir).prepare(descriptor, request)
                }
            }.onFailure {
                showLibrary()
                reportError(R.string.library_error)
            }
        }
    }

    private fun openSample(viewport: Bundle? = null) {
        closePageText()
        saveReading()
        clearFailedOpen()
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
        viewer.setPageLayoutSettings(PageLayoutSettings())
        viewer.restoreViewport(viewport)
        showReader()
        setStatus(R.string.loading)
        worker.openForRestore(viewer.hasRestoredViewport()) { createSample() }
    }

    private fun showReader() {
        reading = true
        updateDisplayWindow()
        root.setBackgroundColor(viewer.outsideBackgroundColor())
        homeScreen.visibility = View.GONE
        homeScroll.visibility = View.GONE
        viewer.visibility = View.VISIBLE
        toolsPresentation.setTitle(
            activeRecord?.title
                ?: getString(if (synthetic) R.string.sample_pdf else R.string.untitled_pdf)
        )
        hideTools()
        showTools()
        updatePageIndicator()
    }

    private fun showLibrary() {
        dismissReadingHint()
        closeSettings()
        closePageText()
        saveReading()
        clearFailedOpen()
        pendingNavigation = null
        cancelSave()
        opening++
        loaded = false
        sourceUri = null
        activeRecord = null
        synthetic = false
        reading = false
        updateDisplayWindow()
        root.setBackgroundColor(themeBackground())
        worker.closeDocument()
        viewer.reset()
        viewer.restoreViewport(null)
        viewer.visibility = View.GONE
        homeScreen.visibility = View.VISIBLE
        homeScroll.visibility = View.VISIBLE
        hideTools()
        readingWindow.apply(false)
        setStatus(R.string.ready)
        updatePageIndicator()
        refreshLibrary()
    }

    private fun clearFailedOpen() {
        failedOpen = null
        retryButton.visibility = View.GONE
        reselectButton.visibility = View.GONE
    }

    private fun documentFailed(failure: PdfFailure) {
        val failed = FailedOpen(
            sourceUri,
            viewer.saveViewport(),
            activeRecord,
            replacedUri,
            synthetic
        )
        loaded = false
        pendingNavigation = null
        navigationDialog?.dismiss()
        settingsDialog?.dismiss()
        showLibrary()
        failedOpen = failed
        setStatus(
            when (failure) {
                PdfFailure.ACCESS -> R.string.pdf_access_error
                PdfFailure.UNAVAILABLE -> R.string.pdf_unavailable_error
                PdfFailure.EMPTY -> R.string.pdf_empty_error
                PdfFailure.INVALID -> R.string.pdf_invalid_error
                PdfFailure.PASSWORD -> R.string.pdf_password_error
                PdfFailure.COPY_LIMIT -> R.string.pdf_copy_limit_error
                PdfFailure.STORAGE -> R.string.pdf_storage_error
                PdfFailure.TIMEOUT -> R.string.pdf_timeout_error
                PdfFailure.READ -> R.string.pdf_read_error
                PdfFailure.RENDER -> R.string.pdf_render_error
            }
        )
        retryButton.visibility = View.VISIBLE
        reselectButton.visibility = if (failed.uri != null) View.VISIBLE else View.GONE
    }

    private fun refreshLibrary() {
        library.execute({ it.list() }) { result ->
            if (isDestroyed || viewer.visibility == View.VISIBLE) return@execute
            result.onSuccess(homePresentation::render)
                .onFailure { reportError(R.string.library_error) }
        }
    }

    private fun confirmRemoveRecord(record: ReadingRecord) {
        AlertDialog.Builder(this)
            .setMessage(R.string.remove_record_message)
            .setPositiveButton(R.string.remove_record) { _, _ ->
                library.execute({ it.remove(record.uri) }) { removed ->
                    if (!isDestroyed) {
                        removed.onSuccess { refreshLibrary() }
                            .onFailure { reportError(R.string.save_error) }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
        // Lifecycle saves must not enqueue the old layout behind an in-flight settings write.
        if (!loaded || pendingLayoutWrites > 0) return
        val record = activeRecord ?: return
        val position = viewer.readingPosition() ?: return
        val updated = record.copy(position = position, pageLayout = viewer.pageLayoutSettings())
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
        homeStatus.visibility = if (message == getString(R.string.ready)) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

    private fun setStatus(resource: Int) = setStatus(getString(resource))

    private fun reportError(resource: Int) {
        setStatus(resource)
        Toast.makeText(this, resource, Toast.LENGTH_LONG).show()
    }

    private fun hideTools() {
        toolsPresentation.scrubber.cancelPreview()
        readerTools.visibility = View.GONE
        viewer.dismissToolsOnOutsideTap = false
        readingWindow.apply(reading)
        updateReadingHint()
    }

    private fun showTools() {
        // Recreating a reader also shows tools programmatically; it is not a user's dismissal.
        if (::readingHintLabel.isInitialized) readingHintLabel.visibility = View.GONE
        readerTools.visibility = View.VISIBLE
        viewer.dismissToolsOnOutsideTap = true
    }

    private fun updateReadingHint() {
        if (!::readingHintLabel.isInitialized) return
        readingHintLabel.visibility = if (reading &&
            loaded &&
            hintState.visible &&
            readerTools.visibility != View.VISIBLE
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
        positionReadingHint()
    }

    private fun positionReadingHint() {
        if (!::readingHintLabel.isInitialized) return
        val params = readingHintLabel.layoutParams as? FrameLayout.LayoutParams ?: return
        val density = resources.displayMetrics.density
        val gap = (24 * density).toInt()
        val number = pageIndicator.layoutParams as? FrameLayout.LayoutParams
        val bottom = if (pageIndicator.visibility == View.VISIBLE && number != null) {
            maxOf(
                readerTools.paddingBottom + gap,
                number.bottomMargin + pageIndicator.height + (12 * density).toInt()
            )
        } else {
            readerTools.paddingBottom + gap
        }
        val left = readerTools.paddingLeft + gap
        val right = readerTools.paddingRight + gap
        if (params.leftMargin != left ||
            params.rightMargin != right ||
            params.bottomMargin != bottom
        ) {
            params.setMargins(left, 0, right, bottom)
            readingHintLabel.layoutParams = params
        }
    }

    private fun dismissReadingHint() {
        hintState.dismiss()
        if (::readingHintLabel.isInitialized) readingHintLabel.visibility = View.GONE
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
        positionReadingHint()
        toolsPresentation.updatePages(
            position?.page,
            expectedPages,
            loaded && pendingNavigation == null
        )
        pageJumpButton.isEnabled = loaded &&
            position != null &&
            expectedPages > 1 &&
            pendingNavigation == null
        bookmarksButton.isEnabled = loaded && activeRecord != null && position != null
        if (::readPageButton.isInitialized) readPageButton.isEnabled = loaded && position != null
        toolsPresentation.updatePanLock(
            viewer.pageLayoutSettings().horizontalPanLocked,
            loaded && reading && pendingLayoutWrites == 0
        )
        documentLayoutSetting?.isEnabled = loaded && reading && pendingLayoutWrites == 0
    }

    private fun showPageText(selectedPage: Int? = null) {
        if (!resumed || !reading || !loaded || pageTextDialog != null) return
        val page = selectedPage ?: viewer.pageForTextReading() ?: return
        if (page !in 0 until expectedPages) return
        val generation = opening
        val content = ReaderStyle(this).label(R.string.page_text_loading).apply {
            id = R.id.page_text_content
            setText(R.string.page_text_loading)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            isFocusable = true
        }
        val dialog = readerFormDialog(
            R.string.read_page_text,
            readerFormBody(content),
            negative = R.string.close_settings,
            neutral = R.string.choose_page_text,
            onNeutral = {
                closePageText()
                val picker = pageJumpDialog(page, expectedPages) { showPageText(it) }
                picker.setTitle(R.string.choose_page_text)
                trackNavigationDialog(picker)
            }
        )
        dialog.setTitle(getString(R.string.page_text_title, page + 1))
        pageTextDialog = dialog
        dialog.setOnDismissListener {
            if (pageTextDialog === dialog) {
                pageTextDialog = null
                pageTextClient.cancel()
                hideTools()
            }
            content.text = ""
        }
        dialog.show()
        pageTextClient.read(page, worker::duplicateSource) { error, text ->
            if (isDestroyed || generation != opening || pageTextDialog !== dialog) return@read
            content.text = when {
                error != 0 -> getString(error)
                text.isNullOrBlank() -> getString(R.string.page_text_empty)
                else -> text
            }
        }
    }

    private fun closePageText() {
        val previous = pageTextDialog
        pageTextDialog = null
        pageTextClient.cancel()
        previous?.setOnDismissListener(null)
        previous?.findViewById<TextView>(R.id.page_text_content)?.text = ""
        previous?.dismiss()
    }

    private fun showPageJump() {
        if (!loaded ||
            expectedPages <= 1 ||
            pendingNavigation != null ||
            navigationDialog != null
        ) {
            return
        }
        val current = viewer.readingPosition() ?: return
        trackNavigationDialog(pageJumpDialog(current.page, expectedPages) { requestPage(it) })
    }

    private fun requestPage(page: Int, y: Double? = null, keepTools: Boolean = false) {
        if (pendingNavigation != null) return
        if (keepTools && page == viewer.readingPosition()?.page) return
        if (page !in 0 until expectedPages) {
            reportError(R.string.page_unavailable)
            return
        }
        pendingNavigation = PageTarget(page, y)
        if (!keepTools) hideTools()
        applyPendingNavigation()
        updatePageIndicator()
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
        updatePageIndicator()
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
                val style = ReaderStyle(this)
                val list = ListView(this).apply {
                    addFooterView(
                        style.label(R.string.bookmark_delete_hint, 12f).apply {
                            setPadding(style.dp(24), style.dp(16), style.dp(24), style.dp(16))
                            setTextColor(style.secondary)
                        },
                        null,
                        false
                    )
                    adapter = readerListAdapter(labels)
                    divider = null
                }
                val body = if (entries.isEmpty()) {
                    readerFormBody(style.label(R.string.no_bookmarks))
                } else {
                    list
                }
                val dialog = readerFormDialog(
                    R.string.bookmarks,
                    body,
                    positive = R.string.add_bookmark,
                    negative = R.string.close_settings,
                    scrollContent = entries.isEmpty(),
                    onConfirm = { selectedDialog ->
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
                        selectedDialog.dismiss()
                    }
                )
                list.setOnItemClickListener { _, _, index, _ ->
                    val entry = entries.getOrNull(index) ?: return@setOnItemClickListener
                    dialog.dismiss()
                    requestPage(entry.page, entry.y)
                }
                trackNavigationDialog(dialog)
                list.setOnItemLongClickListener { _, _, index, _ ->
                    val entry = entries.getOrNull(index) ?: return@setOnItemLongClickListener false
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
        if (settingsDialog != null || isDestroyed || isFinishing) return
        val dialog = readerFormDialog(
            R.string.reader_settings_title,
            settingsControls(),
            negative = R.string.close_settings
        )
        settingsDialog = dialog
        settingsHomeDialog = dialog
        dialog.setOnDismissListener {
            if (settingsHomeDialog === dialog) {
                closeSettings()
                hideTools()
            }
        }
        dialog.window?.setWindowAnimations(0)
        dialog.show()
    }

    private fun showOrientationSettings() {
        val parent = beginSettingsChild() ?: return
        orientationSettingsOpen = true
        settings.load { result ->
            if (!isSettingsParent(parent)) return@load
            settingsChildLoading = false
            result.onSuccess { mode ->
                var confirmed = mode
                var saving = false
                val labels =
                    arrayOf(
                        getString(R.string.orientation_auto),
                        getString(R.string.orientation_portrait),
                        getString(R.string.orientation_landscape),
                        getString(R.string.orientation_reverse_portrait),
                        getString(R.string.orientation_reverse_landscape)
                    )
                val list = ListView(this).apply {
                    choiceMode = ListView.CHOICE_MODE_SINGLE
                    adapter = readerListAdapter(labels.toList(), singleChoice = true)
                    setItemChecked(mode.ordinal, true)
                    divider = null
                }
                val dialog = readerFormDialog(
                    R.string.orientation_settings,
                    list,
                    negative = R.string.close_settings,
                    scrollContent = false
                )
                list.setOnItemClickListener { _, _, index, _ ->
                    if (saving) return@setOnItemClickListener
                    val selected = OrientationMode.entries[index]
                    if (selected == confirmed) return@setOnItemClickListener
                    saving = true
                    list.isEnabled = false
                    settings.save(selected) { saved ->
                        if (isDestroyed || isFinishing) return@save
                        saving = false
                        list.isEnabled = true
                        saved.onSuccess {
                            confirmed = it
                            list.setItemChecked(it.ordinal, true)
                            saveReading()
                            // Keep this child open; recreation restores the same settings section.
                            applyOrientation(it)
                        }.onFailure {
                            list.setItemChecked(confirmed.ordinal, true)
                            reportError(R.string.settings_error)
                        }
                    }
                }
                showSettingsChild(parent, dialog) { orientationSettingsOpen = false }
            }.onFailure {
                orientationSettingsOpen = false
                reportError(R.string.settings_error)
            }
        }
    }

    private fun showBackgroundSettings(draft: Bundle? = null) {
        val parent = beginSettingsChild() ?: return
        settings.loadBackground { result ->
            if (!isSettingsParent(parent)) return@loadBackground
            settingsChildLoading = false
            result.onSuccess { current ->
                val form = backgroundSettingsDialog(current, draft) { selected, done ->
                    settings.saveBackground(selected) { saved ->
                        if (isDestroyed || isFinishing) return@saveBackground
                        done(saved.isSuccess)
                        saved.onSuccess(viewer::setBackgroundSettings)
                    }
                }
                showEditableSettingsChild(parent, SettingsFormKind.BACKGROUND, form)
            }.onFailure { reportError(R.string.settings_error) }
        }
    }

    private fun settingsControls(): LinearLayout {
        val style = ReaderStyle(this)
        val orientationButton = Button(this).apply {
            setText(R.string.orientation_settings)
            setOnClickListener {
                showOrientationSettings()
            }
        }
        val displayButton = Button(this).apply {
            setText(R.string.display_settings)
            setOnClickListener {
                showDisplaySettings()
            }
        }
        val layoutButton = Button(this).apply {
            setText(R.string.page_layout_settings)
            isEnabled = loaded && reading && pendingLayoutWrites == 0
            setOnClickListener {
                showPageLayoutSettings()
            }
        }
        documentLayoutSetting = layoutButton
        val backgroundButton = Button(this).apply {
            setText(R.string.background_settings)
            setOnClickListener {
                showBackgroundSettings()
            }
        }
        var binding = false
        val checkbox = Switch(this).apply {
            setText(R.string.show_page_numbers)
            isChecked = pageNumbersVisible
            style.styleChoice(this)
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
        // A restored settings Window can appear before its asynchronous preference load completes.
        bindPageNumberSetting = { checked ->
            if (checkbox.isEnabled) {
                binding = true
                checkbox.isChecked = checked
                binding = false
            }
        }
        listOf(orientationButton, displayButton, layoutButton, backgroundButton)
            .forEach(style::styleSettingButton)
        return readerFormBody(
            style.label(R.string.reading_environment, 14f).apply { setTextColor(style.secondary) },
            orientationButton,
            backgroundButton,
            checkbox,
            displayButton,
            style.label(R.string.current_document, 14f).apply { setTextColor(style.secondary) },
            layoutButton,
            style.label(R.string.document_settings_hint, 12f).apply {
                setTextColor(style.secondary)
            }
        )
    }

    private fun readerListAdapter(labels: List<String>, singleChoice: Boolean = false) =
        object : ArrayAdapter<String>(
            this,
            if (singleChoice) {
                android.R.layout.simple_list_item_single_choice
            } else {
                android.R.layout.simple_list_item_1
            },
            labels
        ) {
            override fun getView(
                position: Int,
                convertView: View?,
                parent: android.view.ViewGroup
            ): View = super.getView(position, convertView, parent).apply {
                val style = ReaderStyle(this@MainActivity)
                minimumHeight = style.dp(64)
                // Platform list rows have a fixed preferred height; padding otherwise clips glyphs.
                layoutParams.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                setPadding(style.dp(24), style.dp(16), style.dp(24), style.dp(16))
                (this as TextView).apply {
                    textSize = 16f
                    setTextColor(style.text)
                    isSingleLine = false
                    includeFontPadding = true
                }
            }
        }

    private fun beginSettingsChild(): AlertDialog? {
        val parent = settingsHomeDialog ?: return null
        if (settingsDialog !== parent || settingsChildLoading) return null
        if (!isSettingsParent(parent)) return null
        settingsChildLoading = true
        return parent
    }

    private fun showEditableSettingsChild(
        parent: AlertDialog,
        kind: SettingsFormKind,
        form: EditableSettingsForm
    ) {
        if (!isSettingsParent(parent)) return
        settingsFormKind = kind
        editableSettingsForm = form
        showSettingsChild(parent, form.dialog) {
            settingsFormKind = null
            editableSettingsForm = null
        }
    }

    private fun showPageLayoutSettings(draft: Bundle? = null) {
        if (!loaded || !reading || pendingLayoutWrites > 0) return
        val parent = beginSettingsChild() ?: return
        val generation = opening
        val form = pageLayoutDialog(viewer.pageLayoutSettings(), draft) { selected, done ->
            savePageLayoutSettings(selected, generation, done)
        }
        showEditableSettingsChild(parent, SettingsFormKind.LAYOUT, form)
    }

    private fun toggleHorizontalPanLock() {
        if (!loaded || !reading || pendingLayoutWrites > 0) return
        viewer.stopScrolling()
        val selected = viewer.pageLayoutSettings().let {
            it.copy(horizontalPanLocked = !it.horizontalPanLocked)
        }
        savePageLayoutSettings(selected, opening) { success ->
            if (!success) reportError(R.string.settings_error)
        }
    }

    private fun savePageLayoutSettings(
        selected: PageLayoutSettings,
        generation: Int,
        done: (Boolean) -> Unit
    ) {
        val record = activeRecord
        if (generation != opening || !loaded || !reading || pendingLayoutWrites > 0) {
            done(false)
        } else if (record == null) {
            // The synthetic demo has no library record; its layout is intentionally session-only.
            viewer.setPageLayoutSettings(selected)
            updatePageIndicator()
            done(true)
        } else {
            cancelSave()
            val updated = record.copy(position = viewer.readingPosition(), pageLayout = selected)
            pendingLayoutWrites++
            updatePageIndicator()
            library.execute({ it.put(updated) }) { saved ->
                pendingLayoutWrites--
                if (isDestroyed || isFinishing) return@execute
                if (generation != opening) {
                    updatePageIndicator()
                    return@execute
                }
                saved.onSuccess {
                    activeRecord = updated
                    viewer.setPageLayoutSettings(selected)
                }
                updatePageIndicator()
                done(saved.isSuccess)
            }
        }
    }

    private fun isSettingsParent(parent: AlertDialog): Boolean =
        !isDestroyed && !isFinishing && settingsHomeDialog === parent && parent.isShowing

    private fun showSettingsChild(
        parent: AlertDialog,
        dialog: AlertDialog,
        onClosed: () -> Unit = {}
    ) {
        if (!isSettingsParent(parent)) return
        settingsChildLoading = false
        settingsDialog = dialog
        // Keep the opaque parent Window attached during asynchronous loading and child transitions.
        dialog.setOnDismissListener {
            if (settingsDialog === dialog) {
                settingsDialog = settingsHomeDialog
                onClosed()
            }
        }
        dialog.window?.setWindowAnimations(0)
        dialog.show()
    }

    private fun closeSettings() {
        val child = settingsDialog
        val parent = settingsHomeDialog
        settingsDialog = null
        settingsHomeDialog = null
        settingsChildLoading = false
        orientationSettingsOpen = false
        settingsFormKind = null
        editableSettingsForm = null
        pendingLayoutDraft = null
        documentLayoutSetting = null
        bindPageNumberSetting = null
        child?.setOnDismissListener(null)
        child?.dismiss()
        if (parent !== child) {
            parent?.setOnDismissListener(null)
            parent?.dismiss()
        }
    }

    private fun applyOrientation(mode: OrientationMode) {
        val requested = when (mode) {
            OrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            OrientationMode.REVERSE_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            OrientationMode.REVERSE_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        }
        if (requestedOrientation != requested) requestedOrientation = requested
    }

    private fun showDisplaySettings(draft: Bundle? = null) {
        val parent = beginSettingsChild() ?: return
        settings.loadDisplay { result ->
            if (!isSettingsParent(parent)) return@loadDisplay
            settingsChildLoading = false
            result.onSuccess { current ->
                val form = displaySettingsDialog(current, draft) { selected, done ->
                    settings.saveDisplay(selected) { saved ->
                        if (isDestroyed || isFinishing) return@saveDisplay
                        done(saved.isSuccess)
                        saved.onSuccess(::applyDisplaySettings)
                    }
                }
                showEditableSettingsChild(parent, SettingsFormKind.DISPLAY, form)
            }.onFailure { reportError(R.string.settings_error) }
        }
    }

    private fun systemDarkTheme(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    private fun themeBackground(): Int =
        obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground)).let {
            try {
                it.getColor(0, Color.BLACK)
            } finally {
                it.recycle()
            }
        }

    private fun applyDisplaySettings(value: DisplaySettings) {
        displaySettings = value
        updateDisplayWindow()
        if (value.theme.isDark(systemDarkTheme()) != appliedDarkTheme) {
            // The existing saved viewport path preserves zoom/anchor across themed View inflation.
            saveReading()
            recreate()
        }
    }

    private fun updateDisplayWindow() {
        displayWindow.apply(displaySettings, reading && loaded, resumed)
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
            settingsDialog != null -> settingsDialog?.dismiss()
            reading && toolsPresentation.scrubber.cancelPreview() -> Unit
            reading && readerTools.visibility == View.VISIBLE -> hideTools()
            reading -> showLibrary()
            else -> finish()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN &&
            ::readingHintLabel.isInitialized &&
            readingHintLabel.isShown
        ) {
            dismissReadingHint()
        }
        // A second pointer may be routed to a sibling by ViewGroup's split touch dispatch.
        if (event.pointerCount > 1 && ::toolsPresentation.isInitialized) {
            toolsPresentation.scrubber.cancelPreview()
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && ::toolsPresentation.isInitialized) {
            toolsPresentation.scrubber.cancelPreview()
        }
        if (hasFocus && ::readingWindow.isInitialized) readingWindow.apply(reading)
    }

    override fun onStart() {
        super.onStart()
        viewer.setRenderingEnabled(true)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        updateDisplayWindow()
    }

    override fun onPause() {
        toolsPresentation.scrubber.cancelPreview()
        closePageText()
        resumed = false
        updateDisplayWindow()
        saveReading()
        cancelSave()
        super.onPause()
    }

    override fun onStop() {
        viewer.setRenderingEnabled(false)
        saveReading()
        cancelSave()
        super.onStop()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        viewer.trimCache()
    }

    override fun onDestroy() {
        closePageText()
        resumed = false
        updateDisplayWindow()
        navigationDialog?.dismiss()
        navigationDialog = null
        closeSettings()
        cancelSave()
        opening++
        viewer.setRenderingEnabled(false)
        viewer.reset()
        worker.close()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        toolsPresentation.scrubber.cancelPreview()
        outState.putString("displayTheme", displaySettings.theme.name)
        // Applying a theme or orientation can recreate the Activity; return to settings, not the PDF.
        outState.putBoolean("settingsOpen", settingsHomeDialog?.isShowing == true)
        outState.putBoolean("orientationSettingsOpen", orientationSettingsOpen)
        val form = editableSettingsForm
        if (form?.dialog?.isShowing == true) {
            outState.putString("settingsForm", settingsFormKind?.name)
            outState.putBundle("settingsDraft", form.capture())
        } else if (pendingLayoutDraft != null) {
            outState.putString("settingsForm", SettingsFormKind.LAYOUT.name)
            outState.putBundle("settingsDraft", pendingLayoutDraft)
        }
        saveReading()
        pendingNavigation?.let {
            outState.putInt("pendingPage", it.page)
            it.y?.let { fraction -> outState.putDouble("pendingPageY", fraction) }
        }
        outState.putString("sourceUri", sourceUri)
        outState.putBoolean("synthetic", synthetic)
        outState.putString("reselectUri", reselectRecord?.uri)
        outState.putString("replacedUri", replacedUri)
        val viewport = viewer.saveViewport()?.let { saved ->
            if (pendingLayoutWrites > 0) {
                // The repository result decides the layout after recreation; retain only the anchor.
                Bundle(saved).apply {
                    remove("pageSizeMode")
                    remove("pageGap")
                    remove("horizontalPanLocked")
                }
            } else {
                saved
            }
        }
        outState.putBundle("viewport", viewport)
        super.onSaveInstanceState(outState)
    }

    companion object {
        private const val OPEN_PDF = 1
    }
}
