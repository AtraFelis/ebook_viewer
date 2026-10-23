package dev.example.ebookviewer

import android.app.Activity
import android.app.AlertDialog
import android.text.InputFilter
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import android.widget.EditText
import android.widget.ListView
import java.util.UUID

/** UI orchestration only; all catalog changes run on the existing serialized library queue. */
internal class LibraryManagement(
    private val activity: Activity,
    private val repository: LibraryRepository,
    private val available: () -> Boolean,
    private val refresh: () -> Unit,
    private val failed: () -> Unit
) {
    private var dialog: AlertDialog? = null
    private var dialogGeneration = 0L
    private fun live(): Boolean = !activity.isDestroyed && !activity.isFinishing && available()

    fun close() {
        dialogGeneration++
        val previous = dialog
        dialog = null
        previous?.dismiss()
    }

    private fun show(value: AlertDialog) {
        close()
        if (!live()) return
        dialog = value
        value.setOnDismissListener {
            if (dialog === value) {
                dialog = null
                dialogGeneration++
            }
        }
        value.show()
    }

    private fun catalog(complete: (LibraryCatalog) -> Unit) {
        val generation = ++dialogGeneration
        repository.execute({ it.snapshot() }) { result ->
            // A dismissed or superseded request must not reopen an old management dialog.
            if (live() && generation == dialogGeneration) {
                result.onSuccess(complete).onFailure { failed() }
            }
        }
    }

    private fun confirm(message: String, action: Int, change: (DocumentLibrary) -> Unit) {
        val explanation = ReaderStyle(activity).label(action).apply { text = message }
        show(
            activity.readerFormDialog(
                action,
                activity.readerFormBody(explanation),
                positive = action,
                onConfirm = { current ->
                    current.dismiss()
                    mutate(change)
                }
            )
        )
    }

    private fun choices(
        title: Int,
        labels: List<String>,
        heading: String? = null,
        selected: (Int) -> Unit
    ) {
        val list = ListView(activity).apply {
            adapter = activity.readerListAdapter(labels)
            divider = null
        }
        val value = activity.readerFormDialog(
            title,
            list,
            negative = R.string.close_settings,
            scrollContent = false
        )
        heading?.let(value::setTitle)
        list.setOnItemClickListener { _, _, index, _ ->
            close()
            selected(index)
        }
        show(value)
    }

    fun remove(uris: Set<String>) {
        if (uris.isEmpty()) return
        confirm(
            activity.getString(R.string.remove_selected_message, uris.size),
            R.string.remove_selected
        ) { it.removeAll(uris) }
    }

    fun reorder(uri: String, direction: Int, filter: String?) = mutate {
        it.moveBy(uri, direction, filter)
    }

    fun reorderShelf(id: String, direction: Int) = mutate { it.moveShelfBy(id, direction) }

    fun removeFromShelf(uris: Set<String>, id: String) = mutate { it.removeFromShelf(uris, id) }

    fun add(uris: Set<String>) {
        if (uris.isEmpty()) return
        catalog { value ->
            if (value.shelves.isEmpty()) {
                name(null)
                return@catalog
            }
            val style = ReaderStyle(activity)
            val body = ListView(activity).apply {
                choiceMode = ListView.CHOICE_MODE_MULTIPLE
                divider = null
                adapter = object : ArrayAdapter<String>(
                    activity,
                    android.R.layout.simple_list_item_multiple_choice,
                    value.shelves.map { it.name }
                ) {
                    override fun getView(
                        position: Int,
                        convertView: android.view.View?,
                        parent: android.view.ViewGroup
                    ): android.view.View = super.getView(position, convertView, parent).apply {
                        (this as CheckedTextView).setTextColor(style.text)
                        minHeight = style.dp(48)
                        background = style.plainButtonBackground()
                    }
                }
            }
            show(
                activity.readerFormDialog(
                    R.string.add_to_bookshelves,
                    body,
                    positive = R.string.apply_settings,
                    scrollContent = false,
                    onConfirm = { current ->
                        val destinations = value.shelves.filterIndexed { index, _ ->
                            body.isItemChecked(index)
                        }.map { it.id }.toSet()
                        if (destinations.isNotEmpty()) {
                            body.isEnabled = false
                            current.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
                            repository.execute({ it.addToShelves(uris, destinations) }) { result ->
                                if (live()) {
                                    result.onSuccess {
                                        if (dialog === current) close()
                                        refresh()
                                    }.onFailure {
                                        body.isEnabled = true
                                        current.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled =
                                            true
                                        failed()
                                    }
                                }
                            }
                        }
                    }
                )
            )
        }
    }

    fun createShelf() = name(null)

    fun move(uris: Set<String>) {
        if (uris.isEmpty()) return
        catalog { value ->
            val labels =
                listOf(activity.getString(R.string.uncategorized)) + value.shelves.map { it.name }
            choices(R.string.move_to_bookshelf, labels) { index ->
                mutate { it.moveToShelf(uris, value.shelves.getOrNull(index - 1)?.id) }
            }
        }
    }

    fun shelves() = catalog { value ->
        val labels =
            listOf(activity.getString(R.string.add_bookshelf)) + value.shelves.map { it.name }
        choices(R.string.manage_bookshelves, labels) { index ->
            if (index == 0) name(null) else shelfActions(value.shelves[index - 1])
        }
    }

    fun shelfActions(shelf: Bookshelf) {
        choices(
            R.string.manage_bookshelves,
            listOf(
                activity.getString(R.string.rename_bookshelf),
                activity.getString(R.string.delete_bookshelf)
            ),
            heading = shelf.name
        ) { index ->
            if (index == 0) {
                name(shelf)
            } else {
                confirm(
                    activity.getString(R.string.delete_bookshelf_message),
                    R.string.delete_bookshelf
                ) { it.removeShelf(shelf.id) }
            }
        }
    }

    private fun name(shelf: Bookshelf?) {
        val style = ReaderStyle(activity)
        val input = EditText(activity).apply {
            id = R.id.bookshelf_name
            hint = activity.getString(R.string.bookshelf_name_hint)
            filters = arrayOf(InputFilter.LengthFilter(100))
            setText(shelf?.name.orEmpty())
            setTextColor(style.text)
            setHintTextColor(style.secondary)
            maxLines = 2
            style.styleInput(this)
        }
        val form = activity.readerFormDialog(
            if (shelf == null) R.string.add_bookshelf else R.string.rename_bookshelf,
            activity.readerFormBody(input),
            positive = R.string.apply_settings,
            onConfirm = { current ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    input.error = activity.getString(R.string.bookshelf_name_required)
                } else {
                    current.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
                    val id = shelf?.id ?: UUID.randomUUID().toString()
                    repository.execute({ store ->
                        if (shelf == null) {
                            store.addShelf(Bookshelf(id, name))
                        } else {
                            store.renameShelf(id, name)
                        }
                    }) { result ->
                        if (live()) {
                            result.onSuccess {
                                // The save can complete after the user opened a different dialog.
                                if (dialog === current) close()
                                refresh()
                            }.onFailure {
                                if (dialog === current && current.isShowing) {
                                    current.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                                    input.error = activity.getString(R.string.library_change_error)
                                } else {
                                    failed()
                                }
                            }
                        }
                    }
                }
            }
        )
        show(form)
    }

    private fun mutate(change: (DocumentLibrary) -> Unit) {
        repository.execute(change) { result ->
            if (live()) result.onSuccess { refresh() }.onFailure { failed() }
        }
    }
}
