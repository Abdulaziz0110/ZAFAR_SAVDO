package uz.imsoft.shared.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportImportTest {
    private fun res(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("cards/$name")!!.use { it.readBytes() }

    private fun check(r: CardReport) {
        assertEquals("Dadaxon Aka (TRZ)", r.clientName)
        assertEquals(20221001000000L, r.from)
        assertEquals(20261029235959L, r.to)
        assertEquals(2, r.subtotalRows)
        assertEquals(0, r.skippedRows)
        assertTrue(r.hasUsdRows)
        assertEquals(8, r.entries.size)
        val e0 = r.entries[0]
        assertEquals(20261027104117L, e0.stamp)
        assertEquals(EntryKind.SALE, e0.kind)
        assertEquals("Qulupnay (Sublimit)", e0.product)
        assertEquals(2.5, e0.qty, 1e-9)
        assertEquals(Money2(200000.0, 0.0), e0.v(Col.PRICE))
        assertEquals(Money2(500000.0, 0.0), e0.v(Col.SUM))
        val ret = r.entries[3]
        assertEquals(EntryKind.RETURN, ret.kind)
        assertEquals(142000.0, ret.v(Col.RETURN).som, 0.0)
        val pay = r.entries[4]
        assertEquals(EntryKind.PAYMENT, pay.kind)
        assertEquals(1_000_000.0, pay.v(Col.PAYMENT).som, 0.0)
        val disc = r.entries[5]
        assertEquals(20261028090236L, disc.stamp)
        assertEquals(5000.0, disc.v(Col.DISCOUNT).som, 0.0)
        val usd = r.entries[6]
        assertEquals("Pista Akbariy (AK)", usd.product)
        assertEquals(40.0, usd.qty, 0.0)
        assertEquals(Money2(0.0, 15.5), usd.v(Col.PRICE))
        assertEquals(Money2(0.0, 620.0), usd.v(Col.SUM))
        val payUsd = r.entries[7]
        assertEquals(EntryKind.PAYMENT, payUsd.kind)
        assertEquals(Money2(0.0, 100.0), payUsd.v(Col.PAYMENT))
        assertTrue(r.columns.contains("Qoldiq"))
    }

    @Test fun html_fromOneC() {
        val g = TableReader.read(res("card.htm"), "card.htm")
        check(CardParser.parse(g))
    }

    @Test fun xls_biff8_withMerges() {
        val g = TableReader.read(res("card.xls"), "card.xls")
        check(CardParser.parse(g))
    }

    @Test fun xls_bigSstAcrossContinue() {
        val g = TableReader.read(res("big.xls"), "big.xls")
        assertEquals(3000, g.rows.size)
        for (i in listOf(0, 1, 2, 777, 1500, 2998, 2999)) {
            val expect = if (i % 2 == 1) ("Ёнғоқ мағзи №$i — " + "x".repeat(i % 37)) else ("Pista Ahmadiy $i " + "y".repeat(i % 41))
            assertEquals("row $i", expect, g[i, 0]!!.text)
            assertEquals(i * 1.5, g[i, 1]!!.num!!, 1e-9)
        }
    }

    @Test fun text_tsv() {
        val tsv = "01.10.2026 dan 05.10.2026 : Ali\n\nSana\tXarakat\tIzoxlar\tSoni\tNarxi\tSumma\tTo'lov\n" +
            "02.10.2026 10:00\tSavdo So'm\tBodom\t3\t10 000\t30 000\t\n" +
            "03.10.2026 11:00\tTo'lov So'm\t\t\t\t\t25 000\n" +
            "Jami\t\t\t\t\t30 000\t25 000\n"
        val r = CardParser.parse(TableReader.read(tsv.toByteArray(), "x.txt"))
        assertEquals("Ali", r.clientName)
        assertEquals(2, r.entries.size)
        assertEquals(30000.0, r.entries[0].v(Col.SUM).som, 0.0)
        assertEquals(EntryKind.PAYMENT, r.entries[1].kind)
        assertEquals(25000.0, r.entries[1].v(Col.PAYMENT).som, 0.0)
        assertEquals(1, r.skippedRows) // "Jami" qatori
    }

    @Test fun notACard() {
        try {
            CardParser.parse(TableReader.read("<table><tr><td>a</td><td>b</td></tr></table>".toByteArray()))
            throw AssertionError()
        } catch (e: CardParser.NotACard) { }
    }

    @Test fun unsupportedFormats() {
        try { TableReader.read("PK\u0003\u0004xxxx".toByteArray(), "a.xlsx"); throw AssertionError() } catch (e: TableReader.UnsupportedFormat) { }
        try { TableReader.read("MOXCEL....".toByteArray(), "a.mxl"); throw AssertionError() } catch (e: TableReader.UnsupportedFormat) { }
    }

    @Test fun stamp_andNumbers() {
        assertEquals(20261028090236L, Stamp.parse("28.10.2026 9:02:36"))
        assertEquals(20261027000000L, Stamp.parse("27.10.2026 0:00:00"))
        assertEquals(20260105000000L, Stamp.parse("5.1.26"))
        assertNull(Stamp.parse("32.10.2026"))
        assertEquals(20261029000000L, Stamp.addDays(20261028153000L, 1))
        assertEquals(20270101000000L, Stamp.addDays(20261231000000L, 1))
        assertEquals(Stamp.of(2026, 10, 27, 12, 0, 0), Stamp.fromExcel(46322.5))
        assertEquals(3740000.0, Num.parse("3 740 000")!!, 0.0)
        assertEquals(142.5, Num.parse("142,5")!!, 0.0)
        assertEquals(-1250.5, Num.parse("-1 250,50")!!, 0.0)
        assertEquals(11130000.0, Num.parse("11,130,000.00")!!, 0.0)
        assertEquals(1250000.5, Num.parse("1.250.000,50")!!, 0.0)
        assertNull(Num.parse("abc"))
        assertNull(Num.parse(""))
    }

    @Test fun snapshot_roundTripAndMerge() {
        val r = CardParser.parse(TableReader.read(res("card.htm"), "card.htm"))
        val m1 = LedgerMerge.merge(emptyList(), emptyList(), r, 1000L, "file")
        assertEquals(8, m1.added)
        // Xuddi shu fayl qayta import qilinsa — dublikat bo'lmaydi
        val m2 = LedgerMerge.merge(m1.entries, m1.coverage, r, 2000L, "file")
        assertEquals(8, m2.entries.size)
        assertEquals(8, m2.same)
        assertEquals(0, m2.added)
        assertEquals(1, m2.coverage.size)
        val snap = ClientSnapshot(
            123L, "S9", "Dadaxon Aka (TRZ)", 12650.0, DebtInfo(11130000.0, 0.0, 99L, "server"),
            listOf(PriceItem("T1", "B Mayiz 134", "Mayiz", 71000.0, 5.61, 70000.0, 5.53)), m2.entries, m2.coverage, 50L,
        )
        val back = SnapshotCodec.unpack(SnapshotCodec.pack(snap))
        assertEquals(snap, back)
        assertNotNull(back.debt)
        // v2.6: chek osti (telefonlar) ham boradi
        val withFoot = snap.copy(shopName = "ZAFAR QURUQ MEVALARI", shopFooter = "Tel: +998936474646\nTel: +998907788510")
        assertEquals(withFoot, SnapshotCodec.unpack(SnapshotCodec.pack(withFoot)))
    }

    @Test fun merge_replacesOnlyReportPeriod() {
        fun e(st: Long, p: String) = LedgerEntry(st, EntryKind.SALE, "Savdo", p, 1.0, mapOf(Col.SUM to Money2(1.0, 0.0)))
        val old = listOf(e(20261001100000, "a"), e(20261010100000, "b"), e(20261020100000, "c"))
        val rep = CardReport(null, 20261005000000, 20261015235959, listOf(e(20261011100000, "b2")), emptyList(), emptyList(), 0, 0, false)
        val m = LedgerMerge.merge(old, emptyList(), rep, 1L, "file")
        assertEquals(listOf("a", "b2", "c"), m.entries.map { it.product })
        assertEquals(1, m.removed)
    }

    /** 2026-10-29 da kelgan haqiqiy fayl tuzilmasi (.xlsx, 20 ustun, qoldiq qatorlari). */
    @Test fun xlsx_realStructure() {
        val r = CardParser.parse(TableReader.read(res("card_real_like.xlsx"), "Лист1.xlsx"))
        assertEquals("Test Mijoz (TRZ)", r.clientName)
        assertEquals(20261001000000L, r.from)
        assertEquals(20261003235959L, r.to)
        assertEquals(4, r.entries.size)
        assertEquals(listOf(EntryKind.SALE, EntryKind.PAYMENT, EntryKind.RETURN, EntryKind.SALE), r.entries.map { it.kind })
        assertEquals(Money2(1_700_000.0, 0.0), r.entries[0].v(Col.SUM))
        assertEquals(5.0, r.entries[0].qty, 0.0)
        assertEquals("Мижоз накд сумда тулов килди", r.entries[1].product)
        assertEquals(155_000.0, r.entries[1].v(Col.PAYMENT).som, 0.0)
        assertEquals(320_000.0, r.entries[2].v(Col.RETURN).som, 0.0)
        assertEquals(2.5, r.entries[3].qty, 0.0)
        // Yetkazib beruvchi / qarz olish ustunlari mijoz ustunlariga aralashmaydi
        assertEquals(2_700_000.0, r.entries[0].v(Col.BALANCE).som, 0.0)
        assertTrue(r.columns.contains("Chegirma (Yuk beruvchi)"))
        assertEquals(20261001000000L to Money2(1_000_000.0, 0.0), r.opening)
        assertEquals(20261002235959L to Money2(2_517_500.0, 0.0), r.closing)
        assertEquals(2, r.subtotalRows - 3) // 2 kunlik jami + boshlang'ich/aylanma/yakuniy qatorlar
        assertEquals(0, r.skippedRows)
        assertTrue(r.warnings.isEmpty())
    }

    @Test fun xlsx_refParsing() {
        assertEquals(0 to 0, XlsxReader.ref("A1"))
        assertEquals(11 to 27, XlsxReader.ref("AB12"))
        assertEquals(983 to 29, XlsxReader.ref("AD984"))
    }
}
