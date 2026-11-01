package uz.imsoft.shared

import org.junit.Assert.assertEquals
import org.junit.Test
import uz.imsoft.shared.report.Col
import uz.imsoft.shared.report.EntryKind
import uz.imsoft.shared.report.LedgerEntry
import uz.imsoft.shared.report.Money2
import uz.imsoft.shared.report.PendingMerge
import uz.imsoft.shared.report.Stats

/** v4.0: statistika — kunlik/oylik yig'indi, foyda (oxirgi kelgan narx bilan), top tovarlar, narx o'zgarishi. */
class StatsTest {
    private fun sale(stamp: Long, p: String, q: Double, price: Double) = LedgerEntry(stamp, EntryKind.SALE, "S", p, q, mapOf(Col.PRICE to Money2(price), Col.SUM to Money2(q * price)))
    private fun pay(stamp: Long, v: Double) = LedgerEntry(stamp, EntryKind.PAYMENT, "P", "", 0.0, mapOf(Col.PAYMENT to Money2(v)))
    private val now = PendingMerge.millisOf(20261102150000L)
    private val entries = listOf(
        sale(20261102100000L, "Pista", 2.0, 100_000.0), sale(20261102100000L, "Bodom", 1.0, 50_000.0), pay(20261102120000L, 70_000.0),
        sale(20261101090000L, "Pista", 1.0, 90_000.0), sale(20261001090000L, "Pista", 1.0, 80_000.0),
        LedgerEntry(20261102130000L, EntryKind.SALE, "S", "Dollarli", 1.0, mapOf(Col.SUM to Money2(usd = 2.0))),
    )

    @Test fun dailySeries() {
        val s = Stats.series(entries, Stats.Period.DAY, now, 12_000.0) { if (it == "Pista") 80_000.0 else null }
        assertEquals(14, s.size)
        assertEquals("2", s.last().label)
        assertEquals(274_000.0, s.last().sales, 0.0) // 200k + 50k + 2$ x 12 000
        assertEquals(70_000.0, s.last().payments, 0.0)
        assertEquals(40_000.0, s.last().profit, 0.0) // Pista: 200k - 2 x 80k (Bodom kelgan narxi noma'lum — hisoblanmaydi)
        assertEquals(90_000.0, s[s.size - 2].sales, 0.0)
    }

    @Test fun monthlyAndYearly() {
        val m = Stats.series(entries, Stats.Period.MONTH, now, 0.0)
        assertEquals(12, m.size)
        assertEquals("noy", m.last().label)
        assertEquals(340_000.0, m.last().sales, 0.0)
        assertEquals(80_000.0, m[m.size - 2].sales, 0.0)
        assertEquals("2026", Stats.series(entries, Stats.Period.YEAR, now, 0.0).last().label)
    }

    @Test fun topAndPrices() {
        val top = Stats.top(entries, 20261101000000L, 0.0)
        assertEquals("Pista", top.first().product)
        assertEquals(3.0, top.first().qty, 0.0)
        val ch = Stats.priceChanges(entries, 0L, 0.0)
        assertEquals(2, ch.size) // 80k -> 90k -> 100k
        assertEquals(90_000.0 to 100_000.0, ch.first().second)
        val t = Stats.totals(entries, 20261102000000L, 20261103000000L, 0.0)
        assertEquals(2, t.docs) // 10:00 (ikki qator — bitta hujjat) va 13:00
    }
}
