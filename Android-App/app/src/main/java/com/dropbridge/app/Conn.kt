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
        if (Status.line != Net.PENDING) Status.line = "Mencari PC..."
        Thread {
            try { work(a, rescan, onAddr) } finally { busy = false }
        }.start()
    }

    private fun work(a: Activity, rescan: Boolean, onAddr: (String) -> Unit) {
        run {
            val base = if (rescan) Net.rescan() else Net.ensureServer()
            if (base == null) {
                val name = Prefs.pcName.ifEmpty { "PC" }
                // PC lain (mis. laptop satunya) ada di Wi-Fi ini? Sarankan, tapi jangan pindah sendiri.
                val other = if (Prefs.pcId.isNotEmpty()) Net.discoverAll(1).firstOrNull { it.id.isNotEmpty() && it.id != Prefs.pcId } else null
                Status.line = when {
                    other != null -> "PC tidak ditemukan. $name tidak terjangkau. ${other.host.ifEmpty { "PC lain" }} ada di Wi-Fi ini: ketuk Ganti untuk mengirim ke sana."
                    Prefs.remoteServer.isNotEmpty() -> "PC tidak ditemukan. $name tidak terjangkau. Di luar jaringan? Nyalakan Tailscale di HP."
                    else -> "PC tidak ditemukan. Pastikan DropBridge jalan di $name dan HP satu Wi-Fi (atau hotspot) dengannya."
                }
                return
            }
            try {
                val token = Net.ensureToken(base)
                Net.folders(base, token)
                try {
                    val info = Net.getJson(base, token, "/api/info")
                    val n = info.optString("pc_name", "")
                    if (n.isNotEmpty()) Prefs.pcName = n
                    Prefs.pcVersion = info.optString("version", "")
                    info.optString("pc_id", "").let { if (it.isNotEmpty()) Prefs.pcId = it }
                    info.optString("os", "").let { if (it.isNotEmpty()) Prefs.pcOs = it }
                    Net.learnRemote(info)
                } catch (e: Exception) { /* server lama: tanpa nama PC */ }
                Pcs.saveActive()
                Status.line = "Tersambung ke PC ($base)." + if (Net.viaRemote) " Lewat internet (Tailscale)." else ""
                a.runOnUiThread { onAddr(base) }
                if (Queue.all().isNotEmpty() && !UploadService.running) {
                    a.runOnUiThread { UploadService.start(a) }
                }
            } catch (e: NetError) {
                Status.line = when {
                    e.code == 403 && e.message == Net.PENDING -> Net.PENDING
                    e.code == 401 && (e.message ?: "").startsWith("Ditolak") -> e.message ?: "Ditolak oleh PC."
                    e.code == 401 -> "PIN salah. Cek PIN di layar PC."
                    else -> "Gagal masuk: ${e.message}"
                }
            } catch (e: Exception) {
                Status.line = "Gagal terhubung: ${e.message}"
            }
        }
    }
}
