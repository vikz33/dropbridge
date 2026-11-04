package com.dropbridge.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
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
 * Mode host: HP ini jadi "server" kecil. Nyalakan hotspot HP (atau pakai Wi-Fi yang sama),
 * lalu perangkat lain scan QR atau ketik alamat + kode di browser.
 */
class HostActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var toggle: Button
    private lateinit var heroDot: View
    private lateinit var heroState: TextView
    private lateinit var infoView: TextView
    private lateinit var codeView: TextView
    private lateinit var addrBox: LinearLayout
    private lateinit var qr: QrView
    private lateinit var qrHint: TextView
    private lateinit var statView: TextView
    private lateinit var shareBtn: View
    private var chosen = 0
    private var shownKey = ""
    private var toggleOn: Boolean? = null

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        val c: Context = this

        toggle = Ui.button(c, "Mulai host", Ui.Kind.PRIMARY, 56) {
            if (Host.server == null) HostService.start(this@HostActivity) else HostService.stop(this@HostActivity)
        }
        val bar = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        bar.addView(Ui.button(c, "Pengaturan hotspot", Ui.Kind.OUTLINE, 56) {
            try { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
            catch (e: Exception) { Toast.makeText(this@HostActivity, "Buka pengaturan hotspot dari menu Pengaturan HP", Toast.LENGTH_LONG).show() }
        }, Ui.lp(c, 0, Ui.WRAP, r = 6, weight = 1f))
        bar.addView(toggle, Ui.lp(c, 0, Ui.WRAP, l = 6, weight = 1f))

        val s = Ui.screen(this, "Mode host", "iPhone atau HP lain cukup buka browser", back = false, bottom = bar, nav = Ui.TAB_HOST)
        val col = s.content

        col.addView(Ui.text(c, "1) Nyalakan hotspot di HP ini (atau pakai Wi-Fi yang sama). 2) Mulai host. 3) Di perangkat lain: scan QR, atau buka alamat lalu ketik kode.", 13f, false, Ui.MUTED), Ui.lp(c, b = 12))

        val hero = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            background = Ui.rounded(c, Ui.DEEP, 24)
            setPadding(Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18))
        }
        val stateRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        heroDot = Ui.dot(c, Ui.AMBER)
        stateRow.addView(heroDot, LinearLayout.LayoutParams(Ui.dp(c, 10), Ui.dp(c, 10)).apply { rightMargin = Ui.dp(c, 8) })
        heroState = Ui.text(c, "Host belum aktif", 13f, true, Ui.MINT)
        stateRow.addView(heroState)
        hero.addView(stateRow, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        infoView = Ui.text(c, "", 14f, false, Ui.MINT)
        hero.addView(infoView, Ui.lp(c, t = 10))
        qr = QrView(c)
        hero.addView(qr, Ui.lp(c, Ui.WRAP, Ui.WRAP, t = 12))
        qrHint = Ui.text(c, "Arahkan kamera HP lain ke kode ini", 13f, false, Ui.MINT)
        hero.addView(qrHint, Ui.lp(c, Ui.WRAP, Ui.WRAP, t = 12))
        col.addView(hero, Ui.lp(c))

        val codeCard = Ui.card(c, Ui.WHITE, 14)
        codeCard.addView(Ui.caption(c, "Kode akses"))
        codeView = Ui.text(c, "------", 28f, true, Ui.ACCENT).apply { letterSpacing = 0.12f }
        codeCard.addView(codeView, Ui.lp(c, t = 4))
        col.addView(codeCard, Ui.lp(c, t = 12))

        val addrCard = Ui.card(c, Ui.WHITE, 14)
        addrCard.addView(Ui.caption(c, "Alamat"))
        addrBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        addrCard.addView(addrBox, Ui.lp(c, t = 4))
        col.addView(addrCard, Ui.lp(c, t = 12))

        col.addView(Ui.button(c, "Terima otomatis dari HP host lain", Ui.Kind.OUTLINE, 48) {
            startActivity(Intent(this, ReceiveActivity::class.java))
        }, Ui.lp(c, t = 12))

        val statCard = Ui.card(c, Ui.WHITE, 14)
        statView = Ui.text(c, "", 14f, true)
        statCard.addView(statView)
        shareBtn = Ui.button(c, "Bagikan file dari HP ini", Ui.Kind.OUTLINE, 48) {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(i, 9)
        }
        statCard.addView(shareBtn, Ui.lp(c, t = 12))
        col.addView(statCard, Ui.lp(c, t = 12))
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 9 || resultCode != RESULT_OK || data == null) return
        val srv = Host.server ?: return
        val clip = data.clipData
        if (clip != null) {
            for (k in 0 until clip.itemCount) {
                val u = clip.getItemAt(k).uri
                try { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { /* izin sementara cukup */ }
                srv.addShared(u)
            }
        } else {
            val u = data.data
            if (u != null) {
                try { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { /* izin sementara cukup */ }
                srv.addShared(u)
            }
        }
    }

    private fun setToggle(on: Boolean) {
        if (toggleOn == on) return
        toggleOn = on
        val c: Context = this
        toggle.text = if (on) "Berhenti" else "Mulai host"
        toggle.background = Ui.ripple(c, if (on) Ui.DANGER else Ui.ACCENT, 16)
    }

    private fun addrRow(c: Context, text: String, selected: Boolean, onClick: () -> Unit): View {
        val v = Ui.text(c, text, 15f, selected, if (selected) Ui.INK else Ui.MUTED)
        v.minimumHeight = Ui.dp(c, 44)
        v.gravity = android.view.Gravity.CENTER_VERTICAL
        v.setOnClickListener { onClick() }
        return v
    }

    private fun refresh() {
        val c: Context = this
        val srv = Host.server
        if (srv == null) {
            setToggle(false)
            Ui.setDot(heroDot, Ui.AMBER)
            heroState.text = "Host belum aktif"
            infoView.text = "Ketuk Mulai host untuk membuat kode QR."
            infoView.visibility = View.VISIBLE
            addrBox.removeAllViews()
            codeView.text = "------"
            qr.matrix = null
            qr.visibility = View.GONE
            qrHint.visibility = View.GONE
            shareBtn.visibility = View.GONE
            statView.text = "Belum ada file dibagikan"
            shownKey = ""
            return
        }
        setToggle(true)
        Ui.setDot(heroDot, Ui.GREEN)
        heroState.text = "Host aktif"
        shareBtn.visibility = View.VISIBLE
        val ips = Net.lanIps()
        val key = ips.joinToString(",") + ":" + srv.port + ":" + chosen
        if (key != shownKey) {
            shownKey = key
            addrBox.removeAllViews()
            if (ips.isEmpty()) {
                infoView.text = "Belum ada jaringan. Nyalakan hotspot atau sambungkan Wi-Fi, lalu buka layar ini lagi."
                infoView.visibility = View.VISIBLE
                qr.visibility = View.GONE
                qrHint.visibility = View.GONE
            } else {
                if (chosen >= ips.size) chosen = 0
                infoView.visibility = View.GONE
                codeView.text = srv.code
                for ((i, ip) in ips.withIndex()) {
                    addrBox.addView(addrRow(c, "http://$ip:${srv.port}" + if (i == chosen) "  (QR)" else "  (ketuk untuk QR)", i == chosen) {
                        chosen = i
                        shownKey = ""
                    })
                }
                qr.matrix = QrCode.encode("http://${ips[chosen]}:${srv.port}/?k=${srv.code}")
                qr.visibility = View.VISIBLE
                qrHint.visibility = View.VISIBLE
            }
        }
        statView.text = "Diterima: ${srv.received} file (Download/DropBridge)\nDibagikan: ${srv.shared.size} file\n${srv.lastEvent}"
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
