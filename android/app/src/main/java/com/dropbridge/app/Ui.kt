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
            Icon.MENU -> {
                poly(c, false, 4f, 7f, 20f, 7f)
                poly(c, false, 4f, 12f, 20f, 12f)
                poly(c, false, 4f, 17f, 20f, 17f)
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

class Screen(val root: LinearLayout, val content: LinearLayout, val title: TextView, val subtitle: TextView)

/** Tema bersama: warna, kartu, tombol, kolom isian, dan kerangka layar. */
object Ui {
    val GROUND = Color.parseColor("#F1F6F5")
    val INK = Color.parseColor("#0B1F1C")
    val MUTED = Color.parseColor("#4B635F")
    val LINE = Color.parseColor("#D5E2DF")
    val LINE2 = Color.parseColor("#C3D3CF")
    val ACCENT = Color.parseColor("#0F766E")
    val DEEP = Color.parseColor("#0B3D38")
    val SOFT = Color.parseColor("#D6EEE9")
    val MINT = Color.parseColor("#BFE3DC")
    val DANGER = Color.parseColor("#B42318")
    val GREEN = Color.parseColor("#4ADE80")
    val AMBER = Color.parseColor("#F5B942")
    val AMBER_BG = Color.parseColor("#FFF4DB")
    val AMBER_TX = Color.parseColor("#5C3A00")
    val FIELD = Color.parseColor("#F4F7F6")
    val WHITE = Color.WHITE

    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    enum class Kind { PRIMARY, OUTLINE, DEEP, DANGER, SOFT }

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
        RippleDrawable(ColorStateList.valueOf(Color.argb(40, 0, 0, 0)), rounded(c, fill, radius, stroke), null)

    fun text(c: Context, t: String, sp: Float, bold: Boolean = false, color: Int = INK): TextView =
        TextView(c).apply {
            text = t
            textSize = sp
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    /** Judul kecil huruf besar di atas bagian. */
    fun caption(c: Context, t: String): TextView =
        text(c, t.uppercase(), 12f, true, MUTED).apply { letterSpacing = 0.05f }

    fun card(c: Context, fill: Int = WHITE, pad: Int = 16): LinearLayout =
        LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(c, fill, 20, if (fill == WHITE) LINE else 0)
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
        Kind.PRIMARY -> Triple(ACCENT, WHITE, 0)
        Kind.OUTLINE -> Triple(WHITE, INK, LINE2)
        Kind.DEEP -> Triple(DEEP, WHITE, 0)
        Kind.DANGER -> Triple(DANGER, WHITE, 0)
        Kind.SOFT -> Triple(SOFT, DEEP, 0)
    }

    fun button(c: Context, label: String, kind: Kind = Kind.PRIMARY, h: Int = 52, onClick: () -> Unit): Button {
        val (fill, fg, stroke) = palette(kind)
        return UiButton(c).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(fg)
            background = ripple(c, fill, 16, stroke)
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
            background = ripple(c, fill, if (h >= 64) 20 else 16, stroke)
            minimumHeight = dp(c, h)
            setPadding(dp(c, 14), 0, dp(c, 14), 0)
            addView(IconView(c, icon, fg, 22, 2.1f))
            addView(text(c, label, ts, true, fg).apply { setPadding(dp(c, 10), 0, 0, 0) })
            setOnClickListener { onClick() }
        }
    }

    fun tile(c: Context, icon: Int, label: String, onClick: () -> Unit): LinearLayout =
        LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            background = ripple(c, WHITE, 20, LINE)
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
        e.setHintTextColor(Color.parseColor("#6B817D"))
        e.setTextColor(INK)
        e.textSize = 15f
        e.setTypeface(e.typeface, Typeface.BOLD)
        e.background = rounded(c, FIELD, 12, LINE2)
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
    fun screen(a: Activity, title: String, subtitle: String? = null, back: Boolean = true, bottom: View? = null, nav: Int = -1): Screen {
        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(GROUND)
        }
        val head = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(a, if (back) 8 else 20), dp(a, 10), dp(a, 20), dp(a, 4))
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
                background = rounded(a, ACCENT, 11)
                addView(IconView(a, Icon.LOGO, WHITE, 24, 2.0f), FrameLayout.LayoutParams(dp(a, 24), dp(a, 24), Gravity.CENTER))
            }
            head.addView(logo, LinearLayout.LayoutParams(dp(a, 36), dp(a, 36)).apply { rightMargin = dp(a, 10) })
        }
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val t = text(a, title, 20f, true, INK)
        val st = text(a, subtitle ?: "", 12f, false, MUTED)
        st.visibility = if (subtitle.isNullOrEmpty()) View.GONE else View.VISIBLE
        col.addView(t)
        col.addView(st)
        head.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        root.addView(head, LinearLayout.LayoutParams(MATCH, WRAP))

        val content = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(a, 20), dp(a, 12), dp(a, 20), dp(a, 24))
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
        return Screen(root, content, t, st)
    }

    const val TAB_SEND = 0
    const val TAB_HOST = 1
    const val TAB_FILES = 2
    const val TAB_MORE = 3

    private fun tabClass(tab: Int): Class<out Activity> = when (tab) {
        TAB_HOST -> HostActivity::class.java
        TAB_FILES -> BrowseActivity::class.java
        TAB_MORE -> MoreActivity::class.java
        else -> MainActivity::class.java
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

    /** Menu bawah: Kirim, Terima, Folder, Lainnya. */
    fun bottomNav(a: Activity, active: Int): View {
        val row = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(WHITE)
        }
        val items = listOf(
            Triple("Kirim", Icon.UP, TAB_SEND),
            Triple("Terima", Icon.DOWN, TAB_HOST),
            Triple("Folder", Icon.FOLDER, TAB_FILES),
            Triple("Lainnya", Icon.MENU, TAB_MORE),
        )
        for ((label, icon, tab) in items) {
            val on = tab == active
            val col = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = label
                minimumHeight = dp(a, 64)
                background = RippleDrawable(ColorStateList.valueOf(Color.argb(30, 0, 0, 0)), null, null)
                setOnClickListener { if (!on) goTab(a, tab) }
            }
            col.addView(IconView(a, icon, if (on) ACCENT else MUTED, 24, if (on) 2.3f else 1.9f))
            col.addView(text(a, label, 12f, on, if (on) ACCENT else MUTED), lp(a, WRAP, WRAP, t = 4))
            row.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        return row
    }

    @Suppress("DEPRECATION")
    fun systemBars(a: Activity) {
        if (Build.VERSION.SDK_INT >= 21) {
            a.window.statusBarColor = GROUND
            a.window.navigationBarColor = GROUND
        }
        var f = a.window.decorView.systemUiVisibility
        if (Build.VERSION.SDK_INT >= 23) f = f or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (Build.VERSION.SDK_INT >= 26) f = f or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        a.window.decorView.systemUiVisibility = f
    }
}
