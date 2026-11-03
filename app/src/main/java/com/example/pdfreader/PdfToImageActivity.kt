package com.example.pdfreader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Save one page of a PDF as a PNG image. */
class PdfToImageActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pdfUri: Uri? = null
    private var pageCount = 0

    private lateinit var tvStatus: TextView
    private lateinit var btnAction: MaterialButton
    private lateinit var etPage: EditText

    private val pickPdf = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            pdfUri = uri
            readPageCount(uri)
        }
    }

    private val createImage = registerForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { out: Uri? ->
        val source = pdfUri
        if (out != null && source != null) renderPage(out, source)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tool_pdf_to_image)

        tvStatus = findViewById(R.id.tvStatus)
        btnAction = findViewById(R.id.btnAction)
        etPage = findViewById(R.id.etPage)
        findViewById<MaterialButton>(R.id.btnPick).setOnClickListener {
            pickPdf.launch(arrayOf("application/pdf"))
        }
        btnAction.setOnClickListener {
            val page = etPage.text.toString().toIntOrNull()
            if (page == null || page < 1 || page > pageCount) {
                Toast.makeText(
                    this, "Enter a page between 1 and $pageCount",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                createImage.launch("page_$page.png")
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun readPageCount(uri: Uri) {
        tvStatus.text = "Reading PDF…"
        scope.launch {
            val count = withContext(Dispatchers.IO) {
                try {
                    val fd = contentResolver.openFileDescriptor(uri, "r") ?: return@withContext 0
                    fd.use {
                        val renderer = PdfRenderer(it)
                        val pages = renderer.pageCount
                        renderer.close()
                        pages
                    }
                } catch (e: Exception) {
                    0
                }
            }
            pageCount = count
            if (count > 0) {
                tvStatus.text = "${Storage.displayName(this@PdfToImageActivity, uri)} — $count pages"
                btnAction.isEnabled = true
            } else {
                tvStatus.text = "Could not read that PDF"
            }
        }
    }

    private fun renderPage(out: Uri, source: Uri) {
        val pageNumber = etPage.text.toString().toInt()
        btnAction.isEnabled = false
        tvStatus.text = "Rendering page $pageNumber…"
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val fd = contentResolver.openFileDescriptor(source, "r")
                        ?: return@withContext false
                    var success = false
                    fd.use {
                        val renderer = PdfRenderer(it)
                        renderer.openPage(pageNumber - 1).use { page ->
                            val width = (page.width * 2).coerceAtMost(3000)
                            val height = (width.toFloat() * page.height / page.width)
                                .toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(
                                width, height, Bitmap.Config.ARGB_8888
                            )
                            bitmap.eraseColor(Color.WHITE)
                            page.render(
                                bitmap, null, null,
                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                            )
                            success = contentResolver.openOutputStream(out)?.use { stream ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                            } ?: false
                            bitmap.recycle()
                        }
                        renderer.close()
                    }
                    success
                } catch (e: Exception) {
                    false
                }
            }
            btnAction.isEnabled = true
            tvStatus.text = if (ok) "Image saved." else "Something went wrong."
            Toast.makeText(
                this@PdfToImageActivity,
                if (ok) "Page saved as image" else "Could not save image",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
