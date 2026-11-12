package uz.imsoft.shared.pos

import uz.imsoft.shared.receipt.NumFmt
import uz.imsoft.shared.receipt.Receipt
import uz.imsoft.shared.report.Stamp

/**
 * v3.2 Mijoz "Kassa": 2-filialning o'z savdosi o'z mijozlariga (faqat telefonda, serverga va 1-ilovaga BORMAYDI).
 * Summalar so'mda. Sof mantiq — test qilinadi.
 */
data class PosCustomer(val id: Long, val name: String, val phone: String = "", val createdAt: Long = 0L)

data class PosLine(val name: String, val code: String, val qty: Double, val price: Double) {
    val sum: Double get() = Math.round(qty * price * 100.0) / 100.0
}

data class PosSale(
    val id: Long,
    /** yyyyMMddHHmmss */
    val stamp: Long,
    /** 0 — naqd (mijozsiz). */
    val customerId: Long,
    val lines: List<PosLine>,
    val discount: Double,
    val paid: Double,
    val note: String = "",
    val number: Long = id,
) {
    val total: Double get() = Math.round((lines.sumOf { it.sum } - discount) * 100.0) / 100.0
    /** Qarzga qolgan qism (manfiy bo'lmaydi). */
    val debt: Double get() = (total - paid).coerceAtLeast(0.0)
}

data class PosPayment(val id: Long, val stamp: Long, val customerId: Long, val amount: Double, val note: String = "")

data class PosReport(
    val sales: Int,
    val total: Double,
    val paidAtSale: Double,
    val onCredit: Double,
    val payments: Double,
    /** Naqd kassaga tushgan: savdoda to'langan + qarz to'lovlari. */
    val cashIn: Double,
    val top: List<Pair<String, Double>>,
)

object Pos {
    /** Mijoz qarzi = qarzga qolgan savdolar − to'lovlar (musbat — mijoz qarzdor). */
    fun balance(customerId: Long, sales: List<PosSale>, payments: List<PosPayment>): Double {
        val d = sales.filter { it.customerId == customerId }.sumOf { it.total - it.paid } - payments.filter { it.customerId == customerId }.sumOf { it.amount }
        return Math.round(d * 100.0) / 100.0
    }

    fun report(from: Long, to: Long, sales: List<PosSale>, payments: List<PosPayment>): PosReport {
        val s = sales.filter { it.stamp in from..to }
        val p = payments.filter { it.stamp in from..to }
        val total = s.sumOf { it.total }
        val paid = s.sumOf { minOf(it.paid, it.total) }
        val pays = p.sumOf { it.amount }
        val byName = LinkedHashMap<String, Double>()
        for (x in s) for (l in x.lines) byName[l.name] = (byName[l.name] ?: 0.0) + l.sum
        val top = byName.entries.sortedByDescending { it.value }.take(10).map { it.key to Math.round(it.value * 100.0) / 100.0 }
        return PosReport(s.size, r2(total), r2(paid), r2(total - paid), r2(pays), r2(paid + pays), top)
    }

    private fun r2(v: Double) = Math.round(v * 100.0) / 100.0

    /** Mahalliy vaqt -> yyyyMMddHHmmss. */
    fun stampOf(millis: Long, cal: java.util.Calendar = java.util.Calendar.getInstance()): Long {
        cal.timeInMillis = millis
        return Stamp.of(
            cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH),
            cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), cal.get(java.util.Calendar.SECOND),
        )
    }

    /** Kunning boshi va oxiri (yyyyMMddHHmmss) — [days] kun orqaga. */
    fun range(nowStamp: Long, days: Int): Pair<Long, Long> {
        val end = Stamp.dayEnd(nowStamp)
        val start = Stamp.dayStart(Stamp.addDays(nowStamp, -(days - 1).coerceAtLeast(0)))
        return start to end
    }

    /** Chek (do'kon nomi — o'zgarmas brend, qolgani chek sozlamasidan). */
    fun receipt(
        shop: String, sale: PosSale, customer: PosCustomer?, balanceAfter: Double?, labels: Labels,
        agent: String = "", phone: String = "",
    ): Receipt {
        val lines = sale.lines.map { l -> Receipt.Line(l.name, NumFmt.qty(l.qty) + " x " + NumFmt.som(l.price), NumFmt.som(l.sum)) }
        val totals = ArrayList<Receipt.Total>()
        if (sale.discount > 0) {
            totals += Receipt.Total(labels.sum, NumFmt.som(sale.total + sale.discount))
            totals += Receipt.Total(labels.discount, NumFmt.som(sale.discount))
        }
        totals += Receipt.Total(labels.total, NumFmt.som(sale.total), bold = true)
        val after = ArrayList<Receipt.Total>()
        if (customer != null) {
            after += Receipt.Total(labels.paid, NumFmt.som(sale.paid))
            if (balanceAfter != null) after += Receipt.Total(labels.debt, NumFmt.som(balanceAfter))
        }
        // v5.2 (A3): chek 1C "Chek1" maketida chiqadi — uchala ilovada bir xil. Qolgan maydonlar
        // (matn/HTML ulashish) eski ko'rinishda qoladi, ular chop etilmaydi.
        val chek = uz.imsoft.shared.receipt.Chek.ofPos(
            sale, shop, customer?.name.orEmpty(), agent, phone,
            debtBefore = if (balanceAfter != null) balanceAfter - sale.debt else 0.0,
        )
        return Receipt(
            shop, labels.title, customer?.name.orEmpty(), Stamp.formatDateTime(sale.stamp), lines, totals,
            if (sale.note.isNotBlank()) listOf(sale.note) else emptyList(),
            clientLabel = if (customer != null) labels.client else "", clientName = customer?.name.orEmpty(),
            info = listOf(labels.number to sale.number.toString(), labels.time to Stamp.formatDateTime(sale.stamp).substringAfter(' ')),
            date = Stamp.formatDate(sale.stamp), after = after,
            infoKeys = listOf(Receipt.KEY_NUMBER, Receipt.KEY_TIME), chek = chek,
        )
    }

    /** To'lov cheki (kassaga mijoz to'lovi yoki Admin tasdiqlagan to'lov). */
    fun paymentReceipt(shop: String, title: String, client: String, stamp: Long, amount: Double, note: String, number: String, labels: Labels, balanceAfter: Double? = null): Receipt =
        Receipt(
            shop, title, client, Stamp.formatDateTime(stamp), emptyList(),
            listOf(Receipt.Total(labels.paid, NumFmt.som(amount), bold = true)),
            if (note.isNotBlank()) listOf(note) else emptyList(),
            clientLabel = labels.client, clientName = client,
            info = listOf(labels.number to number, labels.time to Stamp.formatDateTime(stamp).substringAfter(' ')),
            date = Stamp.formatDate(stamp),
            after = if (balanceAfter != null) listOf(Receipt.Total(labels.debt, NumFmt.som(balanceAfter))) else emptyList(),
            infoKeys = listOf(Receipt.KEY_NUMBER, Receipt.KEY_TIME),
        )

    data class Labels(
        val title: String, val client: String, val sum: String, val discount: String, val total: String,
        val paid: String, val debt: String, val number: String, val time: String,
    )
}
