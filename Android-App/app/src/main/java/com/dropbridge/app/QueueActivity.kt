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
    private lateinit var pauseBtn: android.widget.Button
    private lateinit var routeBtn: android.widget.Button
    private var shown = "-"
    private lateinit var countView: TextView
    private lateinit var toggleAll: TextView
    private lateinit var heads: LinearLayout
    /** Folder yang dibuka. Sebelum pengguna menyentuh apa pun: satu folder = terbuka, dua atau lebih = tertutup. */
    private val openSet = HashSet<String>()
    private var touched = false
    private val bars = HashMap<String, android.widget.ProgressBar>()

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
            if (Queue.all().isEmpty()) Status.line = "Antrean kosong."
            else if (Prefs.paused) Uploader.resume(this)
            else UploadService.start(this)
            refresh()
        }, Ui.lp(c, t = 12))
        val ctl = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        pauseBtn = Ui.button(c, "Jeda", Ui.Kind.OUTLINE, 44) {
            if (Prefs.paused) Uploader.resume(this) else Uploader.pause(this)
            refresh()
        }
        routeBtn = Ui.button(c, "Cari jalur terbaik", Ui.Kind.OUTLINE, 44) {
            Uploader.bestRoute(this)
            Toast.makeText(this, "Mencari jalur terbaik. Antrean tidak berubah.", Toast.LENGTH_SHORT).show()
            refresh()
        }
        ctl.addView(pauseBtn, Ui.lp(c, 0, Ui.WRAP, r = 6, weight = 1f))
        ctl.addView(routeBtn, Ui.lp(c, 0, Ui.WRAP, l = 6, weight = 1f))
        top.addView(ctl, Ui.lp(c, t = 8))
        col.addView(top, Ui.lp(c))

        heads = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        countView = Ui.caption(c, "")
        heads.addView(countView, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        toggleAll = Ui.text(c, "Buka semua", 14f, true, Ui.ACCENT).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            minimumHeight = Ui.dp(c, 44)
            isClickable = true
            isFocusable = true
            setOnClickListener { toggleEverything() }
        }
        heads.addView(toggleAll)
        col.addView(heads, Ui.lp(c, t = 12))

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

    /** Folder yang sedang terbuka sekarang. */
    private fun effectiveOpen(folders: List<String>): Set<String> =
        if (touched) openSet.filter { it in folders }.toSet() else if (folders.size == 1) folders.toSet() else emptySet()

    private fun toggleFolder(folder: String, folders: List<String>) {
        val now = effectiveOpen(folders).toMutableSet()
        if (!now.add(folder)) now.remove(folder)
        openSet.clear(); openSet.addAll(now); touched = true
        shown = "-"
        refresh()
    }

    private fun toggleEverything() {
        val folders = Queue.groups().map { it.first }
        val now = effectiveOpen(folders)
        openSet.clear()
        if (now.size < folders.size) openSet.addAll(folders)
        touched = true
        shown = "-"
        refresh()
    }

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
        pauseBtn.text = if (Prefs.paused) "Lanjut" else "Jeda"
        val ctlOn = total > 0
        pauseBtn.visibility = if (ctlOn) View.VISIBLE else View.GONE
        routeBtn.visibility = if (ctlOn) View.VISIBLE else View.GONE
        val route = when {
            Net.current.isEmpty() -> ""
            Net.viaRemote -> "\nJalur: Tailscale (lewat internet, bisa lebih lambat)"
            else -> "\nJalur: jaringan lokal"
        }
        statusView.text = when {
            total == 0 -> "Tidak ada file yang menunggu."
            Prefs.paused -> "Dijeda. $total file menunggu. Ketuk Lanjut untuk mengirim.$route"
            UploadService.running || cur.isNotEmpty() -> "$total file di antrean · ${Status.line}$route"
            Prefs.autoSend -> "$total file menunggu PC. Terkirim sendiri begitu PC terjangkau."
            else -> "$total file menunggu PC. Ketuk Kirim sekarang saat PC terjangkau."
        }
        emptyView.visibility = if (total == 0) View.VISIBLE else View.GONE
        clearBtn.visibility = if (total == 0) View.GONE else View.VISIBLE

        val folders = groups.map { it.first }
        val opened = effectiveOpen(folders)
        countView.text = "${groups.size} FOLDER · $total FILE"
        heads.visibility = if (total == 0) View.GONE else View.VISIBLE
        toggleAll.visibility = if (groups.size < 2) View.GONE else View.VISIBLE
        toggleAll.text = if (opened.size >= groups.size) "Ciutkan semua" else "Buka semua"
        for ((_, bar) in bars) bar.progress = Xfer.percent()

        val sig = groups.joinToString("|") { g -> g.first + ":" + g.second.joinToString(",") { it.id } } + "#" + cur + "#" + opened.sorted().joinToString(",")
        if (sig == shown) return
        shown = sig
        list.removeAllViews()
        bars.clear()
        val c: Context = this
        for ((folder, items) in groups) {
            val isOpen = folder in opened
            val sending = items.any { it.id == cur }
            val card = Ui.card(c)
            val head = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = Ui.dp(c, 52)
                isClickable = true
                isFocusable = true
                contentDescription = "${Queue.label(folder)}, ${items.size} file, " + if (isOpen) "terbuka. Ketuk untuk menutup." else "tertutup. Ketuk untuk membuka."
                setOnClickListener { toggleFolder(folder, folders) }
            }
            head.addView(IconView(c, Icon.FOLDER, Ui.ACCENT, 24, 2f))
            val ht = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 8), 0) }
            ht.addView(Ui.text(c, Queue.label(folder), 17f, true))
            ht.addView(Ui.text(c, "${items.size} file · ${Xfer.size(items.sumOf { it.size })}", 13f, false, Ui.MUTED))
            if (sending && !isOpen) {
                val bar = android.widget.ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    progress = Xfer.percent()
                    progressTintList = android.content.res.ColorStateList.valueOf(Ui.ACCENT)
                }
                bars[folder] = bar
                ht.addView(bar, Ui.lp(c, h = Ui.dp(c, 8), t = 6))
            }
            head.addView(ht, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            head.addView(Ui.text(c, if (sending) "Mengirim" else "Menunggu", 12f, true, if (sending) Ui.DEEP else Ui.MUTED).apply {
                background = Ui.rounded(c, if (sending) Ui.SOFT else Ui.FIELD, 999)
                setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4))
            }, Ui.lp(c, Ui.WRAP, Ui.WRAP, r = 6))
            head.addView(IconView(c, Icon.CHEVRON, Ui.MUTED, 20, 2.2f).apply { rotation = if (isOpen) 90f else 0f })
            card.addView(head)

            if (isOpen) {
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
                    rt.addView(Ui.text(c, Queue.shown(it), 14f, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE })
                    rt.addView(Ui.text(c, Xfer.size(it.size) + if (it.id == cur) " · sedang dikirim" else "", 12f, false, if (it.id == cur) Ui.ACCENT else Ui.MUTED))
                    row.addView(rt, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
                    if (it.id != cur) {
                        val item = it
                        row.addView(smallBtn(c, "Pindah", Ui.ACCENT) { askFolder("Pindahkan ${Queue.shown(item)} ke", item.folder, listOf(item.id)) })
                        row.addView(smallBtn(c, "Hapus", Ui.DANGER) { askRemove("Hapus ${Queue.shown(item)} dari antrean?", listOf(item.id)) })
                    }
                    card.addView(row, Ui.lp(c, t = 6))
                }
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
