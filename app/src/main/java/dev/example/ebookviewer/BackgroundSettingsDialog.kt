package dev.example.ebookviewer

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import java.util.Locale

internal fun Activity.backgroundSettingsDialog(
    current: BackgroundSettings,
    draft: Bundle? = null,
    save: (BackgroundSettings, (Boolean) -> Unit) -> Unit
): EditableSettingsForm {
    val style = ReaderStyle(this)
    val saving = SettingsSaveUi(this, draft?.getBoolean("failed") == true)
    val automatic = Switch(this).apply {
        id = R.id.background_automatic
        setText(R.string.background_automatic)
        isChecked = draft?.getBoolean("automatic") ?: current.automatic
        style.styleChoice(this)
    }
    val input = EditText(this).apply {
        id = R.id.background_color_input
        isSingleLine = true
        hint = getString(R.string.background_color_hint)
        contentDescription = getString(R.string.background_color_hint)
        filters = arrayOf(InputFilter.LengthFilter(7))
        setText(
            draft?.getString("color")
                ?: String.format(Locale.ROOT, "#%06X", current.customColor and 0xffffff)
        )
        style.styleInput(this)
    }
    val swatch = View(this).apply {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        setBackgroundColor(current.customColor)
    }
    fun selectedColor(): Int? = parseBackgroundColor(input.text.toString())
    input.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
            selectedColor()?.let(swatch::setBackgroundColor)
        }
        override fun afterTextChanged(text: Editable?) = Unit
    })
    val presets = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val colors = listOf(
            R.string.color_white to "#FFFFFF",
            R.string.color_black to "#000000",
            R.string.color_gray to "#444444",
            R.string.color_paper to "#F4ECD8"
        )
        colors.chunked(2).forEach { rowColors ->
            addView(
                LinearLayout(this@backgroundSettingsDialog).apply {
                    rowColors.forEachIndexed { index, (label, color) ->
                        addView(
                            style.button(label) { input.setText(color) },
                            LinearLayout.LayoutParams(0, -2, 1f).apply {
                                topMargin = style.dp(8)
                                leftMargin = if (index == 0) 0 else style.dp(8)
                            }
                        )
                    }
                }
            )
        }
    }
    selectedColor()?.let(swatch::setBackgroundColor)
    val content = readerFormBody(
        style.label(R.string.background_description),
        automatic,
        style.label(R.string.background_color_hint, 14f),
        input,
        LinearLayout(this).apply {
            addView(swatch, LinearLayout.LayoutParams(-1, style.dp(32)))
        },
        presets,
        saving.notice
    )
    val dialog = readerFormDialog(
        R.string.background_settings,
        content,
        positive = R.string.apply_settings,
        onConfirm = { dialog ->
            val color = selectedColor()
            if (color == null) {
                input.error = getString(R.string.background_invalid_color)
            } else {
                val selected = BackgroundSettings(color, automatic.isChecked)
                saving.submit(dialog, content) { done -> save(selected, done) }
            }
        }
    )
    return EditableSettingsForm(dialog) {
        Bundle().apply {
            putString("color", input.text.toString())
            putBoolean("automatic", automatic.isChecked)
            putBoolean("failed", saving.captureFailed())
        }
    }
}
