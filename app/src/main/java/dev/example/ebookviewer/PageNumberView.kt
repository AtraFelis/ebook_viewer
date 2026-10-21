package dev.example.ebookviewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import kotlin.math.ceil

/** Two contrasting glyph passes keep the label readable without covering a rectangle of the PDF. */
internal class PageNumberView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(32, 37, 45)
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            12f,
            resources.displayMetrics
        )
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val outline = Paint(ink).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeJoin = Paint.Join.ROUND
    }

    var text: String = ""
        set(value) {
            if (field == value) return
            field = value
            contentDescription = value
            requestLayout()
            invalidate()
        }

    init {
        val padding = ceil(2f * density).toInt()
        setPadding(padding, padding, padding, padding)
        isClickable = false
        isFocusable = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val metrics = ink.fontMetrics
        setMeasuredDimension(
            resolveSize(
                ceil(ink.measureText(text)).toInt() + paddingLeft + paddingRight,
                widthMeasureSpec
            ),
            resolveSize(
                ceil(metrics.bottom - metrics.top).toInt() + paddingTop + paddingBottom,
                heightMeasureSpec
            )
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val metrics = ink.fontMetrics
        val centerX = paddingLeft + (width - paddingLeft - paddingRight) / 2f
        val centerY = paddingTop + (height - paddingTop - paddingBottom) / 2f
        val baseline = centerY - (metrics.top + metrics.bottom) / 2f
        canvas.drawText(text, centerX, baseline, outline)
        canvas.drawText(text, centerX, baseline, ink)
    }
}
