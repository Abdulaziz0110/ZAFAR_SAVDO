package com.ilxomjon.dur_novvot_dokon.domain

import com.ilxomjon.dur_novvot_dokon.core.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Faqat SHU TELEFON ma'lumotidan (yuborilgan/kutilayotgan jurnal) hisoblanadigan hisobot. Serverdagi haqiqiy holat emas:
 * boshqa agentlar, ofisdagi tuzatishlar, qaytarishlar bu yerda ko'rinmaydi. Ekranda doim "Lokal" belgisi bilan.
 */
data class StoreDayRow(val storeId: String, val storeName: String, val orders: Int, val som: Double, val usd: Double, val paid: Double)

data class LocalSummary(
    val from: Long,
    val to: Long,
    val saleOrders: Int,
    val saleSom: Double,
    val saleUsd: Double,
    val returnOrders: Int,
    val returnSom: Double,
    val returnUsd: Double,
    val orderPaidSom: Double,
    val orderPaidUsd: Double,
    val payments: Int,
    val payCash: Double,
    val payTransfer: Double,
    val payClick: Double,
    val unsentOrders: Int,
    val unsentPayments: Int,
    val stores: List<StoreDayRow>,
) {
    val payTotal: Double get() = payCash + payTransfer + payClick
}

object LocalReport {
    fun summarize(from: Long, to: Long, orders: List<SentOrder>, payments: List<SentPayment>): LocalSummary {
        val o = orders.filter { it.sentAt in from until to }
        val p = payments.filter { it.sentAt in from until to }
        fun net(x: SentOrder) = Money.round2(x.total - x.discount)
        val sale = o.filter { it.mode == DealMode.SALE }
        val ret = o.filter { it.mode == DealMode.RETURN }
        val byStore = o.groupBy { it.storeId }.map { (id, list) ->
            StoreDayRow(
                id, list.first().storeName, list.size,
                list.filter { it.currency == Currency.SOM }.sumOf { sign(it) * net(it) },
                list.filter { it.currency == Currency.USD }.sumOf { sign(it) * net(it) },
                p.filter { it.storeId == id }.sumOf { it.total },
            )
        }.sortedByDescending { it.som + it.usd * 1e4 }
        return LocalSummary(
            from, to,
            sale.size, sale.filter { it.currency == Currency.SOM }.sumOf { net(it) }, sale.filter { it.currency == Currency.USD }.sumOf { net(it) },
            ret.size, ret.filter { it.currency == Currency.SOM }.sumOf { net(it) }, ret.filter { it.currency == Currency.USD }.sumOf { net(it) },
            o.filter { it.currency == Currency.SOM }.sumOf { it.paid }, o.filter { it.currency == Currency.USD }.sumOf { it.paid },
            p.size, p.sumOf { it.cash }, p.sumOf { it.transfer }, p.sumOf { it.click },
            o.count { it.pending }, p.count { it.pending },
            byStore,
        )
    }

    private fun sign(o: SentOrder) = if (o.mode == DealMode.RETURN) -1.0 else 1.0

    // ---------------- CSV (Excel ochadi; ajratgich ";" — o'zbek/rus Excel uchun) ----------------

