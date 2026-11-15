package com.dropbridge.app

import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import org.json.JSONObject

/**
 * Catatan file dari laptop yang sudah diunduh ke HP ini (aplikasi saja).
 * Kunci = folder di laptop + nama file. Dipakai untuk tanda ikon di tab File dan lembar Buka/Pasang/Lokasi.
 * File yang dihapus dari HP otomatis dianggap belum diunduh.
 */
object Downloaded {
    enum class State { NONE, OK, NEW, LOADING }

    class Entry(val uri: Uri, val size: Long, val name: String, val rel: String, val ts: Long)

    private const val KEY = "downloaded"
    private const val MAX = 400
    /** Unduhan yang sedang berjalan: kunci → persen (0-100, -1 = belum tahu). */
    val running = HashMap<String, Int>()
    private val alive = HashMap<String, Boolean>()

    fun key(folder: String, name: String) = "${Prefs.pcId.ifEmpty { Prefs.server }}|$folder/$name"

    private fun all(): JSONObject = try { JSONObject(Prefs.get(KEY).ifEmpty { "{}" }) } catch (e: Exception) { JSONObject() }

    fun record(folder: String, name: String, uri: Uri, size: Long, savedName: String, rel: String) {
        val j = all()
        j.put(key(folder, name), JSONObject().put("uri", uri.toString()).put("size", size).put("name", savedName).put("rel", rel).put("ts", System.currentTimeMillis()))
        // buang yang paling lama bila terlalu banyak
        if (j.length() > MAX) {
            val keys = j.keys().asSequence().toList().sortedBy { j.optJSONObject(it)?.optLong("ts") ?: 0L }
            for (k in keys.take(j.length() - MAX)) j.remove(k)
        }
        Prefs.put(KEY, j.toString())
        alive[uri.toString()] = true
    }

    fun forget(folder: String, name: String) {
        val j = all(); j.remove(key(folder, name)); Prefs.put(KEY, j.toString())
    }

    fun get(c: Context, folder: String, name: String): Entry? {
        val o = all().optJSONObject(key(folder, name)) ?: return null
        val uri = Uri.parse(o.optString("uri"))
        val ok = alive.getOrPut(uri.toString()) { exists(c, uri) }
        if (!ok) { forget(folder, name); return null }
        return Entry(uri, o.optLong("size"), o.optString("name"), o.optString("rel"), o.optLong("ts"))
    }

    /** Lupakan hasil cek "masih ada di HP" supaya dicek lagi (mis. saat folder dibuka ulang). */
    fun recheck() = alive.clear()

    private fun exists(c: Context, uri: Uri): Boolean = try {
        c.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { it.moveToFirst() } ?: false
    } catch (e: Exception) { false }

    fun state(c: Context, folder: String, name: String, size: Long): State {
        if (running.containsKey(key(folder, name))) return State.LOADING
        val e = get(c, folder, name) ?: return State.NONE
        return if (e.size == size) State.OK else State.NEW
    }

    fun isApk(name: String) = name.lowercase().endsWith(".apk")

    // ---------- Aksi ----------

    fun open(a: Activity, e: Entry) {
        if (isApk(e.name)) { install(a, e); return }
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(e.uri, FileSaver.mimeOf(e.name))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { a.startActivity(i) } catch (x: ActivityNotFoundException) {
            Toast.makeText(a, "Tidak ada aplikasi di HP ini untuk membuka ${e.name}", Toast.LENGTH_LONG).show()
        }
    }

    /** Pasang APK. Pertama kali Android meminta izin "Pasang aplikasi dari DropBridge". */
    fun install(a: Activity, e: Entry) {
        if (Build.VERSION.SDK_INT >= 26 && !a.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(a, "Izinkan DropBridge memasang aplikasi, lalu kembali dan ketuk Pasang lagi", Toast.LENGTH_LONG).show()
            try {
                a.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}")))
            } catch (x: Exception) { /* HP tertentu: pengaturan tidak tersedia */ }
            return
        }
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(e.uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { a.startActivity(i) } catch (x: Exception) {
            Toast.makeText(a, "Tidak bisa membuka pemasang: ${x.message}", Toast.LENGTH_LONG).show()
        }
    }

    /** Buka aplikasi file di folder tempat file disimpan; bila HP tidak mendukung, buka Unduhan dan tunjukkan jalurnya. */
    fun showLocation(a: Activity, e: Entry) {
        val rel = e.rel.trim('/')
        val doc = Uri.parse("content://com.android.externalstorage.documents/document/primary%3A" + Uri.encode(rel))
        val tries = listOf(
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(doc, "vnd.android.document/directory"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) },
            Intent(DownloadManager.ACTION_VIEW_DOWNLOADS),
        )
        Toast.makeText(a, "Lokasi: $rel/${e.name}", Toast.LENGTH_LONG).show()
        for (i in tries) {
            try { a.startActivity(i); return } catch (x: Exception) { /* coba cara berikutnya */ }
        }
    }

    fun share(a: Activity, e: Entry) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = FileSaver.mimeOf(e.name)
            putExtra(Intent.EXTRA_STREAM, e.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        a.startActivity(Intent.createChooser(send, "Bagikan ${e.name}"))
    }

    /** Hapus salinan lama di HP sebelum unduh versi baru (file buatan aplikasi ini sendiri). */
    fun deleteLocal(c: Context, e: Entry) {
        try { c.contentResolver.delete(e.uri, null, null) } catch (x: Exception) { /* biarkan; unduhan baru diberi nama (1) */ }
        alive.remove(e.uri.toString())
    }

    // ---------- Tanda ikon di pojok file ----------

    /** Lingkaran kecil: centang hijau = sudah di HP, panah oranye = versi baru di laptop, cincin teal = diunduh. */
    fun indicator(c: Context, st: State, pct: Int = -1): View? {
        val px = Ui.dp(c, 22)
        return when (st) {
            State.NONE -> null
            State.LOADING -> RingView(c, pct).apply { contentDescription = "Sedang diunduh" }
            else -> FrameLayout(c).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(if (st == State.OK) Ui.OK else Tokens.WARNING)
                    setStroke(Ui.dp(c, 2), Ui.CARD)
                }
                contentDescription = if (st == State.OK) "Sudah di HP" else "Ada versi baru di laptop"
                addView(IconView(c, if (st == State.OK) Icon.CHECK else Icon.DOWN, Ui.WHITE, 12, 2.8f), FrameLayout.LayoutParams(Ui.dp(c, 12), Ui.dp(c, 12), Gravity.CENTER))
                minimumWidth = px
            }
        }
    }

    class RingView(c: Context, var pct: Int) : View(c) {
        private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = Ui.dp(c, 3).toFloat() }
        private val bg = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Ui.CARD }
        override fun onDraw(cv: android.graphics.Canvas) {
            val r = minOf(width, height) / 2f
            cv.drawCircle(width / 2f, height / 2f, r, bg)
            val inset = Ui.dp(context, 3).toFloat()
            val box = android.graphics.RectF(inset, inset, width - inset, height - inset)
            p.color = Ui.LINE; cv.drawArc(box, 0f, 360f, false, p)
            p.color = Ui.ACCENT; cv.drawArc(box, -90f, 360f * (if (pct < 0) 25 else pct) / 100f, false, p)
        }
    }
}
