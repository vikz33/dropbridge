package com.dropbridge.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class Item(
    val id: String,
    val path: String,
    val name: String,
    val folder: String,
    val modified: Long,
    val size: Long,
)

/**
 * Antrean file yang belum terkirim. File disalin ke penyimpanan aplikasi supaya
 * tetap ada walau izin baca dari Galeri sudah hilang atau PC belum terjangkau.
 */
object Queue {
    private var ctx: Context? = null
    private val lock = Any()

    fun init(c: Context) { if (ctx == null) ctx = c.applicationContext }

    private fun indexFile() = File(ctx!!.filesDir, "queue.json")
    private fun dir(): File = File(ctx!!.filesDir, "queue").also { it.mkdirs() }

    private fun load(): MutableList<Item> {
        val out = ArrayList<Item>()
        try {
            val f = indexFile()
            if (!f.exists()) return out
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Item(o.getString("id"), o.getString("path"), o.getString("name"), o.getString("folder"), o.optLong("modified"), o.optLong("size")))
            }
        } catch (e: Exception) { /* indeks rusak: mulai kosong */ }
        return out
    }

    private fun save(list: List<Item>) {
        val arr = JSONArray()
        for (it in list) {
            arr.put(JSONObject().put("id", it.id).put("path", it.path).put("name", it.name).put("folder", it.folder).put("modified", it.modified).put("size", it.size))
        }
        val tmp = File(ctx!!.filesDir, "queue.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(indexFile())
    }

    fun all(): List<Item> = synchronized(lock) { load() }

    fun remove(id: String) = synchronized(lock) {
        val list = load()
        for (it in list) if (it.id == id) File(it.path).delete()
        save(list.filter { it.id != id })
    }

    /** Hapus beberapa file sekaligus (mis. satu folder). */
    fun removeAll(ids: Collection<String>) = synchronized(lock) {
        val set = ids.toSet()
        val list = load()
        for (it in list) if (it.id in set) File(it.path).delete()
        save(list.filter { it.id !in set })
    }

    /** Pindahkan file di antrean ke folder lain sebelum dikirim. */
    fun setFolder(ids: Collection<String>, folder: String) = synchronized(lock) {
        val set = ids.toSet()
        save(load().map { if (it.id in set) Item(it.id, it.path, it.name, folder, it.modified, it.size) else it })
    }

    /** Antrean dikelompokkan per folder tujuan, urut sesuai antrean. */
    fun groups(): List<Pair<String, List<Item>>> = all().groupBy { it.folder }.toList()

    fun label(folder: String): String = pathLabel(folder)

    fun clear() = synchronized(lock) {
        for (it in load()) File(it.path).delete()
        save(emptyList())
    }

    private fun queryLong(c: Context, uri: Uri, col: String): Long = try {
        c.contentResolver.query(uri, arrayOf(col), null, null, null)?.use { cur ->
            if (cur.moveToFirst() && !cur.isNull(0)) cur.getLong(0) else 0L
        } ?: 0L
    } catch (e: Exception) { 0L }

    private fun queryName(c: Context, uri: Uri): String {
        try {
            c.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cur ->
                if (cur.moveToFirst() && !cur.isNull(0)) return cur.getString(0)
            }
        } catch (e: Exception) { /* lanjut */ }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    }

    /** Pindahkan file milik aplikasi (mis. hasil kamera) langsung ke antrean, tanpa menyalin. */
    fun addFile(src: File, name: String, folder: String, shrink: Boolean): Item? {
        if (!src.isFile || src.length() <= 0) return null
        val id = UUID.randomUUID().toString().replace("-", "")
        val dest = File(dir(), id)
        if (!src.renameTo(dest)) {
            src.inputStream().use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
            src.delete()
        }
        var finalName = name
        if (shrink && dest.length() > ImageShrink.MAX_BYTES && ImageShrink.shrink(dest)) {
            finalName = ImageShrink.jpgName(name)
        }
        val item = Item(id, dest.absolutePath, finalName, folder, System.currentTimeMillis(), dest.length())
        synchronized(lock) {
            val list = load()
            list.add(item)
            save(list)
        }
        return item
    }

    /** Salin satu file ke antrean. Mengembalikan null jika gagal dibaca. */
    fun add(c: Context, uri: Uri, folder: String, shrink: Boolean = true): Item? {
        try {
            var name = queryName(c, uri)
            var modified = queryLong(c, uri, "datetaken")
            if (modified <= 0) modified = queryLong(c, uri, "last_modified")
            if (modified <= 0) {
                val s = queryLong(c, uri, "date_modified")
                if (s > 0) modified = s * 1000
            }
            if (modified <= 0) modified = System.currentTimeMillis()

            val id = UUID.randomUUID().toString().replace("-", "")
            val dest = File(dir(), id)
            var size = 0L
            val input = c.contentResolver.openInputStream(uri) ?: return null
            input.use { ins ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        size += n
                    }
                }
            }
            if (shrink && size > ImageShrink.MAX_BYTES && ImageShrink.isImageName(name) && ImageShrink.shrink(dest)) {
                size = dest.length()
                name = ImageShrink.jpgName(name)
            }
            val item = Item(id, dest.absolutePath, name, folder, modified, size)
            synchronized(lock) {
                val list = load()
                list.add(item)
                save(list)
            }
            return item
        } catch (e: Exception) {
            return null
        }
    }
}
