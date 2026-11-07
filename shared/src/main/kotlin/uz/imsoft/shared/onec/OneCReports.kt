package uz.imsoft.shared.onec

import uz.imsoft.shared.JObj

/**
 * v4.1: 1C hisobotlari (kompyuter 1C registrlaridan o'qib yuboradi — onec_data.reports_payload):
 * foyda (tovar bo'yicha), chegirma, xarajatlar, kassa qoldig'i va aylanmasi, ombor qoldig'i, qarzlar.
 * Pul so'm va dollarda alohida (1C'dagidek); umumiy so'm — kurs bilan.
 */
data class OneCReports(
    val from: Long,
    val to: Long,
    val at: Long,
    val profit: Profit?,
    val expenses: List<Amount>?,
    val cash: Cash?,
    val stock: Stock?,
    val debts: Debts?,
    val errors: List<String> = emptyList(),
) {
    data class Amount(val name: String, val som: Double, val usd: Double) {
        fun total(rate: Double) = som + if (rate > 0) usd * rate else 0.0
    }

    data class ProfitRow(val product: String, val code: String, val qty: Double, val costSom: Double, val saleSom: Double, val profitSom: Double,
                         val costUsd: Double, val saleUsd: Double, val profitUsd: Double) {
        fun profit(rate: Double) = profitSom + if (rate > 0) profitUsd * rate else 0.0
        fun sale(rate: Double) = saleSom + if (rate > 0) saleUsd * rate else 0.0
    }

    data class Profit(val cost: Amount, val sale: Amount, val profit: Amount, val discount: Amount, val rows: List<ProfitRow>, val count: Int)

    /** Kassa qoldig'i (naqd, o'tkazma, click so'mda; naqd va o'tkazma $) va moddalar bo'yicha aylanma. */
    data class Cash(val cashSom: Double, val cardSom: Double, val clickSom: Double, val cashUsd: Double, val cardUsd: Double, val turn: List<CashTurn>) {
        val totalSom: Double get() = cashSom + cardSom + clickSom
        val totalUsd: Double get() = cashUsd + cardUsd
    }

    data class CashTurn(val name: String, val inSom: Double, val outSom: Double, val inUsd: Double, val outUsd: Double)

    data class StockRow(val product: String, val code: String, val qty: Double, val usd: Boolean, val cost: Double, val value: Double)

    data class Stock(val rows: List<StockRow>, val count: Int, val valueSom: Double, val valueUsd: Double, val negative: Int) {
        fun total(rate: Double) = valueSom + if (rate > 0) valueUsd * rate else 0.0
    }

    data class DebtRow(val name: String, val som: Double, val usd: Double, val supplier: Boolean)

    /** [owedToUs] — bizga qarzdorlar (so'm, $); [weOwe] — bizning qarzimiz (ta'minotchilar, oldindan to'lovlar). */
    data class Debts(val owedToUs: Amount, val weOwe: Amount, val top: List<DebtRow>, val owe: List<DebtRow>)

    /** Sof foyda (so'm): yalpi foyda − xarajatlar. */
    fun netProfit(rate: Double): Double? {
        val p = profit ?: return null
        return p.profit.total(rate) - expenses.orEmpty().sumOf { it.total(rate) }
    }
}

object OneCReportsCodec {
    private fun d(x: Any?): Double = (x as? Number)?.toDouble() ?: x?.toString()?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
    private fun s(x: Any?): String = when (x) {
        null -> ""
        is Double -> if (x == Math.floor(x) && !x.isInfinite() && kotlin.math.abs(x) < 1e15) x.toLong().toString() else x.toString()
        else -> x.toString()
    }
    private fun list(x: Any?): List<Any?> = (x as? List<*>)?.toList().orEmpty()
    private fun nums(x: Any?, n: Int): List<Double> { val l = list(x); return List(n) { d(l.getOrNull(it)) } }

