package uz.imsoft.shared.report

import uz.imsoft.shared.Fold
import uz.imsoft.shared.NameMatcher
import uz.imsoft.shared.Tok
import uz.imsoft.shared.Tokenizer

/**
 * 1C tarixidan qidiruv. Bitta qatorga istalgan narsani aralash yozish mumkin:
 *  - tovar nomi (lotin/kirill, imlo xatosi, so'z boshi): "pista", "орик", "bodm";
 *  - miqdor: "40kg", "2,5 kg", "12 ta";
 *  - narx yoki summa: "200000", "200 000", "200k", "3,7mln", "15$", ">1mln", "1mln-5mln";
 *  - sana: "27.10", "27.10.2026", "27.10-29.10", "27 oktabr", "oktabr", "bugun", "kecha", "2026-10-27";
 *  - vaqt: "16:21";
 *  - turi: "savdo", "vozvrat", "to'lov", "chegirma"; valyuta: "dollar", "so'm";
 *  - istisno: "-pista" (pista bo'lgan hujjatlar chiqmaydi).
 * Barcha shartlar birga bajarilishi kerak (VA). Bir nechta sana/tur yozilsa — ulardan biri (YOKI).
 * Tovarga oid shartlar (nom, miqdor, narx) imkon qadar BITTA qatorda bajarilishi kerak: "pista 40" — 40 kg pista,
 * "pista bor, boshqa tovar 40 kg" emas. Bunday qator bo'lmasa — "taxminiy" natija.
 */
object LedgerQuery {

    sealed class Cond {
        /** Hujjat darajasidagi shart (sana, tur, valyuta, umumiy summa). null — bu shart hujjatga tegishli emas. */
        open fun onDoc(d: LedgerDoc): Boolean? = null
        /** Qator darajasidagi moslik. null — mos emas. */
        open fun onLine(d: LedgerDoc, e: LedgerEntry, m: NameMatcher): LineMatch? = null
        open val lineLevel: Boolean get() = false
        /** "Sana" guruhi (YOKI bilan birlashadi). */
        open val dateGroup: Boolean get() = false
        open val kindGroup: Boolean get() = false
    }

    class LineMatch(val fuzzy: Boolean, val range: IntRange?)

    // ---------------- hujjat shartlari ----------------

    data class DateRange(val from: Long, val to: Long) : Cond() {
        override fun onDoc(d: LedgerDoc) = d.stamp in from..to
        override val dateGroup get() = true
    }

    /** Kun.oy (yili har qanday). */
    data class DayMonth(val day: Int, val month: Int) : Cond() {
        override fun onDoc(d: LedgerDoc) = Stamp.dayOfMonth(d.stamp) == day && Stamp.month(d.stamp) == month
        override val dateGroup get() = true
    }

    data class Month(val month: Int, val year: Int?) : Cond() {
        override fun onDoc(d: LedgerDoc) = Stamp.month(d.stamp) == month && (year == null || Stamp.year(d.stamp) == year)
        override val dateGroup get() = true
    }

    data class Year(val year: Int) : Cond() {
        override fun onDoc(d: LedgerDoc) = Stamp.year(d.stamp) == year
        override val dateGroup get() = true
    }

    data class TimeOfDay(val hour: Int, val minute: Int?) : Cond() {
        override fun onDoc(d: LedgerDoc) = Stamp.hour(d.stamp) == hour && (minute == null || Stamp.minute(d.stamp) == minute)
    }

    data class Kind(val kinds: Set<EntryKind>) : Cond() {
        override fun onDoc(d: LedgerDoc) = d.kind in kinds
        override val kindGroup get() = true
    }

    data class Currency(val usd: Boolean) : Cond() {
        override fun onDoc(d: LedgerDoc) = if (usd) d.hasUsd else d.hasSom
    }

    /** Hujjat summasi bo'yicha: ">1mln", "<500000", "1mln-5mln". */
    data class Total(val min: Double, val max: Double, val usd: Boolean?) : Cond() {
        override fun onDoc(d: LedgerDoc): Boolean {
            fun ok(v: Double) = v != 0.0 && v >= min - EPS && v <= max + EPS
            return when (usd) {
                true -> ok(d.total.usd)
                false -> ok(d.total.som)
                null -> ok(d.total.som) || ok(d.total.usd)
            }
        }
    }

