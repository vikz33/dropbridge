package com.dropbridge.app

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Tempat aplikasi kamera menyimpan hasil foto untuk mode "Foto langsung".
 * File ada di cache aplikasi, jadi foto kerja tidak ikut masuk Galeri pribadi.
 */
class CaptureProvider : ContentProvider() {
    companion object {
        const val AUTH = "com.dropbridge.app.capture"
        fun dir(c: Context): File = File(c.cacheDir, "capture").apply { mkdirs() }
        fun uriFor(name: String): Uri = Uri.parse("content://$AUTH/$name")
    }

    private fun fileOf(uri: Uri): File {
        val name = (uri.lastPathSegment ?: "").replace(Regex("[^A-Za-z0-9_.-]"), "")
        if (name.isEmpty()) throw FileNotFoundException()
        return File(dir(context!!), name)
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(fileOf(uri), ParcelFileDescriptor.parseMode(mode))

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val f = fileOf(uri)
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val row = cols.map { if (it == OpenableColumns.SIZE) f.length() as Any else f.name as Any }.toTypedArray()
        return MatrixCursor(cols, 1).apply { addRow(row) }
    }

    override fun getType(uri: Uri): String = try { FileSaver.mimeOf(fileOf(uri).name) } catch (e: Exception) { "application/octet-stream" }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
