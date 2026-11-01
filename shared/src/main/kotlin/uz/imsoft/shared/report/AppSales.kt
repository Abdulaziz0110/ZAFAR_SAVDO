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
    /** v3.2: bu savdo emas, to'lov ([paid] — summa, [lines] bo'sh, [note] — izoh). */
    val payment: Boolean = false,
    val note: String = "",
) {
    data class Line(val name: String, val qty: Double, val price: Double)

    val total: Double get() = lines.sumOf { it.qty * it.price }

    companion object {
        const val KIND_TEXT = "@1-ilova"
        const val KEEP = 300

        fun isApp(kindText: String) = kindText == KIND_TEXT

        private fun m(v: Double, usd: Boolean) = if (usd) Money2(usd = v) else Money2(som = v)

        /** 1C tarixi ko'rinishidagi qatorlar (bitta hujjat bo'lib yig'iladi). Savdodagi to'lov — alohida to'lov hujjati. */
        fun toEntries(s: AppSale): List<LedgerEntry> {
            if (s.payment) return listOf(LedgerEntry(s.stamp, EntryKind.PAYMENT, KIND_TEXT, s.note, 0.0, mapOf(Col.PAYMENT to m(s.paid, s.usd))))
            val goods = goodsEntries(s)
            if (s.paid <= 0.0 || s.isReturn) return goods
            return goods + LedgerEntry(s.stamp, EntryKind.PAYMENT, KIND_TEXT, s.code, 0.0, mapOf(Col.PAYMENT to m(s.paid, s.usd)))
        }

        /** 1C hali qamramagan to'lovlar jami (so'm, $) — 2-ilovada "kutilmoqda" deb ko'rsatish uchun. */
        fun pendingPayments(entries: List<LedgerEntry>): Money2 =
            entries.filter { it.kind == EntryKind.PAYMENT && isApp(it.kindText) }.fold(Money2.ZERO) { a, e -> a + e.v(Col.PAYMENT) }

        private fun goodsEntries(s: AppSale): List<LedgerEntry> = s.lines.mapIndexed { i, l ->
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

        /**
         * v3.4: 1C'da (hisobotda) allaqachon bor savdo/to'lov qayta ko'rsatilmaydi — hatto qamrov vaqti ichida bo'lmasa ham:
         * shu turdagi, 20 daqiqa ichidagi va summasi teng (1 so'm / 0,01 $ aniqlikda) hujjat bo'lsa — bu o'sha.
         */
        fun entriesNotIn(sales: List<AppSale>, coveredTo: Long, ledger: List<LedgerEntry>): List<LedgerEntry> {
            val docs = LedgerDocs.build(ledger.filter { !isApp(it.kindText) })
            val used = HashSet<String>()
            val out = ArrayList<LedgerEntry>()
            for (s in sales.filter { it.stamp > coveredTo }.sortedBy { it.stamp }) {
                val mine = toEntries(s)
                val keep = ArrayList<LedgerEntry>()
                for (d in LedgerDocs.build(mine)) {
                    val twin = docs.firstOrNull { o ->
                        o.key !in used && o.kind == d.kind && kotlin.math.abs(minutes(o.stamp) - minutes(d.stamp)) <= 20 &&
                            kotlin.math.abs(o.total.som - d.total.som) < 1.0 && kotlin.math.abs(o.total.usd - d.total.usd) < 0.01
                    }
                    if (twin != null) used += twin.key else keep += d.lines
                }
                out += keep
            }
            return out
        }

        /** yyyyMMddHHmmss -> daqiqalar (taqqoslash uchun). */
        fun minutes(st: Long): Long =
            Stamp.daysFromCivil(Stamp.year(st), Stamp.month(st), Stamp.dayOfMonth(st)) * 1440L + Stamp.hour(st) * 60L + Stamp.minute(st)

        /**
         * v3.4: 2-ilovada qarz: oxirgi server (yoki 1C hisobot) qarzi + undan KEYIN 1-ilovada qilingan savdo/to'lovlar ta'siri.
         * [baseStamp] — server qarzi o'qilgan vaqt (yyyyMMddHHmmss). @return (qo'shiladigan summa, hujjatlar soni).
         */
        fun pendingAfter(ledger: List<LedgerEntry>, baseStamp: Long): Pair<Money2, Int> {
            val docs = LedgerDocs.build(ledger.filter { isApp(it.kindText) && it.stamp > baseStamp })
            return docs.fold(Money2.ZERO) { a, d -> a + d.debtEffect } to docs.size
        }

        fun toJson(s: AppSale): Map<String, Any?> = linkedMapOf(
            "id" to s.id, "at" to s.at, "st" to s.stamp, "r" to s.isReturn, "u" to s.usd, "d" to s.discount, "c" to s.code,
            "l" to s.lines.map { listOf(it.name, it.qty, it.price) },
            "db" to s.debtBefore, "pd" to s.paid, "py" to s.payment, "n" to s.note,
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
            val payment = o.bool("py")
            if (lines.isEmpty() && !payment) return null
            if (payment && o.num("pd") <= 0.0) return null
            val debt = (o.map["db"] as? Number)?.toDouble()
            return AppSale(id, o.long("at"), o.long("st"), o.bool("r"), o.bool("u"), lines, o.num("d"), o.str("c"), debt, o.num("pd"), payment, o.str("n"))
        }
    }
}
