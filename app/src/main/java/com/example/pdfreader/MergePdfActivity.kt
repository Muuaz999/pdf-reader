package com.example.pdfreader

import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Merge two or more PDFs into a single file. */
class MergePdfActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pdfUris: List<Uri> = emptyList()

    private lateinit var tvStatus: TextView
    private lateinit var btnAction: MaterialButton

    private val pickPdfs = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            pdfUris = uris
            tvStatus.text = "${uris.size} PDF(s) selected"
            btnAction.isEnabled = uris.size >= 2
            if (uris.size < 2) tvStatus.text = "Pick at least 2 PDFs to merge"
        }
    }

    private val createPdf = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { out: Uri? ->
        if (out != null && pdfUris.size >= 2) merge(out)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tool_merge)

        tvStatus = findViewById(R.id.tvStatus)
        btnAction = findViewById(R.id.btnAction)
        findViewById<MaterialButton>(R.id.btnPick).setOnClickListener {
            pickPdfs.launch(arrayOf("application/pdf"))
        }
        btnAction.setOnClickListener { createPdf.launch("merged.pdf") }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun merge(out: Uri) {
        btnAction.isEnabled = false
        tvStatus.text = "Merging…"
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    Tools.initPdfBox(this@MergePdfActivity)
                    val workDir = File(cacheDir, "merge_${System.currentTimeMillis()}")
                    workDir.mkdirs()
                    val merger = PDFMergerUtility()
                    val outFile = File(workDir, "merged.pdf")
                    merger.destinationFileName = outFile.absolutePath
                    var copied = true
                    pdfUris.forEachIndexed { index, uri ->
                        val part = File(workDir, "part_$index.pdf")
                        if (!Tools.copyUriToFile(this@MergePdfActivity, uri, part)) copied = false
                        else merger.addSource(part)
                    }
                    var success = false
                    if (copied) {
                        merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
                        success = outFile.exists() &&
                            Tools.copyFileToUri(this@MergePdfActivity, outFile, out)
                    }
                    workDir.deleteRecursively()
                    success
                } catch (e: Exception) {
                    false
                }
            }
            btnAction.isEnabled = pdfUris.size >= 2
            tvStatus.text = if (ok) "Merged PDF saved." else "Something went wrong."
            Toast.makeText(
                this@MergePdfActivity,
                if (ok) "PDFs merged" else "Could not merge PDFs",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
