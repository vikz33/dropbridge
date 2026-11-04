package com.dropbridge.app

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

/** Notifikasi pengiriman otomatis. */
object Notify {
    private const val CH_PROGRESS = "dropbridge_auto"
    private const val CH_DONE = "dropbridge_done"
    private const val ID_PROGRESS = 2
    private const val ID_DONE = 3

    private fun nm(c: Context) = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun builder(c: Context, channel: String, name: String): Notification.Builder {
        if (Build.VERSION.SDK_INT >= 26) {
            nm(c).createNotificationChannel(NotificationChannel(channel, name, NotificationManager.IMPORTANCE_LOW))
        }
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, channel) else Notification.Builder(c)
        val pi = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return b.setContentTitle("DropBridge").setContentIntent(pi)
    }

    fun progress(c: Context, text: String, pct: Int) {
        val b = builder(c, CH_PROGRESS, "Kirim otomatis")
            .setContentText(text.substringAfterLast('\n'))
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
        if (text.contains('\n')) b.setSubText(text.substringBefore('\n'))
        if (pct >= 0) b.setProgress(100, pct, false)
        nm(c).notify(ID_PROGRESS, b.build())
    }

    fun clearProgress(c: Context) { nm(c).cancel(ID_PROGRESS) }

    fun done(c: Context, text: String) {
        val b = builder(c, CH_DONE, "Hasil pengiriman")
            .setContentText("Terkirim ke PC: $text")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setAutoCancel(true)
        nm(c).notify(ID_DONE, b.build())
    }
}

/** Penjadwal: kirim antrean sendiri begitu HP tersambung Wi-Fi dan PC menjawab. */
object AutoSend {
    private const val JOB_ID = 4711

    @Suppress("DEPRECATION")
    fun schedule(ctx: Context) {
        val app = ctx.applicationContext
        Prefs.init(app)
        Queue.init(app)
        if (!Prefs.autoSend || Queue.all().isEmpty()) return
        val js = app.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        if (js.allPendingJobs.any { it.id == JOB_ID }) return
        val info = JobInfo.Builder(JOB_ID, ComponentName(app, AutoSendJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
            .setBackoffCriteria(60_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build()
        try { js.schedule(info) } catch (e: Exception) { /* abaikan: tetap bisa kirim manual */ }
    }

    fun cancel(ctx: Context) {
        val js = ctx.applicationContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        js.cancel(JOB_ID)
    }
}

class AutoSendJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Prefs.init(this)
        Queue.init(this)
        if (!Prefs.autoSend || Queue.all().isEmpty()) return false
        if (!Uploader.acquire()) return false
        val app = applicationContext
        Thread {
            var empty = false
            try {
                empty = Uploader.run(40) { text, pct -> Notify.progress(app, text, pct) }
            } catch (e: Exception) {
                Status.line = "Berhenti: ${e.message}"
            } finally {
                Uploader.release()
                Notify.clearProgress(app)
            }
            if (Xfer.doneCount > 0) Notify.done(app, Xfer.summary.removePrefix("Terakhir: "))
            jobFinished(params, !empty && Queue.all().isNotEmpty())
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        Uploader.stop = true
        return true
    }
}
