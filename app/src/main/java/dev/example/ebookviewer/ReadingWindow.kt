package dev.example.ebookviewer

import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager

/** Stable edge-to-edge bounds keep system bars and tools from resizing the PDF viewport. */
class ReadingWindow(
    private val window: Window,
    private val lightTheme: Boolean,
    surfaceColor: Int
) {
    init {
        @Suppress("DEPRECATION")
        window.statusBarColor = surfaceColor
        @Suppress("DEPRECATION")
        window.navigationBarColor = if (lightTheme && Build.VERSION.SDK_INT < 26) {
            // API 23–25 cannot draw dark navigation icons on a light surface.
            Color.BLACK
        } else {
            surfaceColor
        }
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    @Suppress("DEPRECATION")
    fun apply(reading: Boolean) {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.apply {
                val appearance = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                setSystemBarsAppearance(if (lightTheme) appearance else 0, appearance)
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (reading) {
                    hide(
                        WindowInsets.Type.systemBars()
                    )
                } else {
                    show(WindowInsets.Type.systemBars())
                }
            }
        } else {
            var stable =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            if (lightTheme) {
                stable = stable or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                if (Build.VERSION.SDK_INT >= 26) {
                    stable = stable or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }
            }
            window.decorView.systemUiVisibility = if (reading) {
                stable or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            } else {
                stable
            }
        }
    }
}
