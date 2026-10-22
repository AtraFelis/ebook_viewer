package dev.example.ebookviewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** One borrowed, immutable <=512px preview; no PDF decoding or bitmap copies on the UI thread. */
internal class PageCropPreview(context: Context) : View(context) {
    private val style = ReaderStyle(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val pageRect = RectF()
    private var bitmap: Bitmap? = null
    var crop = PageCrop()
        set(value) {
            field = value
            invalidate()
        }

    init {
        id = R.id.crop_page_preview
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setBackgroundColor(style.background)
    }

    fun showPage(value: Bitmap?) {
        bitmap = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val page = bitmap
        paint.style = Paint.Style.FILL
        if (page == null) {
            paint.color = style.text
            paint.textSize = style.dp(14).toFloat()
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(
                context.getString(R.string.crop_preview_loading),
                width / 2f,
                height / 2f,
                paint
            )
            return
        }
        val padding = style.dp(4)
        val scale = minOf(
            (width - padding * 2f) / page.width,
            (height - padding * 2f) / page.height
        )
        val left = (width - page.width * scale) / 2
        val top = (height - page.height * scale) / 2
        pageRect.set(left, top, left + page.width * scale, top + page.height * scale)
        paint.color = Color.WHITE
        canvas.drawRect(pageRect, paint)
        canvas.drawBitmap(page, null, pageRect, paint)
        val x0 = left + pageRect.width() * crop.left / 100
        val y0 = top + pageRect.height() * crop.top / 100
        val x1 = pageRect.right - pageRect.width() * crop.right / 100
        val y1 = pageRect.bottom - pageRect.height() * crop.bottom / 100
        paint.color = (style.accent and 0x00ffffff) or 0x88000000.toInt()
        canvas.drawRect(left, top, pageRect.right, y0, paint)
        canvas.drawRect(left, y1, pageRect.right, pageRect.bottom, paint)
        canvas.drawRect(left, y0, x0, y1, paint)
        canvas.drawRect(x1, y0, pageRect.right, y1, paint)
        paint.style = Paint.Style.STROKE
        paint.color = style.accent
        paint.strokeWidth = style.dp(1).toFloat()
        canvas.drawRect(x0, y0, x1, y1, paint)
    }
}
