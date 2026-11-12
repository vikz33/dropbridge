package com.dropbridge.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Layar yang muncul dari menu Bagikan: pilih folder tujuan di PC lalu kirim. */
class SendActivity : Activity() {

    private var uris: List<Uri> = emptyList()
    private var sharedText: String = ""
    private lateinit var info: TextView
    private lateinit var info2: TextView
    private lateinit var chips: LinearLayout
    private lateinit var chipScroll: android.widget.HorizontalScrollView
    private lateinit var folderHint: TextView
    private lateinit var hint: TextView
    private lateinit var shrinkSwitch: android.widget.Switch
    private lateinit var folderEdit: AutoCompleteTextView
    private lateinit var sendBtn: Button
    private lateinit var folderAdapter: ArrayAdapter<String>
    private var known: List<String> = emptyList()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    private fun streamOf(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun streamsOf(i: Intent): List<Uri> {
        val l: ArrayList<Uri>? =
            if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        return l ?: emptyList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.applyTheme(this)
        Prefs.init(this)
        Queue.init(this)

        val i = intent
        if (i.action == Intent.ACTION_SEND) {
            val u = streamOf(i)
            if (u != null) uris = listOf(u)
            else sharedText = i.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        } else if (i.action == Intent.ACTION_SEND_MULTIPLE) {
            uris = streamsOf(i)
        }

        known = Prefs.folders

        val c: android.content.Context = this
        sendBtn = Ui.button(c, "Kirim", Ui.Kind.PRIMARY, 56) { send() }
        val bar = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        bar.addView(Ui.button(c, "Batal", Ui.Kind.OUTLINE, 56) { finish() }, Ui.lp(c, 0, Ui.WRAP, r = 6, weight = 1f))
        bar.addView(sendBtn, Ui.lp(c, 0, Ui.WRAP, l = 6, weight = 2f))
        val fromShare = uris.isNotEmpty()
        val s = Ui.screen(this, if (fromShare) "Kirim ke…" else "Kirim ke PC", null, back = true, bottom = bar)
        val col = s.content

        if (fromShare) {
            // Dari menu Bagikan: pilih tujuan. HP lain = host di HP ini, teman scan QR.
            col.addView(Ui.iconButton(c, Icon.PHONE, "Ke HP lain (tanpa aplikasi)", Ui.Kind.INDIGO, 60, 16f) { toOtherPhone() }, Ui.lp(c, b = 12))
            col.addView(Ui.caption(c, "Atau kirim ke PC"), Ui.lp(c, b = 8))
        }

        val summary = Ui.card(c, Ui.CARD, 14)
        val srow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val ic = android.widget.FrameLayout(c).apply {
            background = Ui.rounded(c, Ui.SOFT, 14)
            addView(IconView(c, Icon.IMAGE, Ui.DEEP, 24, 1.8f), android.widget.FrameLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24), android.view.Gravity.CENTER))
        }
        srow.addView(ic, LinearLayout.LayoutParams(Ui.dp(c, 52), Ui.dp(c, 52)).apply { rightMargin = Ui.dp(c, 12) })
        val scol = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        info = Ui.text(c, "", 16f, true)
        info2 = Ui.text(c, "", 13f, false, Ui.MUTED)
        scol.addView(info)
        scol.addView(info2)
        srow.addView(scol, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        summary.addView(srow)
        col.addView(summary, Ui.lp(c))

        // Folder tujuan: sama seperti tab Kirim (ikon folder = panel Pilih folder dengan subfolder)
        val fcard = Ui.card(c, pad = 14)
        val fhead = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        fhead.addView(Ui.text(c, "Folder tujuan di PC", 13f, true, Ui.MUTED), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        folderHint = Ui.text(c, "", 12f, true, Ui.ACCENT)
        fhead.addView(folderHint)
        fcard.addView(fhead)
        folderAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, ArrayList(known))
        folderEdit = AutoCompleteTextView(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setAdapter(folderAdapter)
            threshold = 1
            setText(Prefs.lastFolder)
            setSelection(text.length)
            setDropDownBackgroundDrawable(Ui.rounded(c, Ui.CARD, 12, Ui.LINE))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { updateHint() }
            })
        }
        Ui.styleInput(c, folderEdit, "Ketik atau pilih folder")
        folderEdit.textSize = 18f
        folderEdit.setTypeface(folderEdit.typeface, android.graphics.Typeface.BOLD)
        folderEdit.minimumHeight = Ui.dp(c, 54)
        folderEdit.background = Ui.rounded(c, Ui.CARD, 14).also { it.setStroke(Ui.dp(c, 2), Ui.ACCENT) }
        val frow = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        frow.addView(android.widget.FrameLayout(c).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "Pilih folder"
            background = Ui.ripple(c, Ui.SOFT2, 14)
            setOnClickListener { openPicker() }
            addView(IconView(c, Icon.FOLDER, Ui.ACCENT, 24, 2f), android.widget.FrameLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24), android.view.Gravity.CENTER))
        }, LinearLayout.LayoutParams(Ui.dp(c, 54), Ui.dp(c, 54)).apply { rightMargin = Ui.dp(c, 8) })
        frow.addView(folderEdit, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        fcard.addView(frow, Ui.lp(c, t = 8))
        hint = Ui.text(c, "", 13f, true, Ui.AMBER_TX).apply {
            setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10))
            background = Ui.rounded(c, Ui.AMBER_BG, 12)
            visibility = View.GONE
        }
        fcard.addView(hint, Ui.lp(c, t = 8))
        chips = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        chipScroll = android.widget.HorizontalScrollView(c).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }
        fcard.addView(chipScroll, Ui.lp(c, t = 10))
        col.addView(fcard, Ui.lp(c, t = 12))

        val shrinkCard = Ui.card(c, Ui.CARD, 14)
        val shrinkRow = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        val shrinkTexts = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        shrinkTexts.addView(Ui.text(c, "Kecilkan foto (maks 512 KB)", 16f, true, Ui.INK))
        shrinkTexts.addView(Ui.text(c, "Matikan untuk kirim resolusi penuh, mis. foto liburan.", 13f, false, Ui.MUTED))
        shrinkRow.addView(shrinkTexts, LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        shrinkSwitch = android.widget.Switch(c).apply {
            isChecked = Prefs.shrinkPhotos
            setOnCheckedChangeListener { _, on -> Prefs.shrinkPhotos = on }
        }
        shrinkRow.addView(shrinkSwitch)
        shrinkCard.addView(shrinkRow)
        col.addView(shrinkCard, Ui.lp(c, t = 12))

        updateInfo()
        refreshChips()
        updateHint()
        loadFolders()

        if (uris.isEmpty() && sharedText.isEmpty() && i.getBooleanExtra("pick", false) && savedInstanceState == null) pick()
    }

    private fun pick() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(i, 7)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 7) return
        val list = ArrayList<Uri>()
        if (resultCode == RESULT_OK && data != null) {
            val clip = data.clipData
            if (clip != null) {
                for (k in 0 until clip.itemCount) list.add(clip.getItemAt(k).uri)
            } else {
                val d = data.data
                if (d != null) list.add(d)
            }
        }
        if (list.isEmpty()) {
            finish()
            return
        }
        uris = list
        if (intent.getBooleanExtra("direct", false)) {
            // Dari tab Kirim: folder sudah dipilih di sana, langsung masuk antrean.
            queueAndFinish(intent.getStringExtra("folder") ?: Prefs.lastFolder)
            return
        }
        updateInfo()
    }

    /** Teruskan file ke mode host. Disalin dulu, karena izin baca dari aplikasi lain bisa berakhir. */
    private fun toOtherPhone() {
        sendBtn.isEnabled = false
        info2.text = "Menyiapkan file untuk HP lain..."
        val list = uris
        val app = applicationContext
        Thread {
            val copied = ArrayList<String>()
            for (u in list) HostFiles.copyIn(app, u)?.let { copied.add(it) }
            runOnUiThread {
                if (copied.isEmpty()) {
                    Toast.makeText(this, "File tidak bisa dibaca", Toast.LENGTH_LONG).show()
                    sendBtn.isEnabled = true
                    return@runOnUiThread
                }
                startActivity(Intent(this, HostActivity::class.java)
                    .putStringArrayListExtra("files", copied)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
                finish()
            }
        }.start()
    }

    private fun updateInfo() {
        when {
            uris.isNotEmpty() -> {
                info.text = "${uris.size} file dipilih"
                info2.text = "Siap dikirim ke PC"
                sendBtn.text = "Kirim ${uris.size} file"
                sendBtn.isEnabled = true
            }
            sharedText.isNotEmpty() -> {
                info.text = "Teks dibagikan"
                info2.text = sharedText.take(120)
                sendBtn.text = "Kirim teks ke PC"
                sendBtn.isEnabled = true
            }
            else -> {
                info.text = "Belum ada file"
                info2.text = "Pilih file dulu"
                sendBtn.text = "Kirim"
                sendBtn.isEnabled = false
            }
        }
    }

    private fun refreshChips() {
        chips.removeAllViews()
        val c: android.content.Context = this
        val list = known.take(6)
        chipScroll.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        if (list.isNotEmpty()) chips.addView(Ui.text(c, "Terakhir:", 13f, false, Ui.MUTED), Ui.lp(c, Ui.WRAP, Ui.WRAP, r = 8))
        for (n in list) {
            chips.addView(Ui.text(c, pathLabel(n), 14f, true, Ui.DEEP).apply {
                background = Ui.rounded(c, Ui.SOFT, 999)
                setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8))
                isClickable = true
                setOnClickListener { setTarget(n) }
            }, Ui.lp(c, Ui.WRAP, Ui.WRAP, r = 8))
        }
    }

    private fun setTarget(p: String) {
        folderEdit.setText(p)
        folderEdit.setSelection(p.length)
        folderEdit.dismissDropDown()
    }

    /** Jalur folder dari ketikan: "A / B" → "A/B" (bagian kosong dibuang). */
    private fun cleanPath(t: String): String =
        t.split('/', '\\').map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotEmpty() }.joinToString("/")

    private fun openPicker() {
        FolderPicker.open(this, cleanPath(folderEdit.text.toString())) { p -> setTarget(p); loadFolders() }
    }

    private fun withinOneEdit(x: String, y: String): Boolean {
        val a = x.lowercase()
        val b = y.lowercase()
        if (a == b || Math.abs(a.length - b.length) > 1) return false
        var i = 0
        while (i < a.length && i < b.length && a[i] == b[i]) i++
        if (a.length == b.length) return a.substring(i + 1) == b.substring(i + 1)
        val s = if (a.length < b.length) a else b
        val l = if (a.length < b.length) b else a
        return s.substring(i) == l.substring(i + 1)
    }

    private fun updateHint() {
        val t = cleanPath(folderEdit.text.toString())
        val exists = t.isNotEmpty() && known.any { it.equals(t, ignoreCase = true) }
        val depth = if (t.isEmpty()) 0 else t.count { it == '/' } + 1
        folderHint.text = when {
            t.isEmpty() -> ""
            depth > MAX_DEPTH -> "Maks. $MAX_DEPTH tingkat"
            exists -> "Sudah ada di PC"
            t.contains('/') -> "Subfolder baru"
            else -> "Folder baru"
        }
        folderHint.setTextColor(if (depth > MAX_DEPTH) Ui.DANGER else if (exists) Ui.ACCENT else Ui.MUTED)
        // Peringatan salah ketik: nama hampir sama dengan folder yang sudah ada
        val similar = if (!exists && t.length >= 4) known.filter { withinOneEdit(it, t) }.take(3) else emptyList()
        hint.text = "Mirip dengan: ${similar.joinToString(", ") { pathLabel(it) }}. Salah ketik?"
        hint.visibility = if (similar.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun loadFolders() {
        Thread {
            try {
                val base = Net.ensureServer() ?: return@Thread
                val token = Net.ensureToken(base)
                val names = Net.folders(base, token)
                runOnUiThread {
                    known = names
                    folderAdapter.clear()
                    folderAdapter.addAll(names)
                    folderAdapter.notifyDataSetChanged()
                    refreshChips()
                    updateHint()
                }
            } catch (e: Exception) {
                // PC tidak terjangkau: pakai daftar tersimpan, file tetap bisa masuk antrean.
            }
        }.start()
    }

    private fun send() {
        if (uris.isEmpty() && sharedText.isNotEmpty()) {
            sendBtn.isEnabled = false
            val text = sharedText
            Thread {
                var msg: String
                try {
                    val base = Net.ensureServer()
                    if (base == null) {
                        msg = "PC tidak ditemukan."
                    } else {
                        Net.sendText(base, Net.ensureToken(base), text)
                        msg = "Teks terkirim ke PC."
                    }
                } catch (e: Exception) {
                    msg = "Gagal: ${e.message}"
                }
                runOnUiThread {
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    finish()
                }
            }.start()
            return
        }

        val folder = cleanPath(folderEdit.text.toString())
        if (uris.isEmpty()) return
        if (folder.count { it == '/' } + 1 > MAX_DEPTH) {
            Toast.makeText(this, "Folder maksimal $MAX_DEPTH tingkat", Toast.LENGTH_SHORT).show()
            return
        }
        if (folder.isEmpty()) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Folder tujuan belum diisi")
                .setMessage("Kirim file ini ke \"Tanpa folder\" (langsung di folder penyimpanan PC), atau isi nama folder dulu?")
                .setPositiveButton("Kirim tanpa folder") { _, _ -> queueAndFinish("") }
                .setNegativeButton("Isi folder dulu") { _, _ -> folderEdit.requestFocus() }
                .show()
            return
        }
        queueAndFinish(folder)
    }

    private fun queueAndFinish(folder: String) {
        sendBtn.isEnabled = false
        info2.text = "Menyalin file ke antrean..."
        val list = uris
        val app = applicationContext
        val shrink = shrinkSwitch.isChecked
        val label = if (folder.isEmpty()) "Tanpa folder" else pathLabel(folder)
        Thread {
            var n = 0
            for (u in list) if (Queue.add(app, u, folder, shrink) != null) n++
            if (folder.isNotEmpty()) Prefs.lastFolder = folder
            runOnUiThread {
                if (n > 0) {
                    UploadService.start(app)
                    Toast.makeText(this, "$n file masuk antrean, dikirim ke $label", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "File tidak bisa dibaca", Toast.LENGTH_LONG).show()
                }
                finish()
            }
        }.start()
    }
}
