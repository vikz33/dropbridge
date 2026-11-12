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

    /**
     * Lembar dari bawah: jejak lokasi, daftar subfolder, Folder baru di sini, Pilih.
     * [current] = folder yang sedang tertulis (panel mulai dari sana); [onPick] menerima jalur terpilih.
     */
    fun open(act: Activity, current: String, onPick: (String) -> Unit) {
        val c: Context = act
        var pickPath = ""
        var tree: List<FolderInfo> = emptyList()
        val d = android.app.Dialog(act)
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val r = Ui.dp(c, 24).toFloat()
            background = android.graphics.drawable.GradientDrawable().apply { setColor(Ui.CARD); cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) }
            setPadding(Ui.dp(c, 16), Ui.dp(c, 10), Ui.dp(c, 16), Ui.dp(c, 20))
        }
        box.addView(View(c).apply { background = Ui.rounded(c, Ui.LINE, 4) }, LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = Ui.dp(c, 8) })
        val head = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val up = android.widget.FrameLayout(c).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "Naik satu tingkat"
            background = Ui.ripple(c, Ui.CARD, 12)
            addView(IconView(c, Icon.BACK, Ui.INK, 22, 2.2f), android.widget.FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.CENTER))
        }
        head.addView(up, LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)))
        head.addView(Ui.text(c, "Pilih folder tujuan", 18f, true).apply { setPadding(Ui.dp(c, 6), 0, 0, 0) }, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        head.addView(Ui.text(c, "Tutup", 14f, true, Ui.ACCENT).apply {
            minimumHeight = Ui.dp(c, 44)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 4), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { d.dismiss() }
        })
        box.addView(head)
        val crumbs = Ui.text(c, "", 13f, true, Ui.MUTED).apply { setPadding(Ui.dp(c, 6), Ui.dp(c, 2), 0, Ui.dp(c, 6)) }
        box.addView(crumbs)
        val list = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        box.addView(android.widget.ScrollView(c).apply { addView(list) }, LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(c, 260)))
        val newBtn = Ui.iconButton(c, Icon.FOLDER_PLUS, "Folder baru di sini", Ui.Kind.OUTLINE, 48) {}
        box.addView(newBtn, Ui.lp(c, t = 8))
        val ok = Ui.button(c, "Pilih", Ui.Kind.PRIMARY, 52) {}
        box.addView(ok, Ui.lp(c, t = 8))

        fun render() {
            up.visibility = if (pickPath.isEmpty()) View.INVISIBLE else View.VISIBLE
            crumbs.text = if (pickPath.isEmpty()) "File" else "File › " + pathLabel(pickPath)
            list.removeAllViews()
            val kids = tree.filter { it.parent == pickPath }.sortedBy { it.label.lowercase() }
            if (kids.isEmpty()) {
                list.addView(Ui.text(c, if (pickPath.isEmpty()) "Belum ada folder." else "Belum ada subfolder di sini.", 14f, false, Ui.MUTED).apply {
                    gravity = Gravity.CENTER; setPadding(0, Ui.dp(c, 20), 0, Ui.dp(c, 20))
                }, Ui.lp(c))
            }
            kids.forEachIndexed { i, f ->
                if (i > 0) list.addView(View(c).apply { setBackgroundColor(Ui.DIVIDER) }, LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(c, 1)))
                val row = LinearLayout(c).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = Ui.dp(c, 58)
                    isClickable = true
                    isFocusable = true
                    background = Ui.ripple(c, Ui.CARD, 12)
                    setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0)
                    contentDescription = "Buka ${f.label}"
                }
                row.addView(android.widget.FrameLayout(c).apply {
                    background = Ui.rounded(c, Ui.SOFT2, 11)
                    addView(IconView(c, Icon.FOLDER, Ui.ACCENT, 18, 2f), android.widget.FrameLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18), Gravity.CENTER))
                }, LinearLayout.LayoutParams(Ui.dp(c, 38), Ui.dp(c, 38)).apply { rightMargin = Ui.dp(c, 12) })
                val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
                col.addView(Ui.text(c, f.label, 15f, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                col.addView(Ui.text(c, (if (f.subs > 0) "${f.subs} folder · " else "") + "${f.count} file", 12f, false, Ui.MUTED))
                row.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
                row.addView(IconView(c, Icon.BACK, Ui.MUTED, 16, 2.2f).apply { rotation = 180f })
                row.setOnClickListener { pickPath = f.name; render() }
                list.addView(row, Ui.lp(c))
            }
            val depth = if (pickPath.isEmpty()) 0 else pickPath.count { it == '/' } + 1
            newBtn.visibility = if (depth >= MAX_DEPTH) View.GONE else View.VISIBLE
            ok.visibility = if (pickPath.isEmpty()) View.GONE else View.VISIBLE
            (ok as? TextView)?.text = "Pilih " + pickPath.substringAfterLast('/')
            ok.contentDescription = "Pilih " + pathLabel(pickPath)
        }
        up.setOnClickListener { pickPath = if (pickPath.contains('/')) pickPath.substringBeforeLast('/') else ""; render() }
        ok.setOnClickListener { if (pickPath.isNotEmpty()) { onPick(pickPath); d.dismiss() } }
        newBtn.setOnClickListener {
            askNew(act, pickPath) { made -> onPick(made); d.dismiss() }
        }

        // Mulai dari folder tujuan sekarang bila sudah ada di PC
        val cur = current
        list.addView(Ui.text(c, "Memuat folder dari PC...", 14f, false, Ui.MUTED).apply { gravity = Gravity.CENTER; setPadding(0, Ui.dp(c, 20), 0, 0) }, Ui.lp(c))
        crumbs.text = "File"
        ok.visibility = View.GONE
        Thread {
            try {
                val base = Net.ensureServer() ?: throw java.io.IOException("PC tidak terjangkau")
                val token = Net.ensureToken(base)
                val t = Net.folderTree(base, token)
                act.runOnUiThread {
                    tree = t
                    val hit = t.firstOrNull { it.name.equals(cur, true) }
                    pickPath = hit?.name ?: t.firstOrNull { cur.contains('/') && it.name.equals(cur.substringBeforeLast('/'), true) }?.name ?: ""
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

        d.setContentView(box)
        d.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }
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
