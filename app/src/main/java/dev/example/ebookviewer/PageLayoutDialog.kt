package dev.example.ebookviewer

import android.app.Activity
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import java.util.Locale

internal fun Activity.pageLayoutDialog(
    current: PageLayoutSettings,
    draft: Bundle? = null,
    apply: (PageLayoutSettings, (Boolean) -> Unit) -> Unit
): EditableSettingsForm {
    val style = ReaderStyle(this)
    val saving = SettingsSaveUi(this, draft?.getBoolean("failed") == true)
    val choices = RadioGroup(this).apply {
        addView(
            RadioButton(this@pageLayoutDialog).apply {
                id = R.id.original_page_size
                setText(R.string.original_page_size)
                style.styleChoice(this)
            }
        )
        addView(
            RadioButton(this@pageLayoutDialog).apply {
                id = R.id.uniform_page_width
                setText(R.string.uniform_page_width)
                style.styleChoice(this)
            }
        )
        check(
            if ((draft?.getString("mode") ?: current.mode.name) == PageSizeMode.ORIGINAL.name) {
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
        setText(draft?.getString("gap") ?: String.format(Locale.ROOT, "%d", current.gap))
        style.styleInput(this)
    }
    val content = readerFormBody(
        style.label(R.string.page_layout_description),
        choices,
        style.label(R.string.page_gap_hint, 14f),
        input,
        saving.notice
    )
    val dialog = readerFormDialog(
        R.string.page_layout_settings,
        content,
        positive = R.string.apply_settings,
        onConfirm = { dialog ->
            val gap = input.text.toString().toIntOrNull()
            if (gap == null || gap !in 0..72) {
                input.error = getString(R.string.page_gap_hint)
            } else {
                val mode = if (choices.checkedRadioButtonId == R.id.original_page_size) {
                    PageSizeMode.ORIGINAL
                } else {
                    PageSizeMode.UNIFORM_WIDTH
                }
                val selected = PageLayoutSettings(mode, gap)
                saving.submit(dialog, content) { done -> apply(selected, done) }
            }
        }
    )
    return EditableSettingsForm(dialog) {
        Bundle().apply {
            putString("gap", input.text.toString())
            putString(
                "mode",
                if (choices.checkedRadioButtonId == R.id.original_page_size) {
                    PageSizeMode.ORIGINAL.name
                } else {
                    PageSizeMode.UNIFORM_WIDTH.name
                }
            )
            putBoolean("failed", saving.captureFailed())
        }
    }
}
