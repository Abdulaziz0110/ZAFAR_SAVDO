package com.ilxomjon.dur_novvot_dokon.domain

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Taklif. Izoh uchun: [intervalDays] — odatda necha kunda bir oladi (mediana), [daysSince] — oxirgisidan beri necha kun,
 * [orders] — shu do'kon tarixida necha marta olgan. [confident] = false bo'lsa ekranda "taxminiy" deb ko'rsatiladi.
 * Hech bir taklif fakt emas.
 */
data class Suggestion(
    val productId: String,
    val qty: Double,
    val score: Double,
    val intervalDays: Double? = null,
    val daysSince: Double? = null,
    val orders: Int = 0,
    val confident: Boolean = false,
)

/** "Yo'q ekan" sabablari (agent belgilaydi). */
object FeedbackReason {
    const val NOT_NEEDED = "not_needed"   // 7 kun taklif qilinmaydi
    const val NO_STOCK = "no_stock"       // bugun taklif qilinmaydi
    const val LATER = "later"             // 2 kun
}

/**
 * "Bu mijoz odatda nimalarni oladi?" algoritmi. Faqat shu qurilmadagi buyurtmalar tarixidan o'rganadi.
 *
 *  1. Har buyurtma og'irligi vaqt o'tishi bilan kamayadi: exp(-yosh / 60 kun) — yaqinda olinganlar muhimroq.
 *  2. Davriylik: tovar odatda har N kunda olinsa va shuncha vaqt o'tgan bo'lsa (≥ 0.7 N) — ball oshadi
 *     ("vaqti keldi"); yaqinda (< 0.3 N) olingan bo'lsa — ball kamayadi (hozir kerak emas).
 *  3. Taklif qilinadigan miqdor = shu mijoz odatda olgan miqdorlarning medianasi.
 *  4. Do'kon bo'yicha tarix kam bo'lsa (< 3 buyurtma) — umumiy mashhur tovarlar past og'irlik bilan qo'shiladi.
 */
object Suggestions {
    private const val DAY_MS = 86_400_000.0
    private const val TAU_DAYS = 60.0

    private class Acc {
        var score = 0.0
        val times = ArrayList<Long>()
        val qtys = ArrayList<Double>()
        val globalQtys = ArrayList<Double>()
    }

    fun build(
        storeOrders: List<SentOrder>,
        globalOrders: List<SentOrder>,
        now: Long,
        limit: Int = 12,
        feedback: Map<String, Pair<String, Long>> = emptyMap(),
    ): List<Suggestion> {
        val own = storeOrders.filter { it.mode == DealMode.SALE }
        val acc = HashMap<String, Acc>()

        fun add(order: SentOrder, weight: Double, isOwn: Boolean) {
            val ageDays = max(0L, now - order.sentAt) / DAY_MS
            val w = weight * exp(-ageDays / TAU_DAYS)
            val seen = HashSet<String>()
            for (l in order.lines) {
                if (l.productId.isEmpty() || !seen.add(l.productId)) continue
                val a = acc.getOrPut(l.productId) { Acc() }
                a.score += w
                if (isOwn) { a.times += order.sentAt; a.qtys += l.qty } else a.globalQtys += l.qty
            }
        }

        own.forEach { add(it, 1.0, true) }
        if (own.size < 3) {
            val ownIds = own.mapTo(HashSet()) { it.id }
            globalOrders.filter { it.mode == DealMode.SALE && it.id !in ownIds }.forEach { add(it, 0.25, false) }
        }

        val out = ArrayList<Suggestion>()
        for ((id, a) in acc) {
            if (muted(feedback[id], now)) continue
            val n = a.times.size
            var score = a.score
            var interval: Double? = null
            val since: Double? = a.times.maxOrNull()?.let { (now - it) / DAY_MS }
            if (n >= 2) {
                val times = a.times.sorted()
                val gaps = times.zipWithNext { x, y -> (y - x) / DAY_MS }.filter { it > 0.01 }
                if (gaps.isNotEmpty()) {
                    interval = median(gaps)
                    val ratio = ((now - times.last()) / DAY_MS) / median(gaps)
                    score *= when {
                        ratio >= 0.7 -> 1.0 + min(1.5, ratio - 0.5)
                        ratio < 0.3 -> 0.6
                        else -> 1.0
                    }
                }
            }
            val recentOnce = n == 1 && (now - a.times[0]) / DAY_MS <= 30.0
            val globalOnly = n == 0 && a.globalQtys.size >= 2
            if (!(n >= 2 || recentOnce || globalOnly)) continue
            val qty = median(if (a.qtys.isNotEmpty()) a.qtys else a.globalQtys)
            out += Suggestion(
                id, if (qty > 0.0) Math.round(qty * 1000.0) / 1000.0 else 1.0, score,
                intervalDays = interval, daysSince = since, orders = n, confident = n >= 3 && interval != null,
            )
        }
        return out.sortedByDescending { it.score }.take(limit)
    }

    private fun muted(f: Pair<String, Long>?, now: Long): Boolean {
        if (f == null) return false
        val ageDays = (now - f.second) / DAY_MS
        return when (f.first) {
            FeedbackReason.NOT_NEEDED -> ageDays < 7.0
            FeedbackReason.NO_STOCK -> ageDays < 1.0
            FeedbackReason.LATER -> ageDays < 2.0
            else -> false
        }
    }

    fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val s = values.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2.0
    }
}
