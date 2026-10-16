package dev.example.ebookviewer

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

data class BackgroundSettings(val customColor: Int = DEFAULT_BACKGROUND_COLOR) {
    init {
        require(customColor ushr 24 == 255)
    }
}

object BackgroundSettingsCodec {
    fun read(input: InputStream): BackgroundSettings {
        val data = DataInputStream(input)
        require(data.readInt() == 0x45564231 && data.readInt() == 1)
        val settings = BackgroundSettings(data.readInt())
        require(data.read() == -1)
        return settings
    }

    fun write(output: OutputStream, settings: BackgroundSettings) {
        val data = DataOutputStream(output)
        data.writeInt(0x45564231)
        data.writeInt(1)
        data.writeInt(settings.customColor)
        data.flush()
    }
}

const val DEFAULT_BACKGROUND_COLOR = -0xbbbbbc

fun parseBackgroundColor(value: String): Int? {
    val text = value.trim().removePrefix("#")
    if (!text.matches(Regex("[0-9a-fA-F]{6}"))) return null
    return (255 shl 24) or text.toInt(16)
}
