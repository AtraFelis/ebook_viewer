package dev.example.ebookviewer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BackgroundSettingsTest {
    @Test
    fun customChoiceRoundTrips() {
        val settings = BackgroundSettings(-0xb1328, automatic = true)
        val output = ByteArrayOutputStream()
        BackgroundSettingsCodec.write(output, settings)
        val restored = BackgroundSettingsCodec.read(ByteArrayInputStream(output.toByteArray()))
        assertEquals(settings, restored)
    }

    @Test
    fun previousVersionKeepsManualColorAndDoesNotEnableAutomaticMode() {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).apply {
            writeInt(0x45564231)
            writeInt(1)
            writeInt(-0xb1328)
        }
        assertEquals(
            BackgroundSettings(-0xb1328, automatic = false),
            BackgroundSettingsCodec.read(ByteArrayInputStream(output.toByteArray()))
        )
    }

    @Test
    fun unknownModeIsRejectedWithoutCoercingToManual() {
        val output = ByteArrayOutputStream()
        BackgroundSettingsCodec.write(output, BackgroundSettings())
        val bytes = output.toByteArray().also { it[it.lastIndex] = 2 }
        assertThrows(IllegalArgumentException::class.java) {
            BackgroundSettingsCodec.read(ByteArrayInputStream(bytes))
        }
    }

    @Test
    fun colorInputAcceptsRgbAndRejectsIncompleteOrTransparentValues() {
        assertEquals(-0xed54cc, parseBackgroundColor("#12AB34"))
        assertEquals(-0xed54cc, parseBackgroundColor(" 12ab34 "))
        for (invalid in listOf("", "#123", "#GG0000", "#0012AB34", "red")) {
            assertNull(parseBackgroundColor(invalid))
        }
        assertThrows(IllegalArgumentException::class.java) { BackgroundSettings(0) }
    }

    @Test
    fun invalidVersionTruncationAndTrailingDataAreRejected() {
        val output = ByteArrayOutputStream()
        BackgroundSettingsCodec.write(output, BackgroundSettings())
        val bytes = output.toByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            BackgroundSettingsCodec.read(ByteArrayInputStream(bytes + byteArrayOf(1)))
        }
        bytes[7] = 99
        assertThrows(IllegalArgumentException::class.java) {
            BackgroundSettingsCodec.read(ByteArrayInputStream(bytes))
        }
        assertThrows(java.io.EOFException::class.java) {
            BackgroundSettingsCodec.read(ByteArrayInputStream(bytes.take(3).toByteArray()))
        }
    }
}
