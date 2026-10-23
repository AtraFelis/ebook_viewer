package dev.example.ebookviewer

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
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
            R.attr.readerDisabledText,
            android.R.attr.colorBackground
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
    val background get() = colors[10]
    val isLight = context.obtainStyledAttributes(intArrayOf(R.attr.readerLightTheme)).let {
        try {
            it.getBoolean(0, true)
        } finally {
            it.recycle()
        }
    }

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

    fun plainButtonBackground(radius: Int = 12): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(accentSoft), null, shape(-1, radius))

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

    fun styleChoice(button: CompoundButton) {
        val selected = intArrayOf(android.R.attr.state_checked)
        button.apply {
            textSize = 16f
            minHeight = dp(48)
            minimumHeight = dp(48)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setTextColor(
                ColorStateList(
                    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(colors[9], this@ReaderStyle.text)
                )
            )
            backgroundTintList = null
            background = StateListDrawable().apply {
                addState(selected, shape(accentSoft, 8))
                addState(intArrayOf(), shape(surface, 8))
            }
        }
    }

    fun styleInput(input: EditText) {
        input.apply {
            textSize = 16f
            setTextColor(this@ReaderStyle.text)
            setHintTextColor(secondary)
            minHeight = dp(48)
            minimumHeight = dp(48)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            backgroundTintList = null
            background = StateListDrawable().apply {
                addState(
                    intArrayOf(android.R.attr.state_focused),
                    shape(surface, 8).apply { setStroke(dp(2), accent) }
                )
                addState(intArrayOf(), shape(surface, 8).apply { setStroke(dp(1), border) })
            }
        }
    }

    fun styleSettingButton(button: Button) {
        styleButton(button)
        button.apply {
            textSize = 16f
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setTextColor(
                ColorStateList(
                    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(colors[9], this@ReaderStyle.text)
                )
            )
            background = RippleDrawable(
                ColorStateList.valueOf(accentSoft),
                shape(surface),
                shape(-1)
            )
        }
    }
}
