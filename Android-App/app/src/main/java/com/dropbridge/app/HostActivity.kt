package com.dropbridge.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Menggambar kode QR. */
class QrView(ctx: Context) : View(ctx) {
    var matrix: Array<BooleanArray>? = null
        set(v) { field = v; invalidate() }
    private val paint = Paint()

    override fun onMeasure(w: Int, h: Int) {
        val s = (210 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(s, s)
    }

    override fun onDraw(c: Canvas) {
        val m = matrix ?: return
        paint.color = Color.WHITE
        c.drawRoundRect(android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat()), 18f * resources.displayMetrics.density, 18f * resources.displayMetrics.density, paint)
        val quiet = 3
        val cell = width.toFloat() / (m.size + quiet * 2)
        paint.color = Color.BLACK
        for (y in m.indices) for (x in m.indices) {
            if (m[y][x]) c.drawRect((x + quiet) * cell, (y + quiet) * cell, (x + quiet + 1) * cell, (y + quiet + 1) * cell, paint)
        }
    }
}

/**
 * Tab Hubungkan: HP ini jadi host. Hotspot khusus menyala otomatis, lalu teman scan
 * QR Wi-Fi (langkah 1) dan QR halaman (langkah 2). Bila hotspot khusus gagal,
 * teman memakai hotspot biasa atau Wi-Fi yang sama, dan cukup satu QR.
 */
class HostActivity : Activity() {

    private var builtDark = false

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var scr: Screen
    private lateinit var toggle: Switch
    private lateinit var hero: LinearLayout
    private lateinit var stepRow: LinearLayout
    private lateinit var step1: TextView
    private lateinit var step2: TextView
    private lateinit var qr: QrView
    private lateinit var qrTitle: TextView
    private lateinit var qrHint: TextView
    private lateinit var codeView: TextView
    private lateinit var addrView: TextView
    private lateinit var warn: LinearLayout
    private lateinit var warnText: TextView
    private lateinit var retryBtn: View
    private lateinit var offCard: LinearLayout
    private lateinit var sharedTitle: TextView
    private lateinit var sharedCount: TextView
    private lateinit var sharedList: LinearLayout
    private lateinit var recvCount: TextView
    private lateinit var recvList: LinearLayout
    private lateinit var addBtn: View
    private lateinit var pcRow: LinearLayout
    private lateinit var pcView: TextView
    private var listSig = ""
    private val pendingUris = ArrayList<Uri>()
    private val pendingFiles = ArrayList<String>()
    private var step = 1
    private var chosen = 0
    private var shownKey = ""
    private var updating = false

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    private fun stepTab(c: Context, label: String, n: Int): TextView =
        Ui.text(c, label, 14f, true, Ui.INDIGO_SOFT).apply {
            gravity = Gravity.CENTER
            minimumHeight = Ui.dp(c, 40)
            isClickable = true
            setOnClickListener { step = n; shownKey = ""; refresh() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        val c: Context = this
        scr = Ui.screen(this, "Hubungkan", null, back = false, nav = Ui.TAB_HOST)
        builtDark = Ui.dark
        val col = scr.content

        // Saklar utama
        val sw = Ui.card(c)
        val swRow = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val swText = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        swText.addView(Ui.text(c, "Berbagi dengan HP lain", 16f, true))
        swText.addView(Ui.text(c, "HP teman dan PC bisa masuk lewat browser", 13f, false, Ui.MUTED))
        swRow.addView(swText, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        toggle = Switch(c).apply {
            contentDescription = "Berbagi dengan HP lain"
            setOnCheckedChangeListener { _, on ->
                if (updating) return@setOnCheckedChangeListener
                if (on) turnOn() else turnOff()
            }
        }
        swRow.addView(toggle)
        sw.addView(swRow)
        col.addView(sw, Ui.lp(c))

        // Peringatan bila hotspot belum menyala
        warn = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(c, Ui.AMBER_BG, 16)
            setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12))
            visibility = View.GONE
        }
        warnText = Ui.text(c, "", 13f, false, Ui.AMBER_TX)
        warn.addView(warnText)
        val wb = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        retryBtn = Ui.button(c, "Coba hotspot otomatis", Ui.Kind.OUTLINE, 40) { requestHotspot() }
        wb.addView(retryBtn, Ui.lp(c, Ui.WRAP, Ui.WRAP, r = 8))
        wb.addView(Ui.button(c, "Pengaturan hotspot", Ui.Kind.OUTLINE, 40) {
            try { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
            catch (e: Exception) { Toast.makeText(this, "Buka pengaturan hotspot dari menu Pengaturan HP", Toast.LENGTH_LONG).show() }
        }, Ui.lp(c, Ui.WRAP, Ui.WRAP))
        warn.addView(wb, Ui.lp(c, t = 10))
        col.addView(warn, Ui.lp(c, t = 12))

        // Saat host mati: penjelasan singkat, bukan kartu QR kosong
        offCard = Ui.card(c)
        offCard.addView(Ui.text(c, "Berbagi tanpa aplikasi", 15f, true))
        for ((n, t) in listOf("1" to "Nyalakan saklar di atas. HP ini membuat hotspot sendiri, tidak butuh internet atau kuota.",
                              "2" to "Teman scan QR dengan kamera, iPhone juga bisa.",
                              "3" to "Teman kirim atau ambil file apa saja: foto, video, PDF, dokumen.")) {
            val r = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            r.addView(Ui.text(c, n, 13f, true, Ui.INDIGO_INK).apply { gravity = Gravity.CENTER; background = Ui.rounded(c, Ui.INDIGO_SOFT, 999) },
                LinearLayout.LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28)).apply { rightMargin = Ui.dp(c, 12) })
            r.addView(Ui.text(c, t, 14f, false, Ui.INK), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            offCard.addView(r, Ui.lp(c, t = 10))
        }
        col.addView(offCard, Ui.lp(c, t = 12))