    // ---------------- qator shartlari ----------------

    /** Tovar nomi so'zi. Hujjat darajasida — to'lov izohi va hujjat turi matni ("Savdo So'm"). */
    class Text(val tok: Tok, val raw: String, val exclude: Boolean = false, private val fuzzyAllowed: Boolean = true) : Cond() {
        override val lineLevel get() = true
        private var docCache: Pair<LedgerDoc, Boolean>? = null
        override fun onDoc(d: LedgerDoc): Boolean {
            docCache?.let { if (it.first === d) return it.second }
            val text = d.kindText + " " + d.comment
            val r = NameMatcher(text).match(tok, false)?.let { it.score >= 55 } ?: false
            docCache = d to r
            return r
        }
        override fun onLine(d: LedgerDoc, e: LedgerEntry, m: NameMatcher): LineMatch? {
            if (e.product.isBlank()) return null
            val h = m.match(tok, fuzzyAllowed && !exclude) ?: return null
            return LineMatch(h.fuzzy, h.range)
        }
        override fun toString() = (if (exclude) "-" else "") + raw
    }

    /** Miqdor (aniq yoki oraliq). */
    data class Qty(val min: Double, val max: Double) : Cond() {
        override val lineLevel get() = true
        override fun onDoc(d: LedgerDoc) = false
        override fun onLine(d: LedgerDoc, e: LedgerEntry, m: NameMatcher): LineMatch? =
            if (e.qty != 0.0 && Math.abs(e.qty) >= min - QEPS && Math.abs(e.qty) <= max + QEPS) LineMatch(false, null) else null
    }

    /**
     * Son (birligi aniq emas): miqdor, narx, qator summasi, hujjat summasi yoki tovar nomidagi raqam ("Keshu 210").
     * [usd] — valyuta aytilgan bo'lsa ("15$", "200 ming so'm") faqat o'sha valyutadagi summalar; [amountOnly] — "k/mln/so'm/$"
     * yozilgan: miqdor va nomdagi raqam hisobga olinmaydi.
     */
    data class Number(val min: Double, val max: Double, val text: String, val usd: Boolean? = null, val amountOnly: Boolean = false) : Cond() {
        override val lineLevel get() = true
        private fun hit(v: Double) = v != 0.0 && Math.abs(v) >= min - EPS && Math.abs(v) <= max + EPS
        private fun hit(m: Money2) = when (usd) {
            true -> hit(m.usd)
            false -> hit(m.som)
            null -> hit(m.som) || hit(m.usd)
        }
        override fun onDoc(d: LedgerDoc) = hit(d.total) || (!d.discount.isZero && hit(d.discount))
        override fun onLine(d: LedgerDoc, e: LedgerEntry, m: NameMatcher): LineMatch? {
            if (!amountOnly && e.qty != 0.0 && Math.abs(e.qty) >= min - QEPS && Math.abs(e.qty) <= max + QEPS) return LineMatch(false, null)
            if (hit(e.v(Col.PRICE)) || hit(e.v(Col.SUM)) || hit(LedgerDoc.amountOf(e)) || hit(e.v(Col.DISCOUNT))) return LineMatch(false, null)
            if (!amountOnly && min == max && text.isNotEmpty() && e.product.isNotBlank()) {
                val h = m.match(Tok(text, 0, text.length), false)
                if (h != null && h.score >= 85) return LineMatch(false, h.range)
            }
            return null
        }
    }

    /** Ikki ma'noli so'z: "27.10" (sana yoki 27,1), "oktabr" (oy yoki tovar nomi). Biri bajarilsa yetarli. */
    class Either(val a: Cond, val b: Cond) : Cond() {
        override val lineLevel get() = a.lineLevel || b.lineLevel
        override fun onDoc(d: LedgerDoc): Boolean? {
            val x = a.onDoc(d)
            val y = b.onDoc(d)
            return if (x == null && y == null) null else (x == true || y == true)
        }
        override fun onLine(d: LedgerDoc, e: LedgerEntry, m: NameMatcher) = a.onLine(d, e, m) ?: b.onLine(d, e, m)
        override fun toString() = "($a|$b)"
    }

