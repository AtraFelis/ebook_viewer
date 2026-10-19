package dev.example.ebookviewer

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

data class BackgroundSettings(
    val customColor: Int = DEFAULT_BACKGROUND_COLOR,
    val automatic: Boolean = false
) {
    init {
        require(customColor ushr 24 == 255)
    }
}

object BackgroundSettingsCodec {
    fun read(input: InputStream): BackgroundSettings {
        val data = DataInputStream(input)
        require(data.readInt() == 0x45564231)
        val version = data.readInt()
        require(version in 1..2)
        val color = data.readInt()
        val mode = if (version == 2) data.readInt() else 0
        require(mode in 0..1)
        val settings = BackgroundSettings(color, mode == 1)
        require(data.read() == -1)
        return settings
    }

    fun write(output: OutputStream, settings: BackgroundSettings) {
        val data = DataOutputStream(output)
        data.writeInt(0x45564231)
        data.writeInt(2)
        data.writeInt(settings.customColor)
        data.writeInt(if (settings.automatic) 1 else 0)
        data.flush()
    }
}

const val DEFAULT_BACKGROUND_COLOR = -0xbbbbbc

fun parseBackgroundColor(value: String): Int? {
    val text = value.trim().removePrefix("#")
    if (!text.matches(Regex("[0-9a-fA-F]{6}"))) return null
    return (255 shl 24) or text.toInt(16)
}
