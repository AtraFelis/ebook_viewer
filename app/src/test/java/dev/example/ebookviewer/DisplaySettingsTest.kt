package dev.example.ebookviewer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplaySettingsTest {
    @Test
    fun allModesAndBrightnessEndpointsRoundTrip() {
        for (theme in ReaderTheme.entries) {
            for (system in listOf(false, true)) {
                for (percent in listOf(1, 50, 100)) {
                    for (keep in listOf(false, true)) {
                        val settings = DisplaySettings(theme, system, percent, keep)
                        assertEquals(settings, decode(encode(settings)))
                    }
                }
            }
        }
    }

    @Test
    fun malformedSettingsAreRejectedInsteadOfSilentlyReset() {
        val valid = encode(DisplaySettings())
        for ((offset, invalid) in listOf(3 to 0, 7 to 2, 11 to 3, 15 to 2, 19 to 0, 23 to 2)) {
            val bytes = valid.copyOf().apply { this[offset] = invalid.toByte() }
            assertThrows(IllegalArgumentException::class.java) { decode(bytes) }
        }
        assertThrows(IllegalArgumentException::class.java) { decode(valid + byteArrayOf(0)) }
        for (length in valid.indices) {
            assertThrows(EOFException::class.java) { decode(valid.copyOf(length)) }
        }
        for (percent in listOf(0, 101)) {
            assertThrows(IllegalArgumentException::class.java) {
                DisplaySettings(brightnessPercent = percent)
            }
        }
    }

    @Test
    fun themeSelectionFollowsSystemOnlyInSystemMode() {
        assertFalse(ReaderTheme.SYSTEM.isDark(false))
        assertTrue(ReaderTheme.SYSTEM.isDark(true))
        assertFalse(ReaderTheme.LIGHT.isDark(true))
        assertTrue(ReaderTheme.DARK.isDark(false))
    }

    @Test
    fun brightnessAndKeepAwakeNeverLeakOutsideResumedReading() {
        val settings = DisplaySettings(
            systemBrightness = false,
            brightnessPercent = 25,
            keepScreenOn = true
        )
        assertEquals(DisplayWindowState(0.25f, true), displayWindowState(settings, true, true))
        for ((reading, resumed) in listOf(false to false, true to false, false to true)) {
            assertEquals(DisplayWindowState(), displayWindowState(settings, reading, resumed))
        }
        assertEquals(
            DisplayWindowState(-1f, true),
            displayWindowState(settings.copy(systemBrightness = true), true, true)
        )
        assertEquals(
            DisplayWindowState(0.01f, false),
            displayWindowState(
                DisplaySettings(systemBrightness = false, brightnessPercent = 1),
                true,
                true
            )
        )
    }

    private fun encode(settings: DisplaySettings): ByteArray = ByteArrayOutputStream().also {
        DisplaySettingsCodec.write(it, settings)
    }.toByteArray()

    private fun decode(bytes: ByteArray): DisplaySettings =
        DisplaySettingsCodec.read(ByteArrayInputStream(bytes))
}