    /** Bir nechta sana (yoki tur) shartlari — YOKI. */
    class AnyOf(val list: List<Cond>) : Cond() {
        override fun onDoc(d: LedgerDoc) = list.any { it.onDoc(d) == true }
        override fun toString() = list.joinToString("|", "[", "]")
    }

    class Parsed(val conds: List<Cond>, val excludes: List<Text>) {
        val isEmpty get() = conds.isEmpty() && excludes.isEmpty()
    }

    private const val EPS = 0.005
    private const val QEPS = 0.0005

    // ---------------- tahlil ----------------

    private val DATE_RE = Regex("^(\\d{1,2})[./](\\d{1,2})(?:[./](\\d{2}|\\d{4}))?$")
    private val ISO_RE = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$")
    private val TIME_RE = Regex("^(\\d{1,2}):(\\d{2})$")
    private val CMP_RE = Regex("^(>=|<=|=>|=<|>|<)(.+)$")
    private val NUM_RE = Regex("^\\d[\\d.,]*$")
    private val NUM_SUFFIX_RE = Regex("^(\\d[\\d.,]*)([a-z$']+)$")

    private val MONTHS: Map<String, Int> = buildMap {
        fun add(m: Int, vararg words: String) { for (w in words) put(w, m) }
        add(1, "yanvar", "yanvarya", "january", "jan")
        add(2, "fevral", "fevralya", "february", "feb")
        add(3, "mart", "marta", "march")
        add(4, "aprel", "aprelya", "april")
        add(5, "mayya", "maya")
        add(6, "iyun", "iyunya", "iun", "iunya", "june")
        add(7, "iyul", "iyulya", "iul", "iulya", "july")
        add(8, "avgust", "avgusta", "august")
        add(9, "sentabr", "sentyabr", "sentyabrya", "sentabrya", "september")
        add(10, "oktabr", "oktyabr", "oktyabrya", "oktabrya", "october")
        add(11, "noyabr", "noyabrya", "november")
        add(12, "dekabr", "dekabrya", "december")
    }
    /** Yozilayotgan oy nomi (3+ harf) — tovar nomi bilan birga tekshiriladi. */
    private val MONTH_PREFIX = listOf("yan" to 1, "fev" to 2, "apr" to 4, "avg" to 8, "sen" to 9, "okt" to 10, "noy" to 11, "dek" to 12)

    private val KIND_WORDS: Map<String, Set<EntryKind>> = buildMap {
        for (w in listOf("savdo", "sotuv", "sotildi", "sotilgan", "xarid", "olgan", "olingan", "prodaja", "realizatsiya")) put(w, setOf(EntryKind.SALE))
        for (w in listOf("vozvrat", "vazvrat", "qaytarish", "qaytarib", "qaytgan", "qaytardi", "qaytarilgan", "qaytar", "kaytarish", "vozvratlar"))
            put(w, setOf(EntryKind.RETURN))
        for (w in listOf("tolov", "tolovlar", "tulov", "pul", "oplata", "tolangan", "tolagan", "berilgan")) put(w, setOf(EntryKind.PAYMENT, EntryKind.PAYMENT_RETURN))
        for (w in listOf("chegirma", "skidka")) put(w, setOf(EntryKind.DISCOUNT))
    }
    private val USD_WORDS = setOf("$", "usd", "dollar", "dollr", "dollor")
    private val SOM_WORDS = setOf("som", "sum", "uzs", "sm")
    private val QTY_UNITS = setOf("kg", "kilo", "kilogram", "kilogramm", "kgs", "ta", "dona", "sht", "shtuk", "pcs")
    private val K_UNITS = setOf("k", "ming", "minq", "tis", "tys", "tysyach")
    private val M_UNITS = setOf("m", "mln", "million", "milion", "mil")
    private val STOP = setOf("dan", "gacha", "oyi", "oy", "va", "bilan", "yil", "yili", "kuni", "soat", "s", "po", "do", "ot")

    private fun foldWord(t: String) = Fold.key(t).replace(" ", "")

