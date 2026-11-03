package com.example.pdfreader

import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Extract a page range from a PDF into a new file. */
class SplitPdfActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pdfUri: Uri? = null
    private var pageCount = 0

    private lateinit var tvStatus: TextView
    private lateinit var btnAction: MaterialButton
    private lateinit var etFrom: EditText
    private lateinit var etTo: EditText

    private val pickPdf = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            pdfUri = uri
            readPageCount(uri)
        }
    }

    private val createPdf = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { out: Uri? ->
        val source = pdfUri
        if (out != null && source != null) extract(out, source)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tool_split)

        tvStatus = findViewById(R.id.tvStatus)
        btnAction = findViewById(R.id.btnAction)
        etFrom = findViewById(R.id.etFrom)
        etTo = findViewById(R.id.etTo)
        findViewById<MaterialButton>(R.id.btnPick).setOnClickListener {
            pickPdf.launch(arrayOf("application/pdf"))
        }
        btnAction.setOnClickListener {
            if (validRange()) createPdf.launch("extracted.pdf")
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
                    Tools.initPdfBox(this@SplitPdfActivity)
                    val tmp = File(cacheDir, "split_source.pdf")
                    if (!Tools.copyUriToFile(this@SplitPdfActivity, uri, tmp)) 0
                    else PDDocument.load(tmp).use { it.numberOfPages }
                } catch (e: Exception) {
                    0
                }
            }
            pageCount = count
            if (count > 0) {
                tvStatus.text = "${Storage.displayName(this@SplitPdfActivity, uri)} — $count pages"
                etTo.setText(count.toString())
                btnAction.isEnabled = true
            } else {
                tvStatus.text = "Could not read that PDF"
            }
        }
    }

    private fun validRange(): Boolean {
        val from = etFrom.text.toString().toIntOrNull()
        val to = etTo.text.toString().toIntOrNull()
        if (from == null || to == null || pageCount == 0 ||
            from < 1 || to < from || to > pageCount
        ) {
            Toast.makeText(
                this, "Enter a page range between 1 and $pageCount",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
        return true
    }

    private fun extract(out: Uri, source: Uri) {
        val from = etFrom.text.toString().toInt()
        val to = etTo.text.toString().toInt()
        btnAction.isEnabled = false
        tvStatus.text = "Extracting pages $from–$to…"
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    Tools.initPdfBox(this@SplitPdfActivity)
                    val workDir = File(cacheDir, "split_${System.currentTimeMillis()}")
                    workDir.mkdirs()
                    val srcFile = File(workDir, "source.pdf")
                    val outFile = File(workDir, "extracted.pdf")
                    var success = false
                    if (Tools.copyUriToFile(this@SplitPdfActivity, source, srcFile)) {
                        PDDocument.load(srcFile).use { document ->
                            val result = PDDocument()
                            for (index in (from - 1) until to) {
                                result.importPage(document.getPage(index))
                            }
                            result.save(outFile)
                            result.close()
                        }
                        success = Tools.copyFileToUri(this@SplitPdfActivity, outFile, out)
                    }
                    workDir.deleteRecursively()
                    success
                } catch (e: Exception) {
                    false
                }
            }
            btnAction.isEnabled = true
            tvStatus.text = if (ok) "Pages saved." else "Something went wrong."
            Toast.makeText(
                this@SplitPdfActivity,
                if (ok) "Pages extracted" else "Could not extract pages",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
