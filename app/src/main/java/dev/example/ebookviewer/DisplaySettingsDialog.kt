package dev.example.ebookviewer

import android.app.Activity
import android.app.AlertDialog
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

fun Activity.displaySettingsDialog(
    current: DisplaySettings,
    save: (DisplaySettings) -> Unit
): AlertDialog {
    val choices = listOf(
        Triple(R.id.theme_system, R.string.theme_system, ReaderTheme.SYSTEM),
        Triple(R.id.theme_light, R.string.theme_light, ReaderTheme.LIGHT),
        Triple(R.id.theme_dark, R.string.theme_dark, ReaderTheme.DARK)
    )
    val theme = RadioGroup(this).apply {
        for ((viewId, label, _) in choices) {
            addView(
                RadioButton(this@displaySettingsDialog).apply {
                    id = viewId
                    setText(label)
                }
            )
        }
        check(choices.single { it.third == current.theme }.first)
    }
    val brightnessLabel = TextView(this).apply {
        id = R.id.brightness_label
        text = getString(R.string.brightness_percent, current.brightnessPercent)
    }
    val brightness = SeekBar(this).apply {
        id = R.id.reading_brightness
        max = 99
        progress = current.brightnessPercent - 1
        contentDescription = getString(R.string.reading_brightness)
        isEnabled = !current.systemBrightness
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                brightnessLabel.text = getString(R.string.brightness_percent, progress + 1)
            }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
    }
    val systemBrightness = CheckBox(this).apply {
        id = R.id.system_brightness
        setText(R.string.system_brightness)
        isChecked = current.systemBrightness
        setOnCheckedChangeListener { _, checked -> brightness.isEnabled = !checked }
    }
    val keepScreenOn = CheckBox(this).apply {
        id = R.id.keep_screen_on
        setText(R.string.keep_screen_on)
        isChecked = current.keepScreenOn
    }
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val padding = (16 * resources.displayMetrics.density).toInt()
        setPadding(padding, 0, padding, padding)
        addView(TextView(this@displaySettingsDialog).apply { setText(R.string.app_theme) })
        addView(theme)
        addView(
            TextView(this@displaySettingsDialog).apply {
                setText(R.string.display_description)
            }
        )
        addView(systemBrightness)
        addView(brightnessLabel)
        addView(brightness)
        addView(keepScreenOn)
    }
    return AlertDialog.Builder(this)
        .setTitle(R.string.display_settings)
        .setView(ScrollView(this).apply { addView(content) })
        .setPositiveButton(R.string.apply_settings) { _, _ ->
            save(
                DisplaySettings(
                    choices.single { it.first == theme.checkedRadioButtonId }.third,
                    systemBrightness.isChecked,
                    brightness.progress + 1,
                    keepScreenOn.isChecked
                )
            )
        }
        .setNegativeButton(android.R.string.cancel, null)
        .create()
}
