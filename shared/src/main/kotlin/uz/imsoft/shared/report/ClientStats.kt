package uz.imsoft.shared.report

/** 2-ilova hisobotlari uchun hisob-kitoblar (faqat import qilingan yozuvlardan; server qarzi alohida). */
object ClientStats {

    class Totals(
        val purchased: Money2,
        val returned: Money2,
        val discount: Money2,
        val paid: Money2,
        val purchaseCount: Int,
        val paymentCount: Int,
    ) {
        /** Davr ichidagi aylanma: olindi − qaytarildi − chegirma − to'landi (musbat — qarz ko'paygan). */
        val net: Money2 get() = purchased - returned - discount - paid
    }

    /** Yozuvning "xarid summasi" (faqat SALE). */
    fun purchaseOf(e: LedgerEntry): Money2 = if (e.kind == EntryKind.SALE) e.v(Col.SUM) else Money2.ZERO

    /** Yozuvning to'lovi (To'lov ustuni; to'lov qaytarilishi manfiy). */
    fun paymentOf(e: LedgerEntry): Money2 = when (e.kind) {
        EntryKind.PAYMENT_RETURN -> Money2.ZERO - e.v(Col.PAYMENT_RETURN).let { if (it.isZero) e.v(Col.PAYMENT) else it }
        else -> e.v(Col.PAYMENT)
    }

    fun returnOf(e: LedgerEntry): Money2 = when {
        !e.v(Col.RETURN).isZero -> e.v(Col.RETURN)
        e.kind == EntryKind.RETURN -> e.v(Col.SUM)
        else -> Money2.ZERO
    }

    fun totals(entries: List<LedgerEntry>, from: Long = 0, to: Long = Long.MAX_VALUE): Totals {
        var p = Money2.ZERO; var r = Money2.ZERO; var d = Money2.ZERO; var y = Money2.ZERO
        var pc = 0; var yc = 0
        for (e in entries) {
            if (e.stamp < from || e.stamp > to) continue
            val pu = purchaseOf(e)
            if (!pu.isZero) { p += pu; pc++ }
            r += returnOf(e)
            d += e.v(Col.DISCOUNT) - e.v(Col.RETURN_DISCOUNT)
            val pay = paymentOf(e)
            if (!pay.isZero) { y += pay; yc++ }
        }
        return Totals(p, r, d, y, pc, yc)
    }

    class Day(val day: Long, val entries: List<LedgerEntry>, val totals: Totals)

    /** Kunlar bo'yicha (yangi kun yuqorida), har kun ichida vaqt bo'yicha. */
    fun byDay(entries: List<LedgerEntry>): List<Day> =
        entries.groupBy { Stamp.dayStart(it.stamp) }.entries.sortedByDescending { it.key }
            .map { (d, list) -> val l = list.sortedBy { it.stamp }; Day(d, l, totals(l)) }

    class Purchase(val stamp: Long, val qty: Double, val price: Money2, val sum: Money2, val returned: Boolean)

    /** Tovar bo'yicha xaridlar tarixi (nom kaliti bo'yicha), yangisi birinchi. */
    fun productHistory(entries: List<LedgerEntry>, nameKey: String): List<Purchase> =
        entries.filter { (it.kind == EntryKind.SALE || it.kind == EntryKind.RETURN) && LedgerMerge.nameKey(it.product) == nameKey }
            .sortedByDescending { it.stamp }
            .map { Purchase(it.stamp, it.qty, it.v(Col.PRICE), if (it.kind == EntryKind.SALE) it.v(Col.SUM) else returnOf(it), it.kind == EntryKind.RETURN) }

    /** Har tovarning oxirgi xaridi (nom kaliti -> xarid). */
    fun lastPurchases(entries: List<LedgerEntry>): Map<String, Purchase> {
        val out = HashMap<String, Purchase>()
        for (e in entries) {
            if (e.kind != EntryKind.SALE || e.product.isBlank()) continue
            val k = LedgerMerge.nameKey(e.product)
            val cur = out[k]
            if (cur == null || e.stamp >= cur.stamp) out[k] = Purchase(e.stamp, e.qty, e.v(Col.PRICE), e.v(Col.SUM), false)
        }
        return out
    }

    /** Hisobotdagi oxirgi "Qoldiq" (agar shunday ustun bo'lsa) — server qarzi bo'lmasa ma'lumot sifatida. */
    fun lastBalance(entries: List<LedgerEntry>): Pair<Long, Money2>? =
        entries.lastOrNull { it.values.containsKey(Col.BALANCE) || it.values.containsKey(Col.CREDIT) }
            ?.let { it.stamp to (it.v(Col.BALANCE) - it.v(Col.CREDIT)) }

    /** Davr chegaralari (stamp). [today] — bugungi kun (stamp, istalgan vaqt). */
    object Period {
        fun today(today: Long) = Stamp.dayStart(today) to Stamp.dayEnd(today)
        fun thisWeek(today: Long, dayOfWeekMonday1: Int): Pair<Long, Long> =
            Stamp.addDays(today, -(dayOfWeekMonday1 - 1)) to Stamp.dayEnd(today)
        fun thisMonth(today: Long) = Stamp.of(Stamp.year(today), Stamp.month(today), 1) to Stamp.dayEnd(today)
        fun lastMonth(today: Long): Pair<Long, Long> {
            val firstThis = Stamp.of(Stamp.year(today), Stamp.month(today), 1)
            val lastPrev = Stamp.addDays(firstThis, -1)
            return Stamp.of(Stamp.year(lastPrev), Stamp.month(lastPrev), 1) to Stamp.dayEnd(lastPrev)
        }
        fun lastDays(today: Long, days: Int) = Stamp.addDays(today, -(days - 1)) to Stamp.dayEnd(today)
    }
}
