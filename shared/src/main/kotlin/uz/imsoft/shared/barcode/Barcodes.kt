package uz.imsoft.shared.barcode

import uz.imsoft.shared.report.Grid
import uz.imsoft.shared.report.LedgerMerge

/**
 * Shtrix kodlar (v2.6): skanerlab savdo qilish uchun yadro — Android'ga bog'liq emas, JVM testlari bilan.
 *
 *  - [Ean]: EAN-8 / UPC-A / EAN-13 / ITF-14 tekshiruv raqami va "kanonik" ko'rinish (oldidagi nollarsiz) — Excel'da
 *    "012000800214" son bo'lib "12000800214" ga aylansa ham bir xil topiladi.
 *  - [ScaleFormat]: tarozi yorlig'i (2 prefiks + tovar kodi + og'irlik + tekshiruv) — uzunliklar sozlanadi.
 *  - [BarcodeImport]: 1C "Вывести список" dan saqlangan jadvaldan kod / nom / shtrix kod ustunlarini o'zi topadi.
 *  - [BarcodeLink]: qatorlarni katalogdagi tovarga bog'laydi (avval kod, keyin nom).
 *  - [BarcodeResolver]: skanerlangan matn -> tovar (+ tarozi bo'lsa og'irlik).
 */
object Ean {
    fun digits(s: String): String = s.filter { it in '0'..'9' }

    /** Faqat raqam (bo'shliq/tire olib tashlangan) bo'lsa — o'sha raqamlar, aks holda null. */
    fun numeric(raw: String): String? {
        val t = raw.trim().replace(" ", "").replace(" ", "").replace("-", "")
        return if (t.isNotEmpty() && t.all { it in '0'..'9' }) t else null
    }

    /** GTIN (8/12/13/14) tekshiruv raqami to'g'rimi. */
    fun checkOk(code: String): Boolean {
        if (code.length !in setOf(8, 12, 13, 14) || !code.all { it in '0'..'9' }) return false
        return checkDigit(code.dropLast(1)) == code.last() - '0'
    }

    /** Tekshiruv raqamini hisoblaydi (o'ngdan: 3,1,3,1...). */
    fun checkDigit(body: String): Int {
        var sum = 0
        for ((i, c) in body.reversed().withIndex()) sum += (c - '0') * if (i % 2 == 0) 3 else 1
        return (10 - sum % 10) % 10
    }

    /**
     * Qidiruv kaliti: raqamli kod — oldidagi nollarsiz (UPC-A "012000800214" == EAN-13 "0012000800214" == 12000800214);
     * harf-raqamli (Code 128/39) — katta harf, bo'shliqsiz.
     */
    fun canonical(raw: String): String {
        val n = numeric(raw)
        if (n != null) return n.trimStart('0').ifEmpty { "0" }
        return raw.trim().replace(" ", "").uppercase()
    }

    /** Jadvaldagi qiymat shtrix kodga o'xshaydimi (raqam 6–14 ta yoki harf-raqam 4–40). */
    fun looksLikeBarcode(raw: String): Boolean {
        val n = numeric(raw)
        if (n != null) return n.length in 6..14
        val t = raw.trim()
        return t.length in 4..40 && t.none { it.isWhitespace() } && t.any { it.isDigit() } && t.all { it.isLetterOrDigit() || it in "-./+" }
    }
}

/**
 * Tarozi yorlig'i. Standart: EAN-13 = [prefiks 2 ta][tovar kodi 5 ta][og'irlik 5 ta, gramm][tekshiruv 1 ta].
 * [divisor] — og'irlikni kg ga o'tkazish (1000: gramm; 100: 10 gramm). [checkDigit] — oxirgi raqamni tekshirish.
 */
data class ScaleFormat(
    val enabled: Boolean = true,
    val prefixes: List<String> = listOf("20", "21", "22", "23", "24", "25", "26", "27", "28", "29"),
    val codeLen: Int = 5,
    val valueLen: Int = 5,
    val divisor: Double = 1000.0,
    val checkDigit: Boolean = true,
    /** Yorliqdagi tovar kodi = 1C kodi deb so'ramasdan qabul qilish. Aks holda birinchi marta tasdiq so'raladi. */
    val trustCodes: Boolean = false,
) {
    class Hit(val itemCode: String, val qty: Double, val raw: String)

    val totalLen: Int get() = (prefixes.firstOrNull()?.length ?: 2) + codeLen + valueLen + 1

    fun parse(raw: String): Hit? {
        if (!enabled) return null
        val n = Ean.numeric(raw) ?: return null
        val p = prefixes.firstOrNull { n.startsWith(it) } ?: return null
        if (n.length != p.length + codeLen + valueLen + 1) return null
        if (checkDigit && n.length in setOf(8, 12, 13, 14) && !Ean.checkOk(n)) return null
        val code = n.substring(p.length, p.length + codeLen).trimStart('0').ifEmpty { "0" }
        val v = n.substring(p.length + codeLen, p.length + codeLen + valueLen).toLongOrNull() ?: return null
        if (v <= 0) return null
        return Hit(code, Math.round(v / divisor * 1000.0) / 1000.0, n)
    }

    /** Sozlamada saqlash uchun: "20,21|5|5|1000|1|1". */
    fun encode(): String = listOf(prefixes.joinToString(","), codeLen, valueLen, divisor.toLong(), if (checkDigit) 1 else 0, if (enabled) 1 else 0, if (trustCodes) 1 else 0).joinToString("|")

    companion object {
        fun decode(s: String?): ScaleFormat {
            val p = s?.split('|') ?: return ScaleFormat()
            if (p.size < 6) return ScaleFormat()
            return runCatching {
                ScaleFormat(
                    enabled = p[5] == "1",
                    prefixes = p[0].split(',').map { it.trim() }.filter { it.isNotEmpty() && it.all { c -> c.isDigit() } }.ifEmpty { listOf("20") },
                    codeLen = p[1].toInt().coerceIn(1, 8), valueLen = p[2].toInt().coerceIn(1, 8),
                    divisor = p[3].toDouble().coerceIn(1.0, 100000.0), checkDigit = p[4] == "1",
                    trustCodes = p.getOrNull(6) == "1",
                )
            }.getOrDefault(ScaleFormat())
        }
    }
}

