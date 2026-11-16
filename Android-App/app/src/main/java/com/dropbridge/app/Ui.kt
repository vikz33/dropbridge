package com.dropbridge.app

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Ikon garis sederhana, digambar sendiri (tanpa file gambar). */
object Icon {
    const val UP = 0
    const val DOWN = 1
    const val FOLDER = 2
    const val MAIL = 3
    const val BROADCAST = 4
    const val CHECK = 5
    const val BACK = 6
    const val IMAGE = 7
    const val FILE = 8
    const val BOX = 9
    const val WARN = 10
    const val MENU = 11
    const val LOGO = 12
    const val QR = 13
    const val GEAR = 14
    const val PC = 15
    const val PHONE = 16
    const val WIFI = 17
    const val DOTS = 18
    const val SORT = 19
    const val LIST = 20
    const val GRID = 21
    const val CAMERA = 22
    const val SHARE = 23
    const val PLAY = 24
    const val CLOCK = 25
    const val FOLDER_PLUS = 26
    const val GLOBE = 27
    const val REFRESH = 28
    const val FOLDERS = 29
    const val SHRINK = 30
    const val ADD_PHONE = 31
    const val TEXT = 32
    const val COPY = 33
    const val TRASH = 34
    const val PASTE = 35
    const val LOCK = 36
    const val QUEUE = 37
    const val CLOSE = 38
    const val DRIVE = 39
    const val LAPTOP = 40
    const val CHEVRON = 41
    const val SEND = 42
    const val ORBIT = 43
}

