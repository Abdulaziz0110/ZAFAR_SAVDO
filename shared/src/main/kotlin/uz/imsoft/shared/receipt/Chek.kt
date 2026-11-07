package uz.imsoft.shared.receipt

import uz.imsoft.shared.report.Col
import uz.imsoft.shared.report.EntryKind
import uz.imsoft.shared.report.LedgerDoc
import uz.imsoft.shared.report.Money2

/**
 * 1C chekining AYNAN o'zi — Документы.Реализация maketi "Чек1" (do'kon bazasidan olingan).
 * Maketdagi tartib va yozuvlar, hech qanday qo'shimchasiz:
 *
 *   [logo]
 *   Vaqt: ...                         Sana: ...
 *   Mijoz:  <hujjat raqami>
 *   Mijoz:  <mijoz nomi>
 *   Agent:  <sotuvchi>
 *   Qarz qoldig'i  (So'm):   <oldingi qarz>
 *   Qarz qoldig'i (Val):     <oldingi qarz $>
 *   ───────────────────────
 *   1 || Tovar nomi
 *          2bl 40 x 117 000 = 4 680 000
 *   ───────────────────────
 *   Summa      Sum:  ...
 *              Val:  ...
 *   ───────────────────────
 *   Chegirma   Sum:  ...
 *              Val:  ...
 *   To'lov     Naqd:     ...
 *              Plastik:  ...
 *              Val ( $ ): ...
 *   Qarz qoldig'i (So'm)   ...
 *   Qarz qoldig'i (Val):   ...
 *   Tel : ...
 *
 * Maketda yozuvlarning uch tili bor (ru / ru1 — kirill o'zbekcha / ru2 — lotin o'zbekcha).
 * Bu yerda lotin (ru2) ishlatiladi; kirill kerak bo'lsa [Labels.CYRILLIC] beriladi.
 * 1C sonlarni formatlamasdan chiqaradi, shuning uchun nol ham "0" bo'lib ko'rinadi — chekni bir xil
 * qilish uchun biz ham shunday qilamiz.
 */
