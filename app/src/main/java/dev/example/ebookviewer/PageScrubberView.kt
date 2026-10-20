package dev.example.ebookviewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/** A 48dp touch surface; only a completed single-pointer gesture can request navigation. */
internal class PageScrubberView @JvmOverloads constructor(
    context: Context,
    private val onBegin: () -> Unit = {},
    private val onCommit: (Int) -> Unit = {}
) : View(context) {
    private val style = ReaderStyle(context)
    private val state = PageScrubState()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val location = IntArray(2)
    private var gesture = false
    var onPreviewChanged: () -> Unit = {}
    val previewPage get() = state.target

    init {
        id = R.id.page_scrubber
        minimumHeight = style.dp(48)
        isClickable = true
    }

    fun update(page: Int?, count: Int, available: Boolean) {
        state.update(page, count, available)
        isEnabled = state.enabled
        invalidate()
    }

    fun cancelPreview(): Boolean {
        val canceled = state.cancel()
        if (canceled) changed()
        return canceled
    }

    private fun changed() {
        invalidate()
        onPreviewChanged()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = style.dp(9).toFloat()
        val end = (width - radius).coerceAtLeast(radius)
        val y = height / 2f
        paint.strokeWidth = style.dp(4).toFloat()
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = style.border
        canvas.drawLine(radius, y, end, y, paint)
        val page = state.target ?: state.current ?: return
        if (state.count <= 1) return
        val x = radius + (end - radius) * (page.toDouble() / (state.count - 1)).toFloat()
        paint.color = if (isEnabled) style.accent else style.secondary
        canvas.drawLine(radius, y, x, y, paint)
        canvas.drawCircle(x, y, radius, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            gesture = true
            if (!isEnabled) return true
            onBegin()
            gesture = state.begin(fraction(event.x))
            if (!gesture) return false
            parent?.requestDisallowInterceptTouchEvent(true)
            changed()
            return true
        }
        if (!gesture) return false
        // Keep consuming the canceled stream so its final UP cannot dismiss tools or move the PDF.
        if (event.pointerCount > 1 || !insideWindow(event)) cancelPreview()
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                state.move(fraction(event.x))
                changed()
            }
            MotionEvent.ACTION_UP -> {
                state.move(fraction(event.x))
                val selected = state.commit()
                endGesture()
                changed()
                performClick()
                if (selected != null) onCommit(selected)
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelPreview()
                endGesture()
            }
        }
        return true
    }

    private fun fraction(x: Float): Double {
        val inset = style.dp(9)
        return (x - inset).toDouble() / (width - inset * 2).coerceAtLeast(1)
    }

    private fun insideWindow(event: MotionEvent): Boolean {
        rootView.getLocationOnScreen(location)
        return event.rawX >= location[0] &&
            event.rawX < location[0] + rootView.width &&
            event.rawY >= location[1] &&
            event.rawY < location[1] + rootView.height
    }

    private fun endGesture() {
        gesture = false
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cancelPreview()
    }

    override fun onDetachedFromWindow() {
        cancelPreview()
        endGesture()
        super.onDetachedFromWindow()
    }
}
