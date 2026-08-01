package com.paperflow.app.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

object FileUtils {

    /**
     * Saves a file to the phone's public Downloads directory (Downloads/PaperFlow).
     * Works across all Android versions (including Android 10+ scoped storage).
     */
    fun saveFileToDownloads(context: Context, file: File, mimeType: String = "application/pdf"): Boolean {
        if (!file.exists()) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PaperFlow")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return false

                resolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                true
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val paperFlowDir = File(downloadsDir, "PaperFlow").also { if (!it.exists()) it.mkdirs() }
                val destFile = File(paperFlowDir, file.name)
                file.copyTo(destFile, overwrite = true)
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Resolves a content:// or file:// URI to a local file path.
     * Copies external content stream to cache if necessary.
     */
    fun resolveUriToFilePath(context: Context, uri: Uri): String? {
        return try {
            if (uri.scheme == "file") {
                uri.path
            } else if (uri.scheme == "content") {
                val filename = getFileNameFromUri(context, uri) ?: "opened_${System.currentTimeMillis()}.pdf"
                val cacheFile = File(context.cacheDir, filename)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        input.copyTo(output)
                    }
                }
                cacheFile.absolutePath
            } else {
                uri.path
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (_: Exception) {}
        return name
    }
}
