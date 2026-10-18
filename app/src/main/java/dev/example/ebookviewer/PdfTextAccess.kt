package dev.example.ebookviewer

import android.annotation.SuppressLint
import android.graphics.RectF
import android.graphics.pdf.PdfRendererPreV
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ext.SdkExtensions
import java.io.Closeable

data class PdfSearchHit(val page: Int, val bounds: List<RectF>)

enum class PdfSearchStatus { SEARCHING, COMPLETE, LIMITED, UNAVAILABLE, ERROR }

data class PdfSearchUpdate(
    val hits: List<PdfSearchHit>,
    val pagesSearched: Int,
    val pageCount: Int,
    val status: PdfSearchStatus
)

/** A worker-owned text companion; the existing renderer remains responsible for reading pixels. */
internal interface PdfTextAccess : Closeable {
    fun searchPage(page: Int, query: String, limit: Int): List<PdfSearchHit>

    companion object {
        const val MAX_QUERY = 256
        const val MAX_HITS = 500

        fun isSupported(): Boolean = Build.VERSION.SDK_INT >= 30 &&
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13

        /** Takes ownership on entry, including constructor failure; caller passes a duplicate. */
        fun open(descriptor: ParcelFileDescriptor): PdfTextAccess {
            try {
                check(isSupported())
                return ExtensionPdfText(descriptor)
            } catch (error: Throwable) {
                descriptor.close()
                throw error
            }
        }
    }
}

// PreV is supplied by S extension 13 on API 30–34, not only by OS API 35.
// This class is instantiated only after the SDK extension guard; older reading uses PdfRenderer.
@SuppressLint("NewApi")
private class ExtensionPdfText(descriptor: ParcelFileDescriptor) : PdfTextAccess {
    private val renderer = PdfRendererPreV(descriptor)

    override fun searchPage(page: Int, query: String, limit: Int): List<PdfSearchHit> =
        renderer.openPage(page).use { opened ->
            opened.searchText(query).asSequence().take(limit).mapNotNull { match ->
                val bounds = match.bounds.asSequence().filter {
                    it.left.isFinite() &&
                        it.top.isFinite() &&
                        it.right.isFinite() &&
                        it.bottom.isFinite() &&
                        !it.isEmpty
                }.take(32).map { RectF(it) }.toList()
                if (bounds.isEmpty()) null else PdfSearchHit(page, bounds)
            }.toList()
        }

    override fun close() = renderer.close()
}