/** 1C dan chiqarilgan shtrix kodlar jadvalidan qatorlarni oladi. */
object BarcodeImport {
    class Row(val code: String, val name: String, val barcode: String)
    class Result(val rows: List<Row>, val barcodeCol: Int, val codeCol: Int, val nameCol: Int, val skipped: Int) {
        /**
         * Tarozi tovarlari: 1C'da vazn tovariga "prefiks + tovar kodi" (7 xona, masalan 2900027) yoziladi, yorliqda esa
         * shu 7 xona + og'irlik + tekshiruv raqami bo'ladi. Shunday kodlarning prefikslari (kamida 3 ta) — tarozi sozlamasi uchun.
         */
        val scalePrefixes: List<String> by lazy {
            rows.mapNotNull { Ean.numeric(it.barcode) }.filter { it.length == 7 && it.startsWith("2") }
                .groupingBy { it.take(2) }.eachCount().filter { it.value >= 3 }.keys.sorted()
        }
    }
    class NotBarcodes(msg: String) : Exception(msg)

    private fun text(g: Grid, r: Int, c: Int): String {
        val cell = g[r, c] ?: return ""
        val num = cell.num
        // Excel son bo'lib saqlagan bo'lsa: 1.2000800214E10 -> "12000800214"
        if (num != null && cell.text.isBlank() && num == Math.rint(num) && Math.abs(num) < 1e15) return num.toLong().toString()
        return cell.text.trim()
    }

    private fun isCode(s: String): Boolean {
        val n = Ean.numeric(s) ?: return s.length in 1..12 && s.any { it.isDigit() } && s.none { it.isWhitespace() } && s.all { it.isLetterOrDigit() || it in "-_./" }
        return n.length in 1..11
    }

    private fun isName(s: String) = s.count { it.isLetter() } >= 2

    fun parse(g: Grid): Result {
        if (g.rows.isEmpty()) throw NotBarcodes("empty")
        val w = g.width
        val bc = IntArray(w); val cd = IntArray(w); val nm = IntArray(w)
        for (r in g.rows.indices) for (c in 0 until w) {
            val t = text(g, r, c)
            if (t.isEmpty()) continue
            val n = Ean.numeric(t)
            if (n != null && n.length in 8..14 && (Ean.checkOk(n) || Ean.checkOk(n.padStart(13, '0')) || Ean.checkOk(n.padStart(12, '0')))) bc[c] += 2
            else if (Ean.looksLikeBarcode(t) && n != null && n.length >= 8) bc[c]++
            if (isCode(t)) cd[c]++
            if (isName(t)) nm[c]++
        }
        val barcodeCol = bc.indices.maxByOrNull { bc[it] } ?: -1
        if (barcodeCol < 0 || bc[barcodeCol] < 2) throw NotBarcodes("no_barcode_column")
        val nameCol = nm.indices.filter { it != barcodeCol }.maxByOrNull { nm[it] }?.takeIf { nm[it] > 0 } ?: -1
        val codeCol = cd.indices.filter { it != barcodeCol && it != nameCol }.maxByOrNull { cd[it] }?.takeIf { cd[it] > 0 } ?: -1
        val rows = ArrayList<Row>()
        var skipped = 0
        val seen = HashSet<String>()
        for (r in g.rows.indices) {
            val b = text(g, r, barcodeCol)
            if (!Ean.looksLikeBarcode(b)) { if (b.isNotEmpty()) skipped++; continue }
            val key = Ean.canonical(b)
            if (!seen.add(key + "|" + (if (codeCol >= 0) text(g, r, codeCol) else ""))) continue
            val code = if (codeCol >= 0) text(g, r, codeCol) else ""
            val name = if (nameCol >= 0) text(g, r, nameCol) else ""
            rows += Row(code.replace(" ", "").replace(" ", ""), name, b.replace(" ", ""))
        }
        if (rows.isEmpty()) throw NotBarcodes("no_rows")
        return Result(rows, barcodeCol, codeCol, nameCol, skipped)
    }
}

