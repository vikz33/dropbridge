package com.dropbridge.app

import android.content.Context
import android.content.SharedPreferences

/** Pengaturan kecil yang disimpan di HP: alamat PC, PIN, token, folder terakhir. */
object Prefs {
    private var sp: SharedPreferences? = null

    fun init(ctx: Context) {
        if (sp == null) {
            sp = ctx.applicationContext.getSharedPreferences("dropbridge", Context.MODE_PRIVATE)
            Net.setAppVersion(Ui.appVersion(ctx.applicationContext))
        }
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

    /** Nama komputer PC (mis. PC-GATE), untuk judul dan status. */
    var pcName: String
        get() = get("pcName")
        set(v) = put("pcName", v)

    /** Tampilan: "system" (ikut HP), "light", atau "dark". */
    var theme: String
        get() = get("theme").ifEmpty { "system" }
        set(v) = put("theme", v)

    /** Alamat PC lewat Tailscale (mis. http://100.88.12.4:8000), dipelajari dari PC saat satu Wi-Fi. */
    var remoteServer: String
        get() = get("remoteServer")
        set(v) = put("remoteServer", v)

    /** Kirim otomatis boleh memakai data seluler (lewat Tailscale). */
    var allowMobile: Boolean
        get() = get("allowMobile") != "0"
        set(v) = put("allowMobile", if (v) "1" else "0")

    /** Versi DropBridge di PC (dari /api/info), untuk dicocokkan dengan versi aplikasi. */
    var pcVersion: String
        get() = get("pcVersion")
        set(v) = put("pcVersion", v)

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

    /** PC yang sedang dipakai: kode tetap dan sistemnya (Windows/Linux), dari /api/info. */
    var pcId: String
        get() = get("pcId")
        set(v) = put("pcId", v)

    var pcOs: String
        get() = get("pcOs")
        set(v) = put("pcOs", v)

    /** Hak HP ini di PC yang dipakai: "full" atau "send" (hanya kirim). */
    var role: String
        get() = get("role").ifEmpty { "full" }
        set(v) = put("role", v)

    /** Kode tetap HP ini; PC mengingat izinnya, jadi login ulang tidak perlu izin lagi. */
    val deviceId: String
        get() {
            var v = get("deviceId")
            if (v.isEmpty()) { v = "a" + java.util.UUID.randomUUID().toString().replace("-", ""); put("deviceId", v) }
            return v
        }

    /** Semua PC yang pernah disambungkan (JSON), untuk pindah PC tanpa PIN lagi. */
    var pcList: String
        get() = get("pcList")
        set(v) = put("pcList", v)

    /** Daftar folder di PC (disimpan terakhir kali berhasil diambil) untuk saran nama. */
    var folders: List<String>
        get() = get("folders").split("\n").filter { it.isNotEmpty() }
        set(v) = put("folders", v.joinToString("\n"))
}

/** Teks status yang dibaca layar utama. */
object Status {
    @Volatile var line: String = ""
}
