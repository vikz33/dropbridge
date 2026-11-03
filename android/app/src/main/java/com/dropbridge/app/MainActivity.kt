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
    private lateinit var addrEdit: EditText
    private lateinit var pinEdit: EditText

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
        val s = Ui.screen(this, "DropBridge", null, back = false)
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

        // Tombol utama
        col.addView(Ui.iconButton(c, Icon.UP, "Pilih file dan kirim ke PC", Ui.Kind.PRIMARY, 68, 17f) {
            startActivity(Intent(this, SendActivity::class.java).putExtra("pick", true))
        }, Ui.lp(c, t = 12))

        // Pintasan
        val row1 = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(Ui.tile(c, Icon.FOLDER, "File di PC") {
            startActivity(Intent(this, BrowseActivity::class.java))
        }, Ui.lp(c, 0, Ui.WRAP, r = 6, weight = 1f))
        row1.addView(Ui.tile(c, Icon.MAIL, "Kirim teks") { askText() }, Ui.lp(c, 0, Ui.WRAP, l = 6, weight = 1f))
        col.addView(row1, Ui.lp(c, t = 12))
        val row2 = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(Ui.tile(c, Icon.BROADCAST, "Mode host") {
            startActivity(Intent(this, HostActivity::class.java))
        }, Ui.lp(c, 0, Ui.WRAP, r = 6, weight = 1f))
        row2.addView(Ui.tile(c, Icon.DOWN, "Terima otomatis") {
            startActivity(Intent(this, ReceiveActivity::class.java))
        }, Ui.lp(c, 0, Ui.WRAP, l = 6, weight = 1f))
        col.addView(row2, Ui.lp(c, t = 12))

        // Antrean
        val q = Ui.card(c)
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

        // Sambungan
        val conn = Ui.card(c)
        conn.addView(Ui.caption(c, "Sambungan ke PC"))
        conn.addView(Ui.text(c, "Buka start.bat di PC, lihat PIN di jendela hitam. Alamat PC dicari otomatis; isi manual hanya jika tidak ketemu.", 13f, false, Ui.MUTED), Ui.lp(c, t = 6))
        conn.addView(Ui.text(c, "PIN", 12f, true, Ui.MUTED), Ui.lp(c, t = 12))
        pinEdit = Ui.input(c, "PIN dari layar PC", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD, Prefs.pin)
        conn.addView(pinEdit, Ui.lp(c, t = 4))
        conn.addView(Ui.text(c, "Alamat PC (opsional)", 12f, true, Ui.MUTED), Ui.lp(c, t = 10))
        addrEdit = Ui.input(c, "mis. 192.168.1.19", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, Prefs.server)
        conn.addView(addrEdit, Ui.lp(c, t = 4))
        conn.addView(Ui.button(c, "Sambungkan / cari PC", Ui.Kind.OUTLINE, 48) { connect() }, Ui.lp(c, t = 14))
        col.addView(conn, Ui.lp(c, t = 12))

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        }

        if (Prefs.pin.isEmpty()) {
            Status.line = "Isi PIN dari layar PC, lalu ketuk Sambungkan."
        } else if (Status.line.isEmpty()) {
            connect()
        }
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

    private fun normalize(a: String): String {
        var s = a.trim().trimEnd('/')
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
        if (!Regex(":\\d+$").containsMatchIn(s)) s = "$s:${Net.DEFAULT_PORT}"
        return s
    }

    private fun connect() {
        val pin = pinEdit.text.toString().trim()
        val addr = addrEdit.text.toString().trim()
        if (pin.isNotEmpty()) Prefs.pin = pin
        if (addr.isNotEmpty()) Prefs.server = normalize(addr)
        Status.line = "Mencari PC..."
        Thread {
            val base = Net.ensureServer()
            if (base == null) {
                Status.line = "PC tidak ditemukan. Pastikan start.bat berjalan dan HP satu Wi-Fi (atau hotspot) dengan PC. Atau isi alamat manual."
                return@Thread
            }
            try {
                val token = Net.ensureToken(base, true)
                Net.folders(base, token)
                Status.line = "Tersambung ke PC ($base)."
                runOnUiThread { addrEdit.setText(base) }
                if (Queue.all().isNotEmpty() && !UploadService.running) {
                    runOnUiThread { UploadService.start(this) }
                }
            } catch (e: NetError) {
                Status.line = if (e.code == 401) "PIN salah. Cek PIN di layar PC." else "Gagal masuk: ${e.message}"
            } catch (e: Exception) {
                Status.line = "Gagal terhubung: ${e.message}"
            }
        }.start()
    }

    private fun refresh() {
        val line = Status.line
        val ok = line.startsWith("Tersambung")
        Ui.setDot(heroDot, if (ok) Ui.GREEN else Ui.AMBER)
        heroState.text = if (ok) "Terhubung" else if (line.startsWith("Mencari")) "Mencari PC..." else "Menunggu sambungan"
        heroTitle.text = if (ok) "PC siap" else "Belum terhubung"
        heroSub.text = if (ok) Prefs.server.removePrefix("http://") else line
        val lf = Prefs.lastFolder
        heroFolder.visibility = if (lf.isEmpty()) View.GONE else View.VISIBLE
        heroFolder.text = "Folder terakhir: $lf"

        val n = Queue.all().size
        queueBadge.text = n.toString()
        queueTitle.text = if (n == 0) "Antrean kosong" else "File menunggu di antrean"
        queueSub.text = when {
            n == 0 -> "Semua file sudah terkirim"
            UploadService.running -> "Sedang mengirim..."
            else -> "Terkirim saat PC terjangkau"
        }
        queueActions.visibility = if (n == 0) View.GONE else View.VISIBLE
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