    /** "200 000", "1 250 000,50" kabi bo'lingan sonlarni birlashtiradi; "2,5 kg" -> "2,5kg"; "$ 15" -> "$15". */
    private fun mergeTokens(raw: List<String>): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < raw.size) {
            var t = raw[i]
            if (Regex("^\\d{1,3}$").matches(t)) {
                while (i + 1 < raw.size && Regex("^\\d{3}([.,]\\d+)?$").matches(raw[i + 1])) { t += raw[i + 1]; i++ }
            }
            // birlik yoki "ming/mln/so'm/$" alohida yozilgan
            if (NUM_RE.matches(t) && i + 1 < raw.size) {
                val u = foldWord(raw[i + 1]).ifEmpty { raw[i + 1] }
                if (u in QTY_UNITS || u in K_UNITS || u in M_UNITS || u in SOM_WORDS || raw[i + 1] in USD_WORDS || u in USD_WORDS) {
                    t += if (raw[i + 1] == "$") "$" else u
                    i++
                }
            }
            if (t == "$" && i + 1 < raw.size && NUM_RE.matches(raw[i + 1])) { t = raw[i + 1] + "$"; i++ }
            if (t.startsWith("$") && t.length > 1 && NUM_RE.matches(t.substring(1))) t = t.substring(1) + "$"
            out += t
            i++
        }
        // "27.10 - 29.10", "27.10 dan 29.10 gacha"
        val res = ArrayList<String>()
        var j = 0
        while (j < out.size) {
            if (j + 2 < out.size && out[j + 1] in setOf("-", "–", "—", "..", "dan") && isDateLike(out[j]) && isDateLike(out[j + 2])) {
                res += out[j] + "-" + out[j + 2]
                j += 3
                continue
            }
            res += out[j]
            j++
        }
        return res
    }

    private fun isDateLike(t: String) = DATE_RE.matches(t) || ISO_RE.matches(t)

    /** "27.10" -> (27,10,null); "27.10.26" -> (27,10,2026). */
    private fun parseDate(t: String): Triple<Int, Int, Int?>? {
        ISO_RE.matchEntire(t)?.let { m ->
            val y = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt(); val d = m.groupValues[3].toInt()
            return if (valid(d, mo)) Triple(d, mo, y) else null
        }
        val m = DATE_RE.matchEntire(t) ?: return null
        val d = m.groupValues[1].toInt()
        val mo = m.groupValues[2].toInt()
        val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
        return if (valid(d, mo)) Triple(d, mo, y) else null
    }

    private fun valid(d: Int, m: Int) = d in 1..31 && m in 1..12

    private fun dateCond(p: Triple<Int, Int, Int?>): Cond =
        if (p.third != null) Stamp.of(p.third!!, p.second, p.first).let { DateRange(it, Stamp.dayEnd(it)) } else DayMonth(p.first, p.second)

    /** Son + qo'shimcha: (qiymat, valyuta usd?, faqat summa?, miqdormi?). */
    private class NumSpec(val value: Double, val usd: Boolean?, val amountOnly: Boolean, val qty: Boolean, val text: String)

    private val NUM_ANY_SUFFIX_RE = Regex("^(\\d[\\d.,]*)([\\p{L}'’]+)$")

    private fun parseNum(t0: String): NumSpec? {
        var t = t0.removePrefix("=")
        // Kirillcha birlik: "40кг", "3,7млн", "200минг", "5000сўм" -> lotin
        NUM_ANY_SUFFIX_RE.matchEntire(t)?.let { m -> t = m.groupValues[1] + foldWord(m.groupValues[2]) }
        if (NUM_RE.matches(t)) {
            val v = Num.parse(t) ?: return null
            return NumSpec(v, null, false, false, t.filter { it.isDigit() }.takeIf { t.all { c -> c.isDigit() } }.orEmpty())
        }
        val m = NUM_SUFFIX_RE.matchEntire(t) ?: return null
        val v = Num.parse(m.groupValues[1]) ?: return null
        val suf = m.groupValues[2].replace("'", "")
        return when {
            suf in QTY_UNITS -> NumSpec(v, null, false, true, "")
            suf in K_UNITS -> NumSpec(v * 1000, null, true, false, "")
            suf in M_UNITS -> NumSpec(v * 1_000_000, null, true, false, "")
            suf in SOM_WORDS -> NumSpec(v, false, true, false, "")
            suf == "$" || suf in USD_WORDS -> NumSpec(v, true, true, false, "")
            suf.endsWith("som") || suf.endsWith("sum") -> {
                val k = suf.dropLast(3)
                val mul = when (k) { in K_UNITS -> 1000.0; in M_UNITS -> 1_000_000.0; "" -> 1.0; else -> return null }
                NumSpec(v * mul, false, true, false, "")
            }
            else -> null
        }
    }

    fun parse(query: String, today: Long): Parsed {
        val raw = query.lowercase().replace('\u00A0', ' ').replace('’', '\'').replace('ʻ', '\'').replace('‘', '\'')
            .split(' ', '\t', '\n').filter { it.isNotBlank() }
        val tokens = mergeTokens(raw)
        val conds = ArrayList<Cond>()
        val excludes = ArrayList<Text>()
        val pos = intArrayOf(0)
        while (pos[0] < tokens.size) {
            val t = tokens[pos[0]].trim(',', ';')
            pos[0]++
            if (t.isEmpty() || t == "-") continue
            classify(t, tokens, pos, today, conds, excludes)
        }
        // Sana va tur shartlari YOKI bilan birlashadi. Ikki va undan ko'p sana yozilsa "27.10" kabilar faqat sana deb olinadi.
        val dateLike = conds.count { it.dateGroup || (it is Either && it.a.dateGroup) }
        val norm = if (dateLike > 1) conds.map { if (it is Either && it.a.dateGroup) it.a else it } else conds
        val dates = norm.filter { it.dateGroup }
        val kinds = norm.filter { it.kindGroup }
        val out = ArrayList<Cond>(norm.filter { !it.dateGroup && !it.kindGroup })
        if (dates.size == 1) out += dates[0] else if (dates.size > 1) out += AnyOf(dates)
        if (kinds.size == 1) out += kinds[0] else if (kinds.size > 1) out += AnyOf(kinds)
        return Parsed(out, excludes)
    }

    private val YEAR_RE = Regex("^(19|20)\\d\\d$")

    private fun classify(t: String, tokens: List<String>, pos: IntArray, today: Long, conds: MutableList<Cond>, excludes: MutableList<Text>) {
        // istisno: "-pista"
        if (t.length > 1 && (t[0] == '-' || t[0] == '!') && t[1].isLetter()) {
            Tokenizer.tokens(Fold.key(t.substring(1))).forEach { excludes += Text(it, t.substring(1), exclude = true) }
            return
        }
        // solishtirish: ">1mln", "<=500000"
        val cmp = CMP_RE.matchEntire(t)
        if (cmp != null) {
            val n = parseNum(cmp.groupValues[2])
            if (n != null) {
                val op = cmp.groupValues[1]
                val strictGap = if (op.contains("=")) 0.0 else 0.01
                conds += if (op.contains(">")) Total(n.value + strictGap, Double.MAX_VALUE, n.usd) else Total(0.0, n.value - strictGap, n.usd)
                return
            }
        }
        // oraliq: "27.10-29.10", "10-20", "1mln-5mln"
        if ((t.contains('-') || t.contains("..") || t.contains('–')) && !ISO_RE.matches(t)) {
            val parts = if (t.contains("..")) t.split("..") else t.split('-', '–', '—')
            if (parts.size == 2 && parts.all { it.isNotEmpty() }) {
                val a = parseDate(parts[0])
                val b = parseDate(parts[1])
                if (a != null && b != null) {
                    val y = b.third ?: a.third ?: Stamp.year(today)
                    val ya = a.third ?: if (a.second > b.second) y - 1 else y
                    val from = Stamp.of(ya, a.second, a.first)
                    val to = Stamp.dayEnd(Stamp.of(y, b.second, b.first))
                    conds += DateRange(minOf(from, Stamp.dayStart(to)), maxOf(Stamp.dayEnd(from), to))
                    return
                }
                val na = parseNum(parts[0])
                val nb = parseNum(parts[1])
                if (na != null && nb != null) {
                    val lo = minOf(na.value, nb.value)
                    val hi = maxOf(na.value, nb.value)
                    conds += if (na.qty || nb.qty) Qty(lo, hi) else Number(lo, hi, "", na.usd ?: nb.usd, na.amountOnly || nb.amountOnly)
                    return
                }
            }
        }
        // sana
        val date = parseDate(t)
        if (date != null) {
            if (date.third != null) { conds += dateCond(date); return }
            // "27.10" — sana yoki kasr son (27,1)
            val n = Num.parse(t)
            conds += if (n != null) Either(dateCond(date), Number(n, n, "")) else dateCond(date)
            return
        }
        // vaqt
        val tm = TIME_RE.matchEntire(t)
        if (tm != null) {
            val h = tm.groupValues[1].toInt()
            val mi = tm.groupValues[2].toInt()
            if (h in 0..23 && mi in 0..59) { conds += TimeOfDay(h, mi); return }
        }
        // son
        val n = parseNum(t)
        if (n != null) {
            // "27 oktabr", "27 okt 2026"
            val month = tokens.getOrNull(pos[0])?.let { MONTHS[foldWord(it)] }
            if (n.text.isNotEmpty() && n.text.length <= 2 && month != null && n.value in 1.0..31.0) {
                pos[0]++
                val y = tokens.getOrNull(pos[0])?.takeIf { YEAR_RE.matches(it) }?.toInt()
                if (y != null) pos[0]++
                conds += dateCond(Triple(n.value.toInt(), month, y))
                return
            }
            conds += when {
                n.qty -> Qty(n.value, n.value)
                n.text.length == 4 && n.value in 2000.0..2100.0 -> Either(Year(n.value.toInt()), Number(n.value, n.value, n.text))
                else -> Number(n.value, n.value, n.text, n.usd, n.amountOnly)
            }
            return
        }
        // so'zlar
        val w = foldWord(t)
        if (t in USD_WORDS || w in USD_WORDS) { conds += Currency(true); return }
        if (w.isEmpty()) return
        if (w in SOM_WORDS) { conds += Currency(false); return }
        if (w in STOP) return
        if (w == "bugun" || w == "segodnya") { val d = Stamp.dayStart(today); conds += DateRange(d, Stamp.dayEnd(d)); return }
        if (w == "kecha" || w == "vchera") { val d = Stamp.addDays(today, -1); conds += DateRange(d, Stamp.dayEnd(d)); return }
        val m = MONTHS[w]
        if (m != null) {
            val y = tokens.getOrNull(pos[0])?.takeIf { YEAR_RE.matches(it) }?.toInt()
            if (y != null) pos[0]++
            conds += Month(m, y)
            return
        }
        val textConds = Tokenizer.tokens(Fold.key(t)).map { Text(it, t) }
        val k = KIND_WORDS[w]
        if (k != null) {
            conds += if (textConds.size == 1) Either(Kind(k), textConds[0]) else Kind(k)
            return
        }
        if (textConds.size == 1 && w.length >= 3 && monthWordStartsWith(w)) {
            val mp = MONTH_PREFIX.firstOrNull { w.startsWith(it.first) }
            if (mp != null) { conds += Either(Month(mp.second, null), textConds[0]); return }
        }
        conds += textConds
    }

    private fun monthWordStartsWith(w: String) = MONTHS.keys.any { it.startsWith(w) && it.length > w.length }
}

