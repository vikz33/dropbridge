package com.dropbridge.app

/**
 * Pembuat kode QR kecil tanpa library (mode byte, koreksi galat L, versi 1-10).
 * Cukup untuk alamat pendek seperti http://192.168.43.1:8080/?k=123456.
 */
object QrCode {
    private val ECC_PER_BLOCK = intArrayOf(0, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18)
    private val NUM_BLOCKS = intArrayOf(0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4)

    private fun rawModules(ver: Int): Int {
        var r = (16 * ver + 128) * ver + 64
        if (ver >= 2) {
            val a = ver / 7 + 2
            r -= (25 * a - 10) * a - 55
            if (ver >= 7) r -= 36
        }
        return r
    }

    private fun dataCodewords(ver: Int): Int = rawModules(ver) / 8 - ECC_PER_BLOCK[ver] * NUM_BLOCKS[ver]

    private fun mul(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z
    }

    private fun divisor(deg: Int): IntArray {
        val r = IntArray(deg)
        r[deg - 1] = 1
        var root = 1
        for (i in 0 until deg) {
            for (j in 0 until deg) {
                r[j] = mul(r[j], root)
                if (j + 1 < deg) r[j] = r[j] xor r[j + 1]
            }
            root = mul(root, 2)
        }
        return r
    }

    private fun remainder(data: IntArray, div: IntArray): IntArray {
        val r = IntArray(div.size)
        for (b in data) {
            val f = b xor r[0]
            for (i in 0 until r.size - 1) r[i] = r[i + 1]
            r[r.size - 1] = 0
            for (i in r.indices) r[i] = r[i] xor mul(div[i], f)
        }
        return r
    }

    private fun bit(x: Int, i: Int): Boolean = ((x ushr i) and 1) != 0

    /** Mengembalikan matriks modul (true = gelap), atau null jika teks terlalu panjang. */
    fun encode(text: String): Array<BooleanArray>? {
        val bytes = text.toByteArray(Charsets.UTF_8)
        var ver = 1
        while (ver <= 10) {
            val countBits = if (ver <= 9) 8 else 16
            if (4 + countBits + bytes.size * 8 <= dataCodewords(ver) * 8) break
            ver++
        }
        if (ver > 10) return null

        // Bit data
        val bits = ArrayList<Int>()
        fun put(v: Int, n: Int) { for (i in n - 1 downTo 0) bits.add((v ushr i) and 1) }
        put(4, 4)
        put(bytes.size, if (ver <= 9) 8 else 16)
        for (b in bytes) put(b.toInt() and 0xFF, 8)
        val cap = dataCodewords(ver) * 8
        put(0, minOf(4, cap - bits.size))
        while (bits.size % 8 != 0) bits.add(0)
        var pad = 0xEC
        while (bits.size < cap) {
            put(pad, 8)
            pad = pad xor (0xEC xor 0x11)
        }
        val data = IntArray(bits.size / 8)
        for (i in bits.indices) data[i ushr 3] = data[i ushr 3] or (bits[i] shl (7 - (i and 7)))

        // Pecah ke blok, tambah koreksi galat, lalu selang-seling
        val nb = NUM_BLOCKS[ver]
        val ecl = ECC_PER_BLOCK[ver]
        val raw = rawModules(ver) / 8
        val shortBlocks = nb - raw % nb
        val shortLen = raw / nb
        val div = divisor(ecl)
        val blocks = ArrayList<IntArray>()
        var k = 0
        for (i in 0 until nb) {
            val len = shortLen - ecl + (if (i < shortBlocks) 0 else 1)
            val dat = data.copyOfRange(k, k + len)
            k += len
            val ecc = remainder(dat, div)
            val full = IntArray(shortLen + 1)
            // Blok pendek diberi satu slot kosong di tengah supaya panjangnya sama
            for (j in dat.indices) full[j] = dat[j]
            if (i < shortBlocks) {
                for (j in ecc.indices) full[dat.size + 1 + j] = ecc[j]
            } else {
                for (j in ecc.indices) full[dat.size + j] = ecc[j]
            }
            blocks.add(full)
        }
        val result = ArrayList<Int>()
        for (i in 0..shortLen) {
            for (j in 0 until nb) {
                if (i != shortLen - ecl || j >= shortBlocks) result.add(blocks[j][i])
            }
        }
        val all = IntArray(result.size) { result[it] }

        // Gambar
        val size = ver * 4 + 17
        val mod = Array(size) { BooleanArray(size) }
        val fn = Array(size) { BooleanArray(size) }
        fun setFn(x: Int, y: Int, dark: Boolean) { mod[y][x] = dark; fn[y][x] = true }

        for (i in 0 until size) { setFn(6, i, i % 2 == 0); setFn(i, 6, i % 2 == 0) }
        fun finder(cx: Int, cy: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val dist = maxOf(Math.abs(dx), Math.abs(dy))
                val x = cx + dx
                val y = cy + dy
                if (x in 0 until size && y in 0 until size) setFn(x, y, dist != 2 && dist != 4)
            }
        }
        finder(3, 3); finder(size - 4, 3); finder(3, size - 4)
        val align = ArrayList<Int>()
        if (ver > 1) {
            val n = ver / 7 + 2
            val step = (ver * 4 + n * 2 + 1) / (n * 2 - 2) * 2
            align.add(6)
            val tail = ArrayList<Int>()
            var pos = size - 7
            for (i in 0 until n - 1) { tail.add(0, pos); pos -= step }
            align.addAll(tail)
        }
        for (i in align.indices) for (j in align.indices) {
            if ((i == 0 && j == 0) || (i == 0 && j == align.size - 1) || (i == align.size - 1 && j == 0)) continue
            for (dy in -2..2) for (dx in -2..2) setFn(align[i] + dx, align[j] + dy, maxOf(Math.abs(dx), Math.abs(dy)) != 1)
        }

