package com.dropbridge.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Halaman Antrean: semua file yang menunggu PC, dikelompokkan per folder tujuan. */
class QueueActivity : Activity() {

    private var builtDark = false

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var scr: Screen
    private lateinit var statusView: TextView
    private lateinit var list: LinearLayout
    private lateinit var emptyView: TextView
    private lateinit var clearBtn: View
    private var shown = "-"

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Queue.init(this)
        val c: Context = this
        scr = Ui.screen(this, "Antrean", null, back = true)
        builtDark = Ui.dark
        val col = scr.content

        val top = Ui.card(c)
        statusView = Ui.text(c, "", 14f, false, Ui.INK)
        top.addView(statusView)
        top.addView(Ui.button(c, "Kirim sekarang", Ui.Kind.PRIMARY, 48) {
            if (Queue.all().isEmpty()) Status.line = "Antrean kosong." else UploadService.start(this)
        }, Ui.lp(c, t = 12))
        col.addView(top, Ui.lp(c))

        list = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        col.addView(list, Ui.lp(c))
        emptyView = Ui.text(c, "Antrean kosong. Semua file sudah terkirim.", 14f, false, Ui.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(c, 24), 0, Ui.dp(c, 24))
        }
        col.addView(emptyView, Ui.lp(c))

        clearBtn = Ui.button(c, "Kosongkan semua antrean", Ui.Kind.DANGER, 48) {
            AlertDialog.Builder(this)
                .setMessage("Hapus semua file di antrean? File yang belum terkirim tidak akan dikirim.")
                .setPositiveButton("Hapus") { _, _ ->
                    val keep = Uploader.currentId
                    Queue.removeAll(Queue.all().map { it.id }.filter { it != keep })
                    shown = "-"
                    refresh()
                }
                .setNegativeButton("Batal", null)
                .show()
        }
        col.addView(clearBtn, Ui.lp(c, t = 16))
        refresh()
    }

    private fun movable(ids: List<String>): List<String> = ids.filter { it != Uploader.currentId }

    /** Dialog pilih folder baru untuk file di antrean. */
    private fun askFolder(title: String, current: String, ids: List<String>) {
        val ok = movable(ids)
        if (ok.isEmpty()) {
            Toast.makeText(this, "File ini sedang dikirim, tunggu sebentar.", Toast.LENGTH_SHORT).show()
            return
        }
        val c: Context = this
        val box = AutoCompleteTextView(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setAdapter(ArrayAdapter(c, android.R.layout.simple_dropdown_item_1line, ArrayList(Prefs.folders)))
            threshold = 1
            setText(current)
            setSelection(current.length)
        }
        Ui.styleInput(c, box, "Nama folder, kosongkan = Tanpa folder")
        val wrap = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(c, 20), Ui.dp(c, 8), Ui.dp(c, 20), 0)
            addView(box, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(wrap)
            .setPositiveButton("Pindahkan") { _, _ ->
                val f = box.text.toString().trim()
                Queue.setFolder(ok, f)
                shown = "-"
                refresh()
                Toast.makeText(this, "${ok.size} file akan dikirim ke ${Queue.label(f)}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun askRemove(msg: String, ids: List<String>) {
        val ok = movable(ids)
        if (ok.isEmpty()) {
            Toast.makeText(this, "File ini sedang dikirim, tunggu sebentar.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setMessage(msg)
            .setPositiveButton("Hapus") { _, _ ->
                Queue.removeAll(ok)
                shown = "-"
                refresh()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun smallBtn(c: Context, label: String, color: Int, onClick: () -> Unit): TextView =
        Ui.text(c, label, 14f, true, color).apply {
            gravity = Gravity.CENTER
            minimumHeight = Ui.dp(c, 44)
            setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun refresh() {
        val groups = Queue.groups()
        val cur = Uploader.currentId
        val total = groups.sumOf { it.second.size }
        statusView.text = when {
            total == 0 -> "Tidak ada file yang menunggu."
            UploadService.running || cur.isNotEmpty() -> "$total file di antrean · ${Status.line}"
            Prefs.autoSend -> "$total file menunggu PC. Terkirim sendiri begitu PC terjangkau."
            else -> "$total file menunggu PC. Ketuk Kirim sekarang saat PC terjangkau."
        }
        emptyView.visibility = if (total == 0) View.VISIBLE else View.GONE
        clearBtn.visibility = if (total == 0) View.GONE else View.VISIBLE

        val sig = groups.joinToString("|") { g -> g.first + ":" + g.second.joinToString(",") { it.id } } + "#" + cur
        if (sig == shown) return
        shown = sig
        list.removeAllViews()
        val c: Context = this
        for ((folder, items) in groups) {
            val card = Ui.card(c)
            val head = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            head.addView(IconView(c, Icon.FOLDER, Ui.ACCENT, 24, 2f))
            val ht = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(c, 12), 0, 0, 0) }
            ht.addView(Ui.text(c, Queue.label(folder), 17f, true))
            val sending = items.any { it.id == cur }
            ht.addView(Ui.text(c, "${items.size} file · ${Xfer.size(items.sumOf { it.size })}" + if (sending) " · sedang dikirim" else "", 13f, false, if (sending) Ui.ACCENT else Ui.MUTED))
            head.addView(ht, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            card.addView(head)

            val acts = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            acts.addView(Ui.button(c, "Ganti folder", Ui.Kind.OUTLINE, 42) {
                askFolder("Pindahkan ${items.size} file dari ${Queue.label(folder)} ke", folder, items.map { it.id })
            }, Ui.lp(c, 0, Ui.WRAP, r = 6, weight = 1f))
            acts.addView(Ui.button(c, "Hapus folder ini", Ui.Kind.OUTLINE, 42) {
                askRemove("Hapus ${items.size} file tujuan ${Queue.label(folder)} dari antrean?", items.map { it.id })
            }, Ui.lp(c, 0, Ui.WRAP, l = 6, weight = 1f))
            card.addView(acts, Ui.lp(c, t = 12))

            for (it in items) {
                card.addView(View(c).apply { setBackgroundColor(Ui.LINE) }, LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(c, 1)).apply { topMargin = Ui.dp(c, 10) })
                val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val rt = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
                rt.addView(Ui.text(c, it.name, 14f, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE })
                rt.addView(Ui.text(c, Xfer.size(it.size) + if (it.id == cur) " · sedang dikirim" else "", 12f, false, if (it.id == cur) Ui.ACCENT else Ui.MUTED))
                row.addView(rt, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
                if (it.id != cur) {
                    val item = it
                    row.addView(smallBtn(c, "Pindah", Ui.ACCENT) { askFolder("Pindahkan ${item.name} ke", item.folder, listOf(item.id)) })
                    row.addView(smallBtn(c, "Hapus", Ui.DANGER) { askRemove("Hapus ${item.name} dari antrean?", listOf(item.id)) })
                }
                card.addView(row, Ui.lp(c, t = 6))
            }
            list.addView(card, Ui.lp(c, t = 12))
        }
    }

    override fun onResume() {
        super.onResume()
        if (Ui.themeStale(this, builtDark)) { recreate(); return }
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }
}
