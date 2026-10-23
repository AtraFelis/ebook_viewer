package dev.example.ebookviewer

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.view.View.AccessibilityDelegate
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView

/** Presentation of existing records only; opening, removal and persistence stay in the Activity. */
internal class ReaderHomeView(
    private val context: Context,
    status: TextView,
    retry: Button,
    reselect: Button,
    onSettings: () -> Unit,
    onPick: () -> Unit,
    onSample: () -> Unit,
    private val onOpen: (ReadingRecord) -> Unit,
    private val onRemove: (ReadingRecord) -> Unit,
    private val onRemoveMany: (Set<String>) -> Unit,
    private val onMove: (Set<String>) -> Unit,
    private val onReorder: (String, Int, String?) -> Unit,
    onShelves: () -> Unit
) {
    val view = LinearLayout(context)
    private val style = ReaderStyle(context)
    private val resume = column()
    private val recent = column()
    private val manage = style.button(R.string.manage_documents) {
        managing = !managing
        selected.clear()
        render(catalog)
    }
    private var records: List<ReadingRecord> = emptyList()
    private var displayedRecords: List<ReadingRecord> = emptyList()
    private var managing = false
    private var catalog = LibraryCatalog()
    private val selected = linkedSetOf<String>()
    var shelfFilter: String? = null
        private set
    private val shelfPicker = Spinner(context).apply { id = R.id.bookshelf_filter }
    private var bindingShelves = false
    private val selectionCount = secondaryLabel(R.string.no_selection)
    private val move = style.button(R.string.move_to_bookshelf) { onMove(selected.toSet()) }
    private val remove = style.button(R.string.remove_selected) { onRemoveMany(selected.toSet()) }
    private val selection = column().apply {
        id = R.id.library_selection
        visibility = View.GONE
        spaced(selectionCount, 8)
        spaced(
            style.button(R.string.select_all_documents) {
                val visible = catalog.visible(shelfFilter).map { it.uri }
                if (selected.containsAll(visible)) {
                    selected.removeAll(visible.toSet())
                } else {
                    selected.addAll(visible)
                }
                render(catalog)
            },
            8
        )
        spaced(move, 8)
        spaced(remove, 8)
    }

    fun finishManaging(): Boolean {
        if (!managing) return false
        managing = false
        selected.clear()
        render(catalog)
        return true
    }

    fun saveState(): Bundle = Bundle().apply {
        putBoolean("managing", managing)
        putString("shelf", shelfFilter)
        putStringArrayList("selected", ArrayList(selected))
    }

    fun restoreState(state: Bundle?) {
        if (state == null) return
        managing = state.getBoolean("managing")
        shelfFilter = state.getString("shelf")
        selected.addAll(state.getStringArrayList("selected").orEmpty())
    }

    private fun updateSelection() {
        selection.visibility = if (managing) View.VISIBLE else View.GONE
        selectionCount.text = context.getString(R.string.documents_selected, selected.size)
        move.isEnabled = selected.isNotEmpty()
        remove.isEnabled = selected.isNotEmpty()
    }

    init {
        view.id = R.id.home_screen
        view.orientation = LinearLayout.VERTICAL
        // Insets belong to this outer container. Content spacing must survive inset reapplication.
        view.addView(
            column().apply {
                setPadding(style.dp(24), style.dp(24), style.dp(24), style.dp(32))
                addView(
                    LinearLayout(context).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        addView(
                            style.label(R.string.my_documents, 22f, true),
                            LayoutParams(0, -2, 1f)
                        )
                        addView(style.button(R.string.settings, onClick = onSettings))
                    }
                )
                spaced(resume, 24)
                spaced(style.button(R.string.open_pdf, onClick = onPick), 16)
                spaced(
                    status.apply {
                        textSize = 14f
                        setTextColor(style.secondary)
                    },
                    16
                )
                spaced(
                    column().apply {
                        style.styleButton(retry)
                        style.styleButton(reselect)
                        addView(retry, LayoutParams(-1, -2))
                        spaced(reselect, 8)
                    },
                    0
                )
                spaced(style.label(R.string.library_documents, 18f, true), 24)
                spaced(shelfPicker, 8)
                spaced(style.button(R.string.manage_bookshelves, onClick = onShelves), 8)
                spaced(manage, 16)
                spaced(selection, 8)
                spaced(recent, 16)
                spaced(secondaryLabel(R.string.remove_record_hint, 12f), 12)
                spaced(style.button(R.string.sample_pdf, onClick = onSample), 24)
                spaced(secondaryLabel(R.string.reader_gesture_hint, 12f), 12)
            },
            LayoutParams(-1, -2)
        )
    }

    fun render(value: LibraryCatalog) {
        catalog = value
        records = value.records
        if (records.isEmpty()) {
            managing = false
        }
        if (shelfFilter != null &&
            shelfFilter != "" &&
            value.shelves.none { it.id == shelfFilter }
        ) {
            shelfFilter = null
        }
        selected.retainAll(value.records.map { it.uri }.toSet())
        bindingShelves = true
        val choices = listOf(null, "") + value.shelves.map { it.id }
        val labels =
            listOf(
                context.getString(R.string.all_documents),
                context.getString(R.string.uncategorized)
            ) +
                value.shelves.map { it.name }
        shelfPicker.adapter =
            ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, labels)
        shelfPicker.setSelection(choices.indexOf(shelfFilter).coerceAtLeast(0))
        shelfPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val filter = choices[position]
                if (bindingShelves || filter == shelfFilter) return
                shelfFilter = filter
                selected.clear()
                render(catalog)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        bindingShelves = false
        val values = value.visible(shelfFilter)
        displayedRecords = values
        selected.retainAll(values.map { it.uri }.toSet())
        resume.visibility = if (managing) View.GONE else View.VISIBLE
        resume.removeAllViews()
        recent.removeAllViews()
        val last = records.firstOrNull { it.position != null }
        if (last != null) {
            resume.addView(secondaryLabel(R.string.last_read_document, 14f))
            resume.spaced(resumeCard(last), 16)
        } else {
            resume.addView(
                card(24).apply {
                    addView(style.label(R.string.home_welcome, 22f, true))
                    spaced(secondaryLabel(R.string.home_local_description), 12)
                }
            )
        }
        // The list always contains every record, including the resume card's document.
        // Management reveals explicit removal without making a long press the only route.
        if (values.isEmpty()) {
            recent.addView(secondaryLabel(R.string.no_documents))
        }
        values.forEachIndexed { index, record ->
            recent.spaced(documentRow(record, index), if (recent.childCount == 0) 0 else 16)
        }
        manage.isEnabled = records.isNotEmpty()
        updateSelection()
        manage.setText(if (managing) R.string.finish_managing else R.string.manage_documents)
    }

    private fun resumeCard(record: ReadingRecord): LinearLayout = card(24).apply {
        addView(title(record, 22f, true))
        spaced(metadata(record), 16)
        spaced(
            ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = record.pageCount.coerceAtLeast(1)
                progress = if (record.pageCount > 0) {
                    ((record.position?.page ?: -1) + 1).coerceIn(0, max)
                } else {
                    0
                }
                progressTintList = ColorStateList.valueOf(style.accent)
                progressBackgroundTintList = ColorStateList.valueOf(style.border)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            16,
            4
        )
        spaced(
            style.button(R.string.continue_reading, true) { onOpen(record) }.apply {
                id = R.id.home_record
                contentDescription = context.getString(
                    R.string.resume_document,
                    entryDescription(record)
                )
                setOnLongClickListener {
                    onRemove(record)
                    true
                }
            },
            16
        )
    }

    private fun documentRow(record: ReadingRecord, index: Int): LinearLayout = card(0).apply {
        addView(
            column().apply {
                id = R.id.home_record
                minimumHeight = style.dp(80)
                setPadding(style.dp(16), style.dp(16), style.dp(16), style.dp(16))
                addView(title(record))
                spaced(metadata(record), 8)
                contentDescription = entryDescription(record)
                isFocusable = true
                setOnClickListener {
                    if (managing) {
                        if (!selected.add(record.uri)) selected.remove(record.uri)
                        render(catalog)
                    } else {
                        onOpen(record)
                    }
                }
                setOnLongClickListener {
                    managing = true
                    selected.add(record.uri)
                    render(catalog)
                    true
                }
                val selectable = context.obtainStyledAttributes(
                    intArrayOf(android.R.attr.selectableItemBackground)
                )
                try {
                    foreground = selectable.getDrawable(0)
                } finally {
                    selectable.recycle()
                }
                accessibilityDelegate = object : AccessibilityDelegate() {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfo
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = Button::class.java.name
                    }
                }
            },
            LayoutParams(-1, -2)
        )
        if (managing) {
            spaced(
                CheckBox(context).apply {
                    id = R.id.select_document
                    minHeight = style.dp(48)
                    text = context.getString(R.string.select_document, record.title)
                    isChecked = record.uri in selected
                    buttonTintList = ColorStateList.valueOf(style.accent)
                    setTextColor(style.text)
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) selected.add(record.uri) else selected.remove(record.uri)
                        updateSelection()
                    }
                },
                0
            )
            val visible = displayedRecords
            spaced(
                LinearLayout(context).apply {
                    addView(
                        style.button(R.string.move_document_up) {
                            onReorder(record.uri, -1, shelfFilter)
                        }.apply { isEnabled = index > 0 },
                        LayoutParams(0, -2, 1f)
                    )
                    addView(
                        style.button(R.string.move_document_down) {
                            onReorder(record.uri, 1, shelfFilter)
                        }.apply { isEnabled = index < visible.lastIndex },
                        LayoutParams(0, -2, 1f)
                    )
                },
                0
            )
        }
    }

    private fun title(record: ReadingRecord, size: Float = 16f, strong: Boolean = false): TextView =
        style.label(R.string.untitled_pdf, size, strong).apply {
            text = record.title
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

    private fun metadata(record: ReadingRecord): TextView =
        style.label(R.string.untitled_pdf, 12f).apply {
            val date = DateUtils.formatDateTime(
                context,
                record.openedAt,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
            )
            text = record.position?.takeIf { record.pageCount > 0 }?.let {
                context.getString(R.string.document_metadata, it.page + 1, record.pageCount, date)
            } ?: date
            setTextColor(style.secondary)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

    private fun entryDescription(record: ReadingRecord): String =
        record.position?.takeIf { record.pageCount > 0 }?.let {
            context.getString(R.string.reading_entry, record.title, it.page + 1, record.pageCount)
        } ?: record.title

    private fun column(): LinearLayout =
        LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private fun card(padding: Int): LinearLayout = column().apply {
        background = style.shape(style.surface, 16)
        val pixels = style.dp(padding)
        setPadding(pixels, pixels, pixels, pixels)
    }

    private fun secondaryLabel(resource: Int, size: Float = 16f): TextView =
        style.label(resource, size).apply { setTextColor(style.secondary) }

    private fun LinearLayout.spaced(view: View, top: Int, height: Int = -2) {
        addView(
            view,
            LayoutParams(-1, if (height < 0) height else style.dp(height)).apply {
                topMargin = style.dp(top)
            }
        )
    }
}
