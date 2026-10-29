package uz.imsoft.shared.report

import java.nio.charset.Charset

/**
 * Eski Excel (.xls, BIFF5/BIFF8, shuningdek OLE'siz BIFF3/4) faylidan BIRINCHI varaqni o'qiydi.
 * 1C 7.7 "Файл ▸ Сохранить как ▸ Excel" shunday fayl yozadi. Kutubxonasiz (Apache POI'siz).
 * Birlashtirilgan kataklar (MERGEDCELLS) bitta [Cell] obyektiga yoyiladi.
 */
object XlsReader {
    class XlsException(msg: String) : Exception(msg)

    private val SIG = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())

    fun isOle(b: ByteArray): Boolean = b.size >= 512 && SIG.indices.all { b[it] == SIG[it] }

    fun isRawBiff(b: ByteArray): Boolean =
        b.size > 8 && b[0] == 0x09.toByte() && (b[1] == 0x00.toByte() || b[1] == 0x02.toByte() || b[1] == 0x04.toByte() || b[1] == 0x08.toByte())

    fun read(b: ByteArray): Grid {
        val stream = if (isOle(b)) Cfb(b).stream("Workbook") ?: Cfb(b).stream("Book") ?: throw XlsException("Workbook oqimi topilmadi")
        else if (isRawBiff(b)) b else throw XlsException("Excel fayli emas")
        return Biff(stream).sheet()
    }

    private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun i32(b: ByteArray, o: Int) = u16(b, o) or (u16(b, o + 2) shl 16)

    // ---------------- OLE2 Compound File ----------------
    private class Cfb(val b: ByteArray) {
        val sectorSize = 1 shl u16(b, 0x1E)
        val miniSize = 1 shl u16(b, 0x20)
        val miniCutoff = i32(b, 0x38)
        val fat: IntArray
        val miniFat: IntArray
        val dir: List<Triple<String, Int, Int>> // name, start, size
        val rootStart: Int
        val rootSize: Int

        init {
            if (sectorSize !in listOf(512, 4096)) throw XlsException("Noto'g'ri sektor o'lchami")
            val difat = ArrayList<Int>()
            for (i in 0 until 109) { val s = i32(b, 0x4C + i * 4); if (s >= 0) difat += s }
            var ds = i32(b, 0x44)
            var guard = 0
            while (ds >= 0 && guard++ < 10_000) {
                val off = off(ds)
                if (off + sectorSize > b.size) break
                val per = sectorSize / 4 - 1
                for (i in 0 until per) { val s = i32(b, off + i * 4); if (s >= 0) difat += s }
                ds = i32(b, off + per * 4)
            }
            val fatList = IntArray(difat.size * sectorSize / 4)
            var k = 0
            for (s in difat) {
                val o = off(s)
                if (o + sectorSize > b.size) break
                for (i in 0 until sectorSize / 4) fatList[k++] = i32(b, o + i * 4)
            }
            fat = fatList
            miniFat = chainBytes(i32(b, 0x3C)).let { mf -> IntArray(mf.size / 4) { i32(mf, it * 4) } }
            val dirBytes = chainBytes(i32(b, 0x30))
            val entries = ArrayList<Triple<String, Int, Int>>()
            var rs = -2
            var rz = 0
            for (e in 0 until dirBytes.size / 128) {
                val o = e * 128
                val nameLen = u16(dirBytes, o + 0x40)
                val type = dirBytes[o + 0x42].toInt()
                if (type == 0 || nameLen < 2) continue
                val name = String(dirBytes, o, minOf(64, nameLen) - 2, Charsets.UTF_16LE)
                val start = i32(dirBytes, o + 0x74)
                val size = i32(dirBytes, o + 0x78)
                if (type == 5) { rs = start; rz = size }
                if (type == 2) entries += Triple(name, start, size)
            }
            dir = entries
            rootStart = rs
            rootSize = rz
        }

        fun off(sector: Int) = (sector + 1) * sectorSize

        fun chainBytes(start: Int, limit: Int = Int.MAX_VALUE): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            var s = start
            var guard = 0
            while (s >= 0 && guard++ < 1_000_000 && out.size() < limit) {
                val o = off(s)
                if (o + sectorSize > b.size) { if (o < b.size) out.write(b, o, b.size - o); break }
                out.write(b, o, sectorSize)
                if (s >= fat.size) break
                s = fat[s]
            }
            return out.toByteArray()
        }

        fun stream(name: String): ByteArray? {
            val e = dir.firstOrNull { it.first.equals(name, ignoreCase = true) } ?: return null
            val (_, start, size) = e
            if (size < miniCutoff) {
                val mini = chainBytes(rootStart, rootSize)
                val out = java.io.ByteArrayOutputStream(size)
                var s = start
                var guard = 0
                while (s >= 0 && guard++ < 1_000_000 && out.size() < size) {
                    val o = s * miniSize
                    if (o + miniSize > mini.size) break
                    out.write(mini, o, miniSize)
                    if (s >= miniFat.size) break
                    s = miniFat[s]
                }
                return out.toByteArray().copyOf(minOf(size, out.size()))
            }
            val data = chainBytes(start, size)
            return data.copyOf(minOf(size, data.size))
        }
    }

    // ---------------- BIFF ----------------
    private class Biff(val s: ByteArray) {
        var biff8 = false
        var charset: Charset = Charset.forName("windows-1251")
        val sst = ArrayList<String>()
        val cells = HashMap<Long, Cell>()
        val merges = ArrayList<IntArray>()

        private fun key(r: Int, c: Int) = (r.toLong() shl 20) or c.toLong()

        fun sheet(): Grid {
            var p = 0
            var inSheet = false
            var sheetDone = false
            var sawGlobals = false
            var pendingFormula: Pair<Int, Int>? = null
            while (p + 4 <= s.size && !sheetDone) {
                val type = u16(s, p)
                val len = u16(s, p + 2)
                val d = p + 4
                if (d + len > s.size) break
                when (type) {
                    0x0809, 0x0409, 0x0209, 0x0009 -> {
                        val ver = if (len >= 2) u16(s, d) else 0
                        val dt = if (len >= 4) u16(s, d + 2) else 0x10
                        if (type == 0x0809) biff8 = ver == 0x0600
                        if (dt == 0x0005) sawGlobals = true
                        else if (dt == 0x0010 && !inSheet) inSheet = true
                        else if (type != 0x0809) inSheet = true // BIFF2–4: bitta varaqli
                    }
                    0x000A -> if (inSheet) sheetDone = true
                    0x0042 -> if (len >= 2) {
                        val cp = u16(s, d)
                        charset = when (cp) {
                            1200 -> Charsets.UTF_16LE
                            367, 20127 -> Charsets.US_ASCII
                            65001 -> Charsets.UTF_8
                            0x8000, 10000 -> runCatching { Charset.forName("x-MacRoman") }.getOrDefault(Charsets.ISO_8859_1)
                            else -> runCatching { Charset.forName("windows-$cp") }.getOrDefault(charset)
                        }
                    }
                    0x00FC -> readSst(p)
                    else -> if (inSheet) {
                        when (type) {
                            0x00FD -> if (len >= 10) put(u16(s, d), u16(s, d + 2), Cell(sst.getOrNull(i32(s, d + 6)).orEmpty()))
                            0x0204, 0x00D6 -> if (len >= 8) put(u16(s, d), u16(s, d + 2), Cell(readString(d + 6, d + len)))
                            0x0203 -> if (len >= 14) put(u16(s, d), u16(s, d + 2), numCell(java.lang.Double.longBitsToDouble(i64(d + 6))))
                            0x027E -> if (len >= 10) put(u16(s, d), u16(s, d + 2), numCell(rk(i32(s, d + 6))))
                            0x00BD -> if (len >= 6) {
                                val r = u16(s, d)
                                var c = u16(s, d + 2)
                                var o = d + 4
                                while (o + 6 <= d + len - 2) { put(r, c, numCell(rk(i32(s, o + 2)))); c++; o += 6 }
                            }
                            0x0006, 0x0406, 0x0206 -> if (len >= 14) {
                                val r = u16(s, d); val c = u16(s, d + 2)
                                if (u16(s, d + 12) == 0xFFFF) {
                                    when (s[d + 6].toInt()) {
                                        0 -> pendingFormula = r to c
                                        1 -> put(r, c, Cell(if (s[d + 8].toInt() != 0) "TRUE" else "FALSE"))
                                        else -> Unit
                                    }
                                } else put(r, c, numCell(java.lang.Double.longBitsToDouble(i64(d + 6))))
                            }
                            0x0207 -> pendingFormula?.let { (r, c) -> put(r, c, Cell(readString(d, d + len))); pendingFormula = null }
                            0x00E5 -> if (len >= 2) {
                                val n = u16(s, d)
                                for (i in 0 until n) {
                                    val o = d + 2 + i * 8
                                    if (o + 8 > d + len) break
                                    merges += intArrayOf(u16(s, o), u16(s, o + 2), u16(s, o + 4), u16(s, o + 6))
                                }
                            }
                        }
                    }
                }
                p = d + len
            }
            if (!inSheet && !sawGlobals && cells.isEmpty()) throw XlsException("Varaq topilmadi")
            val b = Grid.Builder()
            for ((k, cell) in cells) b.set((k ushr 20).toInt(), (k and 0xFFFFF).toInt(), cell)
            for (m in merges) {
                val (r1, r2, c1, c2) = m.toList()
                if (r2 < r1 || c2 < c1 || (r2 - r1 + 1) * (c2 - c1 + 1) > 200_000) continue
                val origin = cells[key(r1, c1)] ?: Cell("")
                b.span(r1, c1, r2, c2, origin)
            }
            return b.build()
        }

        private fun put(r: Int, c: Int, cell: Cell) { cells[key(r, c)] = cell }

        private fun numCell(v: Double): Cell = Cell("", v)

        private fun i64(o: Int): Long = (i32(s, o).toLong() and 0xFFFFFFFFL) or (i32(s, o + 4).toLong() shl 32)

        private fun rk(v: Int): Double {
            val r = if (v and 0x02 != 0) (v shr 2).toDouble()
            else java.lang.Double.longBitsToDouble((v.toLong() and 0xFFFFFFFCL) shl 32)
            return if (v and 0x01 != 0) r / 100.0 else r
        }

        /** LABEL/STRING ichidagi satr: BIFF8 — XLUnicodeString, BIFF5 va oldin — 8-bitli kod sahifasi. */
        private fun readString(o: Int, end: Int): String {
            if (o + 2 > end) return ""
            val cch = u16(s, o)
            if (!biff8) {
                val n = minOf(cch, end - o - 2)
                return String(s, o + 2, maxOf(0, n), charset)
            }
            if (o + 3 > end) return ""
            val flags = s[o + 2].toInt()
            val high = flags and 0x01 != 0
            var p = o + 3
            if (flags and 0x08 != 0) p += 2
            if (flags and 0x04 != 0) p += 4
            val bytes = if (high) cch * 2 else cch
            val n = minOf(bytes, end - p)
            if (n <= 0) return ""
            return if (high) String(s, p, n, Charsets.UTF_16LE) else String(s, p, n, Charsets.ISO_8859_1)
        }

        /** SST + CONTINUE yozuvlari: satr ikki yozuv chegarasida bo'linib qolishi mumkin. */
        private fun readSst(recStart: Int) {
            val segs = ArrayList<IntArray>() // [start, end)
            var p = recStart
            var first = true
            while (p + 4 <= s.size) {
                val type = u16(s, p)
                val len = u16(s, p + 2)
                if (!first && type != 0x003C) break
                if (p + 4 + len > s.size) break
                segs += intArrayOf(p + 4, p + 4 + len)
                first = false
                p += 4 + len
            }
            if (segs.isEmpty()) return
            var seg = 0
            var pos = segs[0][0] + 8
            val unique = i32(s, segs[0][0] + 4)
            fun avail() = segs[seg][1] - pos
            fun nextSeg(): Boolean { seg++; if (seg >= segs.size) return false; pos = segs[seg][0]; return true }
            fun byte(): Int { while (avail() <= 0) if (!nextSeg()) throw XlsException("SST tugadi"); return s[pos++].toInt() and 0xFF }
            fun skip(n0: Int) { var n = n0; while (n > 0) { if (avail() <= 0 && !nextSeg()) return; val k = minOf(n, avail()); pos += k; n -= k } }
            try {
                for (i in 0 until unique) {
                    val cch = byte() or (byte() shl 8)
                    val flags = byte()
                    var high = flags and 0x01 != 0
                    val runs = if (flags and 0x08 != 0) byte() or (byte() shl 8) else 0
                    val ext = if (flags and 0x04 != 0) byte() or (byte() shl 8) or (byte() shl 16) or (byte() shl 24) else 0
                    val sb = StringBuilder(cch)
                    var left = cch
                    while (left > 0) {
                        if (avail() <= 0) {
                            if (!nextSeg()) break
                            high = (s[pos++].toInt() and 0x01) != 0 // CONTINUE boshida yangi bayroq
                        }
                        if (high) {
                            if (avail() < 2) { pos = segs[seg][1]; continue }
                            sb.append(((s[pos].toInt() and 0xFF) or ((s[pos + 1].toInt() and 0xFF) shl 8)).toChar()); pos += 2
                        } else {
                            sb.append((s[pos].toInt() and 0xFF).toChar()); pos += 1
                        }
                        left--
                    }
                    skip(runs * 4)
                    skip(ext)
                    sst += sb.toString()
                }
            } catch (e: XlsException) {
                // Qolgan satrlar bo'sh — fayl qisman o'qiladi, ko'rib chiqishda ko'rinadi.
            }
        }
    }
}
