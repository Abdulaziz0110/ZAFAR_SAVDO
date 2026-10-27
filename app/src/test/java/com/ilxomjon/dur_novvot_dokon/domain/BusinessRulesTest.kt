package com.ilxomjon.dur_novvot_dokon.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * C8 qoidalari va v2.3 mantiq testlari. C8: qoldiq tekshirilmaydi, kirim narxidan arzon sotish to'smaydi,
 * narx erkin — tekshiruvchi faqat ogohlantiradi, hech narsani to'smaydi.
 */
class BusinessRulesTest {
    private val DAY = 86_400_000L
    private fun order(id: Long, daysAgo: Int, now: Long, vararg lines: Triple<String, Double, Double>, cur: Currency = Currency.SOM, mode: DealMode = DealMode.SALE) =
        SentOrder(id, now - daysAgo * DAY, "S1", "Do'kon", mode, PriceType.WHOLESALE, cur, lines.sumOf { it.second * it.third }, 0.0, 0.0,
            lines.map { SentLine("n" + it.first, it.second, it.third, it.first) })

    private fun product(id: String, wholesaleSom: Double, cost: Double, baseSom: Boolean = true) =
        Product("g", id, "n$id", baseSom, wholesaleSom * 1.1, wholesaleSom / 12650, wholesaleSom, wholesaleSom / 12650, cost, cost / 12650, 1.0)

    @Test fun pricing_belowCostIsAllowed_andRateZeroDoesNotCrash() {
        val p = product("A", 1000.0, 5000.0)                      // sotuv narxi kirimdan arzon
        assertEquals(1000.0, Pricing.unitPrice(p, PriceType.WHOLESALE, Currency.SOM, 12650.0), 0.0)
        assertEquals(0.0, Pricing.unitPrice(p, PriceType.WHOLESALE, Currency.USD, 0.0), 0.0)   // kurs yo'q
        val usdBase = product("B", 0.0, 0.0, baseSom = false).copy(wholesaleUsd = 2.0)
        assertEquals(25300.0, Pricing.unitPrice(usdBase, PriceType.WHOLESALE, Currency.SOM, 12650.0), 0.001)
    }

    @Test fun checker_onlyWarns_andAtMostTwo() {
        val now = 100 * DAY
        val hist = listOf(
            order(3, 1, now, Triple("A", 10.0, 1000.0), Triple("B", 5.0, 2000.0), Triple("C", 1.0, 100.0)),
            order(2, 8, now, Triple("A", 10.0, 1000.0), Triple("B", 6.0, 2000.0), Triple("C", 1.0, 100.0)),
        )
        val lines = listOf(
            CartLine(0, "A", "nA", 1.0, 100.0, 1000.0),   // 10x katta
            CartLine(0, "B", "nB", 1.0, 5.0, 100.0),      // narx 95% arzon
            CartLine(0, "C", "nC", 1.0, 50.0, 1.0),       // ikkalasi ham
        )
        val w = CartChecker.check(lines, Currency.SOM, DealMode.SALE, hist, emptyList(), emptyMap())
        assertEquals(CartChecker.MAX, w.size)
        assertTrue(w[0] is CartWarning.BigQty)
        // "Shunday qoldir" bosilsa shu ogohlantirishlar qayta chiqmaydi.
        val w2 = CartChecker.check(lines, Currency.SOM, DealMode.SALE, hist, emptyList(), emptyMap(), dismissed = w.map { it.key }.toSet())
        assertTrue(w2.none { it.key in w.map { x -> x.key } })
        // Qaytarish rejimida va tarix bo'lmasa — jim.
        assertTrue(CartChecker.check(lines, Currency.SOM, DealMode.RETURN, hist, emptyList(), emptyMap()).isEmpty())
        assertTrue(CartChecker.check(lines, Currency.SOM, DealMode.SALE, emptyList(), emptyList(), emptyMap()).isEmpty())
    }

