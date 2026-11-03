package com.example.pdfreader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
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

/** Turn picked images into a single PDF, one image per page. */
class ImagesToPdfActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var imageUris: List<Uri> = emptyList()

    private lateinit var tvStatus: TextView
    private lateinit var btnAction: MaterialButton

    private val pickImages = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            imageUris = uris
            tvStatus.text = "${uris.size} image(s) selected"
            btnAction.isEnabled = true
        }
    }

    private val createPdf = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { out: Uri? ->
        if (out != null && imageUris.isNotEmpty()) buildPdf(out)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tool_images_to_pdf)

        tvStatus = findViewById(R.id.tvStatus)
        btnAction = findViewById(R.id.btnAction)
        findViewById<MaterialButton>(R.id.btnPick).setOnClickListener {
            pickImages.launch(arrayOf("image/*"))
        }
        btnAction.setOnClickListener { createPdf.launch("images.pdf") }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildPdf(out: Uri) {
        btnAction.isEnabled = false
        tvStatus.text = "Building PDF…"
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val document = PdfDocument()
                    imageUris.forEachIndexed { index, uri ->
                        val bitmap = decodeImage(uri) ?: return@forEachIndexed
                        val pageInfo = PdfDocument.PageInfo
                            .Builder(bitmap.width, bitmap.height, index + 1)
                            .create()
                        val page = document.startPage(pageInfo)
                        page.canvas.drawBitmap(
                            bitmap, null,
                            Rect(0, 0, bitmap.width, bitmap.height),
                            Paint(Paint.FILTER_BITMAP_FLAG)
                        )
                        document.finishPage(page)
                        bitmap.recycle()
                    }
                    val written = contentResolver.openOutputStream(out)?.use { stream ->
                        document.writeTo(stream)
                        true
                    } ?: false
                    document.close()
                    written
                } catch (e: Exception) {
                    false
                }
            }
            btnAction.isEnabled = true
            tvStatus.text = if (ok) "PDF saved." else "Something went wrong."
            Toast.makeText(
                this@ImagesToPdfActivity,
                if (ok) "PDF created" else "Could not create PDF",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /** Decode an image, downscaling very large ones to keep memory sane. */
    private fun decodeImage(uri: Uri): Bitmap? {
        val stream = contentResolver.openInputStream(uri) ?: return null
        val bitmap = stream.use { BitmapFactory.decodeStream(it) } ?: return null
        val maxDimension = 2000
        if (bitmap.width <= maxDimension && bitmap.height <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }
}
