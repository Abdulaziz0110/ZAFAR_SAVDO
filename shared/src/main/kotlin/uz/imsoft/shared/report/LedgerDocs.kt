package uz.imsoft.shared.report

/**
 * 1C hisobotidagi bitta hujjat: bir vaqtda (bir xil soniyada) yozilgan bir turdagi qatorlar — masalan bitta savdo
 * (5 xil tovar) yoki bitta to'lov. Haqiqiy faylda (2026-10-29) 445 qator = 126 hujjat.
 */
class LedgerDoc(
    val stamp: Long,
    val kind: EntryKind,
    val kindText: String,
    val lines: List<LedgerEntry>,
) {
    /** Barqaror kalit (ikki ilovada ham bir xil). */
    val key: String = "$stamp|${kind.code}|$kindText"

    /** Hujjat summasi (har doim musbat): savdo — summalar, qaytarish — qaytgan summa, to'lov — to'lov. */
    val total: Money2
    val discount: Money2
    val qty: Double
    val hasUsd: Boolean
    val hasSom: Boolean

    /** Hisobot bo'yicha shu hujjatdan keyingi qoldiq (qarz), agar ustun bo'lsa. */
    val balanceAfter: Money2?

    init {
        var t = Money2.ZERO
        var d = Money2.ZERO
        var q = 0.0
        for (e in lines) {
            t += amountOf(e)
            d += e.v(Col.DISCOUNT) - e.v(Col.RETURN_DISCOUNT)
            q += e.qty
        }
        total = t
        discount = d
        qty = q
        hasUsd = t.usd != 0.0 || lines.any { it.v(Col.PRICE).usd != 0.0 }
        hasSom = t.som != 0.0 || lines.any { it.v(Col.PRICE).som != 0.0 }
        balanceAfter = lines.lastOrNull { it.values.containsKey(Col.BALANCE) || it.values.containsKey(Col.CREDIT) }
            ?.let { it.v(Col.BALANCE) - it.v(Col.CREDIT) }
    }

    /** Tovar qatorlari (to'lovda bo'sh). */
    val goods: List<LedgerEntry> get() = if (isGoods) lines.filter { it.product.isNotBlank() } else emptyList()
    val isGoods: Boolean get() = kind == EntryKind.SALE || kind == EntryKind.RETURN
    val isPayment: Boolean get() = kind == EntryKind.PAYMENT || kind == EntryKind.PAYMENT_RETURN

    /** To'lov izohi ("Mijoz naqd so'mda to'lov qildi"). */
    val comment: String get() = if (isGoods) "" else lines.map { it.product }.filter { it.isNotBlank() }.distinct().joinToString("; ")

    /** Qarzga ta'siri: + qarz oshdi (savdo), − kamaydi (to'lov, qaytarish, chegirma). */
    val debtEffect: Money2 get() = when (kind) {
        EntryKind.SALE -> total - discount
        EntryKind.PAYMENT_RETURN -> total
        else -> Money2.ZERO - total
    }

    companion object {
        /** Qatorning hujjat summasidagi ulushi (musbat). */
        fun amountOf(e: LedgerEntry): Money2 = when (e.kind) {
            EntryKind.SALE -> e.v(Col.SUM)
            EntryKind.RETURN -> ClientStats.returnOf(e)
            EntryKind.PAYMENT -> e.v(Col.PAYMENT)
            EntryKind.PAYMENT_RETURN -> e.v(Col.PAYMENT_RETURN).let { if (it.isZero) e.v(Col.PAYMENT) else it }
            EntryKind.DISCOUNT -> e.v(Col.DISCOUNT)
            EntryKind.OTHER -> e.v(Col.SUM).let { if (it.isZero) e.v(Col.PAYMENT) else it }
        }
    }
}

object LedgerDocs {
    /** Qatorlarni hujjatlarga yig'adi. Yangi hujjat birinchi; hujjat ichida qatorlar fayldagi tartibda. */
    fun build(entries: List<LedgerEntry>): List<LedgerDoc> {
        val groups = LinkedHashMap<String, ArrayList<LedgerEntry>>()
        for (e in entries) groups.getOrPut("${e.stamp}|${e.kind.code}|${e.kindText}") { ArrayList() }.add(e)
        return groups.values.map { l -> LedgerDoc(l[0].stamp, l[0].kind, l[0].kindText, l) }
            .sortedWith(compareByDescending<LedgerDoc> { it.stamp }.thenBy { it.kind.ordinal })
    }
}

/**
 * v3.6 Admin "1C tarixi — barcha mijozlar": yozuv qaysi mijozniki ekanini [LedgerEntry.kindText] oxiriga yashirin qo'shadi
 * (ajratgich U+001F). Shunda bir soniyada ikki mijozning hujjati aralashmaydi va mijoz nomi bo'yicha qidiruv ishlaydi.
 * 2-ilovaga bunday yozuv bormaydi (faqat Admin ekrani uchun).
 */
object LedgerOwner {
    const val SEP = '\u001F'

    fun tag(kindText: String, storeId: String, name: String): String = kindText + SEP + storeId + SEP + name

    /** Asl matn (ajratgichsiz). */
    fun plain(kindText: String): String = kindText.substringBefore(SEP)

    /** (do'kon id, mijoz nomi) yoki null. */
    fun of(kindText: String): Pair<String, String>? {
        val i = kindText.indexOf(SEP)
        if (i < 0) return null
        val rest = kindText.substring(i + 1)
        return rest.substringBefore(SEP) to rest.substringAfter(SEP, "")
    }
}
