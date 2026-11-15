package com.dropbridge.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Panel "Pilih folder tujuan" (masuk subfolder, buat folder baru, pilih). Dipakai tab Kirim dan layar Bagikan. */
object FolderPicker {

    /** Tulisan di kolom folder untuk tujuan langsung di folder penyimpanan PC. */
    const val ROOT = "Tanpa folder"
    fun isRoot(t: String) = t.trim().equals(ROOT, ignoreCase = true)

    /**
     * Lembar dari bawah. Tingkat atas: "Terakhir dipakai" (langsung terpilih saat diketuk), lalu "Semua folder"
     * dengan "Tanpa folder" paling atas. Masuk ke folder untuk memilih subfolder. Bawah: Folder baru + Pilih.
     * [current] = isi kolom folder sekarang; [onPick] menerima jalur terpilih ("Tanpa folder" untuk tujuan langsung).
     */
    fun open(act: Activity, current: String, onPick: (String) -> Unit) {
        val c: Context = act
        var pickPath = ""
        var tree: List<FolderInfo> = emptyList()
        var rootCount = -1
        val (d, box) = Ui.sheet(act, "Pilih folder tujuan")
        val crumbs = Ui.text(c, "", 13f, true, Ui.ACCENT).apply {
            setPadding(Ui.dp(c, 4), 0, 0, Ui.dp(c, 6))
            isClickable = true
            visibility = View.GONE
        }
        box.addView(crumbs)
        val list = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        val h = (c.resources.displayMetrics.heightPixels * 0.5f).toInt()
        box.addView(android.widget.ScrollView(c).apply { addView(list) }, LinearLayout.LayoutParams(Ui.MATCH, h))
        val foot = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        val newBtn = Ui.iconButton(c, Icon.FOLDER_PLUS, "Folder baru", Ui.Kind.OUTLINE, 52) {}
        val ok = Ui.button(c, "Pilih", Ui.Kind.PRIMARY, 52) {}
        foot.addView(newBtn, LinearLayout.LayoutParams(0, Ui.WRAP, 1.2f))
        foot.addView(ok, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { leftMargin = Ui.dp(c, 10) })
        box.addView(foot, Ui.lp(c, t = 10))

        fun choose(p: String) { onPick(p); d.dismiss() }

        fun row(title: String, sub: String, mono: Boolean, root: Boolean, chev: Boolean, sel: Boolean, click: () -> Unit): View {
            val r = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = Ui.dp(c, 60)
                isClickable = true
                isFocusable = true
                background = Ui.ripple(c, if (sel) Ui.SOFT else Ui.CARD, 12)
                setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 8), 0)
                contentDescription = "$title, $sub"
                setOnClickListener { click() }
            }
            r.addView(android.widget.FrameLayout(c).apply {
                background = Ui.rounded(c, Ui.FIELD, 12)
                addView(IconView(c, Icon.FOLDER, if (root) Ui.MUTED else Ui.ACCENT, 20, 2f), android.widget.FrameLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20), Gravity.CENTER))
            }, LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40)).apply { rightMargin = Ui.dp(c, 12) })
            val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            col.addView(Ui.text(c, title, 15f, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; if (mono) Ui.mono(this) })
            col.addView(Ui.text(c, sub, 12f, false, Ui.MUTED))
            r.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            if (chev) r.addView(IconView(c, Icon.CHEVRON, Ui.MUTED, 18, 2.2f))
            return r
        }
        fun cap(t: String) = Ui.caption(c, t).apply { setPadding(Ui.dp(c, 4), Ui.dp(c, 10), 0, Ui.dp(c, 4)) }
        val isNum = Regex("\\d{3}")
        fun meta(f: FolderInfo) = (if (f.subs > 0) "${f.subs} folder · " else "") + "${f.count} file"

        fun render() {
            list.removeAllViews()
            crumbs.visibility = if (pickPath.isEmpty()) View.GONE else View.VISIBLE
            crumbs.text = "‹ Semua folder › " + pathLabel(pickPath)
            val cur = current.trim()
            if (pickPath.isEmpty()) {
                val recent = Prefs.recentTargets.mapNotNull { p -> tree.firstOrNull { it.name.equals(p, true) } }.take(3)
                if (recent.isNotEmpty()) {
                    list.addView(cap("Terakhir dipakai"))
                    for (f in recent) list.addView(row(pathLabel(f.name), meta(f), isNum.containsMatchIn(f.name), false, false, f.name.equals(cur, true)) { choose(f.name) }, Ui.lp(c))
                }
                list.addView(cap("Semua folder"))
                list.addView(row(ROOT, "Langsung di folder penyimpanan" + if (rootCount > 0) " · $rootCount file" else "", false, true, false, isRoot(cur)) { choose(ROOT) }, Ui.lp(c))
            }
            val kids = tree.filter { it.parent == pickPath }.sortedBy { it.label.lowercase() }
            if (kids.isEmpty() && pickPath.isNotEmpty()) {
                list.addView(Ui.text(c, "Belum ada subfolder di sini.", 14f, false, Ui.MUTED).apply { gravity = Gravity.CENTER; setPadding(0, Ui.dp(c, 20), 0, Ui.dp(c, 20)) }, Ui.lp(c))
            }
            for (f in kids) list.addView(row(f.label, meta(f), isNum.containsMatchIn(f.label), false, true, false) { pickPath = f.name; render() }, Ui.lp(c))
            val depth = if (pickPath.isEmpty()) 0 else pickPath.count { it == '/' } + 1
            newBtn.visibility = if (depth >= MAX_DEPTH) View.GONE else View.VISIBLE
            ok.visibility = if (pickPath.isEmpty()) View.GONE else View.VISIBLE
            (ok as? TextView)?.text = "Pilih " + pickPath.substringAfterLast('/')
            ok.contentDescription = "Pilih " + pathLabel(pickPath)
        }
        crumbs.setOnClickListener { pickPath = if (pickPath.contains('/')) pickPath.substringBeforeLast('/') else ""; render() }
        ok.setOnClickListener { if (pickPath.isNotEmpty()) choose(pickPath) }
        newBtn.setOnClickListener { askNew(act, pickPath) { made -> choose(made) } }

        list.addView(Ui.text(c, "Memuat folder dari PC...", 14f, false, Ui.MUTED).apply { gravity = Gravity.CENTER; setPadding(0, Ui.dp(c, 20), 0, 0) }, Ui.lp(c))
        ok.visibility = View.GONE
        Thread {
            try {
                val base = Net.ensureServer() ?: throw java.io.IOException("PC tidak terjangkau")
                val token = Net.ensureToken(base)
                val t = Net.folderTree(base, token)
                val rc = try { Net.getJson(base, token, "/api/folders").optJSONObject("root")?.optInt("count") ?: 0 } catch (e: Exception) { 0 }
                act.runOnUiThread {
                    tree = t
                    rootCount = rc
                    // Masuk ke folder tujuan sekarang bila folder itu punya subfolder; selain itu mulai dari atas
                    val hit = t.firstOrNull { it.name.equals(current.trim(), true) }
                    pickPath = if (hit != null && t.any { it.parent == hit.name }) hit.name else ""
                    render()
                }
            } catch (e: Exception) {
                act.runOnUiThread {
                    list.removeAllViews()
                    list.addView(Ui.text(c, "PC tidak terjangkau. Ketik saja nama folder, mis. TRHU-4134291/30-1. Folder dibuat saat file terkirim.", 14f, false, Ui.MUTED).apply {
                        setPadding(Ui.dp(c, 6), Ui.dp(c, 16), Ui.dp(c, 6), 0)
                    }, Ui.lp(c))
                    newBtn.visibility = View.GONE
                }
            }
        }.start()
        d.show()
    }

    /** Dialog nama folder baru di dalam [parent]; done(jalur) dipanggil setelah dibuat di PC. */
    fun askNew(act: Activity, parent: String, done: (String) -> Unit) {
        val c: Context = act
        val box = EditText(c).apply { inputType = InputType.TYPE_CLASS_TEXT; setSingleLine() }
        Ui.styleInput(c, box, "Nama folder, mis. 30-4")
        val wrap = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(c, 20), Ui.dp(c, 4), Ui.dp(c, 20), 0)
            addView(Ui.text(c, "di " + if (parent.isEmpty()) "File (tingkat atas)" else "File › " + pathLabel(parent), 13f, false, Ui.MUTED), Ui.lp(c, b = 10))
            addView(box, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        }
        val dlg = AlertDialog.Builder(act).setTitle("Folder baru").setView(wrap)
            .setPositiveButton("Buat", null).setNegativeButton("Batal", null).create()
        dlg.setOnShowListener {
            box.requestFocus()
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val nm = box.text.toString().trim()
                if (nm.isEmpty() || nm.contains('/') || nm.contains('\\')) {
                    box.error = if (nm.isEmpty()) "Isi nama folder" else "Nama tidak boleh berisi / atau \\"
                    return@setOnClickListener
                }
                val full = if (parent.isEmpty()) nm else "$parent/$nm"
                Thread {
                    try {
                        val base = Net.ensureServer() ?: throw java.io.IOException("PC tidak terjangkau")
                        val made = Net.createFolder(base, Net.ensureToken(base), full)
                        act.runOnUiThread {
                            dlg.dismiss()
                            Toast.makeText(c, "Folder dibuat: ${pathLabel(made)}", Toast.LENGTH_SHORT).show()
                            done(made)
                        }
                    } catch (e: Exception) {
                        act.runOnUiThread { box.error = e.message ?: "Gagal membuat folder" }
                    }
                }.start()
            }
        }
        dlg.show()
    }
}