class IconView(ctx: Context, private val kind: Int, private val color: Int, private val sizeDp: Int = 24, private val sw: Float = 1.9f) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onMeasure(w: Int, h: Int) {
        val s = (sizeDp * resources.displayMetrics.density).toInt()
        setMeasuredDimension(s, s)
    }

    private fun poly(c: Canvas, close: Boolean, vararg v: Float) {
        val path = Path()
        path.moveTo(v[0], v[1])
        var i = 2
        while (i < v.size) { path.lineTo(v[i], v[i + 1]); i += 2 }
        if (close) path.close()
        c.drawPath(path, p)
    }

    override fun onDraw(c: Canvas) {
        val k = width / 24f
        c.save()
        c.scale(k, k)
        p.color = color
        p.strokeWidth = sw
        when (kind) {
            Icon.UP -> {
                poly(c, false, 12f, 16f, 12f, 4f)
                poly(c, false, 7f, 9f, 12f, 4f, 17f, 9f)
                poly(c, false, 5f, 20f, 19f, 20f)
            }
            Icon.DOWN -> {
                poly(c, false, 12f, 4f, 12f, 16f)
                poly(c, false, 7f, 11f, 12f, 16f, 17f, 11f)
                poly(c, false, 5f, 20f, 19f, 20f)
            }
            Icon.FOLDER -> poly(c, true, 3f, 6f, 9f, 6f, 11f, 9f, 21f, 9f, 21f, 18f, 3f, 18f)
            Icon.MAIL -> {
                c.drawRoundRect(RectF(3f, 6f, 21f, 18f), 2f, 2f, p)
                poly(c, false, 3f, 8f, 12f, 14f, 21f, 8f)
            }
            Icon.BROADCAST -> {
                c.drawCircle(12f, 12f, 1.6f, p)
                c.drawArc(RectF(7f, 7f, 17f, 17f), 135f, 90f, false, p)
                c.drawArc(RectF(7f, 7f, 17f, 17f), -45f, 90f, false, p)
                c.drawArc(RectF(3f, 3f, 21f, 21f), 135f, 90f, false, p)
                c.drawArc(RectF(3f, 3f, 21f, 21f), -45f, 90f, false, p)
            }
            Icon.CHECK -> poly(c, false, 5f, 12f, 10f, 17f, 19f, 7f)
            Icon.BACK -> poly(c, false, 15f, 5f, 8f, 12f, 15f, 19f)
            Icon.IMAGE -> {
                c.drawRoundRect(RectF(3f, 4f, 21f, 20f), 3f, 3f, p)
                c.drawCircle(9f, 10f, 1.6f, p)
                poly(c, false, 21f, 16f, 16f, 11f, 7f, 20f)
            }
            Icon.FILE -> {
                poly(c, true, 7f, 3f, 14f, 3f, 19f, 8f, 19f, 21f, 7f, 21f)
                poly(c, false, 14f, 3f, 14f, 8f, 19f, 8f)
            }
            Icon.BOX -> {
                poly(c, true, 5f, 8f, 12f, 4f, 19f, 8f, 19f, 16f, 12f, 20f, 5f, 16f)
                poly(c, false, 5f, 8f, 12f, 12f, 19f, 8f)
                poly(c, false, 12f, 12f, 12f, 20f)
            }
            Icon.LOGO -> {
                poly(c, false, 8.25f, 13.5f, 8.25f, 5.25f)
                poly(c, false, 5.6f, 7.9f, 8.25f, 5.25f, 10.9f, 7.9f)
                poly(c, false, 15.75f, 5.25f, 15.75f, 13.5f)
                poly(c, false, 13.1f, 10.9f, 15.75f, 13.5f, 18.4f, 10.9f)
                poly(c, false, 3f, 18.75f, 21f, 18.75f)
            }
            Icon.QR -> {
                poly(c, true, 4f, 4f, 10f, 4f, 10f, 10f, 4f, 10f)
                poly(c, true, 14f, 4f, 20f, 4f, 20f, 10f, 14f, 10f)
                poly(c, true, 4f, 14f, 10f, 14f, 10f, 20f, 4f, 20f)
                poly(c, true, 14f, 14f, 16f, 14f, 16f, 16f, 14f, 16f)
                poly(c, true, 18f, 18f, 20f, 18f, 20f, 20f, 18f, 20f)
            }
            Icon.GEAR -> {
                poly(c, false, 4f, 7f, 14f, 7f)
                poly(c, false, 18f, 7f, 20f, 7f)
                poly(c, false, 4f, 17f, 8f, 17f)
                poly(c, false, 12f, 17f, 20f, 17f)
                c.drawCircle(16f, 7f, 2f, p)
                c.drawCircle(10f, 17f, 2f, p)
            }
            Icon.PC -> {
                c.drawRoundRect(RectF(3f, 4f, 21f, 16f), 2f, 2f, p)
                poly(c, false, 8f, 20f, 16f, 20f)
                poly(c, false, 12f, 16f, 12f, 20f)
            }
            Icon.PHONE -> {
                c.drawRoundRect(RectF(7f, 3f, 17f, 21f), 2f, 2f, p)
                poly(c, false, 11f, 18f, 13f, 18f)
            }
            Icon.WIFI -> {
                c.drawArc(RectF(2f, 5f, 22f, 25f), 225f, 90f, false, p)
                c.drawArc(RectF(6f, 9f, 18f, 21f), 225f, 90f, false, p)
                c.drawCircle(12f, 18f, 1.2f, p)
            }
            Icon.MENU -> {
                poly(c, false, 4f, 7f, 20f, 7f)
                poly(c, false, 4f, 12f, 20f, 12f)
                poly(c, false, 4f, 17f, 20f, 17f)
            }
            Icon.DOTS -> {
                p.style = Paint.Style.FILL
                c.drawCircle(12f, 5f, 2f, p)
                c.drawCircle(12f, 12f, 2f, p)
                c.drawCircle(12f, 19f, 2f, p)
                p.style = Paint.Style.STROKE
            }
            Icon.SORT -> {
                poly(c, false, 7f, 4f, 7f, 20f)
                poly(c, false, 3f, 16f, 7f, 20f, 11f, 16f)
                poly(c, false, 17f, 20f, 17f, 4f)
                poly(c, false, 13f, 8f, 17f, 4f, 21f, 8f)
            }
            Icon.LIST -> {
                poly(c, false, 8f, 6f, 20f, 6f)
                poly(c, false, 8f, 12f, 20f, 12f)
                poly(c, false, 8f, 18f, 20f, 18f)
                poly(c, false, 4f, 6f, 4.1f, 6f)
                poly(c, false, 4f, 12f, 4.1f, 12f)
                poly(c, false, 4f, 18f, 4.1f, 18f)
            }
            Icon.GRID -> {
                c.drawRoundRect(RectF(4f, 4f, 11f, 11f), 1.5f, 1.5f, p)
                c.drawRoundRect(RectF(13f, 4f, 20f, 11f), 1.5f, 1.5f, p)
                c.drawRoundRect(RectF(4f, 13f, 11f, 20f), 1.5f, 1.5f, p)
                c.drawRoundRect(RectF(13f, 13f, 20f, 20f), 1.5f, 1.5f, p)
            }
            Icon.CAMERA -> {
                poly(c, true, 4f, 8f, 7f, 8f, 9f, 5f, 15f, 5f, 17f, 8f, 20f, 8f, 20f, 20f, 4f, 20f)
                c.drawCircle(12f, 13.5f, 3.5f, p)
            }
            Icon.SHARE -> {
                c.drawCircle(18f, 5f, 2.5f, p)
                c.drawCircle(6f, 12f, 2.5f, p)
                c.drawCircle(18f, 19f, 2.5f, p)
                poly(c, false, 8.2f, 10.8f, 15.8f, 6.4f)
                poly(c, false, 8.2f, 13.2f, 15.8f, 17.6f)
            }
            Icon.PLAY -> {
                p.style = Paint.Style.FILL
                val path = Path(); path.moveTo(8f, 5f); path.lineTo(19f, 12f); path.lineTo(8f, 19f); path.close()
                c.drawPath(path, p)
                p.style = Paint.Style.STROKE
            }
            Icon.REFRESH -> {
                // dua panah melingkar
                c.drawArc(RectF(4f, 4f, 20f, 20f), 200f, 130f, false, p)
                c.drawArc(RectF(4f, 4f, 20f, 20f), 20f, 130f, false, p)
                poly(c, false, 4.5f, 5f, 4.6f, 9.2f, 8.8f, 9f)
                poly(c, false, 19.5f, 19f, 19.4f, 14.8f, 15.2f, 15f)
            }
            Icon.FOLDERS -> {
                poly(c, true, 3f, 6f, 9f, 6f, 11f, 8f, 21f, 8f, 21f, 19f, 3f, 19f)
                poly(c, false, 7f, 12.5f, 17f, 12.5f)
                poly(c, false, 7f, 15.5f, 13f, 15.5f)
            }
            Icon.SHRINK -> {
                poly(c, false, 4f, 14f, 10f, 14f, 10f, 20f)
                poly(c, false, 20f, 10f, 14f, 10f, 14f, 4f)
                poly(c, false, 14f, 10f, 21f, 3f)
                poly(c, false, 3f, 21f, 10f, 14f)
            }
            Icon.ADD_PHONE -> {
                c.drawRoundRect(RectF(7f, 3f, 17f, 21f), 2f, 2f, p)
                poly(c, false, 12f, 8f, 12f, 14f)
                poly(c, false, 9f, 11f, 15f, 11f)
            }
            Icon.TEXT -> {
                poly(c, false, 5f, 6f, 19f, 6f)
                poly(c, false, 5f, 12f, 19f, 12f)
                poly(c, false, 5f, 18f, 14f, 18f)
            }
            Icon.COPY -> {
                c.drawRoundRect(RectF(8f, 8f, 20f, 20f), 2f, 2f, p)
                poly(c, false, 16f, 8f, 16f, 4f, 4f, 4f, 4f, 16f, 8f, 16f)
            }
            Icon.TRASH -> {
                poly(c, false, 4f, 7f, 20f, 7f)
                poly(c, false, 6f, 7f, 7f, 20f, 17f, 20f, 18f, 7f)
                poly(c, false, 9f, 7f, 9f, 4f, 15f, 4f, 15f, 7f)
                poly(c, false, 10f, 11f, 10f, 17f)
                poly(c, false, 14f, 11f, 14f, 17f)
            }
            Icon.PASTE -> {
                c.drawRoundRect(RectF(6f, 4f, 18f, 21f), 2f, 2f, p)
                poly(c, true, 9f, 3f, 15f, 3f, 15f, 6f, 9f, 6f)
            }
            Icon.LOCK -> {
                c.drawRoundRect(RectF(5f, 11f, 19f, 20f), 2f, 2f, p)
                c.drawArc(RectF(8f, 4f, 16f, 12f), 180f, 180f, false, p)
                poly(c, false, 8f, 8f, 8f, 11f)
                poly(c, false, 16f, 8f, 16f, 11f)
            }
            Icon.QUEUE -> {
                poly(c, false, 4f, 6f, 20f, 6f)
                poly(c, false, 4f, 12f, 20f, 12f)
                poly(c, false, 4f, 18f, 14f, 18f)
                c.drawCircle(19f, 18f, 2f, p)
            }
            Icon.CLOSE -> {
                poly(c, false, 6f, 6f, 18f, 18f)
                poly(c, false, 18f, 6f, 6f, 18f)
            }
            Icon.DRIVE -> {
                poly(c, true, 3f, 6f, 9f, 6f, 11f, 8f, 21f, 8f, 21f, 19f, 3f, 19f)
                poly(c, false, 12f, 11f, 12f, 16f)
                poly(c, false, 9.5f, 13.5f, 12f, 16f, 14.5f, 13.5f)
            }
            Icon.LAPTOP -> {
                c.drawRoundRect(RectF(4f, 5f, 20f, 16f), 1.5f, 1.5f, p)
                poly(c, false, 2f, 19f, 22f, 19f)
            }
            Icon.CHEVRON -> poly(c, false, 9f, 6f, 15f, 12f, 9f, 18f)
            Icon.SEND -> {
                poly(c, false, 21f, 3f, 10.5f, 13.5f)
                poly(c, true, 21f, 3f, 14.5f, 21f, 10.5f, 13.5f, 3f, 9.5f)
            }
            Icon.ORBIT -> {
                p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(36f, 6f), 0f)
                c.drawCircle(12f, 12f, 8f, p)
                p.pathEffect = null
                p.style = Paint.Style.FILL
                c.drawCircle(12f, 12f, 2.6f, p)
                c.drawCircle(17.7f, 6.3f, 2.4f, p)
                p.style = Paint.Style.STROKE
            }
            Icon.GLOBE -> {
                c.drawCircle(12f, 12f, 9f, p)
                c.drawOval(RectF(8f, 3f, 16f, 21f), p)
                poly(c, false, 3f, 12f, 21f, 12f)
            }
            Icon.FOLDER_PLUS -> {
                poly(c, true, 3f, 6f, 9f, 6f, 11f, 9f, 21f, 9f, 21f, 18f, 3f, 18f)
                poly(c, false, 12f, 11f, 12f, 16f)
                poly(c, false, 9.5f, 13.5f, 14.5f, 13.5f)
            }
            Icon.CLOCK -> {
                c.drawCircle(12f, 12f, 8f, p)
                poly(c, false, 12f, 8f, 12f, 12f, 15f, 14f)
            }
            Icon.WARN -> {
                poly(c, true, 12f, 3f, 22f, 20f, 2f, 20f)
                poly(c, false, 12f, 10f, 12f, 15f)
                poly(c, false, 12f, 18f, 12f, 18.1f)
            }
        }
        c.restore()
    }
}

