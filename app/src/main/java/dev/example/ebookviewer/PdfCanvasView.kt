package dev.example.ebookviewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.util.LruCache
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.OverScroller
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class PdfCanvasView @JvmOverloads constructor(
    context: Context,
    private val request: (List<Tile>) -> Unit = {
    }
) : View(context) {
    private val viewport = Viewport()
    private val destination = RectF()
    private var pendingClick = false
    private var layout: DocumentLayout? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var requested = emptyList<Tile>()
    private var previews = emptyList<Tile>()

    // Keys only: fallback bitmaps remain inside the same byte-bounded cache.
    private var fallback = emptyList<Tile>()
    private var restored: Bundle? = null
    private var pendingPages: List<PageSize>? = null
    private val cache = object : LruCache<Tile, Bitmap>(64 * 1024 * 1024) {
        override fun sizeOf(key: Tile, value: Bitmap): Int = value.allocationByteCount
    }
    private val scroller = OverScroller(context)
    private val scaleDetector =
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    scroller.forceFinished(true)
                    viewport.zoomAt(
                        detector.scaleFactor.toDouble(),
                        detector.focusX.toDouble(),
                        detector.focusY.toDouble()
                    )
                    constrain()
                    return true
                }
            }
        )
    private val gestures =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent): Boolean {
                    scroller.forceFinished(true)
                    return true
                }

                override fun onScroll(
                    first: MotionEvent?,
                    current: MotionEvent,
                    distanceX: Float,
                    distanceY: Float
                ): Boolean {
                    if (!scaleDetector.isInProgress) {
                        viewport.x -= distanceX
                        viewport.y -= distanceY
                        constrain()
                    }
                    return true
                }

                override fun onFling(
                    first: MotionEvent?,
                    current: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    val document = layout ?: return false
                    scroller.fling(
                        viewport.x.toInt(),
                        viewport.y.toInt(),
                        velocityX.toInt(),
                        velocityY.toInt(),
                        min(0, (width - document.width * viewport.zoom).toInt()),
                        max(0, viewport.x.toInt()),
                        min(0, (height - document.height * viewport.zoom).toInt()),
                        0
                    )
                    postInvalidateOnAnimation()
                    return true
                }

                override fun onSingleTapUp(event: MotionEvent): Boolean {
                    pendingClick = true
                    return true
                }
            }
        )

    init {
        contentDescription = context.getString(R.string.viewer_description)
        isClickable = true
    }

    fun reset() {
        scroller.forceFinished(true)
        layout = null
        pendingPages = null
        cache.evictAll()
        requested = emptyList()
        previews = emptyList()
        fallback = emptyList()
        invalidate()
    }

    fun setPages(pages: List<PageSize>) {
        if (width <= 0 || height <= 0) {
            pendingPages = pages
            return
        }
        val previous = layout
        layout = DocumentLayout(pages)
        if (previous != null) {
            // Existing prefix coordinates stay exact while more pages are appended.
            val page = previous.pageAt((height / 2.0 - viewport.y) / viewport.zoom)
            viewport.x += (previous.left(page) - requireNotNull(layout).left(page)) * viewport.zoom
            constrain()
            return
        }
        val saved = restored
        if (saved == null) {
            viewport.zoom =
                (width.coerceAtLeast(1) / requireNotNull(layout).width).coerceIn(0.1, 8.0)
            viewport.x = 0.0
            viewport.y = 0.0
        } else {
            viewport.zoom = saved.getDouble("zoom", 1.0)
            viewport.x = width / 2.0 - saved.getDouble("centerX") * viewport.zoom
            viewport.y = height / 2.0 - saved.getDouble("centerY") * viewport.zoom
            restored = null
        }
        constrain()
    }

    fun saveViewport(): Bundle? = if (layout == null) {
        restored
    } else {
        Bundle().apply {
            putDouble("zoom", viewport.zoom)
            putDouble("centerX", (width / 2.0 - viewport.x) / viewport.zoom)
            putDouble("centerY", (height / 2.0 - viewport.y) / viewport.zoom)
            putInt(
                "page",
                requireNotNull(layout).pageAt((height / 2.0 - viewport.y) / viewport.zoom)
            )
        }
    }

    fun restoreViewport(saved: Bundle?) {
        restored = saved
    }

    fun hasRestoredViewport(): Boolean = restored != null

    fun putTile(tile: Tile, bitmap: Bitmap) {
        cache.put(tile, bitmap)
        if (requested.all { cache.get(it) != null }) fallback = emptyList()
        invalidate()
    }

    /** Local diagnostics for bounded-cache and first-visible-tile instrumentation. */
    fun renderState(): RenderState = RenderState(
        cache.size(),
        requested.size,
        requested.count { cache.get(it) != null },
        previews.size,
        previews.count { preview ->
            cache.get(preview) != null ||
                requested.filter { it.page == preview.page }.all { cache.get(it) != null }
        }
    )

    fun trimCache() {
        cache.evictAll()
        requested = emptyList()
        previews = emptyList()
        fallback = emptyList()
        updateTiles()
        invalidate()
    }

    private fun constrain() {
        layout?.let { viewport.constrain(it, width, height) }
        updateTiles()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pendingPages?.let {
            pendingPages = null
            setPages(it)
            return
        }
        if (oldw > 0 && oldh > 0) {
            viewport.x += (w - oldw) / 2.0
            viewport.y += (h - oldh) / 2.0
        }
        constrain()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestures.onTouchEvent(event)
        if (pendingClick) {
            pendingClick = false
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            viewport.x = scroller.currX.toDouble()
            viewport.y = scroller.currY.toDouble()
            constrain()
            postInvalidateOnAnimation()
        }
    }

    private fun updateTiles() {
        val document = layout ?: return
        val scale = rasterScale(viewport.zoom)
        val visible = document.visibleTiles(viewport, width, height, scale)
        if (visible != requested) {
            if (requested.firstOrNull()?.scale != scale) {
                val available = requested.filter { cache.get(it) != null }
                if (available.isNotEmpty()) fallback = available
            }
            requested = visible
            previews = visible.map { it.page }.distinct().map { index ->
                val page = document.pages[index]
                val previewScale = min(scale, 512.0 / max(page.width, page.height))
                Tile(
                    index,
                    previewScale,
                    0,
                    0,
                    ceil(page.width * previewScale).toInt(),
                    ceil(
                        page.height * previewScale
                    ).toInt()
                )
            }
            request((previews + visible).distinct().filter { cache.get(it) == null })
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.DKGRAY)
        if (layout == null) return
        for (tile in requested) drawTile(canvas, tile, null)
        for (tile in previews) cache.get(tile)?.let { drawTile(canvas, tile, it) }
        for (tile in fallback) cache.get(tile)?.let { drawTile(canvas, tile, it) }
        for (tile in requested) cache.get(tile)?.let { drawTile(canvas, tile, it) }
    }

    private fun drawTile(canvas: Canvas, tile: Tile, bitmap: Bitmap?) {
        val document = layout ?: return
        val page = document.pages[tile.page]
        val left = viewport.x + document.left(tile.page) * viewport.zoom
        val top = viewport.y + document.tops[tile.page] * viewport.zoom
        val ratio = viewport.zoom / tile.scale
        destination.set(
            (left + tile.x * ratio).toFloat(),
            (top + tile.y * ratio).toFloat(),
            (left + (tile.x + tile.width) * ratio).toFloat(),
            (top + (tile.y + tile.height) * ratio).toFloat()
        )
        val checkpoint = canvas.save()
        canvas.clipRect(
            left.toFloat(),
            top.toFloat(),
            (left + page.width * viewport.zoom).toFloat(),
            (
                top +
                    page.height * viewport.zoom
                ).toFloat()
        )
        paint.color = Color.WHITE
        if (bitmap ==
            null
        ) {
            canvas.drawRect(destination, paint)
        } else {
            canvas.drawBitmap(bitmap, null, destination, paint)
        }
        canvas.restoreToCount(checkpoint)
    }
}

data class RenderState(
    val cachedBytes: Int,
    val visibleTiles: Int,
    val readyTiles: Int,
    val visiblePages: Int,
    val coveredPages: Int
)
