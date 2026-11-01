package uz.imsoft.shared.report

import java.util.Calendar

/**
 * v4.0 Statistika: 1C tarixi yozuvlaridan kun / hafta / oy / yil bo'yicha savdo, to'lov, qaytarish va foyda;
 * eng ko'p sotilgan tovarlar, narx o'zgarishlari. Sof mantiq — testlanadi, ekran faqat chizadi.
 * Pul so'mda: dollar [rate] bilan so'mga o'giriladi (kurs yo'q bo'lsa dollar qismi hisobga olinmaydi).
 */
object Stats {
    enum class Period(val buckets: Int) { DAY(14), WEEK(12), MONTH(12), YEAR(5) }

    data class Bucket(val label: String, val from: Long, val to: Long, val sales: Double, val payments: Double, val returns: Double, val profit: Double)

    data class Totals(val sales: Double, val payments: Double, val returns: Double, val profit: Double, val docs: Int)

    data class Top(val product: String, val qty: Double, val sum: Double, val lastStamp: Long)

    data class PricePoint(val stamp: Long, val price: Double)

    private fun cal(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }

    private fun som(m: Money2, rate: Double) = m.som + if (rate > 0) m.usd * rate else 0.0

    /** Yozuvning savdo summasi (so'm). */
    fun saleOf(e: LedgerEntry, rate: Double): Double = if (e.kind == EntryKind.SALE) kotlin.math.abs(som(e.v(Col.SUM), rate)) else 0.0
    fun paymentOf(e: LedgerEntry, rate: Double): Double = if (e.kind == EntryKind.PAYMENT) kotlin.math.abs(som(e.v(Col.PAYMENT), rate)) else 0.0
    fun returnOf(e: LedgerEntry, rate: Double): Double = if (e.kind == EntryKind.RETURN) kotlin.math.abs(som(if (e.v(Col.RETURN) != Money2.ZERO) e.v(Col.RETURN) else e.v(Col.SUM), rate)) else 0.0

    /** Foyda: (sotilgan narx − kelgan narx) × miqdor. [cost] — tovar nomi bo'yicha oxirgi kelgan narx (so'm) yoki null. */
    fun profitOf(e: LedgerEntry, rate: Double, cost: (String) -> Double?): Double {
        if (e.kind != EntryKind.SALE || e.qty == 0.0) return 0.0
        val c = cost(e.product) ?: return 0.0
        return saleOf(e, rate) - c * kotlin.math.abs(e.qty)
    }

    /** Davr bo'laklari: eng oxirgisi — joriy kun/hafta/oy/yil (oxirgi element). */
    fun ranges(period: Period, now: Long): List<Pair<Long, Long>> {
        val c = cal(now)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        when (period) {
            Period.DAY -> Unit
            Period.WEEK -> { c.firstDayOfWeek = Calendar.MONDAY; while (c.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) c.add(Calendar.DAY_OF_MONTH, -1) }
            Period.MONTH -> c.set(Calendar.DAY_OF_MONTH, 1)
            Period.YEAR -> { c.set(Calendar.DAY_OF_MONTH, 1); c.set(Calendar.MONTH, 0) }
        }
        val field = when (period) { Period.DAY -> Calendar.DAY_OF_MONTH; Period.WEEK -> Calendar.WEEK_OF_YEAR; Period.MONTH -> Calendar.MONTH; Period.YEAR -> Calendar.YEAR }
        val out = ArrayList<Pair<Long, Long>>()
        for (i in period.buckets - 1 downTo 0) {
            val a = (c.clone() as Calendar).apply { add(field, -i) }
            val b = (a.clone() as Calendar).apply { add(field, 1) }
            out += PendingMerge.stampOf(a.timeInMillis) to PendingMerge.stampOf(b.timeInMillis)
        }
        return out
    }

    private val MONTHS = listOf("yan", "fev", "mar", "apr", "may", "iyn", "iyl", "avg", "sen", "okt", "noy", "dek")

