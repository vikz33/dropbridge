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

    fun get(key: String): String = sp?.getString(key, "") ?: ""
    fun put(key: String, value: String) { sp?.edit()?.putString(key, value)?.apply() }

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

    /** Folder bawaan di layar Kirim: "root" (Tanpa folder, bawaan), "last" (terakhir dipakai), atau "fixed" (folder tertentu). */
    var defaultFolderMode: String
        get() = get("defFolderMode").ifEmpty { "root" }
        set(v) = put("defFolderMode", v)

    /** Folder tertentu untuk mode "fixed". */
    var fixedFolder: String
        get() = get("fixedFolder")
        set(v) = put("fixedFolder", v)

    /** Isi kolom folder saat layar Kirim dibuka, sesuai folder bawaan. Folder tertentu yang sudah tidak ada di PC → Tanpa folder. */
    fun startFolder(): String = when (defaultFolderMode) {
        "last" -> lastFolder.ifEmpty { FolderPicker.ROOT }
        "fixed" -> {
            val f = fixedFolder
            val gone = f.isNotEmpty() && folders.isNotEmpty() && folders.none { it.equals(f, ignoreCase = true) }
            if (f.isEmpty() || gone) FolderPicker.ROOT else f
        }
        else -> FolderPicker.ROOT
    }

    /** Cara memberi nama file: "asli" (bawaan) atau "otomatis" (PC memberi nomor urut). */
    var nameMode: String
        get() = get("nameMode").ifEmpty { "asli" }
        set(v) = put("nameMode", v)

    /** Pola nama otomatis: "foto" (Foto 01, bawaan), "folder" (Nama folder 01), atau "tanggal" (2026-11-18 01). */
    var namePattern: String
        get() = get("namePattern").ifEmpty { "foto" }
        set(v) = put("namePattern", v)

    /** Kirim antrean otomatis saat HP tersambung Wi-Fi (default aktif). */
    var shrinkPhotos: Boolean
        get() = sp?.getBoolean("shrinkPhotos", true) ?: true
        set(v) { sp?.edit()?.putBoolean("shrinkPhotos", v)?.apply() }

    var autoSend: Boolean
        get() = sp?.getBoolean("autoSend", true) ?: true
        set(v) { sp?.edit()?.putBoolean("autoSend", v)?.apply() }

    /** Pengiriman dijeda pengguna: tidak ada yang mengirim sendiri sampai Lanjut. Antrean tetap utuh. */
    var paused: Boolean
        get() = sp?.getBoolean("paused", false) ?: false
        set(v) { sp?.edit()?.putBoolean("paused", v)?.apply() }

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

    /** Nama HP ini yang tampil di laptop (kosong = merek + model HP). */
    var devName: String
        get() = get("devName")
        set(v) = put("devName", v)

    /** Folder yang terakhir dipakai HP ini (terbaru di depan), untuk "Terakhir dipakai" di Pilih folder. */
    var recentTargets: List<String>
        get() = get("recentTargets").split("\n").filter { it.isNotEmpty() }
        set(v) = put("recentTargets", v.joinToString("\n"))

    fun rememberTarget(p: String) {
        if (p.isEmpty()) return
        recentTargets = (listOf(p) + recentTargets.filter { !it.equals(p, ignoreCase = true) }).take(8)
    }

    /** Terima file yang dikirim laptop ke HP ini (laptop → HP). Bawaan: hidup. */
    var receiveFromPc: Boolean
        get() = get("recvPc") != "0"
        set(v) = put("recvPc", if (v) "1" else "0")

    /** Tab Ekosistem: tampilkan Daftar (true) atau Peta tata surya (false). */
    /** Tujuan di tab Kirim: "pc" (laptop) atau "qr" (HP tanpa aplikasi lewat berbagi). */
    var sendTo: String
        get() = get("sendTo").ifEmpty { "pc" }
        set(v) = put("sendTo", v)

    var hubList: Boolean
        get() = get("hubList") == "1"
        set(v) = put("hubList", if (v) "1" else "0")

    /** Daftar folder di PC (disimpan terakhir kali berhasil diambil) untuk saran nama. */
    var folders: List<String>
        get() = get("folders").split("\n").filter { it.isNotEmpty() }
        set(v) = put("folders", v.joinToString("\n"))
}

/** Teks status yang dibaca layar utama. */
object Status {
    @Volatile var line: String = ""
}
