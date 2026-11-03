package com.example.pdfreader

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File
import java.io.FileInputStream

/** Shared helpers for the tool screens. */
object Tools {

    @Volatile
    private var pdfBoxReady = false

    fun initPdfBox(context: Context) {
        if (!pdfBoxReady) {
            PDFBoxResourceLoader.init(context.applicationContext)
            pdfBoxReady = true
        }
    }

    /** Copy the content behind [uri] into a local [file]. True on success. */
    fun copyUriToFile(context: Context, uri: Uri, file: File): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } != null
        } catch (e: Exception) {
            false
        }
    }

    /** Copy a local [file] into the document behind [uri]. True on success. */
    fun copyFileToUri(context: Context, file: File, uri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                FileInputStream(file).use { input -> input.copyTo(output) }
            } != null
        } catch (e: Exception) {
            false
        }
    }

    /** Size in bytes of the document behind [uri], or -1 when unknown. */
    fun uriSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        } catch (e: Exception) {
            -1L
        }
    }

    fun formatSize(bytes: Long): String = when {
        bytes < 0 -> "unknown size"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> String.format("%.2f MB", bytes / 1024.0 / 1024.0)
    }
}
