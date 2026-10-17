package dev.example.ebookviewer

import java.io.IOException

enum class PdfFailure {
    ACCESS,
    UNAVAILABLE,
    EMPTY,
    INVALID,
    PASSWORD,
    COPY_LIMIT,
    STORAGE,
    TIMEOUT,
    READ,
    RENDER
}

/** Only a safe reason crosses into UI. Provider exception messages can contain private URIs. */
class PdfSourceException(val failure: PdfFailure) : IOException()