/** Ro'yxatdagi qo'shimcha filtr (chip va davr). */
data class LedgerFilter(val kinds: Set<EntryKind>? = null, val from: Long = 0L, val to: Long = Long.MAX_VALUE)

class DocHit(
    val doc: LedgerDoc,
    /** Mos kelgan qatorlar (bo'sh — butun hujjat). */
    val lines: Set<Int>,
    /** Qator raqami -> nomdagi topilgan qismlar (qalin ko'rsatish uchun). */
    val ranges: Map<Int, List<IntRange>>,
    val approximate: Boolean,
)

class LedgerSummary(
    val docs: Int,
    val sales: Int, val salesTotal: Money2,
    val returns: Int, val returnsTotal: Money2,
    val payments: Int, val paymentsTotal: Money2,
    /** Mos kelgan tovar qatorlari bo'yicha (qidiruvda tovar sharti bo'lsa — faqat o'sha qatorlar). */
    val goodsQty: Double, val goodsSum: Money2, val goodsLines: Int,
    val returnQty: Double, val returnSum: Money2,
)

class LedgerResult(val hits: List<DocHit>, val approximate: Boolean, val summary: LedgerSummary, val hasLineConds: Boolean)

/** Tez qidiruv uchun indeks (har qator nomi oldindan buklanadi). */
class LedgerIndex(entries: List<LedgerEntry>) {
    val docs: List<LedgerDoc> = LedgerDocs.build(entries)
    private val matchers: Map<LedgerEntry, NameMatcher>

