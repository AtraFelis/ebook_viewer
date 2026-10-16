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
    val input = EditText(this).apply {
        id = R.id.page_number_input
        inputType = InputType.TYPE_CLASS_NUMBER
        filters = arrayOf(InputFilter.LengthFilter(10))
        isSingleLine = true
        // Keep the editable number ungrouped and compatible with numeric keyboard input.
        setText(String.format(Locale.ROOT, getString(R.string.page_number_value), currentPage + 1))
        selectAll()
        contentDescription = getString(R.string.page_number_hint, pageCount)
    }
    val dialog = AlertDialog.Builder(this)
        .setTitle(R.string.go_to_page)
        .setMessage(getString(R.string.page_number_hint, pageCount))
        .setView(input)
        .setPositiveButton(R.string.navigate, null)
        .setNegativeButton(android.R.string.cancel, null)
        .create()
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val page = input.text.toString().toIntOrNull()
            if (page == null || page !in 1..pageCount) {
                input.error = getString(R.string.page_number_hint, pageCount)
            } else {
                navigate(page - 1)
                dialog.dismiss()
            }
        }
    }
    return dialog
}
