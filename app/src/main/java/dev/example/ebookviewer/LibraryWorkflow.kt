package dev.example.ebookviewer

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.ListView
import android.widget.ScrollView
import java.util.UUID

/** One window retains navigation drafts; all writes use the application-owned serial IO queue. */
internal class LibraryWorkflow(
    private val activity: Activity,
    private val repository: LibraryRepository,
    private val available: () -> Boolean,
    private val refresh: () -> Unit
) {
    private val style = ReaderStyle(activity)
    private val chrome = LibraryControls(activity)
    private var state: Bundle? = null
    private var dialog: Dialog? = null
    private var catalog = LibraryCatalog()
    private var shelfDocuments = emptyMap<String, List<ReadingRecord>>()
    private var generation = 0
    private var paused = true
    private var busy = false
    private var error = false
    private var loaded = false
    private var list: ListView? = null
    private val covers = BookCoverLoader(activity, priority = 1) { list?.invalidateViews() }
    private fun live() = !activity.isDestroyed && !activity.isFinishing && available()

    fun edit() = start(Bundle().apply { putString("mode", "edit") })

    fun remove(uris: Set<String>, single: Boolean = false) {
        if (uris.isEmpty()) return
        start(
            Bundle().apply {
                putString("mode", "remove")
                putStringArrayList("uris", ArrayList(uris))
                putBoolean("single", single)
            }
        )
    }

    fun actions(shelf: Bookshelf) = start(
        Bundle().apply {
            putString("mode", "actions")
            putString("source", shelf.id)
        }
    )

    fun move(uris: Set<String>, source: String?) {
        // Null means all documents, where moving is deliberately unavailable.
        if (source == null || uris.isEmpty()) return
        start(
            Bundle().apply {
                putString("mode", "move")
                putString("source", source)
                putStringArrayList("uris", ArrayList(uris))
                putBoolean("keep", true)
            }
        )
    }

    private fun start(value: Bundle) {
        if (busy) return
        state = value
        error = false
        reload()
    }

    fun capture(): Bundle? = state?.let(::Bundle)

    fun restore(value: Bundle?) {
        state = value?.let(::Bundle)
    }

    fun resume() {
        paused = false
        if (state != null) reload()
    }

    fun pause() {
        paused = true
        generation++
        dialog?.dismiss()
        dialog = null
        covers.cancel()
    }

    fun close() {
        pause()
        covers.close()
    }

    private fun reload() {
        val request = ++generation
        repository.execute({ it.snapshot() }) { result ->
            if (request != generation || !live() || paused) return@execute
            result.onSuccess {
                catalog = it
                shelfDocuments = it.documentsByShelf()
                loaded = true
                val draft = state ?: return@onSuccess
                val source = draft.getString("source")
                if (!source.isNullOrEmpty() && it.shelves.none { shelf -> shelf.id == source }) {
                    dismiss()
                    refresh()
                    return@onSuccess
                }
                render()
            }.onFailure {
                error = true
                render()
            }
        }
    }

    private fun dismiss() {
        state = null
        dialog?.dismiss()
        dialog = null
        covers.cancel()
    }

    private fun back() {
        val draft = state ?: return
        when (draft.getString("mode")) {
            "name" -> {
                draft.putString("mode", draft.getString("parent", "edit"))
                draft.remove("name")
                draft.remove("newId")
                draft.remove("rename")
                render()
            }
            "confirm" -> {
                draft.putString("mode", draft.getString("parent", "edit"))
                render()
            }
            else -> dismiss()
        }
    }

    private fun transition(mode: String) {
        state?.putString("mode", mode)
        error = false
        render()
    }

    private fun selected(key: String = "selected") =
        state?.getStringArrayList(key).orEmpty().toSet()

    private fun toggle(id: String) {
        val ids = selected().toMutableSet()
        if (!ids.add(id)) ids.remove(id)
        state?.putStringArrayList("selected", ArrayList(ids))
        render()
    }

    private fun write(change: (DocumentLibrary) -> Unit, success: () -> Unit) {
        if (busy) return
        busy = true
        error = false
        render()
        repository.execute(change) { result ->
            busy = false
            if (!live()) return@execute
            result.onSuccess {
                success()
                refresh()
                if (state != null && !paused) reload()
            }.onFailure {
                error = true
                if (!paused) render()
            }
        }
    }

    private fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
    private fun label(text: String, size: Float = 16f, strong: Boolean = false) =
        style.label(R.string.untitled_pdf, size, strong).apply { this.text = text }

    private fun render() {
        if (paused || !live()) return
        val draft = state ?: return
        val mode = draft.getString("mode", "edit")
        val confirmation = mode == "confirm" || mode == "remove"
        val source = draft.getString("source", "")
        val shelf = catalog.shelves.firstOrNull { it.id == source }
        val full = mode == "edit" ||
            mode == "add" ||
            (mode == "name" && draft.getString("parent") == "edit")
        val root = object : LinearLayout(activity) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                if (full) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                } else {
                    val maximum = minOf(
                        MeasureSpec.getSize(heightMeasureSpec),
                        style.dp((activity.resources.configuration.screenHeightDp * 0.78f).toInt())
                    )
                    super.onMeasure(
                        widthMeasureSpec,
                        MeasureSpec.makeMeasureSpec(maximum, MeasureSpec.AT_MOST)
                    )
                }
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            background = style.shape(style.surface, if (full) 0 else 20).apply {
                if (!full && !confirmation) {
                    cornerRadii = floatArrayOf(
                        style.dp(20).toFloat(),
                        style.dp(20).toFloat(),
                        style.dp(20).toFloat(),
                        style.dp(20).toFloat(),
                        0f,
                        0f,
                        0f,
                        0f
                    )
                }
            }
            if (confirmation) {
                setPadding(style.dp(24), style.dp(24), style.dp(24), style.dp(24))
            } else {
                setPadding(style.dp(16), style.dp(8), style.dp(16), style.dp(8))
            }
        }
        val title = when (mode) {
            "move" -> R.string.move_to_bookshelf
            "remove" -> if (draft.getBoolean(
                    "single"
                )
            ) {
                R.string.remove_record
            } else {
                R.string.remove_selected
            }
            "add" -> R.string.library_add_documents
            "actions" -> R.string.manage_bookshelves
            "name" -> if (draft.getBoolean(
                    "rename"
                )
            ) {
                R.string.rename_bookshelf
            } else {
                R.string.add_bookshelf
            }
            "confirm" -> R.string.delete_bookshelf
            else -> R.string.library_edit_shelves
        }
        root.addView(
            row().apply {
                if (!confirmation) {
                    addView(
                        chrome.icon(
                            R.string.library_back,
                            if (full) LibraryIcon.CLOSE else LibraryIcon.BACK
                        ) {
                            back()
                        }
                    )
                }
                addView(
                    label(
                        if (mode ==
                            "actions"
                        ) {
                            shelf?.name.orEmpty()
                        } else {
                            activity.getString(title)
                        },
                        20f,
                        true
                    ).apply { gravity = Gravity.CENTER },
                    LayoutParams(0, -2, 1f)
                )
                if (mode == "edit") {
                    addView(
                        chrome.icon(R.string.add_bookshelf, LibraryIcon.ADD) {
                            name("edit", false)
                        }.apply { id = R.id.bookshelf_add },
                        LayoutParams(style.dp(48), style.dp(48))
                    )
                } else if (!confirmation) {
                    addView(View(activity), LayoutParams(style.dp(48), style.dp(48)))
                }
            }
        )
        if (error) {
            root.addView(
                label(activity.getString(R.string.library_change_error), 14f).apply {
                    setTextColor(chrome.destructive)
                }
            )
            // A failed read must not write an empty replacement catalog.
            root.addView(chrome.action(R.string.retry_document, LibraryIcon.SAVE) { reload() })
        }
        if (busy) root.addView(label(activity.getString(R.string.library_saving), 14f))
        val content = column()
        if (confirmation) content.setPadding(0, style.dp(16), 0, style.dp(24))
        val footer = column()
        val scrollable = mode in setOf("actions", "name", "confirm", "remove")
        val body = if (scrollable) ScrollView(activity).apply { addView(content) } else content
        root.addView(body, LayoutParams(-1, 0, 1f))
        root.addView(footer)
        list = null
        when (if (loaded) mode else "loading") {
            "actions" -> {
                content.addView(
                    chrome.action(R.string.library_add_documents, LibraryIcon.ADD) {
                        draft.remove("selected")
                        transition("add")
                    }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }
                )
                content.addView(
                    chrome.action(R.string.library_shelf_information, LibraryIcon.EDIT) {
                        name("actions", true)
                    }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }
                )
                content.addView(
                    chrome.action(R.string.delete_bookshelf, LibraryIcon.DELETE) {
                        draft.putStringArrayList("deleting", arrayListOf(source))
                        draft.putString("parent", "actions")
                        transition("confirm")
                    }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }
                )
            }
            "edit" -> edit(content, footer, draft)
            "move" -> {
                content.addView(
                    chrome.action(R.string.library_new_shelf, LibraryIcon.ADD) {
                        name("move", false)
                    },
                    LayoutParams(-1, -2).apply {
                        topMargin = style.dp(4)
                        bottomMargin = style.dp(8)
                    }
                )
                val targets = catalog.shelves.filter { it.id != source }
                items(content, targets.size, empty = R.string.library_empty_move_targets, cell = {
                        index,
                        recycled
                    ->
                    shelfRow(targets[index], recycled).apply {
                        setPadding(0, style.dp(8), 0, style.dp(8))
                        val caption = getChildAt(1) as LinearLayout
                        (caption.getChildAt(1).layoutParams as LayoutParams).topMargin = style.dp(4)
                    }
                }) { index ->
                    val target = targets[index].id
                    draft.putString("target", target)
                    val uris = selected("uris")
                    val keep = draft.getBoolean("keep", true)
                    write({ it.transferToShelf(uris, source, target, keep) }) { dismiss() }
                }
                footer.setPadding(0, style.dp(8), 0, 0)
                if (source.isNotEmpty()) {
                    footer.addView(
                        CheckBox(activity).apply {
                            text = activity.getString(R.string.library_keep_source)
                            setTextColor(style.text)
                            minimumHeight = style.dp(48)
                            isChecked = draft.getBoolean("keep", true)
                            setOnCheckedChangeListener { _, checked ->
                                draft.putBoolean("keep", checked)
                            }
                        }
                    )
                }
                if (error &&
                    draft.containsKey("target")
                ) {
                    footer.addView(
                        chrome.action(R.string.library_retry_save, LibraryIcon.SAVE) {
                            val target = requireNotNull(draft.getString("target"))
                            write({
                                it.transferToShelf(
                                    selected("uris"),
                                    source,
                                    target,
                                    draft.getBoolean("keep", true)
                                )
                            }) { dismiss() }
                        }
                    )
                }
            }
            "add" -> {
                val documents = catalog.sortedRecords(null, DocumentSort.NAME)
                content.addView(selectAll(documents.map { it.uri }))
                items(content, documents.size, { index, recycled ->
                    documentRow(documents[index], recycled, documents[index].uri in selected())
                }) { toggle(documents[it].uri) }
                if (selected().isNotEmpty()) {
                    footer.addView(
                        chrome.action(R.string.library_add_documents, LibraryIcon.ADD) {
                            val uris = selected()
                            write({ it.addToShelves(uris, setOf(source)) }) { dismiss() }
                        }
                    )
                }
            }
            "name" -> nameForm(content, footer, draft)
            "remove" -> {
                val uris = selected("uris")
                val message = if (draft.getBoolean("single")) {
                    activity.getString(R.string.remove_record_message)
                } else {
                    activity.getString(R.string.remove_selected_message, uris.size)
                }
                content.addView(
                    label(message).apply {
                        gravity = Gravity.CENTER
                        setTextColor(style.secondary)
                    }
                )
                confirmationActions(footer, title) {
                    write({ it.removeAll(uris) }) { dismiss() }
                }
            }
            "confirm" -> {
                val ids = selected("deleting")
                content.addView(
                    label(
                        activity.getString(R.string.library_delete_shelves_message, ids.size)
                    ).apply {
                        gravity = Gravity.CENTER
                        setTextColor(style.secondary)
                    }
                )
                confirmationActions(footer, R.string.delete_bookshelf) {
                    write({ it.removeShelves(ids) }) {
                        if (draft.getString("parent") == "actions") {
                            dismiss()
                        } else {
                            draft.remove("selected")
                            draft.remove("order")
                            draft.putString("mode", "edit")
                        }
                    }
                }
            }
        }
        fun enable(view: View) {
            if (busy) view.isEnabled = false
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    enable(
                        view.getChildAt(index)
                    )
                }
            }
        }
        enable(root)
        val current = dialog ?: object : Dialog(
            activity,
            if (style.isLight) R.style.ReaderFormLight else R.style.ReaderFormDark
        ) {
            @Deprecated("Legacy dialog back handling")
            override fun onBackPressed() {
                back()
            }
        }.also {
            dialog = it
            it.setOnCancelListener { dismiss() }
        }
        current.setContentView(root)
        current.setCanceledOnTouchOutside(!full)
        current.show()
        current.window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            setGravity(if (full || confirmation) Gravity.CENTER else Gravity.BOTTOM)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            if (full) {
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            } else {
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(0.45f)
            }
            val width = if (confirmation) {
                minOf(style.dp(440), style.dp(activity.resources.configuration.screenWidthDp - 48))
            } else {
                -1
            }
            setLayout(width, if (full) -1 else -2)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        root.setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= 30 && !confirmation) {
                val safe = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or
                        WindowInsets.Type.ime()
                )
                root.setPadding(
                    style.dp(16) + safe.left,
                    style.dp(8) + if (full) safe.top else 0,
                    style.dp(16) + safe.right,
                    style.dp(8) + safe.bottom
                )
            }
            insets
        }
        root.requestApplyInsets()
    }

    private fun confirmationActions(footer: LinearLayout, action: Int, confirmed: () -> Unit) {
        footer.addView(
            row().apply {
                addView(
                    style.button(android.R.string.cancel) { back() }.apply {
                        setTextColor(style.text)
                        val outline = style.shape(Color.TRANSPARENT, 12).apply {
                            setStroke(style.dp(1), style.border)
                        }
                        background = RippleDrawable(
                            ColorStateList.valueOf(style.accentSoft),
                            outline,
                            style.shape(Color.WHITE, 12)
                        )
                    },
                    LayoutParams(0, -2, 1f)
                )
                addView(
                    style.button(R.string.library_confirm_delete, onClick = confirmed).apply {
                        contentDescription = activity.getString(action)
                        setTextColor(Color.WHITE)
                        background = RippleDrawable(
                            ColorStateList.valueOf(0x33FFFFFF),
                            style.shape(0xFFCC4848.toInt(), 12),
                            style.shape(Color.WHITE, 12)
                        )
                    },
                    LayoutParams(0, -2, 1f).apply { leftMargin = style.dp(8) }
                )
            }
        )
    }

    private fun selectAll(ids: List<String>, circular: Boolean = false) = CheckBox(activity).apply {
        text = activity.getString(R.string.select_all_documents)
        setTextColor(style.text)
        minimumHeight = style.dp(48)
        if (circular) {
            circularChoice(this)
            val count = selected().intersect(ids.toSet()).size
            if (count > 0) text = activity.getString(R.string.library_select_count, count)
        }
        isChecked = ids.isNotEmpty() && selected().containsAll(ids)
        setOnClickListener {
            state?.putStringArrayList("selected", ArrayList(if (isChecked) ids else emptyList()))
            render()
        }
    }

    private fun edit(content: LinearLayout, footer: LinearLayout, draft: Bundle) {
        val ordering = draft.getBoolean("ordering")
        content.addView(
            row().apply {
                background = style.shape(if (style.isLight) 0xFFF1F2F4.toInt() else style.border, 8)
                setPadding(style.dp(3), style.dp(3), style.dp(3), style.dp(3))
                listOf(R.string.delete_bookshelf, R.string.library_reorder_shelves).forEachIndexed {
                        index,
                        title
                    ->
                    addView(
                        style.button(title) {
                            draft.putBoolean("ordering", index == 1)
                            transition("edit")
                        }.apply {
                            setTextColor(style.text)
                            val selectedTab = ordering == (index == 1)
                            background = if (selectedTab) {
                                RippleDrawable(
                                    ColorStateList.valueOf(style.accentSoft),
                                    style.shape(style.surface, 6),
                                    style.shape(Color.WHITE, 6)
                                )
                            } else {
                                style.plainButtonBackground(6)
                            }
                            isSelected = selectedTab
                        },
                        LayoutParams(0, -2, 1f)
                    )
                }
            },
            LayoutParams(-1, -2).apply {
                topMargin = style.dp(16)
                bottomMargin = style.dp(16)
            }
        )
        // Preserve a draft's relative order while reconciling shelves added/deleted elsewhere.
        val known = catalog.shelves.map { it.id }
        val order = draft.getStringArrayList("order").orEmpty().filter {
            it in known
        }.toMutableList()
        order.addAll(known.filter { it !in order })
        draft.putStringArrayList("order", ArrayList(order))
        val shelves = order.mapNotNull { id -> catalog.shelves.firstOrNull { it.id == id } }
        if (!ordering) {
            content.addView(
                selectAll(known, circular = true),
                LayoutParams(-1, -2).apply {
                    bottomMargin =
                        style.dp(12)
                }
            )
        }
        items(content, shelves.size, empty = R.string.library_empty_editor, cell = {
                index,
                recycled
            ->
            shelfRow(shelves[index], recycled).apply {
                val id = shelves[index].id
                if (ordering) {
                    val handle = chrome.icon(R.string.library_drag_shelf, LibraryIcon.HANDLE) {}
                    handle.setOnLongClickListener {
                        val data = ClipData.newPlainText("shelf", "")
                        @Suppress("DEPRECATION")
                        if (Build.VERSION.SDK_INT >= 24) {
                            handle.startDragAndDrop(data, View.DragShadowBuilder(handle), id, 0)
                        } else {
                            handle.startDrag(data, View.DragShadowBuilder(handle), id, 0)
                        }
                    }
                    addView(handle)
                    setOnDragListener { target, event ->
                        when (event.action) {
                            DragEvent.ACTION_DRAG_STARTED ->
                                event.localState is String &&
                                    event.localState in order
                            DragEvent.ACTION_DRAG_ENTERED -> {
                                target.alpha = 0.5f
                                true
                            }
                            DragEvent.ACTION_DRAG_EXITED -> {
                                target.alpha = 1f
                                true
                            }
                            DragEvent.ACTION_DROP -> {
                                val moving = event.localState as? String
                                if (moving != null && moving != id && moving in order) {
                                    order.remove(moving)
                                    order.add(order.indexOf(id), moving)
                                    draft.putStringArrayList("order", ArrayList(order))
                                    render()
                                }
                                true
                            }
                            DragEvent.ACTION_DRAG_ENDED -> {
                                target.alpha = 1f
                                true
                            }
                            else -> true
                        }
                    }
                } else {
                    addView(
                        CheckBox(activity).apply {
                            circularChoice(this)
                            isChecked = id in selected()
                            contentDescription = shelves[index].name
                            setOnClickListener { toggle(id) }
                        },
                        0,
                        LayoutParams(style.dp(48), style.dp(48))
                    )
                    setOnDragListener(null)
                }
            }
        }) { if (!ordering) toggle(shelves[it].id) }
        if (ordering) {
            footer.addView(
                chrome.action(R.string.library_save, LibraryIcon.SAVE) {
                    write({ it.reorderShelves(order) }) { dismiss() }
                }
            )
        } else {
            val hasSelection = selected().isNotEmpty()
            footer.gravity = Gravity.CENTER_HORIZONTAL
            footer.addView(
                chrome.icon(R.string.delete_bookshelf, LibraryIcon.DELETE) {
                    draft.putStringArrayList("deleting", ArrayList(selected()))
                    draft.putString("parent", "edit")
                    transition("confirm")
                }.apply {
                    isEnabled = hasSelection
                    setImageDrawable(
                        chrome.glyph(
                            LibraryIcon.DELETE,
                            if (hasSelection) chrome.destructive else style.border
                        )
                    )
                    val circle = style.shape(style.surface, 32).apply {
                        setStroke(style.dp(1), style.border)
                    }
                    background = RippleDrawable(
                        ColorStateList.valueOf(style.accentSoft),
                        circle,
                        style.shape(Color.WHITE, 32)
                    )
                    elevation = style.dp(3).toFloat()
                },
                LayoutParams(style.dp(64), style.dp(64)).apply {
                    topMargin = style.dp(8)
                    bottomMargin = style.dp(8)
                }
            )
        }
    }

    private fun circularChoice(view: CheckBox) {
        fun circle(checked: Boolean) = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setSize(style.dp(20), style.dp(20))
            setColor(if (checked) style.accent else Color.TRANSPARENT)
            if (!checked) setStroke(style.dp(1), style.border)
        }
        val checked = LayerDrawable(
            arrayOf(circle(true), chrome.glyph(LibraryIcon.CHECK, Color.WHITE))
        ).apply {
            setLayerInset(1, style.dp(4), style.dp(4), style.dp(4), style.dp(4))
        }
        view.buttonTintList = null
        val choices = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), checked)
            addState(intArrayOf(), circle(false))
        }
        // CompoundButton does not apply compoundDrawablePadding to its button drawable.
        view.buttonDrawable = InsetDrawable(choices, 0, 0, style.dp(8), 0)
        view.compoundDrawablePadding = style.dp(8)
        view.minimumWidth = style.dp(48)
        view.minimumHeight = style.dp(48)
    }

    private fun name(parent: String, rename: Boolean) {
        state?.apply {
            putString("parent", parent)
            putBoolean("rename", rename)
            putString("newId", UUID.randomUUID().toString())
            putString(
                "name",
                if (rename) {
                    catalog.shelves.firstOrNull {
                        it.id == getString("source")
                    }?.name.orEmpty()
                } else {
                    ""
                }
            )
        }
        transition("name")
    }

    private fun nameForm(content: LinearLayout, footer: LinearLayout, draft: Bundle) {
        val input = EditText(activity).apply {
            id = R.id.bookshelf_name
            filters = arrayOf(InputFilter.LengthFilter(100))
            hint = activity.getString(R.string.bookshelf_name_hint)
            style.styleInput(this)
            setText(draft.getString("name", ""))
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    draft.putString("name", s.toString())
                }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
        }
        content.addView(input)
        footer.addView(
            chrome.action(R.string.library_save, LibraryIcon.SAVE) {
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    input.error = activity.getString(R.string.bookshelf_name_required)
                } else {
                    val rename = draft.getBoolean("rename")
                    val id = requireNotNull(draft.getString(if (rename) "source" else "newId"))
                    write({ store ->
                        if (rename) {
                            store.renameShelf(id, name)
                        } else if (store.snapshot().shelves.none {
                                it.id ==
                                    id
                            }
                        ) {
                            store.addShelf(Bookshelf(id, name))
                        }
                    }) {
                        draft.putString("mode", draft.getString("parent", "edit"))
                        draft.remove("name")
                    }
                }
            }
        )
    }

    private fun items(
        content: LinearLayout,
        count: Int,
        cell: (Int, View?) -> View,
        empty: Int = R.string.no_documents,
        click: (Int) -> Unit
    ) {
        if (count == 0) content.addView(label(activity.getString(empty), 14f))
        val rows = ListView(activity).apply {
            divider = null
            itemsCanFocus = true
            // Short landscape screens scroll controls with the rows, rather than squeezing rows to zero.
            if (content.childCount > 0) {
                val heading = column()
                while (content.childCount > 0) {
                    val child = content.getChildAt(0)
                    content.removeView(child)
                    heading.addView(child)
                }
                addHeaderView(heading, null, false)
            }
            adapter = object : BaseAdapter() {
                override fun getCount() = count
                override fun getItem(position: Int) = position
                override fun getItemId(position: Int) = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup?) =
                    cell(position, convertView).apply {
                        setOnClickListener { click(position) }
                    }
            }
        }
        list = rows
        rows.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, state: Int) = Unit
            override fun onScroll(view: AbsListView?, first: Int, count: Int, total: Int) {
                val visible = (0 until rows.childCount.coerceAtMost(24)).flatMap { index ->
                    val row = rows.getChildAt(index) as? ViewGroup
                    val thumbnail = row?.let { parent ->
                        (0 until parent.childCount).map { parent.getChildAt(it) }
                            .filterIsInstance<Thumbnail>().firstOrNull()
                    }
                    thumbnail?.records.orEmpty().take(3)
                }
                if (!paused) covers.request(visible)
            }
        })
        content.addView(rows, LayoutParams(-1, 0, 1f))
    }

    private fun shelfRow(shelf: Bookshelf, recycled: View?): LinearLayout {
        val documents = shelfDocuments[shelf.id].orEmpty()
        return basicRow(
            recycled,
            shelf.name,
            activity.getString(R.string.library_document_count, documents.size),
            documents.take(3)
        )
    }

    private fun documentRow(
        record: ReadingRecord,
        recycled: View?,
        checked: Boolean
    ): LinearLayout = basicRow(recycled, record.title, "", listOf(record)).apply {
        addView(
            CheckBox(activity).apply {
                isChecked = checked
                contentDescription = record.title
                setOnClickListener { toggle(record.uri) }
            }
        )
    }

    private fun basicRow(
        recycled: View?,
        title: String,
        detail: String,
        records: List<ReadingRecord>
    ): LinearLayout {
        val line = recycled as? LinearLayout ?: row()
        line.removeAllViews()
        line.alpha = 1f
        line.background = style.plainButtonBackground()
        line.setPadding(0, style.dp(6), 0, style.dp(6))
        line.minimumHeight = style.dp(72)
        line.addView(Thumbnail(records), LayoutParams(style.dp(56), style.dp(56)))
        line.addView(
            column().apply {
                setPadding(style.dp(12), 0, style.dp(8), 0)
                addView(
                    label(title, 16f, true).apply {
                        maxLines = 2
                        ellipsize =
                            android.text.TextUtils.TruncateAt.END
                    }
                )
                if (detail.isNotEmpty()) {
                    addView(
                        label(detail, 12f).apply {
                            setTextColor(style.secondary)
                        }
                    )
                }
            },
            LayoutParams(0, -2, 1f)
        )
        return line
    }

    private inner class Thumbnail(val records: List<ReadingRecord>) : View(activity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val rect = RectF()
        private val fallback = chrome.glyph(LibraryIcon.DOCUMENT, style.secondary)
        private val target = RectF()
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val count = records.size.coerceIn(1, 3)
            val gap = style.dp(1).toFloat()
            repeat(count) { index ->
                when {
                    count == 1 -> rect.set(0f, 0f, width.toFloat(), height.toFloat())
                    index == 0 -> rect.set(0f, 0f, width * 0.64f - gap, height.toFloat())
                    else -> rect.set(
                        width * 0.64f + gap,
                        (index - 1) * height / 2f,
                        width.toFloat(),
                        index * height / 2f - gap
                    )
                }
                paint.color = if (style.isLight) 0xFFE3E8EF.toInt() else 0xFF303C4B.toInt()
                canvas.drawRoundRect(rect, style.dp(3).toFloat(), style.dp(3).toFloat(), paint)
                val bitmap = records.getOrNull(index)?.let { covers.get(it.uri) }
                if (bitmap == null) {
                    val size = minOf(
                        style.dp(20).toFloat(),
                        rect.width() * 0.5f,
                        rect.height() * 0.5f
                    )
                    fallback.setBounds(
                        (rect.centerX() - size / 2).toInt(),
                        (rect.centerY() - size / 2).toInt(),
                        (rect.centerX() + size / 2).toInt(),
                        (rect.centerY() + size / 2).toInt()
                    )
                    fallback.draw(canvas)
                } else {
                    // Only bounded thumbnails are decoded off the UI thread.
                    val scale = minOf(rect.width() / bitmap.width, rect.height() / bitmap.height)
                    val w = bitmap.width * scale
                    val h = bitmap.height * scale
                    target.set(
                        rect.centerX() - w / 2,
                        rect.centerY() - h / 2,
                        rect.centerX() + w / 2,
                        rect.centerY() + h / 2
                    )
                    canvas.drawBitmap(bitmap, null, target, paint)
                }
            }
        }
    }
}
