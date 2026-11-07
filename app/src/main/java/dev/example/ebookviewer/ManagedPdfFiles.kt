package dev.example.ebookviewer

import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/** Only generated tokens resolve into this private directory; external paths are never deleted. */
internal class ManagedPdfFiles(private val directory: File, private val available: () -> Long) {
    fun file(token: String): File {
        require(validToken(token))
        return File(directory, "$token.pdf")
    }

    fun copy(input: InputStream, checkCancelled: () -> Unit): String {
        if (!directory.isDirectory && !directory.mkdirs()) throw ManagedStorageException()
        val token = UUID.randomUUID().toString()
        val pending = File(directory, "$token.pending")
        val target = file(token)
        try {
            storage {
                if (available() < RESERVE_BYTES) throw ManagedStorageException()
            }
            val output = storage { FileOutputStream(pending) }
            output.use {
                val bytes = ByteArray(32 * 1024)
                var total = 0L
                while (true) {
                    checkCancelled()
                    val count = input.read(bytes)
                    if (count < 0) break
                    if (count == 0) continue
                    storage {
                        if (available() - count < RESERVE_BYTES) throw ManagedStorageException()
                        it.write(bytes, 0, count)
                    }
                    total += count
                }
                if (total == 0L) throw FileNotFoundException("Empty PDF source")
                storage { it.fd.sync() }
            }
            checkCancelled()
            storage {
                if (!pending.renameTo(target)) throw ManagedStorageException()
            }
            return token
        } finally {
            if (pending.exists() && !pending.delete()) throw ManagedStorageException()
        }
    }

    fun remove(token: String) {
        val target = file(token)
        if (target.exists() && !target.delete()) throw ManagedStorageException()
    }

    /** Repository construction precedes all imports; recover leftovers from an interrupted process. */
    fun recover(referenced: Set<String>) {
        directory.listFiles().orEmpty().forEach { entry ->
            val token = entry.nameWithoutExtension
            if (validToken(token) &&
                (entry.extension == "pending" || entry.extension == "pdf" && token !in referenced)
            ) {
                if (!entry.delete()) throw ManagedStorageException()
            }
        }
    }

    private fun <T> storage(action: () -> T): T = try {
        action()
    } catch (error: IOException) {
        throw ManagedStorageException(error)
    }

    companion object {
        private const val RESERVE_BYTES = 16L * 1024 * 1024
        fun validToken(value: String): Boolean =
            value.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
    }
}

internal class ManagedStorageException(cause: Throwable? = null) :
    IOException("PDF storage unavailable", cause)
