package com.dropbridge.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper

/** Tombol di notifikasi: Jeda, Lanjut, Cari jalur terbaik. */
class QueueActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext
        Prefs.init(app); Queue.init(app)
        when (intent.action) {
            Notify.ACT_PAUSE -> Uploader.pause(app)
            Notify.ACT_RESUME -> Uploader.resume(app)
            Notify.ACT_ROUTE -> Uploader.bestRoute(app)
        }
    }
}

/**
 * Selama aplikasi terbuka: begitu HP masuk jaringan lokal (Wi-Fi atau hotspot menyala), antrean langsung dikirim,
 * atau pengiriman yang sedang lewat Tailscale dipindah ke jalur lokal. Di latar belakang tugas otomatis (AutoSend) yang bekerja.
 */
object NetWatch {
    private var cb: ConnectivityManager.NetworkCallback? = null
    private var rx: BroadcastReceiver? = null
    private val h = Handler(Looper.getMainLooper())

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        if (cb == null) {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { changed(app) }
            }
            try {
                if (Build.VERSION.SDK_INT >= 24) cm.registerDefaultNetworkCallback(callback)
                else cm.registerNetworkCallback(NetworkRequest.Builder().addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI).build(), callback)
                cb = callback
            } catch (e: Exception) { /* tanpa pantauan: AutoSend tetap bekerja */ }
        }
        if (rx == null) {
            val r = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) { changed(app) }
            }
            try {
                val f = IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED")
                if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(r, f, Context.RECEIVER_EXPORTED) else app.registerReceiver(r, f)
                rx = r
            } catch (e: Exception) { /* abaikan */ }
        }
    }

    fun stop(ctx: Context) {
        val app = ctx.applicationContext
        h.removeCallbacksAndMessages(null)
        cb?.let { try { (app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(it) } catch (e: Exception) { /* sudah lepas */ } }
        cb = null
        rx?.let { try { app.unregisterReceiver(it) } catch (e: Exception) { /* sudah lepas */ } }
        rx = null
    }

    private fun changed(app: Context) {
        h.removeCallbacksAndMessages(null)
        h.postDelayed({ Thread { react(app) }.start() }, 3000) // beri waktu alamat IP terbentuk
    }

    private fun react(app: Context) {
        Prefs.init(app); Queue.init(app)
        if (Prefs.paused || !Prefs.autoSend || Queue.all().isEmpty() || !Net.hasLocalNet()) return
        if (Uploader.isBusy) {
            if (Net.viaRemote) Net.localMine()?.let { Prefs.server = it; Uploader.reroute = true }
        } else {
            try { UploadService.start(app) } catch (e: Exception) { /* dikirim saat aplikasi dibuka */ }
        }
    }
}