/** Tombol yang meredup saat dinonaktifkan. */
class UiButton(ctx: Context) : Button(ctx) {
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.5f
    }
}

/** Bilah kemajuan bulat. */
class BarView(ctx: Context) : View(ctx) {
    var fraction = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var fillColor = Ui.ACCENT
    var trackColor = Color.argb(46, 255, 255, 255)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (8 * resources.displayMetrics.density).toInt())
    }

    override fun onDraw(c: Canvas) {
        val r = height / 2f
        p.color = trackColor
        c.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), r, r, p)
        if (fraction > 0f) {
            p.color = fillColor
            c.drawRoundRect(RectF(0f, 0f, maxOf(height.toFloat(), width * fraction), height.toFloat()), r, r, p)
        }
    }
}

class Screen(val root: LinearLayout, val content: LinearLayout, val title: TextView, val subtitle: TextView, val chip: TextView) {
    /** Ikon muat ulang di kepala layar (bila layar memintanya); berputar selama memuat. */
    var refresh: View? = null
    fun spin(on: Boolean) {
        val v = refresh ?: return
        v.animate().cancel()
        if (on) {
            v.rotation = 0f
            v.animate().rotationBy(3600f).setDuration(10000).setInterpolator(android.view.animation.LinearInterpolator()).start()
        } else v.animate().rotation(0f).setDuration(150).start()
    }
}

