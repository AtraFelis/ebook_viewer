package dev.example.ebookviewer

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.SeekBar

internal fun Activity.pageCropDialog(
    current: PageLayoutSettings,
    draft: Bundle? = null,
    preview: PageCropPreview = PageCropPreview(this),
    apply: (PageLayoutSettings, (Boolean) -> Unit) -> Unit
): EditableSettingsForm {
    val style = ReaderStyle(this)
    val saving = SettingsSaveUi(this, draft?.getBoolean("failed") == true)
    val crop = current.crop
    val values = intArrayOf(crop.left, crop.top, crop.right, crop.bottom)
    draft?.getIntArray("crop")?.takeIf { it.size == 4 && it.all { value -> value in 0..45 } }
        ?.copyInto(values)
    preview.crop = PageCrop(values[0], values[1], values[2], values[3])
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
                    preview.crop = PageCrop(values[0], values[1], values[2], values[3])
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }
    }
    val reset = style.button(R.string.crop_reset) { sliders.forEach { it.progress = 0 } }
    val controls = mutableListOf<View>(style.label(R.string.crop_description, 14f))
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
        preview = preview,
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
