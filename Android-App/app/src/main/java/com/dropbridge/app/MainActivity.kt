package com.dropbridge.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Tab Kirim (Home bersih, DropBridge 3): kartu tujuan (laptop + folder), Foto langsung, Pilih file,
 * dan baris ikon berlabel kecil. Yang lain hanya muncul saat perlu: progres kirim, antrean, peringatan.
 * Kirim ke HP lain ada di tab Kirim › Ganti › HP tanpa aplikasi.
 */
class MainActivity : Activity() {

    private var builtDark = false

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var scr: Screen
    private lateinit var routeIcon: FrameLayout
    private lateinit var routeDot: View
    private lateinit var routeTitle: TextView
    private lateinit var routeSub: TextView
    private lateinit var routeBtn: TextView
    private lateinit var routeWarn: TextView
    private lateinit var destCard: LinearLayout
    private var shownRoute = ""
    private lateinit var folderEdit: AutoCompleteTextView
    private lateinit var folderAdapter: ArrayAdapter<String>
    private lateinit var folderHint: TextView
    private lateinit var warnCard: LinearLayout
    private lateinit var inCard: LinearLayout
    private lateinit var inText: TextView
    private lateinit var warnText: TextView
    private lateinit var xferCard: LinearLayout
    private lateinit var xferTitle: TextView
    private lateinit var xferName: TextView
    private lateinit var xferBar: BarView
    private lateinit var xferSize: TextView
    private lateinit var toolsBox: FrameLayout
    // Tujuan "HP tanpa aplikasi" (berbagi lewat QR)
    private lateinit var routeCap: TextView
    private lateinit var folderBlock: LinearLayout
    private lateinit var qrBlock: LinearLayout
    private lateinit var qrView: QrView
    private lateinit var qrCode: TextView
    private lateinit var qrAddr: TextView
    private lateinit var qrHint: TextView
    private lateinit var fotoBtn: View
    private lateinit var pickBtn: View
    private lateinit var shareBtn: View
    private lateinit var guestCard: LinearLayout
    private lateinit var guestTitle: TextView
    private lateinit var guestList: LinearLayout
    private var shownQr = ""
    private var shownGuests = ""
    private var qrStartAt = 0L
    private val pendingShare = ArrayList<android.net.Uri>()
    private var shownTools = ""
    private var shownSub = ""
    private var shownFolders = "-"
    private var pendingAt = 0L
    // Mode Foto langsung: kamera dibuka berulang sampai dibatalkan
    private var capName = ""
    private var capFolder = ""
    private var capCount = 0

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    /** Jalur folder dari ketikan: "A / B" → "A/B" (bagian kosong dibuang). */
    private fun cleanPath(t: String): String =
        t.split('/', '\\').map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotEmpty() }.joinToString("/")

    /** Folder tujuan dari kolom ("" = Tanpa folder), atau null bila belum diisi (kolom diberi fokus). */
    private fun target(): String? {
        val raw = folderEdit.text.toString()
        if (FolderPicker.isRoot(raw)) return ""
        val t = cleanPath(raw)
        if (t.isEmpty()) {
            Toast.makeText(this, "Isi atau pilih folder tujuan dulu", Toast.LENGTH_SHORT).show()
            folderEdit.requestFocus()
            return null
        }
        if (t.count { it == '/' } + 1 > MAX_DEPTH) {
            Toast.makeText(this, "Folder maksimal $MAX_DEPTH tingkat", Toast.LENGTH_SHORT).show()
            return null
        }
        return t
    }

    private fun updateHint() {
        val raw = folderEdit.text.toString()
        val t = cleanPath(raw)
        val exists = t.isNotEmpty() && Prefs.folders.any { it.equals(t, ignoreCase = true) }
        val depth = if (t.isEmpty()) 0 else t.count { it == '/' } + 1
        folderHint.text = when {
            FolderPicker.isRoot(raw) -> "Langsung di penyimpanan"
            t.isEmpty() -> ""
            depth > MAX_DEPTH -> "Maks. $MAX_DEPTH tingkat"
            exists -> "Sudah ada"
            t.contains('/') -> "Subfolder baru"
            else -> "Folder baru"
        }
        folderHint.setTextColor(if (depth > MAX_DEPTH) Ui.DANGER else if (exists || FolderPicker.isRoot(raw)) Ui.ACCENT else Ui.MUTED)
        folderEdit.background = Ui.rounded(this, Ui.CARD, 12).also { it.setStroke(Ui.dp(this, 2), if (raw.isBlank()) Ui.LINE2 else Ui.ACCENT) }
    }

    private fun setTarget(p: String) {
        folderEdit.setText(p)
        folderEdit.setSelection(p.length)
        folderEdit.dismissDropDown()
        Prefs.lastFolder = p
    }

    private fun openPicker() {
        FolderPicker.open(this, folderEdit.text.toString()) { p -> shownFolders = ""; setTarget(p) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Queue.init(this)
        savedInstanceState?.let {
            capName = it.getString("capName", "")
            capFolder = it.getString("capFolder", "")
            capCount = it.getInt("capCount", 0)
        }
        val c: Context = this
        scr = Ui.screen(this, "Kirim", " ", back = false, nav = Ui.TAB_SEND)
        builtDark = Ui.dark
        val col = scr.content

        // ---------- Muncul saat perlu: file dari laptop sudah tersimpan ----------
        inCard = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(c, Ui.SOFT, 18, Ui.ACCENT)
            setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12))
            visibility = View.GONE
        }
        inCard.addView(FrameLayout(c).apply {
            background = Ui.rounded(c, Ui.CARD, 12)
            addView(IconView(c, Icon.DOWN, Ui.ACCENT, 22, 2f), FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.CENTER))
        }, LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)).apply { rightMargin = Ui.dp(c, 12) })
        val inCol = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        inText = Ui.text(c, "", 15f, true, Ui.DEEP)
        inCol.addView(inText)
        inCol.addView(Ui.text(c, "Tersimpan di Download/DropBridge", 13f, false, Ui.DEEP))
        inCard.addView(inCol, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        inCard.addView(Ui.button(c, "Lihat", Ui.Kind.OUTLINE, 40) {
            Inbox.lastNote = ""
            try { startActivity(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)) } catch (e: Exception) {
                Toast.makeText(this, "Buka aplikasi Files › Download › DropBridge", Toast.LENGTH_LONG).show()
            }
        }.apply { textSize = 14f })
        col.addView(inCard, Ui.lp(c, b = 12))

        // ---------- Kartu tujuan: laptop + folder ----------
        destCard = Ui.card(c, pad = 14)
        val rrow = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val iconBox = FrameLayout(c)
        routeIcon = FrameLayout(c)
        iconBox.addView(routeIcon, FrameLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)))
        routeDot = Ui.dot(c, Ui.OK).apply { (background as android.graphics.drawable.GradientDrawable).setStroke(Ui.dp(c, 2), Ui.CARD) }
        iconBox.addView(routeDot, FrameLayout.LayoutParams(Ui.dp(c, 14), Ui.dp(c, 14), Gravity.BOTTOM or Gravity.END))
        rrow.addView(iconBox, LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)).apply { rightMargin = Ui.dp(c, 10) })
        val rcol = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        routeCap = Ui.caption(c, "Ke laptop")
        rcol.addView(routeCap)
        routeTitle = Ui.text(c, "", 16f, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        routeSub = Ui.text(c, "", 12f, false, Ui.MUTED).apply { maxLines = 2 }
        rcol.addView(routeTitle)
        rcol.addView(routeSub)
        rrow.addView(rcol, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        routeBtn = Ui.text(c, "Ganti", 14f, true, Ui.INK).apply {
            gravity = Gravity.CENTER
            minimumHeight = Ui.dp(c, 40)
            setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 14), 0)
            background = Ui.ripple(c, Ui.CARD, 12, Ui.LINE)
            isClickable = true
            isFocusable = true
            contentDescription = "Ganti PC tujuan"
            // Pilih PC tujuan (laptop Windows, laptop Linux, ...) atau hubungkan PC baru
            setOnClickListener {
                PcPicker.open(this@MainActivity, onQr = { goQr() }) {
                    shownRoute = ""; shownSub = ""; shownFolders = "-"; shownQr = ""
                    Conn.connect(this@MainActivity)
                    refresh()
                }
            }
        }
        rrow.addView(routeBtn, LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP).apply { leftMargin = Ui.dp(c, 8) })
        destCard.addView(rrow)
        routeWarn = Ui.text(c, "", 13f, true, Ui.WARN_TX).apply {
            visibility = View.GONE
            background = Ui.rounded(c, Ui.WARN_BG, 12)
            setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8))
        }
        destCard.addView(routeWarn, Ui.lp(c, t = 10))
        folderBlock = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        folderBlock.addView(View(c).apply { setBackgroundColor(Ui.LINE) }, LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(c, 1)).apply { topMargin = Ui.dp(c, 12) })
        val fhead = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fhead.addView(Ui.caption(c, "Ke folder"), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        folderHint = Ui.text(c, "", 12f, true, Ui.ACCENT)
        fhead.addView(folderHint)
        folderBlock.addView(fhead, Ui.lp(c, t = 12))
        folderAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, ArrayList(Prefs.folders))
        folderEdit = AutoCompleteTextView(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setAdapter(folderAdapter)
            threshold = 1
            setText(Prefs.startFolder())
            setDropDownBackgroundDrawable(Ui.rounded(c, Ui.CARD, 12, Ui.LINE))
        }
        Ui.styleInput(c, folderEdit, "Ketik atau pilih folder")
        folderEdit.textSize = 17f
        folderEdit.typeface = Ui.FONT_MONO
        folderEdit.minimumHeight = Ui.dp(c, 52)
        folderEdit.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 14), 0)
        folderEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(e: android.text.Editable?) { updateHint() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
        })
        // Ikon folder di kiri kolom: buka panel Pilih folder
        val frow = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        frow.addView(FrameLayout(c).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "Pilih folder"
            background = Ui.ripple(c, Ui.FIELD, 12)
            setOnClickListener { openPicker() }
            addView(IconView(c, Icon.FOLDER, Ui.ACCENT, 22, 2f), FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.CENTER))
        }, LinearLayout.LayoutParams(Ui.dp(c, 52), Ui.dp(c, 52)).apply { rightMargin = Ui.dp(c, 8) })
        frow.addView(folderEdit, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        folderBlock.addView(frow, Ui.lp(c, t = 8))
        destCard.addView(folderBlock)

        // HP tanpa aplikasi: QR + kode + alamat langsung di kartu tujuan
        qrBlock = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        qrBlock.addView(View(c).apply { setBackgroundColor(Ui.LINE) }, LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(c, 1)).apply { topMargin = Ui.dp(c, 12) })
        val qrow = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        qrView = QrView(c, 140)
        qrow.addView(qrView, LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP).apply { rightMargin = Ui.dp(c, 14) })
        val qcol = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        qcol.addView(Ui.caption(c, "Kode"))
        qrCode = Ui.mono(Ui.text(c, "", 26f, true)).apply { letterSpacing = 0.08f }
        qcol.addView(qrCode)
        qcol.addView(Ui.caption(c, "Alamat"), Ui.lp(c, t = 8))
        qrAddr = Ui.mono(Ui.text(c, "", 14f, false))
        qcol.addView(qrAddr)
        qrow.addView(qcol, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        qrBlock.addView(qrow, Ui.lp(c, t = 12))
        qrHint = Ui.text(c, "", 12f, false, Ui.MUTED).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { Ui.goTab(this@MainActivity, Ui.TAB_HOST) }
        }
        qrBlock.addView(qrHint, Ui.lp(c, t = 10))
        destCard.addView(qrBlock)
        col.addView(destCard, Ui.lp(c))

        // ---------- Muncul saat perlu: progres kirim ----------
        xferCard = Ui.card(c, pad = 14)
        xferCard.visibility = View.GONE
        xferTitle = Ui.text(c, "", 14f, true)
        xferCard.addView(xferTitle)
        xferBar = BarView(c).apply { fillColor = Ui.ACCENT; trackColor = Ui.FIELD }
        xferCard.addView(xferBar, Ui.lp(c, t = 8))
        xferSize = Ui.text(c, "", 12f, false, Ui.MUTED)
        xferCard.addView(xferSize, Ui.lp(c, t = 6))
        xferName = Ui.text(c, "", 12f, false, Ui.MUTED).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE }
        xferCard.addView(xferName, Ui.lp(c, t = 2))
        col.addView(xferCard, Ui.lp(c, t = 12))

        // ---------- Muncul saat perlu: antrean menunggu laptop ----------
        warnCard = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.ripple(c, Ui.WARN_BG, 18, Ui.WARN_LINE)
            setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12))
            isClickable = true
            isFocusable = true
            visibility = View.GONE
            setOnClickListener { startActivity(Intent(c, QueueActivity::class.java)) }
        }
        warnCard.addView(IconView(c, Icon.CLOCK, Ui.WARN_TX, 22, 2f))
        warnText = Ui.text(c, "", 14f, true, Ui.WARN_TX).apply { setPadding(Ui.dp(c, 12), 0, 0, 0) }
        warnCard.addView(warnText, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        col.addView(warnCard, Ui.lp(c, t = 12))

        // ---------- Aksi utama ----------
        fotoBtn = Ui.iconButton(c, Icon.CAMERA, "Foto langsung", Ui.Kind.PRIMARY, 72, 18f) { checkReach { startCapture(true) } }.apply {
            background = Ui.ripple(c, Ui.FILL, 18)
        }
        col.addView(fotoBtn, Ui.lp(c, t = 12))
        pickBtn = Ui.iconButton(c, Icon.IMAGE, "Pilih file", Ui.Kind.SOFT, 56, 17f) { checkReach { pickForPc() } }
        col.addView(pickBtn, Ui.lp(c, t = 10))
        shareBtn = Ui.iconButton(c, Icon.IMAGE, "Pilih file untuk dibagikan", Ui.Kind.INDIGO, 56, 17f) { pickToShare() }.apply { visibility = View.GONE }
        col.addView(shareBtn, Ui.lp(c, t = 12))
        // Yang dibagikan + tamu yang masuk lewat browser
        guestCard = Ui.card(c, pad = 14).apply { visibility = View.GONE }
        val gh = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        guestTitle = Ui.text(c, "", 14f, true)
        gh.addView(guestTitle, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        gh.addView(Ui.text(c, "Kelola", 14f, true, Ui.INDIGO).apply {
            minimumHeight = Ui.dp(c, 40); gravity = Gravity.CENTER_VERTICAL; setPadding(Ui.dp(c, 10), 0, 0, 0)
            isClickable = true; isFocusable = true
            setOnClickListener { Ui.goTab(this@MainActivity, Ui.TAB_HOST) }
        })
        guestCard.addView(gh)
        guestList = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        guestCard.addView(guestList)
        col.addView(guestCard, Ui.lp(c, t = 12))

        // ---------- Baris ikon ----------
        toolsBox = FrameLayout(c)
        col.addView(toolsBox, Ui.lp(c, t = 12))

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        }
        if (Prefs.pin.isEmpty()) {
            Status.line = "Belum ada PIN. Ketuk Ganti untuk menyambungkan ke laptop."
        } else if (Status.line.isEmpty()) {
            Conn.connect(this)
        }
        AutoSend.schedule(this)
        Inbox.schedule(this)
        refresh()
    }

    /** Baris ikon: Semua folder, Muat ulang, Teks, Antrean (hanya bila ada), Foto kecil, Tambah perangkat. */
    private fun refreshTools(queued: Int) {
        val key = "$queued|${Prefs.shrinkPhotos}|${Prefs.role}"
        if (key == shownTools) return
        shownTools = key
        val items = ArrayList<Ui.Tool>()
        if (Prefs.role != "send") items.add(Ui.Tool("Semua folder", Icon.FOLDERS) { Ui.goTab(this, Ui.TAB_FILES) })
        items.add(Ui.Tool("Muat ulang", Icon.REFRESH) {
            shownRoute = ""; shownFolders = "-"
            Toast.makeText(this, "Memeriksa sambungan ke laptop...", Toast.LENGTH_SHORT).show()
            Beat.kick()
            Conn.connect(this)
        })
        items.add(Ui.Tool("Teks", Icon.TEXT) { TextSheet.open(this) })
        if (queued > 0) items.add(Ui.Tool("Antrean", Icon.QUEUE, badge = queued.toString()) { startActivity(Intent(this, QueueActivity::class.java)) })
        items.add(Ui.Tool("Foto kecil", Icon.SHRINK, on = Prefs.shrinkPhotos) {
            Prefs.shrinkPhotos = !Prefs.shrinkPhotos
            Toast.makeText(this, if (Prefs.shrinkPhotos) "Foto dikecilkan ≤ 512 KB" else "Foto dikirim resolusi penuh", Toast.LENGTH_SHORT).show()
            refresh()
        })
        items.add(Ui.Tool("Tambah perangkat", Icon.ADD_PHONE) { Ui.goTab(this, Ui.TAB_HOST) })
        toolsBox.removeAllViews()
        toolsBox.addView(Ui.tools(this, items), FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
    }

    private fun refreshFolders() {
        val key = Prefs.folders.joinToString("|")
        if (key == shownFolders) return
        shownFolders = key
        folderAdapter.clear()
        folderAdapter.addAll(Prefs.folders)
        updateHint()
    }

    private fun pickForPc() {
        val folder = target() ?: return
        Prefs.lastFolder = folderEdit.text.toString().trim()
        startActivity(Intent(this, SendActivity::class.java)
            .putExtra("pick", true)
            .putExtra("direct", true)
            .putExtra("folder", folder))
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putString("capName", capName)
        out.putString("capFolder", capFolder)
        out.putInt("capCount", capCount)
    }

    /** Buka kamera; foto disimpan di cache aplikasi lalu masuk antrean folder terpilih. */
    private fun startCapture(first: Boolean) {
        if (first) {
            val folder = target() ?: return
            Prefs.lastFolder = folderEdit.text.toString().trim()
            capFolder = folder
            capCount = 0
        }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        capName = "IMG_${stamp}_${capCount + 1}.jpg"
        val uri = CaptureProvider.uriFor(capName)
        val i = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("foto", uri)
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(i, 31)
        } catch (e: Exception) {
            Toast.makeText(this, "Aplikasi kamera tidak ditemukan", Toast.LENGTH_LONG).show()
            capName = ""
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 41) {
            if (resultCode != RESULT_OK || data == null) return
            val uris = ArrayList<android.net.Uri>()
            data.clipData?.let { cd -> for (i in 0 until cd.itemCount) uris.add(cd.getItemAt(i).uri) }
            data.data?.let { if (it !in uris) uris.add(it) }
            for (u in uris) {
                try { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { /* izin sementara cukup */ }
            }
            val srv = Host.server
            if (srv != null) uris.forEach { srv.addShared(it) } else pendingShare.addAll(uris)
            shownGuests = ""
            if (uris.isNotEmpty()) Toast.makeText(this, "${uris.size} file siap diambil. Minta teman scan QR.", Toast.LENGTH_LONG).show()
            refresh()
            return
        }
        if (requestCode != 31 || capName.isEmpty()) return
        val f = java.io.File(CaptureProvider.dir(this), capName)
        val name = capName
        capName = ""
        if (resultCode == RESULT_OK && f.isFile && f.length() > 0) {
            capCount++
            val n = capCount
            val folder = capFolder
            val shrink = Prefs.shrinkPhotos
            val app = applicationContext
            Thread {
                val ok = Queue.addFile(f, name, folder, shrink, Queue.autoMode(folder)) != null
                runOnUiThread {
                    if (ok) {
                        Toast.makeText(app, "Foto ke-$n masuk antrean ${pathLabel(folder)}", Toast.LENGTH_SHORT).show()
                        if (!UploadService.running) UploadService.start(this)
                    } else {
                        Toast.makeText(app, "Foto gagal disimpan", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
            startCapture(false) // lanjut foto berikutnya; tekan kembali di kamera untuk selesai
        } else {
            f.delete()
            if (capCount > 0) {
                Toast.makeText(this, "Selesai: $capCount foto masuk antrean ${pathLabel(capFolder)}", Toast.LENGTH_LONG).show()
            } else if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Kamera tidak menyimpan foto. Coba aplikasi kamera lain.", Toast.LENGTH_LONG).show()
            }
            capCount = 0
        }
    }

    private fun refresh() {
        Beat.tick()
        val line = Status.line
        val ok = Beat.alive()
        val host = Prefs.server.removePrefix("http://").substringBefore(':')
        val pcn = Prefs.pcName.ifEmpty { if (host.isEmpty()) "PC" else "PC $host" }
        val qr = Prefs.sendTo == "qr"
        if (Host.server != null && !qr) Ui.setChip(scr.chip, "Berbagi aktif", indigo = true) else Ui.setChip(scr.chip, "")
        // Status sambungan di bawah judul: titik hijau = tersambung, oranye = belum
        val guests = Host.server?.guests() ?: emptyList()
        val (dot, sub) = when {
            qr && Host.server != null -> Ui.INDIGO to ("Berbagi aktif" + if (guests.isNotEmpty()) " · ${guests.size} HP tamu" else "")
            qr -> Ui.MUTED to "Menyalakan berbagi..."
            ok -> Ui.OK to (pcn + if (Net.viaRemote) " · lewat internet" else " · Wi-Fi")
            line == Net.PENDING -> Ui.PENDING to "Menunggu izin dari $pcn"
            line.startsWith("Mencari") -> Ui.MUTED to "Mencari PC..."
            Prefs.pin.isEmpty() -> Ui.PENDING to "Belum tersambung ke PC"
            else -> Ui.PENDING to "$pcn tidak terjangkau${Beat.lastSeen()}"
        }
        if (sub != shownSub) {
            shownSub = sub
            val sp = android.text.SpannableString("● $sub")
            sp.setSpan(android.text.style.ForegroundColorSpan(dot), 0, 1, 0)
            scr.subtitle.text = sp
            scr.subtitle.visibility = View.VISIBLE
        }
        folderBlock.visibility = if (qr) View.GONE else View.VISIBLE
        qrBlock.visibility = if (qr) View.VISIBLE else View.GONE
        fotoBtn.visibility = if (qr) View.GONE else View.VISIBLE
        pickBtn.visibility = if (qr) View.GONE else View.VISIBLE
        shareBtn.visibility = if (qr) View.VISIBLE else View.GONE
        guestCard.visibility = if (qr) View.VISIBLE else View.GONE
        if (qr) refreshQr(guests) else refreshRoute(ok, line)
        refreshFolders()
        // Menunggu izin dari laptop: cek lagi tiap 4 detik sampai diizinkan atau ditolak
        val now0 = System.currentTimeMillis()
        if (line == Net.PENDING && !Conn.busy && now0 - pendingAt > 4000) {
            pendingAt = now0
            Conn.connect(this)
        }

        // Kiriman dari laptop untuk HP ini: diambil sendiri selama aplikasi terbuka
        if (ok) Inbox.maybeCheck(this)
        val fresh = Inbox.lastNote.isNotEmpty() && System.currentTimeMillis() - Inbox.lastNoteAt < 10 * 60_000
        if (fresh) {
            inText.text = Inbox.lastNote
            inCard.visibility = View.VISIBLE
        } else inCard.visibility = View.GONE

        val n = Queue.all().size
        refreshTools(n)
        // Antrean hanya diberi kartu bila laptop tak terjangkau: foto tetap aman, terkirim sendiri nanti
        if (n > 0 && !ok && !UploadService.running) {
            warnText.text = "$n file menunggu laptop. Terkirim sendiri begitu terjangkau."
            warnCard.visibility = View.VISIBLE
        } else warnCard.visibility = View.GONE

        val sending = Xfer.active && Xfer.count > 0
        if (sending) {
            xferCard.visibility = View.VISIBLE
            val cur = Uploader.currentId
            val dest = Queue.all().firstOrNull { it.id == cur }?.folder
            xferTitle.text = "Mengirim ${Xfer.index} dari ${Xfer.count}" + if (dest != null) " ke ${Queue.label(dest)}" else ""
            xferName.text = Xfer.name
            xferBar.fraction = Xfer.percent() / 100f
            val eta = Xfer.eta()
            xferSize.text = "${Xfer.size(Xfer.sentTotal)} dari ${Xfer.size(Xfer.totalBytes)}" +
                (if (Xfer.speed > 0) " · ${Xfer.rate(Xfer.speed)}" else "") +
                (if (eta.isNotEmpty()) " · $eta" else "")
        } else {
            xferCard.visibility = View.GONE
        }
    }

    /** Pindah tujuan ke "HP tanpa aplikasi": berbagi dinyalakan, QR tampil di kartu tujuan. */
    private fun goQr() {
        Prefs.sendTo = "qr"
        shownRoute = ""; shownSub = ""; shownQr = ""; shownGuests = ""
        if (Host.server == null) { HostService.start(this); qrStartAt = System.currentTimeMillis() }
        refresh()
    }

    /** Laptop tujuan tidak terjangkau: beri tahu dulu, file tetap bisa masuk antrean. */
    private fun checkReach(go: () -> Unit) {
        if (Prefs.pin.isEmpty() || Beat.alive() || Status.line == Net.PENDING || Conn.busy) { go(); return }
        val pc = Prefs.pcName.ifEmpty { "Laptop" }
        val seen = Beat.lastSeen().removePrefix(" · ")
        AlertDialog.Builder(this)
            .setTitle("$pc tidak terjangkau")
            .setMessage((if (seen.isNotEmpty()) "${seen.replaceFirstChar { it.uppercase() }}. " else "") +
                "File masuk antrean dan terkirim sendiri saat $pc muncul lagi.")
            .setPositiveButton("Masukkan antrean") { _, _ -> go() }
            .setNegativeButton("Batal", null).show()
    }

    private fun pickToShare() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        @Suppress("DEPRECATION")
        try { startActivityForResult(i, 41) } catch (e: Exception) {
            Toast.makeText(this, "Pemilih file tidak tersedia", Toast.LENGTH_LONG).show()
        }
    }

    /** Kartu tujuan + daftar tamu saat mengirim ke HP tanpa aplikasi. */
    private fun refreshQr(guests: List<Guest>) {
        val c: Context = this
        var srv = Host.server
        if (srv == null && System.currentTimeMillis() - qrStartAt > 5000) {
            HostService.start(this); qrStartAt = System.currentTimeMillis()
        }
        if (srv != null && pendingShare.isNotEmpty()) {
            for (u in pendingShare) srv.addShared(u)
            pendingShare.clear()
        }
        if (shownRoute != "qr") {
            shownRoute = "qr"
            routeCap.text = "Ke HP tanpa aplikasi"
            routeIcon.removeAllViews()
            routeIcon.background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Ui.FIELD) }
            routeIcon.addView(IconView(c, Icon.QR, Ui.INDIGO, 20, 2f), FrameLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20), Gravity.CENTER))
            routeTitle.text = "HP tanpa aplikasi"
            routeSub.text = "Lewat browser · tamu masuk dengan kode"
            routeSub.visibility = View.VISIBLE
            routeBtn.text = "Ganti"
            routeWarn.visibility = View.GONE
            destCard.contentDescription = "Ke HP tanpa aplikasi, lewat browser"
        }
        Ui.setDot(routeDot, if (srv != null) Ui.INDIGO else Ui.MUTED)
        val ip = Net.lanIps().firstOrNull()
        val link = if (srv != null && ip != null) "http://$ip:${srv.port}/?k=${srv.code}" else ""
        val hot = Net.hotspotOn()
        val key = "$link|$hot"
        if (key != shownQr) {
            shownQr = key
            qrView.matrix = if (link.isNotEmpty()) QrCode.encode(link) else null
            qrCode.text = srv?.code ?: "······"
            qrAddr.text = if (srv != null && ip != null) "$ip:${srv.port}" else if (srv == null) "Menyalakan..." else "Belum ada jaringan"
            qrHint.text = if (hot) "Scan dari kamera HP mana pun. Hotspot dan langkah lengkap ada di Ekosistem."
                else "Scan dari kamera HP mana pun. Lewat Wi-Fi kantor sering diblokir: ketuk di sini untuk menyalakan hotspot."
        }
        // Dibagikan + tamu
        val shared = srv?.shared?.size ?: 0
        val fmt = java.text.SimpleDateFormat("HH.mm", java.util.Locale.getDefault())
        fun st(g: Guest) = (if (g.active) "aktif" else "terakhir " + fmt.format(java.util.Date(g.last))) +
            (if (g.sent > 0) " · kirim ${g.sent} file" else "") + (if (g.got > 0) " · ambil ${g.got} file" else "")
        val gkey = "$shared|" + guests.joinToString("|") { it.name + st(it) }
        if (gkey == shownGuests) return
        shownGuests = gkey
        guestTitle.text = if (shared > 0) "Dibagikan · $shared file" else "Belum ada file dibagikan"
        guestList.removeAllViews()
        if (guests.isEmpty()) {
            guestList.addView(Ui.text(c, "Belum ada HP tamu. Minta teman scan QR di atas.", 13f, false, Ui.MUTED), Ui.lp(c, t = 6))
        }
        for (g in guests) {
            val r = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; minimumHeight = Ui.dp(c, 52) }
            r.addView(FrameLayout(c).apply {
                background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(if (g.active) Ui.INDIGO else Ui.FIELD) }
                addView(IconView(c, Icon.PHONE, if (g.active) android.graphics.Color.WHITE else Ui.INDIGO, 18, 2f), FrameLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18), Gravity.CENTER))
            }, LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)).apply { rightMargin = Ui.dp(c, 12) })
            val t = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            t.addView(Ui.text(c, g.name, 14f, true))
            t.addView(Ui.text(c, st(g), 12f, false, Ui.MUTED))
            r.addView(t, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            guestList.addView(r)
        }
    }

    /** Baris laptop di kartu tujuan. Hanya digambar ulang bila keadaannya berubah. */
    private fun refreshRoute(ok: Boolean, line: String) {
        val c: Context = this
        val searching = !ok && (Conn.busy || line.startsWith("Mencari"))
        val addr = Net.current.ifEmpty { Prefs.server }.removePrefix("http://").removeSuffix(":8000")
        val state = when {
            searching -> "search"
            line == Net.PENDING -> "pending"
            ok && Net.viaRemote -> "net"
            ok -> "lan"
            Prefs.pin.isEmpty() -> "nopin"
            else -> "fail"
        }
        val appV = Ui.appVersion(c)
        val pcV = Prefs.pcVersion
        val pc = Prefs.pcName.ifEmpty { if (addr.isEmpty()) "PC" else addr }
        val os = if (Prefs.pcOs.isNotEmpty()) " · ${Prefs.pcOs}" else ""
        routeCap.text = "Ke laptop"
        val key = "$state|$addr|$line|${Beat.lastSeen()}|$pcV|$pc|$os|${Prefs.role}"
        if (key == shownRoute) return
        shownRoute = key
        data class R(val icon: Int, val fg: Int, val bg: Int, val dot: Int, val title: String, val sub: String)
        val r = when (state) {
            "search" -> R(Icon.LAPTOP, Ui.MUTED, Ui.FIELD, Ui.MUTED, pc, "Mencari di Wi-Fi, lalu lewat internet...")
            "pending" -> R(Icon.LAPTOP, Ui.WARN_TX, Ui.WARN_BG, Ui.PENDING, pc, "Menunggu izin. Tekan Izinkan di laptop.")
            "lan" -> R(Icon.LAPTOP, Ui.ACCENT, Ui.SOFT, Ui.OK, "$pc$os", if (Prefs.role == "send") "Hanya kirim" else "")
            "net" -> R(Icon.GLOBE, Ui.INDIGO, Ui.INDIGO_SOFT, Ui.OK, "$pc$os", "Lewat internet (Tailscale), pakai kuota")
            "nopin" -> R(Icon.LAPTOP, Ui.MUTED, Ui.FIELD, Ui.MUTED, "Belum tersambung", "Ketuk Ganti, lalu pilih laptop")
            else -> R(Icon.LAPTOP, Ui.WARN_TX, Ui.WARN_BG, Ui.PENDING, "$pc$os", "Tidak terjangkau${Beat.lastSeen()}. Foto tetap masuk antrean.")
        }
        routeIcon.removeAllViews()
        routeIcon.background = Ui.rounded(c, r.bg, 12)
        routeIcon.addView(IconView(c, r.icon, r.fg, 22, 2f), FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.CENTER))
        Ui.setDot(routeDot, r.dot)
        routeTitle.text = r.title
        routeSub.text = r.sub
        routeSub.visibility = if (r.sub.isEmpty()) View.GONE else View.VISIBLE
        routeBtn.text = if (state == "nopin") "Pilih" else "Ganti"
        destCard.contentDescription = "Ke PC ${r.title}. ${r.sub}"
        // Laptop lain ada di Wi-Fi ini (dari Conn): tawarkan, tidak pindah sendiri
        val other = line.substringAfter("terjangkau. ", "").takeIf { it.contains("ada di Wi-Fi ini") }
        when {
            other != null -> { routeWarn.text = other; routeWarn.visibility = View.VISIBLE }
            Ui.versionMismatch(appV, pcV) -> { routeWarn.text = "Versi beda: aplikasi $appV, laptop $pcV. Perbarui yang lebih lama."; routeWarn.visibility = View.VISIBLE }
            else -> routeWarn.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        if (Ui.themeStale(this, builtDark)) { recreate(); return }
        shownTools = ""
        Beat.kick()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }
}
