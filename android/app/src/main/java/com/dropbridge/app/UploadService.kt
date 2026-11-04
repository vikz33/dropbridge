package com.dropbridge.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Mengirim antrean ke PC di latar belakang. Jika PC belum ditemukan, terus mencoba
 * (maksimal 15 menit); sesudah itu file tetap aman di antrean dan dikirim otomatis
 * saat HP tersambung Wi-Fi (AutoSendJob), atau lewat tombol Kirim antrean.
 */
class UploadService : Service() {

    companion object {
        private const val CHANNEL = "dropbridge_upload"
        private const val NOTIF_ID = 1
        private const val GIVE_UP_SECONDS = 15 * 60

        val running: Boolean get() = Uploader.isBusy

        fun start(ctx: Context) {
            val i = Intent(ctx, UploadService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Prefs.init(this)
        Queue.init(this)
        val n = notif("Menyiapkan pengiriman...", -1)
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIF_ID, n)

        if (!Uploader.acquire()) {
            // Sudah ada pengiriman lain yang berjalan (mis. dari pekerjaan otomatis).
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        val app = applicationContext
        Thread {
            var empty = false
            try {
                empty = Uploader.run(GIVE_UP_SECONDS) { text, pct -> update(text, pct) }
            } catch (e: Exception) {
                Status.line = "Berhenti: ${e.message}"
            } finally {
                Uploader.release()
                stopForeground(true)
                stopSelf()
            }
            if (Xfer.doneCount > 0) Notify.done(app, Xfer.summary.removePrefix("Terakhir: "))
            if (!empty) AutoSend.schedule(app)
        }.start()
        return START_NOT_STICKY
    }

    private fun notif(text: String, pct: Int): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Pengiriman file", NotificationManager.IMPORTANCE_LOW))
        }
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        b.setContentTitle("DropBridge")
            .setContentText(text.substringAfterLast('\n'))
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(pi)
            .setOngoing(true)
        if (text.contains('\n')) b.setSubText(text.substringBefore('\n'))
        if (pct >= 0) b.setProgress(100, pct, false)
        return b.build()
    }

    private fun update(text: String, pct: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, notif(text, pct))
    }
}
