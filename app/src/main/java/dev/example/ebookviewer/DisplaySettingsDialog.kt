package dev.example.ebookviewer

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch

internal fun Activity.displaySettingsDialog(
    current: DisplaySettings,
    draft: Bundle? = null,
    save: (DisplaySettings, (Boolean) -> Unit) -> Unit
): EditableSettingsForm {
    val style = ReaderStyle(this)
    val saving = SettingsSaveUi(this, draft?.getBoolean("failed") == true)
    val initial = draft?.let {
        DisplaySettings(
            ReaderTheme.entries.firstOrNull { theme ->
                theme.name == it.getString("theme")
            } ?: current.theme,
            it.getBoolean("systemBrightness"),
            it.getInt("brightness").coerceIn(1, 100),
            it.getBoolean("keepScreenOn")
        )
    } ?: current
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
                    style.styleChoice(this)
                },
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = style.dp(8) }
            )
        }
        check(choices.single { it.third == initial.theme }.first)
    }
    val brightnessLabel = style.label(R.string.reading_brightness, 14f).apply {
        id = R.id.brightness_label
        text = getString(R.string.brightness_percent, initial.brightnessPercent)
    }
    val brightness = SeekBar(this).apply {
        id = R.id.reading_brightness
        max = 99
        progress = initial.brightnessPercent - 1
        contentDescription = getString(R.string.reading_brightness)
        isEnabled = !initial.systemBrightness
        minimumHeight = style.dp(48)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                brightnessLabel.text = getString(R.string.brightness_percent, progress + 1)
            }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
    }
    val systemBrightness = Switch(this).apply {
        id = R.id.system_brightness
        setText(R.string.system_brightness)
        isChecked = initial.systemBrightness
        style.styleChoice(this)
        setOnCheckedChangeListener { _, checked -> brightness.isEnabled = !checked }
    }
    val keepScreenOn = Switch(this).apply {
        id = R.id.keep_screen_on
        setText(R.string.keep_screen_on)
        isChecked = initial.keepScreenOn
        style.styleChoice(this)
    }
    val content = readerFormBody(
        style.label(R.string.app_theme, 18f, true),
        theme,
        style.label(R.string.display_description, 12f).apply { setTextColor(style.secondary) },
        style.label(R.string.reading_brightness, 18f, true),
        systemBrightness,
        brightnessLabel,
        brightness,
        keepScreenOn,
        saving.notice
    )
    fun selected() = DisplaySettings(
        choices.single { it.first == theme.checkedRadioButtonId }.third,
        systemBrightness.isChecked,
        brightness.progress + 1,
        keepScreenOn.isChecked
    )
    val dialog = readerFormDialog(
        R.string.display_settings,
        content,
        positive = R.string.apply_settings,
        onConfirm = { dialog ->
            val value = selected()
            saving.submit(dialog, content) { done -> save(value, done) }
        }
    )
    return EditableSettingsForm(dialog) {
        val value = selected()
        Bundle().apply {
            putString("theme", value.theme.name)
            putBoolean("systemBrightness", value.systemBrightness)
            putInt("brightness", value.brightnessPercent)
            putBoolean("keepScreenOn", value.keepScreenOn)
            putBoolean("failed", saving.captureFailed())
        }
    }
}
