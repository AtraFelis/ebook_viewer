package dev.example.ebookviewer

import android.content.Context
import android.content.res.ColorStateList
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.ProgressBar
import android.widget.TextView

/** A bounded recent list shares the main scroll; the newest document keeps its resume card. */
internal class ReaderMainHeader(
    private val context: Context,
    onSettings: () -> Unit,
    onPick: () -> Unit,
    private val onOpen: (ReadingRecord) -> Unit,
    private val onRemove: (ReadingRecord) -> Unit
) {
    private val style = ReaderStyle(context)
    private val controls = LibraryControls(context)
    private val resume = column().apply { id = R.id.recent_documents }
    val view = column().apply {
        setPadding(style.dp(24), style.dp(24), style.dp(24), 0)
        addView(
            LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(style.label(R.string.my_documents, 22f, true), LayoutParams(0, -2, 1f))
                addView(controls.icon(R.string.settings, LibraryIcon.SETTINGS, onSettings))
            }
        )
        spaced(resume, 24)
        spaced(
            LinearLayout(context).apply {
                gravity = Gravity.CENTER
                addView(controls.action(R.string.open_pdf, LibraryIcon.ADD, onPick))
            },
            16
        )
    }

    fun render(catalog: LibraryCatalog) {
        resume.removeAllViews()
        val recent = catalog.recentlyRead()
        if (recent.isNotEmpty()) {
            resume.addView(secondaryLabel(R.string.recent_documents, 14f))
            resume.spaced(resumeCard(recent.first()), 16)
            recent.drop(1).forEach { resume.spaced(recentRow(it), 8) }
        } else {
            resume.addView(
                card(24).apply {
                    addView(style.label(R.string.home_welcome, 22f, true))
                    spaced(secondaryLabel(R.string.home_local_description), 12)
                }
            )
        }
    }

    private fun resumeCard(record: ReadingRecord): LinearLayout = card(24).apply {
        addView(title(record, 22f, true))
        spaced(metadata(record), 16)
        spaced(
            ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = record.pageCount.coerceAtLeast(1)
                progress = if (record.pageCount > 0) {
                    ((record.position?.page ?: -1) + 1).coerceIn(0, max)
                } else {
                    0
                }
                progressTintList = ColorStateList.valueOf(style.accent)
                progressBackgroundTintList = ColorStateList.valueOf(style.border)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            16,
            4
        )
        spaced(
            style.button(R.string.continue_reading, true) { onOpen(record) }
                .apply { bindRecord(record) },
            16
        )
    }

    private fun recentRow(record: ReadingRecord): Button =
        controls.action(R.string.continue_reading, LibraryIcon.DOCUMENT) { onOpen(record) }.apply {
            bindRecord(record)
            text = context.getString(
                R.string.recent_document_row,
                record.title,
                metadata(record).text
            )
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            minHeight = style.dp(64)
            minimumHeight = style.dp(64)
        }

    private fun Button.bindRecord(record: ReadingRecord) {
        id = R.id.home_record
        contentDescription = context.getString(R.string.resume_document, entryDescription(record))
        setOnLongClickListener {
            onRemove(record)
            true
        }
    }

    private fun title(record: ReadingRecord, size: Float = 16f, strong: Boolean = false): TextView =
        style.label(R.string.untitled_pdf, size, strong).apply {
            text = record.title
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

    private fun metadata(record: ReadingRecord): TextView =
        style.label(R.string.untitled_pdf, 12f).apply {
            val date = DateUtils.formatDateTime(
                context,
                record.openedAt,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
            )
            text = record.position?.takeIf { record.pageCount > 0 }?.let {
                context.getString(R.string.document_metadata, it.page + 1, record.pageCount, date)
            } ?: date
            setTextColor(style.secondary)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

    private fun entryDescription(record: ReadingRecord): String =
        record.position?.takeIf { record.pageCount > 0 }?.let {
            context.getString(R.string.reading_entry, record.title, it.page + 1, record.pageCount)
        } ?: record.title

    private fun column(): LinearLayout =
        LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private fun card(padding: Int): LinearLayout = column().apply {
        background = style.shape(style.surface, 16)
        val pixels = style.dp(padding)
        setPadding(pixels, pixels, pixels, pixels)
    }

    private fun secondaryLabel(resource: Int, size: Float = 16f): TextView =
        style.label(resource, size).apply { setTextColor(style.secondary) }

    private fun LinearLayout.spaced(view: View, top: Int, height: Int = -2) {
        addView(
            view,
            LayoutParams(-1, if (height < 0) height else style.dp(height)).apply {
                topMargin = style.dp(top)
            }
        )
    }
}
