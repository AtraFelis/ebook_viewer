package dev.example.ebookviewer

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

internal data class PdfImportResult(
    val added: Int,
    val existing: Int,
    val failed: Int,
    val temporary: Int
)

/** Called on the library IO queue. Registration never renders or uploads the selected PDFs. */
internal fun importPdfs(
    store: DocumentLibrary,
    resolver: ContentResolver,
    uris: List<Uri>,
    flags: Int,
    shelf: String?,
    fallbackTitle: String
): PdfImportResult {
    val known = store.list().map { it.uri }.toSet()
    val unique = uris.distinct()
    var failed = 0
    var temporary = 0
    val records = unique.mapNotNull { uri ->
        if (uri.scheme != "content" && uri.scheme != "file") {
            failed++
            return@mapNotNull null
        }
        try {
            if (uri.scheme == "content" &&
                flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0
            ) {
                try {
                    resolver.takePersistableUriPermission(
                        uri,
                        flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // Providers may grant only temporary access; include that in the result.
                }
            }
            val title = if (uri.scheme == "content") {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0)?.takeIf(String::isNotBlank) else null
                }
            } else {
                uri.lastPathSegment
            }
            val durable = uri.scheme == "file" ||
                resolver.persistedUriPermissions.any {
                    it.uri == uri && it.isReadPermission
                }
            if (!durable) temporary++
            ReadingRecord(uri.toString(), title ?: fallbackTitle, System.currentTimeMillis())
        } catch (_: Exception) {
            failed++
            null
        }
    }
    store.addAll(records, shelf)
    return PdfImportResult(
        records.count { it.uri !in known },
        records.count { it.uri in known },
        failed,
        temporary
    )
}

internal fun Intent.selectedPdfUris(): List<Uri> {
    val result = linkedSetOf<Uri>()
    clipData?.let { clip -> repeat(clip.itemCount) { clip.getItemAt(it).uri?.let(result::add) } }
    data?.let(result::add)
    return result.toList()
}
