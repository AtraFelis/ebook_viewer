package dev.example.ebookviewer

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.SeekBar

internal fun Activity.pageCropDialog(
    current: PageLayoutSettings,
    draft: Bundle? = null,
    apply: (PageLayoutSettings, (Boolean) -> Unit) -> Unit
): EditableSettingsForm {
    val style = ReaderStyle(this)
    val saving = SettingsSaveUi(this, draft?.getBoolean("failed") == true)
    val crop = current.crop
    val values = intArrayOf(crop.left, crop.top, crop.right, crop.bottom)
    draft?.getIntArray("crop")?.takeIf { it.size == 4 && it.all { value -> value in 0..45 } }
        ?.copyInto(values)
    val preview = object : View(this) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), style.dp(128))
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val pageWidth = minOf(width * 0.6f, height * 0.7f)
            val pageHeight = height - style.dp(8).toFloat()
            val left = (width - pageWidth) / 2
            val top = style.dp(4).toFloat()
            paint.style = Paint.Style.FILL
            paint.color = style.accentSoft
            canvas.drawRect(left, top, left + pageWidth, top + pageHeight, paint)
            paint.color = style.surface
            canvas.drawRect(
                left + pageWidth * values[0] / 100,
                top + pageHeight * values[1] / 100,
                left + pageWidth * (100 - values[2]) / 100,
                top + pageHeight * (100 - values[3]) / 100,
                paint
            )
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = style.dp(1).toFloat()
            paint.color = style.accent
            canvas.drawRect(left, top, left + pageWidth, top + pageHeight, paint)
        }
    }.apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    val sideLabels = listOf(
        R.string.crop_left,
        R.string.crop_top,
        R.string.crop_right,
        R.string.crop_bottom
    )
    val sideIds = listOf(R.id.crop_left, R.id.crop_top, R.id.crop_right, R.id.crop_bottom)
    val labels = sideLabels.mapIndexed { index, label ->
        style.label(label, 14f).apply {
            text = getString(R.string.crop_side_value, getString(label), values[index])
        }
    }
    val sliders = sideIds.mapIndexed { index, controlId ->
        SeekBar(this).apply {
            id = controlId
            max = 45
            progress = values[index]
            minimumHeight = style.dp(48)
            contentDescription = getString(sideLabels[index])
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    values[index] = progress
                    labels[index].text = getString(
                        R.string.crop_side_value,
                        getString(sideLabels[index]),
                        progress
                    )
                    preview.invalidate()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }
    }
    val reset = style.button(R.string.crop_reset) { sliders.forEach { it.progress = 0 } }
    val controls = mutableListOf<View>(style.label(R.string.crop_description, 14f), preview)
    sliders.indices.forEach { index ->
        controls.add(labels[index])
        controls.add(sliders[index])
    }
    controls.add(reset)
    controls.add(saving.notice)
    val content = readerFormBody(*controls.toTypedArray())
    val dialog = readerFormDialog(
        R.string.page_crop_settings,
        content,
        positive = R.string.apply_settings,
        onConfirm = { dialog ->
            val selected = current.copy(crop = PageCrop(values[0], values[1], values[2], values[3]))
            saving.submit(dialog, content) { done -> apply(selected, done) }
        }
    )
    return EditableSettingsForm(dialog) {
        Bundle().apply {
            putIntArray("crop", values.copyOf())
            putBoolean("failed", saving.captureFailed())
        }
    }
}
