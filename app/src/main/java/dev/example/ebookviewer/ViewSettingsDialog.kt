package dev.example.ebookviewer

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import java.util.Locale

internal data class ViewSettings(
    val background: BackgroundSettings,
    val display: DisplaySettings,
    val inverted: Boolean
)

/** Independent files keep independent confirmation; failed drafts stay available for retry. */
internal class LiveSetting<T>(
    initial: T,
    private val write: (T, (Boolean) -> Unit) -> Unit,
    private val changed: () -> Unit
) {
    var confirmed = initial
        private set
    var value = initial
        private set
    var busy = false
        private set
    var failed = false
        private set

    fun stage(selected: T) {
        if (busy) return
        value = selected
        changed()
    }

    fun restore(selected: T) {
        value = selected
        failed = value != confirmed
    }

    fun choose(selected: T) {
        if (busy) return
        stage(selected)
        save()
    }

    fun save() {
        if (busy) return
        val selected = value
        busy = true
        failed = false
        changed()
        var completed = false
        val done: (Boolean) -> Unit = { success ->
            if (!completed) {
                completed = true
                busy = false
                failed = !success
                if (success) confirmed = selected
                changed()
            }
        }
        try {
            write(selected, done)
        } catch (_: Exception) {
            done(false)
        }
    }
}

