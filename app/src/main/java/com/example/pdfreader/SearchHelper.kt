package com.example.pdfreader

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

object SearchHelper {
    @Volatile
    private var initialized = false

    /**
     * Returns the 0-based page numbers whose text contains [query].
     * Call from a background thread — large documents can take a while.
     * Returns an empty list when the PDF has no extractable text
     * (for example a scanned document without OCR).
     */
    fun findPages(context: Context, uri: Uri, query: String): List<Int> {
        if (!initialized) {
            PDFBoxResourceLoader.init(context.applicationContext)
            initialized = true
        }
        val results = mutableListOf<Int>()
        val input = context.contentResolver.openInputStream(uri) ?: return results
        input.use { stream ->
            PDDocument.load(stream).use { document ->
                val stripper = PDFTextStripper()
                for (page in 1..document.numberOfPages) {
                    stripper.startPage = page
                    stripper.endPage = page
                    val text = stripper.getText(document)
                    if (text.contains(query, ignoreCase = true)) {
                        results.add(page - 1)
                    }
                }
            }
        }
        return results
    }

    /** Extract the text of a single 0-based page (for read-aloud). Background thread only. */
    fun pageText(context: Context, uri: Uri, pageIndex: Int): String {
        if (!initialized) {
            PDFBoxResourceLoader.init(context.applicationContext)
            initialized = true
        }
        val input = context.contentResolver.openInputStream(uri) ?: return ""
        input.use { stream ->
            PDDocument.load(stream).use { document ->
                if (pageIndex !in 0 until document.numberOfPages) return ""
                val stripper = PDFTextStripper()
                stripper.startPage = pageIndex + 1
                stripper.endPage = pageIndex + 1
                return stripper.getText(document).trim()
            }
        }
    }
}
