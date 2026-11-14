package com.dropbridge.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Lembar "Kirim ke PC mana?": PC tersimpan (hijau = ada di Wi-Fi ini), PC baru yang ditemukan,
 * Cari ulang, dan isi alamat manual. Pilihan diingat; HP tidak pindah PC sendiri.
 */
object PcPicker {
    fun open(act: Activity, onChanged: () -> Unit) {
        val c: Context = act
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
        head.addView(Ui.text(c, "Kirim ke PC mana?", 18f, true), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        head.addView(Ui.text(c, "Tutup", 14f, true, Ui.ACCENT).apply {
            minimumHeight = Ui.dp(c, 44); gravity = Gravity.CENTER_VERTICAL; setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 4), 0)
            isClickable = true; isFocusable = true; setOnClickListener { d.dismiss() }
        })
        box.addView(head)
        val list = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        box.addView(android.widget.ScrollView(c).apply { addView(list) }, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        val note = Ui.text(c, "", 12f, false, Ui.MUTED).apply { setPadding(Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 4), 0) }
        box.addView(note)
        val btns = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        val again = Ui.button(c, "Cari ulang", Ui.Kind.OUTLINE, 50) {}
        val manual = Ui.button(c, "Isi manual", Ui.Kind.OUTLINE, 50) {
            d.dismiss(); act.startActivity(Intent(act, MoreActivity::class.java))
        }
        btns.addView(again, Ui.lp(c, 0, Ui.WRAP, r = 5, weight = 1f))
        btns.addView(manual, Ui.lp(c, 0, Ui.WRAP, l = 5, weight = 1f))
        box.addView(btns, Ui.lp(c, t = 10))
        box.addView(Ui.text(c, "Pilihan diingat. Kalau PC terpilih mati, file menunggu di antrean (tidak dikirim ke PC lain).", 12f, false, Ui.MUTED).apply {
            setPadding(Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4), 0)
        })

        var found: List<Net.Found> = emptyList()
        var searching = false

        fun row(name: String, os: String, line: String, on: Boolean, selected: Boolean, click: () -> Unit, long: (() -> Unit)? = null): View {
            val r = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = Ui.dp(c, 64)
                isClickable = true
                isFocusable = true
                setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 8), 0)
                background = if (selected) Ui.ripple(c, Ui.TINT, 14) else Ui.ripple(c, Ui.CARD, 14)
                contentDescription = "$name, $os. $line" + if (selected) ". Dipilih" else ""
                setOnClickListener { click() }
                if (long != null) setOnLongClickListener { long(); true }
            }
            val ic = FrameLayout(c).apply { background = Ui.rounded(c, if (on) Ui.SOFT2 else Ui.FIELD, 12) }
            ic.addView(IconView(c, Icon.PC, if (on) Ui.ACCENT else Ui.HINT, 22, 2f), FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.CENTER))
            ic.addView(View(c).apply { background = Ui.rounded(c, if (on) Ui.OK else Ui.LINE2, 99, Ui.CARD) },
                FrameLayout.LayoutParams(Ui.dp(c, 12), Ui.dp(c, 12), Gravity.END or Gravity.BOTTOM))
            r.addView(ic, LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)).apply { rightMargin = Ui.dp(c, 12) })
            val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            val t = android.text.SpannableStringBuilder(name)
            if (os.isNotEmpty()) {
                val st = t.length
                t.append("  · $os")
                t.setSpan(android.text.style.ForegroundColorSpan(Ui.MUTED), st, t.length, 0)
                t.setSpan(android.text.style.RelativeSizeSpan(0.8f), st, t.length, 0)
            }
            col.addView(Ui.text(c, "", 16f, true, if (on) Ui.INK else Ui.MUTED).apply { text = t; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            col.addView(Ui.text(c, line, 12f, false, Ui.MUTED))
            r.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            if (selected) r.addView(IconView(c, Icon.CHECK, Ui.ACCENT, 22, 2.6f))
            return r
        }

        fun render() {
            list.removeAllViews()
            val saved = Pcs.all().let { all -> if (Pcs.active().server.isNotEmpty() && all.none { Pcs.isActive(it) }) listOf(Pcs.active()) + all else all }
            val here = found.associateBy { it.id }
            for (e in saved) {
                val f = found.firstOrNull { (e.id.isNotEmpty() && it.id == e.id) || it.base == e.server }
                val sel = Pcs.isActive(e)
                val line = when {
                    f != null -> "Wi-Fi ini" + (if (sel) " · dipilih" else "")
                    searching -> "Mencari…"
                    e.remote.isNotEmpty() -> "Tidak di Wi-Fi ini · bisa lewat Tailscale"
                    else -> "Mati atau di jaringan lain"
                }
                list.addView(row(e.label, e.os, line, f != null, sel, click = {
                    if (!sel) {
                        Pcs.switchTo(if (f != null) e.copy(server = f.base) else e)
                        onChanged()
                    }
                    d.dismiss()
                }, long = {
                    AlertDialog.Builder(act).setTitle("Lupakan ${e.label}?")
                        .setMessage("PIN dan alamatnya dihapus dari HP ini. Untuk memakai lagi, masukkan PIN-nya.")
                        .setPositiveButton("Lupakan") { _, _ -> if (!sel) { Pcs.forget(e); render() } }
                        .setNegativeButton("Batal", null).show()
                }), Ui.lp(c, b = 4))
            }
            // PC di Wi-Fi ini yang belum pernah disambungkan
            val known = saved.map { it.id }.filter { it.isNotEmpty() }.toSet()
            for (f in found.filter { it.id.isEmpty() || it.id !in known }) {
                if (saved.any { it.server == f.base && it.id.isEmpty() }) continue
                list.addView(row(f.host.ifEmpty { f.base.removePrefix("http://") }, f.os, "PC baru di Wi-Fi ini · ketuk lalu masukkan PIN", true, false, click = {
                    askPin(act, f) { d.dismiss(); onChanged() }
                }), Ui.lp(c, b = 4))
            }
            if (list.childCount == 0) {
                list.addView(Ui.text(c, if (searching) "Mencari PC di Wi-Fi ini…" else "Belum ada PC. Pastikan DropBridge jalan di laptop dan HP satu Wi-Fi.", 14f, false, Ui.MUTED).apply {
                    gravity = Gravity.CENTER; setPadding(0, Ui.dp(c, 18), 0, Ui.dp(c, 18))
                }, Ui.lp(c))
            }
            note.text = if (searching) "Mencari PC di Wi-Fi ini…" else "Tahan lama sebuah PC untuk melupakannya."
            again.isEnabled = !searching
            again.alpha = if (searching) 0.5f else 1f
        }

        fun search() {
            searching = true
            render()
            Thread {
                val r = try { Net.discoverAll(3) } catch (e: Exception) { emptyList() }
                act.runOnUiThread { found = r; searching = false; if (d.isShowing) render() }
            }.start()
        }
        again.setOnClickListener { search() }

        d.setContentView(box)
        d.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }
        d.show()
        search()
    }

    /** PC baru: masukkan PIN yang tampil di layar laptop itu. */
    private fun askPin(act: Activity, f: Net.Found, done: () -> Unit) {
        val c: Context = act
        val box = EditText(c).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD; setSingleLine() }
        Ui.styleInput(c, box, "PIN, mis. 638334")
        val name = f.host.ifEmpty { f.base.removePrefix("http://") }
        val wrap = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(c, 20), Ui.dp(c, 4), Ui.dp(c, 20), 0)
            addView(Ui.text(c, "PIN tampil di layar DropBridge $name (menu Hubungkan).", 13f, false, Ui.MUTED), Ui.lp(c, b = 10))
            addView(box, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        }
        val dlg = AlertDialog.Builder(act).setTitle("Hubungkan $name").setView(wrap)
            .setPositiveButton("Hubungkan", null).setNegativeButton("Batal", null).create()
        dlg.setOnShowListener {
            box.requestFocus()
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin = box.text.toString().trim()
                if (pin.length < 4) { box.error = "Isi PIN dari layar laptop"; return@setOnClickListener }
                Pcs.startNew(f, pin)
                dlg.dismiss()
                done()
            }
        }
        dlg.show()
    }
}
