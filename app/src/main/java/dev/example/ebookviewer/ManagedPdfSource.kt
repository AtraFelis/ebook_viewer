package dev.example.ebookviewer

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.StatFs
import java.io.File

internal fun managedPdfFiles(directory: File): ManagedPdfFiles = ManagedPdfFiles(directory) {
    StatFs(requireNotNull(directory.parentFile).path).availableBytes
}

/** The existing source adapter owns provider cancellation, pipe limits and descriptor cleanup. */
internal fun ManagedPdfFiles.import(
    resolver: ContentResolver,
    uri: Uri,
    cache: File,
    request: PdfOpenRequest
): String {
    val descriptor = resolver.openFileDescriptor(uri, "r", request.signal)
        ?: throw PdfSourceException(PdfFailure.UNAVAILABLE)
    val prepared = PdfDocumentSource(cache).prepare(descriptor, request)
    return ParcelFileDescriptor.AutoCloseInputStream(prepared).use {
        copy(it, request::throwIfCanceled)
    }
}
