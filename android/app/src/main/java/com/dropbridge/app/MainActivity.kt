package com.dropbridge.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var heroDot: View
    private lateinit var heroState: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroSub: TextView
    private lateinit var heroFolder: TextView
    private lateinit var queueBadge: TextView
    private lateinit var queueTitle: TextView
    private lateinit var queueSub: TextView
    private lateinit var queueActions: LinearLayout
    private lateinit var xferCard: LinearLayout
    private lateinit var xferTitle: TextView
    private lateinit var xferName: TextView
    private lateinit var xferBar: BarView
    private lateinit var xferSpeed: TextView
    private lateinit var xferEta: TextView
    private lateinit var xferSize: TextView
    private lateinit var queueCard: LinearLayout

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
        val s = Ui.screen(this, "DropBridge", null, back = false, nav = Ui.TAB_SEND)
        val col = s.content

        // Kartu status
        val hero = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(c, Ui.DEEP, 24)
            setPadding(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20))
        }
        val stateRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heroDot = Ui.dot(c, Ui.AMBER)
        stateRow.addView(heroDot, LinearLayout.LayoutParams(Ui.dp(c, 10), Ui.dp(c, 10)).apply { rightMargin = Ui.dp(c, 8) })
        heroState = Ui.text(c, "", 13f, true, Ui.MINT)
        stateRow.addView(heroState)
        hero.addView(stateRow)
        heroTitle = Ui.text(c, "", 26f, true, Ui.WHITE)
        hero.addView(heroTitle, Ui.lp(c, t = 14))
        heroSub = Ui.text(c, "", 14f, false, Ui.MINT)
        hero.addView(heroSub, Ui.lp(c, t = 2))
        heroFolder = Ui.pill(c, "", Color.argb(36, 255, 255, 255), Ui.WHITE)
        hero.addView(heroFolder, Ui.lp(c, w = Ui.WRAP, t = 14))
        col.addView(hero, Ui.lp(c))

        // Kartu kecepatan transfer (tampil hanya saat mengirim)
        xferCard = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(c, Ui.ACCENT, 24)
            setPadding(Ui.dp(c, 20), Ui.dp(c, 18), Ui.dp(c, 20), Ui.dp(c, 18))
            visibility = View.GONE
        }
        xferTitle = Ui.text(c, "", 13f, true, Ui.WHITE)
        xferCard.addView(xferTitle)
        xferName = Ui.text(c, "", 15f, true, Ui.WHITE).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE }
        xferCard.addView(xferName, Ui.lp(c, t = 4))
        val speedRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        xferSpeed = Ui.text(c, "", 32f, true, Ui.WHITE)
        speedRow.addView(xferSpeed, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        xferEta = Ui.text(c, "", 14f, true, Ui.WHITE)
        speedRow.addView(xferEta)
        xferCard.addView(speedRow, Ui.lp(c, t = 10))
        xferBar = BarView(c).apply { fillColor = Ui.WHITE; trackColor = Color.argb(70, 255, 255, 255) }
        xferCard.addView(xferBar, Ui.lp(c, t = 10))
        xferSize = Ui.text(c, "", 13f, false, Color.parseColor("#E6F7F4"))
        xferCard.addView(xferSize, Ui.lp(c, t = 8))
        col.addView(xferCard, Ui.lp(c, t = 12))

        // Tombol utama
        col.addView(Ui.iconButton(c, Icon.UP, "Pilih file dan kirim ke PC", Ui.Kind.PRIMARY, 68, 17f) {
            startActivity(Intent(this, SendActivity::class.java).putExtra("pick", true))
        }, Ui.lp(c, t = 12))

        col.addView(Ui.iconButton(c, Icon.MAIL, "Kirim teks atau link", Ui.Kind.OUTLINE, 52, 15f) { askText() }, Ui.lp(c, t = 12))

        // Antrean
        val q = Ui.card(c)
        queueCard = q
        val qTop = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        queueBadge = Ui.text(c, "0", 18f, true, Ui.DEEP).apply {
            gravity = Gravity.CENTER
            background = Ui.rounded(c, Ui.SOFT, 14)
        }
        qTop.addView(queueBadge, LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)).apply { rightMargin = Ui.dp(c, 14) })
        val qText = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        queueTitle = Ui.text(c, "", 15f, true)
        queueSub = Ui.text(c, "", 13f, false, Ui.MUTED)
        qText.addView(queueTitle)
        qText.addView(queueSub)
        qTop.addView(qText, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        q.addView(qTop)
        queueActions = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        queueActions.addView(Ui.button(c, "Kirim antrean", Ui.Kind.DEEP, 44) {
            if (Queue.all().isEmpty()) Status.line = "Antrean kosong." else UploadService.start(this)
        }, Ui.lp(c, 0, Ui.WRAP, r = 6, weight = 1f))
        queueActions.addView(Ui.button(c, "Kosongkan", Ui.Kind.OUTLINE, 44) {
            AlertDialog.Builder(this)
                .setMessage("Hapus semua file di antrean? File yang belum terkirim tidak akan dikirim.")
                .setPositiveButton("Hapus") { _, _ -> Queue.clear() }
                .setNegativeButton("Batal", null)
                .show()
        }, Ui.lp(c, 0, Ui.WRAP, l = 6, weight = 1f))
        q.addView(queueActions, Ui.lp(c, t = 14))
        col.addView(q, Ui.lp(c, t = 12))

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        }

        if (Prefs.pin.isEmpty()) {
            Status.line = "Belum ada PIN. Buka Lainnya, isi PIN dari layar PC."
        } else if (Status.line.isEmpty()) {
            Conn.connect(this)
        }
        AutoSend.schedule(this)
        refresh()
    }

    private fun askText() {
        val box = EditText(this).apply {
            hint = "Tulis atau tempel teks/link"
            minLines = 3
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        AlertDialog.Builder(this)
            .setTitle("Kirim teks ke PC")
            .setView(box)
            .setPositiveButton("Kirim") { _, _ ->
                val t = box.text.toString().trim()
                if (t.isNotEmpty()) sendText(t)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun sendText(t: String) {
        Thread {
            val msg = try {
                val base = Net.ensureServer()
                if (base == null) "PC tidak ditemukan." else {
                    Net.sendText(base, Net.ensureToken(base), t)
                    "Teks terkirim ke PC."
                }
            } catch (e: Exception) {
                "Gagal: ${e.message}"
            }
            runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        }.start()
    }

    private fun refresh() {
        val line = Status.line
        val ok = line.startsWith("Tersambung")
        Ui.setDot(heroDot, if (ok) Ui.GREEN else Ui.AMBER)
        heroState.text = if (ok) "Terhubung" else if (line.startsWith("Mencari")) "Mencari PC..." else "Menunggu sambungan"
        heroTitle.text = if (ok) "PC siap" else "Belum terhubung"
        heroSub.text = if (ok) Prefs.server.removePrefix("http://") else line
        heroSub.setOnClickListener { if (!ok && Prefs.pin.isNotEmpty()) Conn.connect(this) }
        val lf = Prefs.lastFolder
        heroFolder.visibility = if (lf.isEmpty()) View.GONE else View.VISIBLE
        heroFolder.text = "Folder terakhir: $lf"

        val n = Queue.all().size
        queueBadge.text = n.toString()
        queueTitle.text = if (n == 0) "Antrean kosong" else "File menunggu di antrean"
        queueSub.text = when {
            n == 0 -> if (Xfer.summary.isNotEmpty()) Xfer.summary else "Semua file sudah terkirim"
            UploadService.running -> "Sedang mengirim..."
            Prefs.autoSend -> "Terkirim otomatis saat PC terjangkau"
            else -> "Ketuk Kirim antrean saat PC terjangkau"
        }

        if (Xfer.active && Xfer.count > 0) {
            xferCard.visibility = View.VISIBLE
            xferTitle.text = "Mengirim ${Xfer.index} dari ${Xfer.count}"
            xferName.text = Xfer.name
            xferSpeed.text = if (Xfer.speed > 0) Xfer.rate(Xfer.speed) else "Menghitung..."
            xferEta.text = Xfer.eta()
            xferBar.fraction = Xfer.percent() / 100f
            xferSize.text = "${Xfer.size(Xfer.sentTotal)} dari ${Xfer.size(Xfer.totalBytes)} (${Xfer.percent()}%)"
        } else {
            xferCard.visibility = View.GONE
        }
        queueCard.visibility = if (n == 0) View.GONE else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }
}
