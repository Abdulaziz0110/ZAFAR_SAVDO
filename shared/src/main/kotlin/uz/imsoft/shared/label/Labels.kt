package uz.imsoft.shared.label

import uz.imsoft.shared.JObj
import uz.imsoft.shared.barcode.BarcodeImport
import uz.imsoft.shared.barcode.BarcodeLink
import uz.imsoft.shared.report.Grid
import uz.imsoft.shared.report.LedgerMerge
import uz.imsoft.shared.report.TableReader

/** Narx yorlig'i turi (1C'dagi "PLU Narx" va "Shtrix chek" shakllari). */
enum class LabelKind { PLU, BARCODE }

/** Bitta yorliq ma'lumoti (chizishdan oldin). */
data class LabelData(
    val name: String,
    /** "60 000" — so'mda, bo'shliq bilan (1C shaklidagidek). */
    val price: String,
    /** Tarozi PLU raqami (PLU yorlig'i uchun). */
    val plu: String = "",
    /** Do'kondagi shtrix kod (shtrixli yorliq uchun). */
    val barcode: String = "",
    val copies: Int = 1,
)

/**
 * 1C'dagi MXL (tabular hujjat) fayldan matnlarni o'qish — faqat ketma-ket kataklar matni (formatlarsiz).
 * "PLU Narx" shaklida: nom, "KOD:  501", narx — har yorliq uchun.
 */
object MxlText {
    fun isMxl(b: ByteArray): Boolean = b.size > 6 && String(b, 0, 6, Charsets.ISO_8859_1) == "MOXCEL"

    fun strings(b: ByteArray): List<String> {
        val start = (0 until minOf(b.size, 64)).firstOrNull { b[it] == '{'.code.toByte() } ?: return emptyList()
        val s = String(b, start, b.size - start, Charsets.UTF_8)
        val out = ArrayList<String>()
        val re = Regex("\\{\"#\",\"((?:[^\"]|\"\")*)\"\\}")
        for (m in re.findAll(s)) {
            val t = m.groupValues[1].replace("\"\"", "\"").trim()
            if (t.isNotEmpty() && t != "#") out += t
        }
        // Birinchi yozuv — til nomi ("Язык по умолчанию") emas, ular {"#","",1,...} ko'rinishida; regex ularni olmaydi.
        return out
    }
}

/**
 * Tarozi PLU raqamlari ro'yxatini o'qish:
 *  1) 1C "PLU Narx" shakli (Excel yoki MXL): nom, keyin "KOD: 501";
 *  2) jadval: sarlavhada "PLU" (yoki "Номер PLU", "Код весов") ustuni va nom/kod ustuni.
 */
object PluImport {
    class Row(val name: String, val code: String, val plu: String)
    class NotPlu(msg: String) : Exception(msg)

    private val KOD = Regex("^(?:KOD|КОД|PLU|ПЛУ)\\s*[:№#.-]?\\s*(\\d{1,6})$", RegexOption.IGNORE_CASE)
    private val NUMERIC = Regex("^[\\d\\s .,]+$")

    fun parse(bytes: ByteArray, fileName: String = ""): List<Row> {
        val rows = if (MxlText.isMxl(bytes)) fromSequence(MxlText.strings(bytes)) else fromGrid(TableReader.read(bytes, fileName))
        if (rows.isEmpty()) throw NotPlu("no_plu")
        return rows.distinctBy { LedgerMerge.nameKey(it.name) + "|" + it.code }
    }

    /** Ketma-ket matnlar: "Nom", "KOD: 501", "60 000", "Nom2", ... */
    fun fromSequence(items: List<String>): List<Row> {
        val out = ArrayList<Row>()
        for ((i, t) in items.withIndex()) {
            val m = KOD.matchEntire(t.replace('\u00A0', ' ').trim()) ?: continue
            val name = (i - 1 downTo maxOf(0, i - 3)).map { items[it] }.firstOrNull { isName(it) } ?: continue
            out += Row(name, "", m.groupValues[1])
        }
        return out
    }

