package com.dropbridge.app

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.LruCache
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Kotak yang tingginya selalu sama dengan lebarnya (untuk thumbnail dan ikon folder). */
class SquareFrame(c: Context) : FrameLayout(c) {
    override fun onMeasure(w: Int, h: Int) = super.onMeasure(w, w)
}

/**
 * Halaman File: folder di PC sebagai daftar atau ikon, ⋮ untuk aksi, dan urutkan.
 * Di dalam folder: foto tampil sebagai thumbnail, ketuk untuk melihat, unduh ke Galeri atau bagikan.
 */
class BrowseActivity : Activity() {

    private var builtDark = false

    /** name = jalur lengkap "A/B"; parent = jalur induk ("" = tingkat atas). */
    private class FolderRow(val name: String, val count: Int, val size: Long, val modified: Double, val root: Boolean = false,
                            val parent: String = "", val subs: Int = 0) {
        val label: String get() = if (root) name else name.substringAfterLast('/')
        val depth: Int get() = if (root) 0 else name.count { it == '/' } + 1
    }
    private class FileRow(val name: String, val size: Long, val modified: Double, val kind: String, val thumb: Boolean)

    private lateinit var body: LinearLayout
    private lateinit var status: TextView
    private lateinit var scr: Screen
    private var folder: String? = null
    private var base: String = ""
    private var token: String = ""
    private var query = ""
    private var folders: List<FolderRow> = emptyList()
    private var root: FolderRow? = null
    private var files: List<FileRow> = emptyList()

    private val sp by lazy { getSharedPreferences("browse", MODE_PRIVATE) }
    private var folderSort: String
        get() = sp.getString("fsort", "new") ?: "new"
        set(v) { sp.edit().putString("fsort", v).apply() }
    private var fileSort: String
        get() = sp.getString("gsort", "new") ?: "new"
        set(v) { sp.edit().putString("gsort", v).apply() }
    private var folderGrid: Boolean
        get() = sp.getBoolean("fgrid", false)
        set(v) { sp.edit().putBoolean("fgrid", v).apply() }
    private var fileGrid: Boolean
        get() = sp.getBoolean("ggrid", true) // isi folder: foto paling enak dilihat sebagai kotak
        set(v) { sp.edit().putBoolean("ggrid", v).apply() }

    private val folderSorts = listOf("new" to "Terbaru diubah", "old" to "Terlama diubah", "az" to "Nama A–Z", "za" to "Nama Z–A", "big" to "Ukuran terbesar")
    private val fileSorts = listOf("new" to "Terbaru diambil", "old" to "Terlama diambil", "az" to "Nama A–Z", "za" to "Nama Z–A", "big" to "Ukuran terbesar")
    private val coll: Collator = Collator.getInstance(Locale("id")).apply { strength = Collator.PRIMARY }

    // ---------- Format ----------
    private fun fmtTime(ts: Double): String {
        if (ts <= 0) return ""
        val d = System.currentTimeMillis() / 1000.0 - ts
        return when {
            d < 45 -> "baru saja"
            d < 3600 -> "${(d / 60).toInt()} mnt lalu"
            d < 86400 -> "${(d / 3600).toInt()} jam lalu"
            else -> SimpleDateFormat("d MMM, HH.mm", Locale("id")).format(Date((ts * 1000).toLong()))
        }
    }

