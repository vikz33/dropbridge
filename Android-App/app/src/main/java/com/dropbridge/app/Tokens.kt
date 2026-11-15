package com.dropbridge.app

import android.graphics.Color

/** Dibuat otomatis oleh design/build_tokens.py dari tokens.json. Jangan diedit langsung. */
object Tokens {
    var dark = false
    private fun c(l: String, d: String): Int = Color.parseColor(if (dark) d else l)

    val BG: Int get() = c("#F3F6F5", "#0B1513")
    val SURFACE: Int get() = c("#FFFFFF", "#13201E")
    val SURFACE_2: Int get() = c("#EAF0EE", "#1B2B28")
    val BORDER: Int get() = c("#D6E0DD", "#263A36")
    val BORDER_INPUT: Int get() = c("#7E918C", "#6A827D")
    val TEXT: Int get() = c("#0E1A18", "#E8F1EF")
    val TEXT_MUTED: Int get() = c("#4D5E5A", "#A3B6B2")
    val ACCENT: Int get() = c("#0F766E", "#3DD6C1")
    val ON_ACCENT: Int get() = c("#FFFFFF", "#042420")
    val ACCENT_SOFT: Int get() = c("#DCEFEB", "#123A35")
    val ON_ACCENT_SOFT: Int get() = c("#0A4A44", "#BDF3EA")
    val GUEST: Int get() = c("#4F46E5", "#A5B4FC")
    val ON_GUEST: Int get() = c("#FFFFFF", "#13154A")
    val GUEST_SOFT: Int get() = c("#E6E5FC", "#25285A")
    val ON_GUEST_SOFT: Int get() = c("#2E2A8C", "#DDE1FF")
    val SUCCESS: Int get() = c("#15803D", "#4ADE80")
    val WARNING: Int get() = c("#9A4A07", "#FBBF24")
    val WARNING_SOFT: Int get() = c("#FFF0D9", "#3A2A0A")
    val DANGER: Int get() = c("#B42318", "#F87171")
    val DANGER_SOFT: Int get() = c("#FDE8E6", "#3A1614")
    val K_IMAGE: Int get() = c("#6D28D9", "#C4B5FD")
    val K_VIDEO: Int get() = c("#BE185D", "#F9A8D4")
    val K_AUDIO: Int get() = c("#C2410C", "#FDBA74")
    val K_DOC: Int get() = c("#1D4ED8", "#93C5FD")
    val K_ARCHIVE: Int get() = c("#854D0E", "#FDE047")
    val K_OTHER: Int get() = c("#475569", "#CBD5E1")

    const val SPACE_1 = 4
    const val SPACE_2 = 8
    const val SPACE_3 = 12
    const val SPACE_4 = 16
    const val SPACE_5 = 20
    const val SPACE_6 = 24
    const val SPACE_8 = 32
    const val SPACE_10 = 40
    const val RADIUS_SM = 8
    const val RADIUS_MD = 12
    const val RADIUS_LG = 18
    const val RADIUS_XL = 24
    const val RADIUS_PILL = 999
    const val TOUCH = 48
    const val TOUCH_LG = 56
    const val ICON = 22
    const val HEADER = 64
    const val NAV = 72
    const val CONTENT_MAX = 1120
    const val SPLIT = 720
    const val TYPE_CAPTION = 12f
    const val TYPE_SMALL = 14f
    const val TYPE_BODY = 16f
    const val TYPE_LABEL = 16f
    const val TYPE_TITLE_SM = 18f
    const val TYPE_TITLE = 22f
    const val TYPE_DISPLAY = 28f
    const val TYPE_CODE = 17f
}
