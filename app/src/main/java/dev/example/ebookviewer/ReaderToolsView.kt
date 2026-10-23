package dev.example.ebookviewer

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.ceil

/** Overlay chrome: the empty middle lets the PDF receive scroll and pinch gestures. */
internal class ReaderToolsView(
    context: Context,
    status: TextView,
    onHome: () -> Unit,
    onSettings: () -> Unit,
    onViewSettings: () -> Unit,
    onBookmarks: () -> Unit,
    onReadText: () -> Unit,
    onDismiss: () -> Unit,
    onScrubBegin: () -> Unit,
    onScrubPage: (Int) -> Unit,
    onPanLock: () -> Unit = {},
    onOrientation: () -> Unit = {}
) {
    private val style = ReaderStyle(context)
    private var availableHeight = 0
    val title = style.label(R.string.untitled_pdf, 16f).apply {
        id = R.id.reader_title
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
    }
    val home = ImageButton(context).apply {
        id = R.id.reader_home
        contentDescription = context.getString(R.string.document_list_action)
        setImageResource(R.drawable.reader_back)
        imageTintList = ColorStateList.valueOf(style.text)
        setPadding(style.dp(14), style.dp(14), style.dp(14), style.dp(14))
        val circle = InsetDrawable(style.shape(style.background, 20), style.dp(4))
        background = RippleDrawable(ColorStateList.valueOf(style.accentSoft), circle, circle)
        setOnClickListener { onHome() }
    }
    val panLock = ImageButton(context).apply {
        id = R.id.horizontal_pan_lock
        setPadding(style.dp(13), style.dp(13), style.dp(13), style.dp(13))
        val circle = InsetDrawable(style.shape(style.accentSoft, 20), style.dp(4))
        background = RippleDrawable(ColorStateList.valueOf(style.accentSoft), circle, circle)
        setOnClickListener { onPanLock() }
    }
    val viewSettings = action(
        R.string.view_settings,
        R.drawable.reader_view_settings,
        onViewSettings
    ).apply {
        id = R.id.reader_view_settings
    }
    val orientationButton = ImageButton(context).apply {
        id = R.id.reader_orientation
        contentDescription = context.getString(R.string.orientation_settings)
        setImageResource(R.drawable.reader_orientation)
        imageTintList = ColorStateList.valueOf(style.accent)
        setPadding(style.dp(13), style.dp(13), style.dp(13), style.dp(13))
        val circle = InsetDrawable(style.shape(style.accentSoft, 20), style.dp(4))
        background = RippleDrawable(ColorStateList.valueOf(style.accentSoft), circle, circle)
        setOnClickListener { onOrientation() }
    }
    fun updateOrientation(mode: OrientationMode) {
        orientationButton.setImageResource(mode.iconResource())
    }

    val bookmarks = action(R.string.bookmarks, R.drawable.reader_bookmark, onBookmarks)
    val readText = action(R.string.read_text_short, R.drawable.reader_text, onReadText).apply {
        id = R.id.read_page_text
        contentDescription = context.getString(R.string.read_page_text)
    }
    val pageCount = style.label(R.string.loading, 14f).apply {
        id = R.id.reader_page_count
        setTextColor(style.secondary)
    }
    private var currentPage: Int? = null
    private var totalPages = 0
    private var panLockInitialized = false
    val scrubber = PageScrubberView(context, onScrubBegin, onScrubPage).apply {
        onPreviewChanged = ::updatePageLabel
    }
    private val header = LinearLayout(context).apply {
        id = R.id.reader_header
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        minimumHeight = style.dp(56)
        setPadding(style.dp(8), style.dp(4), style.dp(8), style.dp(4))
        setBackgroundColor(style.surface)
        addView(home, LinearLayout.LayoutParams(style.dp(48), style.dp(48)))
        addView(title, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = style.dp(8) })
        addView(orientationButton, LinearLayout.LayoutParams(style.dp(48), style.dp(48)))
        addView(
            panLock,
            LinearLayout.LayoutParams(style.dp(48), style.dp(48)).apply { leftMargin = style.dp(8) }
        )
        dismissBlankTaps(onDismiss)
    }
    private val progress = LinearLayout(context).apply {
        gravity = android.view.Gravity.CENTER_VERTICAL
        pageCount.apply {
            textSize = 12f
            isSingleLine = true
            maxWidth = style.dp(120)
            ellipsize = TextUtils.TruncateAt.END
            setPadding(style.dp(4), 0, style.dp(8), 0)
        }
        addView(pageCount, LinearLayout.LayoutParams(-2, -2))
        addView(scrubber, LinearLayout.LayoutParams(0, style.dp(48), 1f))
        dismissBlankTaps(onDismiss)
    }
    private val actions = LinearLayout(context).apply {
        gravity = android.view.Gravity.CENTER_VERTICAL
        listOf(
            bookmarks,
            viewSettings,
            action(R.string.settings, R.drawable.reader_settings, onSettings),
            readText
        ).forEachIndexed { index, button ->
            addView(
                button,
                LinearLayout.LayoutParams(0, -2, 1f).apply {
                    leftMargin = if (index == 0) 0 else style.dp(6)
                }
            )
        }
        dismissBlankTaps(onDismiss)
    }
    private val footer = LinearLayout(context).apply {
        id = R.id.reader_footer
        orientation = LinearLayout.VERTICAL
        setPadding(style.dp(12), 0, style.dp(12), style.dp(4))
        setBackgroundColor(style.surface)
        addView(progress, LinearLayout.LayoutParams(-1, -2))
        status.apply {
            textSize = 11f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(style.secondary)
            visibility = View.GONE
        }
        addView(status, LinearLayout.LayoutParams(-1, -2))
        addView(actions, LinearLayout.LayoutParams(-1, -2))
        dismissBlankTaps(onDismiss)
    }
    val view = object : LinearLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            availableHeight = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }.apply {
        id = R.id.reader_tools
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        addView(header, LinearLayout.LayoutParams(-1, -2))
        addView(View(context), LinearLayout.LayoutParams(-1, style.dp(48), 1f))
        // Wrap the footer, not the full overlay: a short window still exposes all actions by scroll.
        addView(
            object : ScrollView(context) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    val limit = (availableHeight - header.measuredHeight - style.dp(48))
                        .coerceAtLeast(0)
                    super.onMeasure(
                        widthMeasureSpec,
                        MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST)
                    )
                }
            }.apply { addView(footer) },
            LinearLayout.LayoutParams(-1, -2)
        )
    }

    fun setTitle(text: CharSequence) {
        title.text = text
        title.contentDescription = text
    }

    fun updatePages(page: Int?, count: Int, available: Boolean) {
        if (totalPages != count) {
            // Changing preview digits must not resize the slider and cancel its active gesture.
            val widest = ('0'..'9').maxBy { pageCount.paint.measureText(it.toString()) }
            val sample = pageCount.context.getString(R.string.current_page, count, count).map {
                if (it.isDigit()) widest else it
            }.joinToString("")
            pageCount.layoutParams = pageCount.layoutParams.apply {
                width = (
                    ceil(pageCount.paint.measureText(sample)).toInt() +
                        pageCount.compoundPaddingLeft + pageCount.compoundPaddingRight
                    ).coerceIn(style.dp(48), style.dp(120))
            }
        }
        currentPage = page
        totalPages = count
        scrubber.update(page, count, available)
        updatePageLabel()
    }

    fun updatePanLock(locked: Boolean, available: Boolean) {
        if (panLockInitialized &&
            panLock.isSelected == locked &&
            panLock.isEnabled == available
        ) {
            return
        }
        panLockInitialized = true
        panLock.isEnabled = available
        panLock.isSelected = locked
        panLock.alpha = if (available) 1f else 0.4f
        panLock.setImageResource(
            if (locked) R.drawable.reader_pan_locked else R.drawable.reader_pan_unlocked
        )
        panLock.imageTintList = ColorStateList.valueOf(style.accent)
        panLock.contentDescription = panLock.context.getString(
            if (locked) R.string.unlock_horizontal_pan else R.string.lock_horizontal_pan
        )
    }

    private fun updatePageLabel() {
        val page = currentPage
        pageCount.text = if (page == null || totalPages <= 0) {
            pageCount.context.getString(R.string.loading)
        } else {
            val target = scrubber.previewPage
            if (target != null) {
                pageCount.context.getString(R.string.current_page, target + 1, totalPages)
            } else {
                pageCount.context.getString(R.string.current_page, page + 1, totalPages)
            }
        }
    }

    private fun action(label: Int, icon: Int, click: () -> Unit): Button =
        style.button(label, onClick = click).apply {
            textSize = 11f
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            setPadding(style.dp(2), style.dp(2), style.dp(2), style.dp(2))
            val image = context.getDrawable(icon)?.mutate()?.apply {
                setTint(style.accent)
                setBounds(0, 0, style.dp(20), style.dp(20))
            }
            setCompoundDrawables(null, image, null, null)
            compoundDrawablePadding = style.dp(2)
        }

    private fun View.dismissBlankTaps(onDismiss: () -> Unit) {
        // Only unconsumed space reaches this listener; buttons keep their own event stream.
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        setOnClickListener { onDismiss() }
        var multiple = false
        var clicked = false
        val detector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent): Boolean = true

                override fun onSingleTapUp(event: MotionEvent): Boolean {
                    clicked = !multiple
                    return true
                }
            }
        ).apply { setOnDoubleTapListener(null) }
        setOnTouchListener { touched, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) multiple = false
            if (event.pointerCount > 1) multiple = true
            detector.onTouchEvent(event)
            if (clicked) {
                clicked = false
                touched.performClick()
            }
            true
        }
    }
}
