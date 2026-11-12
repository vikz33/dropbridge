package com.dropbridge.app

import java.io.IOException

/**
 * Inti pengiriman antrean. Dipakai oleh UploadService (dipicu manual/saat berbagi)
 * dan AutoSendJob (dipicu otomatis saat HP tersambung Wi-Fi).
 */
object Uploader {
    @Volatile var stop = false
    /** id file yang sedang dikirim; tidak boleh dipindah/dihapus dari layar Antrean. */
    @Volatile var currentId = ""
    private var busy = false

    @Synchronized
    fun acquire(): Boolean {
        if (busy) return false
        busy = true
        stop = false
        return true
    }

    @Synchronized
    fun release() { busy = false }

    val isBusy: Boolean @Synchronized get() = busy

    private fun nap(ms: Long) {
        var left = ms
        while (left > 0 && !stop) {
            val s = minOf(left, 500L)
            Thread.sleep(s)
            left -= s
        }
    }

    /**
     * Kirim sampai antrean kosong. Mengembalikan true bila antrean kosong saat berhenti.
     * [report] dipanggil dengan teks status dan persen (-1 = tanpa persen), kira-kira tiap detik.
     */
    fun run(giveUpSeconds: Int, report: (String, Int) -> Unit): Boolean {
        var waited = 0
        var began = false
        try {
            while (!stop) {
                val items = Queue.all()
                if (items.isEmpty()) {
                    Status.line = "Semua file sudah terkirim."
                    return true
                }

                val base = Net.ensureServer()
                if (base == null) {
                    Status.line = "PC belum ditemukan. ${items.size} file menunggu. Mencoba lagi..."
                    report(Status.line, -1)
                    if (waited >= giveUpSeconds) {
                        Status.line = "PC tidak ditemukan. ${items.size} file menunggu di antrean."
                        return false
                    }
                    nap(8000)
                    waited += 8
                    continue
                }
                waited = 0

                val token: String
                try {
                    token = Net.ensureToken(base)
                } catch (e: NetError) {
                    Status.line = if (e.code == 401) "PIN salah atau berubah. Buka aplikasi dan isi PIN dari layar PC." else (e.message ?: "Gagal masuk")
                    report(Status.line, -1)
                    return false
                } catch (e: IOException) {
                    nap(5000)
                    continue
                }

                if (!began) { Xfer.begin(); began = true }
                val cur = items[0]
                Xfer.file(cur.name, cur.size, Xfer.doneCount + items.size, Xfer.doneBefore + items.sumOf { it.size })
                var lastReport = 0L
                val dest = Queue.label(cur.folder)
                currentId = cur.id
                try {
                    val real = Net.upload(base, token, cur) { sent ->
                        if (stop) throw IOException("Dihentikan")
                        val fresh = Xfer.progress(sent)
                        val now = System.currentTimeMillis()
                        if (fresh || now - lastReport > 1500) {
                            lastReport = now
                            val sp = if (Xfer.speed > 0) " · " + Xfer.rate(Xfer.speed) else ""
                            val eta = Xfer.eta().let { if (it.isEmpty()) "" else " · $it" }
                            val line = "Mengirim ${Xfer.index} dari ${Xfer.count} ke $dest: ${Xfer.percent()}%$sp$eta"
                            Status.line = line
                            report("${cur.name} → $dest\n$line", Xfer.percent())
                        }
                    }
                    Xfer.fileDone(cur.size)
                    Queue.remove(cur.id)
                    if (real.folder.isNotEmpty()) Prefs.lastFolder = real.folder
                    Status.line = if (real.duplicate) "Sudah ada di PC: ${real.name} → $dest (tidak dikirim ulang)"
                        else "Terkirim: ${cur.name} → $dest"
                } catch (e: NetError) {
                    if (e.code == 401) {
                        Prefs.token = ""
                    } else if (e.code in 400..499 && e.code != 408 && e.code != 429) {
                        // Ditolak PC (mis. kebesaran): jangan diulang terus.
                        Queue.remove(cur.id)
                        Status.line = "Ditolak PC: ${cur.name} (${e.message})"
                    } else {
                        nap(5000)
                    }
                } catch (e: IOException) {
                    if (stop) return false
                    Status.line = "Koneksi terputus, mencoba lagi..."
                    nap(5000)
                } finally {
                    currentId = ""
                }
            }
            return Queue.all().isEmpty()
        } finally {
            if (began) Xfer.end()
        }
    }
}