    private fun setStatus(msg: String) {
        val c: Context = this
        val bad = msg.contains("GAGAL") || msg.contains("Gagal") || msg.contains("tidak ditemukan")
        status.text = msg
        status.setTextColor(if (bad) Ui.AMBER_TX else Ui.DEEP)
        status.background = Ui.rounded(c, if (bad) Ui.AMBER_BG else Ui.SOFT, 14)
        status.visibility = if (msg.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        val c: Context = this
        scr = Ui.screen(this, "File di PC", "Menyambung ke PC...", back = false, nav = Ui.TAB_FILES)
        builtDark = Ui.dark
        status = Ui.text(c, "", 14f, true, Ui.DEEP).apply {
            setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12))
            visibility = View.GONE
        }
        scr.content.addView(status, Ui.lp(c, b = 12))
        body = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        scr.content.addView(body, Ui.lp(c))
        connectAndLoad()
    }

    override fun onDestroy() {
        super.onDestroy()
        thumbPool.shutdownNow()
        worker.shutdown()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val f = folder
        if (f != null) {
            // Naik satu tingkat: dari subfolder ke induknya, dari folder tingkat atas ke daftar folder
            folder = if (f.contains('/')) f.substringBeforeLast('/') else null
            if (folder != null) showFilesLoading()
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
        scr.subtitle.visibility = View.VISIBLE
        if (folder == null) scr.subtitle.text = "Memuat..."
        val f = folder
        Thread {
            try {
                // Daftar folder selalu dimuat: dipakai juga untuk subfolder di dalam folder
                val d = Net.getJson(base, token, "/api/folders")
                val arr = d.getJSONArray("folders")
                val rows = ArrayList<FolderRow>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val name = o.getString("name")
                    rows.add(FolderRow(name, o.optInt("count"), o.optLong("size"), o.optDouble("modified", 0.0), false,
                        o.optString("parent", if (name.contains('/')) name.substringBeforeLast('/') else ""), o.optInt("subs")))
                }
                val r = d.optJSONObject("root")
                val rootRow = if (r != null && r.optInt("count") > 0) FolderRow("Tanpa folder", r.optInt("count"), r.optLong("size"), r.optDouble("modified", 0.0), true) else null
                Prefs.folders = rows.map { it.name }
                if (f == null) {
                    runOnUiThread { folders = rows; root = rootRow; if (folder == null) showFolders() }
                } else {
                    val fr = rows
                    val fd = Net.getJson(base, token, "/api/files?folder=" + Uri.encode(f))
                    val farr = fd.getJSONArray("files")
                    val rows = ArrayList<FileRow>()
                    for (i in 0 until farr.length()) {
                        val o = farr.getJSONObject(i)
                        rows.add(FileRow(o.getString("name"), o.optLong("size"), o.optDouble("modified", 0.0), o.optString("kind", "other"), o.optBoolean("thumb")))
                    }
                    runOnUiThread { folders = fr; root = rootRow; files = rows; if (folder == f) showFiles(f) }
                }
            } catch (e: Exception) {
                runOnUiThread { setStatus("Gagal memuat: ${e.message}") }
            }
        }.start()
    }

    // ---------- Bagian yang dipakai bersama ----------
    private fun <T> sorted(list: List<T>, how: String, name: (T) -> String, size: (T) -> Long, time: (T) -> Double): List<T> = when (how) {
        "az" -> list.sortedWith { a, b -> coll.compare(name(a), name(b)) }
        "za" -> list.sortedWith { a, b -> coll.compare(name(b), name(a)) }
        "big" -> list.sortedByDescending(size)
        "old" -> list.sortedBy(time)
        else -> list.sortedByDescending(time)
    }

    private fun icon(c: Context, kind: Int, color: Int, size: Int = 20, sw: Float = 2f): View =
        IconView(c, kind, color, size, sw)

    /** Tombol ikon bulat tanpa bingkai (untuk ⋮). */
    private fun dotsButton(c: Context, label: String, color: Int = Ui.MUTED, onClick: (View) -> Unit): FrameLayout =
        FrameLayout(c).apply {
            isClickable = true
            isFocusable = true
            contentDescription = label
            background = Ui.ripple(c, Color.TRANSPARENT, 22)
            setOnClickListener { onClick(it) }
            addView(icon(c, Icon.DOTS, color, 20), FrameLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20), Gravity.CENTER))
        }

    /** Baris "Urutkan: ... ▾" dan tombol daftar/ikon. */
    private fun toolbar(c: Context, sortLabel: String, grid: Boolean, onSort: () -> Unit, onView: (Boolean) -> Unit, onNew: (() -> Unit)? = null): View {
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val sort = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            contentDescription = "Urutkan: $sortLabel"
            background = Ui.ripple(c, Color.TRANSPARENT, 12)
            minimumHeight = Ui.dp(c, 44)
            setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 8), 0)
            setOnClickListener { onSort() }
            addView(icon(c, Icon.SORT, Ui.ACCENT, 18))
            addView(Ui.text(c, sortLabel, 14f, true).apply { setPadding(Ui.dp(c, 6), 0, Ui.dp(c, 4), 0) })
            addView(icon(c, Icon.BACK, Ui.MUTED, 14, 2.2f).apply { rotation = -90f })
        }
        row.addView(sort, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        if (onNew != null) {
            row.addView(FrameLayout(c).apply {
                isClickable = true
                isFocusable = true
                contentDescription = "Folder baru di sini"
                background = Ui.ripple(c, Ui.CARD, 12, Ui.LINE)
                setOnClickListener { onNew() }
                addView(icon(c, Icon.FOLDER_PLUS, Ui.ACCENT, 20), FrameLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20), Gravity.CENTER))
            }, LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 42)).apply { rightMargin = Ui.dp(c, 8) })
        }
        val seg = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Ui.rounded(c, Ui.CARD, 12, Ui.LINE)
            setPadding(Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3))
        }
        for ((isGrid, ic, label) in listOf(Triple(false, Icon.LIST, "Tampilan daftar"), Triple(true, Icon.GRID, "Tampilan ikon"))) {
            val on = isGrid == grid
            seg.addView(FrameLayout(c).apply {
                isClickable = true
                isFocusable = true
                contentDescription = label
                isSelected = on
                background = if (on) Ui.rounded(c, Ui.FILL, 9) else Ui.ripple(c, Color.TRANSPARENT, 9)
                setOnClickListener { if (!on) onView(isGrid) }
                addView(icon(c, ic, if (on) Ui.WHITE else Ui.MUTED, 18), FrameLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18), Gravity.CENTER))
            }, LinearLayout.LayoutParams(Ui.dp(c, 42), Ui.dp(c, 36)))
        }
        row.addView(seg)
        return row
    }

    /** Lembar "Urutkan" yang muncul dari bawah layar. */
    private fun sortSheet(opts: List<Pair<String, String>>, cur: String, pick: (String) -> Unit) {
        val c: Context = this
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val r = Ui.dp(c, 24).toFloat()
            background = GradientDrawable().apply { setColor(Ui.CARD); cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) }
            setPadding(Ui.dp(c, 20), Ui.dp(c, 10), Ui.dp(c, 20), Ui.dp(c, 24))
        }
        box.addView(View(c).apply { background = Ui.rounded(c, Ui.LINE, 4) }, LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = Ui.dp(c, 12) })
        box.addView(Ui.text(c, "Urutkan", 18f, true), Ui.lp(c, b = 4))
        for ((i, o) in opts.withIndex()) {
            val on = o.first == cur
            if (i > 0) box.addView(View(c).apply { setBackgroundColor(Ui.DIVIDER) }, LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(c, 1)))
            val row = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                minimumHeight = Ui.dp(c, 52)
                setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 8), 0)
                background = Ui.ripple(c, Ui.CARD, 10)
                contentDescription = o.second + if (on) ", dipilih" else ""
                setOnClickListener { d.dismiss(); if (!on) pick(o.first) }
            }
            row.addView(Ui.text(c, o.second, 16f, on), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            if (on) row.addView(icon(c, Icon.CHECK, Ui.ACCENT, 20, 2.6f))
            box.addView(row, Ui.lp(c))
        }
        d.setContentView(box)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }
        d.show()
    }

    private fun popup(anchor: View, items: List<Pair<String, () -> Unit>>) {
        val pm = PopupMenu(this, anchor, Gravity.END)
        items.forEachIndexed { i, it -> pm.menu.add(0, i, i, it.first) }
        pm.setOnMenuItemClickListener { mi -> items[mi.itemId].second(); true }
        pm.show()
    }

    private fun sendHere(name: String) {
        Prefs.lastFolder = name
        startActivity(Intent(this, SendActivity::class.java).putExtra("pick", true))
    }

    private fun folderMenu(f: FolderRow, anchor: View) {
        val items = ArrayList<Pair<String, () -> Unit>>()
        items.add("Kirim ke sini" to { sendHere(if (f.root) "" else f.name) })
        if (!f.root && f.depth < MAX_DEPTH) items.add("Folder baru di dalamnya" to { askNewFolder(f.name) })
        if (!f.root) items.add("Unduh semua (ZIP)" to { enqueue("$base/api/folders/${encPath(f.name)}/zip", "${f.label}.zip", "DropBridge") })
        popup(anchor, items)
    }

    private fun open(f: FolderRow) {
        folder = if (f.root) "" else f.name
        showFilesLoading()
        load()
    }

    /** Dialog "Folder baru" di dalam [parent] ("" = tingkat atas). Folder baru langsung dibuka. */
    private fun askNewFolder(parent: String) {
        val c: Context = this
        val box = android.widget.EditText(c).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setSingleLine()
        }
        Ui.styleInput(c, box, "Nama folder, mis. 30-4")
        val wrap = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(c, 20), Ui.dp(c, 4), Ui.dp(c, 20), 0)
            addView(Ui.text(c, "di " + if (parent.isEmpty()) "File (tingkat atas)" else "File › " + pathLabel(parent), 13f, false, Ui.MUTED), Ui.lp(c, b = 10))
            addView(box, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        }
        val dlg = android.app.AlertDialog.Builder(this)
            .setTitle("Folder baru")
            .setView(wrap)
            .setPositiveButton("Buat", null)
            .setNegativeButton("Batal", null)
            .create()
        dlg.setOnShowListener {
            box.requestFocus()
            dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val nm = box.text.toString().trim()
                if (nm.isEmpty() || nm.contains('/') || nm.contains('\\')) {
                    box.error = if (nm.isEmpty()) "Isi nama folder" else "Nama tidak boleh berisi / atau \\"
                    return@setOnClickListener
                }
                val full = if (parent.isEmpty()) nm else "$parent/$nm"
                Thread {
                    try {
                        val made = Net.createFolder(base, token, full)
                        runOnUiThread {
                            dlg.dismiss()
                            Toast.makeText(c, "Folder dibuat: ${pathLabel(made)}", Toast.LENGTH_SHORT).show()
                            folder = made
                            showFilesLoading()
                            load()
                        }
                    } catch (e: Exception) {
                        runOnUiThread { box.error = e.message ?: "Gagal membuat folder" }
                    }
                }.start()
            }
        }
        dlg.show()
    }

    /** Membagi daftar ke baris berisi [cols] kolom yang sama lebar. */
    private fun gridRows(c: Context, parent: LinearLayout, views: List<View>, cols: Int, gap: Int) {
        var i = 0
        while (i < views.size) {
            val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            for (k in 0 until cols) {
                val v = views.getOrNull(i + k) ?: View(c)
                row.addView(v, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { if (k > 0) leftMargin = Ui.dp(c, gap) })
            }
            parent.addView(row, Ui.lp(c, b = gap))
            i += cols
        }
    }

    // ---------- Daftar folder ----------
    private fun showFolders() {
        val c: Context = this
        body.removeAllViews()
        scr.title.text = "File di PC"
        scr.subtitle.visibility = View.VISIBLE
        val top = folders.count { it.parent.isEmpty() }
        scr.subtitle.text = if (folders.isEmpty() && root == null) "Belum ada folder di PC" else "$top folder"
        val search = Ui.input(c, "Cari folder", android.text.InputType.TYPE_CLASS_TEXT, query)
        body.addView(search, Ui.lp(c, b = 6))
        body.addView(toolbar(c, folderSorts.first { it.first == folderSort }.second, folderGrid,
            onSort = { sortSheet(folderSorts, folderSort) { folderSort = it; showFolders() } },
            onView = { folderGrid = it; showFolders() }, onNew = { askNewFolder("") }), Ui.lp(c, b = 8))
        val list = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        body.addView(list, Ui.lp(c))
        fun fill() {
            list.removeAllViews()
            val q = query.trim().lowercase()
            // Tanpa pencarian: folder tingkat atas. Dengan pencarian: semua tingkat, tampil jalur lengkap.
            val shown = ArrayList(sorted(folders.filter { if (q.isEmpty()) it.parent.isEmpty() else it.name.lowercase().contains(q) }, folderSort, { it.name }, { it.size }, { it.modified }))
            val r = root
            if (r != null && (q.isEmpty() || "tanpa folder".contains(q))) shown.add(r)
            if (shown.isEmpty()) {
                list.addView(Ui.text(c, if (q.isEmpty()) "Belum ada folder. Kirim file ke sebuah nama folder dan folder itu dibuat otomatis." else "Tidak ada folder yang cocok.", 14f, false, Ui.MUTED).apply {
                    gravity = Gravity.CENTER; setPadding(0, Ui.dp(c, 24), 0, Ui.dp(c, 24))
                })
                return
            }
            folderList(c, list, shown, q.isNotEmpty())
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { query = s?.toString() ?: ""; fill() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        fill()
        body.addView(Ui.button(c, "Muat ulang", Ui.Kind.OUTLINE, 44) { load() }, Ui.lp(c, t = 12))
    }

    /** Folder sebagai daftar (satu kartu) atau ikon (2 kolom), sesuai pilihan tampilan. */
    private fun folderList(c: Context, parent: LinearLayout, shown: List<FolderRow>, full: Boolean) {
        if (folderGrid) {
            gridRows(c, parent, shown.map { folderTile(c, it, full) }, 2, 10)
        } else {
            val card = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                background = Ui.rounded(c, Ui.CARD, 20, Ui.LINE)
                setPadding(Ui.dp(c, 12), Ui.dp(c, 2), Ui.dp(c, 4), Ui.dp(c, 2))
            }
            shown.forEachIndexed { i, f ->
                if (i > 0) card.addView(View(c).apply { setBackgroundColor(Ui.DIVIDER) }, LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(c, 1)))
                card.addView(folderRow(c, f, full), Ui.lp(c))
            }
            parent.addView(card, Ui.lp(c))
        }
    }

    private fun folderSub(f: FolderRow, withTime: Boolean): String {
        val t = if (withTime && !f.root) fmtTime(f.modified) else ""
        return (if (f.subs > 0) "${f.subs} folder · " else "") + "${f.count} file · ${Xfer.size(f.size)}" + if (t.isNotEmpty()) " · $t" else ""
    }

    private fun folderIcon(c: Context, f: FolderRow, box: Int, ic: Int): FrameLayout =
        FrameLayout(c).apply {
            background = Ui.rounded(c, if (f.root) Ui.FIELD else Ui.SOFT2, 12)
            addView(icon(c, Icon.FOLDER, if (f.root) Ui.MUTED else Ui.ACCENT, ic, 2f), FrameLayout.LayoutParams(Ui.dp(c, ic), Ui.dp(c, ic), Gravity.CENTER))
        }

    private fun folderRow(c: Context, f: FolderRow, full: Boolean = false): View {
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; minimumHeight = Ui.dp(c, 66) }
        val main = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            background = Ui.ripple(c, Ui.CARD, 12)
            setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 10))
            setOnClickListener { open(f) }
        }
        main.addView(folderIcon(c, f, 42, 20), LinearLayout.LayoutParams(Ui.dp(c, 42), Ui.dp(c, 42)).apply { rightMargin = Ui.dp(c, 12) })
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.text(c, if (full) pathLabel(f.name) else f.label, 15f, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.START })
        col.addView(Ui.text(c, folderSub(f, true), 12f, false, Ui.MUTED).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        main.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        row.addView(main, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        row.addView(dotsButton(c, "Pilihan untuk ${f.name}") { folderMenu(f, it) }, LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)))
        return row
    }

    private fun folderTile(c: Context, f: FolderRow, full: Boolean = false): View {
        val frame = FrameLayout(c).apply { background = Ui.rounded(c, Ui.CARD, 18, Ui.LINE) }
        val main = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            background = Ui.ripple(c, Color.TRANSPARENT, 18)
            setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 12))
            setOnClickListener { open(f) }
        }
        main.addView(folderIcon(c, f, 0, 34), LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(c, 78)).apply { bottomMargin = Ui.dp(c, 10) })
        main.addView(Ui.text(c, if (full) pathLabel(f.name) else f.label, 14f, true).apply { maxLines = 2; minLines = 2; ellipsize = TextUtils.TruncateAt.END })
        main.addView(Ui.text(c, (if (f.subs > 0) "${f.subs} folder · " else "") + "${f.count} file", 12f, false, Ui.MUTED))
        frame.addView(main, FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        frame.addView(dotsButton(c, "Pilihan untuk ${f.name}") { folderMenu(f, it) }.apply { background = Ui.ripple(c, Ui.CARD, 22) },
            FrameLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44), Gravity.TOP or Gravity.END).apply { topMargin = Ui.dp(c, 4); rightMargin = Ui.dp(c, 2) })
        return frame
    }

    // ---------- Isi folder ----------
    private fun showFilesLoading() {
        files = emptyList()
        body.removeAllViews()
        body.addView(Ui.text(this, "Memuat...", 14f, false, Ui.MUTED), Ui.lp(this))
    }

    private fun showFiles(f: String) {
        val c: Context = this
        body.removeAllViews()
        scr.title.text = "File di PC"
        scr.subtitle.text = "${folders.count { it.parent.isEmpty() }} folder"
        val label = if (f.isEmpty()) "Tanpa folder" else f.substringAfterLast('/')
        val parentPath = if (f.contains('/')) f.substringBeforeLast('/') else null
        val subs = if (f.isEmpty()) emptyList() else sorted(folders.filter { it.parent == f }, folderSort, { it.name }, { it.size }, { it.modified })

        // Jejak lokasi: File › A › B (ketuk untuk naik)
        if (f.isNotEmpty()) {
            val crumbs = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val parts = f.split('/')
            fun crumb(t: String, target: String?, last: Boolean) = Ui.text(c, t, 13f, true, if (last) Ui.INK else Ui.ACCENT).apply {
                minimumHeight = Ui.dp(c, 32)
                gravity = Gravity.CENTER_VERTICAL
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                if (!last) {
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { folder = target; if (target != null) showFilesLoading(); load() }
                }
            }
            crumbs.addView(crumb("File", null, false))
            parts.forEachIndexed { i, x ->
                crumbs.addView(Ui.text(c, "  ›  ", 13f, false, Ui.MUTED))
                crumbs.addView(crumb(x, parts.take(i + 1).joinToString("/"), i == parts.size - 1))
            }
            body.addView(android.widget.HorizontalScrollView(c).apply { isHorizontalScrollBarEnabled = false; addView(crumbs) }, Ui.lp(c, b = 2))
        }

        // Kepala: kembali, nama folder, ⋮
        val head = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(FrameLayout(c).apply {
            isClickable = true
            isFocusable = true
            contentDescription = if (parentPath != null) "Kembali ke ${pathLabel(parentPath)}" else "Kembali ke semua folder"
            background = Ui.ripple(c, Ui.CARD, 14, Ui.LINE)
            setOnClickListener { folder = parentPath; if (parentPath != null) showFilesLoading(); load() }
            addView(icon(c, Icon.BACK, Ui.INK, 22, 2.2f), FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.CENTER))
        }, LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)).apply { rightMargin = Ui.dp(c, 12) })
        val tcol = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        tcol.addView(Ui.text(c, label, 19f, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        tcol.addView(Ui.text(c, (if (subs.isNotEmpty()) "${subs.size} folder · " else "") + "${files.size} file · ${Xfer.size(files.sumOf { it.size })}", 13f, false, Ui.MUTED))
        head.addView(tcol, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        if (f.isNotEmpty()) {
            head.addView(dotsButton(c, "Pilihan folder", Ui.INK) { v ->
                popup(v, listOf("Unduh semua (ZIP)" to { enqueue("$base/api/folders/${encPath(f)}/zip", "$label.zip", "DropBridge") },
                    "Unduh semua file" to { for (r in files) download(f, r.name) }))
            }.apply { background = Ui.ripple(c, Ui.CARD, 14, Ui.LINE) }, LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)).apply { leftMargin = Ui.dp(c, 8) })
        }
        body.addView(head, Ui.lp(c, b = 12))
        body.addView(Ui.iconButton(c, Icon.UP, "Kirim ke folder ini", Ui.Kind.PRIMARY, 52) { sendHere(f) }, Ui.lp(c, b = 6))
        body.addView(toolbar(c, fileSorts.first { it.first == fileSort }.second, fileGrid,
            onSort = { sortSheet(fileSorts, fileSort) { fileSort = it; showFiles(f) } },
            onView = { fileGrid = it; showFiles(f) },
            onNew = if (f.isNotEmpty() && f.count { it == '/' } + 1 < MAX_DEPTH) ({ askNewFolder(f) }) else null), Ui.lp(c, b = 8))

        // Subfolder di atas, file di bawah
        if (subs.isNotEmpty()) {
            body.addView(Ui.text(c, "Folder", 13f, true, Ui.MUTED), Ui.lp(c, t = 4, b = 6))
            folderList(c, body, subs, false)
            if (files.isNotEmpty()) body.addView(Ui.text(c, "File", 13f, true, Ui.MUTED), Ui.lp(c, t = 10, b = 6))
        }
        if (files.isEmpty()) {
            if (subs.isEmpty()) body.addView(Ui.text(c, "Folder ini kosong. Ketuk Kirim ke folder ini, atau buat subfolder dengan ikon folder+.", 14f, false, Ui.MUTED).apply { gravity = Gravity.CENTER; setPadding(0, Ui.dp(c, 24), 0, Ui.dp(c, 24)) }, Ui.lp(c))
            return
        }
        val shown = sorted(files, fileSort, { it.name }, { it.size }, { it.modified })
        if (fileGrid) {
            gridRows(c, body, shown.map { fileTile(c, f, it, shown) }, 3, 6)
        } else {
            val card = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                background = Ui.rounded(c, Ui.CARD, 20, Ui.LINE)
                setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4))
            }
            shown.forEachIndexed { i, r ->
                if (i > 0) card.addView(View(c).apply { setBackgroundColor(Ui.DIVIDER) }, LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(c, 1)))
                card.addView(fileRow(c, f, r, shown), Ui.lp(c))
            }
            body.addView(card, Ui.lp(c))
        }
    }

    private fun isImage(r: FileRow) = r.kind == "image"

    private fun openFile(f: String, r: FileRow, shown: List<FileRow>) {
        if (isImage(r)) viewer(f, shown.filter { isImage(it) }, r) else download(f, r.name)
    }

    private fun fileMenu(f: String, r: FileRow, anchor: View) {
        popup(anchor, listOf("Unduh" to { download(f, r.name) }, "Bagikan" to { share(f, r.name) }))
    }

    /** Gambar kecil: thumbnail dari PC, atau lencana jenis file. */
    private fun picView(c: Context, f: String, r: FileRow, badgeSp: Float): FrameLayout {
        val frame = FrameLayout(c).apply { background = Ui.rounded(c, Ui.FIELD, 0) }
        val ext = r.name.substringAfterLast('.', "FILE").uppercase().take(4)
        val badge = Ui.text(c, ext, badgeSp, true, if (r.kind == "doc") (if (Ui.dark) Color.parseColor("#93C5FD") else Color.parseColor("#1D4ED8")) else Ui.MUTED).apply { gravity = Gravity.CENTER }
        frame.addView(badge, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        if (r.thumb) {
            val iv = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
            frame.addView(iv, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
            loadThumb(iv, f, r.name)
        }
        if (r.kind == "video") {
            frame.addView(FrameLayout(c).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(150, 11, 31, 28)) }
                addView(icon(c, Icon.PLAY, Ui.WHITE, 14), FrameLayout.LayoutParams(Ui.dp(c, 14), Ui.dp(c, 14), Gravity.CENTER))
            }, FrameLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30), Gravity.CENTER))
        }
        frame.clipToOutline = true
        return frame
    }

    private fun fileTile(c: Context, f: String, r: FileRow, shown: List<FileRow>): View {
        val sq = SquareFrame(c).apply {
            background = Ui.rounded(c, if (r.thumb) Ui.FIELD else Ui.CARD, 12, if (r.thumb) 0 else Ui.LINE)
            clipToOutline = true
            isClickable = true
            isFocusable = true
            contentDescription = r.name
            setOnClickListener { openFile(f, r, shown) }
        }
        if (r.thumb) {
            sq.addView(picView(c, f, r, 13f), FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        } else {
            val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(Ui.dp(c, 6), 0, Ui.dp(c, 6), 0) }
            col.addView(picView(c, f, r, 13f).apply { background = Ui.rounded(c, Ui.FIELD, 12) }, LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)).apply { gravity = Gravity.CENTER_HORIZONTAL })
            col.addView(Ui.text(c, r.name, 11f, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.MIDDLE; gravity = Gravity.CENTER; setPadding(0, Ui.dp(c, 6), 0, 0) })
            sq.addView(col, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        }
        val dots = dotsButton(c, "Pilihan untuk ${r.name}", if (r.thumb) Ui.WHITE else Ui.MUTED) { fileMenu(f, r, it) }
        if (r.thumb) dots.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(115, 11, 31, 28)) }
        sq.addView(dots, FrameLayout.LayoutParams(Ui.dp(c, 34), Ui.dp(c, 34), Gravity.TOP or Gravity.END).apply { topMargin = Ui.dp(c, 4); rightMargin = Ui.dp(c, 4) })
        return sq
    }

    private fun fileRow(c: Context, f: String, r: FileRow, shown: List<FileRow>): View {
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; minimumHeight = Ui.dp(c, 64) }
        val main = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            background = Ui.ripple(c, Ui.CARD, 12)
            setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6))
            setOnClickListener { openFile(f, r, shown) }
        }
        main.addView(picView(c, f, r, 11f).apply { background = Ui.rounded(c, Ui.FIELD, 10) }, LinearLayout.LayoutParams(Ui.dp(c, 52), Ui.dp(c, 52)).apply { rightMargin = Ui.dp(c, 12) })
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.text(c, r.name, 14f, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.MIDDLE })
        col.addView(Ui.text(c, Xfer.size(r.size) + " · " + fmtTime(r.modified), 12f, false, Ui.MUTED))
        main.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        row.addView(main, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        row.addView(dotsButton(c, "Pilihan untuk ${r.name}") { fileMenu(f, r, it) }, LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)))
        return row
    }

    // ---------- Thumbnail ----------
    private val thumbPool = Executors.newFixedThreadPool(3)
    private val thumbs = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private fun fetch(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 20000
        conn.setRequestProperty("Authorization", "Bearer $token")
        try {
            if (conn.responseCode != 200) throw java.io.IOException("HTTP ${conn.responseCode}")
            val out = ByteArrayOutputStream()
            conn.inputStream.use { it.copyTo(out) }
            return out.toByteArray()
        } finally {
            conn.disconnect()
        }
    }

    private fun loadThumb(iv: ImageView, f: String, name: String) {
        val key = "$f/$name"
        iv.tag = key
        thumbs.get(key)?.let { iv.setImageBitmap(it); return }
        val url = "$base/api/files/${Uri.encode(name)}/thumb?folder=${Uri.encode(f)}"
        try {
            thumbPool.execute {
                try {
                    val bytes = fetch(url)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@execute
                    thumbs.put(key, bmp)
                    iv.post { if (iv.tag == key) iv.setImageBitmap(bmp) }
                } catch (e: Exception) { /* biarkan lencana jenis file yang tampil */ }
            }
        } catch (e: java.util.concurrent.RejectedExecutionException) { /* layar sudah ditutup */ }
    }

    // ---------- Lihat foto ----------
    private fun viewer(f: String, list: List<FileRow>, start: FileRow) {
        if (list.isEmpty()) return
        val c: Context = this
        var idx = list.indexOfFirst { it.name == start.name }.coerceAtLeast(0)
        val d = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        val iv = ImageView(c).apply { scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = start.name }
        val title = Ui.text(c, "", 14f, true, Ui.WHITE).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.MIDDLE }
        val pos = Ui.text(c, "", 12f, false, Color.parseColor("#B8C7C3"))
        root.addView(iv, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
        val bar = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 20))
            setBackgroundColor(Color.parseColor("#0B1F1C"))
        }
        bar.addView(title)
        bar.addView(pos, Ui.lp(c, b = 10))
        val acts = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        acts.addView(Ui.iconButton(c, Icon.DOWN, "Unduh", Ui.Kind.PRIMARY, 46) { download(f, list[idx].name) }, Ui.lp(c, 0, Ui.WRAP, r = 6, weight = 1f))
        acts.addView(Ui.iconButton(c, Icon.SHARE, "Bagikan", Ui.Kind.SOFT, 46) { share(f, list[idx].name) }, Ui.lp(c, 0, Ui.WRAP, l = 6, r = 6, weight = 1f))
        acts.addView(Ui.button(c, "Tutup", Ui.Kind.OUTLINE, 46) { d.dismiss() }, Ui.lp(c, Ui.WRAP, Ui.WRAP, l = 6))
        bar.addView(acts)
        root.addView(bar, Ui.lp(c))

        val w = resources.displayMetrics.widthPixels
        fun show() {
            val r = list[idx]
            title.text = r.name
            pos.text = "${idx + 1} dari ${list.size} · geser untuk foto lain"
            iv.contentDescription = r.name
            val key = "$f/${r.name}"
            iv.tag = key
            thumbs.get(key)?.let { iv.setImageBitmap(it) } ?: iv.setImageDrawable(null)
            val url = "$base/api/files/${Uri.encode(r.name)}?folder=${Uri.encode(f)}&inline=1"
            try {
                thumbPool.execute {
                    try {
                        val bytes = fetch(url)
                        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                        var s = 1
                        while (o.outWidth / (s * 2) >= w && o.outHeight / (s * 2) >= w) s *= 2
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s }) ?: return@execute
                        iv.post { if (iv.tag == key) iv.setImageBitmap(bmp) }
                    } catch (e: Exception) {
                        iv.post { if (iv.tag == key) Toast.makeText(c, "Foto gagal dimuat", Toast.LENGTH_SHORT).show() }
                    }
                }
            } catch (e: java.util.concurrent.RejectedExecutionException) { /* abaikan */ }
        }
        var downX = 0f
        iv.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.x; true }
                MotionEvent.ACTION_UP -> {
                    val dx = e.x - downX
                    if (dx < -Ui.dp(c, 60) && idx < list.size - 1) { idx++; show() }
                    else if (dx > Ui.dp(c, 60) && idx > 0) { idx--; show() }
                    true
                }
                else -> true
            }
        }
        d.setContentView(root)
        show()
        d.show()
    }

    // ---------- Unduh dan bagikan ----------
    private val worker = Executors.newSingleThreadExecutor()
    private var pending = 0
    private var okCount = 0
    private var failCount = 0
    private var lastError = ""

    private fun download(f: String, name: String) {
        val url = "$base/api/files/${Uri.encode(name)}?folder=${Uri.encode(f)}"
        val sub = if (f.isEmpty()) "DropBridge" else "DropBridge/$f"
        enqueue(url, name, sub)
    }

    private fun whereSaved(name: String, subdir: String, saved: String): String {
        val mime = FileSaver.mimeOf(name)
        val top = when {
            mime.startsWith("image/") -> "Galeri (Pictures)"
            mime.startsWith("video/") -> "Galeri (Movies)"
            else -> "Download"
        }
        return "$top/$subdir/$saved"
    }

    /** Unduh di dalam aplikasi, simpan lewat FileSaver (foto ke Galeri, lainnya ke Download). */
    private fun enqueue(url: String, name: String, subdir: String) {
        pending++
        showProgress("Mengunduh $name...")
        worker.execute {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 60000
                conn.setRequestProperty("Authorization", "Bearer $token")
                try {
                    val code = conn.responseCode
                    if (code == 401) {
                        token = Net.ensureToken(base, true)
                        throw java.io.IOException("Sesi diperbarui, ketuk lagi")
                    }
                    if (code != 200) throw java.io.IOException("PC menjawab HTTP $code")
                    val len = conn.contentLengthLong
                    val meter = Rate()
                    val counted = CountingInputStream(conn.inputStream) { got ->
                        if (meter.update(got)) {
                            val pct = if (len > 0) " ${(got * 100 / len).toInt()}%" else ""
                            runOnUiThread { showProgress("Mengunduh $name:$pct · ${Xfer.rate(meter.bps)}") }
                        }
                    }
                    val saved = FileSaver.save(applicationContext, subdir, name, len, counted)
                    okCount++
                    pending--
                    runOnUiThread { showProgress("Tersimpan: ${whereSaved(name, subdir, saved)}") }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                failCount++
                pending--
                lastError = "${e.javaClass.simpleName}: ${e.message}"
                runOnUiThread { showProgress("GAGAL mengunduh $name: $lastError") }
            }
        }
    }

    /** Ambil file ke cache aplikasi lalu buka lembar Bagikan Android (WhatsApp, Drive, dll). */
    private fun share(f: String, name: String) {
        Toast.makeText(this, "Menyiapkan $name...", Toast.LENGTH_SHORT).show()
        val url = "$base/api/files/${Uri.encode(name)}?folder=${Uri.encode(f)}"
        worker.execute {
            try {
                val safe = "share_" + name.replace(Regex("[^A-Za-z0-9_.-]"), "_")
                val out = File(CaptureProvider.dir(applicationContext), safe)
                out.writeBytes(fetch(url))
                val uri = CaptureProvider.uriFor(safe)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = FileSaver.mimeOf(name)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runOnUiThread { startActivity(Intent.createChooser(send, "Bagikan $name")) }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Gagal menyiapkan file: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun showProgress(msg: String) {
        setStatus("$msg\n(selesai: $okCount, gagal: $failCount, menunggu: $pending)")
        if (!msg.startsWith("Mengunduh")) Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        if (Ui.themeStale(this, builtDark)) recreate()
    }
}
