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
import java.io.IOException

/**
 * Mengirim antrean ke PC di latar belakang. Jika PC belum ditemukan, terus mencoba
 * (maksimal 15 menit); file tetap aman di antrean dan bisa dikirim lagi dari layar utama.
 */
class UploadService : Service() {

    companion object {
        private const val CHANNEL = "dropbridge_upload"
        private const val NOTIF_ID = 1
        private const val GIVE_UP_SECONDS = 15 * 60

        @Volatile
        var running = false

        fun start(ctx: Context) {
            val i = Intent(ctx, UploadService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Prefs.init(this)
        Queue.init(this)
        val n = notif("Menyiapkan pengiriman...")
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIF_ID, n)

        synchronized(this) {
            if (!running) {
                running = true
                Thread {
                    try {
                        loop()
                    } catch (e: Exception) {
                        Status.line = "Berhenti: ${e.message}"
                    } finally {
                        running = false
                        stopForeground(true)
                        stopSelf()
                    }
                }.start()
            }
        }
        return START_NOT_STICKY
    }

    private fun notif(text: String): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Pengiriman file", NotificationManager.IMPORTANCE_LOW))
        }
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return b.setContentTitle("DropBridge")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun update(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, notif(text))
    }

    private fun loop() {
        var waited = 0
        while (true) {
            val items = Queue.all()
            if (items.isEmpty()) {
                Status.line = "Semua file sudah terkirim."
                return
            }

            val base = Net.ensureServer()
            if (base == null) {
                Status.line = "PC belum ditemukan. ${items.size} file menunggu. Mencoba lagi..."
                update(Status.line)
                if (waited >= GIVE_UP_SECONDS) {
                    Status.line = "PC tidak ditemukan. ${items.size} file menunggu di antrean (buka aplikasi lalu ketuk Kirim antrean)."
                    return
                }
                Thread.sleep(8000)
                waited += 8
                continue
            }
            waited = 0

            val token: String
            try {
                token = Net.ensureToken(base)
            } catch (e: NetError) {
                Status.line = if (e.code == 401) "PIN salah atau berubah. Buka aplikasi dan isi PIN dari layar PC." else (e.message ?: "Gagal masuk")
                update(Status.line)
                return
            } catch (e: IOException) {
                Thread.sleep(5000)
                continue
            }

            val cur = items[0]
            val label = "${cur.name} → ${cur.folder} (${items.size} file)"
            update("Mengirim $label")
            try {
                val real = Net.upload(base, token, cur) { sent ->
                    val pct = if (cur.size > 0) (sent * 100 / cur.size).toInt() else 100
                    Status.line = "Mengirim $label: $pct%"
                }
                Queue.remove(cur.id)
                Prefs.lastFolder = real
                Status.line = "Terkirim: ${cur.name}"
            } catch (e: NetError) {
                if (e.code == 401) {
                    Prefs.token = ""
                } else if (e.code in 400..499 && e.code != 408 && e.code != 429) {
                    // Ditolak PC (mis. kebesaran): jangan diulang terus.
                    Queue.remove(cur.id)
                    Status.line = "Ditolak PC: ${cur.name} (${e.message})"
                } else {
                    Thread.sleep(5000)
                }
            } catch (e: IOException) {
                Status.line = "Koneksi terputus, mencoba lagi..."
                Thread.sleep(5000)
            }
        }
    }
}
