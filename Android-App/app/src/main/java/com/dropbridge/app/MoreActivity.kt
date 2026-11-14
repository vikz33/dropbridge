package com.dropbridge.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MoreActivity : Activity() {
    private lateinit var stat: TextView
    private lateinit var addrEdit: EditText
    private lateinit var pinEdit: EditText

    private fun switchRow(c: Context, title: String, sub: String, on: Boolean, change: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val t = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        t.addView(Ui.text(c, title, 15f, true))
        t.addView(Ui.text(c, sub, 13f, false, Ui.MUTED))
        row.addView(t, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        row.addView(Switch(c).apply {
            isChecked = on
            contentDescription = title
            setOnCheckedChangeListener { _, v -> change(v) }
        }, Ui.lp(c, Ui.WRAP, Ui.WRAP, l = 12))
        return row
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        val c: Context = this
        val s = Ui.screen(this, "Pengaturan", null, back = true)
        val col = s.content

        // Tampilan: ikut HP, terang, atau gelap
        val look = Ui.card(c)
        look.addView(Ui.text(c, "Tampilan", 15f, true))
        look.addView(Ui.text(c, "Mode gelap lebih nyaman di malam hari dan hemat baterai di layar AMOLED.", 13f, false, Ui.MUTED))
        val seg = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Ui.rounded(c, Ui.FIELD, 14, Ui.LINE)
            setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4))
        }
        for ((key, label) in listOf("system" to "Ikuti HP", "light" to "Terang", "dark" to "Gelap")) {
            val on = Prefs.theme == key
            seg.addView(Ui.text(c, label, 14f, true, if (on) Ui.WHITE else Ui.MUTED).apply {
                gravity = Gravity.CENTER
                minimumHeight = Ui.dp(c, 42)
                isClickable = true
                isFocusable = true
                contentDescription = "Tampilan $label" + if (on) ", dipilih" else ""
                background = if (on) Ui.rounded(c, Ui.FILL, 11) else null
                setOnClickListener { if (!on) { Prefs.theme = key; recreate() } }
            }, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        }
        look.addView(seg, Ui.lp(c, t = 12))
        col.addView(look, Ui.lp(c, b = 12))

        val photo = Ui.card(c)
        photo.addView(switchRow(c, "Kecilkan foto ≤ 512 KB", "Hemat kuota dan lebih cepat. Matikan untuk resolusi penuh, mis. foto liburan.", Prefs.shrinkPhotos) { on ->
            Prefs.shrinkPhotos = on
        })
        col.addView(photo, Ui.lp(c, b = 12))

        val auto = Ui.card(c)
        auto.addView(switchRow(c, "Kirim otomatis", "Antrean terkirim sendiri saat HP tersambung Wi-Fi yang ada PC-nya", Prefs.autoSend) { on ->
            Prefs.autoSend = on
            if (on) AutoSend.schedule(this) else AutoSend.cancel(this)
        })
        auto.addView(switchRow(c, "Boleh pakai data seluler", "Lewat Tailscale saat tidak satu Wi-Fi dengan PC, mis. dari gudang. Pakai kuota.", Prefs.allowMobile) { on ->
            Prefs.allowMobile = on
            AutoSend.cancel(this)
            AutoSend.schedule(this)
        }, Ui.lp(c, t = 8))
        auto.addView(Ui.button(c, "Izinkan jalan di latar belakang", Ui.Kind.OUTLINE, 44) { openBatterySettings() }, Ui.lp(c, t = 12))
        auto.addView(Ui.text(c, "Disarankan di Huawei dan HP lain yang agresif menghemat baterai, supaya pengiriman otomatis tidak dimatikan.", 12f, false, Ui.MUTED), Ui.lp(c, t = 8))
        col.addView(auto, Ui.lp(c))

        val conn = Ui.card(c)
        conn.addView(Ui.caption(c, "Sambungan ke PC"))
        stat = Ui.text(c, "", 13f, true, Ui.DEEP)
        conn.addView(stat, Ui.lp(c, t = 6))
        conn.addView(Ui.text(c, "Buka start.bat di PC, lihat PIN di jendela hitam. Alamat PC dicari otomatis; isi manual hanya jika tidak ketemu.", 13f, false, Ui.MUTED), Ui.lp(c, t = 6))
        conn.addView(Ui.text(c, "PIN", 12f, true, Ui.MUTED), Ui.lp(c, t = 12))
        pinEdit = Ui.input(c, "PIN dari layar PC", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD, Prefs.pin)
        conn.addView(pinEdit, Ui.lp(c, t = 4))
        conn.addView(Ui.text(c, if (Prefs.remoteServer.isNotEmpty())
            "Dari luar jaringan: ${Prefs.remoteServer} (Tailscale). Dipakai otomatis bila Wi-Fi PC tidak terjangkau; nyalakan Tailscale di HP."
        else
            "Dari luar jaringan: belum ada. Pasang Tailscale di PC dan HP (akun sama), lalu sambungkan sekali lewat Wi-Fi.", 12f, false, Ui.MUTED), Ui.lp(c, t = 10))
        conn.addView(Ui.text(c, "Alamat PC (opsional)", 12f, true, Ui.MUTED), Ui.lp(c, t = 10))
        addrEdit = Ui.input(c, "mis. dropbridge.local atau 192.168.1.19", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, Prefs.server)
        conn.addView(addrEdit, Ui.lp(c, t = 4))
        conn.addView(Ui.button(c, "Sambungkan / cari PC", Ui.Kind.PRIMARY, 48) {
            val pin = pinEdit.text.toString().trim()
            val addr = addrEdit.text.toString().trim()
            val newAddr = if (addr.isNotEmpty()) Conn.normalize(addr) else ""
            if (newAddr.isNotEmpty() && newAddr != Prefs.server) {
                // Alamat lain = bisa jadi PC lain: simpan PC lama di daftar, mulai PC baru
                Pcs.saveActive()
                Prefs.pcId = ""; Prefs.pcOs = ""; Prefs.pcName = ""; Prefs.token = ""; Prefs.remoteServer = ""
                Prefs.server = newAddr
            }
            if (pin.isNotEmpty() && pin != Prefs.pin) { Prefs.pin = pin; Prefs.token = "" }
            Conn.connect(this, rescan = addr.isEmpty()) { addrEdit.setText(it) }
        }, Ui.lp(c, t = 14))
        col.addView(conn, Ui.lp(c, t = 12))

        // Versi, supaya tidak tertukar APK lama dan baru
        val pv = Prefs.pcVersion
        col.addView(Ui.text(c, "DropBridge Android versi ${Ui.appVersion(c)}" + if (pv.isNotEmpty()) " · PC versi $pv" else "", 12f, false, Ui.MUTED).apply {
            gravity = Gravity.CENTER
        }, Ui.lp(c, t = 16))

    }

    override fun onResume() {
        super.onResume()
        stat.text = Status.line.ifEmpty { "Belum tersambung" }
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
