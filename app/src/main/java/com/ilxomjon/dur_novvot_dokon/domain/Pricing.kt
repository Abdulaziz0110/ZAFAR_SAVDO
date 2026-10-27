package com.ilxomjon.dur_novvot_dokon.domain

import com.ilxomjon.dur_novvot_dokon.core.Money

/**
 * Narx qoidalari (avvalgi ilovadagi hisob-kitob bilan bir xil):
 *  - Naqd: chakana narx; Optom: optom narx.
 *  - Mahsulotning asosiy valyutasi so'm bo'lsa ($ = so'm / kurs), aks holda dollar (so'm = $ * kurs).
 */
object Pricing {
    fun unitPrice(p: Product, type: PriceType, currency: Currency, rate: Double): Double {
        val som: Double
        val usd: Double
        if (type == PriceType.CASH) { som = p.cashSom; usd = p.cashUsd } else { som = p.wholesaleSom; usd = p.wholesaleUsd }
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
        val baseSom: Double
        val baseUsd: Double
        if (p.baseIsSom) {
            baseSom = p.costSom
            baseUsd = if (rate > 0) p.costSom / rate else 0.0
        } else {
            baseUsd = p.costUsd
            baseSom = p.costUsd * rate
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
