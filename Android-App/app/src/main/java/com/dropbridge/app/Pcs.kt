package com.dropbridge.app

import org.json.JSONArray
import org.json.JSONObject

/** Satu PC yang tersimpan di HP (alamat, PIN, token, nama, sistem). */
data class PcEntry(
    val id: String, val name: String, val os: String,
    val server: String, val remote: String, val pin: String, val token: String, val version: String,
) {
    fun json(): JSONObject = JSONObject().put("id", id).put("name", name).put("os", os).put("server", server)
        .put("remote", remote).put("pin", pin).put("token", token).put("version", version)

    /** Nama untuk ditampilkan, mis. "laptop-mint". */
    val label: String get() = name.ifEmpty { server.removePrefix("http://").substringBefore(':') }

    companion object {
        fun of(o: JSONObject) = PcEntry(o.optString("id"), o.optString("name"), o.optString("os"), o.optString("server"),
            o.optString("remote"), o.optString("pin"), o.optString("token"), o.optString("version"))
    }
}

/**
 * Beberapa PC (mis. laptop Windows dan laptop Linux). Pengaturan "aktif" tetap di Prefs (server, pin, token, ...)
 * supaya kode lain tidak berubah; daftar ini menyimpan salinan tiap PC untuk berpindah.
 * Aturan: HP selalu mengirim ke PC yang terakhir dipilih, tidak pindah sendiri.
 */
object Pcs {
    fun all(): List<PcEntry> = try {
        val a = JSONArray(Prefs.pcList.ifEmpty { "[]" })
        (0 until a.length()).map { PcEntry.of(a.getJSONObject(it)) }
    } catch (e: Exception) { emptyList() }

    private fun write(list: List<PcEntry>) {
        val a = JSONArray()
        list.forEach { a.put(it.json()) }
        Prefs.pcList = a.toString()
    }

    fun active(): PcEntry = PcEntry(Prefs.pcId, Prefs.pcName, Prefs.pcOs, Prefs.server, Prefs.remoteServer,
        Prefs.pin, Prefs.token, Prefs.pcVersion)

    private fun same(a: PcEntry, b: PcEntry): Boolean =
        if (a.id.isNotEmpty() && b.id.isNotEmpty()) a.id == b.id else a.server.isNotEmpty() && a.server == b.server

    /** Simpan PC yang sedang dipakai ke daftar (dipanggil setelah berhasil tersambung). */
    @Synchronized
    fun saveActive() {
        val cur = active()
        if (cur.server.isEmpty() || cur.pin.isEmpty()) return
        val rest = all().filter { !same(it, cur) }
        write(listOf(cur) + rest)
    }

    /** Pindah ke PC lain: simpan yang sekarang, muat yang dipilih. */
    @Synchronized
    fun switchTo(e: PcEntry) {
        saveActive()
        Prefs.pcId = e.id
        Prefs.pcName = e.name
        Prefs.pcOs = e.os
        Prefs.server = e.server
        Prefs.remoteServer = e.remote
        Prefs.pin = e.pin
        Prefs.token = e.token
        Prefs.pcVersion = e.version
        Prefs.role = "full"
        Prefs.folders = emptyList()
        Net.current = ""
        Net.viaRemote = false
        Status.line = ""
    }

    /** PC baru dari hasil pencarian: alamat + PIN, belum tersimpan. */
    fun startNew(found: Net.Found, pin: String) {
        saveActive()
        Prefs.pcId = found.id
        Prefs.pcName = found.host
        Prefs.pcOs = found.os
        Prefs.server = found.base
        Prefs.remoteServer = ""
        Prefs.pin = pin
        Prefs.token = ""
        Prefs.pcVersion = found.version
        Prefs.role = "full"
        Prefs.folders = emptyList()
        Net.current = ""
        Status.line = ""
    }

    fun forget(e: PcEntry) = write(all().filter { !same(it, e) })

    fun isActive(e: PcEntry): Boolean = same(e, active())
}