data class Chek(
    /** Вакт — "15:16:18". */
    val time: String,
    /** Сана — "14.10.26". */
    val date: String,
    /** Номер — hujjat raqami. */
    val number: String,
    /** Клиент. */
    val client: String,
    /** Агент (Сотрудник). */
    val agent: String,
    /** ДолгСум / ДолгВал — savdodan OLDINGI qarz. */
    val debtSom: String,
    val debtUsd: String,
    val lines: List<Line>,
    /** СуммаСум / СуммаВал. */
    val sumSom: String,
    val sumUsd: String,
    /** СкидкаСум / СкидкаВал. */
    val discountSom: String,
    val discountUsd: String,
    /** Нал / Пер / Вал. */
    val cash: String,
    val card: String,
    val paidUsd: String,
    /** КонСум / КонВал — chekdan keyingi qarz. */
    val endSom: String,
    val endUsd: String,
    /** ТелФирма. */
    val phone: String,
    val labels: Labels = Labels.LATIN,
) {
    /** Maketdagi "[Номер] || [Товар]" va "[Блок] [Кол] х [Цена] = [Сум]". */
    data class Line(val no: Int, val name: String, val block: String, val qty: String, val price: String, val sum: String) {
        /** "1 || Bodom Kalifor M" */
        val title: String get() = "$no || $name"

        /** "2bl 40 х 117 000 = 4 680 000" (blok bo'lmasa — blokisiz). */
        val calc: String get() = (if (block.isEmpty()) "" else "$block ") + qty + " х " + price + " = " + sum
    }

    /** Maketdagi yozuvlar (1C'dan ko'chirilgan). */
    data class Labels(
        val time: String, val date: String, val number: String, val client: String, val agent: String,
        val debtSom: String, val debtUsd: String, val sum: String, val som: String, val usd: String,
        val discount: String, val payment: String, val cash: String, val card: String, val paidUsd: String,
        val endSom: String, val endUsd: String, val phone: String,
    ) {
        companion object {
            /** Maketning ru2 (UZB) tili. */
            val LATIN = Labels(
                time = "Vaqt:", date = "Sana:", number = "Mijoz:", client = "Mijoz:", agent = "Agent:",
                debtSom = "Qarz qoldig'i  (So'm):", debtUsd = "Qarz qoldig'i (Val):",
                sum = "Summa", som = "Sum:", usd = "Val:", discount = "Chegirma", payment = "To'lov",
                cash = "Naqd:", card = "Plastik:", paidUsd = "Val ( $ ):",
                endSom = "Qarz qoldig'i (So'm)", endUsd = "Qarz qoldig'i (Val):", phone = "Tel :",
            )

            /** Maketning ru1 (Узб) tili — 1C kirillda chop etsa. */
            val CYRILLIC = Labels(
                time = "Вакт:", date = "Сана:", number = "Мижоз:",
                client = "Мижоз:", agent = "Агент:",
                debtSom = "Карз колдиги (Сум):",
                debtUsd = "Карз колдиги (Вал):",
                sum = "Сумма", som = "Сум:", usd = "Вал:",
                discount = "Чегирма", payment = "Тулов",
                cash = "Накд:", card = "Пластик:", paidUsd = "Вал ( $ ):",
                endSom = "Карз колдиги (Сум):",
                endUsd = "Карз колдиги (Вал):", phone = "Тел :",
            )
        }
    }

    /** Ulashish va nusxa olish uchun oddiy matn (chop etilganidek tartibda). */
    fun text(): String = buildString {
        val l = labels
        append(l.time).append(' ').append(time).append("   ").append(l.date).append(' ').append(date).append('\n')
        if (number.isNotEmpty()) append(l.number).append(' ').append(number).append('\n')
        if (client.isNotEmpty()) append(l.client).append(' ').append(client).append('\n')
        if (agent.isNotEmpty()) append(l.agent).append(' ').append(agent).append('\n')
        append(l.debtSom).append(' ').append(debtSom).append('\n')
        append(l.debtUsd).append(' ').append(debtUsd).append('\n')
        append("------------------------\n")
        for (x in lines) append(x.title).append('\n').append("   ").append(x.calc).append('\n')
        append("------------------------\n")
        append(l.sum).append(' ').append(l.som).append(' ').append(sumSom).append('\n')
        append("      ").append(l.usd).append(' ').append(sumUsd).append('\n')
        append(l.discount).append(' ').append(l.som).append(' ').append(discountSom).append('\n')
        append("        ").append(l.usd).append(' ').append(discountUsd).append('\n')
        append(l.payment).append(' ').append(l.cash).append(' ').append(cash).append('\n')
        append("       ").append(l.card).append(' ').append(card).append('\n')
        append("       ").append(l.paidUsd).append(' ').append(paidUsd).append('\n')
        append(l.endSom).append(' ').append(endSom).append('\n')
        append(l.endUsd).append(' ').append(endUsd).append('\n')
        if (phone.isNotEmpty()) append(l.phone).append(' ').append(phone).append('\n')
    }

    companion object {
        /** 1C sonni formatlamasdan chiqaradi: bo'shliq bilan ajratilgan butun son, nol — "0". */
        fun num(v: Double): String = NumFmt.som(v)

        private val TEL = Regex("^\\s*(tel|тел|telefon|телефон)\\s*[:.]?\\s*", RegexOption.IGNORE_CASE)

        /** Chek osti telefonlari -> 1C "ТелФирма" qatori: "Tel :" yozuvi ikki marta chiqmasin. */
        fun phoneOf(lines: List<String>): String =
            lines.map { TEL.replace(it.trim(), "") }.filter { it.isNotEmpty() }.joinToString(", ")

        private fun qtyOf(v: Double) = NumFmt.qty(v)

        /**
         * Ilovada qilingan savdodan chek (1C maketidagi maydonlar to'liq: naqd/plastik ham bor).
         * Savdo dollarda bo'lsa summalar "Val" ustuniga, so'mda bo'lsa "Sum" ustuniga tushadi — 1C'dagidek.
         */
        fun ofSale(
            s: uz.imsoft.shared.report.AppSale, number: String, client: String, agent: String, phone: String,
            labels: Labels = Labels.LATIN,
        ): Chek {
            val (time, date) = ReceiptBuilder.timeAndDate(s.stamp)
            fun money(v: Double) = if (s.usd) NumFmt.usd(v) else num(v)
            val rows = s.lines.mapIndexed { i, x ->
                Line(i + 1, x.name, "", qtyOf(x.qty), money(x.price), money(x.qty * x.price))
            }
            val net = s.total - (if (s.isReturn) 0.0 else s.discount)
            val before = s.debtBefore ?: 0.0
            val end = if (s.isReturn) before - net + s.paid else before + net - s.paid
            val zero = num(0.0)
            return Chek(
                time, date, number, client, agent,
                num(before), NumFmt.usd(0.0), rows,
                if (s.usd) zero else num(s.total), if (s.usd) NumFmt.usd(s.total) else NumFmt.usd(0.0),
                if (s.usd) zero else num(s.discount), if (s.usd) NumFmt.usd(s.discount) else NumFmt.usd(0.0),
                if (s.usd) zero else num(s.cash), if (s.usd) zero else num(s.card), NumFmt.usd(if (s.usd) s.paid else 0.0),
                num(end), NumFmt.usd(0.0), phone, labels,
            )
        }

        /**
         * 1C tarixidagi hujjatdan ([LedgerDoc]) chek. To'lov taqsimoti (naqd/plastik) 1C hisobotida yo'q —
         * savdoda to'langan summa naqd ustunida ko'rsatiladi.
         */
        fun ofDoc(
            doc: LedgerDoc, number: String, client: String, agent: String, phone: String,
            debtBefore: Money2 = Money2.ZERO, cash: Double = 0.0, card: Double = 0.0, paidUsd: Double = 0.0,
            labels: Labels = Labels.LATIN,
        ): Chek {
            val (time, date) = ReceiptBuilder.timeAndDate(doc.stamp)
            val rows = ArrayList<Line>()
            if (doc.isGoods) {
                for ((i, e) in doc.goods.withIndex()) {
                    val price = e.v(Col.PRICE)
                    val sum = LedgerDoc.amountOf(e)
                    rows += Line(
                        i + 1, e.product, "", qtyOf(e.qty),
                        if (price.usd != 0.0) NumFmt.usd(price.usd) else num(price.som),
                        if (sum.usd != 0.0) NumFmt.usd(sum.usd) else num(sum.som),
                    )
                }
            } else {
                for ((i, e) in doc.lines.withIndex()) {
                    val sum = LedgerDoc.amountOf(e)
                    rows += Line(i + 1, e.product.ifBlank { doc.kindText }, "", qtyOf(1.0), num(sum.som), num(sum.som))
                }
            }
            val end = doc.balanceAfter ?: (debtBefore + doc.debtEffect)
            val disc = if (doc.kind == EntryKind.SALE) doc.discount else Money2.ZERO
            return Chek(
                time, date, number, client, agent,
                num(debtBefore.som), NumFmt.usd(debtBefore.usd), rows,
                num(doc.total.som), NumFmt.usd(doc.total.usd),
                num(disc.som), NumFmt.usd(disc.usd),
                num(cash), num(card), NumFmt.usd(paidUsd),
                num(end.som), NumFmt.usd(end.usd), phone, labels,
            )
        }
    }
}

