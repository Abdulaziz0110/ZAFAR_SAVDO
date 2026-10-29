package uz.imsoft.shared.report

/**
 * Jadval katagi. Birlashtirilgan (merge / rowspan / colspan) kataklarda butun maydon BITTA [Cell] obyektiga ishora qiladi —
 * shu orqali "bu ikki qator bitta yozuvga tegishli" ekanini bilamiz.
 */
class Cell(val text: String, val num: Double? = null) {
    val isEmpty: Boolean get() = text.isBlank() && num == null
    override fun toString(): String = if (num != null && text.isBlank()) num.toString() else text
}

class Grid(val rows: List<Array<Cell?>>) {
    val width: Int = rows.maxOfOrNull { it.size } ?: 0
    operator fun get(r: Int, c: Int): Cell? = rows.getOrNull(r)?.getOrNull(c)

    /** Bo'sh qator va ustunlarsiz matn ko'rinishi (tashxis uchun). */
    fun dump(maxRows: Int = 40): String = buildString {
        for (r in rows.take(maxRows)) append(r.joinToString(" | ") { it?.toString()?.replace('\n', ' ').orEmpty() }).append('\n')
    }

    class Builder {
        private val rows = ArrayList<ArrayList<Cell?>>()

        fun set(r: Int, c: Int, cell: Cell) {
            while (rows.size <= r) rows.add(ArrayList())
            val row = rows[r]
            while (row.size <= c) row.add(null)
            row[c] = cell
        }

        fun isFree(r: Int, c: Int): Boolean = rows.getOrNull(r)?.getOrNull(c) == null

        /** [cell] ni [r1..r2]×[c1..c2] maydonga yoyadi. */
        fun span(r1: Int, c1: Int, r2: Int, c2: Int, cell: Cell) {
            for (r in r1..r2) for (c in c1..c2) set(r, c, cell)
        }

        fun get(r: Int, c: Int): Cell? = rows.getOrNull(r)?.getOrNull(c)

        fun build(): Grid {
            val w = rows.maxOfOrNull { it.size } ?: 0
            return Grid(rows.map { row -> Array(w) { i -> row.getOrNull(i) } })
        }
    }
}

/** Fayl turini aniqlab, mos o'quvchini ishlatadi. */
object TableReader {
    class UnsupportedFormat(msg: String) : Exception(msg)

    fun read(bytes: ByteArray, fileName: String = ""): Grid {
        val name = fileName.lowercase()
        return when {
            XlsReader.isOle(bytes) -> XlsReader.read(bytes)
            isZip(bytes) -> try { XlsxReader.read(bytes) } catch (e: Exception) { throw UnsupportedFormat("xlsx") }
            name.endsWith(".mxl") || startsWith(bytes, "MOXCEL") -> throw UnsupportedFormat("mxl")
            looksHtml(bytes) -> HtmlTable.read(TextDecode.decode(bytes))
            else -> TextTable.read(TextDecode.decode(bytes))
        }
    }

    private fun isZip(b: ByteArray) = b.size > 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte()
    private fun startsWith(b: ByteArray, s: String) = b.size >= s.length && s.indices.all { b[it] == s[it].code.toByte() }

    private fun looksHtml(b: ByteArray): Boolean {
        val head = String(b, 0, minOf(b.size, 4096), Charsets.ISO_8859_1).lowercase()
        return head.contains("<table") || head.contains("<html") || head.contains("<tr")
    }
}

/** Matn kodlashini aniqlash: BOM, HTML meta charset, aks holda UTF-8 (to'g'ri bo'lsa) yoki Windows-1251 (1C 7.7). */
object TextDecode {
    fun decode(b: ByteArray): String {
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) return String(b, 3, b.size - 3, Charsets.UTF_8)
        if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16LE)
        if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16BE)
        val head = String(b, 0, minOf(b.size, 4096), Charsets.ISO_8859_1).lowercase()
        val m = Regex("charset\\s*=\\s*[\"']?([a-z0-9_-]+)").find(head)
        if (m != null) {
            val cs = runCatching { charset(m.groupValues[1]) }.getOrNull()
            if (cs != null) return String(b, cs)
        }
        if (validUtf8(b)) return String(b, Charsets.UTF_8)
        return String(b, charset("windows-1251"))
    }

    private fun validUtf8(b: ByteArray): Boolean {
        var i = 0
        while (i < b.size) {
            val x = b[i].toInt() and 0xFF
            val n = when {
                x < 0x80 -> 0
                x in 0xC2..0xDF -> 1
                x in 0xE0..0xEF -> 2
                x in 0xF0..0xF4 -> 3
                else -> return false
            }
            if (i + n >= b.size && n > 0) return false
            for (k in 1..n) if ((b[i + k].toInt() and 0xC0) != 0x80) return false
            i += n + 1
        }
        return true
    }
}

/** Tab yoki nuqta-vergul bilan ajratilgan matn (CSV/TXT). */
object TextTable {
    fun read(text: String): Grid {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val sep = when {
            lines.take(50).sumOf { l -> l.count { it == '\t' } } > 0 -> '\t'
            lines.take(50).sumOf { l -> l.count { it == ';' } } > 0 -> ';'
            lines.take(50).sumOf { l -> l.count { it == '|' } } > 0 -> '|'
            else -> ','
        }
        val b = Grid.Builder()
        for ((r, line) in lines.withIndex()) {
            for ((c, raw) in splitLine(line, sep).withIndex()) {
                val t = raw.trim()
                if (t.isNotEmpty()) b.set(r, c, Cell(t))
            }
        }
        return b.build()
    }

    private fun splitLine(line: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == sep && !quoted -> { out += sb.toString(); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }
}
