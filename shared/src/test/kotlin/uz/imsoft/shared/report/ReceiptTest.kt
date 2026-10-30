package uz.imsoft.shared.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.imsoft.shared.receipt.EscPosRaster
import uz.imsoft.shared.receipt.NumFmt
import uz.imsoft.shared.receipt.ReceiptBuilder

class ReceiptTest {
    private val labels = ReceiptBuilder.Labels(
        "so'm", { k -> if (k == EntryKind.SALE) "Savdo cheki" else "Hujjat" }, "Mijoz", "Jami", "Chegirma", "To'lanadi", "Qoldiq", "Tovarlar",
        "1C hisobotidan nusxa",
    )

    @Test fun saleReceipt() {
        val e1 = LedgerEntry(20261029161619, EntryKind.SALE, "Savdo So'm", "Pista Dolana (D)", 40.0,
            mapOf(Col.PRICE to Money2(182000.0, 0.0), Col.SUM to Money2(7_280_000.0, 0.0), Col.BALANCE to Money2(9_000_000.0, 0.0)))
        val e2 = LedgerEntry(20261029161619, EntryKind.SALE, "Savdo So'm", "Bodom <M> & Co", 2.5,
            mapOf(Col.PRICE to Money2(120000.0, 0.0), Col.SUM to Money2(300_000.0, 0.0), Col.DISCOUNT to Money2(5000.0, 0.0)))
        val doc = LedgerDocs.build(listOf(e1, e2)).single()
        val r = ReceiptBuilder.fromDoc(doc, "ZAFAR QURUQ MEVALARI", "Dadaxon Aka", labels, footer = listOf("Tel: +998936474646"), staff = "Dokon")
        assertEquals("29.10.2026 16:16", r.dateText)
        assertEquals(2, r.lines.size)
        // Do'kon chekidagidek: "40 x 182 000 = 7 280 000"
        assertEquals("40 x 182 000", r.lines[0].detail)
        assertEquals("7 280 000", r.lines[0].amount)
        assertEquals("40 x 182 000 = 7 280 000", r.lines[0].formula)
        assertEquals("2,5 x 120 000", r.lines[1].detail)
        assertTrue(r.totals.any { it.label == "To'lanadi" && it.value == "7 575 000" && it.bold })
        assertTrue(r.after.any { it.label == "Qoldiq" && it.value == "9 000 000" })
        assertEquals(listOf("Xodim" to "Dokon", "Vaqt" to "16:16:19"), r.info)
        assertEquals("29.10.26", r.date)
        assertEquals("Dadaxon Aka", r.clientName)
        assertEquals("", r.banner)
        val text = ReceiptBuilder.toText(r)
        assertTrue(text.contains("1) Pista Dolana (D)"))
        assertTrue(text.contains("40 x 182 000 = 7 280 000"))
        assertTrue(text.contains("Tel: +998936474646"))
        assertTrue(text.contains("1C hisobotidan nusxa"))
        val html = ReceiptBuilder.toHtml(r)
        assertTrue(html.contains("Bodom &lt;M&gt; &amp; Co"))
    }

    @Test fun returnReceiptHasBanner() {
        val e = LedgerEntry(20261014151618, EntryKind.RETURN, "Qaytarish", "Keshu 160", 1.76,
            mapOf(Col.PRICE to Money2(160000.0, 0.0), Col.RETURN to Money2(281_600.0, 0.0)))
        val r = ReceiptBuilder.fromDoc(LedgerDocs.build(listOf(e)).single(), "ZAFAR", "Izzattilo Aka", labels)
        assertEquals("QAYTIB OLISH !!!", r.banner)
        assertEquals("1,76 x 160 000 = 281 600", r.lines[0].formula)
        assertTrue(r.totals.single().bold)
        assertEquals("Summa", r.totals.single().label)
        assertEquals("281 600", r.totals.single().value)
        assertTrue(ReceiptBuilder.toHtml(r).contains("QAYTIB OLISH !!!"))
    }

    @Test fun numFmt() {
        assertEquals("1 250 000", NumFmt.som(1_250_000.0))
        assertEquals("12,50", NumFmt.usd(12.5))
        assertEquals("2,5", NumFmt.qty(2.5))
        assertEquals("1 000 so'm + 2,00 $", NumFmt.money2(Money2(1000.0, 2.0), "so'm"))
    }

    @Test fun escPosRaster() {
        // 10x3: birinchi qator to'liq qora, ikkinchisi faqat 1-nuqta, uchinchisi oq
        val w = 10; val h = 3
        val px = BooleanArray(w * h) { i -> i < 10 || i == 10 }
        val b = EscPosRaster.encode(w, h, px, cut = false, feedLines = 0)
        // INIT(2) + CENTER(3) + GS v 0 header(8) + 2 bayt*3 qator + LEFT(3)
        assertEquals(2 + 3 + 8 + 6 + 3, b.size)
        assertEquals(0x1D.toByte(), b[5]); assertEquals(0x76.toByte(), b[6]); assertEquals(0x30.toByte(), b[7])
        assertEquals(2.toByte(), b[9]) // bayt/qator
        assertEquals(3.toByte(), b[11]) // qatorlar
        assertEquals(0xFF.toByte(), b[13]); assertEquals(0xC0.toByte(), b[14])
        assertEquals(0x80.toByte(), b[15]); assertEquals(0x00.toByte(), b[16])
        assertEquals(0.toByte(), b[17]); assertEquals(0.toByte(), b[18])
    }

    @Test fun escPosBands() {
        val w = 576; val h = 500
        val b = EscPosRaster.encode(w, h, BooleanArray(w * h), cut = true, feedLines = 4)
        // 3 ta bo'lak (240+240+20)
        assertEquals(2 + 3 + 3 * 8 + 72 * 500 + 3 + 4 + 4, b.size)
    }
}
