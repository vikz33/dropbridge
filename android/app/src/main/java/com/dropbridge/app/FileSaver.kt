package com.dropbridge.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Menyimpan file masuk ke folder Download/<subdir> di HP. */
object FileSaver {

    fun mimeOf(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        val m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        if (m != null) return m
        return when (ext) {
            "heic", "heif" -> "image/heic"
            "mov" -> "video/quicktime"
            else -> "application/octet-stream"
        }
    }

    /** Salin len byte (len < 0: sampai akhir aliran). */
    fun copy(input: InputStream, out: OutputStream, len: Long) {
        val buf = ByteArray(64 * 1024)
        if (len < 0) {
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
            return
        }
        var left = len
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) throw IOException("Koneksi terputus")
            out.write(buf, 0, n)
            left -= n
        }
    }

    /** Mengembalikan nama final file (bisa berbeda jika nama sudah dipakai). */
    fun save(ctx: Context, subdir: String, name: String, len: Long, input: InputStream): String {
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = ctx.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(name))
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/$subdir")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("Gagal membuat file")
            try {
                val os = resolver.openOutputStream(uri) ?: throw IOException("Gagal menulis file")
                os.use { copy(input, it, len) }
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            } catch (e: Exception) {
                try { resolver.delete(uri, null, null) } catch (x: Exception) { /* abaikan */ }
                throw e
            }
            var final = name
            try {
                resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) final = c.getString(0)
                }
            } catch (e: Exception) { /* pakai nama awal */ }
            return final
        }
        val dir = File(ctx.getExternalFilesDir(null), subdir)
        dir.mkdirs()
        var f = File(dir, name)
        var n = 1
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        while (f.exists()) { f = File(dir, "$base ($n)$ext"); n++ }
        try {
            f.outputStream().use { copy(input, it, len) }
        } catch (e: Exception) {
            f.delete()
            throw e
        }
        return f.name
    }
}
