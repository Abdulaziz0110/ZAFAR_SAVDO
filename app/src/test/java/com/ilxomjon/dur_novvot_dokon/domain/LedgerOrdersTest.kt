package com.ilxomjon.dur_novvot_dokon.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.imsoft.shared.report.Col
import uz.imsoft.shared.report.EntryKind
import uz.imsoft.shared.report.LedgerEntry
import uz.imsoft.shared.report.LedgerOwner
import uz.imsoft.shared.report.Money2
import uz.imsoft.shared.report.PendingMerge

/** v5.0: tavsiya mijozning butun tarixidan o'rganishi (1C hujjatlari ham). */
class LedgerOrdersTest {
    private fun e(stamp: Long, doc: String, store: String, product: String, qty: Double, price: Double) = LedgerEntry(
        stamp, EntryKind.SALE, LedgerOwner.tag(doc, store, "Ali"), product, qty,
        mapOf(Col.PRICE to Money2(som = price), Col.SUM to Money2(som = qty * price)),
    )

    private val ids = mapOf("pista" to "1001", "bodom" to "77")
    private val idOf: (String) -> String? = { n -> ids[LedgerOrders.key(n)] }

    @Test fun groupsEntriesIntoDocuments() {
        val list = listOf(
            e(20261101120000L, "1C 5", "s1", "Pista", 2.0, 100.0),
            e(20261101120000L, "1C 5", "s1", "Bodom", 1.0, 50.0),
            e(20261102120000L, "1C 6", "s1", "Pista", 3.0, 110.0),
            e(20261102120000L, "1C 7", "s2", "Pista", 9.0, 110.0), // boshqa mijoz
            e(20261102130000L, "1C 8", "s1", "Nomalum", 1.0, 1.0), // katalogda yo'q
        )
        val orders = LedgerOrders.of(list, "s1", idOf)
        assertEquals(2, orders.size)
        assertEquals(PendingMerge.millisOf(20261102120000L), orders[0].sentAt)
        assertEquals(listOf("1001"), orders[0].lines.map { it.productId })
        assertEquals(2, orders[1].lines.size)
        assertEquals(250.0, orders[1].total, 0.01)
        assertTrue(orders.all { it.mode == DealMode.SALE })
    }

    @Test fun mergeSkipsTheSameDocumentTwice() {
        val led = LedgerOrders.of(listOf(e(20261101120000L, "1C 5", "s1", "Pista", 2.0, 100.0)), "s1", idOf)
        val own = listOf(
            SentOrder(
                1L, PendingMerge.millisOf(20261101123000L), "s1", "Ali", DealMode.SALE, PriceType.WHOLESALE,
                Currency.SOM, 200.0, 0.0, 0.0, listOf(SentLine("Pista", 2.0, 100.0, "1001")),
            ),
        )
        assertEquals(1, LedgerOrders.merge(own, led).size)
        // Boshqa summadagi hujjat qo'shiladi
        val other = LedgerOrders.of(listOf(e(20261101120000L, "1C 9", "s1", "Pista", 7.0, 100.0)), "s1", idOf)
        assertEquals(2, LedgerOrders.merge(own, other).size)
    }

    @Test fun suggestionsLearnFromShopSales() {
        // Telefonda hech narsa yo'q, lekin 1C'da mijoz uch marta Pista olgan — taklifda chiqishi kerak
        val now = System.currentTimeMillis()
        fun day(d: Int) = PendingMerge.stampOf(now - d * 86_400_000L)
        val led = LedgerOrders.of(
            listOf(
                e(day(21), "1C 1", "s1", "Pista", 10.0, 100.0),
                e(day(14), "1C 2", "s1", "Pista", 12.0, 100.0),
                e(day(7), "1C 3", "s1", "Pista", 10.0, 100.0),
            ),
            "s1", idOf,
        )
        val s = Suggestions.build(LedgerOrders.merge(emptyList(), led), emptyList(), now)
        assertEquals(listOf("1001"), s.map { it.productId })
        assertEquals(10.0, s[0].qty, 0.01)
        assertEquals(3, s[0].orders)
    }
}
