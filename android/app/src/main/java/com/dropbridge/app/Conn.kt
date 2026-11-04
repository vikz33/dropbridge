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

    /** Cari PC (atau pakai alamat tersimpan), login, lalu kirim antrean bila ada. Hasil di Status.line. */
    fun connect(a: Activity, onAddr: (String) -> Unit = {}) {
        Status.line = "Mencari PC..."
        Thread {
            val base = Net.ensureServer()
            if (base == null) {
                Status.line = "PC tidak ditemukan. Pastikan start.bat berjalan dan HP satu Wi-Fi (atau hotspot) dengan PC. Atau isi alamat manual di Lainnya."
                return@Thread
            }
            try {
                val token = Net.ensureToken(base, true)
                Net.folders(base, token)
                Status.line = "Tersambung ke PC ($base)."
                a.runOnUiThread { onAddr(base) }
                if (Queue.all().isNotEmpty() && !UploadService.running) {
                    a.runOnUiThread { UploadService.start(a) }
                }
            } catch (e: NetError) {
                Status.line = if (e.code == 401) "PIN salah. Cek PIN di layar PC." else "Gagal masuk: ${e.message}"
            } catch (e: Exception) {
                Status.line = "Gagal terhubung: ${e.message}"
            }
        }.start()
    }
}
