package uz.imsoft.shared.receipt

import uz.imsoft.shared.onec.OneCDoc
import uz.imsoft.shared.report.Money2

/**
 * v5.3.13: yuk beruvchi hujjatlarining cheki — savdo chekimizning AYNAN o'sha ko'rinishi ([Chek]):
 *  - kirim (va yuk beruvchiga qaytarish): tovarlar ro'yxati va summa, qarz qatorlari YO'Q;
 *  - yuk beruvchiga to'lov: to'lov summasi, oldingi va keyingi qarzimiz.
 */
object SupplierChek {
    /** Hujjatning yuk beruvchiga qarzimizga ta'siri (musbat — qarzimiz oshadi). */
    fun effect(d: OneCDoc): Money2 = when (d.kind) {
        OneCDoc.RECEIPT -> Money2(d.som, d.usd)
        OneCDoc.SUPPLIER_PAY, OneCDoc.SUPPLIER_RETURN -> Money2(-d.som, -d.usd)
        else -> Money2.ZERO
    }

    private val order = compareBy<OneCDoc>({ it.stamp }, { it.number }, { it.id })

    /**
     * [target] hujjatdan OLDINGI qarzimiz: hozirgi qarzdan ([oweNow]) shu hujjat va undan keyingi hamma hujjatlar ta'sirini
     * ayiramiz. Kompyuter yuk beruvchining oxirgi 90 kunlik hujjatlarini to'liq yuboradi — shu oraliqdagi hujjat uchun aniq.
     */
    fun oweBefore(docs: List<OneCDoc>, oweNow: Money2, target: OneCDoc): Money2 {
        var m = oweNow
        for (d in docs) if (d.id == target.id || order.compare(d, target) > 0) m -= effect(d)
        return Money2(round2(m.som), round4(m.usd))
    }

    private fun round2(v: Double) = Math.round(v * 100.0) / 100.0
    private fun round4(v: Double) = Math.round(v * 10000.0) / 10000.0

    /** Bu hujjat turiga chek bormi. */
    fun supports(kind: String): Boolean = kind == OneCDoc.RECEIPT || kind == OneCDoc.SUPPLIER_PAY || kind == OneCDoc.SUPPLIER_RETURN

    /**
     * Chek. [title] — sarlavha, [labels] — yorliqlar (ilova matnlaridan). [debtBefore] faqat to'lovda kerak
     * (null bo'lsa qarz qatorlari chiqmaydi).
     */
    fun of(doc: OneCDoc, title: String, labels: Chek.Labels, agent: String, phone: String, debtBefore: Money2?): Chek {
        val (time, date) = ReceiptBuilder.timeAndDate(doc.stamp)
        val rows = doc.lines.mapIndexed { i, x ->
            val usd = x.priceUsd != 0.0 && x.priceSom == 0.0
            Chek.Line(
                i + 1, x.name, "", NumFmt.qty(x.qty),
                if (usd) NumFmt.usd(x.priceUsd) else Chek.num(x.priceSom),
                if (usd || (x.sumSom == 0.0 && x.sumUsd != 0.0)) NumFmt.usd(x.sumUsd) else Chek.num(x.sumSom),
            )
        }
        val showDebt = doc.kind == OneCDoc.SUPPLIER_PAY && debtBefore != null
        val before = debtBefore ?: Money2.ZERO
        val end = before + effect(doc)
        val zeroUsd = NumFmt.usd(0.0)
        return Chek(
            time, date, doc.number, doc.party, agent,
            Chek.num(before.som), NumFmt.usd(before.usd), rows,
            Chek.num(doc.som), NumFmt.usd(doc.usd),
            Chek.num(0.0), zeroUsd,
            Chek.num(0.0), Chek.num(0.0), zeroUsd,
            Chek.num(end.som), NumFmt.usd(end.usd), phone, labels,
            title = title, showDebt = showDebt, showPay = false,
        )
    }
}
