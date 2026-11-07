package uz.imsoft.shared.report

import org.junit.Assert.assertEquals
import org.junit.Test

/** v5.0.3: mijoz kesimidagi statistika va "shu mijozsiz" filtri. */
class StatsClientsTest {
    private fun sale(stamp: Long, store: String, name: String, product: String, qty: Double, sum: Double) = LedgerEntry(
        stamp, EntryKind.SALE, LedgerOwner.tag("Savdo So'm", store, name), product, qty,
        mapOf(Col.SUM to Money2(sum, 0.0), Col.PRICE to Money2(sum / qty, 0.0)),
    )

    private val entries = listOf(
        sale(20261101100000, "S1", "Dadaxon Aka", "Keshu 160", 10.0, 1_600_000.0),
        sale(20261101110000, "S1", "Dadaxon Aka", "Bodom", 5.0, 900_000.0),
        sale(20261102100000, "S2", "Izzattilo Aka", "Keshu 160", 2.0, 340_000.0),
        LedgerEntry(
            20261102120000, EntryKind.PAYMENT, LedgerOwner.tag("To'lov", "S2", "Izzattilo Aka"), "", 0.0,
            mapOf(Col.PAYMENT to Money2(100_000.0, 0.0)),
        ),
    )

    private val cost: (String) -> Double? = { n -> if (n == "Keshu 160") 150_000.0 else null }

    @Test fun perClient() {
        val list = Stats.clients(entries, 20261101000000, 20261103000000, 0.0, cost)
        assertEquals(2, list.size)
        val d = list[0]
        assertEquals("Dadaxon Aka", d.name)
        assertEquals(2_500_000.0, d.sales, 0.01)
        // Keshu: 1 600 000 - 10*150 000 = 100 000; Bodom — kelgan narx yo'q
        assertEquals(100_000.0, d.profit, 0.01)
        assertEquals(2, d.docs)
        assertEquals(1_250_000.0, d.avg, 0.01)
        val i = list[1]
        assertEquals("Izzattilo Aka", i.name)
        assertEquals(340_000.0, i.sales, 0.01)
        assertEquals(100_000.0, i.payments, 0.01)
    }

    @Test fun withoutOneClient() {
        val without = Stats.only(entries, "Dadaxon Aka", Stats.Who.WITHOUT)
        assertEquals(2, without.size)
        val k = Stats.kpi(without, 20261101000000, 20261103000000, 0.0, cost)
        assertEquals(340_000.0, k.sales, 0.01)
        assertEquals(100_000.0, k.payments, 0.01)

        val only = Stats.only(entries, "dadaxon aka", Stats.Who.ONLY)
        assertEquals(2, only.size)
        assertEquals(2_500_000.0, Stats.kpi(only, 20261101000000, 20261103000000, 0.0, cost).sales, 0.01)

        assertEquals(entries.size, Stats.only(entries, "Dadaxon Aka", Stats.Who.ALL).size)
        assertEquals(entries.size, Stats.only(entries, "", Stats.Who.ONLY).size)
    }

    @Test fun byStoreIdAlso() {
        assertEquals(2, Stats.only(entries, "S1", Stats.Who.ONLY).size)
    }
}
