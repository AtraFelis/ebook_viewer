package dev.example.ebookviewer

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup

internal class EditableSettingsForm(val dialog: AlertDialog, val capture: () -> Bundle)

/** A failed write retains the same controls and draft; only confirmed success closes the form. */
internal class SettingsSaveUi(private val activity: Activity, failed: Boolean) {
    val notice = ReaderStyle(activity).label(R.string.settings_save_failed, 14f).apply {
        id = R.id.settings_save_error
        visibility = if (failed) View.VISIBLE else View.GONE
    }
    private var saving = false

    fun captureFailed(): Boolean = notice.visibility == View.VISIBLE

    fun submit(dialog: AlertDialog, content: View, write: ((Boolean) -> Unit) -> Unit) {
        if (saving) return
        saving = true
        val controls = mutableMapOf<View, Boolean>()
        fun disable(view: View) {
            controls[view] = view.isEnabled
            view.isEnabled = false
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) disable(view.getChildAt(index))
            }
        }
        disable(content)
        val apply = requireNotNull(dialog.getButton(AlertDialog.BUTTON_POSITIVE))
        apply.isEnabled = false
        apply.setText(R.string.settings_saving)
        notice.visibility = View.GONE
        var completed = false
        val finish: (Boolean) -> Unit = { success ->
            activity.runOnUiThread {
                if (completed) return@runOnUiThread
                completed = true
                saving = false
                if (activity.isDestroyed || !dialog.isShowing) return@runOnUiThread
                controls.forEach { (view, enabled) -> view.isEnabled = enabled }
                apply.isEnabled = true
                if (success) {
                    dialog.dismiss()
                } else {
                    apply.setText(R.string.retry_settings_save)
                    notice.visibility = View.VISIBLE
                    notice.post {
                        if (dialog.isShowing) {
                            notice.requestRectangleOnScreen(
                                android.graphics.Rect(0, 0, notice.width, notice.height),
                                true
                            )
                        }
                    }
                }
            }
        }
        try {
            write(finish)
        } catch (_: Exception) {
            // A rejected submission needs the same recovery as an asynchronous write failure.
            finish(false)
        }
    }
}