    init {
        val m = java.util.IdentityHashMap<LedgerEntry, NameMatcher>()
        val byName = HashMap<String, NameMatcher>()
        for (d in docs) for (e in d.lines) m[e] = byName.getOrPut(e.product) { NameMatcher(e.product) }
        matchers = m
    }

    private fun matcher(e: LedgerEntry) = matchers[e] ?: NameMatcher(e.product)

    fun byKey(key: String): LedgerDoc? = docs.firstOrNull { it.key == key }

    fun search(query: String, today: Long, filter: LedgerFilter = LedgerFilter(), fuzzy: Boolean = true): LedgerResult {
        val base = docs.filter { (filter.kinds == null || it.kind in filter.kinds) && it.stamp >= filter.from && it.stamp <= filter.to }
        val parsed = LedgerQuery.parse(query, today)
        if (parsed.isEmpty) return result(base.map { DocHit(it, emptySet(), emptyMap(), false) }, false, false)
        val conds = if (fuzzy) parsed.conds else parsed.conds.map { noFuzzy(it) }
        val hasLine = conds.any { it.lineLevel }
        var (hits, approx) = run(base, conds, parsed.excludes)
        if (hits.isEmpty() && query.any { it.isLetter() }) {
            // Klaviatura tili adashgan bo'lishi mumkin ("ьфнша" -> "mayiz")
            val alt = LedgerQuery.parse(Fold.swapLayout(query), today)
            if (!alt.isEmpty) {
                val r = run(base, alt.conds, alt.excludes)
                if (r.first.isNotEmpty()) { hits = r.first.map { DocHit(it.doc, it.lines, it.ranges, true) }; approx = true }
            }
        }
        if (hits.isEmpty()) {
            // Son boshi bo'yicha ("3762" -> 3 762 000) — faqat taxminiy
            val nums = conds.filterIsInstance<LedgerQuery.Number>().filter { it.text.length >= 3 && it.min == it.max }
            if (nums.size == 1 && conds.size == 1) {
                val p = nums[0].text
                hits = base.mapNotNull { d ->
                    val idx = d.lines.indices.filter { i ->
                        val e = d.lines[i]
                        listOf(e.v(Col.PRICE), e.v(Col.SUM), LedgerDoc.amountOf(e)).any { m -> startsWithDigits(m.som, p) || startsWithDigits(m.usd, p) }
                    }.toSet()
                    if (idx.isNotEmpty() || startsWithDigits(d.total.som, p)) DocHit(d, idx, emptyMap(), true) else null
                }
                approx = hits.isNotEmpty()
            }
        }
        return result(hits, approx, hasLine)
    }

