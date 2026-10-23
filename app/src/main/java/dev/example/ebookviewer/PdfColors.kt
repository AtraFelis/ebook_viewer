package dev.example.ebookviewer

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/** Alpha is unchanged; this matrix is used only by the page drawing paint. */
internal fun pdfInversionMatrix(): FloatArray = floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f
)

object PdfColorsCodec {
    fun read(input: InputStream): Boolean {
        val data = DataInputStream(input)
        require(data.readInt() == 0x45564931 && data.readInt() == 1)
        val value = data.readUnsignedByte()
        require(value in 0..1 && data.read() == -1)
        return value == 1
    }

    fun write(output: OutputStream, inverted: Boolean) {
        DataOutputStream(output).apply {
            writeInt(0x45564931)
            writeInt(1)
            writeBoolean(inverted)
            flush()
        }
    }
}
