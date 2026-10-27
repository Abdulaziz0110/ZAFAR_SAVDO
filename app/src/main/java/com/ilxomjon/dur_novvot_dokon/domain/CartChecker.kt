package com.ilxomjon.dur_novvot_dokon.domain

import kotlin.math.abs

/**
 * Savatcha tekshiruvchisi: FAQAT ogohlantiradi, hech qachon to'smaydi (C8: qoldiq va arzon sotish tekshirilmaydi).
 * Ko'pi bilan [MAX] ta ogohlantirish. Tarix bo'lmasa — jim.
 */
sealed interface CartWarning {
    val key: String

    /** Miqdor shu do'konning odatiy miqdoridan keskin katta (masalan 10 o'rniga 100). */
    data class BigQty(val productId: String, val name: String, val qty: Double, val usual: Double) : CartWarning { override val key = "qty:$productId" }

    /** Narx shu do'konga oxirgi sotilgan narxdan keskin farq qiladi. */
    data class PriceDiff(val productId: String, val name: String, val price: Double, val last: Double) : CartWarning { override val key = "price:$productId" }

    /** Odatda oladigan tovarlar savatchada yo'q. */
    data class MissingUsual(val names: List<String>) : CartWarning { override val key = "missing:" + names.joinToString("|") }
}

object CartChecker {
    const val MAX = 2

    fun check(
        lines: List<CartLine>,
        currency: Currency,
        mode: DealMode,
        storeOrders: List<SentOrder>,
        suggestions: List<Suggestion>,
        names: Map<String, String>,
        dismissed: Set<String> = emptySet(),
        includeMissing: Boolean = false,
    ): List<CartWarning> {
        if (lines.isEmpty() || mode != DealMode.SALE) return emptyList()
        val own = storeOrders.filter { it.mode == DealMode.SALE }
        val out = ArrayList<CartWarning>()
        for (l in lines) {
            val qtys = own.mapNotNull { o -> o.lines.firstOrNull { it.productId == l.productId }?.qty }
            if (qtys.size >= 2) {
                val usual = Suggestions.median(qtys)
                if (usual > 0 && l.qty >= maxOf(usual * 3.0, usual + 10.0)) out += CartWarning.BigQty(l.productId, l.name, l.qty, usual)
            }
            val last = own.filter { it.currency == currency }.firstNotNullOfOrNull { o -> o.lines.firstOrNull { it.productId == l.productId }?.price }
            if (last != null && last > 0 && l.price > 0 && abs(l.price - last) / last > 0.3) out += CartWarning.PriceDiff(l.productId, l.name, l.price, last)
        }
        if (includeMissing) {
            val inCart = lines.mapTo(HashSet()) { it.productId }
            val missing = suggestions.filter { it.confident && it.productId !in inCart }.take(3).mapNotNull { names[it.productId] }
            if (missing.isNotEmpty()) out += CartWarning.MissingUsual(missing)
        }
        return out.filter { it.key !in dismissed }.take(MAX)
    }

    /** Shu do'konga shu tovar oxirgi qaysi narxda va qachon sotilgan (ayni valyutada). */
    fun lastPrice(storeOrders: List<SentOrder>, productId: String, currency: Currency): Pair<Double, Long>? {
        for (o in storeOrders) {
            if (o.mode != DealMode.SALE || o.currency != currency) continue
            val l = o.lines.firstOrNull { it.productId == productId } ?: continue
            return l.price to o.sentAt
        }
        return null
    }
}
