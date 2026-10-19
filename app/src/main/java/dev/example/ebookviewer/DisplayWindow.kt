package dev.example.ebookviewer

import android.view.Window
import android.view.WindowManager

/** No Settings.System writes, wake locks, or device-wide brightness changes. */
class DisplayWindow(private val window: Window) {
    fun apply(settings: DisplaySettings, reading: Boolean, resumed: Boolean) {
        val state = displayWindowState(settings, reading, resumed)
        val attributes = window.attributes
        if (attributes.screenBrightness != state.brightness) {
            attributes.screenBrightness = state.brightness
            window.attributes = attributes
        }
        val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        val enabled = window.attributes.flags and flag != 0
        if (enabled != state.keepScreenOn) {
            if (state.keepScreenOn) window.addFlags(flag) else window.clearFlags(flag)
        }
    }
}