/** Import qatorlarini katalogga bog'lash. */
object BarcodeLink {
    class Linked(
        /** kanonik shtrix kod -> tovar id. */
        val map: Map<String, String>,
        /** 1C kodi (oldidagi nollarsiz) -> tovar id — tarozi yorlig'idagi tovar kodi uchun ham. */
        val codes: Map<String, String>,
        val byCode: Int,
        val byName: Int,
        val unmatched: List<BarcodeImport.Row>,
    )

    fun codeKey(code: String): String {
        val t = code.trim().replace(" ", "").replace(" ", "")
        return if (t.isNotEmpty() && t.all { it.isDigit() }) t.trimStart('0').ifEmpty { "0" } else t.uppercase()
    }

    fun <T> link(rows: List<BarcodeImport.Row>, items: List<T>, id: (T) -> String, name: (T) -> String): Linked {
        val byId = HashMap<String, String>()
        for (it in items) byId.putIfAbsent(codeKey(id(it)), id(it))
        val byNameMap = HashMap<String, String?>()
        for (it in items) {
            val k = LedgerMerge.nameKey(name(it))
            // Bir xil nomli ikki tovar — nom bo'yicha bog'lanmaydi (noto'g'ri tovar tushmasin)
            byNameMap[k] = if (byNameMap.containsKey(k) && byNameMap[k] != id(it)) null else id(it)
        }
        val map = HashMap<String, String>()
        val codes = HashMap<String, String>()
        var c1 = 0; var c2 = 0
        val un = ArrayList<BarcodeImport.Row>()
        for (r in rows) {
            val pid = (if (r.code.isNotEmpty()) byId[codeKey(r.code)] else null)?.also { c1++ }
                ?: byNameMap[LedgerMerge.nameKey(r.name)]?.also { c2++ }
            if (pid == null) { un += r; continue }
            map[Ean.canonical(r.barcode)] = pid
            if (r.code.isNotEmpty()) codes[codeKey(r.code)] = pid
        }
        // Katalogdagi id lar ham 1C kodi bo'lishi mumkin (tarozi kodi uchun)
        for ((k, v) in byId) codes.putIfAbsent(k, v)
        return Linked(map, codes, c1, c2, un)
    }
}

/** Skanerlangan matnni tovarga aylantiradi. */
class BarcodeResolver(
    /** kanonik shtrix kod -> tovar id (import + o'rganilgan). */
    private val barcodes: Map<String, String>,
    /** tarozi tovar kodi -> tovar id (o'rganilgan). */
    private val scaleCodes: Map<String, String>,
    /** 1C / katalog kodi -> tovar id. */
    private val codes: Map<String, String>,
    private val scale: ScaleFormat,
) {
    sealed class Result {
        abstract val raw: String
        /** [qty] — tarozidan bo'lsa og'irlik (kg), aks holda null (odatdagi miqdor). */
        class Found(override val raw: String, val productId: String, val qty: Double?, val via: Via) : Result()
        /**
         * Topilmadi. [scaleCode] — tarozi yorlig'i bo'lsa tovar kodi (o'rganish shu kodga bog'lanadi);
         * [suggestion] — shu kodli tovar (1C kodi bo'yicha) — foydalanuvchi bir marta tasdiqlaydi.
         */
        class Unknown(override val raw: String, val scaleCode: String?, val qty: Double?, val suggestion: String? = null) : Result()
    }

    enum class Via { BARCODE, SCALE_BARCODE, SCALE_LEARNED, SCALE_CODE }

    fun resolve(raw0: String): Result {
        val raw = raw0.trim()
        val key = Ean.canonical(raw)
        barcodes[key]?.let { return Result.Found(raw, it, null, Via.BARCODE) }
        val n = Ean.numeric(raw)
        // 1C'da kod tekshiruv raqamisiz saqlangan bo'lishi mumkin ("250000000031" -> yorliqda EAN-13 "2500000000310")
        if (n != null && n.length in setOf(8, 13, 14) && Ean.checkOk(n)) {
            barcodes[Ean.canonical(n.dropLast(1))]?.let { return Result.Found(raw, it, null, Via.BARCODE) }
        }
        val hit = scale.parse(raw)
        if (hit != null && n != null) {
            // 1C usuli: vazn tovarining shtrix kodi = yorliqning boshi (prefiks + tovar kodi), masalan 2900027
            barcodes[Ean.canonical(n.take(n.length - scale.valueLen - 1))]?.let { return Result.Found(raw, it, hit.qty, Via.SCALE_BARCODE) }
            scaleCodes[hit.itemCode]?.let { return Result.Found(raw, it, hit.qty, Via.SCALE_LEARNED) }
            val byCode = codes[hit.itemCode]
            if (byCode != null && scale.trustCodes) return Result.Found(raw, byCode, hit.qty, Via.SCALE_CODE)
            return Result.Unknown(raw, hit.itemCode, hit.qty, byCode)
        }
        return Result.Unknown(raw, null, null)
    }
}