    @Suppress("UNCHECKED_CAST")
    fun parse(bytes: ByteArray): OneCReports {
        val o = JObj.parse(String(bytes, Charsets.UTF_8))
        val m = o.map
        val profit = (m["profit"] as? Map<String, Any?>)?.let { p ->
            val t = nums(p["tot"], 6)
            val disc = nums(p["disc"], 2)
            val rows = list(p["rows"]).mapNotNull { r ->
                val l = list(r)
                if (l.isEmpty()) null
                else OneCReports.ProfitRow(s(l.getOrNull(0)), s(l.getOrNull(1)), d(l.getOrNull(2)), d(l.getOrNull(3)), d(l.getOrNull(4)), d(l.getOrNull(5)),
                    d(l.getOrNull(6)), d(l.getOrNull(7)), d(l.getOrNull(8)))
            }
            OneCReports.Profit(
                OneCReports.Amount("", t[0], t[3]), OneCReports.Amount("", t[1], t[4]), OneCReports.Amount("", t[2], t[5]),
                OneCReports.Amount("", disc[0], disc[1]), rows, (p["n"] as? Number)?.toInt() ?: rows.size,
            )
        }
        val expenses = (m["exp"] as? List<*>)?.mapNotNull { r -> list(r).takeIf { it.isNotEmpty() }?.let { OneCReports.Amount(s(it[0]), d(it.getOrNull(1)), d(it.getOrNull(2))) } }
        val cash = (m["cash"] as? Map<String, Any?>)?.let { c ->
            val b = nums(c["bal"], 5)
            val turn = list(c["turn"]).mapNotNull { r ->
                val l = list(r)
                if (l.isEmpty()) null
                else OneCReports.CashTurn(s(l[0]), d(l.getOrNull(1)) + d(l.getOrNull(3)) + d(l.getOrNull(5)), d(l.getOrNull(2)) + d(l.getOrNull(4)) + d(l.getOrNull(6)),
                    d(l.getOrNull(7)), d(l.getOrNull(8)))
            }
            OneCReports.Cash(b[0], b[1], b[2], b[3], b[4], turn)
        }
        val stock = (m["stock"] as? Map<String, Any?>)?.let { st ->
            val rows = list(st["rows"]).mapNotNull { r ->
                val l = list(r)
                if (l.isEmpty()) null else OneCReports.StockRow(s(l[0]), s(l.getOrNull(1)), d(l.getOrNull(2)), d(l.getOrNull(3)) != 0.0, d(l.getOrNull(4)), d(l.getOrNull(5)))
            }
            OneCReports.Stock(rows, (st["n"] as? Number)?.toInt() ?: rows.size, d(st["vs"]), d(st["vu"]), (st["neg"] as? Number)?.toInt() ?: 0)
        }
        val debts = (m["debt"] as? Map<String, Any?>)?.let { db ->
            fun rows(x: Any?) = list(x).mapNotNull { r -> val l = list(r); if (l.isEmpty()) null else OneCReports.DebtRow(s(l[0]), d(l.getOrNull(1)), d(l.getOrNull(2)), d(l.getOrNull(3)) != 0.0) }
            val pos = nums(db["pos"], 2)
            val neg = nums(db["neg"], 2)
            OneCReports.Debts(OneCReports.Amount("", pos[0], pos[1]), OneCReports.Amount("", neg[0], neg[1]), rows(db["top"]), rows(db["owe"]))
        }
        return OneCReports(OneCCodec.stampOf(m["f"]), OneCCodec.stampOf(m["t"]), o.long("at"), profit, expenses, cash, stock, debts, list(m["err"]).map { s(it) })
    }
}

/**
 * v4.1: ommaviy narx belgilash qoidasi (bir nechta tovarga birdan). Natija yaxlitlanadi ([step]: so'mda 100/500/1000,
 * dollarda 0.01). Kelgan narx noma'lum bo'lsa — "kelgan narx + …" usulida natija yo'q (o'tkazib yuboriladi).
 */
object PriceRule {
    enum class Mode {
        /** Hozirgi narx ± %. */
        PERCENT,
        /** Kelgan narx + summa. */
        COST_PLUS,
        /** Kelgan narx + %. */
        COST_PERCENT,
        /** Aniq narx (hammasiga bir xil). */
        FIXED,
    }

    fun round(v: Double, step: Double): Double {
        if (step <= 0) return v
        // 3.345 kabi qiymat double'da birozgina kichik bo'ladi (3.34499…) — kichik tuzatmasiz pastga yaxlitlanib ketardi
        val n = Math.round(v / step + if (v >= 0) 1e-9 else -1e-9)
        val r = n * step
        return if (step < 1) Math.round(r * 100.0) / 100.0 else r
    }

    /** @return yangi narx yoki null (hisoblab bo'lmaydi yoki natija 0 dan kichik). */
    fun apply(old: Double, cost: Double, mode: Mode, value: Double, step: Double): Double? {
        val raw = when (mode) {
            Mode.PERCENT -> if (old <= 0) return null else old * (1 + value / 100.0)
            Mode.COST_PLUS -> if (cost <= 0) return null else cost + value
            Mode.COST_PERCENT -> if (cost <= 0) return null else cost * (1 + value / 100.0)
            Mode.FIXED -> value
        }
        val r = round(raw, step)
        return if (r <= 0 || r.isNaN() || r.isInfinite()) null else r
    }
}
