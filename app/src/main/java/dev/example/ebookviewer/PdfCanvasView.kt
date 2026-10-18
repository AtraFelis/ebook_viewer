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
    private var tapAllowed = true
    private var hadMultiplePointers = false
    private var layout: DocumentLayout? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var requested = emptyList<Tile>()
    private var previews = emptyList<Tile>()

    // Keys only: fallback bitmaps remain inside the same byte-bounded cache.
    private var fallback = emptyList<Tile>()
    private var restored: Bundle? = null
    private var pendingPages: List<PageSize>? = null
    private var pageLayout = PageLayoutSettings()
    private var renderingEnabled = true
    private var searchHit: PdfSearchHit? = null
    private val searchPaint = Paint().apply { color = Color.argb(100, 255, 190, 0) }

    fun showSearchHit(hit: PdfSearchHit?): Boolean {
        if (hit == null) {
            searchHit = null
            invalidate()
            return true
        }
        val document = layout ?: return false
        if (hit.page !in document.pages.indices || hit.bounds.isEmpty()) return false
        val bounds = hit.bounds.first()
        val page = document.pages[hit.page]
        if (!goToPage(hit.page, (bounds.centerY() / page.height).toDouble().coerceIn(0.0, 1.0))) {
            return false
        }
        val left = viewport.x +
            (document.left(hit.page) + bounds.left * document.scale(hit.page)) * viewport.zoom
        val right = viewport.x +
            (document.left(hit.page) + bounds.right * document.scale(hit.page)) * viewport.zoom
        if (left < 0) {
            viewport.x -= left
        } else if (right > width) {
            viewport.x -= right - width
        }
        searchHit = hit
        constrain()
        return true
    }

    fun pageLayoutSettings(): PageLayoutSettings = pageLayout

    fun setPageLayoutSettings(settings: PageLayoutSettings) {
        if (pageLayout == settings) return
        val saved = saveViewport()
        pageLayout = settings
        val pages = layout?.pages ?: return
        scroller.forceFinished(true)
        layout = null
        restored = saved
        setPages(pages)
    }
    var onPositionChanged: (() -> Unit)? = null
    var onUserInteraction: (() -> Unit)? = null
    var onBackgroundColorChanged: ((Int) -> Unit)? = null
    private var outsideColor = DEFAULT_BACKGROUND_COLOR

    fun setBackgroundSettings(settings: BackgroundSettings) {
        if (outsideColor == settings.customColor) return
        outsideColor = settings.customColor
        onBackgroundColorChanged?.invoke(outsideColor)
        invalidate()
    }

    fun outsideBackgroundColor(): Int = outsideColor

    fun goToPage(page: Int, fractionY: Double? = null): Boolean {
        val document = layout ?: return false
        if (page !in document.pages.indices) return false
        scroller.forceFinished(true)
        viewport.moveToPage(document, page, fractionY, width, height)
        constrain()
        return true
    }

    private val cache = object : LruCache<Tile, Bitmap>(64 * 1024 * 1024) {
        override fun sizeOf(key: Tile, value: Bitmap): Int = value.allocationByteCount
    }
    private val scroller = OverScroller(context)
    private val scaleDetector =
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val document = layout ?: return false
                    scroller.forceFinished(true)
                    viewport.zoomAt(
                        detector.scaleFactor.toDouble(),
                        detector.focusX.toDouble(),
                        detector.focusY.toDouble(),
                        ZoomLimits.gestureMinimum(document.width, width, height)
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
                    pendingClick = tapAllowed &&
                        !hadMultiplePointers &&
                        event.x in width / 3f..width * 2f / 3f &&
                        event.y in height / 3f..height * 2f / 3f
                    return true
                }
            }
        )

    init {
        contentDescription = context.getString(R.string.viewer_description)
        isClickable = true
        // Only single-tap controls are supported; fast repeated taps must still toggle tools.
        gestures.setOnDoubleTapListener(null)
    }

    fun reset() {
        searchHit = null
        scroller.forceFinished(true)
        request(emptyList())
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
        layout = DocumentLayout(pages, pageLayout)
        if (previous != null) {
            // Uniform widths may change when a wider page is discovered. Reproject the anchor.
            val centerX = (width / 2.0 - viewport.x) / viewport.zoom
            val centerY = (height / 2.0 - viewport.y) / viewport.zoom
            val page = previous.pageAt(centerY)
            val fractionX = (centerX - previous.left(page)) / previous.pageWidth(page)
            val offsetY = centerY - previous.tops[page]
            val fractionY = (offsetY / previous.pageHeight(page)).coerceIn(0.0, 1.0)
            val gapOffset = (offsetY - previous.pageHeight(page)).coerceAtLeast(0.0)
            val document = requireNotNull(layout)
            viewport.x = width / 2.0 -
                (document.left(page) + fractionX * document.pageWidth(page)) * viewport.zoom
            val projectedY = document.tops[page] + fractionY * document.pageHeight(page) + gapOffset
            viewport.y = height / 2.0 - projectedY * viewport.zoom
            constrain()
            return
        }
        val saved = restored
        if (saved == null) {
            viewport.zoom =
                (width.coerceAtLeast(1) / requireNotNull(layout).width)
                    .coerceIn(ZoomLimits.MIN_SAVED, ZoomLimits.MAX)
            viewport.x = 0.0
            viewport.y = 0.0
        } else {
            viewport.zoom = saved.getDouble("zoom", 1.0)
                .coerceIn(ZoomLimits.MIN_SAVED, ZoomLimits.MAX)
            val document = requireNotNull(layout)
            val page = saved.getInt("page").coerceIn(document.pages.indices)
            val centerX = if (saved.containsKey("fractionX")) {
                document.left(page) + saved.getDouble("fractionX") * document.pageWidth(page)
            } else {
                saved.getDouble("centerX")
            }
            val centerY = if (saved.containsKey("fractionY")) {
                document.tops[page] + saved.getDouble("fractionY") * document.pageHeight(page)
            } else {
                saved.getDouble("centerY")
            }
            viewport.x = width / 2.0 - centerX * viewport.zoom
            viewport.y = height / 2.0 - centerY * viewport.zoom
            restored = null
        }
        constrain()
    }

    fun saveViewport(): Bundle? = if (layout == null) {
        restored
    } else {
        Bundle().apply {
            putString("pageSizeMode", pageLayout.mode.name)
            putInt("pageGap", pageLayout.gap)
            putDouble("zoom", viewport.zoom)
            putDouble("centerX", (width / 2.0 - viewport.x) / viewport.zoom)
            putDouble("centerY", (height / 2.0 - viewport.y) / viewport.zoom)
            putInt(
                "page",
                requireNotNull(layout).pageAt((height / 2.0 - viewport.y) / viewport.zoom)
            )
            readingPosition()?.let {
                putDouble("fractionX", it.x)
                putDouble("fractionY", it.y)
            }
        }
    }

    fun readingPosition(): ReadingPosition? {
        val document = layout ?: return null
        val centerX = (width / 2.0 - viewport.x) / viewport.zoom
        val centerY = (height / 2.0 - viewport.y) / viewport.zoom
        val page = document.pageAt(centerY)
        return ReadingPosition(
            page,
            (centerX - document.left(page)) / document.pageWidth(page),
            ((centerY - document.tops[page]) / document.pageHeight(page)).coerceIn(0.0, 1.0),
            viewport.zoom
        )
    }

    fun restorePosition(position: ReadingPosition?) {
        restoreViewport(
            position?.let {
                Bundle().apply {
                    putInt("page", it.page)
                    putDouble("fractionX", it.x)
                    putDouble("fractionY", it.y)
                    putDouble("zoom", it.zoom)
                }
            }
        )
    }

    fun restoreViewport(saved: Bundle?) {
        restored = saved
        pageLayout = if (saved?.containsKey("pageSizeMode") == true) {
            PageLayoutSettings(
                PageSizeMode.valueOf(requireNotNull(saved.getString("pageSizeMode"))),
                saved.getInt("pageGap")
            )
        } else {
            pageLayout
        }
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
        request(emptyList())
        cache.evictAll()
        requested = emptyList()
        previews = emptyList()
        fallback = emptyList()
        updateTiles()
        invalidate()
    }

    /** Keep the document/viewport while releasing pixels and stopping work for an invisible window. */
    fun setRenderingEnabled(enabled: Boolean) {
        if (renderingEnabled == enabled) return
        renderingEnabled = enabled
        if (enabled) {
            updateTiles()
            invalidate()
        } else {
            scroller.forceFinished(true)
            trimCache()
        }
    }

    private fun constrain() {
        layout?.let { viewport.constrain(it, width, height) }
        updateTiles()
        invalidate()
        if (layout != null) onPositionChanged?.invoke()
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
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                onUserInteraction?.invoke()
                tapAllowed = scroller.isFinished
                hadMultiplePointers = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> hadMultiplePointers = true
        }
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
        if (!renderingEnabled) return
        val document = layout ?: return
        val scale = rasterScale(viewport.zoom)
        val visible = document.visibleTiles(viewport, width, height, scale)
        if (visible != requested) {
            if (requested.firstOrNull()?.scale != visible.firstOrNull()?.scale) {
                val available = requested.filter { cache.get(it) != null }
                if (available.isNotEmpty()) fallback = available
            }
            requested = visible
            previews = visible.map { it.page }.distinct().map { index ->
                val page = document.pages[index]
                val previewScale = min(
                    scale * document.scale(index),
                    512.0 / max(page.width, page.height)
                )
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
        canvas.drawColor(outsideColor)
        if (layout == null) return
        for (tile in requested) drawTile(canvas, tile, null)
        for (tile in previews) cache.get(tile)?.let { drawTile(canvas, tile, it) }
        for (tile in fallback) cache.get(tile)?.let { drawTile(canvas, tile, it) }
        for (tile in requested) cache.get(tile)?.let { drawTile(canvas, tile, it) }
        val hit = searchHit ?: return
        val document = layout ?: return
        if (hit.page !in document.pages.indices) return
        val scale = viewport.zoom * document.scale(hit.page)
        val left = viewport.x + document.left(hit.page) * viewport.zoom
        val top = viewport.y + document.tops[hit.page] * viewport.zoom
        for (bounds in hit.bounds) {
            canvas.drawRect(
                (left + bounds.left * scale).toFloat(),
                (top + bounds.top * scale).toFloat(),
                (left + bounds.right * scale).toFloat(),
                (top + bounds.bottom * scale).toFloat(),
                searchPaint
            )
        }
    }

    private fun drawTile(canvas: Canvas, tile: Tile, bitmap: Bitmap?) {
        val document = layout ?: return
        val left = viewport.x + document.left(tile.page) * viewport.zoom
        val top = viewport.y + document.tops[tile.page] * viewport.zoom
        val ratio = viewport.zoom * document.scale(tile.page) / tile.scale
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
            (left + document.pageWidth(tile.page) * viewport.zoom).toFloat(),
            (
                top +
                    document.pageHeight(tile.page) * viewport.zoom
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
