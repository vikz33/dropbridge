package com.dropbridge.app

import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Peta "tata surya" di tab Ekosistem (aplikasi). Matahari = HP ini; planet = laptop.
 * Orbit dalam = laptop di Wi-Fi ini sekarang (tujuan kirim ditandai), tengah = pernah tersambung tapi tidak terlihat,
 * luar = laptop baru di Wi-Fi ini (perlu PIN), putus-putus = tujuan dipakai lewat internet (Tailscale).
 * Berputar pelan; berhenti saat layar tidak terlihat, mode hemat baterai, atau animasi dimatikan di HP.
 */
class OrbitMap(private val act: Activity, private val onChanged: () -> Unit) {

    class Planet(val key: String, val name: String, val os: String, val sub: String, val ring: Int,
                 val dim: Boolean, val target: Boolean, val saved: PcEntry?, val found: Net.Found?, val guest: Guest? = null)

    private val c: Context = act
    private var found: List<Net.Found> = emptyList()
    private var searching = false
    private var lastScan = 0L
    private var sig = ""
    var listMode = Prefs.hubList
        private set

    val root: LinearLayout = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
    private val sum = Ui.text(c, "", 13f, false, Ui.MUTED)
    private val space = Space(c)
    private val list = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
    private val segMap: TextView
    private val segList: TextView

