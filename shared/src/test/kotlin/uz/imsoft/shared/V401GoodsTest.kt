package uz.imsoft.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.imsoft.shared.onec.OneCDoc
import uz.imsoft.shared.onec.OneCLedger
import uz.imsoft.shared.onec.OneCLine
import uz.imsoft.shared.onec.OneCSales
import uz.imsoft.shared.report.Col
import uz.imsoft.shared.report.EntryKind
import uz.imsoft.shared.report.LedgerEntry
import uz.imsoft.shared.report.Money2
import uz.imsoft.shared.report.PendingMerge

/** v4.0.1: kartochkadagi tovarsiz savdo 1C hujjatidan tovarlari bilan almashadi (2-ilova tarixi va Narxlar). */
class V401GoodsTest {
    private val now = PendingMerge.millisOf(20261102080000L)

    private fun card(stamp: Long, kind: EntryKind, sum: Double) = LedgerEntry(
        stamp, kind, "Chakana savdo 32 211", "", 0.0,
        mapOf((if (kind == EntryKind.PAYMENT) Col.PAYMENT else Col.SUM) to Money2(som = sum)),
    )

    private fun doc(no: String, stamp: Long, party: String, kind: String = OneCDoc.RETAIL) = OneCDoc(
        "$kind:$no:$stamp", kind, no, stamp, party, "", 6_650_000.0, 0.0, "",
        listOf(OneCLine("Pista", "001", 20.0, 200_000.0, 4_000_000.0), OneCLine("Bodom", "002", 10.0, 265_000.0, 2_650_000.0)),
    )

    @Test fun cardSaleReplacedByDocLines() {
        val entries = listOf(
            card(20261101153928L, EntryKind.SALE, 6_650_000.0),
            card(20261101160000L, EntryKind.PAYMENT, 5_000_000.0),
            card(20250101120000L, EntryKind.SALE, 100.0), // kompyuter qamramagan vaqt — qoladi
        )
        val sales = OneCSales(20261003000000L, 20261102000000L, now, listOf(doc("32211", 20261101153928L, "Dadaxon Aka (TRZ)"), doc("32212", 20261101170000L, "Boshqa")))
        val out = OneCLedger.withGoods(entries, sales, now) { it.party == "Dadaxon Aka (TRZ)" }
        assertEquals(listOf("", "Pista", "Bodom", ""), out.map { it.product })
        assertEquals(listOf(20250101120000L, 20261101153928L, 20261101153928L, 20261101160000L), out.map { it.stamp })
        assertEquals(6_650_000.0, out.filter { it.kind == EntryKind.SALE && it.stamp > 20260000000000L }.sumOf { it.v(Col.SUM).som }, 0.01)
        assertTrue(out.any { it.kind == EntryKind.PAYMENT })
    }

    @Test fun noSalesKeepsCard() {
        val entries = listOf(card(20261101153928L, EntryKind.SALE, 1.0))
        assertEquals(entries, OneCLedger.withGoods(entries, null, now) { true })
        assertEquals(entries, OneCLedger.withGoods(entries, OneCSales(0, 0, now, listOf(doc("1", 20261101153928L, "x"))), now) { true })
    }

    @Test fun lastMinutesStayFromCard() {
        // Kompyuter 07:58 da yubordi: 07:55 dan keyingi kartochka yozuvi qoladi (1C'da hali bo'lmasligi mumkin)
        val sent = PendingMerge.millisOf(20261102075800L)
        val entries = listOf(card(20261102075700L, EntryKind.SALE, 1.0))
        val out = OneCLedger.withGoods(entries, OneCSales(20261101000000L, 20261102000000L, sent, emptyList<OneCDoc>() + doc("9", 20261101100000L, "x")), now) { false }
        assertEquals(entries, out)
    }

    // ---------------- ko'proq statistika ----------------

    private fun sale(stamp: Long, product: String, qty: Double, sum: Double, client: String = "") = LedgerEntry(
        stamp, EntryKind.SALE, if (client.isEmpty()) "1C №1" else uz.imsoft.shared.report.LedgerOwner.tag("1C №1", "s", client), product, qty,
        mapOf(Col.SUM to Money2(som = sum)),
    )

    @Test fun kpiCountsDocsClientsAvg() {
        val e = listOf(
            sale(20261101100000L, "Pista", 2.0, 400.0, "A"), sale(20261101100000L, "Bodom", 1.0, 100.0, "A"),
            sale(20261101120000L, "Pista", 1.0, 200.0, "B"),
        )
        val k = uz.imsoft.shared.report.Stats.kpi(e, 20261101000000L, 20261102000000L, 0.0) { if (it == "Pista") 150.0 else null }
        assertEquals(700.0, k.sales, 0.01)
        assertEquals(2, k.docs)
        assertEquals(350.0, k.avg, 0.01)
        assertEquals(2, k.clients)
        assertTrue(k.hasProfit)
        assertEquals(400.0 - 300.0 + 200.0 - 150.0, k.profit, 0.01)
    }

    @Test fun fastRanksByDaysAndTrend() {
        val e = listOf(
            sale(20261020100000L, "Pista", 1.0, 100.0), // oldingi davr
            sale(20261028100000L, "Pista", 2.0, 200.0), sale(20261029100000L, "Pista", 2.0, 200.0),
            sale(20261029110000L, "Bodom", 10.0, 5000.0),
        )
        val f = uz.imsoft.shared.report.Stats.fast(e, 20261027000000L, 20261103000000L, 0.0)
        assertEquals(listOf("Pista", "Bodom"), f.map { it.product })
        assertEquals(2, f[0].days)
        assertEquals(300.0, f[0].trend ?: 0.0, 0.01)
        assertEquals(null, f[1].trend)
    }

    @Test fun sharesWeekdaysHoursIdle() {
        val e = listOf(
            sale(20261102100000L, "Pista", 1.0, 600.0, "A"), // dushanba
            sale(20261101150000L, "Bodom", 1.0, 300.0, "B"), // yakshanba
            sale(20261101153000L, "Yong'oq", 1.0, 100.0, "C"),
            sale(20260901100000L, "Mayiz", 1.0, 10.0), sale(20260905100000L, "Mayiz", 1.0, 10.0),
        )
        val sh = uz.imsoft.shared.report.Stats.byClient(e, 20261101000000L, 20261103000000L, 0.0, limit = 2)
        assertEquals(listOf("A", "B", ""), sh.map { it.label })
        assertEquals(100.0, sh[2].value, 0.01)
        val w = uz.imsoft.shared.report.Stats.weekdays(e, 20261101000000L, 20261103000000L, 0.0)
        assertEquals(600.0, w[0], 0.01)
        assertEquals(400.0, w[6], 0.01)
        val h = uz.imsoft.shared.report.Stats.hours(e, 20261101000000L, 20261103000000L, 0.0)
        assertEquals(400.0, h[15], 0.01)
        val idle = uz.imsoft.shared.report.Stats.idle(e, now, 14)
        assertEquals(listOf("Mayiz"), idle.map { it.product })
        assertEquals(2, idle[0].count)
    }
}
