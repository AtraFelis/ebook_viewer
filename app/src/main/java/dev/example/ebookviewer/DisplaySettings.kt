package dev.example.ebookviewer

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

enum class ReaderTheme { SYSTEM, LIGHT, DARK }

data class DisplaySettings(
    val theme: ReaderTheme = ReaderTheme.SYSTEM,
    val systemBrightness: Boolean = true,
    val brightnessPercent: Int = 50,
    val keepScreenOn: Boolean = false
) {
    init {
        require(brightnessPercent in 1..100)
    }
}

fun ReaderTheme.isDark(systemDark: Boolean): Boolean = when (this) {
    ReaderTheme.SYSTEM -> systemDark
    ReaderTheme.LIGHT -> false
    ReaderTheme.DARK -> true
}

data class DisplayWindowState(val brightness: Float = -1f, val keepScreenOn: Boolean = false)

/** Window overrides belong only to a ready reader in the resumed Activity. */
fun displayWindowState(
    settings: DisplaySettings,
    reading: Boolean,
    resumed: Boolean
): DisplayWindowState = if (reading && resumed) {
    DisplayWindowState(
        if (settings.systemBrightness) -1f else settings.brightnessPercent / 100f,
        settings.keepScreenOn
    )
} else {
    DisplayWindowState()
}

object DisplaySettingsCodec {
    fun read(input: InputStream): DisplaySettings {
        val data = DataInputStream(input)
        require(data.readInt() == 0x45564431 && data.readInt() == 1)
        val theme = data.readInt()
        val system = data.readInt()
        val percent = data.readInt()
        val keep = data.readInt()
        require(theme in ReaderTheme.entries.indices && system in 0..1 && keep in 0..1)
        val settings = DisplaySettings(ReaderTheme.entries[theme], system == 1, percent, keep == 1)
        require(data.read() == -1)
        return settings
    }

    fun write(output: OutputStream, settings: DisplaySettings) {
        DataOutputStream(output).apply {
            writeInt(0x45564431)
            writeInt(1)
            writeInt(settings.theme.ordinal)
            writeInt(if (settings.systemBrightness) 1 else 0)
            writeInt(settings.brightnessPercent)
            writeInt(if (settings.keepScreenOn) 1 else 0)
            flush()
        }
    }
}
