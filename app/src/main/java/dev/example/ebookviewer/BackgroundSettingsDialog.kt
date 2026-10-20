package dev.example.ebookviewer

import android.app.Activity
import android.app.AlertDialog
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

fun Activity.backgroundSettingsDialog(
    current: BackgroundSettings,
    save: (BackgroundSettings) -> Unit
): AlertDialog {
    val automatic = CheckBox(this).apply {
        id = R.id.background_automatic
        setText(R.string.background_automatic)
        isChecked = current.automatic
    }
    val input = EditText(this).apply {
        id = R.id.background_color_input
        isSingleLine = true
        hint = getString(R.string.background_color_hint)
        contentDescription = getString(R.string.background_color_hint)
        filters = arrayOf(InputFilter.LengthFilter(7))
        setText(String.format(Locale.ROOT, "#%06X", current.customColor and 0xffffff))
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
        orientation = LinearLayout.HORIZONTAL
        val colors = listOf(
            R.string.color_white to "#FFFFFF",
            R.string.color_black to "#000000",
            R.string.color_gray to "#444444",
            R.string.color_paper to "#F4ECD8"
        )
        for ((label, color) in colors) {
            val button = Button(this@backgroundSettingsDialog).apply {
                setText(label)
                setOnClickListener { input.setText(color) }
            }
            addView(button, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val padding = (16 * resources.displayMetrics.density).toInt()
        setPadding(padding, 0, padding, padding)
        addView(
            TextView(this@backgroundSettingsDialog).apply {
                setText(R.string.background_description)
            }
        )
        addView(automatic)
        addView(input)
        val swatchHeight = (32 * resources.displayMetrics.density).toInt()
        addView(swatch, LinearLayout.LayoutParams(-1, swatchHeight))
        addView(presets)
    }
    val dialog = AlertDialog.Builder(this)
        .setTitle(R.string.background_settings)
        .setView(ScrollView(this).apply { addView(content) })
        .setPositiveButton(R.string.apply_settings, null)
        .setNegativeButton(android.R.string.cancel, null)
        .create()
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val color = selectedColor()
            if (color == null) {
                input.error = getString(R.string.background_invalid_color)
            } else {
                save(BackgroundSettings(color, automatic.isChecked))
                dialog.dismiss()
            }
        }
    }
    return dialog
}