        // Kartu QR
        hero = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = Ui.rounded(c, Ui.INDIGO_DEEP, 24)
            setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 18))
        }
        stepRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Ui.rounded(c, Color.argb(26, 255, 255, 255), 14)
            setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4))
        }
        step1 = stepTab(c, "1. Sambung Wi-Fi", 1)
        step2 = stepTab(c, "2. Buka halaman", 2)
        stepRow.addView(step1, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        stepRow.addView(step2, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        hero.addView(stepRow, Ui.lp(c, b = 12))
        qr = QrView(c)
        hero.addView(qr, Ui.lp(c, Ui.WRAP, Ui.WRAP))
        qrTitle = Ui.text(c, "", 15f, true, Ui.WHITE).apply { gravity = Gravity.CENTER }
        hero.addView(qrTitle, Ui.lp(c, t = 12))
        qrHint = Ui.text(c, "", 13f, false, Color.parseColor("#C7D2FE")).apply { gravity = Gravity.CENTER }
        hero.addView(qrHint, Ui.lp(c, t = 4))
        val info = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        codeView = Ui.text(c, "", 15f, true, Ui.WHITE).apply { background = Ui.rounded(c, Color.argb(30, 255, 255, 255), 10); setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10), Ui.dp(c, 6)) }
        addrView = Ui.text(c, "", 15f, true, Ui.WHITE).apply { background = Ui.rounded(c, Color.argb(30, 255, 255, 255), 10); setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10), Ui.dp(c, 6)) }
        info.addView(codeView)
        info.addView(addrView, Ui.lp(c, Ui.WRAP, Ui.WRAP, l = 8))
        hero.addView(info, Ui.lp(c, Ui.WRAP, Ui.WRAP, t = 12))
        col.addView(hero, Ui.lp(c, t = 12))

        // File yang dibagikan ke HP lain
        val shareCard = Ui.card(c, pad = 14)
        val sh = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        sharedTitle = Ui.text(c, "Dibagikan ke HP lain", 15f, true)
        sh.addView(sharedTitle, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        sharedCount = Ui.text(c, "", 13f, false, Ui.MUTED)
        sh.addView(sharedCount)
        shareCard.addView(sh)
        sharedList = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        shareCard.addView(sharedList, Ui.lp(c, t = 4))
        addBtn = Ui.iconButton(c, Icon.UP, "Tambah file untuk HP lain", Ui.Kind.INDIGO, 48) { pickFiles() }
        shareCard.addView(addBtn, Ui.lp(c, t = 10))
        col.addView(shareCard, Ui.lp(c, t = 12))

        // File yang diterima dari HP lain (semua jenis file)
        val recvCard = Ui.card(c, pad = 14)
        val rh = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        rh.addView(Ui.text(c, "Diterima dari HP lain", 15f, true), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        recvCount = Ui.text(c, "", 13f, false, Ui.MUTED)
        rh.addView(recvCount)
        recvCard.addView(rh)
        recvList = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        recvCard.addView(recvList, Ui.lp(c, t = 4))
        recvCard.addView(Ui.text(c, "Foto dan video masuk ke Galeri › DropBridge. File lain (PDF, dokumen, ZIP, dll.) masuk ke Download › DropBridge.", 12f, false, Ui.MUTED), Ui.lp(c, t = 8))
        val ob = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        ob.addView(Ui.iconButton(c, Icon.IMAGE, "Galeri", Ui.Kind.OUTLINE, 44, 14f) { openGallery() }, Ui.lp(c, 0, Ui.WRAP, r = 5, weight = 1f))
        ob.addView(Ui.iconButton(c, Icon.DOWN, "Download", Ui.Kind.OUTLINE, 44, 14f) { openDownloads() }, Ui.lp(c, 0, Ui.WRAP, l = 5, weight = 1f))
        recvCard.addView(ob, Ui.lp(c, t = 10))
        col.addView(recvCard, Ui.lp(c, t = 12))

        // PC
        pcRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.ripple(c, Ui.CARD, 16, Ui.LINE)
            setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12))
            isClickable = true
            isFocusable = true
            setOnClickListener { startActivity(Intent(c, MoreActivity::class.java)) }
        }
        pcRow.addView(IconView(c, Icon.PC, Ui.ACCENT, 22, 2f))
        pcView = Ui.text(c, "", 14f, false, Ui.INK).apply { setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 8), 0) }
        pcRow.addView(pcView, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        pcRow.addView(IconView(c, Icon.BACK, Ui.MUTED, 16, 2.2f).apply { rotation = 180f })
        col.addView(pcRow, Ui.lp(c, t = 12))

        if (savedInstanceState == null) {
            intent.getStringArrayListExtra("files")?.let { pendingFiles.addAll(it) }
            val pick = intent.getBooleanExtra("pick", false)
            if (intent.getBooleanExtra("start", false) && Host.server == null) turnOn()
            if (pendingFiles.isNotEmpty() || pick) {
                if (Host.server == null) turnOn()
                if (pick) pickFiles()
            }
        }
        refresh()
    }

    private fun turnOn() {
        HostService.start(this)
        step = 1
        shownKey = ""
        requestHotspot()
    }

    private fun turnOff() {
        HostService.stop(this)
        Hotspot.stop()
        step = 1
        shownKey = ""
        refresh()
    }

    /** Nyalakan hotspot khusus (minta izin dulu bila perlu). Tidak perlu bila hotspot biasa sudah menyala. */
    private fun requestHotspot() {
        if (Net.hotspotOn() || !Hotspot.supported()) return
        val need = Hotspot.permissions().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) {
            requestPermissions(need.toTypedArray(), 21)
            return
        }
        Hotspot.start(this) { shownKey = ""; refresh() }
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 21) return
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            Hotspot.start(this) { shownKey = ""; refresh() }
        } else {
            Hotspot.error = "Izin ditolak, jadi hotspot otomatis tidak bisa dibuat."
        }
        refresh()
    }

    private fun pickFiles() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(i, 9)
    }

    private fun openDownloads() {
        try {
            startActivity(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(this, "Buka aplikasi File, folder Download › DropBridge", Toast.LENGTH_LONG).show()
        }
    }

    private fun ago(ms: Long): String {
        val d = (System.currentTimeMillis() - ms) / 1000
        return when {
            d < 45 -> "baru saja"
            d < 3600 -> "${d / 60} mnt lalu"
            else -> "${d / 3600} jam lalu"
        }
    }

    /** Baris file kecil: ikon jenis file, nama, keterangan. */
    private fun fileLine(c: Context, name: String, sub: String): View {
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; minimumHeight = Ui.dp(c, 52) }
        val mime = FileSaver.mimeOf(name)
        val ic = when {
            mime.startsWith("image/") -> Icon.IMAGE
            mime.startsWith("video/") -> Icon.PLAY
            else -> Icon.FILE
        }
        row.addView(android.widget.FrameLayout(c).apply {
            background = Ui.rounded(c, Ui.INDIGO_SOFT, 10)
            addView(IconView(c, ic, Ui.INDIGO, 18, 2f), android.widget.FrameLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18), Gravity.CENTER))
        }, LinearLayout.LayoutParams(Ui.dp(c, 38), Ui.dp(c, 38)).apply { rightMargin = Ui.dp(c, 12) })
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.text(c, name, 14f, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE })
        col.addView(Ui.text(c, sub, 12f, false, Ui.MUTED).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        row.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        return row
    }

    private fun fillLists(srv: HostServer?) {
        val c: Context = this
        val shared = srv?.shared?.toList() ?: emptyList()
        val recv = srv?.receivedList?.toList() ?: emptyList()
        val waiting = pendingUris.size + pendingFiles.size
        val sig = shared.joinToString("|") { it.name + ":" + it.taken } + "#" + recv.joinToString("|") { it.name + ":" + ago(it.time) } + "#$waiting#${srv != null}"
        if (sig == listSig) return
        listSig = sig
        sharedList.removeAllViews()
        sharedCount.text = if (shared.isEmpty()) "" else "${shared.size} file"
        if (shared.isEmpty()) {
            sharedList.addView(Ui.text(c, when {
                waiting > 0 -> "$waiting file menunggu host menyala..."
                srv == null -> "Nyalakan saklar, lalu pilih file apa saja untuk diambil teman."
                else -> "Belum ada. Tambahkan foto, video, PDF, atau file lain."
            }, 13f, false, Ui.MUTED), Ui.lp(c, t = 4))
        }
        for ((i, f) in shared.take(6).withIndex()) {
            if (i > 0) sharedList.addView(View(c).apply { setBackgroundColor(Ui.DIVIDER) }, LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(c, 1)))
            sharedList.addView(fileLine(c, f.name, Xfer.size(f.size) + if (f.taken > 0) " · diambil ${f.taken}×" else " · belum diambil"), Ui.lp(c))
        }
        if (shared.size > 6) sharedList.addView(Ui.text(c, "dan ${shared.size - 6} file lagi", 12f, false, Ui.MUTED), Ui.lp(c, t = 4))

        recvList.removeAllViews()
        recvCount.text = if (recv.isEmpty()) "" else "${recv.size} file"
        if (recv.isEmpty()) {
            recvList.addView(Ui.text(c, "Belum ada file dari HP lain.", 13f, false, Ui.MUTED), Ui.lp(c, t = 4))
        }
        for ((i, f) in recv.take(5).withIndex()) {
            if (i > 0) recvList.addView(View(c).apply { setBackgroundColor(Ui.DIVIDER) }, LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(c, 1)))
            recvList.addView(fileLine(c, f.name, "${Xfer.size(f.size)} · ${f.where} · ${ago(f.time)}"), Ui.lp(c))
        }
        if (recv.size > 5) recvList.addView(Ui.text(c, "dan ${recv.size - 5} file lagi", 12f, false, Ui.MUTED), Ui.lp(c, t = 4))
    }

    private fun openGallery() {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY))
        } catch (e: Exception) {
            Toast.makeText(this, "Buka aplikasi Galeri, album DropBridge", Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 9 || resultCode != RESULT_OK || data == null) return
        val uris = ArrayList<Uri>()
        val clip = data.clipData
        if (clip != null) {
            for (k in 0 until clip.itemCount) uris.add(clip.getItemAt(k).uri)
        } else {
            data.data?.let { uris.add(it) }
        }
        for (u in uris) {
            try { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { /* izin sementara cukup */ }
        }
        pendingUris.addAll(uris)
        refresh()
        if (uris.isNotEmpty()) Toast.makeText(this, "${uris.size} file siap diambil. Minta teman scan QR.", Toast.LENGTH_LONG).show()
    }

    private fun setStepTab(t: TextView, on: Boolean) {
        val c: Context = this
        t.background = if (on) Ui.rounded(c, Ui.CARD, 11) else null
        t.setTextColor(if (on) Ui.INDIGO_DEEP else Ui.INDIGO_SOFT)
    }

    private fun refresh() {
        val srv = Host.server
        updating = true
        toggle.isChecked = srv != null
        updating = false

        val line = Status.line
        val pcn = Prefs.pcName.ifEmpty { Prefs.server.removePrefix("http://") }
        pcView.text = if (line.startsWith("Tersambung")) "PC: $pcn · " + (if (Net.viaRemote) "lewat internet (Tailscale)" else "Wi-Fi lokal, tanpa internet") else "PC belum tersambung · ketuk untuk pengaturan"
        Ui.setChip(scr.chip, "")

        if (srv == null) {
            setSub("Host mati · teman belum bisa masuk", Ui.MUTED)
            warn.visibility = View.GONE
            hero.visibility = View.GONE
            offCard.visibility = View.VISIBLE
            addBtn.visibility = View.GONE
            fillLists(null)
            shownKey = ""
            return
        }
        hero.visibility = View.VISIBLE
        offCard.visibility = View.GONE

        // File yang menunggu host siap
        if (pendingUris.isNotEmpty()) {
            for (u in pendingUris) srv.addShared(u)
            pendingUris.clear()
        }
        if (pendingFiles.isNotEmpty()) {
            for (p in pendingFiles) srv.addSharedFile(java.io.File(p))
            pendingFiles.clear()
        }

        setSub("Host aktif · kode ${srv.code}", Ui.OK)
        addBtn.visibility = View.VISIBLE
        qr.visibility = View.VISIBLE

        val ips = Net.lanIps()
        if (chosen >= ips.size) chosen = 0
        val ip = ips.getOrNull(chosen)
        val wifiStep = Hotspot.active
        if (!wifiStep) step = 2

        // Peringatan hotspot
        val showWarn = !wifiStep && !Net.hotspotOn()
        warn.visibility = if (showWarn) View.VISIBLE else View.GONE
        if (showWarn) {
            warnText.text = when {
                Hotspot.starting -> "Menyalakan hotspot otomatis..."
                Hotspot.error.isNotEmpty() -> "${Hotspot.error}\nKalau HP teman tidak bisa membuka alamat di bawah, nyalakan hotspot biasa dan minta teman menyambung ke situ."
                else -> "Hotspot belum menyala. Lewat Wi-Fi kantor, HP lain sering tidak bisa membuka alamat ini."
            }
            retryBtn.visibility = if (Hotspot.supported() && !Hotspot.starting) View.VISIBLE else View.GONE
        }

        stepRow.visibility = if (wifiStep) View.VISIBLE else View.GONE
        setStepTab(step1, step == 1)
        setStepTab(step2, step == 2)

        val link = if (ip != null) "http://$ip:${srv.port}/?k=${srv.code}" else ""
        val key = "$step|${Hotspot.ssid}|$link"
        if (key != shownKey) {
            shownKey = key
            if (step == 1 && wifiStep) {
                qr.matrix = QrCode.encode(Hotspot.wifiQr())
                qrTitle.text = "Teman scan dengan kamera"
                qrHint.text = "Langsung tersambung ke Wi-Fi ${Hotspot.ssid} tanpa password. Setelah itu ketuk langkah 2."
            } else if (link.isNotEmpty()) {
                qr.matrix = QrCode.encode(link)
                qrTitle.text = "Teman scan untuk membuka halaman"
                qrHint.text = if (wifiStep) "Pastikan HP teman sudah tersambung ke ${Hotspot.ssid}." else "Atau buka alamat di bawah, lalu ketik kodenya."
            } else {
                qr.matrix = null
                qrTitle.text = "Belum ada jaringan"
                qrHint.text = "Nyalakan hotspot atau sambungkan Wi-Fi."
            }
        }
        codeView.text = "Kode ${srv.code}"
        addrView.text = if (ip != null) "$ip:${srv.port}" else ""
        addrView.setOnClickListener { if (ips.size > 1) { chosen = (chosen + 1) % ips.size; shownKey = ""; refresh() } }

        fillLists(srv)
    }

    private var shownSubText = ""
    private fun setSub(t: String, dot: Int) {
        if (t == shownSubText) return
        shownSubText = t
        val sp = android.text.SpannableString("● $t")
        sp.setSpan(android.text.style.ForegroundColorSpan(dot), 0, 1, 0)
        scr.subtitle.text = sp
        scr.subtitle.visibility = View.VISIBLE
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
