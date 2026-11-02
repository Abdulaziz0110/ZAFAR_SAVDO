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

    // ---------------- v4.0.1: ko'proq statistika ----------------

    data class Kpi(
        val sales: Double, val profit: Double, val payments: Double, val returns: Double,
        val docs: Int, val avg: Double, val clients: Int, val hasProfit: Boolean, val goodsLines: Int,
    )

    private fun owner(e: LedgerEntry): String = LedgerOwner.of(e.kindText)?.second?.trim().orEmpty()

    /** Asosiy ko'rsatkichlar: savdo, foyda, to'lov, qaytarish, hujjatlar (cheklar), o'rtacha chek, mijozlar soni. */
    fun kpi(entries: List<LedgerEntry>, fromStamp: Long, toStamp: Long, rate: Double, cost: (String) -> Double? = { null }): Kpi {
        var s = 0.0; var p = 0.0; var r = 0.0; var pr = 0.0; var withCost = false; var lines = 0
        val docs = HashSet<String>()
        val clients = HashSet<String>()
        for (e in entries) {
            if (e.stamp < fromStamp || e.stamp >= toStamp) continue
            s += saleOf(e, rate); p += paymentOf(e, rate); r += returnOf(e, rate)
            if (e.kind == EntryKind.SALE) {
                docs += "${e.stamp}|${LedgerOwner.plain(e.kindText)}|${owner(e)}"
                owner(e).takeIf { it.isNotEmpty() }?.let { clients += it.lowercase() }
                if (e.product.isNotBlank()) {
                    lines++
                    if (e.qty != 0.0 && cost(e.product) != null) { withCost = true; pr += profitOf(e, rate, cost) }
                }
            }
        }
        return Kpi(s, pr, p, r, docs.size, if (docs.isEmpty()) 0.0 else s / docs.size, clients.size, withCost, lines)
    }

    /**
     * Tez sotilayotgan tovarlar: davrda nechta KUNDA sotilgani (tezligi), kuniga o'rtacha miqdor va oldingi teng davrga
     * nisbatan o'zgarish (%). Avval eng ko'p kunlarda sotilganlar.
     */
    data class Fast(val product: String, val qty: Double, val sum: Double, val days: Int, val perDay: Double, val trend: Double?, val lastStamp: Long)

    fun fast(entries: List<LedgerEntry>, fromStamp: Long, toStamp: Long, rate: Double, limit: Int = 10): List<Fast> {
        val fromMs = PendingMerge.millisOf(fromStamp)
        val toMs = PendingMerge.millisOf(toStamp)
        val spanDays = ((toMs - fromMs) / 86_400_000L).coerceAtLeast(1L)
        val prevFrom = PendingMerge.stampOf(fromMs - (toMs - fromMs))
        class Acc { var q = 0.0; var s = 0.0; val days = HashSet<Long>(); var prevQ = 0.0; var last = 0L }
        val m = HashMap<String, Acc>()
        for (e in entries) {
            if (e.kind != EntryKind.SALE || e.product.isBlank()) continue
            if (e.stamp >= toStamp || e.stamp < prevFrom) continue
            val a = m.getOrPut(e.product) { Acc() }
            if (e.stamp >= fromStamp) {
                a.q += kotlin.math.abs(e.qty); a.s += saleOf(e, rate); a.days += e.day
                if (e.stamp > a.last) a.last = e.stamp
            } else a.prevQ += kotlin.math.abs(e.qty)
        }
        return m.entries.filter { it.value.days.isNotEmpty() }.map { (k, a) ->
            val trend = if (a.prevQ > 0) (a.q - a.prevQ) / a.prevQ * 100.0 else null
            Fast(k, a.q, a.s, a.days.size, a.q / spanDays, trend, a.last)
        }.sortedWith(compareByDescending<Fast> { it.days }.thenByDescending { it.sum }).take(limit)
    }

    /** Ulush (doiraviy grafik uchun): eng kattalari va qolgani bitta ("" nomli) bo'lak. */
    data class Slice(val label: String, val value: Double)

    private fun slices(m: Map<String, Double>, limit: Int): List<Slice> {
        val sorted = m.entries.filter { it.value > 0 }.sortedByDescending { it.value }
        val out = sorted.take(limit).map { Slice(it.key, it.value) }.toMutableList()
        val rest = sorted.drop(limit).sumOf { it.value }
        if (rest > 0) out += Slice("", rest)
        return out
    }

    /** Mijozlar ulushi (savdo summasi bo'yicha). Mijoz nomi yozuvda bo'lmasa — bo'sh ro'yxat. */
    fun byClient(entries: List<LedgerEntry>, fromStamp: Long, toStamp: Long, rate: Double, limit: Int = 5): List<Slice> {
        val m = HashMap<String, Double>()
        for (e in entries) {
            if (e.kind != EntryKind.SALE || e.stamp < fromStamp || e.stamp >= toStamp) continue
            val o = owner(e)
            if (o.isEmpty()) continue
            m[o] = (m[o] ?: 0.0) + saleOf(e, rate)
        }
        return if (m.size < 2) emptyList() else slices(m, limit)
    }

    /** Tovarlar ulushi (savdo summasi bo'yicha). */
    fun byGoods(entries: List<LedgerEntry>, fromStamp: Long, toStamp: Long, rate: Double, limit: Int = 5): List<Slice> {
        val m = HashMap<String, Double>()
        for (e in entries) {
            if (e.kind != EntryKind.SALE || e.product.isBlank() || e.stamp < fromStamp || e.stamp >= toStamp) continue
            m[e.product] = (m[e.product] ?: 0.0) + saleOf(e, rate)
        }
        return if (m.size < 2) emptyList() else slices(m, limit)
    }

    /** Hafta kunlari bo'yicha savdo (0 — dushanba … 6 — yakshanba). */
    fun weekdays(entries: List<LedgerEntry>, fromStamp: Long, toStamp: Long, rate: Double): DoubleArray {
        val out = DoubleArray(7)
        val c = Calendar.getInstance()
        for (e in entries) {
            if (e.kind != EntryKind.SALE || e.stamp < fromStamp || e.stamp >= toStamp) continue
            c.timeInMillis = PendingMerge.millisOf(e.stamp)
            out[(c.get(Calendar.DAY_OF_WEEK) + 5) % 7] += saleOf(e, rate)
        }
        return out
    }

    /** Kun soatlari bo'yicha savdo (faqat vaqti ma'lum yozuvlar). Hammasi 0 bo'lsa — vaqt yo'q. */
    fun hours(entries: List<LedgerEntry>, fromStamp: Long, toStamp: Long, rate: Double): DoubleArray {
        val out = DoubleArray(24)
        for (e in entries) {
            if (e.kind != EntryKind.SALE || e.stamp < fromStamp || e.stamp >= toStamp) continue
            val hms = e.stamp % 1_000_000
            if (hms == 0L) continue
            out[(hms / 10_000).toInt().coerceIn(0, 23)] += saleOf(e, rate)
        }
        return out
    }

    /** To'xtab qolgan tovarlar: oxirgi yilda sotilgan, lekin [idleDays] kundan beri sotilmagan (oldin ko'p sotilgani birinchi). */
    data class Idle(val product: String, val lastStamp: Long, val days: Int, val count: Int)

    fun idle(entries: List<LedgerEntry>, nowMs: Long, idleDays: Int = 14, limit: Int = 10): List<Idle> {
        val yearAgo = PendingMerge.stampOf(nowMs - 365L * 86_400_000L)
        val last = HashMap<String, Long>()
        val days = HashMap<String, HashSet<Long>>()
        for (e in entries) {
            if (e.kind != EntryKind.SALE || e.product.isBlank() || e.stamp < yearAgo) continue
            if ((last[e.product] ?: 0L) < e.stamp) last[e.product] = e.stamp
            days.getOrPut(e.product) { HashSet() } += e.day
        }
        val out = ArrayList<Idle>()
        for ((p, st) in last) {
            val d = ((nowMs - PendingMerge.millisOf(st)) / 86_400_000L).toInt()
            val n = days[p]?.size ?: 0
            if (d >= idleDays && n >= 2) out += Idle(p, st, d, n)
        }
        return out.sortedWith(compareByDescending<Idle> { it.count }.thenBy { it.days }).take(limit)
    }
}
