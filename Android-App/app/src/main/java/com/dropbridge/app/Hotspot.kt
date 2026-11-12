package com.dropbridge.app

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import java.io.File

/**
 * Hotspot khusus DropBridge (local-only hotspot, Android 8+): dibuat otomatis, nama dan password
 * bisa dibaca aplikasi, jadi HP teman cukup scan QR Wi-Fi tanpa mengetik password.
 * Tidak berbagi internet. Bila gagal, pengguna memakai hotspot biasa dari Pengaturan.
 */
object Hotspot {
    @Volatile private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    @Volatile var ssid = ""
    @Volatile var pass = ""
    @Volatile var error = ""
    @Volatile var starting = false

    val active: Boolean get() = reservation != null && ssid.isNotEmpty()

    fun supported(): Boolean = Build.VERSION.SDK_INT >= 26

    /** Izin yang dibutuhkan sebelum start(). */
    fun permissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) arrayOf("android.permission.NEARBY_WIFI_DEVICES")
        else arrayOf("android.permission.ACCESS_FINE_LOCATION")

    fun start(ctx: Context, done: () -> Unit) {
        if (!supported()) {
            error = "Android di HP ini belum mendukung hotspot otomatis."
            done()
            return
        }
        if (reservation != null || starting) {
            done()
            return
        }
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        starting = true
        error = ""
        try {
            wm.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(r: WifiManager.LocalOnlyHotspotReservation) {
                    reservation = r
                    try {
                        if (Build.VERSION.SDK_INT >= 30) {
                            val c = r.softApConfiguration
                            @Suppress("DEPRECATION")
                            ssid = (c.ssid ?: "").trim('"')
                            pass = c.passphrase ?: ""
                        } else {
                            @Suppress("DEPRECATION")
                            val c = r.wifiConfiguration
                            @Suppress("DEPRECATION")
                            ssid = (c?.SSID ?: "").trim('"')
                            @Suppress("DEPRECATION")
                            pass = (c?.preSharedKey ?: "").trim('"')
                        }
                    } catch (e: Exception) {
                        error = "Nama hotspot tidak terbaca."
                    }
                    starting = false
                    done()
                }

                override fun onStopped() {
                    reservation = null
                    ssid = ""
                    pass = ""
                    starting = false
                    done()
                }

                override fun onFailed(reason: Int) {
                    starting = false
                    error = when (reason) {
                        ERROR_INCOMPATIBLE_MODE -> "Hotspot biasa sedang menyala atau Wi-Fi sibuk. Pakai hotspot biasa saja."
                        ERROR_TETHERING_DISALLOWED -> "HP ini tidak mengizinkan hotspot."
                        else -> "Hotspot otomatis gagal dibuat (kode $reason)."
                    }
                    done()
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: SecurityException) {
            starting = false
            error = "Butuh izin lokasi / perangkat sekitar, dan lokasi (GPS) harus menyala."
            done()
        } catch (e: Exception) {
            starting = false
            error = "Hotspot otomatis tidak tersedia: ${e.message}"
            done()
        }
    }

    fun stop() {
        try { reservation?.close() } catch (e: Exception) { /* abaikan */ }
        reservation = null
        ssid = ""
        pass = ""
        starting = false
    }

    private fun esc(s: String): String = s.replace(Regex("([\\\\;,:\"])"), "\\\\$1")

    /** Teks QR Wi-Fi standar; kamera iPhone dan Android bisa langsung menyambung. */
    fun wifiQr(): String = "WIFI:T:WPA;S:${esc(ssid)};P:${esc(pass)};;"
}

/** Salinan file yang dibagikan ke HP lain, supaya tetap bisa dibaca walau izin aplikasi asal berakhir. */
object HostFiles {
    fun dir(ctx: Context): File = File(ctx.cacheDir, "host").apply { mkdirs() }

    /** Salin ke cache, kembalikan path-nya (atau null bila gagal). */
    fun copyIn(ctx: Context, uri: Uri): String? {
        var name = "file"
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) name = c.getString(0)
            }
        } catch (e: Exception) { /* pakai nama awal */ }
        name = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val d = dir(ctx)
        var f = File(d, name)
        var i = 1
        while (f.exists()) {
            val base = name.substringBeforeLast('.', name)
            val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
            f = File(d, "$base ($i)$ext")
            i++
        }
        return try {
            val ins = ctx.contentResolver.openInputStream(uri) ?: return null
            ins.use { input -> f.outputStream().use { FileSaver.copy(input, it, -1) } }
            f.absolutePath
        } catch (e: Exception) {
            f.delete()
            null
        }
    }

    fun clear(ctx: Context) {
        dir(ctx).listFiles()?.forEach { it.delete() }
    }
}
