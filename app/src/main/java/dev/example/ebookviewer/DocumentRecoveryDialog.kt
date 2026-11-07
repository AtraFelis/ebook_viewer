package dev.example.ebookviewer

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView

/** A bounded central recovery window leaves the library visible, dimmed and untouched. */
internal class DocumentRecoveryDialog(activity: Activity, private val body: View) :
    Dialog(
        activity,
        if (ReaderStyle(activity).isLight) R.style.ReaderFormLight else R.style.ReaderFormDark
    ) {
    private val style = ReaderStyle(activity)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val heading = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(style.dp(24), style.dp(12), style.dp(8), 0)
            addView(
                style.label(R.string.document_recovery_title, 20f, true),
                LinearLayout.LayoutParams(0, -2, 1f)
            )
            addView(
                LibraryControls(context).icon(R.string.close_settings, LibraryIcon.CLOSE) {
                    dismiss()
                }
            )
        }
        val content = object : ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val maximum = (context.resources.displayMetrics.heightPixels * 0.7f).toInt()
                val unbounded = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
                val available = if (unbounded) {
                    maximum
                } else {
                    minOf(maximum, MeasureSpec.getSize(heightMeasureSpec))
                }
                super.onMeasure(
                    widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST)
                )
            }
        }.apply { addView(body) }
        setContentView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = style.shape(style.surface, 20)
                addView(heading)
                addView(content)
            }
        )
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.45f)
            setGravity(Gravity.CENTER)
            val width = minOf(
                style.dp(420),
                context.resources.displayMetrics.widthPixels - style.dp(40)
            )
            setLayout(width, -2)
        }
    }
}
