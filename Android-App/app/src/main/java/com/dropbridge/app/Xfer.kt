package com.dropbridge.app

import java.io.FilterInputStream
import java.io.InputStream
import java.util.Locale

/** Penghitung kecepatan yang dihaluskan; update() true bila angka baru siap ditampilkan (sekitar tiap 0,7 detik). */
class Rate {
    @Volatile var bps = 0.0
    private var lastT = 0L
    private var lastB = 0L

    fun reset() { bps = 0.0; lastT = 0L; lastB = 0L }

    fun update(total: Long): Boolean {
        val now = System.nanoTime() / 1_000_000
        if (lastT == 0L) { lastT = now; lastB = total; return false }
        val dt = now - lastT
        if (dt < 700) return false
        val inst = (total - lastB) * 1000.0 / dt
        bps = if (bps <= 0.0) inst else bps * 0.6 + inst * 0.4
        lastT = now
        lastB = total
        return true
    }
}

/** Aliran yang melapor jumlah byte terbaca. */
class CountingInputStream(ins: InputStream, private val onBytes: (Long) -> Unit) : FilterInputStream(ins) {
    private var total = 0L

    override fun read(): Int {
        val r = super.read()
        if (r >= 0) { total++; onBytes(total) }
        return r
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        if (n > 0) { total += n; onBytes(total) }
        return n
    }
}

/** Keadaan pengiriman yang sedang berjalan: dibaca layar utama dan notifikasi. */
object Xfer {
    @Volatile var active = false
    @Volatile var name = ""
    @Volatile var index = 0
    @Volatile var count = 0
    @Volatile var fileSent = 0L
    @Volatile var fileSize = 0L
    @Volatile var doneBefore = 0L
    @Volatile var doneCount = 0
    @Volatile var totalBytes = 0L
    @Volatile var summary = ""
    private val rate = Rate()
    private var startMs = 0L

    val speed: Double get() = rate.bps
    val sentTotal: Long get() = doneBefore + fileSent

    fun begin() {
        active = true
        doneBefore = 0L
        doneCount = 0
        fileSent = 0L
        rate.reset()
        startMs = System.currentTimeMillis()
    }

    fun file(name: String, size: Long, count: Int, totalBytes: Long) {
        this.name = name
        this.fileSize = size
        this.fileSent = 0L
        this.index = doneCount + 1
        this.count = count
        this.totalBytes = totalBytes
    }

    /** Mengembalikan true saat kecepatan baru dihitung. */
    fun progress(sent: Long): Boolean {
        fileSent = sent
        return rate.update(doneBefore + sent)
    }

    fun fileDone(size: Long) {
        doneBefore += size
        doneCount++
        fileSent = 0L
    }

    fun end() {
        active = false
        val ms = (System.currentTimeMillis() - startMs).coerceAtLeast(1)
        if (doneCount > 0) {
            summary = "Terakhir: $doneCount file, ${size(doneBefore)}, rata-rata ${rate(doneBefore * 1000.0 / ms)}"
        }
        rate.reset()
    }

    fun percent(): Int {
        val t = totalBytes
        return if (t <= 0) 0 else (sentTotal * 100 / t).toInt().coerceIn(0, 100)
    }

    fun eta(): String {
        val s = speed
        if (s < 1024) return ""
        val sec = ((totalBytes - sentTotal) / s).toLong().coerceAtLeast(0)
        return "sisa " + duration(sec)
    }

    fun duration(sec: Long): String =
        if (sec < 60) "$sec dtk" else "${sec / 60} mnt ${sec % 60} dtk"

    fun size(b: Long): String {
        if (b < 1024) return "$b B"
        var v = b.toDouble()
        val units = arrayOf("KB", "MB", "GB", "TB")
        var i = -1
        do { v /= 1024; i++ } while (v >= 1024 && i < units.size - 1)
        return String.format(Locale("in", "ID"), "%.1f %s", v, units[i])
    }

    fun rate(bps: Double): String =
        if (bps >= 1024 * 1024) String.format(Locale("in", "ID"), "%.1f MB/s", bps / (1024 * 1024))
        else String.format(Locale("in", "ID"), "%d KB/s", (bps / 1024).toInt())
}