    init {
        val bar = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bar.addView(sum, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        val seg = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Ui.rounded(c, Ui.FIELD, 12)
            setPadding(Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3))
        }
        fun segBtn(t: String, list: Boolean) = Ui.text(c, t, 13f, true).apply {
            gravity = Gravity.CENTER
            minimumHeight = Ui.dp(c, 36)
            setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 14), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { listMode = list; Prefs.hubList = list; sig = ""; render() }
        }
        segMap = segBtn("Peta", false)
        segList = segBtn("Daftar", true)
        seg.addView(segMap)
        seg.addView(segList)
        bar.addView(seg)
        root.addView(bar)
        root.addView(space, LinearLayout.LayoutParams(Ui.MATCH, (c.resources.displayMetrics.widthPixels * 0.98f).toInt()).apply { topMargin = Ui.dp(c, 10) })
        root.addView(list, Ui.lp(c, t = 10))
        val acts = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        acts.addView(Ui.iconButton(c, Icon.REFRESH, "Cari ulang", Ui.Kind.OUTLINE, 48, 15f) { Beat.kick(); scan(true) }, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        root.addView(acts, Ui.lp(c, t = 10))
    }

    fun start() { scan(false); space.motion(true) }
    fun stop() { space.motion(false) }

    /** Dipanggil tiap detik oleh layar; cari ulang laptop di Wi-Fi tiap 20 detik. */
    fun tick() {
        if (System.currentTimeMillis() - lastScan > 20000) scan(false)
        render()
    }

    private fun scan(user: Boolean) {
        if (searching) return
        searching = true
        lastScan = System.currentTimeMillis()
        if (user) render()
        Thread {
            val r = try { Net.discoverAll(2) } catch (e: Exception) { emptyList() }
            act.runOnUiThread { found = r; searching = false; lastScan = System.currentTimeMillis(); sig = ""; render() }
        }.start()
    }

    private fun planets(): List<Planet> {
        val out = ArrayList<Planet>()
        val ok = Beat.alive()
        val active = Pcs.active()
        val saved = Pcs.all().let { all -> if (active.server.isNotEmpty() && all.none { Pcs.isActive(it) }) listOf(active) + all else all }
        for (e in saved) {
            val f = found.firstOrNull { (e.id.isNotEmpty() && it.id == e.id) || it.base == e.server }
            val isTarget = Pcs.isActive(e)
            val here = f != null || (isTarget && ok && !Net.viaRemote)
            val viaNet = isTarget && ok && Net.viaRemote
            val ring = when { viaNet -> 3; here -> 0; else -> 1 }
            val sub = when {
                isTarget && viaNet -> "Tujuan · lewat internet"
                isTarget && here -> "Tujuan · Wi-Fi ini"
                isTarget -> "Tujuan · tidak terjangkau"
                here -> "Wi-Fi ini"
                e.remote.isNotEmpty() -> "Bisa lewat Tailscale"
                else -> "Mati / jaringan lain"
            }
            out.add(Planet("s" + (e.id.ifEmpty { e.server }), e.label, e.os, sub, ring, !here && !viaNet, isTarget, e, f))
        }
        val known = saved.mapNotNull { it.id.ifEmpty { null } }.toSet()
        for (f in found) {
            if ((f.id.isNotEmpty() && f.id in known) || saved.any { it.server == f.base }) continue
            out.add(Planet("f" + f.id.ifEmpty { f.base }, f.host.ifEmpty { f.base.removePrefix("http://") }, f.os, "Baru · perlu PIN", 2, false, false, null, f))
        }
        // Tamu lewat browser (saat berbagi menyala): aktif di orbit dalam, yang baru pergi meredup di tengah
        val fmt = java.text.SimpleDateFormat("HH.mm", java.util.Locale.getDefault())
        Host.server?.guests()?.forEachIndexed { i, g ->
            val sub = if (g.active) "Tamu · aktif" else "Tamu · terakhir " + fmt.format(java.util.Date(g.last))
            out.add(Planet("g$i${g.name}", g.name, "", sub, if (g.active) 0 else 1, !g.active, false, null, null, g))
        }
        return out
    }

    fun render() {
        val ps = planets()
        val here = ps.count { it.guest == null && (it.ring == 0 || it.ring == 3) }
        val fresh = ps.count { it.ring == 2 }
        val guests = ps.count { it.guest != null }
        sum.text = when {
            searching && ps.isEmpty() -> "Mencari laptop di Wi-Fi ini…"
            ps.isEmpty() -> "Belum ada laptop. Ketuk Cari ulang."
            else -> listOfNotNull(if (here > 0) "$here laptop terjangkau" else "Tidak ada laptop terjangkau",
                if (fresh > 0) "$fresh baru" else null, if (guests > 0) "$guests HP tamu" else null, if (searching) "mencari…" else null).joinToString(" · ")
        }
        segMap.background = if (!listMode) Ui.rounded(c, Ui.CARD, 9, Ui.LINE) else null
        segList.background = if (listMode) Ui.rounded(c, Ui.CARD, 9, Ui.LINE) else null
        segMap.setTextColor(if (!listMode) Ui.INK else Ui.MUTED)
        segList.setTextColor(if (listMode) Ui.INK else Ui.MUTED)
        space.visibility = if (listMode) View.GONE else View.VISIBLE
        list.visibility = if (listMode) View.VISIBLE else View.GONE
        val s = ps.joinToString("|") { "${it.key},${it.ring},${it.sub},${it.target},${it.os}" } + listMode + Ui.dark
        if (s == sig) return
        sig = s
        if (listMode) renderList(ps) else space.set(ps)
    }

    private fun renderList(ps: List<Planet>) {
        list.removeAllViews()
        val rows = ps.sortedBy { it.ring }.map { p ->
            val r = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = Ui.dp(c, 64)
                setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8))
                isClickable = true
                isFocusable = true
                background = Ui.ripple(c, if (p.target) Ui.SOFT else Ui.CARD, 0)
                contentDescription = "${p.name}, ${p.sub}"
                setOnClickListener { detail(p) }
            }
            r.addView(ball(p, 44), LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)).apply { rightMargin = Ui.dp(c, 12) })
            val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            col.addView(Ui.text(c, p.name + if (p.os.isNotEmpty()) " · ${p.os}" else "", 15f, true, if (p.dim) Ui.MUTED else Ui.INK))
            col.addView(Ui.text(c, p.sub, 12f, false, Ui.MUTED))
            r.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            r.addView(IconView(c, Icon.CHEVRON, Ui.MUTED, 18, 2.2f))
            r as View
        }
        if (rows.isEmpty()) list.addView(Ui.text(c, if (searching) "Mencari laptop…" else "Belum ada laptop. Pastikan DropBridge jalan di laptop dan HP satu Wi-Fi.", 14f, false, Ui.MUTED).apply { gravity = Gravity.CENTER; setPadding(0, Ui.dp(c, 16), 0, Ui.dp(c, 16)) })
        else list.addView(Ui.group(c, rows))
    }

    /** Bulatan planet: laptop putih + lencana W/L, redup bila tidak terlihat, cincin teal bila tujuan. */
    private fun ball(p: Planet, sizeDp: Int): View {
        val f = FrameLayout(c)
        if (p.guest != null) {
            f.addView(FrameLayout(c).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#6366F1")) }
                alpha = if (p.dim) 0.5f else 1f
                addView(IconView(c, Icon.PHONE, Color.WHITE, (sizeDp * 0.44f).toInt(), 2f),
                    FrameLayout.LayoutParams(Ui.dp(c, (sizeDp * 0.44f).toInt()), Ui.dp(c, (sizeDp * 0.44f).toInt()), Gravity.CENTER))
            }, FrameLayout.LayoutParams(Ui.dp(c, sizeDp), Ui.dp(c, sizeDp), Gravity.CENTER))
            return f
        }
        val core = FrameLayout(c).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(if (p.ring == 2) "#FDE68A" else "#E2E8F0"))
                if (p.target) setStroke(Ui.dp(c, 3), Color.parseColor("#5EEAD4"))
            }
            alpha = if (p.dim) 0.5f else 1f
            addView(IconView(c, Icon.LAPTOP, Color.parseColor("#0F172A"), (sizeDp * 0.46f).toInt(), 2f),
                FrameLayout.LayoutParams(Ui.dp(c, (sizeDp * 0.46f).toInt()), Ui.dp(c, (sizeDp * 0.46f).toInt()), Gravity.CENTER))
        }
        f.addView(core, FrameLayout.LayoutParams(Ui.dp(c, sizeDp), Ui.dp(c, sizeDp), Gravity.CENTER))
        val badge = when {
            p.ring == 3 -> "↗" to "#4F46E5"
            p.os.contains("Windows", true) -> "W" to "#1D4ED8"
            p.os.contains("Linux", true) || p.os.contains("Mint", true) -> "L" to "#B45309"
            else -> null
        }
        if (badge != null) f.addView(Ui.text(c, badge.first, 10f, true, Color.WHITE).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = Ui.dp(c, 10).toFloat(); setColor(Color.parseColor(badge.second)); setStroke(Ui.dp(c, 2), Color.parseColor("#0B2724")) }
            minWidth = Ui.dp(c, 20)
        }, FrameLayout.LayoutParams(Ui.WRAP, Ui.dp(c, 20), Gravity.BOTTOM or Gravity.END))
        return f
    }

    /** Lembar detail planet: info, Jadikan tujuan kirim, Buka File, Lupakan PC. */
    private fun detail(p: Planet) {
        p.guest?.let { g ->
            val (d, box) = Ui.sheet(act, "HP tamu")
            val head = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            head.addView(ball(p, 52), LinearLayout.LayoutParams(Ui.dp(c, 58), Ui.dp(c, 58)).apply { rightMargin = Ui.dp(c, 12) })
            val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            col.addView(Ui.text(c, g.name, 18f, true))
            col.addView(Ui.text(c, p.sub.removePrefix("Tamu · ").replaceFirstChar { it.uppercase() } +
                (if (g.sent > 0) " · kirim ${g.sent} file" else "") + (if (g.got > 0) " · ambil ${g.got} file" else ""), 13f, false, Ui.MUTED))
            head.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            box.addView(head, Ui.lp(c, t = 4))
            box.addView(Ui.text(c, "Masuk lewat browser dengan kode. Tidak ada data yang disimpan: hilang dari daftar 2 menit setelah halamannya ditutup. " +
                "Untuk mengirim ke HP ini, bagikan file lewat tab Kirim.", 14f, false, Ui.INK), Ui.lp(c, t = 12))
            box.addView(Ui.button(c, "Bagikan file ke HP ini", Ui.Kind.INDIGO, 52) {
                Prefs.sendTo = "qr"; d.dismiss(); Ui.goTab(act, Ui.TAB_SEND)
            }, Ui.lp(c, t = 14))
            d.show()
            return
        }
        val (d, box) = Ui.sheet(act, "Laptop")
        val head = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(ball(p, 52), LinearLayout.LayoutParams(Ui.dp(c, 58), Ui.dp(c, 58)).apply { rightMargin = Ui.dp(c, 12) })
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.text(c, p.name, 18f, true))
        val addr = (p.found?.base ?: p.saved?.server ?: "").removePrefix("http://")
        val ver = p.found?.version?.ifEmpty { null } ?: p.saved?.version?.ifEmpty { null }
        col.addView(Ui.text(c, listOfNotNull(p.os.ifEmpty { null }, addr.ifEmpty { null }, ver?.let { "PC $it" }).joinToString(" · "), 13f, false, Ui.MUTED))
        head.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        box.addView(head, Ui.lp(c, t = 4))
        box.addView(Ui.text(c, "Status: " + p.sub + if (p.target) " · hak HP ini ${if (Prefs.role == "send") "Hanya kirim" else "Penuh"}" else "", 14f, false, Ui.INK), Ui.lp(c, t = 12))
        // Kirim ke sini: jadikan tujuan, lalu buka tab Kirim
        box.addView(Ui.button(c, "Kirim ke sini", Ui.Kind.PRIMARY, 52) {
            Prefs.sendTo = "pc"
            if (p.target) {
                d.dismiss(); Ui.goTab(act, Ui.TAB_SEND)
            } else if (p.saved != null) {
                Pcs.switchTo(if (p.found != null) p.saved.copy(server = p.found.base) else p.saved)
                d.dismiss(); Conn.connect(act); Ui.goTab(act, Ui.TAB_SEND)
            } else if (p.found != null) {
                d.dismiss()
                PcPicker.askPin(act, p.found) { Conn.connect(act); Ui.goTab(act, Ui.TAB_SEND) }
            }
        }, Ui.lp(c, t = 14))
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        if (p.target && Prefs.role != "send") row.addView(Ui.button(c, "Buka File di PC ini", Ui.Kind.OUTLINE, 48) { d.dismiss(); Ui.goTab(act, Ui.TAB_FILES) }, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        if (p.saved != null && !p.target) {
            if (row.childCount > 0) row.addView(View(c), LinearLayout.LayoutParams(Ui.dp(c, 10), 1))
            row.addView(Ui.button(c, "Lupakan PC", Ui.Kind.OUTLINE, 48) {
                AlertDialog.Builder(act).setTitle("Lupakan ${p.name}?")
                    .setMessage("PIN dan alamatnya dihapus dari HP ini. Untuk memakai lagi, masukkan PIN-nya.")
                    .setPositiveButton("Lupakan") { _, _ -> Pcs.forget(p.saved); d.dismiss(); sig = ""; render() }
                    .setNegativeButton("Batal", null).show()
            }.apply { setTextColor(Ui.DANGER) }, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        }
        if (row.childCount > 0) box.addView(row, Ui.lp(c, t = 10))
        d.show()
    }

    // ---------- Gambar ruang angkasa + planet yang berputar ----------
    private inner class Space(ctx: Context) : FrameLayout(ctx) {
        private val ringT = floatArrayOf(70f, 110f, 160f, 200f)
        private val orbit = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = Ui.dp(ctx, 1).toFloat() + 0.5f; color = Color.argb(56, 158, 232, 219) }
        private val dash = Paint(orbit).apply { color = Color.argb(128, 165, 180, 252); pathEffect = DashPathEffect(floatArrayOf(Ui.dp(ctx, 6).toFloat(), Ui.dp(ctx, 5).toFloat()), 0f) }
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        private val star = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(110, 255, 255, 255) }
        private var items: List<Pair<Planet, View>> = emptyList()
        private val angle0 = HashMap<String, Float>()
        private var t0 = System.currentTimeMillis()
        private var anim: ValueAnimator? = null
        private val sun: View

        init {
            setWillNotDraw(false)
            clipToOutline = true
            background = GradientDrawable().apply { cornerRadius = Ui.dp(ctx, 24).toFloat(); setColor(Color.parseColor("#071A18")) }
            val s = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
            s.addView(FrameLayout(ctx).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#5EEAD4"), Color.parseColor("#0F766E"))).apply { shape = GradientDrawable.OVAL }
                addView(IconView(ctx, Icon.PHONE, Color.WHITE, 30, 2.2f), LayoutParams(Ui.dp(ctx, 30), Ui.dp(ctx, 30), Gravity.CENTER))
            }, LinearLayout.LayoutParams(Ui.dp(ctx, 64), Ui.dp(ctx, 64)))
            s.addView(Ui.text(ctx, "HP ini", 12f, true, Color.parseColor("#E6F4F1")).apply { gravity = Gravity.CENTER; setShadowLayer(2f, 0f, 1f, Color.BLACK) })
            sun = s
            addView(sun, LayoutParams(Ui.WRAP, Ui.WRAP))
        }

        private fun radius(i: Int): Float {
            val half = minOf(width, height) / 2f
            val inner = Ui.dp(context, 32 + 40).toFloat()
            val outer = half - Ui.dp(context, 34)
            return inner + (outer - inner) * floatArrayOf(0f, 0.38f, 0.72f, 1f)[i]
        }

        fun set(ps: List<Planet>) {
            for ((_, v) in items) removeView(v)
            val byRing = ps.groupBy { it.ring }
            items = ps.map { p ->
                val idx = byRing[p.ring]!!.sortedBy { it.key }.indexOf(p)
                angle0.getOrPut(p.key) { 360f / byRing[p.ring]!!.size * idx + p.ring * 47f + 20f }
                val v = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    isClickable = true
                    isFocusable = true
                    contentDescription = "${p.name}, ${p.sub}"
                    setOnClickListener { detail(p) }
                }
                v.addView(ball(p, if (p.ring == 0) 52 else 46), LinearLayout.LayoutParams(Ui.dp(context, 58), Ui.dp(context, 54)))
                v.addView(Ui.text(context, p.name, 11f, true, Color.parseColor("#E6F4F1")).apply {
                    gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; maxWidth = Ui.dp(context, 100)
                    setShadowLayer(2f, 0f, 1f, Color.BLACK); alpha = if (p.dim) 0.6f else 1f
                })
                v.addView(Ui.text(context, p.sub, 10f, false, Color.parseColor("#9DB8B2")).apply {
                    gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; maxWidth = Ui.dp(context, 100)
                })
                addView(v, LayoutParams(Ui.WRAP, Ui.WRAP))
                p to v
            }
            requestLayout()
            invalidate()
            place()
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            super.onLayout(changed, l, t, r, b)
            bg.shader = RadialGradient(width / 2f, height / 2f, maxOf(width, height) * 0.6f,
                intArrayOf(Color.parseColor("#12403B"), Color.parseColor("#0B2724"), Color.parseColor("#071A18")), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            sun.translationX = (width - sun.width) / 2f
            sun.translationY = (height - sun.height) / 2f + Ui.dp(context, 8)
            place()
        }

        /** Letakkan planet menurut waktu: sudut = sudut awal + 360° × waktu / periode orbit. */
        private fun place() {
            if (width == 0) return
            val now = System.currentTimeMillis()
            val moving = anim?.isRunning == true
            for ((p, v) in items) {
                val sec = (if (moving) now - t0 else pausedAt) / 1000f
                val a = Math.toRadians((angle0[p.key]!! + 360f * sec / ringT[p.ring]).toDouble())
                val r = radius(p.ring)
                val bx = width / 2f + (r * Math.cos(a)).toFloat()
                val by = height / 2f + (r * Math.sin(a)).toFloat()
                // pusat bulatan (bukan labelnya) tepat di orbit
                v.translationX = bx - v.width / 2f
                v.translationY = by - Ui.dp(context, 27)
            }
        }

        override fun onDraw(cv: Canvas) {
            cv.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)
            val w = width.toFloat(); val h = height.toFloat()
            for ((x, y) in listOf(0.12f to 0.18f, 0.78f to 0.12f, 0.88f to 0.72f, 0.22f to 0.82f, 0.52f to 0.06f, 0.06f to 0.55f, 0.93f to 0.35f)) cv.drawCircle(w * x, h * y, 1.6f, star)
            val used = items.map { it.first.ring }.toMutableSet().apply { add(0) }
            for (i in 0..3) if (i in used) cv.drawCircle(w / 2, h / 2, radius(i), if (i == 3) dash else orbit)
        }

        /** Putaran pelan; mati bila animasi HP dimatikan atau mode hemat baterai. */
        fun motion(on: Boolean) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val allowed = !pm.isPowerSaveMode && (android.os.Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled())
            if (on && allowed) {
                if (anim?.isRunning == true) return
                t0 = System.currentTimeMillis() - pausedAt
                anim = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 10000
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    addUpdateListener { place() }
                    start()
                }
            } else {
                if (anim?.isRunning == true) pausedAt = System.currentTimeMillis() - t0
                anim?.cancel()
                anim = null
            }
        }
        private var pausedAt = 0L

        override fun onDetachedFromWindow() { motion(false); super.onDetachedFromWindow() }
    }
}
