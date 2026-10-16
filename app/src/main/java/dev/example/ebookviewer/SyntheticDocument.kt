package dev.example.ebookviewer

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File

/** Generated locally on a worker; shared by the demo and reproducible device probes. */
object SyntheticDocument {
    fun write(file: File) {
        val pdf = PdfDocument()
        try {
            val paint = Paint().apply {
                color = Color.BLACK
                textSize = 24f
            }
            repeat(100) { index ->
                val width = if (index % 2 == 0) 600 else 800
                val height = if (index % 2 == 0) 800 else 600
                val page = pdf.startPage(
                    PdfDocument.PageInfo.Builder(width, height, index + 1).create()
                )
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawText("P0 synthetic page ${index + 1}", 32f, 48f, paint)
                for (line in 1..15) {
                    page.canvas.drawLine(32f, 60f + line * 28, width - 32f, 60f + line * 28, paint)
                }
                pdf.finishPage(page)
            }
            file.outputStream().use(pdf::writeTo)
        } finally {
            pdf.close()
        }
    }
}
