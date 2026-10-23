package dev.example.ebookviewer

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import kotlin.math.cos
import kotlin.math.sin

internal enum class LibraryIcon {
    BACK,
    CLOSE,
    SETTINGS,
    MORE,
    SORT,
    GRID,
    LIST,
    CHECK,
    ADD,
    MOVE,
    DELETE,
    EDIT,
    SAVE,
    DOCUMENT,
    HANDLE
}

/** Library chrome has its own transparent controls; reading tools keep their established styling. */
internal class LibraryControls(private val context: Context) {
    private val style = ReaderStyle(context)
    val destructive = if (style.isLight) 0xFFB3261E.toInt() else 0xFFFF6B6B.toInt()

    fun action(label: Int, symbol: LibraryIcon, click: () -> Unit): Button =
        style.button(label, onClick = click).apply {
            background = style.plainButtonBackground()
            setTextColor(if (symbol == LibraryIcon.DELETE) destructive else style.text)
            setPadding(style.dp(8), style.dp(8), style.dp(8), style.dp(8))
            compoundDrawablePadding = style.dp(6)
            setCompoundDrawablesRelative(
                glyph(symbol, if (symbol == LibraryIcon.DELETE) destructive else style.text).apply {
                    setBounds(0, 0, style.dp(20), style.dp(20))
                },
                null,
                null,
                null
            )
        }

    fun icon(label: Int, symbol: LibraryIcon, click: () -> Unit): ImageButton =
        ImageButton(context).apply {
            contentDescription = context.getString(label)
            setImageDrawable(glyph(symbol))
            scaleType = ImageView.ScaleType.CENTER
            background = style.plainButtonBackground()
            minimumWidth = style.dp(48)
            minimumHeight = style.dp(48)
            setPadding(style.dp(14), style.dp(14), style.dp(14), style.dp(14))
            setOnClickListener { click() }
        }

    fun raised(view: View, radius: Int) {
        val surface = style.shape(style.surface, radius).apply {
            setStroke(style.dp(1), style.border)
        }
        view.background = RippleDrawable(
            ColorStateList.valueOf(style.accentSoft),
            surface,
            style.shape(Color.WHITE, radius)
        )
        view.elevation = style.dp(3).toFloat()
    }

    fun glyph(symbol: LibraryIcon, color: Int = style.text): Drawable =
        LibraryGlyph(symbol, color, style.dp(20))
}

private class LibraryGlyph(private val symbol: LibraryIcon, color: Int, private val size: Int) :
    Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    private fun line(canvas: Canvas, vararg points: Float) {
        path.reset()
        path.moveTo(points[0], points[1])
        for (index in 2 until points.size step 2) path.lineTo(points[index], points[index + 1])
        canvas.drawPath(path, paint)
    }

    override fun draw(canvas: Canvas) {
        val saved = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        when (symbol) {
            LibraryIcon.BACK -> line(canvas, 15f, 4f, 7f, 12f, 15f, 20f)
            LibraryIcon.CLOSE -> {
                line(canvas, 5f, 5f, 19f, 19f)
                line(canvas, 19f, 5f, 5f, 19f)
            }
            LibraryIcon.MORE -> repeat(3) { canvas.drawCircle(12f, 5f + it * 7, 0.8f, paint) }
            LibraryIcon.SETTINGS -> {
                canvas.drawCircle(12f, 12f, 7f, paint)
                canvas.drawCircle(12f, 12f, 2.5f, paint)
                repeat(8) {
                    val angle = it * Math.PI / 4
                    canvas.drawLine(
                        12f + cos(angle).toFloat() * 7,
                        12f + sin(angle).toFloat() * 7,
                        12f + cos(angle).toFloat() * 10,
                        12f + sin(angle).toFloat() * 10,
                        paint
                    )
                }
            }
            LibraryIcon.SORT -> {
                line(canvas, 4f, 8f, 8f, 4f, 12f, 8f)
                line(canvas, 8f, 4f, 8f, 20f)
                line(canvas, 12f, 16f, 16f, 20f, 20f, 16f)
                line(canvas, 16f, 20f, 16f, 4f)
            }
            LibraryIcon.GRID -> repeat(4) {
                val x = 4f + (it % 2) * 10
                val y = 4f + (it / 2) * 10
                canvas.drawRect(x, y, x + 6, y + 6, paint)
            }
            LibraryIcon.LIST, LibraryIcon.HANDLE -> repeat(3) {
                val y = 6f + it * 6
                canvas.drawLine(if (symbol == LibraryIcon.LIST) 8f else 4f, y, 20f, y, paint)
                if (symbol == LibraryIcon.LIST) canvas.drawCircle(4f, y, 0.7f, paint)
            }
            LibraryIcon.CHECK -> line(canvas, 5f, 12f, 10f, 17f, 20f, 7f)
            LibraryIcon.ADD -> {
                line(canvas, 12f, 4f, 12f, 20f)
                line(canvas, 4f, 12f, 20f, 12f)
            }
            LibraryIcon.MOVE -> {
                line(canvas, 3f, 19f, 3f, 5f, 9f, 5f, 11f, 8f, 21f, 8f, 21f, 19f)
                line(canvas, 8f, 14f, 17f, 14f, 14f, 11f)
                line(canvas, 17f, 14f, 14f, 17f)
            }
            LibraryIcon.DELETE -> {
                line(canvas, 4f, 6f, 20f, 6f)
                line(canvas, 9f, 6f, 9f, 3f, 15f, 3f, 15f, 6f)
                line(canvas, 6f, 6f, 7f, 21f, 17f, 21f, 18f, 6f)
                line(canvas, 10f, 10f, 10f, 17f)
                line(canvas, 14f, 10f, 14f, 17f)
            }
            LibraryIcon.EDIT -> line(canvas, 4f, 20f, 5f, 15f, 17f, 3f, 21f, 7f, 9f, 19f, 4f, 20f)
            LibraryIcon.SAVE -> {
                line(canvas, 4f, 3f, 18f, 3f, 21f, 6f, 21f, 21f, 3f, 21f, 3f, 3f)
                canvas.drawRect(7f, 3f, 16f, 9f, paint)
                canvas.drawRect(7f, 14f, 17f, 21f, paint)
            }
            LibraryIcon.DOCUMENT -> {
                line(canvas, 5f, 3f, 15f, 3f, 20f, 8f, 20f, 21f, 5f, 21f, 5f, 3f)
                line(canvas, 15f, 3f, 15f, 8f, 20f, 8f)
                line(canvas, 9f, 12f, 16f, 12f)
                line(canvas, 9f, 16f, 16f, 16f)
            }
        }
        canvas.restoreToCount(saved)
    }

    override fun getIntrinsicWidth() = size
    override fun getIntrinsicHeight() = size

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }
    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