/** PDF va chop etish uchun — maketdagi tartib va o'lchamlar (Arial qalin, 80 mm ustun). */
fun Chek.html(): String {
    fun e(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    val l = labels
    val b = StringBuilder()
    b.append("<!DOCTYPE html><html><head><meta charset='utf-8'><style>")
    b.append("body{font-family:Arial,sans-serif;font-weight:bold;color:#000;margin:0;padding:12px}")
    b.append(".r{max-width:300px;margin:0 auto}")
    b.append(".a{display:flex;justify-content:space-between;font-size:9pt}")
    b.append(".p{display:flex;justify-content:space-between;align-items:baseline;margin:1px 0}")
    b.append(".p .k{font-size:11pt}.p .v{font-size:14pt}")
    b.append(".s{display:flex;justify-content:space-between;font-size:9pt;margin:1px 0}")
    b.append(".t{display:flex;justify-content:flex-end;gap:10px;font-size:11pt;margin:1px 0}.t .c0{margin-right:auto;font-size:10pt}")
    b.append("table{width:100%;border-collapse:collapse;margin:6px 0}td{border:1px solid #000;padding:3px 5px;font-size:9pt}")
    b.append(".n{display:block}.f{display:block;text-align:right}hr{border:0;border-top:1px solid #000;margin:6px 0}")
    b.append(".tel{text-align:center;font-size:12pt;margin-top:8px}</style></head><body><div class='r'>")
    b.append("<div class='a'><span>").append(e(l.time)).append(' ').append(e(time)).append("</span><span>")
        .append(e(l.date)).append(' ').append(e(date)).append("</span></div>")
    if (number.isNotEmpty()) b.append("<div class='p'><span class='k'>").append(e(l.number)).append("</span><span class='v'>").append(e(number)).append("</span></div>")
    if (client.isNotEmpty()) b.append("<div class='p'><span class='k'>").append(e(l.client)).append("</span><span class='v'>").append(e(client)).append("</span></div>")
    if (agent.isNotEmpty()) b.append("<div class='s' style='font-size:11pt'><span>").append(e(l.agent)).append("</span><span>").append(e(agent)).append("</span></div>")
    b.append("<div class='s'><span>").append(e(l.debtSom)).append("</span><span>").append(e(debtSom)).append("</span></div>")
    b.append("<div class='s'><span>").append(e(l.debtUsd)).append("</span><span>").append(e(debtUsd)).append("</span></div>")
    b.append("<table>")
    for (x in lines) b.append("<tr><td><span class='n'>").append(e(x.title)).append("</span><span class='f'>").append(e(x.calc)).append("</span></td></tr>")
    b.append("</table><hr>")
    fun t3(c0: String, k: String, v: String) {
        b.append("<div class='t'><span class='c0'>").append(e(c0)).append("</span><span>").append(e(k)).append("</span><span>").append(e(v)).append("</span></div>")
    }
    t3(l.sum, l.som, sumSom)
    t3("", l.usd, sumUsd)
    b.append("<hr>")
    t3(l.discount, l.som, discountSom)
    t3("", l.usd, discountUsd)
    t3(l.payment, l.cash, cash)
    t3("", l.card, card)
    t3("", l.paidUsd, paidUsd)
    b.append("<div class='s' style='font-size:11pt'><span>").append(e(l.endSom)).append("</span><span>").append(e(endSom)).append("</span></div>")
    b.append("<div class='s' style='font-size:11pt'><span>").append(e(l.endUsd)).append("</span><span>").append(e(endUsd)).append("</span></div>")
    if (phone.isNotEmpty()) b.append("<div class='tel'>").append(e(l.phone)).append(' ').append(e(phone)).append("</div>")
    b.append("</div></body></html>")
    return b.toString()
}
