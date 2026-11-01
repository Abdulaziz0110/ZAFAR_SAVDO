package uz.imsoft.shared.report

import java.util.Calendar

/**
 * v4.0 "Yagona ro'yxat": telefonda qilingan savdo/to'lov darhol ro'yxat tepasida ("1C'ga ketmoqda" belgisi bilan),
 * 1C'dan o'sha hujjat kelgach — u 1C yozuviga almashadi (ikki marta ko'rinmaydi).
 *
 * Moslash: mijoz nomi bir xil, turi bir xil (savdo / qaytarish / to'lov), summasi teng (0.5% yoki 1 so'm aniqlikda) va
 * 1C hujjati telefondagidan 15 daqiqa oldin — 3 kun keyin oralig'ida. Har 1C hujjati faqat bitta telefon yozuviga mos keladi.
 */
object PendingMerge {
    data class Line(val name: String, val qty: Double, val price: Double)

    data class Local(
        val key: String,
        /** Telefondagi vaqt (ms). */
        val at: Long,
        val kind: EntryKind,
        val storeId: String,
        val storeName: String,
        val lines: List<Line>,
        /** Savdo/qaytarish summasi yoki to'lov summasi. */
        val total: Double,
        val usd: Boolean = false,
    )

    private const val KEEP_MS = 3L * 86_400_000L

    fun stampOf(ms: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return Stamp.of(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND))
    }

    fun millisOf(stamp: Long): Long {
        val s = stamp.toString().padStart(14, '0')
        return Calendar.getInstance().apply {
            clear()
            set(s.substring(0, 4).toInt(), s.substring(4, 6).toInt() - 1, s.substring(6, 8).toInt(), s.substring(8, 10).toInt(), s.substring(10, 12).toInt(), s.substring(12, 14).toInt())
        }.timeInMillis
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private class Doc(val stamp: Long, val name: String, val kind: EntryKind, var som: Double, var usd: Double, var used: Boolean = false)

    /** 1C yozuvlarini hujjatlarga yig'adi (bir vaqt + bir mijoz + bir tur = bitta hujjat). */
    private fun docs(entries: List<LedgerEntry>): List<Doc> {
        val m = LinkedHashMap<String, Doc>()
        for (e in entries) {
            val name = norm(LedgerOwner.of(e.kindText)?.second.orEmpty())
            val kind = when (e.kind) { EntryKind.PAYMENT -> EntryKind.PAYMENT; EntryKind.RETURN -> EntryKind.RETURN; else -> EntryKind.SALE }
            val d = m.getOrPut("${e.stamp}|$name|${kind.code}") { Doc(e.stamp, name, kind, 0.0, 0.0) }
            val v = when (kind) {
                EntryKind.PAYMENT -> e.v(Col.PAYMENT)
                EntryKind.RETURN -> if (e.v(Col.RETURN) != Money2.ZERO) e.v(Col.RETURN) else e.v(Col.SUM)
                else -> e.v(Col.SUM)
            }
            d.som += kotlin.math.abs(v.som)
            d.usd += kotlin.math.abs(v.usd)
        }
        return m.values.toList()
    }

    private fun close(a: Double, b: Double) = kotlin.math.abs(a - b) <= maxOf(1.0, kotlin.math.abs(b) * 0.005)

    /** 1C'da hali yo'q telefon yozuvlari (eng yangisi birinchi). */
    fun unmatched(entries: List<LedgerEntry>, locals: List<Local>, now: Long = System.currentTimeMillis()): List<Local> {
        val recent = locals.filter { now - it.at < KEEP_MS && it.total > 0 }
        if (recent.isEmpty()) return emptyList()
        val from = stampOf(recent.minOf { it.at } - 15 * 60_000L)
        val pool = docs(entries.filter { it.stamp >= from })
        val out = ArrayList<Local>()
        for (l in recent.sortedBy { it.at }) {
            val name = norm(l.storeName)
            val lo = l.at - 15 * 60_000L
            val hi = l.at + KEEP_MS
            val hit = pool.firstOrNull { d ->
                !d.used && d.kind == l.kind && d.name == name && millisOf(d.stamp) in lo..hi &&
                    close(if (l.usd) d.usd else d.som, l.total)
            }
            if (hit != null) hit.used = true else out += l
        }
        return out.sortedByDescending { it.at }
    }

    /** 1C yozuvlari + hali 1C'ga yetmagan telefon yozuvlari ([label] — "1C'ga ketmoqda"). */
    fun merge(entries: List<LedgerEntry>, locals: List<Local>, label: String, now: Long = System.currentTimeMillis()): List<LedgerEntry> {
        val add = unmatched(entries, locals, now)
        if (add.isEmpty()) return entries
        val extra = ArrayList<LedgerEntry>()
        for (l in add) {
            val stamp = stampOf(l.at)
            val kt = LedgerOwner.tag(label, l.storeId, l.storeName)
            fun m(v: Double) = if (l.usd) Money2(usd = v) else Money2(som = v)
            if (l.kind == EntryKind.PAYMENT) {
                extra += LedgerEntry(stamp, EntryKind.PAYMENT, kt, "", 0.0, mapOf(Col.PAYMENT to m(l.total)))
            } else {
                val col = if (l.kind == EntryKind.RETURN) Col.RETURN else Col.SUM
                for (ln in l.lines) extra += LedgerEntry(stamp, l.kind, kt, ln.name, ln.qty, mapOf(Col.PRICE to m(ln.price), col to m(ln.qty * ln.price)))
            }
        }
        return (entries + extra).sortedBy { it.stamp }
    }
}