    fun label(period: Period, fromStamp: Long): String {
        val s = fromStamp.toString().padStart(14, '0')
        val y = s.substring(0, 4); val m = s.substring(4, 6).toInt(); val d = s.substring(6, 8).toInt()
        return when (period) {
            Period.DAY -> "$d"
            Period.WEEK -> "$d.${s.substring(4, 6)}"
            Period.MONTH -> MONTHS[(m - 1).coerceIn(0, 11)]
            Period.YEAR -> y
        }
    }

    fun series(entries: List<LedgerEntry>, period: Period, now: Long, rate: Double, cost: (String) -> Double? = { null }): List<Bucket> {
        val rs = ranges(period, now)
        val acc = Array(rs.size) { DoubleArray(4) }
        val start = rs.first().first
        for (e in entries) {
            if (e.stamp < start) continue
            val i = rs.indexOfFirst { e.stamp >= it.first && e.stamp < it.second }
            if (i < 0) continue
            acc[i][0] += saleOf(e, rate)
            acc[i][1] += paymentOf(e, rate)
            acc[i][2] += returnOf(e, rate)
            acc[i][3] += profitOf(e, rate, cost)
        }
        return rs.mapIndexed { i, r -> Bucket(label(period, r.first), r.first, r.second, acc[i][0], acc[i][1], acc[i][2], acc[i][3]) }
    }

    fun totals(entries: List<LedgerEntry>, fromStamp: Long, toStamp: Long, rate: Double, cost: (String) -> Double? = { null }): Totals {
        var s = 0.0; var p = 0.0; var r = 0.0; var pr = 0.0
        val docs = HashSet<String>()
        for (e in entries) {
            if (e.stamp < fromStamp || e.stamp >= toStamp) continue
            s += saleOf(e, rate); p += paymentOf(e, rate); r += returnOf(e, rate); pr += profitOf(e, rate, cost)
            if (e.kind == EntryKind.SALE) docs += "${e.stamp}|${e.kindText}"
        }
        return Totals(s, p, r, pr, docs.size)
    }

    /** Eng ko'p sotilgan tovarlar (summa bo'yicha). */
    fun top(entries: List<LedgerEntry>, fromStamp: Long, rate: Double, limit: Int = 20): List<Top> {
        val m = LinkedHashMap<String, DoubleArray>()
        val last = HashMap<String, Long>()
        for (e in entries) {
            if (e.kind != EntryKind.SALE || e.stamp < fromStamp || e.product.isBlank()) continue
            val a = m.getOrPut(e.product) { DoubleArray(2) }
            a[0] += kotlin.math.abs(e.qty); a[1] += saleOf(e, rate)
            if ((last[e.product] ?: 0L) < e.stamp) last[e.product] = e.stamp
        }
        return m.entries.map { Top(it.key, it.value[0], it.value[1], last[it.key] ?: 0L) }.sortedByDescending { it.sum }.take(limit)
    }

    /** Tovar narxining o'zgarishlari (har o'zgarish bir nuqta, vaqt bo'yicha). */
    fun priceHistory(entries: List<LedgerEntry>, product: String, rate: Double): List<PricePoint> {
        val out = ArrayList<PricePoint>()
        for (e in entries.filter { it.kind == EntryKind.SALE && it.product == product }.sortedBy { it.stamp }) {
            val p = som(e.v(Col.PRICE), rate)
            if (p <= 0) continue
            if (out.isEmpty() || kotlin.math.abs(out.last().price - p) > 0.009) out += PricePoint(e.stamp, p)
        }
        return out
    }

    /** Narxi o'zgargan tovarlar: (tovar, oldingi narx, yangi narx, vaqt) — eng yangisi birinchi. */
    fun priceChanges(entries: List<LedgerEntry>, fromStamp: Long, rate: Double, limit: Int = 30): List<Triple<String, Pair<Double, Double>, Long>> {
        val byProduct = entries.filter { it.kind == EntryKind.SALE && it.product.isNotBlank() }.groupBy { it.product }
        val out = ArrayList<Triple<String, Pair<Double, Double>, Long>>()
        for ((p, list) in byProduct) {
            val h = priceHistory(list, p, rate)
            for (i in 1 until h.size) if (h[i].stamp >= fromStamp) out += Triple(p, h[i - 1].price to h[i].price, h[i].stamp)
        }
        return out.sortedByDescending { it.third }.take(limit)
    }
}
