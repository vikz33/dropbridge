package com.dropbridge.app

import android.content.Context
import android.content.SharedPreferences

/** Pengaturan kecil yang disimpan di HP: alamat PC, PIN, token, folder terakhir. */
object Prefs {
    private var sp: SharedPreferences? = null

    fun init(ctx: Context) {
        if (sp == null) sp = ctx.applicationContext.getSharedPreferences("dropbridge", Context.MODE_PRIVATE)
    }

    private fun get(key: String): String = sp?.getString(key, "") ?: ""
    private fun put(key: String, value: String) { sp?.edit()?.putString(key, value)?.apply() }

    var server: String
        get() = get("server")
        set(v) = put("server", v)

    var pin: String
        get() = get("pin")
        set(v) = put("pin", v)

    var token: String
        get() = get("token")
        set(v) = put("token", v)

    var hostAddr: String
        get() = get("hostAddr")
        set(v) = put("hostAddr", v)

    var lastFolder: String
        get() = get("lastFolder")
        set(v) = put("lastFolder", v)

    /** Kirim antrean otomatis saat HP tersambung Wi-Fi (default aktif). */
    var shrinkPhotos: Boolean
        get() = sp?.getBoolean("shrinkPhotos", true) ?: true
        set(v) { sp?.edit()?.putBoolean("shrinkPhotos", v)?.apply() }

    var autoSend: Boolean
        get() = sp?.getBoolean("autoSend", true) ?: true
        set(v) { sp?.edit()?.putBoolean("autoSend", v)?.apply() }

    /** Daftar folder di PC (disimpan terakhir kali berhasil diambil) untuk saran nama. */
    var folders: List<String>
        get() = get("folders").split("\n").filter { it.isNotEmpty() }
        set(v) = put("folders", v.joinToString("\n"))
}

/** Teks status yang dibaca layar utama. */
object Status {
    @Volatile var line: String = ""
}
