package uz.imsoft.shared.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClientStatsTest {
    private val entries = CardParser.parse(
        TableReader.read(javaClass.classLoader!!.getResourceAsStream("cards/card.htm")!!.use { it.readBytes() }, "card.htm"),
    ).entries

    @Test fun totals_allPeriod() {
        val t = ClientStats.totals(entries)
        assertEquals(Money2(5_625_000.0, 620.0), t.purchased)
        assertEquals(Money2(142_000.0, 0.0), t.returned)
        assertEquals(Money2(5_000.0, 0.0), t.discount)
        assertEquals(Money2(1_000_000.0, 100.0), t.paid)
        assertEquals(5, t.purchaseCount)
        assertEquals(2, t.paymentCount)
        assertEquals(Money2(5_625_000.0 - 142_000 - 5_000 - 1_000_000, 520.0), t.net)
    }

    @Test fun totals_periodAndDays() {
        val (a, b) = ClientStats.Period.today(20261028120000)
        val t = ClientStats.totals(entries, a, b)
        assertEquals(Money2(675_000.0, 620.0), t.purchased)
        val days = ClientStats.byDay(entries)
        assertEquals(listOf(20261028000000L, 20261027000000L), days.map { it.day })
        assertEquals(3, days[0].entries.size)
        assertEquals(5, days[1].entries.size)
    }

    @Test fun productHistory_andLast() {
        val h = ClientStats.productHistory(entries, LedgerMerge.nameKey("B Mayiz 134"))
        assertEquals(2, h.size)
        assertEquals(true, h[0].returned) // qaytarish keyinroq bo'lgan
        assertEquals(10.0, h[1].qty, 0.0)
        val last = ClientStats.lastPurchases(entries)
        assertEquals(Money2(0.0, 15.5), last[LedgerMerge.nameKey("Pista Akbariy (AK)")]!!.price)
        assertNull(ClientStats.lastBalance(entries))
    }

    @Test fun periods() {
        assertEquals(20261001000000L to 20261028235959L, ClientStats.Period.thisMonth(20261028101010))
        assertEquals(20260901000000L to 20260930235959L, ClientStats.Period.lastMonth(20261028101010))
        assertEquals(20251201000000L to 20251231235959L, ClientStats.Period.lastMonth(20260115000000))
        assertEquals(20261026000000L to 20261028235959L, ClientStats.Period.thisWeek(20261028101010, 3))
        assertEquals(20261022000000L to 20261028235959L, ClientStats.Period.lastDays(20261028101010, 7))
    }
}
