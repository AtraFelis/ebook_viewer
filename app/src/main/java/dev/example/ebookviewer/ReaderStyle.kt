package dev.example.ebookviewer

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.TextView
import kotlin.math.roundToInt

/** App chrome only: PDF pixels and the user's outside-page color have separate owners. */
internal class ReaderStyle(private val context: Context) {
    private val colors = context.obtainStyledAttributes(
        intArrayOf(
            R.attr.readerSurface,
            R.attr.readerText,
            R.attr.readerSecondaryText,
            R.attr.readerBorder,
            R.attr.readerAccentSoft,
            android.R.attr.colorAccent,
            R.attr.readerPressed,
            R.attr.readerOnAccent,
            R.attr.readerDisabled,
            R.attr.readerDisabledText
        )
    ).let { values ->
        try {
            IntArray(values.length()) { values.getColor(it, 0) }
        } finally {
            values.recycle()
        }
    }
    val surface get() = colors[0]
    val text get() = colors[1]
    val secondary get() = colors[2]
    val border get() = colors[3]
    val accentSoft get() = colors[4]
    val accent get() = colors[5]

    fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    fun label(resource: Int, size: Float = 16f, strong: Boolean = false): TextView =
        TextView(context).apply {
            setText(resource)
            textSize = size
            setTextColor(this@ReaderStyle.text)
            includeFontPadding = false
            typeface = Typeface.create("sans-serif", if (strong) Typeface.BOLD else Typeface.NORMAL)
            // Keep line spacing in the same scaled units as the system font, including 200% UI.
            setLineSpacing(textSize * 0.2f, 1f)
        }

    fun shape(color: Int, radius: Int = 12): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    fun button(resource: Int, primary: Boolean = false, onClick: () -> Unit): Button =
        Button(context).apply {
            setText(resource)
            styleButton(this, primary)
            setOnClickListener { onClick() }
        }

    fun styleButton(button: Button, primary: Boolean = false) {
        val enabled = intArrayOf(android.R.attr.state_enabled)
        val foreground = if (primary) colors[7] else accent
        val fill = if (primary) accent else accentSoft
        button.apply {
            isAllCaps = false
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0f
            gravity = Gravity.CENTER
            minHeight = dp(48)
            minimumHeight = dp(48)
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setTextColor(
                ColorStateList(
                    arrayOf(enabled, intArrayOf()),
                    intArrayOf(foreground, colors[9])
                )
            )
            backgroundTintList = null
            val fills = StateListDrawable().apply {
                addState(enabled, shape(fill))
                addState(intArrayOf(), shape(colors[8]))
            }
            background = RippleDrawable(ColorStateList.valueOf(colors[6]), fills, shape(-1))
            stateListAnimator = null
            elevation = 0f
        }
    }
}
