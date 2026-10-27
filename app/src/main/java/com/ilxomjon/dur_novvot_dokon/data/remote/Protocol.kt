package com.ilxomjon.dur_novvot_dokon.data.remote

import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.domain.CartLine
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.DealMode
import com.ilxomjon.dur_novvot_dokon.domain.PriceType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Serverdagi "savollar" jadvali orqali ishlaydigan matn protokoli. Formatlar avvalgi ilova bilan aynan bir xil.
 * So'rov turlari: 1 = buyurtma, 3 = do'kon qarzi, 4 = to'lov, 5 = hisobot.
 */
object Protocol {
    const val TYPE_ORDER = "1"
    const val TYPE_DEBT = "3"
    const val TYPE_PAYMENT = "4"
    const val TYPE_REPORT = "5"

    fun timestamp(d: Date): String = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US).format(d)
    fun reportDate(d: Date): String = SimpleDateFormat("dd.MM.yyyy", Locale.US).format(d)

    /** Erkin matn ichidagi protokol ajratgichlarini olib tashlaydi. */
    fun cleanText(s: String): String =
        s.replace('^', ' ').replace('~', ' ').replace('@', ' ').replace('\'', '`').replace('\n', ' ').replace('\r', ' ').trim()

    class Payments(val cashSom: Double = 0.0, val cashUsd: Double = 0.0, val card: Double = 0.0)

    fun order(
        agentId: String, storeId: String, time: Date, currency: Currency, priceType: PriceType,
        pay: Payments, discount: Double, rate: String, mode: DealMode, printer: String, lines: List<CartLine>,
    ): String {
        val head = listOf(
            agentId, storeId, timestamp(time), currency.code, priceType.code,
            Money.plain(pay.cashSom), Money.plain(pay.cashUsd), Money.plain(pay.card), Money.plain(discount),
            rate.ifBlank { "0" }, mode.code, printer,
        ).joinToString("^")
        val body = lines.joinToString("") { "${it.productId}^${Money.qty(it.qty)}^${Money.plain(it.price)}@" }
        return "$head~$body"
    }

    fun payment(
        agentId: String, storeId: String, time: Date, rate: String, note: String,
        cash: Double, transfer: Double, click: Double, printer: String,
    ): String = listOf(
        agentId, storeId, Money.plain(cash), "0", timestamp(time), rate.ifBlank { "0" }, cleanText(note),
        Money.plain(cash), Money.plain(transfer), Money.plain(click), printer,
    ).joinToString("^") + "~"

    fun report(from: Date, to: Date, storeId: String): String = "${reportDate(from)}#${reportDate(to)}#$storeId"

    /** Serverdan kelgan hisobot HTML'ini ekranga mos holga keltiradi (avvalgi ilova bilan bir xil). */
    fun reportHtml(raw: String): String = raw.replace("\n", " ").replace("\\", "").replace("`", "\"")

    /** "qarz_som:qarz_val" javobini ajratadi. */
    fun parseDebt(answer: String): Pair<String, String> {
        val parts = answer.split(":")
        val som = parts.getOrNull(0)?.trim().orEmpty().ifEmpty { "0" }
        val usd = parts.getOrNull(1)?.trim().orEmpty().ifEmpty { "0" }
        return som to usd
    }
}