    private fun noFuzzy(c: LedgerQuery.Cond): LedgerQuery.Cond = when (c) {
        is LedgerQuery.Text -> LedgerQuery.Text(c.tok, c.raw, c.exclude, fuzzyAllowed = false)
        is LedgerQuery.Either -> LedgerQuery.Either(noFuzzy(c.a), noFuzzy(c.b))
        else -> c
    }

    private fun startsWithDigits(v: Double, prefix: String): Boolean {
        if (v == 0.0) return false
        val s = Math.round(Math.abs(v)).toString()
        return s.length > prefix.length && s.startsWith(prefix)
    }

    private fun run(base: List<LedgerDoc>, conds: List<LedgerQuery.Cond>, excludes: List<LedgerQuery.Text>): Pair<List<DocHit>, Boolean> {
        val strict = ArrayList<DocHit>()
        val relaxed = ArrayList<DocHit>()
        val lineConds = conds.filter { it.lineLevel }
        val docConds = conds.filter { !it.lineLevel }
        for (d in base) {
            if (docConds.any { it.onDoc(d) != true }) continue
            if (excludes.isNotEmpty() && excludes.any { x -> d.lines.any { e -> x.onLine(d, e, matcher(e)) != null } }) continue
            if (lineConds.isEmpty()) { strict += DocHit(d, emptySet(), emptyMap(), false); continue }
            val docSat = BooleanArray(lineConds.size) { lineConds[it].onDoc(d) == true }
            val n = d.lines.size
            val sat = Array(lineConds.size) { arrayOfNulls<LedgerQuery.LineMatch>(n) }
            // Hujjat darajasida bajarilgan shart (masalan "to'lov" — turi) qatorlarga qaralmaydi (taxminiy belgisi chiqmasin).
            for ((ci, c) in lineConds.withIndex()) if (!docSat[ci]) for (li in 0 until n) sat[ci][li] = c.onLine(d, d.lines[li], matcher(d.lines[li]))
            val strictLines = (0 until n).filter { li -> lineConds.indices.all { ci -> docSat[ci] || sat[ci][li] != null } }
                .filter { li -> lineConds.indices.any { ci -> sat[ci][li] != null } || lineConds.indices.all { docSat[it] } }
            val fuzzyUsed = { lines: Collection<Int> -> lines.any { li -> lineConds.indices.any { ci -> sat[ci][li]?.fuzzy == true } } }
            fun ranges(lines: Collection<Int>): Map<Int, List<IntRange>> = lines.associateWith { li ->
                NameMatcher.mergeRanges(lineConds.indices.mapNotNull { ci -> sat[ci][li]?.range })
            }.filterValues { it.isNotEmpty() }
            if (strictLines.isNotEmpty()) {
                val onlyDoc = lineConds.indices.all { docSat[it] } && strictLines.none { li -> lineConds.indices.any { ci -> sat[ci][li] != null } }
                val lines = if (onlyDoc) emptySet() else strictLines.toSet()
                strict += DocHit(d, lines, ranges(lines), fuzzyUsed(lines))
                continue
            }
            if (lineConds.indices.all { docSat[it] }) { strict += DocHit(d, emptySet(), emptyMap(), false); continue }
            // Shartlar turli qatorlarda bajarildi — taxminiy
            if (lineConds.indices.all { ci -> docSat[ci] || sat[ci].any { it != null } }) {
                val lines = (0 until n).filter { li -> lineConds.indices.any { ci -> sat[ci][li] != null } }.toSet()
                relaxed += DocHit(d, lines, ranges(lines), true)
            }
        }
        if (strict.isNotEmpty()) return strict to strict.all { it.approximate }
        if (relaxed.isNotEmpty()) return relaxed to true
        // Bitta so'z tushib qolsa ham (kamida 2 ta tovar sharti bo'lsa) — taxminiy
        val texts = lineConds.filterIsInstance<LedgerQuery.Text>()
        if (texts.size >= 2) {
            val best = ArrayList<DocHit>()
            for (drop in texts) {
                val r = run(base, conds - drop, excludes).first
                for (h in r) if (best.none { it.doc === h.doc }) best += DocHit(h.doc, h.lines, h.ranges, true)
            }
            if (best.isNotEmpty()) return best.sortedByDescending { it.doc.stamp } to true
        }
        return emptyList<DocHit>() to false
    }

