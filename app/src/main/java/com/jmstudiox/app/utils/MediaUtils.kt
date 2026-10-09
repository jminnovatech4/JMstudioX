package com.jmstudiox.app.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object MediaUtils {

    fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) return c.getString(i)
                }
            }
        return "media_${System.currentTimeMillis()}"
    }

    fun copyToCache(context: Context, uri: Uri, name: String? = null): File {
        val safeName = (name ?: displayName(context, uri))
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val ext = safeName.substringAfterLast('.', "mp4")
        val out = File(context.cacheDir, "jmstudiox_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to read selected media." }
            FileOutputStream(out).use { output -> input.copyTo(output) }
        }
        return out
    }

    fun formatTime(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) / 1000L)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun durationMs(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }

    fun fileSize(file: File): String {
        val mb = file.length() / 1024f / 1024f
        return String.format(Locale.US, "%.1f MB", mb)
    }
}
