package dev.example.ebookviewer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

class MainActivity : Activity() {
    private lateinit var worker: PdfWorker
    private lateinit var viewer: PdfCanvasView
    private lateinit var status: TextView
    private var sourceUri: String? = null
    private var synthetic = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { setText(R.string.ready) }
        viewer = PdfCanvasView(this) { worker.request(it) }
        worker = PdfWorker(
            onLoaded = {
                viewer.setPages(it)
                status.text = getString(R.string.document_ready, it.size)
            },
            onTile = viewer::putTile,
            onError = { status.setText(R.string.load_error) }
        )
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setOnApplyWindowInsetsListener { view, insets ->
                @Suppress("DEPRECATION")
                view.setPadding(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom
                )
                insets
            }
            addView(
                Button(this@MainActivity).apply {
                    setText(R.string.open_pdf)
                    setOnClickListener {
                        @Suppress("DEPRECATION")
                        startActivityForResult(
                            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                type = "application/pdf"
                                addCategory(Intent.CATEGORY_OPENABLE)
                            },
                            OPEN_PDF
                        )
                    }
                }
            )
            addView(
                Button(this@MainActivity).apply {
                    setText(R.string.sample_pdf)
                    setOnClickListener {
                        sourceUri = null
                        synthetic = true
                        viewer.restoreViewport(null)
                        open { createSample() }
                    }
                }
            )
            addView(status)
            addView(
                viewer,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            )
        }
        setContentView(root)
        sourceUri = savedInstanceState?.getString("sourceUri")
        synthetic = savedInstanceState?.getBoolean("synthetic") ?: false
        viewer.restoreViewport(savedInstanceState?.getBundle("viewport"))
        if (synthetic) {
            open { createSample() }
        } else {
            sourceUri?.let { savedUri ->
                open {
                    requireNotNull(contentResolver.openFileDescriptor(Uri.parse(savedUri), "r"))
                }
            }
        }
    }

    @Deprecated("Platform Activity result API for dependency-free P0")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OPEN_PDF && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            openDocument(uri)
        }
    }

    fun openDocument(uri: Uri) {
        sourceUri = uri.toString()
        synthetic = false
        viewer.restoreViewport(null)
        open { requireNotNull(contentResolver.openFileDescriptor(uri, "r")) }
    }

    private fun open(source: () -> ParcelFileDescriptor) {
        viewer.reset()
        status.setText(R.string.loading)
        worker.openForRestore(viewer.hasRestoredViewport(), source)
    }

    /** Synthetic, non-private fixture. File IO and generation run on the PDF worker. */
    private fun createSample(): ParcelFileDescriptor {
        val file = File.createTempFile("p0-synthetic-", ".pdf", cacheDir)
        try {
            SyntheticDocument.write(file)
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } finally {
            // Android keeps an opened descriptor valid after unlinking the temporary name.
            file.delete()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        viewer.trimCache()
    }

    override fun onDestroy() {
        worker.close()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("sourceUri", sourceUri)
        outState.putBoolean("synthetic", synthetic)
        outState.putBundle("viewport", viewer.saveViewport())
        super.onSaveInstanceState(outState)
    }

    companion object {
        private const val OPEN_PDF = 1
    }
}
