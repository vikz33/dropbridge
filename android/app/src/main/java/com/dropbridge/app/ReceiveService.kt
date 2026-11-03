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
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Terima otomatis: terus memeriksa daftar file yang dibagikan HP host dan menyimpan
 * file baru ke Download/DropBridge tanpa perlu ketuk apa pun.
 */
class ReceiveService : Service() {

    companion object {
        private const val CHANNEL = "dropbridge_receive"
        private const val NOTIF_ID = 3

        @Volatile var running = false
        @Volatile var status = ""
        @Volatile var count = 0

        fun start(ctx: Context, base: String, code: String) {
            val i = Intent(ctx, ReceiveService::class.java).putExtra("base", base).putExtra("code", code)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, ReceiveService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val base = intent?.getStringExtra("base") ?: ""
        val code = intent?.getStringExtra("code") ?: ""
        val n = notif("Menunggu file dari host...")
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIF_ID, n)

        if (base.isEmpty() || code.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        synchronized(this) {
            if (!running) {
                running = true
                count = 0
                status = "Menyambung ke host..."
                Thread {
                    try {
                        loop(base, code)
                    } catch (e: Exception) {
                        status = "Berhenti: ${e.message}"
                    } finally {
                        running = false
                        stopSelf()
                    }
                }.start()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    private fun notif(text: String): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Terima otomatis", NotificationManager.IMPORTANCE_LOW))
        }
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, ReceiveActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return b.setContentTitle("DropBridge terima otomatis")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun update(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, notif(text))
    }

    private fun open(url: String, timeout: Int): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 4000
        c.readTimeout = timeout
        return c
    }

    private fun loop(base: String, code: String) {
        val done = HashSet<String>()
        while (running) {
            try {
                val c = open("$base/api/list?k=$code", 6000)
                val http = c.responseCode
                if (http == 401 || http == 429) {
                    status = if (http == 401) "Kode salah. Cek kode di layar HP host." else "Terlalu banyak percobaan. Tunggu semenit."
                    update(status)
                    c.disconnect()
                    return
                }
                val text = c.inputStream.bufferedReader().use { it.readText() }
                c.disconnect()
                val arr = JSONObject(text).getJSONArray("files")
                for (i in 0 until arr.length()) {
                    if (!running) return
                    val o = arr.getJSONObject(i)
                    val id = o.getInt("id")
                    val name = o.getString("name")
                    val key = "$id|$name"
                    if (done.contains(key)) continue
                    status = "Menerima $name..."
                    update(status)
                    val d = open("$base/dl/$id?k=$code", 60000)
                    try {
                        if (d.responseCode != 200) throw IOException("HTTP ${d.responseCode}")
                        val len = d.contentLengthLong
                        FileSaver.save(applicationContext, "DropBridge", name, len, d.inputStream)
                    } finally {
                        d.disconnect()
                    }
                    done.add(key)
                    count++
                }
                status = "Menunggu file baru dari host. Diterima: $count file."
                update(status)
            } catch (e: IOException) {
                status = "Tidak terhubung ke host, mencoba lagi..."
            }
            Thread.sleep(3000)
        }
    }
}