    @Test fun checker_lastPrice_sameCurrencyOnly() {
        val now = 100 * DAY
        val hist = listOf(order(2, 1, now, Triple("A", 1.0, 3.5), cur = Currency.USD), order(1, 5, now, Triple("A", 1.0, 40000.0)))
        assertEquals(40000.0, CartChecker.lastPrice(hist, "A", Currency.SOM)!!.first, 0.0)
        assertEquals(3.5, CartChecker.lastPrice(hist, "A", Currency.USD)!!.first, 0.0)
    }

    @Test fun suggestions_explanation_and_feedback() {
        val now = 200 * DAY
        val hist = (0 until 5).map { order(10L - it, 8 + it * 7, now, Triple("A", 10.0, 1000.0)) }
        val s = Suggestions.build(hist, emptyList(), now).first { it.productId == "A" }
        assertEquals(10.0, s.qty, 0.0)
        assertEquals(7.0, s.intervalDays!!, 0.01)
        assertEquals(8.0, s.daysSince!!, 0.01)
        assertTrue(s.confident)
        // "Kerak emas" — 7 kun taklif qilinmaydi, keyin qaytadi.
        val fb = mapOf("A" to (FeedbackReason.NOT_NEEDED to now - 2 * DAY))
        assertTrue(Suggestions.build(hist, emptyList(), now, feedback = fb).none { it.productId == "A" })
        val old = mapOf("A" to (FeedbackReason.NOT_NEEDED to now - 9 * DAY))
        assertTrue(Suggestions.build(hist, emptyList(), now, feedback = old).any { it.productId == "A" })
        // Tarix kam bo'lsa ishonch yo'q ("taxminiy").
        val few = Suggestions.build(hist.take(1), emptyList(), now).first()
        assertTrue(!few.confident)
    }

    @Test fun localReport_summary_and_csv() {
        val now = 50 * DAY
        val o = listOf(
            order(1, 0, now, Triple("A", 2.0, 1000.0)),
            order(2, 0, now, Triple("B", 1.0, 500.0), mode = DealMode.RETURN),
            order(3, 0, now, Triple("C", 1.0, 3.0), cur = Currency.USD).copy(pending = true),
        )
        val p = listOf(SentPayment(1, now, "S1", "Do'kon", 1000.0, 200.0, 0.0, "izoh; \"qo'shtirnoq\"", pending = true))
        val s = LocalReport.summarize(now - DAY, now + DAY, o, p)
        assertEquals(2, s.saleOrders); assertEquals(2000.0, s.saleSom, 0.0); assertEquals(3.0, s.saleUsd, 0.0)
        assertEquals(1, s.returnOrders); assertEquals(500.0, s.returnSom, 0.0)
        assertEquals(1200.0, s.payTotal, 0.0); assertEquals(1, s.unsentOrders); assertEquals(1, s.unsentPayments)
        val csv = LocalReport.paymentsCsv(p, { "T-0001" }, { "Navbatda" })
        assertTrue(csv.startsWith("﻿"))
        assertTrue(csv.contains("\"izoh; \"\"qo'shtirnoq\"\"\""))   // ; va " to'g'ri qochirilgan
        val oc = LocalReport.ordersCsv(o, { "B-1" }, { "ok" })
        assertEquals(1 + 3, oc.trim().split("\r\n").size)
    }

    @Test fun builtInPassword_notPlainInSource_butStillWorks() {
        val pw = ServerConfig.DEFAULT.password
        val h = MessageDigest.getInstance("SHA-256").digest(pw.toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals(EXPECTED_SHA, h)
        // Manba kodida parol oddiy matn sifatida yo'q.
        val roots = listOf(java.io.File("src/main/java"), java.io.File("app/src/main/java"))
        val root = roots.firstOrNull { it.isDirectory } ?: return
        val leaked = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.filter { it.readText().contains(pw) }.toList()
        assertTrue("parol manba kodda ochiq: $leaked", leaked.isEmpty())
    }

    private companion object { const val EXPECTED_SHA = "8def6c731561e6f5d5a1f5f4cfb29a57b786d7493fd1615c286fa53819b899cc" }
}