/** Tema bersama: warna, kartu, tombol, kolom isian, dan kerangka layar. */
object Ui {
    // Warna diisi ulang oleh applyTheme(): terang atau gelap (ikut sistem atau pilihan di Pengaturan).
    var dark = false
        private set
    var GROUND = 0; var CARD = 0; var INK = 0; var MUTED = 0; var HINT = 0
    var LINE = 0; var LINE2 = 0; var DIVIDER = 0
    /** ACCENT = teks/ikon teal; FILL = latar tombol teal dengan teks putih. */
    var ACCENT = 0; var FILL = 0; var DEEP = 0; var SOFT = 0; var SOFT2 = 0; var TINT = 0; var FIELD = 0
    var MINT = 0; var DANGER = 0; var GREEN = 0; var OK = 0
    var AMBER = 0; var AMBER_BG = 0; var AMBER_TX = 0
    var WARN_BG = 0; var WARN_LINE = 0; var WARN_TX = 0; var WARN_BADGE = 0
    val PENDING = Color.parseColor("#F59E0B")
    val WHITE = Color.WHITE
    // Warna mode "HP lain" (host): teal = PC, indigo = HP lain
    var INDIGO = 0; var INDIGO_FILL = 0; var INDIGO_DEEP = 0; var INDIGO_SOFT = 0; var INDIGO_INK = 0

    private fun c(light: String, night: String): Int = Color.parseColor(if (dark) night else light)
    /** Warna lain yang memakai pilihan terang/gelap; dipakai untuk teks di atas FILL. */
    var ON_FILL = 0

    // Warna dari design/tokens.json (Tokens.kt dibuat oleh build_tokens.py). Nama lama tetap dipakai layar-layar.
    private fun setColors() {
        Tokens.dark = dark
        GROUND = Tokens.BG; CARD = Tokens.SURFACE
        INK = Tokens.TEXT; MUTED = Tokens.TEXT_MUTED; HINT = c("#6B7D79", "#8DA29D")
        LINE = Tokens.BORDER; LINE2 = Tokens.BORDER_INPUT; DIVIDER = Tokens.SURFACE_2
        ACCENT = Tokens.ACCENT; FILL = Tokens.ACCENT; ON_FILL = Tokens.ON_ACCENT; DEEP = Tokens.ON_ACCENT_SOFT
        SOFT = Tokens.ACCENT_SOFT; SOFT2 = Tokens.ACCENT_SOFT; TINT = Tokens.SURFACE_2; FIELD = Tokens.SURFACE_2
        MINT = c("#BFE3DC", "#1E5A52"); DANGER = Tokens.DANGER; GREEN = Tokens.SUCCESS; OK = Tokens.SUCCESS
        AMBER = c("#F5B942", "#F5B942"); AMBER_BG = Tokens.WARNING_SOFT; AMBER_TX = Tokens.WARNING
        WARN_BG = Tokens.WARNING_SOFT; WARN_LINE = c("#F3D19E", "#6B4A12"); WARN_TX = Tokens.WARNING; WARN_BADGE = c("#FFE3BD", "#4A3410")
        INDIGO = Tokens.GUEST; INDIGO_FILL = c("#4F46E5", "#4F46E5"); INDIGO_DEEP = c("#312E81", "#1E1B4B")
        INDIGO_SOFT = Tokens.GUEST_SOFT; INDIGO_INK = Tokens.ON_GUEST_SOFT
    }

    // Huruf ikut terpasang (assets/fonts, lisensi OFL): Plus Jakarta Sans + IBM Plex Mono untuk nomor kontainer.
    var FONT: Typeface = Typeface.DEFAULT; private set
    var FONT_BOLD: Typeface = Typeface.DEFAULT_BOLD; private set
    var FONT_XBOLD: Typeface = Typeface.DEFAULT_BOLD; private set
    var FONT_MONO: Typeface = Typeface.MONOSPACE; private set
    private var fontsLoaded = false
    fun loadFonts(c: Context) {
        if (fontsLoaded) return
        try {
            val am = c.applicationContext.assets
            FONT = Typeface.createFromAsset(am, "fonts/jakarta-500.ttf")
            FONT_BOLD = Typeface.createFromAsset(am, "fonts/jakarta-700.ttf")
            FONT_XBOLD = Typeface.createFromAsset(am, "fonts/jakarta-800.ttf")
            FONT_MONO = Typeface.createFromAsset(am, "fonts/plexmono-600.ttf")
        } catch (e: Exception) { /* pakai huruf bawaan HP */ }
        fontsLoaded = true
    }

