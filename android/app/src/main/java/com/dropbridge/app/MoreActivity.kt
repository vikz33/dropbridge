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
        val s = Ui.screen(this, "Lainnya", null, back = false, nav = Ui.TAB_MORE)
        val col = s.content

        val photo = Ui.card(c)
        photo.addView(switchRow(c, "Kecilkan foto otomatis", "Foto di atas 512 KB dikecilkan sebelum dikirim.", Prefs.shrinkPhotos) { Prefs.shrinkPhotos = it })
        col.addView(photo, Ui.lp(c))

        val auto = Ui.card(c)
        auto.addView(switchRow(c, "Kirim otomatis", "Antrean terkirim sendiri saat HP tersambung Wi-Fi yang ada PC-nya", Prefs.autoSend) { on ->
            Prefs.autoSend = on
            if (on) AutoSend.schedule(this) else AutoSend.cancel(this)
        })
        auto.addView(Ui.button(c, "Izinkan jalan di latar belakang", Ui.Kind.OUTLINE, 44) { openBatterySettings() }, Ui.lp(c, t = 12))
        auto.addView(Ui.text(c, "Disarankan di Huawei dan HP lain yang agresif menghemat baterai, supaya pengiriman otomatis tidak dimatikan.", 12f, false, Ui.MUTED), Ui.lp(c, t = 8))
        col.addView(auto, Ui.lp(c, t = 12))

        val conn = Ui.card(c)
        conn.addView(Ui.caption(c, "Sambungan ke PC"))
        stat = Ui.text(c, "", 13f, true, Ui.DEEP)
        conn.addView(stat, Ui.lp(c, t = 6))
        conn.addView(Ui.text(c, "Buka start.bat di PC, lihat PIN di jendela hitam. Alamat PC dicari otomatis; isi manual hanya jika tidak ketemu.", 13f, false, Ui.MUTED), Ui.lp(c, t = 6))
        conn.addView(Ui.text(c, "PIN", 12f, true, Ui.MUTED), Ui.lp(c, t = 12))
        pinEdit = Ui.input(c, "PIN dari layar PC", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD, Prefs.pin)
        conn.addView(pinEdit, Ui.lp(c, t = 4))
        conn.addView(Ui.text(c, "Alamat PC (opsional)", 12f, true, Ui.MUTED), Ui.lp(c, t = 10))
        addrEdit = Ui.input(c, "mis. dropbridge.local atau 192.168.1.19", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, Prefs.server)
        conn.addView(addrEdit, Ui.lp(c, t = 4))
        conn.addView(Ui.button(c, "Sambungkan / cari PC", Ui.Kind.PRIMARY, 48) {
            val pin = pinEdit.text.toString().trim()
            val addr = addrEdit.text.toString().trim()
            if (pin.isNotEmpty()) Prefs.pin = pin
            if (addr.isNotEmpty()) Prefs.server = Conn.normalize(addr)
            Conn.connect(this) { addrEdit.setText(it) }
        }, Ui.lp(c, t = 14))
        col.addView(conn, Ui.lp(c, t = 12))

        val rec = Ui.card(c)
        rec.addView(Ui.text(c, "Terima otomatis dari HP host lain", 15f, true))
        rec.addView(Ui.text(c, "Untuk mengunduh file dari HP lain yang sedang mode host.", 13f, false, Ui.MUTED))
        rec.addView(Ui.button(c, "Buka", Ui.Kind.OUTLINE, 44) { startActivity(Intent(this, ReceiveActivity::class.java)) }, Ui.lp(c, t = 12))
        col.addView(rec, Ui.lp(c, t = 12))
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
