package com.dropbridge.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Pengaturan (ikon di kanan atas). Urutan sama dengan web dan laptop:
 * Perangkat ini, Pengiriman, Tentang, Keluar. Kecilkan foto ada di Home (ikon Foto kecil);
 * sambungan ke laptop (Ganti, PIN, alamat manual) ada di tab Hubungkan.
 */
class MoreActivity : Activity() {
    private var builtDark = false
    private lateinit var nameVal: TextView
    private lateinit var bgVal: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        val c: Context = this
        val s = Ui.screen(this, "Pengaturan", Net.deviceName(), back = true)
        builtDark = Ui.dark
        val col = s.content

        fun section(title: String, body: View, top: Int = 16) {
            col.addView(Ui.caption(c, title).apply { setPadding(Ui.dp(c, 6), 0, 0, Ui.dp(c, 6)) }, Ui.lp(c, t = top))
            col.addView(body, Ui.lp(c))
        }

        // ---------- Perangkat ini ----------
        nameVal = Ui.text(c, Net.deviceName(), 14f, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; maxWidth = Ui.dp(c, 160) }
        val nameRight = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        nameRight.addView(nameVal)
        nameRight.addView(IconView(c, Icon.CHEVRON, Ui.MUTED, 18, 2.2f), Ui.lp(c, Ui.WRAP, Ui.WRAP, l = 6))
        val nameRow = Ui.settingRow(c, "Nama perangkat", "Terlihat di laptop saat minta izin", nameRight) { askName() }

