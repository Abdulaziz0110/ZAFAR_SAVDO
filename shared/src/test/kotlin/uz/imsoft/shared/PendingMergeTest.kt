package uz.imsoft.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.imsoft.shared.report.Col
import uz.imsoft.shared.report.EntryKind
import uz.imsoft.shared.report.LedgerEntry
import uz.imsoft.shared.report.LedgerOwner
import uz.imsoft.shared.report.Money2
import uz.imsoft.shared.report.PendingMerge
import uz.imsoft.shared.report.PendingMerge.Line
import uz.imsoft.shared.report.PendingMerge.Local

/** v4.0: telefondagi savdo 1C'dan kelguncha ro'yxatda, kelgach — almashadi (dublikat yo'q). */
class PendingMergeTest {
    private val t0 = PendingMerge.millisOf(20261101100000L)

    private fun c1(stamp: Long, name: String, sum: Double, kind: EntryKind = EntryKind.SALE) =
        LedgerEntry(stamp, kind, LedgerOwner.tag("Реализация мобил", "s1", name), "Pista", 1.0,
            if (kind == EntryKind.PAYMENT) mapOf(Col.PAYMENT to Money2(sum)) else mapOf(Col.SUM to Money2(sum)))

    private val sale = Local("o1", t0, EntryKind.SALE, "s1", "Ali  Aka", listOf(Line("Pista", 2.0, 50_000.0), Line("Bodom", 1.0, 20_000.0)), 120_000.0)

    @Test fun stampRoundTrip() {
        assertEquals(20261101100000L, PendingMerge.stampOf(t0))
    }

    @Test fun pendingShownUntil1CHasIt() {
        val now = t0 + 60_000
        val merged = PendingMerge.merge(emptyList(), listOf(sale), "1C'ga ketmoqda", now)
        assertEquals(2, merged.size)
        assertTrue(merged.all { LedgerOwner.plain(it.kindText) == "1C'ga ketmoqda" })
        assertEquals(100_000.0, merged.first { it.product == "Pista" }.v(Col.SUM).som, 0.0)
        // 1C'da ikki qator (bitta hujjat) — 5 daqiqadan keyin, nomi boshqacha yozilgan (bo'sh joy/katta harf)
        val inC = listOf(c1(20261101100500L, "ali aka", 100_000.0), c1(20261101100500L, "ali aka", 20_000.0))
        assertEquals(inC, PendingMerge.merge(inC, listOf(sale), "x", now))
    }

    @Test fun notMatchedWhenSumDiffersOrTooEarly() {
        val now = t0 + 60_000
        assertEquals(1, PendingMerge.unmatched(listOf(c1(20261101100500L, "Ali Aka", 90_000.0)), listOf(sale), now).size)
        assertEquals(1, PendingMerge.unmatched(listOf(c1(20261101090000L, "Ali Aka", 120_000.0)), listOf(sale), now).size)
        // Bitta 1C hujjati ikkita bir xil savdoga mos kelmaydi
        val twice = listOf(sale, sale.copy(key = "o2", at = t0 + 120_000))
        assertEquals(1, PendingMerge.unmatched(listOf(c1(20261101101000L, "Ali Aka", 120_000.0)), twice, now).size)
    }

    @Test fun paymentAndExpiry() {
        val pay = Local("p1", t0, EntryKind.PAYMENT, "s1", "Ali Aka", emptyList(), 300_000.0)
        assertEquals(0, PendingMerge.unmatched(listOf(c1(20261101103000L, "Ali Aka", 300_000.0, EntryKind.PAYMENT)), listOf(pay), t0).size)
        // 3 kundan eski telefon yozuvi ko'rsatilmaydi (1C'da yo'q bo'lsa ham — "Yuborilganlar"da qoladi)
        assertEquals(0, PendingMerge.unmatched(emptyList(), listOf(pay), t0 + 4 * 86_400_000L).size)
    }
}
