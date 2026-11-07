package uz.imsoft.shared.sched

import uz.imsoft.shared.Json

/**
 * v5.0: dollar kursini internetdan o'qish. Turli saytlar JSON'ni turlicha nomlaydi, shuning uchun o'qish
 * "bag'rikeng": USD yozuvi topiladi va ma'lum nomlardagi sonlardan qaysi biri bo'lsa o'sha olinadi.
 * Sof mantiq — testlanadi; tarmoq qismi ilovada.
 */
object RateQuote {
    data class Quote(val source: String, val buy: Double, val sell: Double, val date: String) {
        /** Savdo uchun qulay kurs: sotish narxi (bo'lmasa olish narxi). */
        val value: Double get() = if (sell > 0) sell else buy

        /** Olish va sotish o'rtasi (ikkalasi bo'lsa). */
        val mid: Double get() = if (buy > 0 && sell > 0) (buy + sell) / 2 else value
    }

    private val BUY = listOf("nbu_buy_price", "buy_price", "buy", "sotib_olish", "cb_buy")
    private val SELL = listOf("nbu_cell_price", "nbu_sell_price", "sell_price", "sell", "sotish", "cb_sell")
    private val RATE = listOf("Rate", "rate", "cb_price", "value")
    private val DATE = listOf("Date", "date", "created_at", "updated_at")

    private fun num(v: Any?): Double {
        val d = when (v) {
            is Number -> v.toDouble()
            is String -> uz.imsoft.shared.NumText.parse(v)
            else -> 0.0
        }
        return if (d.isNaN() || d.isInfinite() || d <= 0 || d > 10_000_000) 0.0 else d
    }

    private fun pick(m: Map<String, Any?>, keys: List<String>): Double {
        for (k in keys) m[k]?.let { val d = num(it); if (d > 0) return d }
        return 0.0
    }

    private fun isUsd(m: Map<String, Any?>): Boolean {
        val ccy = (m["Ccy"] ?: m["ccy"] ?: m["code"] ?: m["Code"] ?: m["title"] ?: m["name"])?.toString()?.uppercase().orEmpty()
        return ccy == "USD" || ccy == "840" || ccy.contains("USD")
    }

    /** @return USD kursi yoki null (topilmasa / son chiqmasa). */
    @Suppress("UNCHECKED_CAST")
    fun parse(source: String, json: String): Quote? {
        val root = runCatching { Json.parse(json) }.getOrNull() ?: return null
        val items = ArrayList<Map<String, Any?>>()
        fun collect(x: Any?) {
            when (x) {
                is Map<*, *> -> {
                    val m = x as Map<String, Any?>
                    items += m
                    for (v in m.values) if (v is List<*> || v is Map<*, *>) collect(v)
                }
                is List<*> -> for (v in x) collect(v)
            }
        }
        collect(root)
        val m = items.firstOrNull { isUsd(it) } ?: return null
        var buy = pick(m, BUY)
        var sell = pick(m, SELL)
        if (buy == 0.0 && sell == 0.0) {
            val r = pick(m, RATE)
            if (r <= 0) return null
            buy = r
            sell = r
        }
        val date = DATE.firstNotNullOfOrNull { k -> m[k]?.toString()?.takeIf { it.isNotBlank() } }.orEmpty().take(10)
        return Quote(source, buy, sell, date)
    }
}
