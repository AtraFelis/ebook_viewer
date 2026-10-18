package dev.example.ebookviewer

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

fun Activity.pdfSearchDialog(worker: PdfWorker, pick: (PdfSearchHit?) -> Unit): AlertDialog {
    val input = EditText(this).apply {
        id = R.id.pdf_search_input
        hint = getString(R.string.pdf_search_hint)
        contentDescription = hint
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        filters = arrayOf(InputFilter.LengthFilter(PdfTextAccess.MAX_QUERY))
        isSingleLine = true
    }
    val status = TextView(this).apply {
        id = R.id.pdf_search_status
        setText(R.string.pdf_search_idle)
    }
    val position = TextView(this).apply { id = R.id.pdf_search_position }
    val previous = Button(this).apply {
        id = R.id.pdf_search_previous
        setText(R.string.pdf_search_previous)
        isEnabled = false
    }
    val next = Button(this).apply {
        id = R.id.pdf_search_next
        setText(R.string.pdf_search_next)
        isEnabled = false
    }
    var hits = emptyList<PdfSearchHit>()
    var selected = -1
    var highlighted: PdfSearchHit? = null
    fun select(index: Int) {
        selected = if (hits.isEmpty()) -1 else index.coerceIn(hits.indices)
        position.text = getString(R.string.pdf_search_position, selected + 1, hits.size)
        previous.isEnabled = selected > 0
        next.isEnabled = selected >= 0 && selected < hits.lastIndex
        val chosen = hits.getOrNull(selected)
        if (chosen != highlighted) {
            highlighted = chosen
            pick(chosen)
        }
    }
    previous.setOnClickListener { select(selected - 1) }
    next.setOnClickListener { select(selected + 1) }
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(input)
        addView(status)
        addView(position)
        addView(
            LinearLayout(this@pdfSearchDialog).apply {
                addView(previous, LinearLayout.LayoutParams(0, -2, 1f))
                addView(next, LinearLayout.LayoutParams(0, -2, 1f))
            }
        )
    }
    val dialog = AlertDialog.Builder(this)
        .setTitle(R.string.pdf_search)
        .setView(ScrollView(this).apply { addView(content) })
        .setPositiveButton(R.string.pdf_search_start, null)
        .setNegativeButton(R.string.resume_reading, null)
        .create()
    input.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            worker.cancelSearch()
            hits = emptyList()
            select(-1)
            status.setText(R.string.pdf_search_idle)
        }
    })
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val query = input.text.toString().trim()
            if (query.isEmpty()) {
                input.error = getString(R.string.pdf_search_hint)
                return@setOnClickListener
            }
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(input.windowToken, 0)
            hits = emptyList()
            select(-1)
            status.setText(R.string.pdf_search_started)
            worker.search(query) { update ->
                if (!dialog.isShowing) return@search
                hits = update.hits
                if (selected == -1 && hits.isNotEmpty()) select(0) else select(selected)
                val completeResource = if (hits.isEmpty()) {
                    R.string.pdf_search_empty
                } else {
                    R.string.pdf_search_complete
                }
                status.text = when (update.status) {
                    PdfSearchStatus.SEARCHING -> getString(
                        R.string.pdf_search_progress,
                        update.pagesSearched,
                        update.pageCount,
                        hits.size
                    )
                    PdfSearchStatus.COMPLETE -> getString(
                        completeResource,
                        hits.size
                    )
                    PdfSearchStatus.LIMITED -> getString(R.string.pdf_search_limited, hits.size)
                    PdfSearchStatus.UNAVAILABLE -> getString(R.string.pdf_search_unavailable)
                    PdfSearchStatus.ERROR -> getString(R.string.pdf_search_error)
                }
            }
        }
    }
    return dialog
}
