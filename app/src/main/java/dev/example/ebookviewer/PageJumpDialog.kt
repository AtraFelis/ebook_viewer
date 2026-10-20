package dev.example.ebookviewer

import android.app.Activity
import android.app.AlertDialog
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import java.util.Locale

fun Activity.pageJumpDialog(
    currentPage: Int,
    pageCount: Int,
    navigate: (Int) -> Unit
): AlertDialog {
    val style = ReaderStyle(this)
    val input = EditText(this).apply {
        id = R.id.page_number_input
        inputType = InputType.TYPE_CLASS_NUMBER
        filters = arrayOf(InputFilter.LengthFilter(10))
        isSingleLine = true
        // Keep the editable number ungrouped and compatible with numeric keyboard input.
        setText(String.format(Locale.ROOT, getString(R.string.page_number_value), currentPage + 1))
        selectAll()
        contentDescription = getString(R.string.page_number_hint, pageCount)
        style.styleInput(this)
    }
    return readerFormDialog(
        R.string.go_to_page,
        readerFormBody(
            style.label(R.string.go_to_page).apply { text = input.contentDescription },
            input
        ),
        positive = R.string.navigate,
        onConfirm = { dialog ->
            val page = input.text.toString().toIntOrNull()
            if (page == null || page !in 1..pageCount) {
                input.error = getString(R.string.page_number_hint, pageCount)
            } else {
                navigate(page - 1)
                dialog.dismiss()
            }
        }
    )
}
