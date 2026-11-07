package dev.example.ebookviewer

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.OperationCanceledException
import android.provider.OpenableColumns
import java.io.File

internal data class PdfImportResult(val added: Int, val existing: Int, val failed: Int)

/** Called on the library IO queue. Registration never renders or uploads the selected PDFs. */
internal fun importPdfs(
    store: DocumentLibrary,
    resolver: ContentResolver,
    uris: List<Uri>,
    flags: Int,
    shelf: String?,
    fallbackTitle: String,
    cache: File,
    request: PdfOpenRequest = PdfOpenRequest()
): PdfImportResult {
    val known = store.list().associateBy { it.uri }
    val unique = uris.distinct()
    var failed = 0
    val created = mutableListOf<String>()
    try {
        val records = unique.mapNotNull { uri ->
            request.throwIfCanceled()
            if (uri.scheme != "content" && uri.scheme != "file") {
                failed++
                return@mapNotNull null
            }
            try {
                val previous = known[uri.toString()]
                if (previous?.managedCopy != null) return@mapNotNull previous
                if (uri.scheme == "content" &&
                    flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0
                ) {
                    try {
                        resolver.takePersistableUriPermission(
                            uri,
                            flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: SecurityException) {
                        // The app copy remains readable after a temporary provider grant expires.
                    }
                }
                val title = if (uri.scheme == "content") {
                    resolver.query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null,
                        request.signal
                    )?.use {
                        if (it.moveToFirst()) it.getString(0)?.takeIf(String::isNotBlank) else null
                    }
                } else {
                    uri.lastPathSegment
                }
                val token = store.copies.import(resolver, uri, cache, request)
                created.add(token)
                val record = previous
                    ?: ReadingRecord(
                        uri.toString(),
                        title ?: fallbackTitle,
                        System.currentTimeMillis()
                    )
                record.copy(managedCopy = token)
            } catch (error: OperationCanceledException) {
                throw error
            } catch (_: Exception) {
                failed++
                null
            }
        }
        request.throwIfCanceled()
        store.addAll(records, shelf)
        return PdfImportResult(
            records.count { it.uri !in known },
            records.count { it.uri in known },
            failed
        )
    } finally {
        created.forEach(store::removeUnreferencedCopy)
    }
}
internal fun Intent.selectedPdfUris(): List<Uri> {
    val result = linkedSetOf<Uri>()
    clipData?.let { clip -> repeat(clip.itemCount) { clip.getItemAt(it).uri?.let(result::add) } }
    data?.let(result::add)
    return result.toList()
}
