package dev.example.ebookviewer

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.os.Parcelable
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView

/** Cells are recycled; a large catalog never creates every View or opens every PDF at once. */
internal class BookshelfLibraryView(
    private val context: Context,
    status: TextView,
    retry: Button,
    reselect: Button,
    private val onSettings: () -> Unit,
    @Suppress("unused") private val onPick: () -> Unit,
    private val onSample: () -> Unit,
    private val onOpen: (ReadingRecord) -> Unit,
    @Suppress("unused") private val onRemove: (ReadingRecord) -> Unit,
    private val onRemoveMany: (Set<String>) -> Unit,
    private val onMove: (Set<String>) -> Unit,
    private val onReorder: (String, Int, String?) -> Unit,
    private val onShelves: () -> Unit,
    private val onExit: () -> Unit = {},
    private val onRemoveFromShelf: (Set<String>, String) -> Unit = { _, _ -> },
    private val onShelfAction: (Bookshelf) -> Unit = {},
    private val onShelfReorder: (String, Int) -> Unit = { _, _ -> },
    private val mainHeader: View? = null,
    private val onEditShelves: () -> Unit = onShelves,
    private val preferences: LibraryPreferencesRepository =
        (context.applicationContext as ReaderApplication).libraryPreferences
) {
    private val style = ReaderStyle(context)
    private val chrome = LibraryControls(context)
    private var options = LibraryPreferences()
    private var preferencesReady = false
    private var savingPreferences = false
    private var preferenceDraft: LibraryPreferences? = null
    private var closed = false
    private var preferenceDialog: AlertDialog? = null
    private var pendingPreferenceError = false
    private var catalog = LibraryCatalog()
    private var home = true
    private var managing = false
    private var ordering = false
    private var active = false
    private var scrollState: Parcelable? = null
    private var homeScrollState: Parcelable? = null
    private var shelfColumns = 2
    private val selected = linkedSetOf<String>()
    var shelfFilter: String? = null
        private set
    private val header = column()
    private val toolbar = column()
    private val selection = column().apply { id = R.id.library_selection }
    private val footer = object : LinearLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
            (getChildAt(0) as? Button)?.maxWidth =
                (available - style.dp(64 + 16)).coerceAtLeast(style.dp(48))
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }.apply {
        id = R.id.library_document_actions
        gravity = Gravity.CENTER
        clipChildren = false
        clipToPadding = false
    }
    private val grid = GridView(context).apply {
        id = R.id.library_grid
        horizontalSpacing = style.dp(16)
        verticalSpacing = style.dp(16)
        stretchMode = GridView.STRETCH_COLUMN_WIDTH
        clipToPadding = false
        setPadding(style.dp(16), style.dp(8), style.dp(16), style.dp(16))
    }
    private var entries = emptyList<Entry>()
    private val adapter = CatalogAdapter()
    private val shelfRows = ShelfRowsAdapter()
    private val mainList = if (mainHeader == null) {
        null
    } else {
        object : ListView(context) {
            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                val minimum = style.dp(
                    if (context.resources.configuration.fontScale >=
                        1.5f
                    ) {
                        220
                    } else {
                        144
                    }
                )
                val columns = ((w - style.dp(48)) / minimum).coerceIn(1, 4)
                if (columns != shelfColumns) {
                    shelfColumns = columns
                    shelfRows.notifyDataSetChanged()
                }
            }
        }.apply {
            id = R.id.main_bookshelf_list
            divider = null
            dividerHeight = 0
            itemsCanFocus = true
        }
    }
    private val feedback = column().apply {
        addView(status)
        addView(retry)
        addView(reselect)
    }
    private val controls = column().apply {
        addView(feedback)
        addView(toolbar)
        addView(selection)
    }
    private val controlsScroll = object : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val limit = style.dp(context.resources.configuration.screenHeightDp / 3)
            super.onMeasure(
                widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST)
            )
        }
    }.apply { addView(controls) }
    private val mainHeading = if (mainHeader == null) {
        null
    } else {
        column().apply {
            addView(mainHeader)
            addView(
                style.label(R.string.bookshelves, 18f, true).apply {
                    setPadding(style.dp(24), style.dp(24), style.dp(24), style.dp(8))
                }
            )
        }
    }
    private val covers = BookCoverLoader(context) {
        grid.invalidateViews()
        mainList?.invalidateViews()
    }
    val view = column().apply {
        id = R.id.home_screen
        setBackgroundColor(style.surface)
        addView(header)
        addView(controlsScroll)
        mainList?.let { addView(it, LayoutParams(-1, 0, 1f)) }
        addView(grid, LayoutParams(-1, 0, 1f))
        addView(footer)
    }

    init {
        mainList?.apply {
            addHeaderView(mainHeading, null, false)
            adapter = shelfRows
            setOnScrollListener(object : AbsListView.OnScrollListener {
                override fun onScrollStateChanged(view: AbsListView?, state: Int) = Unit
                override fun onScroll(view: AbsListView?, first: Int, count: Int, total: Int) {
                    requestHomeCovers(first, count)
                }
            })
        }
        style.styleButton(retry)
        style.styleButton(reselect)
        status.textSize = 14f
        status.setTextColor(style.secondary)
        grid.adapter = adapter
        grid.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, state: Int) = Unit
            override fun onScroll(view: AbsListView?, first: Int, count: Int, total: Int) {
                requestCovers(first, count)
            }
        })
        preferences.load { result ->
            if (closed) return@load
            preferencesReady = result.isSuccess
            result.onSuccess {
                options = it
                if (preferenceDraft == it) {
                    preferenceDraft = null
                } else if (preferenceDraft != null) {
                    preferenceError()
                }
            }.onFailure { preferenceError() }
            render(catalog)
        }
        render(catalog)
    }

    fun setActive(value: Boolean) {
        active = value && !closed
        if (active && pendingPreferenceError) preferenceError()
        if (active) requestVisibleCovers() else covers.cancel()
    }

    fun close() {
        closed = true
        preferenceDialog?.dismiss()
        preferenceDialog = null
        covers.close()
    }

    fun finishManaging(): Boolean = when {
        managing || ordering -> {
            managing = false
            ordering = false
            selected.clear()
            render(catalog)
            true
        }
        !home -> {
            home = true
            shelfFilter = null
            scrollState = homeScrollState
            render(catalog)
            true
        }
        else -> false
    }

    fun saveState(): Bundle = Bundle().apply {
        putBoolean("home", home)
        putBoolean("managing", managing)
        putBoolean("ordering", ordering)
        putString("shelf", shelfFilter)
        putStringArrayList("selected", ArrayList(selected))
        putParcelable(
            "scroll",
            if (home &&
                mainList != null
            ) {
                mainList.onSaveInstanceState()
            } else {
                grid.onSaveInstanceState()
            }
        )
        putParcelable("homeScroll", homeScrollState)
        preferenceDraft?.let {
            putString("draftView", it.view.name)
            putString("draftDocuments", it.documents.name)
            putString("draftShelves", it.shelves.name)
        }
    }

    @Suppress("DEPRECATION")
    fun restoreState(state: Bundle?) {
        if (state == null) return
        home = state.getBoolean("home", true)
        managing = state.getBoolean("managing")
        ordering = state.getBoolean("ordering")
        shelfFilter = state.getString("shelf")
        selected.addAll(state.getStringArrayList("selected").orEmpty())
        scrollState = state.getParcelable("scroll")
        homeScrollState = state.getParcelable("homeScroll")
        state.getString("draftView")?.let {
            preferenceDraft = LibraryPreferences(
                LibraryViewMode.valueOf(it),
                DocumentSort.valueOf(requireNotNull(state.getString("draftDocuments"))),
                BookshelfSort.valueOf(requireNotNull(state.getString("draftShelves")))
            )
        }
    }

    fun render(value: LibraryCatalog) {
        catalog = value
        if (shelfFilter?.isNotEmpty() == true && value.shelves.none { it.id == shelfFilter }) {
            home = true
            shelfFilter = null
        }
        val visible = value.sortedRecords(shelfFilter, options.documents)
        selected.retainAll(visible.map { it.uri }.toSet())
        entries =
            if (home) {
                val uncategorized = if (value.visible("").isNotEmpty()) {
                    listOf(Entry(uncategorized = true))
                } else {
                    emptyList()
                }
                uncategorized + value.sortedShelves(options.shelves).map {
                    Entry(shelf = it)
                }
            } else {
                visible.map { Entry(record = it) }
            }
        header.removeAllViews()
        toolbar.removeAllViews()
        selection.removeAllViews()
        header.setPadding(style.dp(16), style.dp(8), style.dp(16), 0)
        toolbar.setPadding(style.dp(16), 0, style.dp(16), 0)
        selection.setPadding(style.dp(16), 0, style.dp(16), 0)
        val title = when {
            managing -> context.getString(R.string.documents_selected, selected.size)
            home -> context.getString(R.string.my_library)
            shelfFilter == null -> context.getString(R.string.all_documents)
            shelfFilter == "" -> context.getString(R.string.uncategorized)
            else -> value.shelves.firstOrNull { it.id == shelfFilter }?.name.orEmpty()
        }
        header.addView(
            row().apply {
                addView(
                    chrome.icon(R.string.library_back, LibraryIcon.BACK) {
                        if (home) onExit() else finishManaging()
                    }
                )
                addView(
                    label(title, 22f, true).apply {
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                    },
                    LayoutParams(0, -2, 1f)
                )
                addView(
                    chrome.icon(
                        if (home) R.string.settings else R.string.library_more,
                        if (home) LibraryIcon.SETTINGS else LibraryIcon.MORE
                    ) {
                        if (home) {
                            onSettings()
                        } else {
                            more(
                                header.getChildAt(0).let {
                                    it as ViewGroup
                                }.getChildAt(2)
                            )
                        }
                    }
                )
            }
        )
        if (home) {
            val compact = context.resources.configuration.let {
                it.fontScale >= 1.5f || it.screenWidthDp < 360
            }
            val tools = row().apply {
                addView(label(context.getString(R.string.library_item_count, entries.size), 12f))
                addView(
                    action(R.string.library_edit) {
                        onEditShelves()
                    }.apply { id = R.id.bookshelf_edit },
                    LayoutParams(-2, -2).apply { leftMargin = style.dp(8) }
                )
                addView(View(context), LayoutParams(0, 1, 1f))
            }
            val navigation = if (compact) row() else tools
            navigation.addView(
                action(shelfSortLabel()) { sortShelves(it) },
                if (compact) LayoutParams(0, -2, 1f) else LayoutParams(-2, -2)
            )
            navigation.addView(
                action(R.string.all_documents) { enter(null) },
                (if (compact) LayoutParams(0, -2, 1f) else LayoutParams(-2, -2)).apply {
                    leftMargin = style.dp(8)
                }
            )
            toolbar.addView(tools)
            if (compact) toolbar.addView(navigation)
            if (entries.isEmpty()) {
                toolbar.addView(
                    label(context.getString(R.string.library_empty_shelves), 14f)
                )
            }
        } else {
            toolbar.addView(
                row().apply {
                    addView(
                        label(
                            context.getString(R.string.library_document_count, visible.size),
                            12f
                        ),
                        LayoutParams(0, -2, 1f)
                    )
                    addView(action(documentSortLabel()) { sortDocuments(it) })
                    addView(
                        action(
                            if (options.view ==
                                LibraryViewMode.GRID
                            ) {
                                R.string.library_list
                            } else {
                                R.string.library_grid
                            }
                        ) {
                            changePreferences(
                                options.copy(
                                    view = if (options.view ==
                                        LibraryViewMode.GRID
                                    ) {
                                        LibraryViewMode.LIST
                                    } else {
                                        LibraryViewMode.GRID
                                    }
                                )
                            )
                        }
                    )
                }
            )
            if (managing) {
                selection.addView(
                    CheckBox(context).apply {
                        text = context.getString(R.string.select_all_documents)
                        setTextColor(style.text)
                        minimumHeight = style.dp(48)
                        isChecked =
                            visible.isNotEmpty() &&
                            selected.containsAll(visible.map { it.uri })
                        setOnClickListener {
                            if (isChecked) {
                                selected.addAll(
                                    visible.map {
                                        it.uri
                                    }
                                )
                            } else {
                                selected.clear()
                            }
                            render(catalog)
                        }
                    }
                )
            }
            if (entries.isEmpty()) {
                toolbar.addView(
                    label(context.getString(R.string.no_documents), 14f)
                )
            }
        }
        selection.visibility = if (managing) View.VISIBLE else View.GONE
        if (mainList != null && mainHeading != null) {
            view.setBackgroundColor(if (home) style.background else style.surface)
            (controls.parent as ViewGroup).removeView(controls)
            if (home) {
                mainHeading.addView(controls)
            } else {
                controlsScroll.addView(controls)
            }
            mainList.visibility = if (home) View.VISIBLE else View.GONE
            grid.visibility = if (home) View.GONE else View.VISIBLE
            header.visibility = if (home) View.GONE else View.VISIBLE
            controlsScroll.visibility = if (home) View.GONE else View.VISIBLE
            shelfRows.notifyDataSetChanged()
        }
        updateColumns()
        adapter.notifyDataSetChanged()
        footer.removeAllViews()
        footer.visibility =
            if (!home && managing && selected.isNotEmpty()) View.VISIBLE else View.GONE
        footer.setPadding(style.dp(16), style.dp(8), style.dp(16), style.dp(16))
        if (shelfFilter != null) {
            footer.addView(
                action(R.string.move_to_bookshelf) { onMove(selected.toSet()) }.apply {
                    minimumHeight = style.dp(64)
                    minHeight = style.dp(64)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(style.dp(20), style.dp(12), style.dp(20), style.dp(12))
                    chrome.raised(this, 32)
                },
                LayoutParams(-2, -2).apply { rightMargin = style.dp(16) }
            )
        }
        footer.addView(
            chrome.icon(R.string.remove_selected, LibraryIcon.DELETE) {
                onRemoveMany(selected.toSet())
            }.apply {
                setImageDrawable(chrome.glyph(LibraryIcon.DELETE, chrome.destructive))
                chrome.raised(this, 32)
            },
            LayoutParams(style.dp(64), style.dp(64))
        )
        scrollState?.let {
            if (home &&
                mainList != null
            ) {
                mainList.onRestoreInstanceState(it)
            } else {
                grid.onRestoreInstanceState(it)
            }
        }
        scrollState = null
        grid.post { if (!closed) requestVisibleCovers() }
    }

    private fun enter(filter: String?) {
        homeScrollState = mainList?.onSaveInstanceState() ?: grid.onSaveInstanceState()
        shelfFilter = filter
        home = false
        ordering = false
        selected.clear()
        render(catalog)
        grid.setSelection(0)
    }

    private fun more(anchor: View) {
        PopupMenu(context, anchor).apply {
            menu.add(R.string.manage_documents).setOnMenuItemClickListener {
                managing = true
                render(catalog)
                true
            }
            menu.add(R.string.settings).setOnMenuItemClickListener {
                onSettings()
                true
            }
            if (managing) {
                menu.add(R.string.remove_selected).setOnMenuItemClickListener {
                    onRemoveMany(selected.toSet())
                    true
                }
            }
            if (!shelfFilter.isNullOrEmpty()) {
                menu.add(R.string.manage_bookshelves).setOnMenuItemClickListener {
                    catalog.shelves.firstOrNull { it.id == shelfFilter }?.let(onShelfAction)
                    true
                }
            }
            menu.add(R.string.library_refresh_covers).setOnMenuItemClickListener {
                covers.clear()
                true
            }
            menu.add(R.string.sample_pdf).setOnMenuItemClickListener {
                onSample()
                true
            }
            show()
        }
    }

    private fun sortDocuments(anchor: View) {
        PopupMenu(context, anchor).apply {
            listOf(
                R.string.library_recent,
                R.string.library_name,
                R.string.library_manual
            ).forEachIndexed {
                    index,
                    label
                ->
                menu.add(label).setOnMenuItemClickListener {
                    changePreferences(options.copy(documents = DocumentSort.entries[index]))
                    true
                }
            }
            show()
        }
    }

    private fun sortShelves(anchor: View) {
        PopupMenu(context, anchor).apply {
            listOf(R.string.library_custom, R.string.library_name).forEachIndexed { index, label ->
                menu.add(label).setOnMenuItemClickListener {
                    ordering = false
                    changePreferences(options.copy(shelves = BookshelfSort.entries[index]))
                    true
                }
            }
            show()
        }
    }

    private fun changePreferences(value: LibraryPreferences) {
        if (!preferencesReady || savingPreferences) {
            preferenceError()
            return
        }
        preferenceDraft = value
        savingPreferences = true
        preferences.save(value) { result ->
            if (closed) return@save
            savingPreferences = false
            result.onSuccess {
                options = it
                preferenceDraft = null
                render(catalog)
            }.onFailure { preferenceError() }
        }
    }

    private fun preferenceError() {
        if (closed) return
        pendingPreferenceError = true
        if (!active) return
        val activity = context as Activity
        if (activity.isDestroyed || activity.isFinishing) return
        pendingPreferenceError = false
        preferenceDialog?.dismiss()
        preferenceDialog = activity.readerFormDialog(
            R.string.library_preferences_error,
            activity.readerFormBody(
                label(context.getString(R.string.library_preferences_error), 16f)
            ),
            positive = R.string.retry_document,
            onConfirm = { dialog ->
                dialog.dismiss()
                if (preferencesReady) {
                    preferenceDraft?.let(::changePreferences)
                } else {
                    preferences.load { result ->
                        if (!closed) {
                            result.onSuccess {
                                options = it
                                preferencesReady = true
                                render(catalog)
                            }.onFailure { preferenceError() }
                        }
                    }
                }
            }
        ).also { it.show() }
    }

    private fun updateColumns() {
        val minimum = style.dp(if (context.resources.configuration.fontScale >= 1.5f) 220 else 150)
        grid.verticalSpacing =
            style.dp(if (!home && options.view == LibraryViewMode.LIST) 4 else 16)
        grid.columnWidth = minimum
        grid.numColumns =
            if (!home &&
                options.view == LibraryViewMode.LIST
            ) {
                1
            } else {
                GridView.AUTO_FIT
            }
    }

    private fun requestCovers(first: Int, count: Int) {
        if (!active || (home && mainList != null)) return
        covers.request(
            entries.drop(first.coerceAtLeast(0)).take(count.coerceAtMost(24)).flatMap { entry ->
                entryDocuments(entry).take(if (home) 3 else 1)
            }
        )
    }

    private fun requestVisibleCovers() {
        if (home && mainList != null) {
            requestHomeCovers(mainList.firstVisiblePosition, mainList.childCount)
        } else {
            requestCovers(grid.firstVisiblePosition, grid.childCount)
        }
    }

    private fun requestHomeCovers(first: Int, count: Int) {
        if (!active || !home || mainList == null) return
        val start = ((first - 1).coerceAtLeast(0) * shelfColumns).coerceAtMost(entries.size)
        val visible = entries.drop(start).take((count * shelfColumns).coerceAtMost(24))
        covers.request(visible.flatMap { entryDocuments(it).take(3) })
    }

    /** A single scrolling list keeps the original header and recycled rows of bookshelf cards together. */
    private inner class ShelfRowsAdapter : BaseAdapter() {
        override fun getCount() = if (home) (entries.size + shelfColumns - 1) / shelfColumns else 0
        override fun getItem(position: Int) = position
        override fun getItemId(position: Int) = position.toLong()
        override fun isEnabled(position: Int) = false
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val line = convertView as? LinearLayout ?: row()
            val previous = (0 until line.childCount).map { line.getChildAt(it) }
            line.removeAllViews()
            line.gravity = Gravity.TOP
            line.setPadding(style.dp(24), style.dp(8), style.dp(24), style.dp(16))
            repeat(shelfColumns) { column ->
                val index = position * shelfColumns + column
                val cell = if (index < entries.size) {
                    adapter.getView(index, previous.getOrNull(column), line)
                } else {
                    View(context)
                }
                line.addView(
                    cell,
                    LayoutParams(0, -2, 1f).apply {
                        if (column > 0) leftMargin = style.dp(16)
                    }
                )
            }
            return line
        }
    }

    private fun shelfSortLabel() = if (options.shelves ==
        BookshelfSort.CUSTOM
    ) {
        R.string.library_custom
    } else {
        R.string.library_name
    }
    private fun documentSortLabel() = when (options.documents) {
        DocumentSort.RECENT -> R.string.library_recent
        DocumentSort.NAME -> R.string.library_name
        DocumentSort.MANUAL -> R.string.library_manual
    }
    private fun metadata(record: ReadingRecord) =
        record.position?.takeIf { record.pageCount > 0 }?.let {
            context.getString(
                R.string.library_progress,
                (it.page + 1).coerceAtMost(record.pageCount),
                record.pageCount
            )
        } ?: context.getString(R.string.library_unread)
    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private fun label(value: String, size: Float, strong: Boolean = false) =
        style.label(R.string.untitled_pdf, size, strong).apply {
            text =
                value
        }
    private fun action(resource: Int, click: (View) -> Unit): Button {
        val icon = when (resource) {
            R.string.library_custom, R.string.library_name, R.string.library_recent,
            R.string.library_manual -> LibraryIcon.SORT
            R.string.all_documents -> LibraryIcon.DOCUMENT
            R.string.library_list -> LibraryIcon.LIST
            R.string.library_grid -> LibraryIcon.GRID
            R.string.library_edit -> LibraryIcon.EDIT
            R.string.move_to_bookshelf -> LibraryIcon.MOVE
            R.string.add_bookshelf -> LibraryIcon.ADD
            else -> LibraryIcon.LIST
        }
        return chrome.action(resource, icon) {}.apply { setOnClickListener(click) }
    }

    private fun entryDocuments(entry: Entry) = entry.record?.let(::listOf)
        ?: catalog.visible(if (entry.uncategorized) "" else entry.shelf?.id)

    private data class Entry(
        val record: ReadingRecord? = null,
        val shelf: Bookshelf? = null,
        val uncategorized: Boolean = false
    )
    private inner class Cell : LinearLayout(context) {
        val image = CoverMosaic()
        val title = label("", 16f, true).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        val detail = label("", 12f).apply { setTextColor(style.secondary) }
        val recent = label("", 12f).apply { setTextColor(style.secondary) }
        val more = chrome.icon(R.string.library_more, LibraryIcon.MORE) {}.apply {
            id = R.id.bookshelf_more
        }
        val titleRow = row().apply {
            addView(title, LayoutParams(0, -2, 1f))
            addView(more, LayoutParams(style.dp(48), style.dp(48)))
        }
        val caption = column().apply {
            addView(titleRow)
            addView(detail)
            addView(recent)
        }
        val buttons = row()
        init {
            orientation = VERTICAL
            addView(image)
            addView(caption)
            addView(buttons)
            setPadding(style.dp(2), style.dp(2), style.dp(2), style.dp(2))
            background = style.plainButtonBackground()
        }
    }

    private inner class CatalogAdapter : BaseAdapter() {
        override fun getCount() = entries.size
        override fun getItem(position: Int): Any = entries[position].let {
            it.record ?: it.shelf ?: context.getString(R.string.uncategorized)
        }
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val cell = convertView as? Cell ?: Cell()
            val entry = entries[position]
            val record = entry.record
            val shelf = entry.shelf
            val documents = entryDocuments(entry)
            val list = !home && options.view == LibraryViewMode.LIST
            cell.image.square = home
            cell.orientation = if (list) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            cell.image.layoutParams =
                LayoutParams(
                    if (list) style.dp(40) else -1,
                    if (home) {
                        -2
                    } else {
                        style.dp(
                            if (list) {
                                56
                            } else if (home) {
                                140
                            } else {
                                170
                            }
                        )
                    }
                )
            cell.minimumHeight = if (list) style.dp(64) else 0
            cell.caption.layoutParams =
                if (list) {
                    LayoutParams(0, -2, 1f).apply {
                        leftMargin = style.dp(12)
                    }
                } else {
                    LayoutParams(-1, -2).apply { topMargin = style.dp(8) }
                }
            cell.gravity = if (list) Gravity.CENTER_VERTICAL else Gravity.TOP
            cell.id = if (record == null) R.id.bookshelf_tile else R.id.home_record
            val chosen = record != null && record.uri in selected
            val name = record?.title ?: shelf?.name ?: context.getString(R.string.uncategorized)
            cell.title.text = if (chosen) {
                context.getString(R.string.library_selected_title, name)
            } else {
                name
            }
            cell.title.setTextColor(if (chosen) style.accent else style.text)
            cell.detail.text =
                record?.let(::metadata)
                    ?: context.getString(
                        R.string.library_document_count,
                        documents.size
                    )
            cell.contentDescription = name
            cell.titleRow.minimumHeight = if (home) style.dp(48) else 0
            cell.more.visibility = if (shelf == null) View.GONE else View.VISIBLE
            cell.more.setOnClickListener { shelf?.let(onShelfAction) }
            val lastRead = documents.filter { it.position != null }.maxOfOrNull { it.openedAt }
            cell.recent.visibility = if (home && lastRead != null) View.VISIBLE else View.GONE
            cell.recent.text = lastRead?.let {
                context.getString(
                    R.string.bookshelf_last_read,
                    DateUtils.getRelativeTimeSpanString(
                        it,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    )
                )
            }.orEmpty()
            cell.image.records = documents.take(if (home) 3 else 1)
            cell.image.invalidate()
            cell.setOnClickListener {
                if (home) {
                    enter(if (entry.uncategorized) "" else requireNotNull(shelf).id)
                } else if (record != null) {
                    if (managing) {
                        if (!selected.add(record.uri)) selected.remove(record.uri)
                        render(catalog)
                    } else {
                        onOpen(record)
                    }
                }
            }
            cell.setOnLongClickListener {
                if (shelf !=
                    null
                ) {
                    onShelfAction(shelf)
                } else if (record !=
                    null
                ) {
                    managing = true
                    selected.add(record.uri)
                    render(catalog)
                }
                true
            }
            cell.buttons.removeAllViews()
            cell.buttons.orientation = LinearLayout.HORIZONTAL
            if (home && ordering && shelf != null && options.shelves == BookshelfSort.CUSTOM) {
                val shelfPosition = catalog.shelves.indexOfFirst { it.id == shelf.id }
                cell.buttons.orientation = LinearLayout.VERTICAL
                cell.buttons.addView(
                    action(R.string.move_document_up) {
                        onShelfReorder(shelf.id, -1)
                    }.apply {
                        isEnabled =
                            shelfPosition > 0
                    }
                )
                cell.buttons.addView(
                    action(R.string.move_document_down) {
                        onShelfReorder(shelf.id, 1)
                    }.apply {
                        isEnabled =
                            shelfPosition < catalog.shelves.lastIndex
                    }
                )
            } else if (!home &&
                managing &&
                options.documents == DocumentSort.MANUAL &&
                record != null
            ) {
                cell.buttons.addView(
                    action(R.string.move_document_up) {
                        onReorder(record.uri, -1, shelfFilter)
                    }.apply {
                        isEnabled =
                            position > 0
                    }
                )
                cell.buttons.addView(
                    action(R.string.move_document_down) {
                        onReorder(record.uri, 1, shelfFilter)
                    }.apply {
                        isEnabled =
                            position < entries.lastIndex
                    }
                )
            }
            cell.buttons.visibility = if (cell.buttons.childCount == 0) View.GONE else View.VISIBLE
            cell.buttons.layoutParams = LayoutParams(if (list) -2 else -1, -2)
            return cell
        }
    }

    private inner class CoverMosaic : View(context) {
        var records = emptyList<ReadingRecord>()
        var square = false
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val bounds = android.graphics.RectF()
        private val target = android.graphics.RectF()
        private val fallback = chrome.glyph(LibraryIcon.DOCUMENT, style.secondary)
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            if (!square) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                return
            }
            val width = MeasureSpec.getSize(widthMeasureSpec)
            setMeasuredDimension(width, resolveSize(width, heightMeasureSpec))
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val count = records.size.coerceAtLeast(1)
            bounds.set(0f, 0f, width.toFloat(), height.toFloat())
            paint.color = style.border
            canvas.drawRoundRect(bounds, style.dp(8).toFloat(), style.dp(8).toFloat(), paint)
            repeat(count) { index ->
                val gap = style.dp(2).toFloat()
                if (!square || count == 1 || index == 0) {
                    bounds.set(
                        0f,
                        0f,
                        if (count ==
                            1
                        ) {
                            width.toFloat()
                        } else {
                            width * 0.64f - gap
                        },
                        height.toFloat()
                    )
                } else {
                    bounds.set(
                        width * 0.64f + gap,
                        (index - 1) * height / 2f,
                        width.toFloat(),
                        index * height / 2f - gap
                    )
                }
                paint.color = if (style.isLight) 0xFFE3E8EF.toInt() else 0xFF303C4B.toInt()
                canvas.drawRoundRect(bounds, style.dp(8).toFloat(), style.dp(8).toFloat(), paint)
                val bitmap = records.getOrNull(index)?.let { covers.get(it.uri) }
                if (bitmap != null) {
                    val scale =
                        minOf(bounds.width() / bitmap.width, bounds.height() / bitmap.height)
                    val w = bitmap.width * scale
                    val h = bitmap.height * scale
                    target.set(
                        bounds.centerX() - w / 2,
                        bounds.centerY() - h / 2,
                        bounds.centerX() + w / 2,
                        bounds.centerY() + h / 2
                    )
                    canvas.drawBitmap(bitmap, null, target, paint)
                } else {
                    paint.color = style.accent
                    paint.textSize = style.dp(14).toFloat()
                    paint.textAlign = Paint.Align.CENTER
                    val size = minOf(
                        style.dp(32).toFloat(),
                        bounds.width() * 0.5f,
                        bounds.height() * 0.5f
                    )
                    fallback.setBounds(
                        (bounds.centerX() - size / 2).toInt(),
                        (bounds.centerY() - size / 2).toInt(),
                        (bounds.centerX() + size / 2).toInt(),
                        (bounds.centerY() + size / 2).toInt()
                    )
                    fallback.draw(canvas)
                }
            }
        }
    }
}