        fun formatBits(mask: Int) {
            val d = (1 shl 3) or mask // ECC level L = 01
            var rem = d
            for (i in 0 until 10) rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
            val b = ((d shl 10) or rem) xor 0x5412
            for (i in 0..5) setFn(8, i, bit(b, i))
            setFn(8, 7, bit(b, 6)); setFn(8, 8, bit(b, 7)); setFn(7, 8, bit(b, 8))
            for (i in 9..14) setFn(14 - i, 8, bit(b, i))
            for (i in 0..7) setFn(size - 1 - i, 8, bit(b, i))
            for (i in 8..14) setFn(8, size - 15 + i, bit(b, i))
            setFn(8, size - 8, true)
        }
        formatBits(0)
        if (ver >= 7) {
            var rem = ver
            for (i in 0 until 12) rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
            val b = (ver shl 12) or rem
            for (i in 0 until 18) {
                val a = size - 11 + i % 3
                val c = i / 3
                setFn(a, c, bit(b, i)); setFn(c, a, bit(b, i))
            }
        }

        // Letakkan data zig-zag
        var idx = 0
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until size) for (j in 0..1) {
                val x = right - j
                val upward = ((right + 1) and 2) == 0
                val y = if (upward) size - 1 - vert else vert
                if (!fn[y][x] && idx < all.size * 8) {
                    mod[y][x] = bit(all[idx ushr 3], 7 - (idx and 7))
                    idx++
                }
            }
            right -= 2
        }

        fun applyMask(m: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                val inv = when (m) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (!fn[y][x] && inv) mod[y][x] = !mod[y][x]
            }
        }

        fun penalty(): Int {
            var p = 0
            for (pass in 0..1) {
                for (a in 0 until size) {
                    var run = 1
                    for (b in 1 until size) {
                        val cur = if (pass == 0) mod[a][b] else mod[b][a]
                        val prev = if (pass == 0) mod[a][b - 1] else mod[b - 1][a]
                        if (cur == prev) {
                            run++
                            if (run == 5) p += 3 else if (run > 5) p += 1
                        } else run = 1
                    }
                    for (b in 0..size - 11) {
                        var m1 = true
                        var m2 = true
                        val pat = booleanArrayOf(true, false, true, true, true, false, true, false, false, false, false)
                        for (t in 0 until 11) {
                            val v = if (pass == 0) mod[a][b + t] else mod[b + t][a]
                            if (v != pat[t]) m1 = false
                            if (v != pat[10 - t]) m2 = false
                        }
                        if (m1) p += 40
                        if (m2) p += 40
                    }
                }
            }
            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                val c = mod[y][x]
                if (c == mod[y][x + 1] && c == mod[y + 1][x] && c == mod[y + 1][x + 1]) p += 3
            }
            var dark = 0
            for (y in 0 until size) for (x in 0 until size) if (mod[y][x]) dark++
            val total = size * size
            val kk = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1
            p += maxOf(kk, 0) * 10
            return p
        }

        var best = 0
        var bestScore = Int.MAX_VALUE
        for (m in 0 until 8) {
            applyMask(m)
            formatBits(m)
            val s = penalty()
            if (s < bestScore) { bestScore = s; best = m }
            applyMask(m)
        }
        applyMask(best)
        formatBits(best)
        return mod
    }
}
