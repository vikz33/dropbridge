package com.dropbridge.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Tab Kirim: pilih tujuan (PC atau HP lain), lalu pilih file. */
class MainActivity : Activity() {

    private var builtDark = false

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var scr: Screen
    private lateinit var tabPc: TextView
    private lateinit var tabHp: TextView
    private lateinit var pcBox: LinearLayout
    private lateinit var hpBox: LinearLayout
    private lateinit var hostPill: TextView
    private lateinit var hostCard: LinearLayout
    private lateinit var hostInfo: TextView
    private lateinit var hostRecv: LinearLayout
    private var hostSig = "-"
    private lateinit var routeCard: LinearLayout
    private lateinit var routeIcon: android.widget.FrameLayout
    private lateinit var routeTitle: TextView
    private lateinit var routeSub: TextView
    private lateinit var routeBtn: TextView
    private lateinit var routeWarn: TextView
    private var shownRoute = ""
    private lateinit var folderEdit: AutoCompleteTextView
    private lateinit var folderAdapter: ArrayAdapter<String>
    private lateinit var chips: LinearLayout
    private lateinit var queueCard: LinearLayout
    private lateinit var queueBadge: TextView
    private lateinit var queueTitle: TextView
    private lateinit var queueSub: TextView
    private lateinit var queueGroups: LinearLayout
    private var shownQueue = "-"
    private lateinit var xferCard: LinearLayout
    private lateinit var xferTitle: TextView
    private lateinit var xferName: TextView
    private lateinit var xferBar: BarView
    private lateinit var xferSize: TextView
    private lateinit var folderHint: TextView
    private lateinit var shrinkText: TextView
    private lateinit var recentCard: LinearLayout
    private lateinit var recentList: LinearLayout
    private var recentAt = 0L
    private var recentSig = ""
    private var wasSending = false
    private var shownSub = ""
    private var shownShrink: Boolean? = null
    private var mode = 0 // 0 = ke PC, 1 = ke HP lain
    // Mode Foto langsung: kamera dibuka berulang sampai dibatalkan
    private var capName = ""
    private var capFolder = ""
    private var capCount = 0
    private var shownFolders = "-"

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    private fun segTab(c: Context, label: String, onClick: () -> Unit): TextView =
        Ui.text(c, label, 15f, true, Ui.MUTED).apply {
            gravity = Gravity.CENTER
            minimumHeight = Ui.dp(c, 46)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    /** Tombol besar dengan ikon di atas dan label di tengah. indigo = mode HP lain. */
    private fun bigTile(c: Context, icon: Int, label: String, primary: Boolean, indigo: Boolean = false, onClick: () -> Unit): LinearLayout {
        val bg = when {
            primary && indigo -> Ui.INDIGO_FILL
            primary -> Ui.FILL
            indigo -> Ui.INDIGO_SOFT
            else -> Ui.SOFT2
        }
        val fg = when {
            primary -> Ui.WHITE
            indigo -> Ui.INDIGO_INK
            else -> Ui.DEEP
        }
        return LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = label
            background = Ui.ripple(c, bg, 22)
            setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 8), 0)
            setOnClickListener { onClick() }
            addView(IconView(c, icon, if (primary) Ui.WHITE else if (indigo) Ui.INDIGO else Ui.ACCENT, 30, 2f),
                LinearLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30)).apply { gravity = Gravity.CENTER_HORIZONTAL })
            addView(Ui.text(c, label, 16f, true, fg).apply { gravity = Gravity.CENTER; setPadding(0, Ui.dp(c, 8), 0, 0) },
                LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        }
    }

    /** Jalur folder dari ketikan: "A / B" → "A/B" (bagian kosong dibuang). */
    private fun cleanPath(t: String): String =
        t.split('/', '\\').map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotEmpty() }.joinToString("/")

    private fun updateHint() {
        val t = cleanPath(folderEdit.text.toString())
        val exists = t.isNotEmpty() && Prefs.folders.any { it.equals(t, ignoreCase = true) }
        val depth = if (t.isEmpty()) 0 else t.count { it == '/' } + 1
        folderHint.text = when {
            t.isEmpty() -> ""
            depth > MAX_DEPTH -> "Maks. $MAX_DEPTH tingkat"
            exists -> "Sudah ada di PC"
            t.contains('/') -> "Subfolder baru"
            else -> "Folder baru"
        }
        folderHint.setTextColor(if (depth > MAX_DEPTH) Ui.DANGER else if (exists) Ui.ACCENT else Ui.MUTED)
    }

    // ---------- Panel Pilih folder (FolderPicker.kt) ----------
    private fun setTarget(p: String) {
        folderEdit.setText(p)
        folderEdit.setSelection(p.length)
        folderEdit.dismissDropDown()
        Prefs.lastFolder = p
    }

    private fun openPicker() {
        FolderPicker.open(this, cleanPath(folderEdit.text.toString())) { p -> shownFolders = ""; setTarget(p) }
    }

    private fun step(c: Context, n: String, t: String): LinearLayout =
        LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.text(c, n, 14f, true, Ui.INDIGO_INK).apply {
                gravity = Gravity.CENTER
                background = Ui.rounded(c, Ui.INDIGO_SOFT, 999)
            }, LinearLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30)).apply { rightMargin = Ui.dp(c, 12) })
            addView(Ui.text(c, t, 14f, false, Ui.INK), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
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
        scr = Ui.screen(this, "Kirim ke PC", " ", back = false, nav = Ui.TAB_SEND)
        builtDark = Ui.dark
        val col = scr.content

        // Pilihan tujuan
        val seg = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Ui.rounded(c, Ui.CARD, 16, Ui.LINE)
            setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4))
        }
        tabPc = segTab(c, "Ke PC") { setMode(0) }
        tabHp = segTab(c, "Ke HP lain") { setMode(1) }
        seg.addView(tabPc, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        seg.addView(tabHp, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        col.addView(seg, Ui.lp(c))

        // ---------- Ke PC ----------
        pcBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        // Jalur sambungan: lewat apa, perlu internet atau tidak, dan tombol Cari PC
        routeCard = Ui.card(c, pad = 12)
        val rrow = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        routeIcon = android.widget.FrameLayout(c)
        rrow.addView(routeIcon, LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40)).apply { rightMargin = Ui.dp(c, 12) })
        val rcol = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        routeTitle = Ui.text(c, "", 14f, true)
        routeSub = Ui.text(c, "", 12f, false, Ui.MUTED)
        rcol.addView(routeTitle)
        rcol.addView(routeSub)
        rrow.addView(rcol, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        routeBtn = Ui.text(c, "Cari PC", 14f, true, Ui.ACCENT).apply {
            gravity = Gravity.CENTER
            minimumHeight = Ui.dp(c, 40)
            setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 14), 0)
            background = Ui.ripple(c, Ui.CARD, 12, Ui.LINE2)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (Prefs.pin.isEmpty()) startActivity(Intent(c, MoreActivity::class.java))
                else { Conn.connect(this@MainActivity, rescan = true); shownRoute = ""; refresh() }
            }
        }
        rrow.addView(routeBtn, LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP).apply { leftMargin = Ui.dp(c, 8) })
        routeCard.addView(rrow)
        routeWarn = Ui.text(c, "", 12f, true, Ui.WARN_TX).apply { visibility = View.GONE; setPadding(0, Ui.dp(c, 8), 0, 0) }
        routeCard.addView(routeWarn)
        pcBox.addView(routeCard, Ui.lp(c, t = 12))

        // Folder tujuan
        val fcard = Ui.card(c, pad = 14)
        val fhead = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fhead.addView(Ui.text(c, "Folder tujuan", 13f, true, Ui.MUTED), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        folderHint = Ui.text(c, "", 12f, true, Ui.ACCENT)
        fhead.addView(folderHint)
        fcard.addView(fhead)
        folderAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, ArrayList(Prefs.folders))
        folderEdit = AutoCompleteTextView(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setAdapter(folderAdapter)
            threshold = 1
            setText(Prefs.lastFolder)
            setDropDownBackgroundDrawable(Ui.rounded(c, Ui.CARD, 12, Ui.LINE))
        }
        Ui.styleInput(c, folderEdit, "Ketik atau pilih folder")
        folderEdit.textSize = 18f
        folderEdit.setTypeface(folderEdit.typeface, android.graphics.Typeface.BOLD)
        folderEdit.minimumHeight = Ui.dp(c, 54)
        folderEdit.background = Ui.rounded(c, Ui.CARD, 14).also { it.setStroke(Ui.dp(c, 2), Ui.ACCENT) }
        folderEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(e: android.text.Editable?) { updateHint() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
        })
        // Ikon folder di kiri kolom: buka panel Pilih folder (masuk subfolder, buat baru, pilih)
        val frow = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        frow.addView(android.widget.FrameLayout(c).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "Pilih folder"
            background = Ui.ripple(c, Ui.SOFT2, 14)
            setOnClickListener { openPicker() }
            addView(IconView(c, Icon.FOLDER, Ui.ACCENT, 24, 2f), android.widget.FrameLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24), Gravity.CENTER))
        }, LinearLayout.LayoutParams(Ui.dp(c, 54), Ui.dp(c, 54)).apply { rightMargin = Ui.dp(c, 8) })
        frow.addView(folderEdit, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        fcard.addView(frow, Ui.lp(c, t = 8))
        chips = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fcard.addView(android.widget.HorizontalScrollView(c).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }, Ui.lp(c, t = 10))
        pcBox.addView(fcard, Ui.lp(c, t = 12))

        // Dua tombol besar: pilih dari galeri/file, atau langsung foto
        val acts = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        acts.addView(bigTile(c, Icon.IMAGE, "Pilih file", true) { pickForPc() }, LinearLayout.LayoutParams(0, Ui.dp(c, 112), 1f).apply { rightMargin = Ui.dp(c, 5) })
        acts.addView(bigTile(c, Icon.CAMERA, "Foto langsung", false) { startCapture(true) }, LinearLayout.LayoutParams(0, Ui.dp(c, 112), 1f).apply { leftMargin = Ui.dp(c, 5) })
        pcBox.addView(acts, Ui.lp(c, t = 12))

        val opts = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(c, 4), 0, 0, 0)
        }
        shrinkText = Ui.text(c, "", 13f, false, Ui.MUTED).apply {
            minimumHeight = Ui.dp(c, 44)
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setOnClickListener { startActivity(Intent(c, MoreActivity::class.java)) }
        }
        opts.addView(shrinkText, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        opts.addView(Ui.text(c, "Kirim teks", 14f, true, Ui.ACCENT).apply {
            minimumHeight = Ui.dp(c, 44)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 4), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { askText() }
        })
        pcBox.addView(opts, Ui.lp(c, t = 2))

        // Kartu Pengiriman (tampil hanya saat mengirim)
        xferCard = Ui.card(c, pad = 14)
        xferCard.visibility = View.GONE
        xferCard.addView(Ui.text(c, "Pengiriman", 15f, true))
        val xbox = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(c, Ui.TINT, 14)
            setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10))
        }
        xferTitle = Ui.text(c, "", 14f, true)
        xbox.addView(xferTitle)
        xferName = Ui.text(c, "", 12f, false, Ui.MUTED).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE }
        xbox.addView(xferName, Ui.lp(c, t = 2))
        xferBar = BarView(c).apply { fillColor = Ui.ACCENT; trackColor = Ui.LINE }
        xbox.addView(xferBar, Ui.lp(c, t = 8))
        xferSize = Ui.text(c, "", 12f, false, Ui.MUTED)
        xbox.addView(xferSize, Ui.lp(c, t = 6))
        xferCard.addView(xbox, Ui.lp(c, t = 8))
        pcBox.addView(xferCard, Ui.lp(c, t = 12))

        // Antrean: file yang menunggu PC, per folder
        queueCard = Ui.card(c, pad = 14)
        val qTop = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        queueBadge = Ui.text(c, "0", 16f, true, Ui.DEEP).apply { gravity = Gravity.CENTER; background = Ui.rounded(c, Ui.SOFT, 12) }
        qTop.addView(queueBadge, LinearLayout.LayoutParams(Ui.dp(c, 38), Ui.dp(c, 38)).apply { rightMargin = Ui.dp(c, 12) })
        val qText = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        queueTitle = Ui.text(c, "", 15f, true)
        queueSub = Ui.text(c, "", 12f, false, Ui.MUTED)
        qText.addView(queueTitle)
        qText.addView(queueSub)
        qTop.addView(qText, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        queueCard.addView(qTop)
        // 3 folder teratas; semuanya di halaman Antrean
        queueGroups = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        queueCard.addView(queueGroups, Ui.lp(c, t = 8))
        val qa = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        qa.addView(Ui.button(c, "Semua antrean", Ui.Kind.OUTLINE, 44) {
            startActivity(Intent(this, QueueActivity::class.java))
        }, Ui.lp(c, 0, Ui.WRAP, r = 6, weight = 1f))
        qa.addView(Ui.button(c, "Kirim sekarang", Ui.Kind.PRIMARY, 44) {
            if (Queue.all().isEmpty()) Status.line = "Antrean kosong." else UploadService.start(this)
        }, Ui.lp(c, 0, Ui.WRAP, l = 6, weight = 1f))
        queueCard.addView(qa, Ui.lp(c, t = 10))
        pcBox.addView(queueCard, Ui.lp(c, t = 12))

        // Bukti file sudah sampai di PC
        recentCard = Ui.card(c, pad = 14)
        recentCard.visibility = View.GONE
        val rhead = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        rhead.addView(Ui.text(c, "Terakhir masuk ke PC", 15f, true), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        rhead.addView(Ui.text(c, "Lihat di File", 13f, true, Ui.ACCENT).apply {
            minimumHeight = Ui.dp(c, 36)
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setOnClickListener { Ui.goTab(this@MainActivity, Ui.TAB_FILES) }
        })
        recentCard.addView(rhead)
        recentList = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        recentCard.addView(recentList)
        pcBox.addView(recentCard, Ui.lp(c, t = 12))
        col.addView(pcBox, Ui.lp(c))

        // ---------- Ke HP lain ----------
        hpBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        val tcard = Ui.card(c, pad = 14)
        tcard.addView(Ui.text(c, "Tujuan", 13f, true, Ui.MUTED))
        val trow = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        trow.addView(android.widget.FrameLayout(c).apply {
            background = Ui.rounded(c, Ui.INDIGO_SOFT, 14)
            addView(IconView(c, Icon.PHONE, Ui.INDIGO, 24, 2f), android.widget.FrameLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24), Gravity.CENTER))
        }, LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)).apply { rightMargin = Ui.dp(c, 12) })
        val tcol = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        tcol.addView(Ui.text(c, "HP teman di dekatmu", 17f, true))
        tcol.addView(Ui.text(c, "Lewat hotspot HP ini · tidak butuh internet", 13f, false, Ui.MUTED))
        trow.addView(tcol, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        hostPill = Ui.text(c, "", 12f, true, Ui.MUTED).apply { setPadding(Ui.dp(c, 10), Ui.dp(c, 5), Ui.dp(c, 10), Ui.dp(c, 5)) }
        trow.addView(hostPill)
        tcard.addView(trow, Ui.lp(c, t = 8))
        hpBox.addView(tcard, Ui.lp(c, t = 12))

        val hacts = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        hacts.addView(bigTile(c, Icon.UP, "Pilih file", true, indigo = true) {
            startActivity(Intent(this, HostActivity::class.java).putExtra("pick", true).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }, LinearLayout.LayoutParams(0, Ui.dp(c, 112), 1f).apply { rightMargin = Ui.dp(c, 5) })
        hacts.addView(bigTile(c, Icon.DOWN, "Terima dari teman", false, indigo = true) {
            startActivity(Intent(this, HostActivity::class.java).putExtra("start", true).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }, LinearLayout.LayoutParams(0, Ui.dp(c, 112), 1f).apply { leftMargin = Ui.dp(c, 5) })
        hpBox.addView(hacts, Ui.lp(c, t = 12))

        val hopts = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(Ui.dp(c, 4), 0, 0, 0) }
        hopts.addView(Ui.text(c, "Semua jenis file: foto, video, PDF, dokumen", 13f, false, Ui.MUTED).apply { minimumHeight = Ui.dp(c, 44); gravity = Gravity.CENTER_VERTICAL },
            LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        hopts.addView(Ui.text(c, "Lihat QR", 14f, true, Ui.INDIGO).apply {
            minimumHeight = Ui.dp(c, 44)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 4), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { Ui.goTab(this@MainActivity, Ui.TAB_HOST) }
        })
        hpBox.addView(hopts, Ui.lp(c, t = 2))

        // Ringkasan saat host aktif: yang dibagikan dan yang diterima
        hostCard = Ui.card(c, pad = 14)
        hostCard.addView(Ui.text(c, "Aktivitas berbagi", 15f, true))
        hostInfo = Ui.text(c, "", 13f, false, Ui.MUTED)
        hostCard.addView(hostInfo, Ui.lp(c, t = 2))
        hostRecv = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        hostCard.addView(hostRecv, Ui.lp(c, t = 4))
        hostCard.isClickable = true
        hostCard.setOnClickListener { Ui.goTab(this, Ui.TAB_HOST) }
        hpBox.addView(hostCard, Ui.lp(c, t = 12))
        col.addView(hpBox, Ui.lp(c))

        setMode(0)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        }
        if (Prefs.pin.isEmpty()) {
            Status.line = "Belum ada PIN. Ketuk di sini untuk mengisi PIN dari layar PC."
        } else if (Status.line.isEmpty()) {
            Conn.connect(this)
        }
        AutoSend.schedule(this)
        refresh()
    }

    private fun setMode(m: Int) {
        mode = m
        val c: Context = this
        val on = if (m == 0) tabPc else tabHp
        val off = if (m == 0) tabHp else tabPc
        on.background = Ui.rounded(c, if (m == 0) Ui.FILL else Ui.INDIGO_FILL, 12)
        on.setTextColor(Ui.WHITE)
        off.background = null
        off.setTextColor(Ui.MUTED)
        pcBox.visibility = if (m == 0) View.VISIBLE else View.GONE
        hpBox.visibility = if (m == 1) View.VISIBLE else View.GONE
        scr.title.text = if (m == 0) "Kirim ke PC" else "Kirim ke HP lain"
        shownSub = ""
        if (::folderHint.isInitialized) refresh()
    }

    private fun refreshChips() {
        val list = Prefs.folders.take(6)
        val key = list.joinToString("|")
        if (key == shownFolders) return
        shownFolders = key
        folderAdapter.clear()
        folderAdapter.addAll(Prefs.folders)
        chips.removeAllViews()
        val c: Context = this
        if (list.isNotEmpty()) chips.addView(Ui.text(c, "Terakhir:", 13f, false, Ui.MUTED), Ui.lp(c, Ui.WRAP, Ui.WRAP, r = 8))
        updateHint()
        for (n in list) {
            chips.addView(Ui.text(c, pathLabel(n), 14f, true, Ui.DEEP).apply {
                background = Ui.rounded(c, Ui.SOFT, 999)
                setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8))
                isClickable = true
                setOnClickListener {
                    folderEdit.setText(n)
                    folderEdit.setSelection(n.length)
                    folderEdit.dismissDropDown()
                }
            }, Ui.lp(c, Ui.WRAP, Ui.WRAP, r = 8))
        }
    }

    private fun pickForPc() {
        val folder = folderEdit.text.toString().trim()
        if (folder.isEmpty()) {
            Toast.makeText(this, "Isi nama folder tujuan dulu", Toast.LENGTH_SHORT).show()
            folderEdit.requestFocus()
            return
        }
        Prefs.lastFolder = folder
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
            val folder = folderEdit.text.toString().trim()
            if (folder.isEmpty()) {
                Toast.makeText(this, "Isi nama folder tujuan dulu", Toast.LENGTH_SHORT).show()
                folderEdit.requestFocus()
                return
            }
            Prefs.lastFolder = folder
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
                val ok = Queue.addFile(f, name, folder, shrink) != null
                runOnUiThread {
                    if (ok) {
                        Toast.makeText(app, "Foto ke-$n masuk antrean $folder", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Selesai: $capCount foto masuk antrean ${capFolder}", Toast.LENGTH_LONG).show()
            } else if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Kamera tidak menyimpan foto. Coba aplikasi kamera lain.", Toast.LENGTH_LONG).show()
            }
            capCount = 0
        }
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
        val host = Prefs.server.removePrefix("http://").substringBefore(':')
        val pcn = Prefs.pcName.ifEmpty { if (host.isEmpty()) "PC" else "PC $host" }
        if (Host.server != null) Ui.setChip(scr.chip, "Host aktif", indigo = true) else Ui.setChip(scr.chip, "")
        // Status sambungan di bawah judul: titik hijau = tersambung, oranye = belum
        val (dot, sub) = when {
            mode == 1 -> Ui.INDIGO to "Teman tidak perlu aplikasi"
            ok -> Ui.OK to "$pcn terhubung"
            line.startsWith("Mencari") -> Ui.PENDING to "Mencari PC..."
            Prefs.pin.isEmpty() -> Ui.PENDING to "Belum tersambung ke PC"
            else -> Ui.PENDING to "$pcn tidak terjangkau"
        }
        if (sub != shownSub) {
            shownSub = sub
            val sp = android.text.SpannableString("● $sub")
            sp.setSpan(android.text.style.ForegroundColorSpan(dot), 0, 1, 0)
            scr.subtitle.text = sp
            scr.subtitle.visibility = View.VISIBLE
        }
        refreshHost()
        val shrink = Prefs.shrinkPhotos
        if (shrink != shownShrink) {
            shownShrink = shrink
            val t = (if (shrink) "Foto dikecilkan ≤ 512 KB" else "Foto dikirim resolusi penuh") + " · Ubah"
            val sp = android.text.SpannableString(t)
            sp.setSpan(android.text.style.ForegroundColorSpan(Ui.ACCENT), t.length - 4, t.length, 0)
            sp.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), t.length - 4, t.length, 0)
            shrinkText.text = sp
        }
        refreshRoute(ok, line, host)
        refreshChips()

        val groups = Queue.groups()
        val n = groups.sumOf { it.second.size }
        queueBadge.text = n.toString()
        queueTitle.text = if (ok) "$n file di antrean · ${groups.size} folder" else "$n file menunggu PC"
        // PC tak terjangkau: kartu oranye, foto tetap aman di antrean
        val c: Context = this
        queueCard.background = if (ok) Ui.rounded(c, Ui.CARD, 20, Ui.LINE) else Ui.rounded(c, Ui.WARN_BG, 20, Ui.WARN_LINE)
        queueBadge.background = Ui.rounded(c, if (ok) Ui.SOFT else Ui.WARN_BADGE, 12)
        queueBadge.setTextColor(if (ok) Ui.DEEP else Ui.WARN_TX)
        refreshQueueGroups(groups)
        queueSub.text = when {
            UploadService.running -> "Sedang mengirim..."
            !ok -> "Tetap ambil foto. Terkirim sendiri begitu PC terjangkau."
            Prefs.autoSend -> "Terkirim sendiri begitu PC terjangkau"
            else -> "Ketuk Kirim sekarang saat PC terjangkau"
        }
        queueCard.visibility = if (n == 0) View.GONE else View.VISIBLE

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
                (if (eta.isNotEmpty()) " · $eta" else "") +
                (if (Xfer.speed > 0) " · ${Xfer.rate(Xfer.speed)}" else "")
        } else {
            xferCard.visibility = View.GONE
        }
        // Daftar "Terakhir masuk ke PC": muat ulang tiap 15 detik dan setelah kiriman selesai
        val now = System.currentTimeMillis()
        if (mode == 0 && ok && ((wasSending && !sending) || now - recentAt > 15000)) {
            recentAt = now
            loadRecent()
        }
        wasSending = sending
    }

    private fun refreshQueueGroups(groups: List<Pair<String, List<Item>>>) {
        val cur = Uploader.currentId
        val sig = groups.joinToString("|") { g -> g.first + ":" + g.second.size } + "#" + cur
        if (sig == shownQueue) return
        shownQueue = sig
        queueGroups.removeAllViews()
        val c: Context = this
        for ((folder, items) in groups.take(3)) {
            val row = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = Ui.dp(c, 48)
                isClickable = true
                setOnClickListener { startActivity(Intent(c, QueueActivity::class.java)) }
            }
            row.addView(IconView(c, Icon.FOLDER, Ui.ACCENT, 20, 2f))
            row.addView(Ui.text(c, Queue.label(folder), 15f, true).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 8), 0)
            }, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            val sending = items.any { it.id == cur }
            row.addView(Ui.text(c, if (sending) "dikirim..." else "${items.size} file · ${Xfer.size(items.sumOf { it.size })}", 13f, sending, if (sending) Ui.ACCENT else Ui.MUTED))
            queueGroups.addView(row)
        }
        if (groups.size > 3) {
            queueGroups.addView(Ui.text(c, "dan ${groups.size - 3} folder lagi", 13f, false, Ui.MUTED), Ui.lp(c, t = 2))
        }
    }

    /** Kartu jalur sambungan di mode Ke PC. Hanya digambar ulang bila keadaannya berubah. */
    private fun refreshRoute(ok: Boolean, line: String, host: String) {
        val c: Context = this
        val searching = Conn.busy || line.startsWith("Mencari")
        val addr = Net.current.ifEmpty { Prefs.server }.removePrefix("http://").removeSuffix(":8000")
        val state = when {
            searching -> "search"
            ok && Net.viaRemote -> "net"
            ok -> "lan"
            Prefs.pin.isEmpty() -> "nopin"
            else -> "fail"
        }
        val appV = Ui.appVersion(c)
        val pcV = Prefs.pcVersion
        val key = "$state|$addr|$line|$pcV"
        if (key == shownRoute) return
        shownRoute = key
        data class R(val icon: Int, val fg: Int, val bg: Int, val title: String, val sub: String, val btn: String)
        val r = when (state) {
            "search" -> R(Icon.WIFI, Ui.MUTED, Ui.FIELD, "Mencari PC...", "Cek Wi-Fi dulu, lalu jalur internet (Tailscale)", "Mencari…")
            "lan" -> R(Icon.WIFI, Ui.ACCENT, Ui.SOFT2, "Lewat Wi-Fi / hotspot yang sama", "Tidak butuh internet · $addr", "Cari PC")
            "net" -> R(Icon.GLOBE, Ui.INDIGO, Ui.INDIGO_SOFT, "Lewat internet (Tailscale)", "Butuh internet, pakai kuota data · $addr", "Cari PC")
            "nopin" -> R(Icon.PC, Ui.MUTED, Ui.FIELD, "Belum tersambung ke PC", "Masukkan PIN dari layar PC", "Sambungkan")
            else -> R(Icon.WARN, Ui.WARN_TX, Ui.WARN_BADGE, "PC tidak ditemukan", line.removePrefix("PC tidak ditemukan. "), "Cari PC")
        }
        routeIcon.removeAllViews()
        routeIcon.background = Ui.rounded(c, r.bg, 12)
        routeIcon.addView(IconView(c, r.icon, r.fg, 22, 2f), android.widget.FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.CENTER))
        routeTitle.text = r.title
        routeSub.text = r.sub
        routeBtn.text = r.btn
        routeBtn.isEnabled = state != "search"
        routeBtn.alpha = if (state == "search") 0.5f else 1f
        routeCard.background = if (state == "fail") Ui.rounded(c, Ui.WARN_BG, 20, Ui.WARN_LINE) else Ui.rounded(c, Ui.CARD, 20, Ui.LINE)
        routeCard.contentDescription = "${r.title}. ${r.sub}"
        // Versi aplikasi dan PC beda: salah satu kemungkinan belum diperbarui
        if (Ui.versionMismatch(appV, pcV)) {
            routeWarn.text = "Versi beda: aplikasi $appV, PC $pcV. Perbarui yang lebih lama."
            routeWarn.visibility = View.VISIBLE
        } else routeWarn.visibility = View.GONE
    }

    /** Bagian "Ke HP lain": status host dan file yang terakhir diterima. */
    private fun refreshHost() {
        val srv = Host.server
        val c: Context = this
        val sig = if (srv == null) "off" else "${srv.shared.size}|${srv.received}|${srv.receivedList.firstOrNull()?.name}"
        if (sig == hostSig) return
        hostSig = sig
        hostPill.text = if (srv != null) "● Host aktif" else "Host mati"
        hostPill.setTextColor(if (srv != null) Ui.INDIGO_INK else Ui.MUTED)
        hostPill.background = Ui.rounded(c, if (srv != null) Ui.INDIGO_SOFT else Ui.FIELD, 999)
        hostCard.visibility = if (srv == null) View.GONE else View.VISIBLE
        if (srv == null) return
        hostInfo.text = "${srv.shared.size} file dibagikan · ${srv.received} file diterima · kode ${srv.code}"
        hostRecv.removeAllViews()
        for ((i, r) in srv.receivedList.take(3).withIndex()) {
            if (i > 0) hostRecv.addView(View(c).apply { setBackgroundColor(Ui.DIVIDER) }, LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(c, 1)))
            val row = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8)) }
            row.addView(Ui.text(c, r.name, 14f, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE })
            row.addView(Ui.text(c, "${Xfer.size(r.size)} · masuk ke ${r.where}", 12f, false, Ui.MUTED))
            hostRecv.addView(row, Ui.lp(c))
        }
    }

    private fun fmtTime(ts: Double): String {
        val d = System.currentTimeMillis() / 1000.0 - ts
        return when {
            d < 45 -> "baru saja"
            d < 3600 -> "${(d / 60).toInt()} mnt lalu"
            d < 86400 -> "${(d / 3600).toInt()} jam lalu"
            else -> java.text.SimpleDateFormat("d MMM, HH.mm", java.util.Locale("id")).format(java.util.Date((ts * 1000).toLong()))
        }
    }

    private fun httpBytes(url: String): ByteArray? {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 4000
        conn.readTimeout = 10000
        conn.setRequestProperty("Authorization", "Bearer ${Prefs.token}")
        return try {
            if (conn.responseCode != 200) null else conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private class Recent(val folder: String, val name: String, val modified: Double, val thumb: Boolean, var bmp: android.graphics.Bitmap? = null)

    /** Ambil 3 file terbaru di PC beserta thumbnail-nya. */
    private fun loadRecent() {
        val base = Net.current.ifEmpty { Prefs.server }
        if (base.isEmpty()) return
        Thread {
            try {
                val raw = httpBytes("$base/api/files/recent?limit=3") ?: return@Thread
                val arr = org.json.JSONObject(String(raw)).getJSONArray("files")
                val items = (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    Recent(o.getString("folder"), o.getString("name"), o.optDouble("modified", 0.0), o.optBoolean("thumb"))
                }
                val sig = items.joinToString("|") { it.folder + "/" + it.name }
                if (sig == recentSig) { runOnUiThread { refreshRecentTimes(items) }; return@Thread }
                for (r in items) if (r.thumb) {
                    try {
                        val b = httpBytes("$base/api/files/${android.net.Uri.encode(r.name)}/thumb?folder=${android.net.Uri.encode(r.folder)}")
                        if (b != null) r.bmp = android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)
                    } catch (e: Exception) { /* tanpa thumbnail */ }
                }
                runOnUiThread { recentSig = sig; showRecent(items) }
            } catch (e: Exception) { /* PC sedang tak terjangkau */ }
        }.start()
    }

    private val recentSubs = ArrayList<TextView>()

    private fun refreshRecentTimes(items: List<Recent>) {
        items.forEachIndexed { i, r -> recentSubs.getOrNull(i)?.text = Queue.label(r.folder) + " · " + fmtTime(r.modified) }
    }

    private fun showRecent(items: List<Recent>) {
        val c: Context = this
        recentList.removeAllViews()
        recentSubs.clear()
        recentCard.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        items.forEachIndexed { i, r ->
            if (i > 0) recentList.addView(View(c).apply { setBackgroundColor(Ui.DIVIDER) }, LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(c, 1)))
            val row = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = Ui.dp(c, 58)
                isClickable = true
                setOnClickListener { Ui.goTab(this@MainActivity, Ui.TAB_FILES) }
            }
            val pic = android.widget.FrameLayout(c).apply { background = Ui.rounded(c, Ui.FIELD, 10); clipToOutline = true }
            if (r.bmp != null) {
                pic.addView(android.widget.ImageView(c).apply { scaleType = android.widget.ImageView.ScaleType.CENTER_CROP; setImageBitmap(r.bmp) })
            } else {
                pic.addView(Ui.text(c, r.name.substringAfterLast('.', "FILE").uppercase().take(4), 10f, true, Ui.MUTED).apply { gravity = Gravity.CENTER },
                    android.widget.FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
            }
            row.addView(pic, LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)).apply { rightMargin = Ui.dp(c, 12) })
            val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            col.addView(Ui.text(c, r.name, 14f, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE })
            val sub = Ui.text(c, Queue.label(r.folder) + " · " + fmtTime(r.modified), 12f, false, Ui.MUTED).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
            recentSubs.add(sub)
            col.addView(sub)
            row.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            row.addView(IconView(c, Icon.CHECK, Ui.OK, 20, 2.6f).apply { contentDescription = "Sudah di PC" })
            recentList.addView(row, Ui.lp(c))
        }
    }

    override fun onResume() {
        super.onResume()
        if (Ui.themeStale(this, builtDark)) { recreate(); return }
        recentAt = 0L
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }
}
