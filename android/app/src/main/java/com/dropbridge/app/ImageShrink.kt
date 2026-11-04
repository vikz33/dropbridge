package com.dropbridge.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

/** Mengecilkan foto yang lebih besar dari batas (resolusi dan kualitas JPEG) sebelum masuk antrean. */
object ImageShrink {
    const val MAX_BYTES = 512L * 1024

    fun isImageName(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")
    }

    /** Menimpa [file] dengan JPEG yang lebih kecil. Mengembalikan true jika berhasil dikecilkan. */
    fun shrink(file: File, maxBytes: Long = MAX_BYTES): Boolean {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
            val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (longEdge / (sample * 2) >= 2048) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return false

            val rotation = try {
                when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } catch (e: Exception) { 0f }
            if (rotation != 0f) {
                val m = Matrix().apply { postRotate(rotation) }
                val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                if (r !== bmp) bmp.recycle()
                bmp = r
            }
            if (bmp.hasAlpha()) { // PNG transparan -> latar putih
                val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
                val c = Canvas(flat)
                c.drawColor(-1)
                c.drawBitmap(bmp, 0f, 0f, null)
                bmp.recycle()
                bmp = flat
            }

            var best: ByteArray? = null
            var cur = bmp
            var done = false
            var round = 0
            while (!done && round < 12) {
                for (q in intArrayOf(85, 75, 65)) {
                    val out = ByteArrayOutputStream()
                    cur.compress(Bitmap.CompressFormat.JPEG, q, out)
                    best = out.toByteArray()
                    if (best.size <= maxBytes) { done = true; break }
                }
                if (done || maxOf(cur.width, cur.height) <= 640) break
                val w = (cur.width * 0.85f).toInt().coerceAtLeast(1)
                val h = (cur.height * 0.85f).toInt().coerceAtLeast(1)
                val next = Bitmap.createScaledBitmap(cur, w, h, true)
                if (next !== cur) cur.recycle()
                cur = next
                round++
            }
            cur.recycle()
            val data = best ?: return false
            if (data.size.toLong() >= file.length()) return false
            file.writeBytes(data)
            true
        } catch (e: Throwable) {
            false
        }
    }

    fun jpgName(name: String): String =
        if (name.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") }) name
        else name.substringBeforeLast('.', name) + ".jpg"
}
