package uz.imsoft.shared.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerSearchTest {
    private fun sale(st: Long, p: String, q: Double, price: Double, kt: String = "Savdo So'm") =
        LedgerEntry(st, EntryKind.SALE, kt, p, q, mapOf(Col.PRICE to Money2(price, 0.0), Col.SUM to Money2(q * price, 0.0)))
    private fun usdSale(st: Long, p: String, q: Double, price: Double) =
        LedgerEntry(st, EntryKind.SALE, "Savdo $", p, q, mapOf(Col.PRICE to Money2(0.0, price), Col.SUM to Money2(0.0, q * price)))
    private fun pay(st: Long, som: Double, note: String = "Мижоз накд сумда тулов килди") =
        LedgerEntry(st, EntryKind.PAYMENT, "To'lov So'm", note, 0.0, mapOf(Col.PAYMENT to Money2(som, 0.0)))
    private fun ret(st: Long, p: String, q: Double, sum: Double) =
        LedgerEntry(st, EntryKind.RETURN, "Qaytarib olish So'm", p, q, mapOf(Col.RETURN to Money2(sum, 0.0)))

    private val entries = listOf(
        sale(20261029161619, "Pista Dolana (DA)", 10.0, 162000.0),
        sale(20261029161619, "Bodom Yongoq M", 20.0, 120000.0),
        sale(20261029161619, "Pista Dolana (D)", 40.0, 182000.0),
        sale(20261028121800, "Pista Mumtoz (M)", 40.0, 187000.0),
        sale(20261028121800, "Keshu 210", 20.0, 136000.0),
        sale(20261028121800, "Qulupnay (Sublimit)", 2.5, 200000.0),
        sale(20261027104117, "Orik Bargak Balonchik", 99.0, 38000.0),
        sale(20261027104117, "Bodom Kalifor T", 40.0, 120000.0),
        pay(20261028161220, 21_510_000.0),
        pay(20261026161900, 8_000_000.0, "Мижоз Click (So'm) орқали тўлов қилди"),
        ret(20261027103700, "Pista Dolana (D)", 2.5, 460000.0),
        usdSale(20260915120000, "Pista Akbariy (AK)", 40.0, 15.5),
    )
    private val idx = LedgerIndex(entries)
    private val today = 20261029180000L

    private fun find(q: String) = idx.search(q, today)
    private fun stamps(q: String) = find(q).hits.map { it.doc.stamp }

    @Test fun groupsIntoDocuments() {
        assertEquals(7, idx.docs.size)
        val d = idx.docs.first()
        assertEquals(20261029161619L, d.stamp)
        assertEquals(3, d.lines.size)
        assertEquals(1_620_000.0 + 2_400_000.0 + 7_280_000.0, d.total.som, 0.001)
        assertEquals(70.0, d.qty, 0.0)
    }

    @Test fun emptyQueryReturnsAll() {
        assertEquals(7, find("").hits.size)
        assertEquals(7, find("  dan  ").hits.size)
    }

    @Test fun productName_latinCyrillicTyposLayout() {
        assertEquals(listOf(20261029161619L, 20261028121800L, 20261027103700L, 20260915120000L), stamps("pista"))
        assertEquals(stamps("pista"), stamps("писта"))
        assertTrue(find("pistta").hits.isNotEmpty())
        assertEquals(listOf(20261027104117L), stamps("орик"))
        // ЙЦУКЕН adashgan: "зшыеф" = "pista"
        val r = find("зшыеф")
        assertTrue(r.approximate)
        assertTrue(r.hits.isNotEmpty())
    }

    @Test fun nameAndQtyOnSameLine() {
        // 40 kg pista: 29.10 (Dolana D), 28.10 (Mumtoz), 15.09 ($). 27.10 dagi hujjatda 40 kg BODOM bor — chiqmasligi kerak.
        assertEquals(listOf(20261029161619L, 20261028121800L, 20260915120000L), stamps("pista 40"))
        assertEquals(stamps("pista 40"), stamps("pista 40kg"))
        assertEquals(stamps("pista 40"), stamps("40 kg pista"))
        assertEquals(stamps("pista 40"), stamps("писта 40кг"))
        assertEquals(stamps("pista 40"), stamps("писта 40 кг"))
        val h = find("pista 40").hits.first()
        assertEquals(setOf(2), h.lines)
        assertFalse(h.approximate)
    }

    @Test fun priceAndAmounts() {
        assertEquals(listOf(20261028121800L), stamps("200000"))
        assertEquals(stamps("200000"), stamps("200 000"))
        assertEquals(stamps("200000"), stamps("200k"))
        assertEquals(stamps("200000"), stamps("200 ming"))
        assertEquals(listOf(20261027104117L), stamps("3762000"))
        assertEquals(listOf(20261027104117L), stamps("3 762 000"))
        assertEquals(listOf(20261028161220L), stamps("21,51mln"))
        assertEquals(listOf(20261028161220L), stamps("21,51млн"))
        assertEquals(stamps("200000"), stamps("200минг"))
        assertEquals(listOf(20260915120000L), stamps("15.5$"))
        assertEquals(listOf(20260915120000L), stamps("$620"))
        // narx + nom: 182 000 dagi Dolana faqat 29.10 da
        assertEquals(listOf(20261029161619L), stamps("dolana 182000"))
    }

    @Test fun numberPrefixFallbackIsApproximate() {
        val r = find("3762")
        assertTrue(r.approximate)
        assertEquals(listOf(20261027104117L), r.hits.map { it.doc.stamp })
    }

    @Test fun nameDigitsMatchExactly() {
        assertEquals(listOf(20261028121800L), stamps("keshu 210"))
        assertTrue(stamps("keshu 21").isEmpty() || find("keshu 21").approximate)
    }

    @Test fun datesAndRanges() {
        assertEquals(listOf(20261029161619L), stamps("29.10"))
        assertEquals(listOf(20261029161619L), stamps("29.10.2026"))
        assertEquals(listOf(20261029161619L), stamps("2026-10-29"))
        assertEquals(listOf(20261029161619L), stamps("bugun"))
        assertEquals(listOf(20261028161220L, 20261028121800L), stamps("kecha"))
        assertEquals(listOf(20261028161220L, 20261028121800L, 20261027104117L, 20261027103700L), stamps("27.10-28.10"))
        assertEquals(stamps("27.10-28.10"), stamps("27.10 dan 28.10 gacha"))
        assertEquals(listOf(20261027104117L, 20261027103700L), stamps("27 oktabr"))
        assertEquals(listOf(20260915120000L), stamps("sentabr"))
        assertEquals(listOf(20260915120000L), stamps("сентябрь"))
        // Ikki kun — YOKI
        assertEquals(listOf(20261029161619L, 20261026161900L), stamps("29.10 26.10"))
        assertEquals(listOf(20261029161619L), stamps("16:16"))
    }

    @Test fun kindsAndCurrency() {
        assertEquals(listOf(20261028161220L, 20261026161900L), stamps("to'lov"))
        assertEquals(stamps("to'lov"), stamps("тўлов"))
        assertFalse(find("to'lov").approximate)
        assertEquals(listOf(20261027103700L), stamps("vozvrat"))
        assertEquals(listOf(20261027103700L), stamps("qaytarish pista"))
        assertEquals(listOf(20261028161220L), stamps("to'lov >10mln"))
        assertEquals(listOf(20261026161900L), stamps("click"))
        assertEquals(listOf(20260915120000L), stamps("dollar"))
        assertEquals(listOf(20261029161619L, 20261028121800L, 20261027104117L), stamps("savdo oktabr"))
    }

    @Test fun exclusion() {
        assertEquals(listOf(20261027104117L), stamps("savdo oktabr -pista"))
    }

    @Test fun summaryCountsMatchedLinesOnly() {
        val s = find("pista 40kg oktabr").summary
        assertEquals(2, s.docs)
        assertEquals(80.0, s.goodsQty, 0.0)
        assertEquals(40 * 182000.0 + 40 * 187000.0, s.goodsSum.som, 0.01)
        val all = find("oktabr").summary
        assertEquals(3, all.sales)
        assertEquals(2, all.payments)
        assertEquals(29_510_000.0, all.paymentsTotal.som, 0.01)
        assertEquals(1, all.returns)
    }

    @Test fun relaxedAcrossLinesIsApproximate() {
        // "bodom 10" — 10 kg bodom yo'q, lekin 29.10 hujjatda bodom va (boshqa qatorda) 10 kg pista bor
        val r = find("bodom 10")
        assertTrue(r.approximate)
        assertTrue(r.hits.any { it.doc.stamp == 20261029161619L })
    }

    @Test fun highlightRanges() {
        val h = find("dolana").hits.first()
        val r = h.ranges[0]!!.first()
        assertEquals("Dolana", "Pista Dolana (DA)".substring(r.first, r.last + 1))
    }

    @Test fun filterByKindAndPeriod() {
        val r = idx.search("", today, LedgerFilter(kinds = setOf(EntryKind.SALE), from = 20261028000000, to = 20261029235959))
        assertEquals(listOf(20261029161619L, 20261028121800L), r.hits.map { it.doc.stamp })
    }

    @Test fun ambiguousShortDate_matchesQtyToo() {
        // "2.5" — 2-may yoki 2,5 kg: qulupnay va qaytarish (2,5 kg)
        assertEquals(listOf(20261028121800L, 20261027103700L), stamps("2.5"))
        assertEquals(listOf(20261028121800L), stamps("qulupnay 2,5"))
    }
}
