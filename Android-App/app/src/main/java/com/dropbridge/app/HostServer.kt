package com.dropbridge.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/** Penyimpan tunggal server host yang sedang berjalan, dibaca layar dan layanan. */
object Host {
    @Volatile var server: HostServer? = null
}

class Shared(val uri: Uri, val name: String, val size: Long) {
    @Volatile var taken = 0
}

/** File yang diterima dari HP lain, untuk daftar di aplikasi. */
class Received(val name: String, val size: Long, val time: Long) {
    val mime: String get() = FileSaver.mimeOf(name)
    /** Lokasi simpan: foto ke Galeri (Pictures), video ke Movies, lainnya ke Download. */
    val where: String get() = when {
        mime.startsWith("image/") -> "Galeri › DropBridge"
        mime.startsWith("video/") -> "Galeri (Movies) › DropBridge"
        else -> "Download › DropBridge"
    }
}

/**
 * Server web mini di HP: perangkat lain (iPhone, HP apa pun) membuka alamatnya di browser,
 * memasukkan kode 6 angka, lalu mengirim file ke HP ini atau mengambil file yang dibagikan.
 */
class HostServer(private val ctx: Context, val code: String) {
    val shared = CopyOnWriteArrayList<Shared>()
    @Volatile var received = 0
    val receivedList = CopyOnWriteArrayList<Received>()
    @Volatile var port = 0
    @Volatile var lastEvent = ""

    private var server: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()
    private val fails = ConcurrentHashMap<String, LongArray>() // ip -> [jumlah gagal, waktu kunci selesai]

    fun start(): Boolean {
        for (p in 8080..8090) {
            try {
                server = ServerSocket(p)
                port = p
                break
            } catch (e: IOException) { /* coba port lain */ }
        }
        val s = server ?: return false
        pool.execute {
            while (!s.isClosed) {
                try {
                    val sock = s.accept()
                    pool.execute { handle(sock) }
                } catch (e: IOException) {
                    break
                }
            }
        }
        return true
    }

    fun stop() {
        try { server?.close() } catch (e: IOException) { /* abaikan */ }
        pool.shutdownNow()
    }

