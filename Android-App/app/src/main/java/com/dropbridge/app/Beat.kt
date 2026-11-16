package com.dropbridge.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cek denyut ke laptop tujuan selama layar aplikasi terlihat: tiap 10 detik.
 * Gagal 2 kali berturut-turut = "tidak terjangkau · terakhir HH.mm". Bila laptop kembali, status pulih sendiri.
 */
object Beat {
    @Volatile var lastOk = 0L
        private set
    @Volatile var fails = 0
        private set
    @Volatile private var lastTry = 0L
    @Volatile private var busy = false

    /** Laptop tujuan sedang terjangkau (bukti baru dari denyut atau sambungan terakhir berhasil). */
    fun alive(): Boolean {
        if (Prefs.pin.isEmpty() || Status.line == Net.PENDING || fails >= 2) return false
        return System.currentTimeMillis() - lastOk < 25_000 || Status.line.startsWith("Tersambung")
    }

    fun ok() { lastOk = System.currentTimeMillis(); fails = 0 }
    fun lost() { fails = 2 }
    /** Cek secepatnya (tombol Muat ulang, Cari ulang, layar dibuka lagi). */
    fun kick() { lastTry = 0 }
    fun reset() { lastOk = 0; fails = 0; lastTry = 0 }

    /** " · terakhir 22.10" (kosong bila belum pernah tersambung). */
    fun lastSeen(): String =
        if (lastOk == 0L) "" else " · terakhir " + SimpleDateFormat("HH.mm", Locale.getDefault()).format(Date(lastOk))

    /** Dipanggil tiap detik oleh layar yang terlihat. */
    fun tick() {
        if (busy || Conn.busy || Prefs.pin.isEmpty() || Status.line == Net.PENDING) return
        val now = System.currentTimeMillis()
        // Saat terputus, cari ulang (termasuk alamat baru / Tailscale) lebih jarang: tiap 20 detik
        if (now - lastTry < if (fails >= 2) 20_000 else 10_000) return
        lastTry = now
        busy = true
        Thread {
            try {
                val base = if (fails >= 2) Net.ensureServer() else Net.current.ifEmpty { Prefs.server }.ifEmpty { null }
                val up = base != null && (fails >= 2 || Net.alive(base))
                if (up) {
                    val wasLost = fails >= 2
                    ok()
                    val l = Status.line
                    if (wasLost || l.startsWith("PC tidak") || l.startsWith("Gagal terhubung") || l.startsWith("Mencari")) {
                        Status.line = "Tersambung ke PC ($base)." + if (Net.viaRemote) " Lewat internet (Tailscale)." else ""
                    }
                } else {
                    fails++
                    if (fails >= 2 && Status.line.startsWith("Tersambung")) Status.line = "PC tidak terjangkau."
                }
            } catch (e: Exception) {
                fails++
            } finally {
                busy = false
            }
        }.start()
    }
}
