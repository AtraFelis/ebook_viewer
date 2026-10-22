package dev.example.ebookviewer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PdfColorsTest {
    @Test
    fun inversionFlipsAllRgbChannelsAndKeepsAlpha() {
        val matrix = pdfInversionMatrix()
        fun apply(pixel: FloatArray): FloatArray = FloatArray(4) { row ->
            (0..3).sumOf { (matrix[row * 5 + it] * pixel[it]).toDouble() }.toFloat() +
                matrix[row * 5 + 4]
        }
        assertArrayEquals(
            floatArrayOf(255f, 255f, 255f, 255f),
            apply(floatArrayOf(0f, 0f, 0f, 255f)),
            0f
        )
        assertArrayEquals(
            floatArrayOf(0f, 0f, 0f, 255f),
            apply(floatArrayOf(255f, 255f, 255f, 255f)),
            0f
        )
        val pixel = floatArrayOf(24f, 100f, 231f, 128f)
        assertArrayEquals(floatArrayOf(231f, 155f, 24f, 128f), apply(pixel), 0f)
        assertArrayEquals(pixel, apply(apply(pixel)), 0f)
    }

    @Test
    fun savedToggleRoundTripsAndUnknownOrMalformedSettingsFail() {
        for (value in listOf(true, false)) {
            val output = ByteArrayOutputStream()
            PdfColorsCodec.write(output, value)
            assertEquals(value, PdfColorsCodec.read(ByteArrayInputStream(output.toByteArray())))
            val invalid = output.toByteArray().also { it[it.lastIndex] = 2 }
            assertThrows(IllegalArgumentException::class.java) {
                PdfColorsCodec.read(ByteArrayInputStream(invalid))
            }
            assertThrows(IllegalArgumentException::class.java) {
                PdfColorsCodec.read(ByteArrayInputStream(output.toByteArray() + 0))
            }
            val unknown = output.toByteArray().also { it[7] = 2 }
            assertThrows(IllegalArgumentException::class.java) {
                PdfColorsCodec.read(ByteArrayInputStream(unknown))
            }
        }
    }
}
