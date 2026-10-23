package dev.example.ebookviewer

import android.content.Context
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView

/** Shared rows retain the reader theme and enough height for large-font glyphs. */
internal fun Context.readerListAdapter(
    labels: List<String>,
    singleChoice: Boolean = false,
    icons: List<Int>? = null
) = object : ArrayAdapter<String>(
    this,
    if (singleChoice) {
        android.R.layout.simple_list_item_single_choice
    } else {
        android.R.layout.simple_list_item_1
    },
    labels
) {
    override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
        super.getView(position, convertView, parent).apply {
            val style = ReaderStyle(this@readerListAdapter)
            minimumHeight = style.dp(64)
            // Platform list rows have a fixed preferred height; padding otherwise clips glyphs.
            layoutParams.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            setPadding(style.dp(24), style.dp(16), style.dp(24), style.dp(16))
            (this as TextView).apply {
                textSize = 16f
                setTextColor(style.text)
                isSingleLine = false
                includeFontPadding = true
                val icon = icons?.get(position)?.let {
                    this@readerListAdapter.getDrawable(it)?.mutate()?.apply {
                        setTint(style.accent)
                        setBounds(0, 0, style.dp(24), style.dp(24))
                    }
                }
                setCompoundDrawablesRelative(icon, null, null, null)
                compoundDrawablePadding = style.dp(12)
            }
        }
}
