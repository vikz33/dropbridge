package com.dropbridge.app

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.view.View
import android.widget.TextView

/** Terima otomatis dari HP host: hubungkan ke hotspot host, isi kode, lalu file yang dibagikan tersimpan sendiri. */
class ReceiveActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var addrEdit: EditText
    private lateinit var codeEdit: EditText
    private lateinit var toggle: Button
    private lateinit var statusView: TextView
    private lateinit var heroDot: View
    private lateinit var heroState: TextView
    private var toggleOn: Boolean? = null

    private val ticker = object : Runnable {
        override fun run() {
            val on = ReceiveService.running
            if (toggleOn != on) {
                toggleOn = on
                toggle.text = if (on) "Berhenti menerima" else "Mulai terima otomatis"
                toggle.background = Ui.ripple(this@ReceiveActivity, if (on) Ui.DANGER else Ui.ACCENT, 16)
            }
            Ui.setDot(heroDot, if (on) Ui.GREEN else Ui.AMBER)
            heroState.text = if (on) "Menerima otomatis" else "Belum aktif"
            statusView.text = if (ReceiveService.status.isEmpty()) "Isi alamat dan kode host, lalu mulai." else ReceiveService.status
            handler.postDelayed(this, 1000)
        }
    }

    /** Saat tersambung ke hotspot HP host, alamat host = gateway Wi-Fi. */
    @Suppress("DEPRECATION")
    private fun gatewayIp(): String {
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val gw = wm.dhcpInfo?.gateway ?: 0
            if (gw == 0) return ""
            return "${gw and 0xff}.${(gw shr 8) and 0xff}.${(gw shr 16) and 0xff}.${(gw shr 24) and 0xff}"
        } catch (e: Exception) {
            return ""
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        val c: Context = this

        toggle = Ui.button(c, "Mulai terima otomatis", Ui.Kind.PRIMARY, 56) {
            if (ReceiveService.running) {
                ReceiveService.stop(this@ReceiveActivity)
            } else {
                var a = addrEdit.text.toString().trim().trimEnd('/')
                val code = codeEdit.text.toString().trim()
                if (a.isEmpty() || code.isEmpty()) {
                    ReceiveService.status = "Isi alamat dan kode dulu."
                } else {
                    if (!a.startsWith("http://") && !a.startsWith("https://")) a = "http://$a"
                    if (!Regex(":\\d+$").containsMatchIn(a)) a = "$a:8080"
                    Prefs.hostAddr = a.removePrefix("http://")
                    ReceiveService.start(this@ReceiveActivity, a, code)
                }
            }
        }
        val bar = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        bar.addView(toggle, Ui.lp(c))

        val s = Ui.screen(this, "Terima otomatis", "Ambil file dari HP host tanpa klik unduh", back = true, bottom = bar)
        val col = s.content

        val hero = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(c, Ui.DEEP, 24)
            setPadding(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20))
        }
        val stateRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        heroDot = Ui.dot(c, Ui.AMBER)
        stateRow.addView(heroDot, LinearLayout.LayoutParams(Ui.dp(c, 10), Ui.dp(c, 10)).apply { rightMargin = Ui.dp(c, 8) })
        heroState = Ui.text(c, "Belum aktif", 13f, true, Ui.MINT)
        stateRow.addView(heroState)
        hero.addView(stateRow)
        statusView = Ui.text(c, "", 15f, true, Ui.WHITE)
        hero.addView(statusView, Ui.lp(c, t = 12))
        col.addView(hero, Ui.lp(c))

        col.addView(Ui.text(c, "1) Sambungkan HP ini ke hotspot HP host. 2) Isi kode 6 angka dari layar host. 3) Mulai. Setiap file yang dibagikan host tersimpan sendiri di Download/DropBridge.", 13f, false, Ui.MUTED), Ui.lp(c, t = 12))

        val card = Ui.card(c)
        card.addView(Ui.caption(c, "HP host"))
        card.addView(Ui.text(c, "Alamat", 12f, true, Ui.MUTED), Ui.lp(c, t = 10))
        val gw = gatewayIp()
        addrEdit = Ui.input(c, "Diisi otomatis dari hotspot", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            if (gw.isNotEmpty()) "$gw:8080" else Prefs.hostAddr)
        card.addView(addrEdit, Ui.lp(c, t = 4))
        card.addView(Ui.text(c, "Kode", 12f, true, Ui.MUTED), Ui.lp(c, t = 10))
        codeEdit = Ui.input(c, "Kode dari layar host", InputType.TYPE_CLASS_NUMBER)
        card.addView(codeEdit, Ui.lp(c, t = 4))
        col.addView(card, Ui.lp(c, t = 12))
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
