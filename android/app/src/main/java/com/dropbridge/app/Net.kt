package com.dropbridge.app

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue

class NetError(val code: Int, message: String) : IOException(message)

object Net {
    const val UDP_PORT = 47800
    const val DEFAULT_PORT = 8000

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun detail(text: String, fallback: String): String = try {
        val d = JSONObject(text).optString("detail", "")
        if (d.isNotEmpty()) d else fallback
    } catch (e: Exception) { fallback }

    private fun request(method: String, url: String, token: String?, body: String?, timeoutMs: Int): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return Pair(code, text)
        } finally {
            c.disconnect()
        }
    }

    // ---------- Menemukan PC ----------

    private fun probe(base: String, timeoutMs: Int): Boolean = try {
        val r = request("GET", "$base/api/info", null, null, timeoutMs)
        r.first == 200 && JSONObject(r.second).optString("name") == "DropBridge"
    } catch (e: Exception) { false }

    private class Local(val name: String, val ip: String, val bcast: InetAddress?, val prefix: Int)

    private fun locals(): List<Local> {
        val out = ArrayList<Local>()
        val en = try { NetworkInterface.getNetworkInterfaces() } catch (e: Exception) { null } ?: return out
        for (ni in Collections.list(en)) {
            try {
                if (!ni.isUp || ni.isLoopback) continue
                val n = ni.name.lowercase()
                if (n.startsWith("rmnet") || n.startsWith("ccmni") || n.startsWith("tun") || n.startsWith("dummy") || n.startsWith("v4-") || n.startsWith("clat")) continue
                for (ia in ni.interfaceAddresses) {
                    val a = ia.address
                    if (a is Inet4Address && !a.isLinkLocalAddress) {
                        val host = a.hostAddress
                        if (host != null) out.add(Local(n, host, ia.broadcast, ia.networkPrefixLength.toInt()))
                    }
                }
            } catch (e: Exception) { /* lewati antarmuka ini */ }
        }
        return out
    }

    /** Alamat IP HP ini di jaringan lokal; yang tampak seperti hotspot (ap/softap) diurutkan lebih dulu. */
    fun lanIps(): List<String> {
        val all = locals()
        val hot = all.filter { it.name.startsWith("ap") || it.name.startsWith("swlan") || it.name.startsWith("softap") }
        return (hot + all.filter { !hot.contains(it) }).map { it.ip }.distinct()
    }

    /** Cara cepat: kirim siaran UDP, PC yang menjalankan DropBridge membalas. */
    private fun udpDiscover(): String? {
        val targets = ArrayList<InetAddress>()
        try { targets.add(InetAddress.getByName("255.255.255.255")) } catch (e: Exception) { /* abaikan */ }
        for (l in locals()) { val b = l.bcast; if (b != null) targets.add(b) }
        val sock = try { DatagramSocket() } catch (e: Exception) { return null }
        try {
            sock.broadcast = true
            sock.soTimeout = 400
            val msg = "DROPBRIDGE?".toByteArray()
            val buf = ByteArray(512)
            for (round in 0 until 3) {
                for (t in targets) {
                    try { sock.send(DatagramPacket(msg, msg.size, t, UDP_PORT)) } catch (e: Exception) { /* abaikan */ }
                }
                val end = System.currentTimeMillis() + 700
                while (System.currentTimeMillis() < end) {
                    val p = DatagramPacket(buf, buf.size)
                    try { sock.receive(p) } catch (e: SocketTimeoutException) { break }
                    try {
                        val j = JSONObject(String(p.data, 0, p.length))
                        if (j.optString("name") == "DropBridge") {
                            return "http://${p.address.hostAddress}:${j.optInt("port", DEFAULT_PORT)}"
                        }
                    } catch (e: Exception) { /* bukan balasan kita */ }
                }
            }
        } catch (e: Exception) { /* jatuh ke pemindaian */ } finally {
            sock.close()
        }
        return null
    }

    /** Cadangan: periksa semua alamat di jaringan yang sama (port 8000). */
    private fun scan(): String? {
        val bases = LinkedHashSet<String>()
        for (l in locals()) {
            if (l.prefix < 16) continue
            val p = l.ip.split(".")
            if (p.size == 4) bases.add("${p[0]}.${p[1]}.${p[2]}")
        }
        if (bases.isEmpty()) return null
        val pool = Executors.newFixedThreadPool(48)
        val found = LinkedBlockingQueue<String>()
        try {
            val futures = ArrayList<Future<*>>()
            for (b in bases) {
                for (i in 1..254) {
                    val url = "http://$b.$i:$DEFAULT_PORT"
                    futures.add(pool.submit(Runnable {
                        if (found.isEmpty() && probe(url, 500)) found.offer(url)
                    }))
                }
            }
            val end = System.currentTimeMillis() + 12000
            while (System.currentTimeMillis() < end && found.isEmpty() && futures.any { !it.isDone }) {
                Thread.sleep(100)
            }
            return found.peek()
        } finally {
            pool.shutdownNow()
        }
    }

    fun discover(): String? = udpDiscover() ?: scan()

    /** Alamat PC yang bisa dipakai sekarang: yang tersimpan jika masih hidup, jika tidak cari ulang. */
    @Synchronized
    fun ensureServer(): String? {
        val saved = Prefs.server
        if (saved.isNotEmpty() && probe(saved, 1500)) return saved
        val found = discover() ?: return null
        Prefs.server = found
        return found
    }

    // ---------- Login dan data ----------

    /** Token yang sah. Memakai token tersimpan; jika kedaluwarsa, login ulang dengan PIN tersimpan. */
    @Synchronized
    fun ensureToken(base: String, force: Boolean = false): String {
        if (!force) {
            val t = Prefs.token
            if (t.isNotEmpty()) {
                val r = request("GET", "$base/api/session", t, null, 4000)
                if (r.first == 200) return t
            }
        }
        val pin = Prefs.pin
        if (pin.isEmpty()) throw NetError(401, "Belum ada PIN. Isi PIN dari layar PC.")
        val r = request("POST", "$base/api/login", null, JSONObject().put("pin", pin).toString(), 6000)
        if (r.first == 200) {
            val t = JSONObject(r.second).optString("token", "")
            if (t.isNotEmpty()) {
                Prefs.token = t
                return t
            }
        }
        throw NetError(r.first, detail(r.second, "Login gagal (${r.first})"))
    }

    fun getJson(base: String, token: String, path: String): JSONObject {
        val r = request("GET", base + path, token, null, 8000)
        if (r.first != 200) throw NetError(r.first, detail(r.second, "Gagal (${r.first})"))
        return JSONObject(r.second)
    }

    fun folders(base: String, token: String): List<String> {
        val r = request("GET", "$base/api/folders", token, null, 6000)
        if (r.first != 200) throw NetError(r.first, detail(r.second, "Gagal mengambil folder"))
        val arr = JSONObject(r.second).getJSONArray("folders")
        val names = ArrayList<String>()
        for (i in 0 until arr.length()) names.add(arr.getJSONObject(i).getString("name"))
        Prefs.folders = names
        return names
    }

    fun sendText(base: String, token: String, text: String) {
        val body = JSONObject().put("text", text).put("source", "hp").toString()
        val r = request("POST", "$base/api/clips", token, body, 8000)
        if (r.first !in 200..299) throw NetError(r.first, detail(r.second, "Gagal mengirim teks (${r.first})"))
    }

    /** Kirim satu file mentah ke PC. Mengembalikan nama folder final dari server. */
    fun upload(base: String, token: String, item: Item, onProgress: (Long) -> Unit): String {
        val f = File(item.path)
        var url = "$base/api/files/upload-raw?folder=${enc(item.folder)}&name=${enc(item.name)}"
        if (item.modified > 0) url += "&last_modified=${item.modified}"
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 5000
            c.readTimeout = 60000
            c.setFixedLengthStreamingMode(f.length())
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Content-Type", "application/octet-stream")
            f.inputStream().use { ins ->
                c.outputStream.use { out ->
                    val buf = ByteArray(64 * 1024)
                    var sent = 0L
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        sent += n
                        onProgress(sent)
                    }
                }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw NetError(code, detail(text, "Gagal ($code)"))
            return JSONObject(text).optString("folder", item.folder)
        } finally {
            c.disconnect()
        }
    }
}
