package uz.imsoft.shared.report

import uz.imsoft.shared.JObj

/**
 * 1-ilovada qilingan savdo/qaytarish (v2.6). 2-ilovada darhol ko'rinsin deb snapshot'ga qo'shiladi — 1C hisoboti shu kunni
 * qamragunicha (keyin 1C'dagi asl yozuv ko'rinadi va bu yozuv o'zi chiqib ketadi). Bu SERVER TASDIG'I EMAS: 2-ilovada
 * "1-ilovadan" deb belgilanadi ([KIND_TEXT]).
 */
data class AppSale(
    val id: String,
    /** Yaratilgan vaqt (ms). */
    val at: Long,
    /** Mahalliy vaqt yyyyMMddHHmmss (LedgerEntry bilan bir xil). */
    val stamp: Long,
    val isReturn: Boolean,
    val usd: Boolean,
    val lines: List<Line>,
    val discount: Double,
    /** Kvitansiya kodi (masalan B-0412). */
    val code: String,
    /** Savdo paytida 1-ilovadagi (serverdan olingan) qarz, so'm — chekda "oldingi qarz". null — noma'lum. */
    val debtBefore: Double? = null,
    /** Savdoda to'langan summa (savdo valyutasida). */
    val paid: Double = 0.0,
) {
    data class Line(val name: String, val qty: Double, val price: Double)

    val total: Double get() = lines.sumOf { it.qty * it.price }

    companion object {
        const val KIND_TEXT = "@1-ilova"
        const val KEEP = 300

        fun isApp(kindText: String) = kindText == KIND_TEXT

        private fun m(v: Double, usd: Boolean) = if (usd) Money2(usd = v) else Money2(som = v)

        /** 1C tarixi ko'rinishidagi qatorlar (bitta hujjat bo'lib yig'iladi). */
        fun toEntries(s: AppSale): List<LedgerEntry> = s.lines.mapIndexed { i, l ->
            val sum = Math.round(l.qty * l.price * 100.0) / 100.0
            val values = LinkedHashMap<Col, Money2>()
            values[Col.PRICE] = m(l.price, s.usd)
            if (s.isReturn) values[Col.RETURN] = m(sum, s.usd) else values[Col.SUM] = m(sum, s.usd)
            if (i == 0 && s.discount > 0 && !s.isReturn) values[Col.DISCOUNT] = m(s.discount, s.usd)
            LedgerEntry(s.stamp, if (s.isReturn) EntryKind.RETURN else EntryKind.SALE, KIND_TEXT, l.name, l.qty, values)
        }

        /** 1C hisoboti hali qamramagan savdolar (oxirgi qamrangan vaqtdan keyingilari). */
        fun entriesAfter(sales: List<AppSale>, coveredTo: Long): List<LedgerEntry> =
            sales.filter { it.stamp > coveredTo }.sortedBy { it.stamp }.flatMap { toEntries(it) }

        fun toJson(s: AppSale): Map<String, Any?> = linkedMapOf(
            "id" to s.id, "at" to s.at, "st" to s.stamp, "r" to s.isReturn, "u" to s.usd, "d" to s.discount, "c" to s.code,
            "l" to s.lines.map { listOf(it.name, it.qty, it.price) },
            "db" to s.debtBefore, "pd" to s.paid,
        )

        fun fromJson(o: JObj): AppSale? {
            val id = o.str("id")
            if (id.isEmpty()) return null
            val lines = o.arr("l").mapNotNull { x ->
                val a = x as? List<*> ?: return@mapNotNull null
                val name = a.getOrNull(0)?.toString().orEmpty()
                val qty = (a.getOrNull(1) as? Number)?.toDouble() ?: return@mapNotNull null
                val price = (a.getOrNull(2) as? Number)?.toDouble() ?: 0.0
                if (name.isBlank()) null else Line(name, qty, price)
            }
            if (lines.isEmpty()) return null
            val debt = (o.map["db"] as? Number)?.toDouble()
            return AppSale(id, o.long("at"), o.long("st"), o.bool("r"), o.bool("u"), lines, o.num("d"), o.str("c"), debt, o.num("pd"))
        }
    }
}