        val lookRow = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 14))
        }
        lookRow.addView(Ui.text(c, "Tampilan", 15f, true))
        val seg = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Ui.rounded(c, Ui.FIELD, 12)
            setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4))
        }
        for ((key, label) in listOf("system" to "Ikuti HP", "light" to "Terang", "dark" to "Gelap")) {
            val on = Prefs.theme == key
            seg.addView(Ui.text(c, label, 13f, true, if (on) Ui.INK else Ui.MUTED).apply {
                gravity = Gravity.CENTER
                minimumHeight = Ui.dp(c, 38)
                isClickable = true
                isFocusable = true
                contentDescription = "Tampilan $label" + if (on) ", dipilih" else ""
                background = if (on) Ui.rounded(c, Ui.CARD, 9, Ui.LINE) else null
                setOnClickListener { if (!on) { Prefs.theme = key; recreate() } }
            }, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        }
        lookRow.addView(seg, Ui.lp(c, t = 10))
        section("Perangkat ini", Ui.group(c, listOf(nameRow, lookRow)), top = 4)

        // ---------- Pengiriman ----------
        val autoRow = Ui.settingRow(c, "Kirim otomatis", "Antrean dikirim sendiri saat laptop kembali terjangkau",
            Ui.switch(c, Prefs.autoSend, "Kirim otomatis") { on ->
                Prefs.autoSend = on
                if (on) AutoSend.schedule(this) else AutoSend.cancel(this)
            })
        val mobileRow = Ui.settingRow(c, "Boleh pakai data seluler", "Mati: hanya kirim lewat Wi-Fi",
            Ui.switch(c, Prefs.allowMobile, "Boleh pakai data seluler") { on ->
                Prefs.allowMobile = on
                AutoSend.cancel(this)
                AutoSend.schedule(this)
            })
        bgVal = Ui.text(c, "", 13f, true, Ui.OK)
        val bgRow = Ui.settingRow(c, "Jalan di latar belakang", null, IconView(c, Icon.CHEVRON, Ui.MUTED, 18, 2.2f)) { openBatterySettings() }
        ((bgRow.getChildAt(0) as LinearLayout)).addView(bgVal)
        val recvRow = Ui.settingRow(c, "Terima file dari laptop", "File yang dikirim laptop langsung tersimpan di Download/DropBridge",
            Ui.switch(c, Prefs.receiveFromPc, "Terima file dari laptop") { on -> Prefs.receiveFromPc = on; Inbox.schedule(this) })
        section("Pengiriman", Ui.group(c, listOf(autoRow, mobileRow, recvRow, bgRow)))

        // ---------- Tentang: versi tiap bagian ----------
        val appV = Ui.appVersion(c)
        val pcV = Prefs.pcVersion
        fun verRow(icon: Int, title: String, sub: String, ver: String): View {
            val r = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = Ui.dp(c, 64)
                setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10))
            }
            r.addView(FrameLayout(c).apply {
                background = Ui.rounded(c, Ui.SOFT, 12)
                addView(IconView(c, icon, Ui.ACCENT, 20, 2f), FrameLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20), Gravity.CENTER))
            }, LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40)).apply { rightMargin = Ui.dp(c, 12) })
            val t = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            t.addView(Ui.text(c, title, 15f, true))
            t.addView(Ui.text(c, sub, 13f, false, Ui.MUTED))
            r.addView(t, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            r.addView(Ui.mono(Ui.text(c, ver.ifEmpty { "?" }, 15f, false, Ui.INK)))
            return r
        }
        val pcName = Prefs.pcName.ifEmpty { "belum tersambung" }
        val rows = arrayListOf(
            verRow(Icon.PHONE, "Aplikasi HP", "DropBridge Android", appV),
            verRow(Icon.LAPTOP, "Laptop · $pcName", Prefs.pcOs.ifEmpty { "Versi diketahui setelah tersambung" }, pcV),
        )
        if (pcV.isNotEmpty()) {
            val bad = Ui.versionMismatch(appV, pcV)
            val pill = Ui.text(c, if (bad) "Versi beda. Perbarui yang lebih lama." else "✓  Versi cocok", 12f, true, if (bad) Ui.WARN_TX else Ui.DEEP).apply {
                background = Ui.rounded(c, if (bad) Ui.WARN_BG else Ui.SOFT, 999)
                setPadding(Ui.dp(c, 12), Ui.dp(c, 5), Ui.dp(c, 12), Ui.dp(c, 5))
            }
            rows.add(LinearLayout(c).apply {
                setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 12))
                addView(pill)
            })
        }
        section("Tentang", Ui.group(c, rows))

        // ---------- Keluar ----------
        if (Prefs.pin.isNotEmpty() || Prefs.token.isNotEmpty()) {
            col.addView(Ui.button(c, "Keluar dari ${Prefs.pcName.ifEmpty { "laptop" }}", Ui.Kind.OUTLINE, 52) { askLogout() }.apply {
                setTextColor(Ui.DANGER)
                background = Ui.ripple(c, Ui.CARD, 12, Tokens.DANGER_SOFT)
            }, Ui.lp(c, t = 20))
        }
    }

    private fun askName() {
        val c: Context = this
        val box = Ui.input(c, "Mis. HP Budi", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS, Net.deviceName())
        val wrap = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(c, 20), Ui.dp(c, 4), Ui.dp(c, 20), 0)
            addView(box, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        }
        AlertDialog.Builder(this).setTitle("Nama perangkat").setView(wrap)
            .setPositiveButton("Simpan") { _, _ ->
                val name = box.text.toString().trim().take(40)
                Prefs.devName = name
                nameVal.text = Net.deviceName()
                Thread {
                    try {
                        val base = Net.ensureServer() ?: return@Thread
                        Net.rename(base, Net.ensureToken(base), Net.deviceName())
                    } catch (e: Exception) { /* nama tetap tersimpan di HP; dikirim saat login berikutnya */ }
                }.start()
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun askLogout() {
        val pc = Prefs.pcName.ifEmpty { "laptop" }
        AlertDialog.Builder(this)
            .setTitle("Keluar dari $pc?")
            .setMessage("Untuk mengirim lagi, perlu PIN dari laptop (dan izin bila diminta). Antrean di HP tetap tersimpan.")
            .setPositiveButton("Keluar") { _, _ ->
                val base = Net.current.ifEmpty { Prefs.server }
                val token = Prefs.token
                Thread { if (base.isNotEmpty() && token.isNotEmpty()) Net.logout(base, token) }.start()
                Pcs.forgetLogin()  // PIN juga dilupakan di daftar PC, jadi masuk lagi perlu PIN
                Prefs.token = ""
                Prefs.pin = ""
                Status.line = "Keluar dari $pc. Ketuk Ganti untuk menyambungkan lagi."
                Toast.makeText(this, "Keluar dari $pc", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("Batal", null).show()
    }

    override fun onResume() {
        super.onResume()
        if (Ui.themeStale(this, builtDark)) { recreate(); return }
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val ok = android.os.Build.VERSION.SDK_INT < 23 || pm.isIgnoringBatteryOptimizations(packageName)
        bgVal.text = if (ok) "Sudah diizinkan" else "Belum diizinkan · disarankan agar kirim otomatis tidak dimatikan"
        bgVal.setTextColor(if (ok) Ui.OK else Ui.WARN_TX)
    }

    private fun openBatterySettings() {
        try {
            startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")))
            } catch (e2: Exception) {
                Toast.makeText(this, "Buka Pengaturan > Baterai > DropBridge, lalu izinkan jalan di latar belakang", Toast.LENGTH_LONG).show()
            }
        }
    }
}
