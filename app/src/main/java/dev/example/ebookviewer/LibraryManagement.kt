package dev.example.ebookviewer

import android.app.Activity
import android.app.AlertDialog
import android.text.InputFilter
import android.widget.EditText
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
    private fun live(): Boolean = !activity.isDestroyed && !activity.isFinishing && available()

    fun close() {
        dialog?.dismiss()
        dialog = null
    }

    private fun show(value: AlertDialog) {
        close()
        if (!live()) return
        dialog = value
        value.show()
    }

    private fun catalog(complete: (LibraryCatalog) -> Unit) {
        repository.execute({ it.snapshot() }) { result ->
            if (live()) result.onSuccess(complete).onFailure { failed() }
        }
    }

    fun remove(uris: Set<String>) {
        if (uris.isEmpty()) return
        show(
            AlertDialog.Builder(activity)
                .setMessage(activity.getString(R.string.remove_selected_message, uris.size))
                .setPositiveButton(R.string.remove_selected) { _, _ ->
                    mutate { it.removeAll(uris) }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        )
    }

    fun reorder(uri: String, direction: Int, filter: String?) = mutate {
        it.moveBy(uri, direction, filter)
    }

    fun move(uris: Set<String>) {
        if (uris.isEmpty()) return
        catalog { value ->
            val choices =
                listOf(activity.getString(R.string.uncategorized)) + value.shelves.map { it.name }
            show(
                AlertDialog.Builder(activity)
                    .setTitle(R.string.move_to_bookshelf)
                    .setItems(choices.toTypedArray()) { _, index ->
                        mutate { it.moveToShelf(uris, value.shelves.getOrNull(index - 1)?.id) }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .create()
            )
        }
    }

    fun shelves() = catalog { value ->
        val choices =
            listOf(activity.getString(R.string.add_bookshelf)) + value.shelves.map { it.name }
        show(
            AlertDialog.Builder(activity)
                .setTitle(R.string.manage_bookshelves)
                .setItems(choices.toTypedArray()) { _, index ->
                    if (index == 0) name(null) else shelfActions(value.shelves[index - 1])
                }
                .setNegativeButton(R.string.close_settings, null)
                .create()
        )
    }

    private fun shelfActions(shelf: Bookshelf) {
        show(
            AlertDialog.Builder(activity)
                .setTitle(shelf.name)
                .setItems(
                    arrayOf(
                        activity.getString(R.string.rename_bookshelf),
                        activity.getString(R.string.delete_bookshelf)
                    )
                ) { _, index ->
                    if (index == 0) {
                        name(shelf)
                    } else {
                        show(
                            AlertDialog.Builder(activity)
                                .setMessage(R.string.delete_bookshelf_message)
                                .setPositiveButton(R.string.delete_bookshelf) { _, _ ->
                                    mutate { it.removeShelf(shelf.id) }
                                }
                                .setNegativeButton(android.R.string.cancel, null)
                                .create()
                        )
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        )
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
                                close()
                                refresh()
                            }.onFailure {
                                current.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                                input.error = activity.getString(R.string.library_change_error)
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
