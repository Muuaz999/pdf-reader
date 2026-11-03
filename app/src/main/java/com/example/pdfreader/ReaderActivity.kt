package com.example.pdfreader

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReaderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_NAME = "extra_name"
    }

    private lateinit var root: View
    private lateinit var toolbar: MaterialToolbar
    private lateinit var imgPage: ZoomableImageView
    private lateinit var tvPage: TextView
    private lateinit var seekPage: SeekBar
    private lateinit var btnPrev: MaterialButton
    private lateinit var btnNext: MaterialButton
    private lateinit var progress: ProgressBar

    private var uri: Uri? = null
    private var docName: String = "Document.pdf"
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var pageCount: Int = 0
    private var currentPage: Int = 0
    private var nightMode: Boolean = false

    private var highlightTerms: List<String> = emptyList()
    private val highlightCache = mutableMapOf<Int, HighlightPage>()
    private var tts: TextToSpeech? = null
    private var ttsReady: Boolean = false

    private val renderLock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var renderJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        root = findViewById(R.id.readerRoot)
        toolbar = findViewById(R.id.toolbar)
        imgPage = findViewById(R.id.imgPage)
        tvPage = findViewById(R.id.tvPage)
        seekPage = findViewById(R.id.seekPage)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        progress = findViewById(R.id.progress)

        val uriString = intent.getStringExtra(EXTRA_URI)
        if (uriString == null) {
            Toast.makeText(this, "No document selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        uri = Uri.parse(uriString)
        docName = intent.getStringExtra(EXTRA_NAME) ?: Storage.displayName(this, uri!!)
        toolbar.title = docName
        highlightTerms = Storage.getHighlightTerms(this, uri!!)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                ttsReady = true
            }
        }

        toolbar.inflateMenu(R.menu.reader_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_search -> { showSearchDialog(); true }
                R.id.action_goto -> { showGoToDialog(); true }
                R.id.action_bookmark_toggle -> { toggleBookmark(); true }
                R.id.action_bookmarks -> { showBookmarks(); true }
                R.id.action_night -> {
                    nightMode = !nightMode
                    item.isChecked = nightMode
                    applyNightMode()
                    true
                }
                R.id.action_highlight -> { showHighlightDialog(); true }
                R.id.action_read_aloud -> { readAloudPage(); true }
                R.id.action_share -> { sharePdf(); true }
                R.id.action_info -> { showInfo(); true }
                else -> false
            }
        }

        btnPrev.setOnClickListener { showPage(currentPage - 1) }
        btnNext.setOnClickListener { showPage(currentPage + 1) }
        imgPage.onSwipeLeft = { showPage(currentPage + 1) }
        imgPage.onSwipeRight = { showPage(currentPage - 1) }
        tvPage.setOnClickListener { showGoToDialog() }

        seekPage.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) showPage(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        openDocument()
    }

    private fun openDocument() {
        val docUri = uri ?: return
        progress.visibility = View.VISIBLE
        scope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    val fd = contentResolver.openFileDescriptor(docUri, "r")
                        ?: throw IllegalStateException("Could not open file")
                    fileDescriptor = fd
                    val pdfRenderer = PdfRenderer(fd)
                    renderer = pdfRenderer
                    pdfRenderer.pageCount
                }
                if (count <= 0) throw IllegalStateException("Empty PDF")
                pageCount = count
                seekPage.max = pageCount - 1
                val startPage = Storage.getLastPage(this@ReaderActivity, docUri)
                    .coerceIn(0, pageCount - 1)
                showPage(startPage)
            } catch (e: Exception) {
                progress.visibility = View.GONE
                Toast.makeText(
                    this@ReaderActivity,
                    "Could not open this PDF. It may be password-protected or damaged.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    private fun showPage(index: Int) {
        if (renderer == null || pageCount == 0) return
        val pageToRender = index.coerceIn(0, pageCount - 1)
        currentPage = pageToRender
        uri?.let { Storage.saveLastPage(this, it, pageToRender) }

        renderJob?.cancel()
        val screenWidth = resources.displayMetrics.widthPixels
        progress.visibility = View.VISIBLE
        renderJob = scope.launch {
            val rawBitmap = withContext(Dispatchers.IO) {
                synchronized(renderLock) {
                    val pdfRenderer = renderer ?: return@synchronized null
                    val page = try {
                        pdfRenderer.openPage(pageToRender)
                    } catch (e: Exception) {
                        return@synchronized null
                    }
                    try {
                        val targetWidth = (screenWidth * 2).coerceAtMost(2400)
                        val targetHeight =
                            (targetWidth.toFloat() * page.height / page.width).toInt()
                                .coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(
                            targetWidth, targetHeight, Bitmap.Config.ARGB_8888
                        )
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    } catch (e: Exception) {
                        null
                    } finally {
                        page.close()
                    }
                }
            }
            val bitmap = if (rawBitmap != null && highlightTerms.isNotEmpty()) {
                withContext(Dispatchers.IO) { applyHighlights(rawBitmap, pageToRender) }
            } else {
                rawBitmap
            }
            // A newer page request may have superseded this one.
            if (pageToRender != currentPage) return@launch
            progress.visibility = View.GONE
            if (bitmap != null) {
                imgPage.setImageBitmap(bitmap)
                // Gentle fade-in each time a page appears.
                imgPage.alpha = 0.3f
                imgPage.animate().alpha(1f).setDuration(220).start()
            } else {
                Toast.makeText(this@ReaderActivity, "Could not render page", Toast.LENGTH_SHORT)
                    .show()
            }
            tvPage.text = "${pageToRender + 1} / $pageCount"
            if (seekPage.progress != pageToRender) seekPage.progress = pageToRender
            btnPrev.isEnabled = pageToRender > 0
            btnNext.isEnabled = pageToRender < pageCount - 1
        }
    }

    private fun applyNightMode() {
        if (nightMode) {
            val invert = ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            imgPage.colorFilter = ColorMatrixColorFilter(invert)
            root.setBackgroundColor(Color.BLACK)
        } else {
            imgPage.clearColorFilter()
            root.setBackgroundColor(Color.parseColor("#ECECEC"))
        }
    }

    private fun showSearchDialog() {
        val input = EditText(this).apply {
            hint = "Text to find"
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(48, 32, 48, 8)
        }
        AlertDialog.Builder(this)
            .setTitle("Search in document")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Search") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) runSearch(query)
            }
            .show()
    }

    private fun runSearch(query: String) {
        val docUri = uri ?: return
        Toast.makeText(this, "Searching…", Toast.LENGTH_SHORT).show()
        scope.launch {
            val results = try {
                withContext(Dispatchers.IO) {
                    SearchHelper.findPages(this@ReaderActivity, docUri, query)
                }
            } catch (e: Exception) {
                emptyList()
            }
            if (results.isEmpty()) {
                Toast.makeText(
                    this@ReaderActivity,
                    "No matches. Scanned PDFs without a text layer can't be searched.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                val labels = results.map { "Page ${it + 1}" }.toTypedArray()
                AlertDialog.Builder(this@ReaderActivity)
                    .setTitle("${results.size} match(es) for \"$query\"")
                    .setItems(labels) { _, which -> showPage(results[which]) }
                    .setNegativeButton("Close", null)
                    .show()
            }
        }
    }

    private fun showGoToDialog() {
        if (pageCount == 0) return
        val input = EditText(this).apply {
            hint = "1 – $pageCount"
            inputType = InputType.TYPE_CLASS_NUMBER
            setPadding(48, 32, 48, 8)
        }
        AlertDialog.Builder(this)
            .setTitle("Go to page")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Go") { _, _ ->
                val page = input.text.toString().toIntOrNull()
                if (page != null) showPage(page - 1)
                else Toast.makeText(this, "Enter a valid page number", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun toggleBookmark() {
        val docUri = uri ?: return
        val bookmarked = Storage.toggleBookmark(this, docUri, currentPage)
        Toast.makeText(
            this,
            if (bookmarked) "Bookmarked page ${currentPage + 1}"
            else "Removed bookmark for page ${currentPage + 1}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showBookmarks() {
        val docUri = uri ?: return
        val bookmarks = Storage.getBookmarks(this, docUri)
        if (bookmarks.isEmpty()) {
            Toast.makeText(this, "No bookmarks yet for this document", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = bookmarks.map { "Page ${it + 1}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Bookmarks")
            .setItems(labels) { _, which -> showPage(bookmarks[which]) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showInfo() {
        AlertDialog.Builder(this)
            .setTitle("Document info")
            .setMessage(
                "Name: $docName\nPages: $pageCount\nCurrent page: ${currentPage + 1}"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    /** Paint saved highlight boxes over a rendered page bitmap. */
    private fun applyHighlights(bitmap: Bitmap, pageIndex: Int): Bitmap {
        val docUri = uri ?: return bitmap
        return try {
            val page = highlightCache[pageIndex]
                ?: TextHighlighter.highlightsForPage(this, docUri, pageIndex, highlightTerms)
                    ?.also { highlightCache[pageIndex] = it }
                ?: return bitmap
            if (page.rects.isEmpty()) return bitmap
            val scaleX = bitmap.width / page.pageWidth
            val scaleY = bitmap.height / page.pageHeight
            val canvas = Canvas(bitmap)
            val paint = Paint().apply {
                color = Color.argb(120, 255, 235, 59)
                style = Paint.Style.FILL
            }
            page.rects.forEach { rect ->
                canvas.drawRect(
                    rect.left * scaleX, rect.top * scaleY,
                    rect.right * scaleX, rect.bottom * scaleY,
                    paint
                )
            }
            bitmap
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun showHighlightDialog() {
        val docUri = uri ?: return
        val input = EditText(this).apply {
            hint = "Word or phrase to highlight"
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(48, 32, 48, 8)
        }
        val current = if (highlightTerms.isEmpty()) {
            "No highlights yet for this document. Add a word or phrase and every match will be marked in yellow, on every page."
        } else {
            "Current highlights:\n" + highlightTerms.joinToString(", ")
        }
        AlertDialog.Builder(this)
            .setTitle("Highlight text")
            .setMessage(current)
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Clear all") { _, _ ->
                Storage.clearHighlightTerms(this, docUri)
                highlightTerms = emptyList()
                highlightCache.clear()
                showPage(currentPage)
                Toast.makeText(this, "Highlights cleared", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("Add") { _, _ ->
                val term = input.text.toString().trim()
                if (term.isNotEmpty()) {
                    Storage.addHighlightTerm(this, docUri, term)
                    highlightTerms = Storage.getHighlightTerms(this, docUri)
                    highlightCache.clear()
                    showPage(currentPage)
                    Toast.makeText(
                        this,
                        "\"$term\" will be highlighted in this document",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }

    private fun readAloudPage() {
        val docUri = uri ?: return
        val engine = tts
        if (engine == null || !ttsReady) {
            Toast.makeText(this, "Read aloud is not ready yet", Toast.LENGTH_SHORT).show()
            return
        }
        if (engine.isSpeaking) {
            engine.stop()
            Toast.makeText(this, "Stopped reading", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Reading page ${currentPage + 1}…", Toast.LENGTH_SHORT).show()
        scope.launch {
            val text = try {
                withContext(Dispatchers.IO) {
                    SearchHelper.pageText(this@ReaderActivity, docUri, currentPage)
                }
            } catch (e: Exception) {
                ""
            }
            if (text.isBlank()) {
                Toast.makeText(
                    this@ReaderActivity,
                    "No readable text on this page. Scanned PDFs can't be read aloud.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                engine.speak(text.take(3800), TextToSpeech.QUEUE_FLUSH, null, "reader_page")
            }
        }
    }

    private fun sharePdf() {
        val docUri = uri ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, docUri)
            putExtra(Intent.EXTRA_SUBJECT, docName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share PDF"))
    }

    override fun onDestroy() {
        scope.cancel()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) { /* engine already gone */ }
        synchronized(renderLock) {
            try { renderer?.close() } catch (e: Exception) { /* already closed */ }
            try { fileDescriptor?.close() } catch (e: Exception) { /* already closed */ }
        }
        super.onDestroy()
    }
}