    private fun isName(t: String) = !NUMERIC.matches(t) && KOD.matchEntire(t.trim()) == null && t.count { it.isLetter() } >= 2

    fun fromGrid(g: Grid): List<Row> {
        fun txt(r: Int, c: Int): String {
            val cell = g[r, c] ?: return ""
            val n = cell.num
            if (cell.text.isBlank() && n != null) return if (n == Math.rint(n)) n.toLong().toString() else n.toString()
            return cell.text.trim()
        }
        // 1) "KOD: 501" kataklari — nom yuqoridagi qatorlarda (yoki chapda)
        val seq = ArrayList<Row>()
        for (r in g.rows.indices) for (c in 0 until g.width) {
            val t = txt(r, c)
            val m = KOD.matchEntire(t) ?: continue
            if (c > 0 && g[r, c - 1] === g[r, c]) continue // birlashtirilgan katak
            var name: String? = null
            loop@ for (rr in r downTo maxOf(0, r - 3)) {
                for (cc in 0 until g.width) {
                    if (rr == r && cc >= c) break
                    val x = txt(rr, cc)
                    if (isName(x)) name = x
                }
                if (name != null) break@loop
            }
            if (name != null) seq += Row(name, "", m.groupValues[1])
        }
        if (seq.isNotEmpty()) return seq
        // 2) Sarlavhali jadval
        for (r in 0 until minOf(g.rows.size, 15)) {
            val heads = (0 until g.width).map { txt(r, it).lowercase() }
            val pc = heads.indexOfFirst { it.contains("plu") || it.contains("плу") || it.contains("код вес") || it.contains("kod tarozi") }
            if (pc < 0) continue
            val nc = heads.indexOfFirst { it.contains("наимен") || it.contains("nomi") || it.contains("tovar") || it.contains("товар") || it == "name" }
            val cc = heads.indexOfFirst { it == "код" || it == "kod" || it == "code" || it.startsWith("код ") && !it.contains("вес") && !it.contains("plu") }
            val out = ArrayList<Row>()
            for (rr in r + 1 until g.rows.size) {
                val plu = txt(rr, pc).filter { it.isDigit() }
                if (plu.isEmpty()) continue
                val name = if (nc >= 0) txt(rr, nc) else ""
                val code = if (cc >= 0) txt(rr, cc).filterNot { it == ' ' || it == '\u00A0' } else ""
                if (name.isEmpty() && code.isEmpty()) continue
                out += Row(name, code, plu.trimStart('0').ifEmpty { "0" })
            }
            if (out.isNotEmpty()) return out
        }
        return emptyList()
    }

    /** Katalogga bog'lash: avval kod, keyin nom (bir xil nomli ikki tovar — bog'lanmaydi). @return tovar id -> PLU. */
    fun <T> link(rows: List<Row>, items: List<T>, id: (T) -> String, name: (T) -> String): Pair<Map<String, String>, List<Row>> {
        val byCode = HashMap<String, String>()
        for (it in items) byCode.putIfAbsent(BarcodeLink.codeKey(id(it)), id(it))
        val byName = HashMap<String, String?>()
        for (it in items) {
            val k = LedgerMerge.nameKey(name(it))
            byName[k] = if (byName.containsKey(k) && byName[k] != id(it)) null else id(it)
        }
        val map = LinkedHashMap<String, String>()
        val missing = ArrayList<Row>()
        for (r in rows) {
            val pid = (if (r.code.isNotEmpty()) byCode[BarcodeLink.codeKey(r.code)] else null) ?: byName[LedgerMerge.nameKey(r.name)]
            if (pid == null) missing += r else map[pid] = r.plu
        }
        return map to missing
    }
}

