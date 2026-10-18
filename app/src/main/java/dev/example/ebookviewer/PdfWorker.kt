package dev.example.ebookviewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** All renderer/descriptor/page ownership stays on one worker. Native render is not interruptible. */
class PdfWorker(
    private val onLoaded: (List<PageSize>) -> Unit,
    private val onTile: (Tile, Bitmap) -> Unit,
    private val onError: (PdfFailure) -> Unit,
    private val onPageCount: (Int) -> Unit = {}
) {
    private data class Request(
        val document: Long,
        val generation: Long,
        val epoch: Long,
        val tiles: List<Tile>
    )

    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val cancellation = Executors.newSingleThreadExecutor()
    private val opening = AtomicReference<PdfOpenRequest?>()
    private val main = Handler(Looper.getMainLooper())
    private val document = AtomicLong()
    private val generation = AtomicLong()
    private val tileEpoch = AtomicLong()
    private val pending = AtomicReference<Request?>()
    private val desired = AtomicReference<Request?>()
    private val scheduled = AtomicBoolean()
    private var renderer: PdfRenderer? = null
    private var closed = false

    fun open(source: () -> ParcelFileDescriptor) {
        openForRestore(false, source)
    }

    fun openForRestore(restore: Boolean, source: () -> ParcelFileDescriptor) {
        openCancelable(restore) { source() }
    }

    fun openCancelable(restore: Boolean, source: (PdfOpenRequest) -> ParcelFileDescriptor) {
        check(!closed)
        val id = document.incrementAndGet()
        val openRequest = PdfOpenRequest()
        opening.getAndSet(openRequest)?.cancel(cancellation)
        cancelTiles()
        executor.execute {
            if (document.get() != id) return@execute
            renderer?.close()
            renderer = null
            try {
                openRequest.throwIfCanceled()
                val descriptor = source(openRequest)
                if (document.get() != id) {
                    descriptor.close()
                    return@execute
                }
                val opened = try {
                    PdfRenderer(descriptor)
                } catch (_: SecurityException) {
                    descriptor.close()
                    throw PdfSourceException(PdfFailure.PASSWORD)
                } catch (error: Exception) {
                    descriptor.close()
                    throw PdfSourceException(PdfFailure.INVALID)
                }
                renderer = opened
                openRequest.throwIfCanceled()
                if (opened.pageCount == 0) throw PdfSourceException(PdfFailure.EMPTY)
                val pageCount = opened.pageCount
                main.post { if (document.get() == id) onPageCount(pageCount) }
                val pages = ArrayList<PageSize>()
                // A zoomed-out restored viewport can span many pages; use exact bounds before clamping.
                val initialCount = if (restore) opened.pageCount else minOf(4, opened.pageCount)
                for (index in 0 until initialCount) {
                    if (document.get() != id) return@execute
                    openRequest.throwIfCanceled()
                    opened.openPage(index).use { pages.add(PageSize(it.width, it.height)) }
                }
                main.post { if (document.get() == id) onLoaded(pages) }
                scanMore(id, opened, pages)
            } catch (error: Exception) {
                renderer?.close()
                renderer = null
                val failure = when (error) {
                    is PdfSourceException -> error.failure
                    is SecurityException -> PdfFailure.ACCESS
                    is FileNotFoundException -> PdfFailure.UNAVAILABLE
                    else -> PdfFailure.READ
                }
                main.post { if (document.get() == id) onError(failure) }
            } finally {
                opening.compareAndSet(openRequest, null)
            }
        }
    }

    private fun scanMore(id: Long, opened: PdfRenderer, previous: List<PageSize>) {
        if (previous.size == opened.pageCount) return
        executor.schedule({
            if (document.get() != id || renderer !== opened) return@schedule
            try {
                val pages = previous.toMutableList()
                repeat(minOf(2, opened.pageCount - pages.size)) {
                    opened.openPage(pages.size).use { page ->
                        pages.add(PageSize(page.width, page.height))
                    }
                }
                if (document.get() != id) return@schedule
                main.post { if (document.get() == id) onLoaded(pages) }
                scanMore(id, opened, pages)
            } catch (_: Exception) {
                main.post { if (document.get() == id) onError(PdfFailure.RENDER) }
            }
        }, 25, TimeUnit.MILLISECONDS)
    }

    fun request(tiles: List<Tile>) {
        if (closed) return
        if (tiles.isEmpty()) {
            cancelTiles()
            return
        }
        val request = Request(document.get(), generation.incrementAndGet(), tileEpoch.get(), tiles)
        desired.set(request)
        pending.set(request)
        schedule()
    }

    /** Invalidate queued deliveries too, even if the same tile is requested again after a trim. */
    fun cancelTiles() {
        tileEpoch.incrementAndGet()
        generation.incrementAndGet()
        pending.set(null)
        desired.set(null)
    }

    private fun schedule() {
        if (!scheduled.compareAndSet(false, true)) return
        executor.execute {
            try {
                val request = pending.getAndSet(null) ?: return@execute
                val current = renderer ?: return@execute
                for (tile in request.tiles.take(4)) {
                    if (!isCurrent(request)) break
                    val bitmap = Bitmap.createBitmap(
                        tile.width,
                        tile.height,
                        Bitmap.Config.ARGB_8888
                    )
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        current.openPage(tile.page).use { page ->
                            val matrix = Matrix().apply {
                                setScale(tile.scale.toFloat(), tile.scale.toFloat())
                                postTranslate(-tile.x.toFloat(), -tile.y.toFloat())
                            }
                            page.render(
                                bitmap,
                                Rect(0, 0, tile.width, tile.height),
                                matrix,
                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                            )
                        }
                        if (!isRelevant(request, tile)) {
                            bitmap.recycle()
                            break
                        }
                        main.post {
                            if (isRelevant(
                                    request,
                                    tile
                                )
                            ) {
                                onTile(tile, bitmap)
                            } else {
                                bitmap.recycle()
                            }
                        }
                    } catch (_: Exception) {
                        bitmap.recycle()
                        main.post { if (isCurrent(request)) onError(PdfFailure.RENDER) }
                        break
                    }
                }
                if (isCurrent(request) && request.tiles.size > 4) {
                    pending.compareAndSet(null, request.copy(tiles = request.tiles.drop(4)))
                }
            } finally {
                scheduled.set(false)
                if (pending.get() != null) schedule()
            }
        }
    }

    private fun isCurrent(request: Request): Boolean =
        request.document == document.get() && request.generation == generation.get()

    private fun isRelevant(request: Request, tile: Tile): Boolean {
        val latest = desired.get() ?: return false
        return request.document == document.get() &&
            request.epoch == tileEpoch.get() &&
            latest.document == request.document &&
            tile in latest.tiles
    }

    fun closeDocument() {
        if (closed) return
        document.incrementAndGet()
        opening.getAndSet(null)?.cancel(cancellation)
        cancelTiles()
        // Close after the active native call, never concurrently with render.
        executor.execute {
            renderer?.close()
            renderer = null
        }
    }

    fun close() {
        if (closed) return
        closeDocument()
        closed = true
        executor.shutdown()
        cancellation.shutdown()
    }
}
