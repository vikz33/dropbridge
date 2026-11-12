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
    private lateinit var chipsTitle: TextView
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

        col.addView(Ui.caption(c, "Folder tujuan di PC"), Ui.lp(c, t = 18, b = 8))
        folderAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, ArrayList(known))
        folderEdit = AutoCompleteTextView(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setAdapter(folderAdapter)
            threshold = 1
            setText(Prefs.lastFolder)
            setSelection(text.length)
            setDropDownBackgroundDrawable(Ui.rounded(c, Ui.CARD, 12, Ui.LINE))
            setOnFocusChangeListener { _, focused -> if (focused && text.isEmpty()) showDropDown() }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { updateHint() }
            })
        }
        Ui.styleInput(c, folderEdit, "Ketik nama folder, mis. 27 012345")
        folderEdit.textSize = 18f
        folderEdit.minimumHeight = Ui.dp(c, 56)
        folderEdit.background = Ui.rounded(c, Ui.CARD, 16, Ui.ACCENT).also { it.setStroke(Ui.dp(c, 2), Ui.ACCENT) }
        col.addView(folderEdit, Ui.lp(c))

        hint = Ui.text(c, "", 14f, false, Ui.MUTED).apply {
            setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12))
            visibility = View.GONE
        }
        col.addView(hint, Ui.lp(c, t = 10))

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

        chipsTitle = Ui.caption(c, "Folder di PC")
        chipsTitle.visibility = View.GONE
        col.addView(chipsTitle, Ui.lp(c, t = 18, b = 8))
        chipScroll = android.widget.HorizontalScrollView(c).apply {
            isHorizontalScrollBarEnabled = false
            visibility = View.GONE
        }
        chips = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        chipScroll.addView(chips)
        col.addView(chipScroll, Ui.lp(c))

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
        val list = known.take(8)
        val v = if (list.isEmpty()) View.GONE else View.VISIBLE
        chipsTitle.visibility = v
        chipScroll.visibility = v
        for (n in list) {
            chips.addView(Ui.button(c, n, Ui.Kind.OUTLINE, 44) {
                folderEdit.setText(n)
                folderEdit.setSelection(n.length)
                folderEdit.dismissDropDown()
            }.apply { background = Ui.ripple(c, Ui.CARD, 22, Ui.LINE2) }, Ui.lp(c, Ui.WRAP, Ui.WRAP, r = 8))
        }
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

    private fun showHint(msg: String, bg: Int, fg: Int) {
        val c: android.content.Context = this
        hint.text = msg
        hint.setTextColor(fg)
        hint.background = Ui.rounded(c, bg, 14)
        hint.visibility = View.VISIBLE
    }

    private fun updateHint() {
        val name = folderEdit.text.toString().trim()
        if (name.isEmpty()) {
            hint.visibility = View.GONE
            return
        }
        val exact = known.firstOrNull { it.equals(name, ignoreCase = true) }
        if (exact != null) {
            showHint("Folder sudah ada di PC.", Ui.SOFT, Ui.DEEP)
            return
        }
        val similar = if (name.length >= 4) known.filter { withinOneEdit(it, name) }.take(3) else emptyList()
        if (similar.isNotEmpty()) {
            showHint("Mirip dengan: ${similar.joinToString(", ")}. Salah ketik?", Ui.AMBER_BG, Ui.AMBER_TX)
        } else {
            showHint("Folder baru, dibuat otomatis di PC.", Ui.FIELD, Ui.MUTED)
        }
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

        val folder = folderEdit.text.toString().trim()
        if (uris.isEmpty()) return
        if (folder.isEmpty()) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Folder tujuan belum diisi")
                .setMessage("Kirim file ini ke \"Tanpa folder\" (langsung di folder shared di PC), atau isi nama folder dulu?")
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
        val label = if (folder.isEmpty()) "Tanpa folder" else "folder \"$folder\""
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