    private fun csv(v: String): String = if (v.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    private fun num(v: Double) = Money.plain(v)
    private fun dt(ms: Long) = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(Date(ms))

    /** Buyurtma qatorlari: har tovar bitta qator. BOM bilan (Excel kirillni to'g'ri ochishi uchun). */
    fun ordersCsv(orders: List<SentOrder>, receipt: (SentOrder) -> String, status: (SentOrder) -> String): String {
        val sb = StringBuilder("﻿")
        sb.append("Kvitansiya;Sana;Do'kon;Rejim;Narx turi;Valyuta;Tovar kodi;Tovar;Soni;Narx;Summa;Chegirma (buyurtma);Holat\r\n")
        for (o in orders.sortedBy { it.sentAt }) {
            val mode = if (o.mode == DealMode.RETURN) "Qaytarish" else "Sotuv"
            val type = if (o.priceType == PriceType.CASH) "Naqd" else "Optom"
            val cur = if (o.currency == Currency.USD) "USD" else "UZS"
            o.lines.forEachIndexed { i, l ->
                sb.append(csv(receipt(o))).append(';').append(dt(o.sentAt)).append(';').append(csv(o.storeName)).append(';').append(mode).append(';')
                    .append(type).append(';').append(cur).append(';').append(csv(l.productId)).append(';').append(csv(l.name)).append(';')
                    .append(Money.qtyDisplay(l.qty)).append(';').append(num(l.price)).append(';').append(num(l.qty * l.price)).append(';')
                    .append(if (i == 0) num(o.discount) else "").append(';').append(csv(status(o))).append("\r\n")
            }
        }
        return sb.toString()
    }

    fun paymentsCsv(payments: List<SentPayment>, receipt: (SentPayment) -> String, status: (SentPayment) -> String): String {
        val sb = StringBuilder("﻿")
        sb.append("Kvitansiya;Sana;Do'kon;Naqd;O'tkazma;Click;Jami;Izoh;Holat\r\n")
        for (p in payments.sortedBy { it.sentAt }) {
            sb.append(csv(receipt(p))).append(';').append(dt(p.sentAt)).append(';').append(csv(p.storeName)).append(';')
                .append(num(p.cash)).append(';').append(num(p.transfer)).append(';').append(num(p.click)).append(';').append(num(p.total)).append(';')
                .append(csv(p.note)).append(';').append(csv(status(p))).append("\r\n")
        }
        return sb.toString()
    }

    // ---------------- HTML (Hisobot ekrani uchun, "Lokal" belgisi bilan) ----------------

    private fun h(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun html(s: LocalSummary, title: String, labels: Labels): String {
        val sb = StringBuilder()
        sb.append("<p><b>").append(h(labels.localBadge)).append("</b></p>")
        sb.append("<h3>").append(h(title)).append("</h3><table>")
        fun row(k: String, v: String) { sb.append("<tr><td>").append(h(k)).append("</td><td>").append(h(v)).append("</td></tr>") }
        row(labels.sales, "${s.saleOrders} · ${Money.display(s.saleSom)} ${labels.som}" + if (s.saleUsd > 0) " · ${Money.format(s.saleUsd, Currency.USD)} $" else "")
        row(labels.returns, "${s.returnOrders} · ${Money.display(s.returnSom)} ${labels.som}" + if (s.returnUsd > 0) " · ${Money.format(s.returnUsd, Currency.USD)} $" else "")
        row(labels.paidInOrders, "${Money.display(s.orderPaidSom)} ${labels.som}" + if (s.orderPaidUsd > 0) " · ${Money.format(s.orderPaidUsd, Currency.USD)} $" else "")
        row(labels.payments, "${s.payments} · ${Money.display(s.payTotal)} ${labels.som}")
        row(labels.cash, Money.display(s.payCash)); row(labels.transfer, Money.display(s.payTransfer)); row(labels.click, Money.display(s.payClick))
        row(labels.unsent, "${s.unsentOrders} / ${s.unsentPayments}")
        sb.append("</table>")
        if (s.stores.isNotEmpty()) {
            sb.append("<h3>").append(h(labels.byStore)).append("</h3><table><tr><th>").append(h(labels.store)).append("</th><th>#</th><th>")
                .append(h(labels.som)).append("</th><th>$</th><th>").append(h(labels.payments)).append("</th></tr>")
            for (r in s.stores) {
                sb.append("<tr><td>").append(h(r.storeName)).append("</td><td>").append(r.orders).append("</td><td>").append(h(Money.display(r.som)))
                    .append("</td><td>").append(h(Money.format(r.usd, Currency.USD))).append("</td><td>").append(h(Money.display(r.paid))).append("</td></tr>")
            }
            sb.append("</table>")
        }
        return sb.toString()
    }

    class Labels(
        val localBadge: String, val sales: String, val returns: String, val paidInOrders: String, val payments: String,
        val cash: String, val transfer: String, val click: String, val unsent: String, val byStore: String, val store: String, val som: String,
    )
}
