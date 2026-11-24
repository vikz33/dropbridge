package com.dropbridge.app

import java.io.IOException

/**
 * Inti pengiriman antrean. Dipakai oleh UploadService (dipicu manual/saat berbagi)
 * dan AutoSendJob (dipicu otomatis saat HP tersambung Wi-Fi).
 */
object Uploader {
    @Volatile var stop = false
    /** Minta pengiriman berhenti sebentar lalu memilih jalur ulang (file yang sedang jalan dilanjutkan dari byte terakhir). */
    @Volatile var reroute = false
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

    /** Jeda: berhenti sekarang, antrean utuh, tidak ada yang mengirim sendiri sampai Lanjut. */
    fun pause(ctx: android.content.Context) {
        Prefs.init(ctx.applicationContext); Queue.init(ctx.applicationContext)
        Prefs.paused = true
        stop = true
        AutoSend.cancel(ctx)
        Status.line = "Dijeda. ${Queue.all().size} file menunggu."
        Notify.clearProgress(ctx)
        Notify.paused(ctx, Queue.all().size)
    }

    fun resume(ctx: android.content.Context) {
        Prefs.init(ctx.applicationContext); Queue.init(ctx.applicationContext)
        Prefs.paused = false
        Notify.clearPaused(ctx)
        Status.line = "Melanjutkan..."
        if (Queue.all().isNotEmpty()) {
            if (isBusy) { stop = false } else try { UploadService.start(ctx) } catch (e: Exception) { /* dilanjutkan saat aplikasi dibuka */ }
        }
    }

    /** Cari jalur terbaik (lokal dulu, Tailscale cadangan) tanpa menyentuh antrean. */
    fun bestRoute(ctx: android.content.Context) {
        Prefs.init(ctx.applicationContext); Queue.init(ctx.applicationContext)
        Status.line = "Mencari jalur terbaik..."
        if (isBusy) { reroute = true; return }
        val app = ctx.applicationContext
        Thread {
            Net.forgetRoute()
            val b = Net.ensureServer()
            Status.line = if (b == null) "PC belum ditemukan. Antrean aman, dicoba lagi nanti."
                else if (Net.viaRemote) "Jalur: Tailscale (lewat internet)." else "Jalur: jaringan lokal."
            if (b != null && !Prefs.paused && Queue.all().isNotEmpty()) try { UploadService.start(app) } catch (e: Exception) { /* abaikan */ }
        }.start()
    }

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
        // Selama lewat Tailscale: bila jaringan lokal muncul (Wi-Fi/hotspot), pindah ke sana tanpa menunggu file selesai.
        var done = false
        Thread {
            while (!done && !stop) {
                try { Thread.sleep(10_000) } catch (e: InterruptedException) { return@Thread }
                if (done || stop || Prefs.paused) continue
                if (Net.viaRemote && Net.hasLocalNet()) {
                    val f = try { Net.localMine() } catch (e: Exception) { null }
                    if (f != null) { Prefs.server = f; reroute = true }
                }
            }
        }.also { it.isDaemon = true }.start()
        try {
            while (!stop) {
                if (Prefs.paused) {
                    Status.line = "Dijeda. ${Queue.all().size} file menunggu."
                    return false
                }
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
                Xfer.file(Queue.shown(cur), cur.size, Xfer.doneCount + items.size, Xfer.doneBefore + items.sumOf { it.size })
                var lastReport = 0L
                val dest = Queue.label(cur.folder)
                currentId = cur.id
                try {
                    val real = Net.upload(base, token, cur) { sent ->
                        if (stop) throw IOException("Dihentikan")
                        if (reroute) throw IOException("Ganti jalur")
                        val fresh = Xfer.progress(sent)
                        val now = System.currentTimeMillis()
                        if (fresh || now - lastReport > 1500) {
                            lastReport = now
                            val sp = if (Xfer.speed > 0) " · " + Xfer.rate(Xfer.speed) else ""
                            val eta = Xfer.eta().let { if (it.isEmpty()) "" else " · $it" }
                            val line = "Mengirim ${Xfer.index} dari ${Xfer.count} ke $dest: ${Xfer.percent()}%$sp$eta"
                            Status.line = line
                            report("${Queue.shown(cur)} → $dest\n$line", Xfer.percent())
                        }
                    }
                    Xfer.fileDone(cur.size)
                    Queue.remove(cur.id)
                    if (real.folder.isNotEmpty()) { Prefs.lastFolder = real.folder; Prefs.rememberTarget(real.folder) }
                    Status.line = if (real.duplicate) "Sudah ada di PC: ${real.name} → $dest (tidak dikirim ulang)"
                        else "Terkirim: ${real.name} → $dest"
                } catch (e: NetError) {
                    if (e.code == 401) {
                        Prefs.token = ""
                    } else if (e.code in 400..499 && e.code != 408 && e.code != 429) {
                        // Ditolak PC (mis. kebesaran): jangan diulang terus.
                        Queue.remove(cur.id)
                        Status.line = "Ditolak PC: ${Queue.shown(cur)} (${e.message})"
                    } else {
                        Net.markBad()
                        nap(5000)
                    }
                } catch (e: IOException) {
                    if (stop) return false
                    if (reroute) {
                        reroute = false
                        Net.forgetRoute()
                        Status.line = "Mencari jalur terbaik..."
                        report(Status.line, -1)
                    } else {
                        Net.markBad()
                        Status.line = "Koneksi terputus, mencoba lagi..."
                        nap(5000)
                    }
                } finally {
                    currentId = ""
                }
            }
            return Queue.all().isEmpty()
        } finally {
            done = true
            reroute = false
            if (began) Xfer.end()
        }
    }
}