    /** Huruf mono untuk nomor kontainer/dokumen (mis. TCKU 6177329 › 30-23). */
    fun mono(t: TextView): TextView { t.typeface = FONT_MONO; return t }

    init { setColors() }

    /** Versi aplikasi ini, mis. "2.3.0" (dari versionName di build.gradle.kts). */
    fun appVersion(c: Context): String = try {
        @Suppress("DEPRECATION")
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "?"
    } catch (e: Exception) { "?" }

    /** Versi aplikasi dan PC beda di angka depan (mis. 2.3 vs 2.2)? Kemungkinan salah satu belum diperbarui. */
    fun versionMismatch(app: String, pc: String): Boolean {
        if (pc.isEmpty() || app == "?") return false
        fun mm(v: String) = v.split('.').take(2).joinToString(".")
        return mm(app) != mm(pc)
    }

    /** Apakah layar sebaiknya gelap: pilihan di Pengaturan, atau ikut mode gelap HP. */
    fun wantDark(a: Context): Boolean {
        Prefs.init(a)
        return when (Prefs.theme) {
            "dark" -> true
            "light" -> false
            else -> (a.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
    }

    /** Pilihan tema berubah sejak layar dibuat? Layar perlu dibuat ulang. */
    fun themeStale(a: Activity, builtDark: Boolean): Boolean = wantDark(a) != builtDark

    /** Tentukan terang/gelap untuk layar ini. Dipanggil oleh screen() sebelum tampilan dibuat. */
    fun applyTheme(a: Context) {
        loadFonts(a)
        dark = wantDark(a)
        setColors()
        if (a is Activity) a.setTheme(if (dark) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
    }

    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    enum class Kind { PRIMARY, OUTLINE, DEEP, DANGER, SOFT, INDIGO }

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density + 0.5f).toInt()

    fun lp(c: Context, w: Int = MATCH, h: Int = WRAP, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0, weight: Float = 0f): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h, weight).apply { setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b)) }

    fun rounded(c: Context, fill: Int, radius: Int, stroke: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(c, radius).toFloat()
            if (stroke != 0) setStroke(dp(c, 1).coerceAtLeast(1), stroke)
        }

    fun ripple(c: Context, fill: Int, radius: Int, stroke: Int = 0): Drawable =
        RippleDrawable(ColorStateList.valueOf(if (dark) Color.argb(50, 255, 255, 255) else Color.argb(40, 0, 0, 0)), rounded(c, fill, radius, stroke), null)

    fun text(c: Context, t: String, sp: Float, bold: Boolean = false, color: Int = INK): TextView =
        TextView(c).apply {
            text = t
            textSize = sp
            setTextColor(color)
            typeface = if (bold) (if (sp >= 20f) FONT_XBOLD else FONT_BOLD) else FONT
        }

    /** Judul kecil huruf besar di atas bagian. */
    fun caption(c: Context, t: String): TextView =
        text(c, t.uppercase(), 12f, true, MUTED).apply { letterSpacing = 0.06f }

    fun card(c: Context, fill: Int = CARD, pad: Int = 16): LinearLayout =
        LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(c, fill, 18, if (fill == CARD) LINE else 0)
            setPadding(dp(c, pad), dp(c, pad), dp(c, pad), dp(c, pad))
        }

    fun pill(c: Context, t: String, bg: Int, fg: Int): TextView =
        text(c, t, 12f, true, fg).apply {
            background = rounded(c, bg, 999)
            setPadding(dp(c, 12), dp(c, 6), dp(c, 12), dp(c, 6))
        }

    fun dot(c: Context, color: Int): View =
        View(c).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) } }

    fun setDot(v: View, color: Int) { (v.background as GradientDrawable).setColor(color) }

    private fun palette(k: Kind): Triple<Int, Int, Int> = when (k) {
        Kind.PRIMARY -> Triple(FILL, ON_FILL, 0)
        Kind.OUTLINE -> Triple(CARD, INK, LINE)
        Kind.DEEP -> Triple(if (dark) Color.parseColor("#134E48") else DEEP, WHITE, 0)
        Kind.DANGER -> Triple(if (dark) Color.parseColor("#B42318") else DANGER, WHITE, 0)
        Kind.SOFT -> Triple(SOFT, DEEP, 0)
        Kind.INDIGO -> Triple(INDIGO_FILL, WHITE, 0)
    }

    fun button(c: Context, label: String, kind: Kind = Kind.PRIMARY, h: Int = 52, onClick: () -> Unit): Button {
        val (fill, fg, stroke) = palette(kind)
        return UiButton(c).apply {
            text = label
            isAllCaps = false
            textSize = 16f
            typeface = FONT_BOLD
            setTextColor(fg)
            background = ripple(c, fill, 12, stroke)
            stateListAnimator = null
            minHeight = 0
            minimumHeight = dp(c, h)
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(c, 16), 0, dp(c, 16), 0)
            setOnClickListener { onClick() }
        }
    }

    fun iconButton(c: Context, icon: Int, label: String, kind: Kind = Kind.PRIMARY, h: Int = 52, ts: Float = 15f, onClick: () -> Unit): LinearLayout {
        val (fill, fg, stroke) = palette(kind)
        return LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = ripple(c, fill, if (h >= 64) 18 else 12, stroke)
            minimumHeight = dp(c, h)
            setPadding(dp(c, 14), 0, dp(c, 14), 0)
            addView(IconView(c, icon, if (kind == Kind.SOFT) ACCENT else fg, if (h >= 64) 26 else 22, 2.1f))
            addView(text(c, label, ts, true, fg).apply { setPadding(dp(c, 10), 0, 0, 0) })
            setOnClickListener { onClick() }
        }
    }

    fun tile(c: Context, icon: Int, label: String, onClick: () -> Unit): LinearLayout =
        LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            background = ripple(c, CARD, 20, LINE)
            minimumHeight = dp(c, 88)
            setPadding(dp(c, 14), dp(c, 14), dp(c, 14), dp(c, 14))
            addView(IconView(c, icon, ACCENT, 24, 1.9f))
            addView(View(c), lp(c, MATCH, 0, weight = 1f))
            addView(text(c, label, 14f, true, INK))
            setOnClickListener { onClick() }
        }

    /** Gaya kolom isian; berlaku juga untuk AutoCompleteTextView. */
    fun styleInput(c: Context, e: EditText, hint: String): EditText {
        e.hint = hint
        e.setHintTextColor(HINT)
        e.setTextColor(INK)
        e.textSize = 16f
        e.typeface = FONT_BOLD
        e.background = rounded(c, CARD, 12, LINE2)
        e.setPadding(dp(c, 12), 0, dp(c, 12), 0)
        e.minimumHeight = dp(c, 48)
        e.setSingleLine(true)
        return e
    }

    fun input(c: Context, hint: String, inputType: Int, value: String = ""): EditText =
        styleInput(c, EditText(c), hint).apply {
            this.inputType = inputType
            setText(value)
        }

    /** Kerangka layar: kepala (tombol kembali atau logo + judul), isi yang bisa digulir, bilah bawah opsional. */
    fun screen(a: Activity, title: String, subtitle: String? = null, back: Boolean = true, bottom: View? = null, nav: Int = -1,
               onRefresh: (() -> Unit)? = null): Screen {
        applyTheme(a)
        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(GROUND)
        }
        val head = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(a, if (back) 8 else 16), dp(a, 12), dp(a, 16), dp(a, 4))
        }
        if (back) {
            val b = FrameLayout(a).apply {
                isClickable = true
                isFocusable = true
                contentDescription = "Kembali"
                setOnClickListener { @Suppress("DEPRECATION") a.onBackPressed() }
                addView(IconView(a, Icon.BACK, INK, 24, 2.1f), FrameLayout.LayoutParams(dp(a, 24), dp(a, 24), Gravity.CENTER))
            }
            head.addView(b, LinearLayout.LayoutParams(dp(a, 44), dp(a, 44)))
        } else {
            val logo = FrameLayout(a).apply {
                background = rounded(a, Color.parseColor("#0F766E"), 12)
                addView(IconView(a, Icon.LOGO, WHITE, 24, 2.0f), FrameLayout.LayoutParams(dp(a, 24), dp(a, 24), Gravity.CENTER))
            }
            head.addView(logo, LinearLayout.LayoutParams(dp(a, 40), dp(a, 40)).apply { rightMargin = dp(a, 12) })
        }
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val t = text(a, title, 22f, true, INK)
        val st = text(a, subtitle ?: "", 14f, false, MUTED).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        st.visibility = if (subtitle.isNullOrEmpty()) View.GONE else View.VISIBLE
        col.addView(t)
        col.addView(st)
        head.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        // Penanda sambungan (mis. "PC-GATE" atau "Host · 1 HP"), diisi oleh layar masing-masing
        val chip = text(a, "", 12f, true, INK).apply {
            background = rounded(a, CARD, 999, LINE)
            setPadding(dp(a, 12), dp(a, 7), dp(a, 12), dp(a, 7))
            visibility = View.GONE
        }
        head.addView(chip, LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(a, 8) })
        var refreshIcon: View? = null
        if (onRefresh != null) {
            val ic = IconView(a, Icon.REFRESH, INK, 22, 2f)
            refreshIcon = ic
            val r = FrameLayout(a).apply {
                isClickable = true
                isFocusable = true
                contentDescription = "Muat ulang"
                background = ripple(a, CARD, 12, LINE)
                setOnClickListener { onRefresh() }
                addView(ic, FrameLayout.LayoutParams(dp(a, 22), dp(a, 22), Gravity.CENTER))
            }
            head.addView(r, LinearLayout.LayoutParams(dp(a, 48), dp(a, 48)).apply { leftMargin = dp(a, 8) })
        }
        if (nav >= 0) {
            val g = FrameLayout(a).apply {
                isClickable = true
                isFocusable = true
                contentDescription = "Pengaturan"
                background = ripple(a, CARD, 12, LINE)
                setOnClickListener { a.startActivity(android.content.Intent(a, MoreActivity::class.java)) }
                addView(IconView(a, Icon.GEAR, INK, 22, 2f), FrameLayout.LayoutParams(dp(a, 22), dp(a, 22), Gravity.CENTER))
            }
            head.addView(g, LinearLayout.LayoutParams(dp(a, 48), dp(a, 48)).apply { leftMargin = dp(a, 8) })
        }
        root.addView(head, LinearLayout.LayoutParams(MATCH, WRAP))

        val content = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(a, 16), dp(a, 8), dp(a, 16), dp(a, 24))
        }
        val sv = ScrollView(a).apply { addView(content, ViewGroup.LayoutParams(MATCH, WRAP)) }
        root.addView(sv, LinearLayout.LayoutParams(MATCH, 0, 1f))

        if (bottom != null) {
            root.addView(View(a).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(MATCH, dp(a, 1)))
            bottom.setPadding(dp(a, 20), dp(a, 12), dp(a, 20), dp(a, 16))
            root.addView(bottom, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        if (nav >= 0) {
            root.addView(View(a).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(MATCH, dp(a, 1)))
            root.addView(bottomNav(a, nav), LinearLayout.LayoutParams(MATCH, WRAP))
        }
        systemBars(a)
        a.setContentView(root)
        return Screen(root, content, t, st, chip).also { it.refresh = refreshIcon }
    }

    const val TAB_SEND = 0
    const val TAB_FILES = 1
    const val TAB_HOST = 2

    private fun tabClass(tab: Int): Class<out Activity> = when (tab) {
        TAB_HOST -> HostActivity::class.java
        TAB_FILES -> BrowseActivity::class.java
        else -> MainActivity::class.java
    }

    /** Isi penanda sambungan di kepala layar. indigo = mode HP lain. */
    fun setChip(chip: TextView, label: String, indigo: Boolean = false) {
        val c = chip.context
        chip.text = "● $label"
        chip.visibility = if (label.isEmpty()) View.GONE else View.VISIBLE
        chip.setTextColor(if (indigo) INDIGO_INK else INK)
        chip.background = if (indigo) rounded(c, INDIGO_SOFT, 999) else rounded(c, CARD, 999, LINE)
    }

    /** Pindah antar tab utama. Kirim adalah dasar tumpukan; tab lain saling menggantikan. */
    fun goTab(a: Activity, tab: Int) {
        val i = android.content.Intent(a, tabClass(tab)).addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
        if (tab == TAB_SEND) i.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
        a.startActivity(i)
        if (tab != TAB_SEND && a !is MainActivity) a.finish()
        @Suppress("DEPRECATION")
        a.overridePendingTransition(0, 0)
    }

    /** Menu bawah: Kirim, File, Ekosistem. Pengaturan lewat ikon di kepala layar. */
    fun bottomNav(a: Activity, active: Int): View {
        val row = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(CARD)
        }
        val items = listOf(
            Triple("Kirim", Icon.SEND, TAB_SEND),
            Triple("File", Icon.FOLDER, TAB_FILES),
            Triple("Ekosistem", Icon.ORBIT, TAB_HOST),
        )
        for ((label, icon, tab) in items) {
            if (tab == TAB_FILES && Prefs.role == "send" && active != TAB_FILES) continue  // HP "hanya kirim": tanpa menu File
            val on = tab == active
            val col = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = label
                minimumHeight = dp(a, 72)
                background = RippleDrawable(ColorStateList.valueOf(if (dark) Color.argb(40, 255, 255, 255) else Color.argb(30, 0, 0, 0)), null, null)
                setOnClickListener { if (!on) goTab(a, tab) }
            }
            val pill = FrameLayout(a).apply {
                if (on) background = rounded(a, SOFT, 999)
                addView(IconView(a, icon, if (on) DEEP else MUTED, 24, if (on) 2.3f else 1.9f), FrameLayout.LayoutParams(dp(a, 24), dp(a, 24), Gravity.CENTER))
            }
            col.addView(pill, LinearLayout.LayoutParams(dp(a, 56), dp(a, 30)))
            col.addView(text(a, label, 12f, on, if (on) DEEP else MUTED), lp(a, WRAP, WRAP, t = 4))
            row.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        return row
    }

    // ---------- DropBridge 3: komponen bersama ----------

    /** Satu ikon berlabel kecil di baris ikon Home. badge = angka (mis. antrean), on = titik hijau (mis. Foto kecil hidup). */
    class Tool(val label: String, val icon: Int, val badge: String? = null, val on: Boolean? = null, val click: (View) -> Unit)

    fun tools(c: Context, items: List<Tool>): LinearLayout {
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(c, CARD, 18, LINE)
            setPadding(dp(c, 6), dp(c, 8), dp(c, 6), dp(c, 8))
        }
        for (t in items) {
            val col = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                isClickable = true
                isFocusable = true
                contentDescription = t.label + when (t.on) { true -> ", hidup"; false -> ", mati"; else -> "" } + (t.badge?.let { ", $it" } ?: "")
                background = ripple(c, CARD, 12)
                setPadding(dp(c, 2), dp(c, 6), dp(c, 2), dp(c, 6))
                setOnClickListener { t.click(it) }
            }
            val box = FrameLayout(c)
            val ic = FrameLayout(c).apply {
                background = rounded(c, FIELD, 14)
                addView(IconView(c, t.icon, if (t.on == false) MUTED else ACCENT, 22, 2f), FrameLayout.LayoutParams(dp(c, 22), dp(c, 22), Gravity.CENTER))
            }
            box.addView(ic, FrameLayout.LayoutParams(dp(c, 44), dp(c, 44), Gravity.CENTER))
            if (t.on == true) box.addView(View(c).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(OK); setStroke(dp(c, 2), CARD) }
            }, FrameLayout.LayoutParams(dp(c, 13), dp(c, 13), Gravity.TOP or Gravity.END).apply { topMargin = 0; rightMargin = dp(c, 2) })
            if (t.badge != null) box.addView(text(c, t.badge, 11f, true, WHITE).apply {
                gravity = Gravity.CENTER
                background = GradientDrawable().apply { cornerRadius = dp(c, 10).toFloat(); setColor(Tokens.WARNING); setStroke(dp(c, 2), CARD) }
                setPadding(dp(c, 5), 0, dp(c, 5), 0)
                minWidth = dp(c, 20)
            }, FrameLayout.LayoutParams(WRAP, dp(c, 20), Gravity.TOP or Gravity.END))
            col.addView(box, LinearLayout.LayoutParams(dp(c, 56), dp(c, 46)))
            col.addView(text(c, t.label, 11f, true, MUTED).apply { gravity = Gravity.CENTER; maxLines = 2 }, lp(c, MATCH, WRAP, t = 6))
            row.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        return row
    }

    /** Lembar dari bawah (Pilih folder, Teks, Pilih PC). Mengembalikan dialog dan kotak isinya. */
    fun sheet(a: Activity, title: String, onClose: (() -> Unit)? = null): Pair<android.app.Dialog, LinearLayout> {
        val c: Context = a
        val d = android.app.Dialog(a)
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val r = dp(c, 24).toFloat()
            background = GradientDrawable().apply { setColor(CARD); cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) }
            setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 20))
        }
        box.addView(View(c).apply { background = rounded(c, LINE, 4) }, LinearLayout.LayoutParams(dp(c, 40), dp(c, 4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(c, 8) })
        val head = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(c, 44) }
        head.addView(text(c, title, 20f, true), LinearLayout.LayoutParams(0, WRAP, 1f))
        head.addView(text(c, "Tutup", 15f, true, ACCENT).apply {
            minimumHeight = dp(c, 44)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(c, 12), 0, dp(c, 4), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { d.dismiss() }
        })
        box.addView(head)
        d.setContentView(box)
        d.window?.let { w ->
            w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            w.setLayout(MATCH, WRAP)
            w.setGravity(Gravity.BOTTOM)
        }
        if (onClose != null) d.setOnDismissListener { onClose() }
        return d to box
    }

    /** Baris pengaturan: judul + keterangan kecil di kiri, isi bebas di kanan. */
    fun settingRow(c: Context, title: String, sub: String? = null, right: View? = null, onClick: (() -> Unit)? = null): LinearLayout {
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(c, 60)
            setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10))
            if (onClick != null) {
                isClickable = true
                isFocusable = true
                background = ripple(c, CARD, 0)
                setOnClickListener { onClick() }
            }
        }
        val t = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        t.addView(text(c, title, 15f, true))
        if (!sub.isNullOrEmpty()) t.addView(text(c, sub, 13f, false, MUTED))
        row.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (right != null) row.addView(right, LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(c, 12) })
        return row
    }

    /** Kelompok baris pengaturan dalam satu kartu, dengan garis di antara baris. */
    fun group(c: Context, rows: List<View>): LinearLayout {
        val g = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(c, CARD, 18, LINE)
            clipToOutline = true
        }
        rows.forEachIndexed { i, r ->
            if (i > 0) g.addView(View(c).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(MATCH, dp(c, 1)))
            g.addView(r, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        return g
    }

    /** Sakelar Android bawaan diwarnai teal. */
    fun switch(c: Context, on: Boolean, label: String, change: (Boolean) -> Unit): android.widget.Switch =
        android.widget.Switch(c).apply {
            isChecked = on
            contentDescription = label
            if (Build.VERSION.SDK_INT >= 23) {
                val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
                thumbTintList = ColorStateList(states, intArrayOf(ACCENT, CARD))
                trackTintList = ColorStateList(states, intArrayOf(SOFT, LINE2))
            }
            setOnCheckedChangeListener { _, v -> change(v) }
        }

    @Suppress("DEPRECATION")
    fun systemBars(a: Activity) {
        if (Build.VERSION.SDK_INT >= 21) {
            a.window.statusBarColor = GROUND
            a.window.navigationBarColor = GROUND
        }
        var f = a.window.decorView.systemUiVisibility
        val light = if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            else if (Build.VERSION.SDK_INT >= 23) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else 0
        f = if (dark) f and light.inv() else f or light
        a.window.decorView.systemUiVisibility = f
    }
}
