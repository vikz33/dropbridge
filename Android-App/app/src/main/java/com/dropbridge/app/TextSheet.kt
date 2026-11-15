package com.dropbridge.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Lembar "Teks" dari ikon Teks di Home: tulis atau tempel teks lalu kirim ke laptop.
 * HP berakses Penuh juga melihat semua teks di laptop (terbaru di atas) dan bisa Salin, Pilih sebagian, Hapus (+ Urungkan).
 * HP "Hanya kirim" tetap bisa mengirim; daftarnya disembunyikan.
 */
object TextSheet {

    fun open(act: Activity) {
        val c: Context = act
        val (d, box) = Ui.sheet(act, "Teks")
        val full = Prefs.role != "send"

        val input = EditText(c).apply {
            hint = "Tulis atau tempel teks…"
            setHintTextColor(Ui.HINT)
            setTextColor(Ui.INK)
            textSize = 16f
            typeface = Ui.FONT
            gravity = Gravity.TOP
            minLines = 3
            maxLines = 6
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            background = Ui.rounded(c, Ui.CARD, 14).also { it.setStroke(Ui.dp(c, 2), Ui.ACCENT) }
            setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12))
        }
        box.addView(input, Ui.lp(c, t = 4))

        val btns = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        btns.addView(Ui.iconButton(c, Icon.PASTE, "Tempel", Ui.Kind.OUTLINE, 48) {
            val cm = c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(c)?.toString().orEmpty()
            if (t.isEmpty()) Toast.makeText(c, "Papan klip kosong", Toast.LENGTH_SHORT).show()
            else { input.setText(t); input.setSelection(t.length) }
        }, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { rightMargin = Ui.dp(c, 5) })

        val list = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        val count = Ui.caption(c, "Semua teks")
        lateinit var load: () -> Unit

        btns.addView(Ui.iconButton(c, Icon.UP, "Kirim ke laptop", Ui.Kind.PRIMARY, 48) {
            val t = input.text.toString().trim()
            if (t.isEmpty()) { input.requestFocus(); return@iconButton }
            Thread {
                val msg = try {
                    val base = Net.ensureServer() ?: throw java.io.IOException("Laptop tidak terjangkau")
                    Net.sendText(base, Net.ensureToken(base), t)
                    null
                } catch (e: Exception) { "Gagal: ${e.message}" }
                act.runOnUiThread {
                    if (msg == null) {
                        input.setText("")
                        Toast.makeText(c, "Teks terkirim ke laptop", Toast.LENGTH_SHORT).show()
                        if (full) load()
                    } else Toast.makeText(c, msg, Toast.LENGTH_LONG).show()
                }
            }.start()
        }, LinearLayout.LayoutParams(0, Ui.WRAP, 1.4f).apply { leftMargin = Ui.dp(c, 5) })
        box.addView(btns, Ui.lp(c, t = 10))

        if (!full) {
            val note = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = Ui.rounded(c, Ui.FIELD, 14)
                setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12))
            }
            note.addView(IconView(c, Icon.LOCK, Ui.MUTED, 20, 2f))
            note.addView(Ui.text(c, "HP ini berhak Hanya kirim. Daftar teks hanya terlihat di HP berakses Penuh.", 14f, false, Ui.MUTED).apply {
                setPadding(Ui.dp(c, 10), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            box.addView(note, Ui.lp(c, t = 14))
            d.show()
            return
        }

        val head = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(count, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        head.addView(Ui.caption(c, "terbaru di atas"))
        box.addView(head, Ui.lp(c, t = 14, b = 4))
        val h = (c.resources.displayMetrics.heightPixels * 0.45f).toInt()
        box.addView(ScrollView(c).apply { addView(list) }, LinearLayout.LayoutParams(Ui.MATCH, h))

        fun copy(cl: Net.Clip) {
            val cm = c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("DropBridge", cl.text))
            Toast.makeText(c, "Disalin. Tempel di aplikasi lain.", Toast.LENGTH_SHORT).show()
        }

        fun remove(cl: Net.Clip) {
            Thread {
                val err = try {
                    val base = Net.ensureServer() ?: throw java.io.IOException("Laptop tidak terjangkau")
                    Net.deleteClip(base, Net.ensureToken(base), cl.id); null
                } catch (e: Exception) { e.message }
                act.runOnUiThread {
                    if (err != null) { Toast.makeText(c, "Gagal: $err", Toast.LENGTH_LONG).show(); return@runOnUiThread }
                    load()
                    Undo.show(act, box, "Teks dihapus") {
                        Thread {
                            try {
                                val base = Net.ensureServer() ?: throw java.io.IOException()
                                Net.restoreClip(base, Net.ensureToken(base), cl.id)
                                act.runOnUiThread { load() }
                            } catch (e: Exception) {
                                act.runOnUiThread { Toast.makeText(c, "Sudah tidak bisa dikembalikan", Toast.LENGTH_SHORT).show() }
                            }
                        }.start()
                    }
                }
            }.start()
        }

        fun row(cl: Net.Clip): View {
            val r = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(Ui.dp(c, 2), Ui.dp(c, 12), 0, Ui.dp(c, 12))
            }
            val left = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            val body = Ui.text(c, cl.text, 15f, false, Ui.INK).apply {
                maxLines = 3
                ellipsize = android.text.TextUtils.TruncateAt.END
                setLineSpacing(0f, 1.1f)
            }
            left.addView(body)
            val from = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val col = if (cl.fromPc) Ui.ACCENT else Ui.INDIGO
            from.addView(IconView(c, if (cl.fromPc) Icon.LAPTOP else Icon.PHONE, col, 14, 2.2f))
            from.addView(Ui.text(c, cl.from, 12f, true, col).apply { setPadding(Ui.dp(c, 6), 0, Ui.dp(c, 6), 0); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            from.addView(Ui.text(c, "· " + ago(cl.ts), 12f, false, Ui.MUTED))
            left.addView(from, Ui.lp(c, t = 6))
            r.addView(left, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))

            val acts = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
            acts.addView(Ui.iconButton(c, Icon.COPY, "Salin", Ui.Kind.SOFT, 40, 14f) { copy(cl) }.apply { setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 12), 0) })
            val dots = android.widget.FrameLayout(c).apply {
                isClickable = true
                isFocusable = true
                contentDescription = "Pilihan teks"
                background = Ui.ripple(c, Ui.CARD, 12)
                addView(IconView(c, Icon.DOTS, Ui.MUTED, 18, 2f), android.widget.FrameLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18), Gravity.CENTER))
            }
            dots.setOnClickListener {
                val m = PopupMenu(c, dots)
                m.menu.add(0, 1, 0, "Salin")
                m.menu.add(0, 2, 1, "Pilih sebagian")
                m.menu.add(0, 3, 2, "Hapus")
                m.setOnMenuItemClickListener { mi ->
                    when (mi.itemId) {
                        1 -> copy(cl)
                        2 -> { body.maxLines = Int.MAX_VALUE; body.ellipsize = null; body.setTextIsSelectable(true); body.requestFocus() }
                        3 -> remove(cl)
                    }
                    true
                }
                m.show()
            }
            acts.addView(dots, LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 40)).apply { topMargin = Ui.dp(c, 2) })
            r.addView(acts, LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP).apply { leftMargin = Ui.dp(c, 10) })
            return r
        }

        load = {
            Thread {
                val got = try {
                    val base = Net.ensureServer() ?: throw java.io.IOException("Laptop tidak terjangkau")
                    Net.clips(base, Net.ensureToken(base))
                } catch (e: Exception) { null }
                act.runOnUiThread {
                    list.removeAllViews()
                    if (got == null) {
                        list.addView(Ui.text(c, "Daftar teks tidak bisa dimuat. Cek sambungan ke laptop.", 14f, false, Ui.MUTED).apply { gravity = Gravity.CENTER; setPadding(0, Ui.dp(c, 20), 0, 0) }, Ui.lp(c))
                        return@runOnUiThread
                    }
                    count.text = "SEMUA TEKS · ${got.size}"
                    if (got.isEmpty()) {
                        list.addView(Ui.text(c, "Belum ada teks. Tulis di atas, lalu salin di perangkat lain.", 14f, false, Ui.MUTED).apply { gravity = Gravity.CENTER; setPadding(0, Ui.dp(c, 20), 0, 0) }, Ui.lp(c))
                    }
                    got.forEachIndexed { i, cl ->
                        list.addView(View(c).apply { setBackgroundColor(Ui.LINE) }, LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(c, 1)))
                        list.addView(row(cl), Ui.lp(c))
                    }
                }
            }.start()
        }
        list.addView(Ui.text(c, "Memuat teks dari laptop...", 14f, false, Ui.MUTED).apply { gravity = Gravity.CENTER; setPadding(0, Ui.dp(c, 20), 0, 0) }, Ui.lp(c))
        d.show()
        load()
    }

    private fun ago(ts: Double): String {
        val d = System.currentTimeMillis() / 1000.0 - ts
        return when {
            d < 45 -> "baru saja"
            d < 3600 -> "${(d / 60).toInt()} mnt lalu"
            d < 86400 -> java.text.SimpleDateFormat("HH.mm", java.util.Locale("id")).format(java.util.Date((ts * 1000).toLong()))
            d < 172800 -> "Kemarin"
            else -> java.text.SimpleDateFormat("d MMM", java.util.Locale("id")).format(java.util.Date((ts * 1000).toLong()))
        }
    }
}

/** Kabar kecil di bawah layar dengan tombol "Urungkan" (4 detik). */
object Undo {
    fun show(act: Activity, host: LinearLayout, msg: String, undo: () -> Unit) {
        val c: Context = act
        val bar = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(c, Ui.INK, 14)
            setPadding(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 4))
            minimumHeight = Ui.dp(c, 48)
        }
        bar.addView(Ui.text(c, msg, 14f, true, Ui.GROUND), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        var done = false
        bar.addView(Ui.text(c, "Urungkan", 14f, true, if (Ui.dark) Ui.ACCENT else Ui.SOFT).apply {
            minimumHeight = Ui.dp(c, 44)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { if (!done) { done = true; host.removeView(bar); undo() } }
        })
        host.addView(bar, Ui.lp(c, t = 10))
        host.postDelayed({ if (!done) { done = true; host.removeView(bar) } }, 4000)
    }
}
