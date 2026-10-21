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

/** Overlay chrome: the empty middle lets the PDF receive scroll and pinch gestures. */
internal class ReaderToolsView(
    context: Context,
    status: TextView,
    onHome: () -> Unit,
    onSettings: () -> Unit,
    onPage: () -> Unit,
    onBookmarks: () -> Unit,
    onReadText: () -> Unit,
    onDismiss: () -> Unit,
    onScrubBegin: () -> Unit,
    onScrubPage: (Int) -> Unit
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
    val page = action(R.string.go_to_page, onPage)
    val bookmarks = action(R.string.bookmarks, onBookmarks)
    val readText = action(R.string.read_page_text, onReadText).apply {
        id = R.id.read_page_text
    }
    val pageCount = style.label(R.string.loading, 14f).apply {
        id = R.id.reader_page_count
        setTextColor(style.secondary)
    }
    private var currentPage: Int? = null
    private var totalPages = 0
    val scrubber = PageScrubberView(context, onScrubBegin, onScrubPage).apply {
        onPreviewChanged = ::updatePageLabel
    }
    private val header = LinearLayout(context).apply {
        id = R.id.reader_header
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        minimumHeight = style.dp(64)
        setPadding(style.dp(8), style.dp(8), style.dp(16), style.dp(8))
        setBackgroundColor(style.surface)
        addView(home, LinearLayout.LayoutParams(style.dp(48), style.dp(48)))
        addView(title, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = style.dp(8) })
        dismissBlankTaps(onDismiss)
    }
    private val summary = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(pageCount, LinearLayout.LayoutParams(-1, -2))
        addView(scrubber, LinearLayout.LayoutParams(-1, style.dp(48)))
        status.apply {
            textSize = 12f
            setTextColor(style.secondary)
        }
        addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = style.dp(8) })
        dismissBlankTaps(onDismiss)
    }
    private val actions = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(page, LinearLayout.LayoutParams(0, -2, 1f))
                addView(bookmarks, LinearLayout.LayoutParams(0, -2, 1f))
                addView(
                    action(R.string.settings, onSettings),
                    LinearLayout.LayoutParams(0, -2, 1f)
                )
                dismissBlankTaps(onDismiss)
            },
            LinearLayout.LayoutParams(-1, -2)
        )
        addView(readText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = style.dp(4) })
        dismissBlankTaps(onDismiss)
    }
    private val footer = object : LinearLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val wide = MeasureSpec.getSize(widthMeasureSpec) >= style.dp(760) &&
                availableHeight < style.dp(480)
            orientation = if (wide) HORIZONTAL else VERTICAL
            for (index in 0 until childCount) {
                val params = getChildAt(index).layoutParams as LayoutParams
                params.width = if (wide) 0 else -1
                params.weight = if (wide) 1f else 0f
                params.topMargin = if (!wide && index > 0) style.dp(12) else 0
                params.leftMargin = if (wide && index > 0) style.dp(24) else 0
            }
            val horizontal = style.dp(
                if (MeasureSpec.getSize(widthMeasureSpec) < style.dp(360)) 16 else 24
            )
            setPadding(horizontal, style.dp(16), horizontal, style.dp(16))
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }.apply {
        id = R.id.reader_footer
        setBackgroundColor(style.surface)
        addView(summary, LinearLayout.LayoutParams(-1, -2))
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
        currentPage = page
        totalPages = count
        scrubber.update(page, count, available)
        updatePageLabel()
    }

    private fun updatePageLabel() {
        val page = currentPage
        pageCount.text = if (page == null || totalPages <= 0) {
            pageCount.context.getString(R.string.loading)
        } else {
            val target = scrubber.previewPage
            if (target != null) {
                pageCount.context.getString(
                    R.string.page_scrub_preview,
                    page + 1,
                    totalPages,
                    target + 1
                )
            } else {
                pageCount.context.getString(R.string.current_page, page + 1, totalPages)
            }
        }
    }

    private fun action(label: Int, click: () -> Unit): Button =
        style.button(label, onClick = click).apply {
            setPadding(style.dp(4), style.dp(8), style.dp(4), style.dp(8))
            background = RippleDrawable(
                ColorStateList.valueOf(style.accentSoft),
                style.shape(style.surface, 8),
                style.shape(-1, 8)
            )
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