    private fun result(hits: List<DocHit>, approx: Boolean, hasLine: Boolean): LedgerResult {
        var sales = 0; var returns = 0; var pays = 0
        var st = Money2.ZERO; var rt = Money2.ZERO; var pt = Money2.ZERO
        var gq = 0.0; var gs = Money2.ZERO; var gl = 0; var rq = 0.0; var rs = Money2.ZERO
        for (h in hits) {
            val d = h.doc
            when (d.kind) {
                EntryKind.SALE -> { sales++; st += d.total }
                EntryKind.RETURN -> { returns++; rt += d.total }
                EntryKind.PAYMENT, EntryKind.PAYMENT_RETURN -> { pays++; pt += if (d.kind == EntryKind.PAYMENT) d.total else Money2.ZERO - d.total }
                else -> Unit
            }
            val idx = if (h.lines.isEmpty()) d.lines.indices else h.lines
            for (i in idx) {
                val e = d.lines[i]
                when (e.kind) {
                    EntryKind.SALE -> { gq += e.qty; gs += e.v(Col.SUM); gl++ }
                    EntryKind.RETURN -> { rq += e.qty; rs += ClientStats.returnOf(e) }
                    else -> Unit
                }
            }
        }
        return LedgerResult(hits, approx, LedgerSummary(hits.size, sales, st, returns, rt, pays, pt, gq, gs, gl, rq, rs), hasLine)
    }
}
