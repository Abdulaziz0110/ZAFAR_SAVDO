package com.ilxomjon.dur_novvot_dokon.data.remote

import com.ilxomjon.dur_novvot_dokon.domain.CartLine
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.DealMode
import com.ilxomjon.dur_novvot_dokon.domain.PriceType
import java.util.Calendar
import java.util.Date

/**
 * Protokol fixture holatlari. Har holat bitta satr qaytaradi; kutilgan qiymat `fixtures/protocol.txt` da.
 * Sana/vaqt default vaqt zonasida quriladi (testda Asia/Tashkent).
 */
object ProtocolCases {
    fun date(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0, s: Int = 0): Date =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, min, s) }.time

    private val T = date(2026, 10, 26, 14, 5, 9)
    private val MORNING = date(2026, 1, 3, 7, 8, 9)

    private fun line(id: String, qty: Double, price: Double, block: Double = 1.0) = CartLine(0, id, "nom $id", block, qty, price)

    val cases: Map<String, () -> String> = linkedMapOf(
        // ---------- Tur 1: buyurtma ----------
        "order.som.sale.basic" to {
            Protocol.order(
                "7", "S9", T, Currency.SOM, PriceType.WHOLESALE, Protocol.Payments(100000.0, 0.0, 5000.0), 0.0, "12650",
                DealMode.SALE, "1", listOf(line("T01", 5.0, 12500.0), line("T02", 2.5, 3000.0)),
            )
        },
        "order.som.cash.discount.printer3" to {
            Protocol.order(
                "12", "105", MORNING, Currency.SOM, PriceType.CASH, Protocol.Payments(250000.0, 0.0, 0.0), 12345.67, "12650.50",
                DealMode.SALE, "3", listOf(line("A-1", 10.0, 26500.0), line("A-2", 1.0, 1999.99)),
            )
        },
        "order.usd.wholesale" to {
            Protocol.order(
                "7", "S9", T, Currency.USD, PriceType.WHOLESALE, Protocol.Payments(0.0, 120.5, 0.0), 0.0, "12650",
                DealMode.SALE, "2", listOf(line("T01", 3.0, 10.25), line("T09", 0.5, 7.0)),
            )
        },
        "order.return.mode2" to {
            Protocol.order(
                "7", "S9", T, Currency.SOM, PriceType.WHOLESALE, Protocol.Payments(), 0.0, "12650",
                DealMode.RETURN, "1", listOf(line("T01", 2.0, 12500.0)),
            )
        },
        "order.blank.rate.is.zero" to {
            Protocol.order(
                "7", "S9", T, Currency.SOM, PriceType.WHOLESALE, Protocol.Payments(), 0.0, "",
                DealMode.SALE, "1", listOf(line("T01", 1.0, 100.0)),
            )
        },
        "order.fraction.qty.and.price" to {
            Protocol.order(
                "7", "S9", T, Currency.SOM, PriceType.WHOLESALE, Protocol.Payments(), 0.0, "12650",
                DealMode.SALE, "1",
                listOf(line("Q1", 1.0 / 3.0, 99.999), line("Q2", 0.1 + 0.2, 0.1 + 0.2), line("Q3", 1234567.0, 0.5), line("Q4", 2.0005, 1e9)),
            )
        },
        "order.single.line" to {
            Protocol.order(
                "1", "1", T, Currency.SOM, PriceType.CASH, Protocol.Payments(5000.0, 0.0, 0.0), 0.0, "12650",
                DealMode.SALE, "1", listOf(line("777", 1.0, 5000.0)),
            )
        },

        // ---------- Tur 4: to'lov ----------
        "payment.basic" to { Protocol.payment("7", "S9", T, "12650", "", 1000.0, 2000.0, 3000.0, "2") },
        "payment.cash.only" to { Protocol.payment("12", "105", MORNING, "12650.50", "qarz uchun", 1500000.0, 0.0, 0.0, "1") },
        "payment.fraction" to { Protocol.payment("7", "S9", T, "12650", "", 1250.5, 0.25, 99.999, "3") },
        "payment.note.cleaned" to { Protocol.payment("7", "S9", T, "12650", "a^b~c@d'e\nf\rg  ", 1.0, 0.0, 0.0, "1") },
        "payment.note.cyrillic" to { Protocol.payment("7", "S9", T, "12650", "Қарз тўлови, ёғ", 1000.0, 0.0, 0.0, "1") },
        "payment.blank.rate" to { Protocol.payment("7", "S9", T, "", "", 1000.0, 0.0, 0.0, "1") },

        // ---------- Tur 5: hisobot ----------
        "report.same.day" to { Protocol.report(T, T, "S9") },
        "report.range" to { Protocol.report(MORNING, T, "105") },

        // ---------- Vaqt formati ----------
        "timestamp.afternoon" to { Protocol.timestamp(T) },
        "timestamp.morning.padded" to { Protocol.timestamp(MORNING) },
        "reportDate" to { Protocol.reportDate(MORNING) },

        // ---------- Tur 3: qarz javobini ajratish ----------
        "debt.parse.normal" to { Protocol.parseDebt("1500000:12.5").let { "${it.first}|${it.second}" } },
        "debt.parse.spaces" to { Protocol.parseDebt(" 1500000 : 12.5 ").let { "${it.first}|${it.second}" } },
        "debt.parse.only.som" to { Protocol.parseDebt("1500000").let { "${it.first}|${it.second}" } },
        "debt.parse.empty" to { Protocol.parseDebt("").let { "${it.first}|${it.second}" } },
        "debt.parse.colon.only" to { Protocol.parseDebt(":").let { "${it.first}|${it.second}" } },
        "debt.parse.negative" to { Protocol.parseDebt("-250000:-1.5").let { "${it.first}|${it.second}" } },

        // ---------- Hisobot HTML'ini tozalash ----------
        "reportHtml.clean" to { Protocol.reportHtml("<table>\n<tr><td class=`a`>\\x</td></tr>\n</table>") },

        // ---------- Erkin matn tozalash ----------
        "cleanText" to { Protocol.cleanText("  ^izoh~bir@ikki'uch\r\n  ") },
    )
}
