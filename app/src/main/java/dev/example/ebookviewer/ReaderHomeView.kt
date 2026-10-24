package dev.example.ebookviewer

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

/** Preserve the main header; bookshelves replace only the registered-documents section. */
internal class ReaderHomeView(
    context: Context,
    status: TextView,
    retry: Button,
    reselect: Button,
    onSettings: () -> Unit,
    onPick: () -> Unit,
    onSample: () -> Unit,
    onOpen: (ReadingRecord) -> Unit,
    onRemove: (ReadingRecord) -> Unit,
    onRemoveMany: (Set<String>) -> Unit,
    onMove: (Set<String>) -> Unit,
    onReorder: (String, Int, String?) -> Unit,
    onShelves: () -> Unit,
    onRemoveFromShelf: (Set<String>, String) -> Unit = { _, _ -> },
    onShelfAction: (Bookshelf) -> Unit = {},
    onShelfReorder: (String, Int) -> Unit = { _, _ -> },
    onEditShelves: () -> Unit = onShelves
) {
    private val header = ReaderMainHeader(context, onSettings, onPick, onOpen, onRemove)
    private val library = BookshelfLibraryView(
        context, status, retry, reselect, onSettings, onPick, onSample, onOpen,
        onRemove, onRemoveMany, onMove, onReorder, onShelves,
        onRemoveFromShelf = onRemoveFromShelf,
        onShelfAction = onShelfAction,
        onShelfReorder = onShelfReorder,
        mainHeader = header.view,
        onEditShelves = onEditShelves
    )
    val view get() = library.view
    val shelfFilter get() = library.shelfFilter

    fun render(value: LibraryCatalog) {
        header.render(value)
        library.render(value)
    }

    fun setActive(value: Boolean) = library.setActive(value)
    fun close() = library.close()
    fun finishManaging() = library.finishManaging()
    fun saveState(): Bundle = library.saveState()

    fun restoreState(state: Bundle?) {
        library.restoreState(state?.getBundle("shelves") ?: state)
    }
}