/**
 * Kompyuterdan (onec_export.py --codes) kelgan tovar kodlari: 1C kodi, nomi, tarozi PLU va shtrix kodlar.
 * JSON: {"v":1,"src":{"plu":..,"bc":..},"items":[{"c":"2555","n":"Orik ..","p":"501","b":["2901740"]}]}
 */
object CodesPayload {
    /** [prices] — 1C narx turlari bo'yicha (nomi -> narx), bo'lmasa bo'sh. */
    class Item(val code: String, val name: String, val plu: String, val barcodes: List<String>, val prices: Map<String, Double> = emptyMap())
    class Data(
        val items: List<Item>, val pluSource: String, val barcodeSource: String,
        val priceSource: String = "", val priceTypes: List<String> = emptyList(),
    ) {
        val pluRows: List<PluImport.Row> get() = items.filter { it.plu.isNotEmpty() }.map { PluImport.Row(it.name, it.code, it.plu) }
        val barcodeRows: List<BarcodeImport.Row> get() = items.flatMap { i -> i.barcodes.map { BarcodeImport.Row(i.code, i.name, it) } }
    }

    @Suppress("UNCHECKED_CAST")
    /** Kompyuterdan kelgan tovarni katalogdagisiga bog'lash: avval kod, keyin nom (ikki ma'noli nom bog'lanmaydi). */
    fun <T> linkIds(items: List<Item>, catalog: List<T>, id: (T) -> String, name: (T) -> String): Map<Item, String> {
        val byCode = HashMap<String, String>()
        for (it in catalog) byCode.putIfAbsent(BarcodeLink.codeKey(id(it)), id(it))
        val byName = HashMap<String, String?>()
        for (it in catalog) {
            val k = LedgerMerge.nameKey(name(it))
            byName[k] = if (byName.containsKey(k) && byName[k] != id(it)) null else id(it)
        }
        val out = LinkedHashMap<Item, String>()
        for (i in items) {
            val pid = (if (i.code.isNotEmpty()) byCode[BarcodeLink.codeKey(i.code)] else null) ?: byName[LedgerMerge.nameKey(i.name)]
            if (pid != null) out[i] = pid
        }
        return out
    }

    fun parse(bytes: ByteArray): Data {
        val o = JObj.parse(String(bytes, Charsets.UTF_8))
        if (o.long("v", 0) < 1) throw IllegalArgumentException("codes: v")
        val items = o.arr("items").mapNotNull { x ->
            val m = JObj(x as? Map<String, Any?> ?: return@mapNotNull null)
            val code = m.str("c").trim()
            val name = m.str("n").trim()
            if (code.isEmpty() && name.isEmpty()) return@mapNotNull null
            val plu = m.str("p").filter { it.isDigit() }.trimStart('0')
            val bc = (m.map["b"] as? List<Any?>).orEmpty().mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }.distinct()
            val pr = (m.map["pr"] as? Map<String, Any?>).orEmpty().mapNotNull { (k, v) ->
                val d = (v as? Number)?.toDouble() ?: v?.toString()?.toDoubleOrNull() ?: return@mapNotNull null
                if (d > 0 && k.isNotBlank()) k.trim() to d else null
            }.toMap()
            Item(code, name, plu, bc, pr)
        }
        val src = o.obj("src")
        val types = src?.arr("types").orEmpty().mapNotNull { it?.toString()?.trim()?.takeIf { t -> t.isNotEmpty() } }
            .ifEmpty { items.flatMap { it.prices.keys }.distinct() }
        return Data(items, src?.str("plu").orEmpty(), src?.str("bc").orEmpty(), src?.str("price").orEmpty(), types)
    }
}

/** Narxni yorliq uchun yozish: 60000.0 -> "60 000" (1C shaklidagidek, birliksiz). */
object LabelPrice {
    fun som(v: Double): String {
        val n = Math.round(v)
        val s = kotlin.math.abs(n).toString().reversed().chunked(3).joinToString(" ").reversed()
        return if (n < 0) "-$s" else s
    }
}
