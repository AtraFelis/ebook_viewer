package dev.example.ebookviewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.LruCache
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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

    // Opt-in aggregate instrumentation. Normal reading does not calculate coverage or retain traces.
    var onSharpnessDraw: ((SharpnessDraw) -> Unit)? = null
    private var observedLayout: DocumentLayout? = null
    private var observedZoom = Double.NaN
    private var observedX = Double.NaN
    private var observedY = Double.NaN
    private var observedWidth = 0
    private var observedHeight = 0
    private val drawSnapshot = SharpnessDraw()
    private var viewportChangedNanos = 0L
    private var viewportRevision = 0L

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
    var onReadPageText: (() -> Unit)? = null
    var onUserInteraction: (() -> Unit)? = null
    var onBackgroundColorChanged: ((Int) -> Unit)? = null
    private var outsideColor = DEFAULT_BACKGROUND_COLOR
    private var backgroundSettings = BackgroundSettings()
    private val backgroundColors = LinkedHashMap<Int, Int>()
    private var backgroundAnalyzer: PageBackgroundAnalyzer? = null
    private var backgroundCandidate = -1
    private var selectedBackgroundPage = -1
    private var analyzingPreview: Tile? = null
    private val backgroundHandler = Handler(Looper.getMainLooper())
    private val commitBackgroundPage = Runnable {
        selectedBackgroundPage = backgroundCandidate
        analyzingPreview = null
        applyOutsideColor(
            backgroundColors[selectedBackgroundPage] ?: backgroundSettings.customColor
        )
        analyzeBackgroundPreview()
    }

    fun setBackgroundSettings(settings: BackgroundSettings) {
        if (backgroundSettings == settings) return
        backgroundSettings = settings
        stopBackgroundAnalysis(clearColors = true)
        applyOutsideColor(settings.customColor)
        updateBackgroundPage()
    }

    private fun applyOutsideColor(color: Int) {
        if (outsideColor == color) return
        outsideColor = color
        onBackgroundColorChanged?.invoke(outsideColor)
        invalidate()
    }

    fun outsideBackgroundColor(): Int = outsideColor

    private fun updateBackgroundPage() {
        if (!backgroundSettings.automatic || !renderingEnabled) return
        val document = layout ?: return
        val centerY = (height / 2.0 - viewport.y) / viewport.zoom
        val page = backgroundPage(
            document,
            centerY,
            selectedBackgroundPage,
            24.0 * resources.displayMetrics.density / viewport.zoom
        )
        if (page != backgroundCandidate) {
            backgroundCandidate = page
            backgroundHandler.removeCallbacks(commitBackgroundPage)
            backgroundHandler.postDelayed(commitBackgroundPage, 120)
        } else if (page == selectedBackgroundPage) {
            analyzeBackgroundPreview()
        }
    }

    private fun analyzeBackgroundPreview() {
        if (!backgroundSettings.automatic || !renderingEnabled || selectedBackgroundPage < 0) return
        if (backgroundColors.containsKey(selectedBackgroundPage)) return
        val preview = previews.firstOrNull { it.page == selectedBackgroundPage } ?: return
        if (analyzingPreview == preview) return
        val bitmap = cache.get(preview) ?: return
        val analyzer = backgroundAnalyzer ?: PageBackgroundAnalyzer { page, color ->
            // Results are document-scoped; close invalidates queued callbacks before reset/reuse.
            backgroundColors.remove(page)
            backgroundColors[page] = color
            if (backgroundColors.size > 16) backgroundColors.remove(backgroundColors.keys.first())
            if (backgroundSettings.automatic && page == selectedBackgroundPage) {
                applyOutsideColor(color)
            }
        }.also { backgroundAnalyzer = it }
        analyzingPreview = preview
        analyzer.submit(selectedBackgroundPage, bitmap, backgroundSettings.customColor)
    }

    private fun stopBackgroundAnalysis(clearColors: Boolean) {
        backgroundHandler.removeCallbacks(commitBackgroundPage)
        backgroundAnalyzer?.close()
        backgroundAnalyzer = null
        analyzingPreview = null
        backgroundCandidate = -1
        selectedBackgroundPage = -1
        if (clearColors) backgroundColors.clear()
    }

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
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        // Only single-tap controls are supported; fast repeated taps must still toggle tools.
        gestures.setOnDoubleTapListener(null)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        val position = readingPosition() ?: return
        val count = layout?.pages?.size ?: return
        info.contentDescription = context.getString(
            R.string.accessible_page,
            position.page + 1,
            count
        )
        info.isScrollable = count > 1
        info.addAction(
            AccessibilityNodeInfo.AccessibilityAction(
                AccessibilityNodeInfo.ACTION_CLICK,
                context.getString(R.string.reading_tools_action)
            )
        )
        info.addAction(
            AccessibilityNodeInfo.AccessibilityAction(
                R.id.read_page_text,
                context.getString(R.string.read_page_text)
            )
        )
        if (position.page + 1 < count) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        }
        if (position.page > 0) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        val position = readingPosition()
        return when {
            action == R.id.read_page_text && position != null -> {
                onReadPageText?.invoke()
                true
            }
            action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD && position != null ->
                accessiblePageJump(position.page + 1)
            action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD && position != null ->
                accessiblePageJump(position.page - 1)
            else -> super.performAccessibilityAction(action, arguments)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val page = readingPosition()?.page
        return when {
            keyCode == KeyEvent.KEYCODE_PAGE_DOWN && page != null -> accessiblePageJump(page + 1)
            keyCode == KeyEvent.KEYCODE_PAGE_UP && page != null -> accessiblePageJump(page - 1)
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun accessiblePageJump(page: Int): Boolean {
        if (!goToPage(page)) return false
        // Invalidate assistive services' cached page description after an explicit page action.
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SCROLLED)
        return true
    }

    override fun onInitializeAccessibilityEvent(event: AccessibilityEvent) {
        super.onInitializeAccessibilityEvent(event)
        val position = readingPosition() ?: return
        event.isScrollable = true
        event.itemCount = layout?.pages?.size ?: 0
        event.fromIndex = position.page
        event.toIndex = position.page
    }

    fun reset() {
        stopBackgroundAnalysis(clearColors = true)
        applyOutsideColor(backgroundSettings.customColor)
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
        updateBackgroundPage()
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
        stopBackgroundAnalysis(clearColors = false)
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

    override fun onDetachedFromWindow() {
        stopBackgroundAnalysis(clearColors = false)
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateBackgroundPage()
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
        observeViewport(document)
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
        updateBackgroundPage()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(outsideColor)
        if (layout == null) return
        for (tile in requested) drawTile(canvas, tile, null)
        for (tile in previews) cache.get(tile)?.let { drawTile(canvas, tile, it) }
        for (tile in fallback) cache.get(tile)?.let { drawTile(canvas, tile, it) }
        for (tile in requested) cache.get(tile)?.let { drawTile(canvas, tile, it) }
        onSharpnessDraw?.let { observer ->
            val document = requireNotNull(layout)
            observeViewport(document)
            var total = 0.0
            var sharp = 0.0
            for (tile in requested) {
                val area = tile.visibleArea(document, viewport, width, height)
                total += area
                if (cache.get(tile) != null) sharp += area
            }
            drawSnapshot.revision = viewportRevision
            drawSnapshot.changedNanos = viewportChangedNanos
            drawSnapshot.drawnNanos = SystemClock.elapsedRealtimeNanos()
            drawSnapshot.visibleArea = total
            drawSnapshot.sharpArea = sharp
            drawSnapshot.cachedBytes = cache.size()
            observer(drawSnapshot)
        }
    }

    private fun observeViewport(document: DocumentLayout) {
        if (onSharpnessDraw == null) return
        if (document !== observedLayout ||
            viewport.zoom != observedZoom ||
            viewport.x != observedX ||
            viewport.y != observedY ||
            width != observedWidth ||
            height != observedHeight
        ) {
            observedLayout = document
            observedZoom = viewport.zoom
            observedX = viewport.x
            observedY = viewport.y
            observedWidth = width
            observedHeight = height
            viewportChangedNanos = SystemClock.elapsedRealtimeNanos()
            viewportRevision++
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

/** Borrowed callback snapshot: copy fields before retaining. onDraw, not GPU presentation. */
class SharpnessDraw(
    var revision: Long = 0,
    var changedNanos: Long = 0,
    var drawnNanos: Long = 0,
    var visibleArea: Double = 0.0,
    var sharpArea: Double = 0.0,
    var cachedBytes: Int = 0
) {
    val sharpFraction: Double get() = if (visibleArea > 0.0) sharpArea / visibleArea else 0.0
}
