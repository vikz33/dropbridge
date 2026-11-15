package com.dropbridge.app

import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import java.net.HttpURLConnection
import java.net.URL

/**
 * Kiriman laptop → HP: laptop menaruh file di kotak kiriman untuk HP ini; aplikasi mengambilnya sendiri.
 * Saat aplikasi terbuka dicek tiap ±10 detik, di latar belakang tiap ±15 menit (InboxJob).
 * File tersimpan di Download/DropBridge (foto ke Galeri) lalu ada notifikasi "3 file dari laptop-acer".
 */
object Inbox {
    /** Kabar terakhir untuk Home: "3 file dari laptop-acer" (kosong = tidak ada). */
    @Volatile var lastNote = ""
    @Volatile var lastNoteAt = 0L
    @Volatile private var busy = false
    private var lastCheck = 0L

    /** Dari layar yang terbuka: cek bila sudah lewat [everyMs]. Berjalan di thread sendiri. */
    fun maybeCheck(c: Context, everyMs: Long = 10_000) {
        if (busy || !Prefs.receiveFromPc || Prefs.pin.isEmpty()) return
        val now = System.currentTimeMillis()
        if (now - lastCheck < everyMs) return
        lastCheck = now
        val app = c.applicationContext
        Thread { try { check(app) } catch (e: Exception) { /* coba lagi nanti */ } }.start()
    }

    /** Ambil semua kiriman yang menunggu. Mengembalikan jumlah file yang tersimpan. */
    fun check(c: Context): Int {
        if (busy) return 0
        busy = true
        try {
            val base = Net.ensureServer() ?: return 0
            val token = Net.ensureToken(base)
            val items = Net.getJson(base, token, "/api/outbox").optJSONArray("items") ?: return 0
            if (items.length() == 0) return 0
            var got = 0
            var from = ""
            var oneName = ""
            var oneUri: android.net.Uri? = null
            for (i in 0 until items.length()) {
                val o = items.getJSONObject(i)
                val id = o.getString("id")
                val name = o.optString("name", "file")
                from = o.optString("from").ifEmpty { "laptop" }
                val conn = URL("$base/api/outbox/$id/file").openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 60000
                conn.setRequestProperty("Authorization", "Bearer $token")
                try {
                    if (conn.responseCode != 200) continue
                    val (saved, uri) = FileSaver.saveUri(c, "DropBridge", name, conn.contentLengthLong, conn.inputStream)
                    oneName = saved
                    oneUri = uri
                    got++
                } finally { conn.disconnect() }
                // Baru dianggap diterima setelah tersimpan di HP
                try { Net.postJson(base, token, "/api/outbox/$id/done") } catch (e: Exception) { /* laptop menandai saat cek berikutnya */ }
            }
            if (got > 0) {
                lastNote = if (got == 1) "$oneName dari $from" else "$got file dari $from"
                lastNoteAt = System.currentTimeMillis()
                notify(c, lastNote, if (got == 1) oneUri else null, oneName)
            }
            return got
        } finally { busy = false }
    }

    private fun notify(c: Context, text: String, uri: android.net.Uri?, name: String) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("dropbridge_inbox", "File dari laptop", NotificationManager.IMPORTANCE_DEFAULT))
        // Satu file: buka langsung (APK = pasang). Lebih dari satu: buka Unduhan.
        val open = if (uri != null) Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, if (name.lowercase().endsWith(".apk")) "application/vnd.android.package-archive" else FileSaver.mimeOf(name))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        } else Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(c, 7, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, "dropbridge_inbox") else Notification.Builder(c)
        b.setContentTitle(text).setContentText("Tersimpan di Download/DropBridge · ketuk untuk membuka")
            .setSmallIcon(android.R.drawable.stat_sys_download_done).setContentIntent(pi).setAutoCancel(true)
        nm.notify(8, b.build())
    }

    // ---------- Latar belakang ----------
    private const val JOB_ID = 4712

    @Suppress("DEPRECATION")
    fun schedule(ctx: Context) {
        val app = ctx.applicationContext
        val js = app.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        if (!Prefs.receiveFromPc) { js.cancel(JOB_ID); return }
        if (js.allPendingJobs.any { it.id == JOB_ID }) return
        val info = JobInfo.Builder(JOB_ID, ComponentName(app, InboxJob::class.java))
            .setRequiredNetworkType(if (Prefs.allowMobile && Prefs.remoteServer.isNotEmpty()) JobInfo.NETWORK_TYPE_ANY else JobInfo.NETWORK_TYPE_UNMETERED)
            .setPeriodic(15 * 60 * 1000L)
            .setPersisted(false)
            .build()
        try { js.schedule(info) } catch (e: Exception) { /* tetap dicek saat aplikasi dibuka */ }
    }
}

class InboxJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Prefs.init(this)
        if (!Prefs.receiveFromPc || Prefs.pin.isEmpty()) return false
        val app = applicationContext
        Thread {
            try { Inbox.check(app) } catch (e: Exception) { /* coba lagi di jadwal berikutnya */ }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
