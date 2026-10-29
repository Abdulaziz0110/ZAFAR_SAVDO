package uz.imsoft.shared.report

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * Yangi Excel (.xlsx) faylidan BIRINCHI varaqni o'qiydi: satrlar (sharedStrings, inline), raqamlar, birlashtirilgan
 * kataklar (mergeCells). Kutubxonasiz — faqat ZIP va SAX (Android'da ham, JVM'da ham bor).
 * 1C "Сохранить как ▸ Excel" yoki Excel'da qayta saqlangan "Mijoz kartochkasi" shu yo'l bilan o'qiladi.
 */
object XlsxReader {
    class XlsxException(msg: String) : Exception(msg)

    private const val MAX_ENTRY = 80 * 1024 * 1024

    fun isXlsx(b: ByteArray): Boolean = b.size > 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() &&
        entries(b, setOf("[Content_Types].xml")).isNotEmpty() && String(entries(b, setOf("[Content_Types].xml")).values.first(), Charsets.UTF_8).contains("spreadsheetml")

    fun read(b: ByteArray): Grid {
        val wanted = setOf("xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/sharedStrings.xml")
        val meta = entries(b, wanted)
        val sheetPath = firstSheetPath(meta["xl/workbook.xml"], meta["xl/_rels/workbook.xml.rels"])
        val sheet = entries(b, setOf(sheetPath)).values.firstOrNull()
            ?: entries(b, null).entries.firstOrNull { it.key.startsWith("xl/worksheets/") && it.key.endsWith(".xml") }?.value
            ?: throw XlsxException("Varaq topilmadi")
        val shared = meta["xl/sharedStrings.xml"]?.let { sharedStrings(it) } ?: emptyList()
        return parseSheet(sheet, shared)
    }

    private fun entries(b: ByteArray, names: Set<String>?): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(b)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory || (names != null && e.name !in names)) continue
                val bos = java.io.ByteArrayOutputStream()
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = z.read(buf)
                    if (n < 0) break
                    bos.write(buf, 0, n)
                    if (bos.size() > MAX_ENTRY) throw XlsxException("Fayl juda katta")
                }
                out[e.name] = bos.toByteArray()
                if (names != null && out.size == names.size) break
            }
        }
        return out
    }

    private fun sax(data: ByteArray, h: DefaultHandler) {
        val f = SAXParserFactory.newInstance()
        f.isNamespaceAware = false
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { f.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        f.newSAXParser().parse(ByteArrayInputStream(data), h)
    }

    private fun local(q: String) = q.substringAfter(':')

    private fun firstSheetPath(workbook: ByteArray?, rels: ByteArray?): String {
        val def = "xl/worksheets/sheet1.xml"
        if (workbook == null) return def
        var rid: String? = null
        sax(workbook, object : DefaultHandler() {
            override fun startElement(uri: String?, ln: String?, q: String, a: Attributes) {
                if (rid == null && local(q) == "sheet") {
                    for (i in 0 until a.length) if (local(a.getQName(i)) == "id") rid = a.getValue(i)
                }
            }
        })
        if (rid == null || rels == null) return def
        var target: String? = null
        sax(rels, object : DefaultHandler() {
            override fun startElement(uri: String?, ln: String?, q: String, a: Attributes) {
                if (local(q) == "Relationship" && a.getValue("Id") == rid) target = a.getValue("Target")
            }
        })
        val t = target ?: return def
        return if (t.startsWith("/")) t.removePrefix("/") else "xl/" + t.removePrefix("./")
    }

    private fun sharedStrings(data: ByteArray): List<String> {
        val out = ArrayList<String>()
        sax(data, object : DefaultHandler() {
            var cur: StringBuilder? = null
            var inT = false
            var inRph = false
            override fun startElement(uri: String?, ln: String?, q: String, a: Attributes) {
                when (local(q)) {
                    "si" -> cur = StringBuilder()
                    "t" -> inT = true
                    "rPh" -> inRph = true // fonetik yozuv — kerak emas
                }
            }
            override fun endElement(uri: String?, ln: String?, q: String) {
                when (local(q)) {
                    "si" -> { out += cur.toString(); cur = null }
                    "t" -> inT = false
                    "rPh" -> inRph = false
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inT && !inRph) cur?.append(ch, start, length)
            }
        })
        return out
    }

    /** "AB12" -> (11, 27) — 0 dan boshlanadigan (qator, ustun). */
    fun ref(r: String): Pair<Int, Int>? {
        var col = 0
        var i = 0
        while (i < r.length && r[i].isLetter()) { col = col * 26 + (r[i].uppercaseChar() - 'A' + 1); i++ }
        val row = r.substring(i).toIntOrNull() ?: return null
        if (col == 0 || row == 0) return null
        return (row - 1) to (col - 1)
    }

    private fun parseSheet(data: ByteArray, shared: List<String>): Grid {
        val b = Grid.Builder()
        val origins = HashMap<Long, Cell>()
        val merges = ArrayList<IntArray>()
        sax(data, object : DefaultHandler() {
            var r = -1; var c = -1; var type = ""
            val text = StringBuilder()
            var inV = false; var inT = false
            var rowIdx = -1; var colIdx = -1
            override fun startElement(uri: String?, ln: String?, q: String, a: Attributes) {
                when (local(q)) {
                    "row" -> { rowIdx = (a.getValue("r")?.toIntOrNull() ?: (rowIdx + 2)) - 1; colIdx = -1 }
                    "c" -> {
                        val p = a.getValue("r")?.let { ref(it) }
                        if (p != null) { r = p.first; c = p.second } else { r = rowIdx; c = colIdx + 1 }
                        colIdx = c
                        type = a.getValue("t") ?: ""
                        text.setLength(0)
                    }
                    "v" -> inV = true
                    "t" -> inT = true
                    "mergeCell" -> a.getValue("ref")?.let { ref ->
                        val parts = ref.split(':')
                        val p1 = ref(parts[0]); val p2 = if (parts.size > 1) ref(parts[1]) else p1
                        if (p1 != null && p2 != null) merges += intArrayOf(p1.first, p2.first, p1.second, p2.second)
                    }
                }
            }
            override fun endElement(uri: String?, ln: String?, q: String) {
                when (local(q)) {
                    "v" -> inV = false
                    "t" -> inT = false
                    "c" -> {
                        if (r >= 0 && c >= 0 && text.isNotEmpty()) {
                            val raw = text.toString()
                            val cell = when (type) {
                                "s" -> Cell(shared.getOrNull(raw.trim().toIntOrNull() ?: -1).orEmpty())
                                "inlineStr", "str", "e" -> Cell(raw)
                                "b" -> Cell(if (raw == "1") "TRUE" else "FALSE")
                                else -> raw.toDoubleOrNull()?.let { Cell("", it) } ?: Cell(raw)
                            }
                            b.set(r, c, cell)
                            origins[(r.toLong() shl 20) or c.toLong()] = cell
                        }
                        r = -1; c = -1
                    }
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inV || inT) text.append(ch, start, length)
            }
        })
        for (m in merges) {
            val (r1, r2, c1, c2) = m.toList()
            if (r2 < r1 || c2 < c1 || (r2 - r1 + 1).toLong() * (c2 - c1 + 1) > 200_000) continue
            val origin = origins[(r1.toLong() shl 20) or c1.toLong()] ?: Cell("")
            b.span(r1, c1, r2, c2, origin)
        }
        return b.build()
    }
}
