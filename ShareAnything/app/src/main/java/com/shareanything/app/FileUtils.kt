package com.shareanything.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

data class PickedFile(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long
)

object FileUtils {

    /** Reads display name + size for a content:// (or file:// ) Uri via ContentResolver. */
    fun readMeta(context: Context, uri: Uri): PickedFile {
        var name = "file"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }
        return PickedFile(uri, name, size)
    }

    /**
     * Copies the content behind [uri] into app cache dir (shared/<name>) so it can be:
     *  - exposed safely to other apps via FileProvider
     *  - served by the local HTTP server
     * Returns the resulting File.
     */
    fun copyToShareCache(context: Context, uri: Uri, name: String): File {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val outFile = File(dir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return outFile
    }

    fun humanReadableSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.size - 1) {
            value /= 1024
            unitIndex++
        }
        return "%.1f %s".format(value, units[unitIndex])
    }
}
