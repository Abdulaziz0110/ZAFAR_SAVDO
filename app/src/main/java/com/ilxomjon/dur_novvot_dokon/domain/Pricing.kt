package com.ilxomjon.dur_novvot_dokon.domain

import com.ilxomjon.dur_novvot_dokon.core.Money

/**
 * Narx qoidalari (avvalgi ilovadagi hisob-kitob bilan bir xil):
 *  - Naqd: chakana narx; Optom: optom narx.
 *  - Mahsulotning asosiy valyutasi so'm bo'lsa ($ = so'm / kurs), aks holda dollar (so'm = $ * kurs).
 */
object Pricing {
    /**
     * v3.1: 1- va 2-narx (so'mda) — 1C'dan kompyuter orqali keladi (ПродажЦена1/2). Yo'q bo'lsa — 4-narx (optom).
     * Ilova ishga tushganda o'rnatiladi.
     */
    @Volatile var level12: (productId: String, level: Int) -> Double? = { _, _ -> null }

    /**
     * v4.0: kelgan narx — OXIRGI kirimdagi narx (so'm, $), o'rtacha emas. Admin: 1C kirim hujjatlaridan; Agent: Admin
     * katalog bilan yuboradi. Yo'q bo'lsa — serverdagi narx.
     */
    @Volatile var lastCost: (productId: String) -> Pair<Double, Double>? = { null }

    fun unitPrice(p: Product, type: PriceType, currency: Currency, rate: Double): Double =
        unitPrice(p, PriceType.levelOf(type), currency, rate)

    /** [level] 1..4: 3 — Naqd (chakana), 4 — Optom, 1/2 — 1C narxi. */
    fun unitPrice(p: Product, level: Int, currency: Currency, rate: Double): Double {
        if (level == 1 || level == 2) {
            val som1c = level12(p.id, level)
            if (som1c != null && som1c > 0) {
                return Money.round2(if (currency == Currency.SOM) som1c else if (rate > 0) som1c / rate else 0.0)
            }
        }
        val som: Double
        val usd: Double
        if (level == 3) { som = p.cashSom; usd = p.cashUsd } else { som = p.wholesaleSom; usd = p.wholesaleUsd }
        val baseSom: Double
        val baseUsd: Double
        if (p.baseIsSom) {
            baseSom = som
            baseUsd = if (rate > 0) som / rate else 0.0
        } else {
            baseUsd = usd
            baseSom = usd * rate
        }
        return Money.round2(if (currency == Currency.SOM) baseSom else baseUsd)
    }

    /** Kelgan (kirim) narx: shu valyutada. Mahsulotning asosiy valyutasi qoidasi narxniki bilan bir xil. */
    fun costPrice(p: Product, currency: Currency, rate: Double): Double {
        val (cs, cu) = lastCost(p.id)?.takeIf { it.first > 0 || it.second > 0 } ?: (p.costSom to p.costUsd)
        val baseSom: Double
        val baseUsd: Double
        if (p.baseIsSom) {
            baseSom = if (cs > 0) cs else cu * rate
            baseUsd = if (rate > 0) baseSom / rate else 0.0
        } else {
            baseUsd = if (cu > 0) cu else if (rate > 0) cs / rate else 0.0
            baseSom = baseUsd * rate
        }
        return Money.round2(if (currency == Currency.SOM) baseSom else baseUsd)
    }

    /** Chegirma foizdan summaga. */
    fun discountFromPercent(total: Double, percent: Double): Double =
        Money.round2(total * percent.coerceIn(0.0, 100.0) / 100.0)

    fun totals(total: Double, discount: Double): Totals {
        val d = discount.coerceIn(0.0, maxOf(total, 0.0))
        return Totals(total, d, Money.round2(total - d))
    }
}