/** Bounded bottom Window; only its content scrolls and the PDF viewport never resizes. */
internal class ViewSettingsDialog(
    private val activity: Activity,
    initial: ViewSettings,
    draft: Bundle? = null,
    saveBackground: (BackgroundSettings, (Boolean) -> Unit) -> Unit,
    saveDisplay: (DisplaySettings, (Boolean) -> Unit) -> Unit,
    saveInversion: (Boolean, (Boolean) -> Unit) -> Unit,
    private val preview: (ViewSettings) -> Unit,
    private val closed: (ViewSettings) -> Unit
) : Dialog(
    activity,
    if (ReaderStyle(activity).isLight) R.style.ReaderAlertLight else R.style.ReaderAlertDark
) {
    private val style = ReaderStyle(activity)
    private var binding = false
    private var ready = false
    private val backgroundState = LiveSetting(initial.background, saveBackground, ::bind)
    private val displayState = LiveSetting(initial.display, saveDisplay, ::bind)
    private val inversionState = LiveSetting(initial.inverted, saveInversion, ::bind)
    private val automatic = button(R.string.automatic_short) {
        backgroundState.choose(backgroundState.value.copy(automatic = true))
    }.apply { id = R.id.background_automatic }
    private val input = EditText(activity).apply {
        id = R.id.background_color_input
        isSingleLine = true
        hint = activity.getString(R.string.background_color_hint)
        contentDescription = hint
        filters = arrayOf(InputFilter.LengthFilter(7))
        setText(String.format(Locale.ROOT, "#%06X", initial.background.customColor and 0xffffff))
        style.styleInput(this)
    }
    private val customApply = button(R.string.apply_settings) {
        val color = parseBackgroundColor(input.text.toString())
        if (color == null) {
            input.error = activity.getString(R.string.background_invalid_color)
        } else {
            backgroundState.choose(BackgroundSettings(color, false))
        }
    }
    private val custom = LinearLayout(activity).apply {
        visibility = View.GONE
        addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        addView(customApply, LinearLayout.LayoutParams(-2, -2))
    }
    private val presetButtons = mutableListOf<Pair<Button, Int>>()
    private val presets = LinearLayout(activity).apply {
        id = R.id.view_background_presets
        gravity = Gravity.CENTER_VERTICAL
        addView(automatic, LinearLayout.LayoutParams(-2, style.dp(48)))
        for ((label, color) in listOf(
            R.string.color_white to Color.WHITE,
            R.string.color_paper to 0xfff4ecd8.toInt(),
            R.string.color_soft_green to 0xffdcebd6.toInt(),
            R.string.color_sky_blue to 0xffdeeff7.toInt(),
            R.string.color_gray to 0xff444444.toInt(),
            R.string.color_black to Color.BLACK
        )) {
            val button = Button(activity).apply {
                contentDescription = activity.getString(label)
                backgroundTintList = null
                minWidth = 0
                minimumWidth = 0
                setPadding(0, 0, 0, 0)
                setOnClickListener { backgroundState.choose(BackgroundSettings(color, false)) }
            }
            presetButtons.add(button to color)
            addView(button, LinearLayout.LayoutParams(style.dp(48), style.dp(48)))
        }
        addView(
            button(R.string.custom_color_short) {
                custom.visibility =
                    if (custom.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            },
            LinearLayout.LayoutParams(-2, style.dp(48))
        )
    }
    private val system = button(R.string.automatic_short) {
        val value = displayState.value
        displayState.choose(value.copy(systemBrightness = !value.systemBrightness))
    }.apply { id = R.id.system_brightness }
    private val brightness = SeekBar(activity).apply {
        id = R.id.reading_brightness
        max = 99
        contentDescription = activity.getString(R.string.reading_brightness)
        minimumHeight = style.dp(48)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    displayState.stage(displayState.value.copy(brightnessPercent = progress + 1))
                }
            }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = displayState.save()
        })
    }
    private val invert = Switch(activity).apply {
        id = R.id.invert_pdf
        setText(R.string.invert_pdf_label)
        style.styleChoice(this)
        background = ColorDrawable(Color.TRANSPARENT)
        setOnCheckedChangeListener { _, value -> if (!binding) inversionState.choose(value) }
    }
    private val keep = Switch(activity).apply {
        id = R.id.keep_screen_on
        setText(R.string.keep_screen_on)
        style.styleChoice(this)
        background = ColorDrawable(Color.TRANSPARENT)
        setOnCheckedChangeListener { _, value ->
            if (!binding) displayState.choose(displayState.value.copy(keepScreenOn = value))
        }
    }
    private val notice = style.label(R.string.settings_save_failed, 12f).apply {
        id = R.id.settings_save_error
    }
    private val retry = button(R.string.retry_settings_save) {
        if (backgroundState.failed) backgroundState.save()
        if (displayState.failed) displayState.save()
        if (inversionState.failed) inversionState.save()
    }.apply { id = R.id.view_settings_retry }

    init {
        draft?.let {
            val background = BackgroundSettings(it.getInt("color"), it.getBoolean("automatic"))
            backgroundState.restore(background)
            displayState.restore(
                initial.display.copy(
                    systemBrightness = it.getBoolean("system"),
                    brightnessPercent = it.getInt("brightness").coerceIn(1, 100),
                    keepScreenOn = it.getBoolean("keep")
                )
            )
            inversionState.restore(it.getBoolean("inverted"))
            input.setText(it.getString("input"))
            custom.visibility = if (it.getBoolean("custom")) View.VISIBLE else View.GONE
        }
    }

    fun capture(): Bundle = Bundle().apply {
        putInt("color", backgroundState.value.customColor)
        putBoolean("automatic", backgroundState.value.automatic)
        putBoolean("system", displayState.value.systemBrightness)
        putInt("brightness", displayState.value.brightnessPercent)
        putBoolean("keep", displayState.value.keepScreenOn)
        putBoolean("inverted", inversionState.value)
        putString("input", input.text.toString())
        putBoolean("custom", custom.visibility == View.VISIBLE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        val header = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(
                style.label(R.string.view_settings, 16f, true),
                LinearLayout.LayoutParams(0, -2, 1f)
            )
            addView(
                ImageButton(activity).apply {
                    contentDescription = activity.getString(R.string.close_settings)
                    setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    imageTintList = android.content.res.ColorStateList.valueOf(style.accent)
                    background = ColorDrawable(Color.TRANSPARENT)
                    setOnClickListener { dismiss() }
                },
                LinearLayout.LayoutParams(style.dp(48), style.dp(48))
            )
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(style.label(R.string.background_settings, 12f))
            addView(HorizontalScrollView(activity).apply { addView(presets) })
            addView(custom)
            addView(
                LinearLayout(activity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(style.label(R.string.brightness_short, 14f))
                    addView(brightness, LinearLayout.LayoutParams(0, style.dp(48), 1f))
                    addView(system)
                }
            )
            addView(invert, LinearLayout.LayoutParams(-1, -2))
            addView(keep, LinearLayout.LayoutParams(-1, -2))
            addView(notice)
            addView(retry)
        }
        val root = object : LinearLayout(activity) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                // Window metrics include IME-adjusted available height on supported devices.
                val available = activity.window.decorView.height
                    .takeIf { it > 0 } ?: activity.resources.displayMetrics.heightPixels
                val limit = minOf(
                    style.dp(360),
                    (available * 0.65).toInt(),
                    MeasureSpec.getSize(heightMeasureSpec)
                )
                super.onMeasure(
                    widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST)
                )
            }
        }.apply {
            id = R.id.view_settings_panel
            orientation = LinearLayout.VERTICAL
            setPadding(style.dp(16), 0, style.dp(16), style.dp(8))
            setBackgroundColor(style.surface)
            addView(header, LinearLayout.LayoutParams(-1, -2))
            addView(
                ScrollView(activity).apply { addView(content) },
                LinearLayout.LayoutParams(-1, -2)
            )
        }
        setContentView(root)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            setDimAmount(0.15f)
        }
        setCanceledOnTouchOutside(true)
        setOnDismissListener {
            ready = false
            closed(
                ViewSettings(
                    backgroundState.confirmed,
                    displayState.confirmed,
                    inversionState.confirmed
                )
            )
        }
        ready = true
        bind()
    }

    private fun bind() {
        if (!ready || activity.isDestroyed || activity.isFinishing) return
        binding = true
        automatic.isEnabled = !backgroundState.busy
        automatic.isSelected = backgroundState.value.automatic
        automatic.setTextColor(
            if (backgroundState.value.automatic) style.accent else style.secondary
        )
        for ((button, color) in presetButtons) {
            button.isEnabled = !backgroundState.busy
            val selected =
                !backgroundState.value.automatic && backgroundState.value.customColor == color
            button.isSelected = selected
            button.background = android.graphics.drawable.InsetDrawable(
                style.shape(color, 18).apply {
                    setStroke(
                        style.dp(if (selected) 3 else 1),
                        if (selected) style.accent else style.border
                    )
                },
                style.dp(6)
            )
        }
        input.isEnabled = !backgroundState.busy
        customApply.isEnabled = !backgroundState.busy
        brightness.progress = displayState.value.brightnessPercent - 1
        brightness.isEnabled = !displayState.busy && !displayState.value.systemBrightness
        system.isEnabled = !displayState.busy
        system.isSelected = displayState.value.systemBrightness
        system.setTextColor(
            if (displayState.value.systemBrightness) style.accent else style.secondary
        )
        keep.isEnabled = !displayState.busy
        keep.isChecked = displayState.value.keepScreenOn
        invert.isEnabled = !inversionState.busy
        invert.isChecked = inversionState.value
        val failed = backgroundState.failed || displayState.failed || inversionState.failed
        notice.visibility = if (failed) View.VISIBLE else View.GONE
        retry.visibility = notice.visibility
        binding = false
        // A focused Dialog owns its Window brightness while the Activity remains underneath.
        window?.let { window ->
            window.attributes = window.attributes.apply {
                screenBrightness = if (displayState.value.systemBrightness) {
                    -1f
                } else {
                    displayState.value.brightnessPercent / 100f
                }
            }
            if (displayState.value.keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        preview(ViewSettings(backgroundState.value, displayState.value, inversionState.value))
    }

    private fun button(label: Int, click: () -> Unit): Button =
        style.button(label, onClick = click).apply { background = style.plainButtonBackground() }
}
