package com.dropbridge.app

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Lihat folder dan file di PC, lalu unduh ke HP (folder Download/DropBridge). */
class BrowseActivity : Activity() {

    private class FileRow(val name: String, val size: Long)

    private lateinit var body: LinearLayout
    private lateinit var status: TextView
    private lateinit var scr: Screen
    private var folder: String? = null
    private var base: String = ""
    private var token: String = ""

    private fun fmtSize(b: Long): String {
        if (b < 1024) return "$b B"
        var v = b.toDouble()
        val units = arrayOf("KB", "MB", "GB", "TB")
        var i = -1
        do { v /= 1024; i++ } while (v >= 1024 && i < units.size - 1)
        return String.format("%.1f %s", v, units[i])
    }

    private fun setStatus(msg: String) {
        val c: android.content.Context = this
        val bad = msg.contains("GAGAL") || msg.contains("Gagal") || msg.contains("tidak ditemukan")
        status.text = msg
        status.setTextColor(if (bad) Ui.AMBER_TX else Ui.DEEP)
        status.background = Ui.rounded(c, if (bad) Ui.AMBER_BG else Ui.SOFT, 14)
        status.visibility = if (msg.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        val c: android.content.Context = this
        scr = Ui.screen(this, "Folder", "Menyambung ke PC...", back = false, nav = Ui.TAB_FILES)
        status = Ui.text(c, "", 14f, true, Ui.DEEP).apply {
            setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12))
            visibility = View.GONE
        }
        scr.content.addView(status, Ui.lp(c, b = 12))
        body = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        scr.content.addView(body, Ui.lp(c))
        connectAndLoad()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (folder != null) {
            folder = null
            load()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun connectAndLoad() {
        Thread {
            try {
                val b = Net.ensureServer()
                if (b == null) {
                    runOnUiThread { scr.subtitle.text = "PC tidak ditemukan"; setStatus("PC tidak ditemukan. Pastikan start.bat jalan dan satu jaringan dengan HP.") }
                    return@Thread
                }
                base = b
                token = Net.ensureToken(b)
                runOnUiThread { load() }
            } catch (e: Exception) {
                runOnUiThread { setStatus("Gagal: ${e.message}") }
            }
        }.start()
    }

    private fun load() {
        scr.subtitle.text = "Memuat..."
        scr.subtitle.visibility = View.VISIBLE
        val f = folder
        Thread {
            try {
                if (f == null) {
                    val d = Net.getJson(base, token, "/api/folders")
                    val arr = d.getJSONArray("folders")
                    val rows = ArrayList<Triple<String, Int, Long>>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        rows.add(Triple(o.getString("name"), o.optInt("count"), o.optLong("size")))
                    }
                    val root = d.optJSONObject("root")
                    runOnUiThread { showFolders(rows, root?.optInt("count") ?: 0) }
                } else {
                    val d = Net.getJson(base, token, "/api/files?folder=" + Uri.encode(f))
                    val arr = d.getJSONArray("files")
                    val rows = ArrayList<FileRow>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        rows.add(FileRow(o.getString("name"), o.optLong("size")))
                    }
                    runOnUiThread { showFiles(f, rows) }
                }
            } catch (e: Exception) {
                runOnUiThread { setStatus("Gagal memuat: ${e.message}") }
            }
        }.start()
    }

    private var query = ""

    private fun showFolders(rows: List<Triple<String, Int, Long>>, rootCount: Int) {
        val c: android.content.Context = this
        body.removeAllViews()
        scr.title.text = "Folder"
        scr.subtitle.visibility = View.VISIBLE
        scr.subtitle.text = if (rows.isEmpty() && rootCount == 0) "Belum ada folder di PC" else "${rows.size} folder di PC"
        val search = Ui.input(c, "Cari folder", android.text.InputType.TYPE_CLASS_TEXT, query)
        body.addView(search, Ui.lp(c, b = 12))
        val list = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        body.addView(list, Ui.lp(c))
        fun fill() {
            list.removeAllViews()
            val q = query.trim().lowercase()
            val shown = rows.filter { q.isEmpty() || it.first.lowercase().contains(q) }
            for (r in shown) {
                val wrap = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
                wrap.addView(rowView(Icon.FOLDER, r.first, "${r.second} file, ${fmtSize(r.third)}", null) {
                    folder = r.first
                    load()
                })
                wrap.addView(Ui.button(c, "Kirim ke sini", Ui.Kind.SOFT, 40) {
                    Prefs.lastFolder = r.first
                    startActivity(Intent(this, SendActivity::class.java).putExtra("pick", true))
                }, Ui.lp(c, t = 4))
                list.addView(wrap, Ui.lp(c, b = 10))
            }
            if (shown.isEmpty() && rows.isNotEmpty()) list.addView(Ui.text(c, "Tidak ada folder yang cocok.", 14f, false, Ui.MUTED))
            if (q.isEmpty() && rootCount > 0) {
                list.addView(rowView(Icon.FOLDER, "Tanpa folder", "$rootCount file", null) {
                    folder = ""
                    load()
                }, Ui.lp(c, b = 8))
            }
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { query = s?.toString() ?: ""; fill() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        fill()
        body.addView(Ui.button(c, "Muat ulang", Ui.Kind.OUTLINE, 48) { load() }, Ui.lp(c, t = 8))
    }

    private fun showFiles(f: String, rows: List<FileRow>) {
        val c: android.content.Context = this
        body.removeAllViews()
        scr.title.text = if (f.isEmpty()) "Tanpa folder" else f
        scr.subtitle.visibility = View.VISIBLE
        scr.subtitle.text = "${rows.size} file, ${fmtSize(rows.sumOf { it.size })}"
        body.addView(Ui.iconButton(c, Icon.BACK, "Semua folder", Ui.Kind.OUTLINE, 44) {
            folder = null
            load()
        }, Ui.lp(c, b = 12))
        if (rows.isNotEmpty()) {
            val bar = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            bar.addView(Ui.iconButton(c, Icon.DOWN, "Unduh semua", Ui.Kind.PRIMARY, 48) {
                for (r in rows) download(f, r.name)
            }, Ui.lp(c, 0, Ui.WRAP, r = 6, weight = 1f))
            if (f.isNotEmpty()) {
                bar.addView(Ui.iconButton(c, Icon.BOX, "Unduh ZIP", Ui.Kind.OUTLINE, 48) {
                    enqueue("$base/api/folders/${Uri.encode(f)}/zip", "$f.zip", "DropBridge")
                }, Ui.lp(c, 0, Ui.WRAP, l = 6, weight = 1f))
            }
            body.addView(bar, Ui.lp(c, b = 12))
        }
        for (r in rows) {
            val isImg = r.name.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".webp") || it.endsWith(".heic") || it.endsWith(".gif") }
            body.addView(rowView(if (isImg) Icon.IMAGE else Icon.FILE, r.name, fmtSize(r.size), Icon.DOWN) {
                download(f, r.name)
            }, Ui.lp(c, b = 8))
        }
    }

    private fun rowView(icon: Int, title: String, sub: String, action: Int?, onClick: () -> Unit): View {
        val c: android.content.Context = this
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            background = Ui.ripple(c, Ui.WHITE, 16, Ui.LINE)
            minimumHeight = Ui.dp(c, 64)
            setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10))
            setOnClickListener { onClick() }
        }
        val ic = android.widget.FrameLayout(c).apply {
            background = Ui.rounded(c, if (icon == Icon.FILE) Color.parseColor("#FDE6E1") else Ui.SOFT, 12)
            addView(IconView(c, icon, if (icon == Icon.FILE) Color.parseColor("#8A2E1B") else Ui.DEEP, 22, 1.8f),
                android.widget.FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), android.view.Gravity.CENTER))
        }
        box.addView(ic, LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)).apply { rightMargin = Ui.dp(c, 12) })
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.text(c, title, 15f, true))
        col.addView(Ui.text(c, sub, 12f, false, Ui.MUTED))
        box.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        if (action != null) {
            val btn = android.widget.FrameLayout(c).apply {
                background = Ui.rounded(c, Ui.WHITE, 22, Ui.LINE2)
                contentDescription = "Unduh $title"
                addView(IconView(c, action, Ui.ACCENT, 20, 2.1f), android.widget.FrameLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20), android.view.Gravity.CENTER))
            }
            box.addView(btn, LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)))
        } else {
            box.addView(IconView(c, Icon.BACK, Ui.MUTED, 18, 2f).apply { rotation = 180f })
        }
        return box
    }

    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var pending = 0
    private var okCount = 0
    private var failCount = 0
    private var lastError = ""

    private fun download(f: String, name: String) {
        val url = "$base/api/files/${Uri.encode(name)}?folder=${Uri.encode(f)}"
        val sub = if (f.isEmpty()) "DropBridge" else "DropBridge/$f"
        enqueue(url, name, sub)
    }

    /** Unduh di dalam aplikasi (tanpa DownloadManager), simpan ke Download/<subdir>. */
    private fun enqueue(url: String, name: String, subdir: String) {
        pending++
        showProgress("Mengunduh $name...")
        worker.execute {
            try {
                val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 5000
                c.readTimeout = 60000
                c.setRequestProperty("Authorization", "Bearer $token")
                try {
                    val code = c.responseCode
                    if (code == 401) {
                        // token kedaluwarsa: login ulang sekali
                        token = Net.ensureToken(base, true)
                        throw java.io.IOException("Sesi diperbarui, ketuk lagi")
                    }
                    if (code != 200) throw java.io.IOException("PC menjawab HTTP $code")
                    val len = c.contentLengthLong
                    val meter = Rate()
                    val counted = CountingInputStream(c.inputStream) { got ->
                        if (meter.update(got)) {
                            val pct = if (len > 0) " ${(got * 100 / len).toInt()}%" else ""
                            runOnUiThread { showProgress("Mengunduh $name:$pct · ${Xfer.rate(meter.bps)}") }
                        }
                    }
                    val saved = FileSaver.save(applicationContext, subdir, name, len, counted)
                    okCount++
                    pending--
                    runOnUiThread { showProgress("Tersimpan: Download/$subdir/$saved") }
                } finally {
                    c.disconnect()
                }
            } catch (e: Exception) {
                failCount++
                pending--
                lastError = "${e.javaClass.simpleName}: ${e.message}"
                runOnUiThread { showProgress("GAGAL mengunduh $name: $lastError") }
            }
        }
    }

    private fun showProgress(msg: String) {
        setStatus("$msg\n(selesai: $okCount, gagal: $failCount, menunggu: $pending)")
        if (!msg.startsWith("Mengunduh")) Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}