    fun addShared(uri: Uri) {
        var name = "file"
        var size = -1L
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    if (!c.isNull(0)) name = c.getString(0)
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        } catch (e: Exception) { /* pakai nilai awal */ }
        shared.add(Shared(uri, name, size))
    }

    /** File salinan di cache (dari menu Bagikan). */
    fun addSharedFile(f: java.io.File) {
        if (f.isFile) shared.add(Shared(Uri.fromFile(f), f.name, f.length()))
    }

    // ---------- HTTP ----------

    private fun readHead(input: InputStream): String? {
        val sb = StringBuilder()
        var state = 0 // cocokkan \r\n\r\n
        while (sb.length < 16384) {
            val b = input.read()
            if (b < 0) return null
            sb.append(b.toChar())
            state = when {
                b == 13 && (state == 0 || state == 2) -> state + 1
                b == 10 && (state == 1 || state == 3) -> state + 1
                b == 13 -> 1
                else -> 0
            }
            if (state == 4) return sb.toString()
        }
        return null
    }

    private fun respond(out: OutputStream, status: Int, ctype: String, body: ByteArray) {
        val text = when (status) {
            200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 404 -> "Not Found"; 429 -> "Too Many Requests"; else -> "Error"
        }
        val head = "HTTP/1.1 $status $text\r\nContent-Type: $ctype\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray())
        out.write(body)
        out.flush()
    }

    private fun json(out: OutputStream, status: Int, o: JSONObject) =
        respond(out, status, "application/json; charset=utf-8", o.toString().toByteArray())

    private fun queryOf(q: String): Map<String, String> {
        val m = HashMap<String, String>()
        if (q.isEmpty()) return m
        for (part in q.split("&")) {
            val i = part.indexOf('=')
            try {
                if (i < 0) m[URLDecoder.decode(part, "UTF-8")] = ""
                else m[URLDecoder.decode(part.substring(0, i), "UTF-8")] = URLDecoder.decode(part.substring(i + 1), "UTF-8")
            } catch (e: Exception) { /* abaikan parameter rusak */ }
        }
        return m
    }

    /** 0 = sah, 401 = salah, 429 = dikunci sementara. */
    private fun check(ip: String, k: String?): Int {
        val now = System.currentTimeMillis()
        val f = fails.getOrPut(ip) { LongArray(2) }
        synchronized(f) {
            if (f[1] > now) return 429
            if (k != null && k.length == code.length && k == code) {
                f[0] = 0
                return 0
            }
            if (k == null) return 401 // tanpa kode (mis. favicon): tolak, tapi jangan hitung sebagai tebakan
            f[0]++
            if (f[0] >= 5) {
                f[0] = 0
                f[1] = now + 60_000
            }
            return 401
        }
    }

    private fun handle(sock: Socket) {
        try {
            sock.soTimeout = 30000
            val input = sock.getInputStream().buffered(65536)
            val out = sock.getOutputStream()
            val head = readHead(input) ?: return
            val lines = head.split("\r\n")
            val first = lines[0].split(" ")
            if (first.size < 2) return
            val method = first[0]
            val target = first[1]
            val headers = HashMap<String, String>()
            for (l in lines.drop(1)) {
                val i = l.indexOf(':')
                if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
            }
            val qi = target.indexOf('?')
            val path = if (qi < 0) target else target.substring(0, qi)
            val q = queryOf(if (qi < 0) "" else target.substring(qi + 1))
            val ip = sock.inetAddress.hostAddress ?: "?"

            if (method == "GET" && path == "/") {
                val html = ctx.assets.open("host.html").use { it.readBytes() }
                respond(out, 200, "text/html; charset=utf-8", html)
                return
            }

            if (path == "/favicon.ico" || path.startsWith("/apple-touch-icon")) {
                respond(out, 404, "text/plain", ByteArray(0))
                return
            }

            // Selain halaman utama, semua butuh kode
            val auth = check(ip, q["k"] ?: headers["x-code"])
            if (auth != 0) {
                json(out, auth, JSONObject().put("detail", if (auth == 429) "Terlalu banyak percobaan" else "Kode salah"))
                return
            }

            when {
                method == "GET" && path == "/api/ping" ->
                    json(out, 200, JSONObject().put("ok", true).put("name", Net.deviceName()))

                method == "GET" && path == "/api/list" -> {
                    val arr = JSONArray()
                    for ((i, s) in shared.withIndex()) {
                        val mime = FileSaver.mimeOf(s.name)
                        arr.put(JSONObject().put("id", i).put("name", s.name).put("size", s.size)
                            .put("img", mime.startsWith("image/") && !mime.contains("heic")))
                    }
                    json(out, 200, JSONObject().put("files", arr))
                }

                method == "GET" && path.startsWith("/dl/") -> {
                    val id = path.substring(4).toIntOrNull()
                    val s = if (id != null && id >= 0 && id < shared.size) shared[id] else null
                    if (s == null) {
                        json(out, 404, JSONObject().put("detail", "Tidak ada"))
                        return
                    }
                    val ins = ctx.contentResolver.openInputStream(s.uri)
                    if (ins == null) {
                        json(out, 404, JSONObject().put("detail", "File tidak bisa dibuka"))
                        return
                    }
                    ins.use {
                        val len = if (s.size >= 0) "Content-Length: ${s.size}\r\n" else ""
                        val fname = URLEncoder.encode(s.name, "UTF-8").replace("+", "%20")
                        // inline=1: untuk grid foto (tekan lama, Tambahkan ke Foto); selain itu unduhan biasa
                        val inline = q["inline"] == "1"
                        val disp = if (inline) "inline" else "attachment"
                        val h = "HTTP/1.1 200 OK\r\nContent-Type: ${FileSaver.mimeOf(s.name)}\r\n$len" +
                            "Content-Disposition: $disp; filename*=UTF-8''$fname\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
                        out.write(h.toByteArray())
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = it.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                        }
                        out.flush()
                    }
                    if (q["inline"] != "1") {
                        s.taken++
                        lastEvent = "Diambil: ${s.name}"
                    }
                }

                method == "GET" && path == "/dlzip" -> {
                    if (shared.isEmpty()) {
                        json(out, 404, JSONObject().put("detail", "Belum ada file yang dibagikan"))
                        return
                    }
                    val h = "HTTP/1.1 200 OK\r\nContent-Type: application/zip\r\n" +
                        "Content-Disposition: attachment; filename=\"DropBridge.zip\"\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
                    out.write(h.toByteArray())
                    val items = shared.map { sh -> Pair(sh.name, { ctx.contentResolver.openInputStream(sh.uri) }) }
                    writeZip(out, items)
                    lastEvent = "Diambil sebagai ZIP (${shared.size} file)"
                }

                method == "POST" && path == "/up" -> {
                    val len = headers["content-length"]?.toLongOrNull()
                    if (len == null || len < 0) {
                        json(out, 400, JSONObject().put("detail", "Content-Length wajib"))
                        return
                    }
                    val name = cleanName(q["name"] ?: "file")
                    val saved = saveReceived(name, len, input)
                    received++
                    receivedList.add(0, Received(saved, len, System.currentTimeMillis()))
                    while (receivedList.size > 30) receivedList.removeAt(receivedList.size - 1)
                    lastEvent = "Diterima: $saved"
                    json(out, 200, JSONObject().put("ok", true).put("name", saved))
                }

                else -> json(out, 404, JSONObject().put("detail", "Tidak ditemukan"))
            }
        } catch (e: Exception) {
            // koneksi putus di tengah jalan: abaikan
        } finally {
            try { sock.close() } catch (e: IOException) { /* abaikan */ }
        }
    }

    private fun cleanName(n: String): String {
        val c = n.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().trim('.')
        return if (c.isEmpty()) "file" else c.take(150)
    }

    private fun saveReceived(name: String, len: Long, input: InputStream): String =
        FileSaver.save(ctx, "DropBridge", name, len, input)
}

/** Menulis ZIP (tanpa kompresi, foto sudah terkompresi) ke aliran keluaran. */
fun writeZip(out: OutputStream, items: List<Pair<String, () -> InputStream?>>) {
    val zos = java.util.zip.ZipOutputStream(out)
    zos.setLevel(0)
    val used = HashSet<String>()
    for ((name, open) in items) {
        var n = name
        var i = 1
        while (!used.add(n)) {
            n = name.substringBeforeLast('.', name) + " ($i)" + (if (name.contains('.')) "." + name.substringAfterLast('.') else "")
            i++
        }
        val ins = open() ?: continue
        zos.putNextEntry(java.util.zip.ZipEntry(n))
        ins.use { FileSaver.copy(it, zos, -1) }
        zos.closeEntry()
    }
    zos.finish()
    zos.flush()
}
