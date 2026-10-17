package dev.example.ebookviewer

import android.app.Activity
import android.app.AlertDialog
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import java.util.Locale

fun Activity.pageLayoutDialog(
    current: PageLayoutSettings,
    apply: (PageLayoutSettings) -> Unit
): AlertDialog {
    val choices = RadioGroup(this).apply {
        addView(
            RadioButton(this@pageLayoutDialog).apply {
                id = R.id.original_page_size
                setText(R.string.original_page_size)
            }
        )
        addView(
            RadioButton(this@pageLayoutDialog).apply {
                id = R.id.uniform_page_width
                setText(R.string.uniform_page_width)
            }
        )
        check(
            if (current.mode == PageSizeMode.ORIGINAL) {
                R.id.original_page_size
            } else {
                R.id.uniform_page_width
            }
        )
    }
    val input = EditText(this).apply {
        id = R.id.page_gap_input
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_NUMBER
        filters = arrayOf(InputFilter.LengthFilter(3))
        hint = getString(R.string.page_gap_hint)
        contentDescription = hint
        setText(String.format(Locale.ROOT, "%d", current.gap))
    }
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(this@pageLayoutDialog).apply { setText(R.string.page_layout_description) })
        addView(choices)
        addView(TextView(this@pageLayoutDialog).apply { setText(R.string.page_gap_hint) })
        addView(input)
    }
    val dialog = AlertDialog.Builder(this)
        .setTitle(R.string.page_layout_settings)
        .setView(content)
        .setPositiveButton(R.string.apply_settings, null)
        .setNegativeButton(android.R.string.cancel, null)
        .create()
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val gap = input.text.toString().toIntOrNull()
            if (gap == null || gap !in 0..72) {
                input.error = getString(R.string.page_gap_hint)
            } else {
                val mode = if (choices.checkedRadioButtonId == R.id.original_page_size) {
                    PageSizeMode.ORIGINAL
                } else {
                    PageSizeMode.UNIFORM_WIDTH
                }
                apply(PageLayoutSettings(mode, gap))
                dialog.dismiss()
            }
        }
    }
    return dialog
}
