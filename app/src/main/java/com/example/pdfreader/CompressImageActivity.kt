package com.example.pdfreader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.SeekBar
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
import java.io.ByteArrayOutputStream

/** Re-save an image as a smaller JPEG at a chosen quality. */
class CompressImageActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var imageUri: Uri? = null
    private var originalSize = -1L

    private lateinit var tvStatus: TextView
    private lateinit var tvQuality: TextView
    private lateinit var seekQuality: SeekBar
    private lateinit var btnAction: MaterialButton

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
            originalSize = Tools.uriSize(this, uri)
            tvStatus.text = "Original: ${Tools.formatSize(originalSize)}"
            btnAction.isEnabled = true
        }
    }

    private val createImage = registerForActivityResult(
        ActivityResultContracts.CreateDocument("image/jpeg")
    ) { out: Uri? ->
        val source = imageUri
        if (out != null && source != null) compress(out, source)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tool_compress)

        tvStatus = findViewById(R.id.tvStatus)
        tvQuality = findViewById(R.id.tvQuality)
        seekQuality = findViewById(R.id.seekQuality)
        btnAction = findViewById(R.id.btnAction)
        findViewById<MaterialButton>(R.id.btnPick).setOnClickListener {
            pickImage.launch(arrayOf("image/*"))
        }
        btnAction.setOnClickListener { createImage.launch("compressed.jpg") }
        seekQuality.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvQuality.text = "Quality: ${progress + 10}%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun quality(): Int = seekQuality.progress + 10

    private fun compress(out: Uri, source: Uri) {
        btnAction.isEnabled = false
        tvStatus.text = "Compressing…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val bitmap = contentResolver.openInputStream(source)?.use {
                        BitmapFactory.decodeStream(it)
                    } ?: return@withContext null
                    val buffer = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality(), buffer)
                    val bytes = buffer.toByteArray()
                    bitmap.recycle()
                    val written = contentResolver.openOutputStream(out)?.use { stream ->
                        stream.write(bytes)
                        true
                    } ?: false
                    if (written) bytes.size.toLong() else null
                } catch (e: Exception) {
                    null
                }
            }
            btnAction.isEnabled = true
            if (result != null) {
                val saved = if (originalSize > 0) {
                    val pct = 100 - (result * 100 / originalSize)
                    " — saved $pct%"
                } else ""
                tvStatus.text =
                    "Original: ${Tools.formatSize(originalSize)} → " +
                    "Compressed: ${Tools.formatSize(result)}$saved"
                Toast.makeText(this@CompressImageActivity, "Image saved", Toast.LENGTH_SHORT)
                    .show()
            } else {
                tvStatus.text = "Something went wrong."
                Toast.makeText(
                    this@CompressImageActivity, "Could not compress image",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
