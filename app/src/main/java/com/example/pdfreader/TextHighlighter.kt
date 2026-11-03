package com.example.pdfreader

import android.content.Context
import android.graphics.RectF
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

/** Location of highlighted matches on one page, in PDF points (top-left origin). */
data class HighlightPage(
    val pageWidth: Float,
    val pageHeight: Float,
    val rects: List<RectF>
)

/**
 * Finds where search terms appear on a page using PDFBox text positions,
 * so the reader can paint highlight boxes over the rendered page.
 */
object TextHighlighter {

    fun highlightsForPage(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        terms: List<String>
    ): HighlightPage? {
        val cleaned = terms.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return null
        Tools.initPdfBox(context)
        val input = context.contentResolver.openInputStream(uri) ?: return null
        return input.use { stream ->
            PDDocument.load(stream).use { document ->
                if (pageIndex !in 0 until document.numberOfPages) return null
                val page = document.getPage(pageIndex)
                val stripper = CollectingStripper()
                stripper.startPage = pageIndex + 1
                stripper.endPage = pageIndex + 1
                stripper.getText(document)

                val rects = mutableListOf<RectF>()
                for ((lineText, positions) in stripper.lines) {
                    val haystack = lineText.lowercase()
                    for (term in cleaned) {
                        var from = 0
                        while (true) {
                            val index = haystack.indexOf(term, from)
                            if (index < 0) break
                            val end = index + term.length
                            if (end <= positions.size) {
                                var left = Float.MAX_VALUE
                                var top = Float.MAX_VALUE
                                var right = 0f
                                var bottom = 0f
                                for (i in index until end) {
                                    val pos = positions[i]
                                    left = minOf(left, pos.xDirAdj)
                                    top = minOf(top, pos.yDirAdj)
                                    right = maxOf(right, pos.xDirAdj + pos.widthDirAdj)
                                    bottom = maxOf(bottom, pos.yDirAdj + pos.heightDir)
                                }
                                if (right > left && bottom > top) {
                                    rects.add(RectF(left, top, right, bottom))
                                }
                            }
                            from = index + 1
                        }
                    }
                }
                HighlightPage(page.mediaBox.width, page.mediaBox.height, rects)
            }
        }
    }

    /** A stripper that remembers each line's text together with its glyph positions. */
    private class CollectingStripper : PDFTextStripper() {
        val lines = mutableListOf<Pair<String, List<TextPosition>>>()

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            lines.add(text to textPositions.toList())
            super.writeString(text, textPositions)
        }
    }
}
