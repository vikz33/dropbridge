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
import java.security.SecureRandom

/** Menjaga server host tetap hidup selama layar mati. */
class HostService : Service() {

    companion object {
        private const val CHANNEL = "dropbridge_host"
        private const val NOTIF_ID = 2

        fun start(ctx: Context) {
            val i = Intent(ctx, HostService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, HostService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = notif()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIF_ID, n)

        if (Host.server == null) {
            val code = (SecureRandom().nextInt(900000) + 100000).toString()
            val s = HostServer(applicationContext, code)
            if (s.start()) Host.server = s else stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Host.server?.stop()
        Host.server = null
        Hotspot.stop()
        HostFiles.clear(applicationContext)
        super.onDestroy()
    }

    private fun notif(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Mode host", NotificationManager.IMPORTANCE_LOW))
        }
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, HostActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return b.setContentTitle("DropBridge mode host aktif")
            .setContentText("Perangkat lain bisa mengirim atau mengambil file dari HP ini.")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
