package com.dropbridge.app

import android.app.Activity

/** Sambungan ke PC: dipakai layar utama (otomatis) dan layar Lainnya (manual). */
object Conn {
    fun normalize(a: String): String {
        var s = a.trim().trimEnd('/')
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
        if (!Regex(":\\d+$").containsMatchIn(s)) s = "$s:${Net.DEFAULT_PORT}"
        return s
    }

    @Volatile var busy = false

    /**
     * Cari PC (atau pakai alamat tersimpan), login, lalu kirim antrean bila ada. Hasil di Status.line.
     * rescan = true: cari ulang di Wi-Fi dulu (tombol "Cari PC"), baru alamat tersimpan dan Tailscale.
     */
    fun connect(a: Activity, rescan: Boolean = false, onAddr: (String) -> Unit = {}) {
        if (busy) return
        busy = true
        Status.line = "Mencari PC..."
        Thread {
            try { work(a, rescan, onAddr) } finally { busy = false }
        }.start()
    }

    private fun work(a: Activity, rescan: Boolean, onAddr: (String) -> Unit) {
        run {
            val base = if (rescan) Net.rescan() else Net.ensureServer()
            if (base == null) {
                Status.line = if (Prefs.remoteServer.isNotEmpty())
                    "PC tidak ditemukan. Di luar jaringan? Nyalakan Tailscale di HP. Atau pastikan start.bat berjalan di PC."
                else
                    "PC tidak ditemukan. Pastikan start.bat berjalan dan HP satu Wi-Fi (atau hotspot) dengan PC. Atau isi alamat manual di Pengaturan."
                return
            }
            try {
                val token = Net.ensureToken(base, true)
                Net.folders(base, token)
                try {
                    val info = Net.getJson(base, token, "/api/info")
                    val n = info.optString("pc_name", "")
                    if (n.isNotEmpty()) Prefs.pcName = n
                    Prefs.pcVersion = info.optString("version", "")
                    Net.learnRemote(info)
                } catch (e: Exception) { /* server lama: tanpa nama PC */ }
                Status.line = "Tersambung ke PC ($base)." + if (Net.viaRemote) " Lewat internet (Tailscale)." else ""
                a.runOnUiThread { onAddr(base) }
                if (Queue.all().isNotEmpty() && !UploadService.running) {
                    a.runOnUiThread { UploadService.start(a) }
                }
            } catch (e: NetError) {
                Status.line = if (e.code == 401) "PIN salah. Cek PIN di layar PC." else "Gagal masuk: ${e.message}"
            } catch (e: Exception) {
                Status.line = "Gagal terhubung: ${e.message}"
            }
        }
    }
}
