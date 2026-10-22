package dev.example.ebookviewer

import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

internal fun Activity.readerFormBody(vararg controls: View): LinearLayout {
    val style = ReaderStyle(this)
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(style.dp(24), style.dp(8), style.dp(24), style.dp(24))
        controls.forEachIndexed { index, control ->
            addView(
                control,
                LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = if (index == 0) 0 else style.dp(16)
                }
            )
        }
    }
}

internal fun Activity.readerFormDialog(
    title: Int,
    content: View,
    positive: Int? = null,
    negative: Int = android.R.string.cancel,
    neutral: Int? = null,
    scrollContent: Boolean = true,
    onNeutral: (() -> Unit)? = null,
    onConfirm: ((AlertDialog) -> Unit)? = null,
    preview: View? = null
): AlertDialog = ReaderFormDialog(
    this,
    title,
    content,
    positive,
    negative,
    neutral,
    scrollContent,
    onNeutral,
    onConfirm,
    preview
)

/** A separate Window changes app chrome without resizing the PDF underneath it. */
private class ReaderFormDialog(
    private val activity: Activity,
    private val titleResource: Int,
    private val content: View,
    private val positive: Int?,
    private val negative: Int,
    private val neutral: Int?,
    private val scrollContent: Boolean,
    private val onNeutral: (() -> Unit)?,
    private val onConfirm: ((AlertDialog) -> Unit)?,
    private val preview: View?
) : AlertDialog(
    activity,
    if (ReaderStyle(activity).isLight) R.style.ReaderFormLight else R.style.ReaderFormDark
) {
    private val style = ReaderStyle(activity)
    private val actions = mutableMapOf<Int, Button>()
    private lateinit var heading: TextView
    private var headingText: CharSequence = activity.getString(titleResource)

    override fun setTitle(title: CharSequence?) {
        super.setTitle(title)
        headingText = title ?: ""
        if (::heading.isInitialized) heading.text = headingText
    }

    override fun getButton(whichButton: Int): Button? = actions[whichButton]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        heading = style.label(titleResource, 22f, true).apply {
            text = headingText
            setPadding(style.dp(24), style.dp(24), style.dp(24), style.dp(16))
            accessibilityHeadingCompat()
        }
        val footer = object : LinearLayout(activity) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
                val wanted = actions.values.sumOf {
                    val textWidth = it.paint.measureText(it.text.toString())
                    (textWidth + it.paddingLeft + it.paddingRight).toInt()
                } + style.dp(8) * (actions.size - 1).coerceAtLeast(0)
                val stacked = wanted > available
                orientation = if (stacked) VERTICAL else HORIZONTAL
                for (index in 0 until childCount) {
                    val child = getChildAt(index)
                    val params = child.layoutParams as LayoutParams
                    val width = if (stacked) -1 else 0
                    val weight = if (stacked) 0f else 1f
                    val left = if (stacked || index == 0) 0 else style.dp(8)
                    val top = if (!stacked || index == 0) 0 else style.dp(8)
                    if (params.width != width ||
                        params.weight != weight ||
                        params.leftMargin != left ||
                        params.topMargin != top
                    ) {
                        params.width = width
                        params.weight = weight
                        params.leftMargin = left
                        params.topMargin = top
                        child.layoutParams = params
                    }
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            setPadding(style.dp(24), style.dp(16), style.dp(24), style.dp(24))
        }
        fun action(which: Int, label: Int, primary: Boolean = false, click: () -> Unit) {
            val button = style.button(label, primary, click)
            actions[which] = button
            footer.addView(button, LinearLayout.LayoutParams(0, -2, 1f))
        }
        action(BUTTON_NEGATIVE, negative) { dismiss() }
        neutral?.let { label ->
            action(BUTTON_NEUTRAL, label, positive == null) {
                dismiss()
                onNeutral?.invoke()
            }
        }
        positive?.let { label ->
            action(BUTTON_POSITIVE, label, true) { onConfirm?.invoke(this) ?: dismiss() }
        }
        val flow = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(content, LinearLayout.LayoutParams(-1, if (scrollContent) -2 else -1))
        }
        val body = if (scrollContent) ScrollView(activity).apply { addView(flow) } else flow
        val root = object : LinearLayout(activity) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                preview?.let {
                    heading.measure(
                        MeasureSpec.makeMeasureSpec(
                            (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight)
                                .coerceAtLeast(0),
                            MeasureSpec.EXACTLY
                        ),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                    )
                    val available =
                        MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom
                    it.layoutParams.height = minOf(
                        style.dp(240),
                        available / 3,
                        (available - heading.measuredHeight - style.dp(96)).coerceAtLeast(0)
                    )
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(style.background)
            addView(heading, LinearLayout.LayoutParams(-1, -2))
            preview?.let { addView(it, LinearLayout.LayoutParams(-1, style.dp(240))) }
            addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(footer, LinearLayout.LayoutParams(-1, -2))
        }
        fun adaptFooter() {
            if (root.height == 0 || !scrollContent) return
            val bodyHeight = root.height - root.paddingTop - root.paddingBottom -
                heading.height - footer.height - (preview?.height ?: 0)
            val inline = bodyHeight < style.dp(96)
            if (inline && footer.parent === root) {
                root.removeView(footer)
                flow.addView(footer, LinearLayout.LayoutParams(-1, -2))
            } else if (!inline && footer.parent === flow) {
                flow.removeView(footer)
                root.addView(footer, LinearLayout.LayoutParams(-1, -2))
            }
        }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> adaptFooter() }
        root.setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val safe = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or
                        WindowInsets.Type.ime()
                )
                root.setPadding(safe.left, safe.top, safe.right, safe.bottom)
                root.post { adaptFooter() }
            }
            insets
        }
        setContentView(root)
        window?.apply {
            fun hasEditor(view: View): Boolean {
                if (view.onCheckIsTextEditor()) return true
                if (view is ViewGroup) {
                    for (index in 0 until view.childCount) {
                        if (hasEditor(view.getChildAt(index))) return true
                    }
                }
                return false
            }
            // AlertController cannot see editors installed via our setContentView, so undo its block.
            if (hasEditor(content)) clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            if (Build.VERSION.SDK_INT >= 30) {
                ReadingWindow(this, style.isLight, style.background).apply(false)
            }
        }
    }
}

private fun View.accessibilityHeadingCompat() {
    if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
}
