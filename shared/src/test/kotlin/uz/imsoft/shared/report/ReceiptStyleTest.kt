package uz.imsoft.shared.report

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.imsoft.shared.receipt.Dither
import uz.imsoft.shared.receipt.Receipt
import uz.imsoft.shared.receipt.ReceiptBuilder
import uz.imsoft.shared.receipt.ReceiptStyle

class ReceiptStyleTest {
    private fun sample(): Receipt = Receipt(
        "ZAFAR", "Savdo cheki", "Mijoz: Dadaxon Aka", "29.10.2026 16:16",
        listOf(Receipt.Line("Pista", "40 x 182 000", "7 280 000")),
        listOf(Receipt.Total("Summa", "7 280 000", bold = true)), listOf("izoh"),
        clientLabel = "Mijoz", clientName = "Dadaxon Aka",
        info = listOf("Xodim" to "Dokon", "Chek №" to "B-1", "Vaqt" to "16:16:19"), date = "29.10.26",
        before = listOf(Receipt.Total("Qarz", "1 000")), after = listOf(Receipt.Total("Qarz", "8 280 000")),
        footer = listOf("Tel: +998 93 647 46 46"),
        infoKeys = listOf(Receipt.KEY_STAFF, Receipt.KEY_NUMBER, Receipt.KEY_TIME),
    )

    @Test fun roundTrip() {
        val s = ReceiptStyle(
            paper = 58, arial = true, bold = true, headerSerif = false, text = 115, header = 140, logo = false, logoWidth = 45,
            staff = false, number = false, time = true, date = false, client = false, before = false, lineNo = false, frame = false,
            after = false, footer = false, notes = false, sub = "Toshkent, Chorsu\nMo'ljal: bozor", thanks = "Rahmat!", dark = 180, feed = 12,
        )
        assertEquals(s, ReceiptStyle.decode(s.encode()))
        assertEquals(384, s.width)
        assertEquals(576, ReceiptStyle.STANDARD.width)
        for (p in listOf(ReceiptStyle.STANDARD, ReceiptStyle.ONEC, ReceiptStyle.COMPACT)) assertEquals(p, ReceiptStyle.decode(p.encode()))
    }

    @Test fun decodeIsTolerant() {
        assertEquals(ReceiptStyle.STANDARD, ReceiptStyle.decode(""))
        assertEquals(ReceiptStyle.STANDARD, ReceiptStyle.decode(null))
        assertEquals(ReceiptStyle.STANDARD, ReceiptStyle.decode("{buzuq"))
        assertEquals(ReceiptStyle.STANDARD, ReceiptStyle.decode("[1,2]"))
        // Chegaradan tashqari qiymatlar keltiriladi, noma'lum kalitlar e'tiborsiz
        val s = ReceiptStyle.decode("""{"p":100,"t":999,"h":1,"lw":0,"dk":5,"fd":500,"zz":1,"sub":"a\nb\nc\nd\ne\nf"}""")
        assertEquals(80, s.paper)
        assertEquals(150, s.text)
        assertEquals(50, s.header)
        assertEquals(20, s.logoWidth)
        assertEquals(100, s.dark)
        assertEquals(40, s.feed)
        assertEquals("a\nb\nc\nd", s.sub)
        // Eski (bo'sh maydonli) uslub — standart qiymatlar
        assertEquals(ReceiptStyle.STANDARD.copy(paper = 58), ReceiptStyle.decode("""{"p":58}"""))
    }

    @Test fun applyHidesParts() {
        val r = ReceiptStyle(staff = false, number = true, time = false, date = false, before = false, after = false, footer = false, notes = false,
            sub = " Chorsu bozori \n\n 2-qator ", thanks = " Rahmat! ").apply(sample())
        assertEquals(listOf("Chek №" to "B-1"), r.info)
        assertEquals(listOf(Receipt.KEY_NUMBER), r.infoKeys)
        assertEquals("", r.date)
        assertTrue(r.before.isEmpty() && r.after.isEmpty() && r.footer.isEmpty() && r.notes.isEmpty())
        assertEquals(listOf("Chorsu bozori", "2-qator"), r.subheader)
        assertEquals("Rahmat!", r.thanks)
        assertEquals("Dadaxon Aka", r.clientName)
        val noClient = ReceiptStyle(client = false).apply(sample())
        assertEquals("", noClient.clientLabel)
        assertEquals("", noClient.client)
        // Ikki marta qo'llash — natija o'zgarmaydi
        val st = ReceiptStyle(staff = false, sub = "x")
        assertEquals(st.apply(sample()), st.apply(st.apply(sample())))
        // Standart — hech narsa yo'qolmaydi
        val all = ReceiptStyle.STANDARD.apply(sample())
        assertEquals(sample().info, all.info)
        assertEquals(sample().footer, all.footer)
    }

    @Test fun textAndHtmlCarrySubAndThanks() {
        val r = ReceiptStyle(sub = "Chorsu <bozori>", thanks = "Rahmat & omad").apply(sample())
        val t = ReceiptBuilder.toText(r)
        assertTrue(t.startsWith("ZAFAR\nChorsu <bozori>\n"))
        assertTrue(t.contains("Rahmat & omad"))
        val h = ReceiptBuilder.toHtml(r)
        assertTrue(h.contains("Chorsu &lt;bozori&gt;"))
        assertTrue(h.contains("Rahmat &amp; omad"))
    }

    @Test fun builderMarksInfoKeys() {
        val e = LedgerEntry(20261029161619, EntryKind.SALE, "Savdo So'm", "Pista", 1.0,
            mapOf(Col.PRICE to Money2(1000.0, 0.0), Col.SUM to Money2(1000.0, 0.0)))
        val labels = ReceiptBuilder.Labels("so'm", { "Savdo" }, "Mijoz", "Jami", "Chegirma", "To'lanadi", "Qoldiq", "Tovarlar", "izoh")
        val r = ReceiptBuilder.fromDoc(LedgerDocs.build(listOf(e)).single(), "Z", "A", labels, staff = "Dokon")
        assertEquals(listOf(Receipt.KEY_STAFF, Receipt.KEY_TIME), r.infoKeys)
        assertEquals(listOf("Vaqt" to "16:16:19"), ReceiptStyle(staff = false).apply(r).info)
    }

    @Test fun ditherKeepsGray() {
        // Oq — oq, qora — qora, 50% kulrang — taxminan yarmi qora
        val w = 40; val h = 40
        assertFalse(Dither.floyd(w, h, IntArray(w * h) { 255 }).any { it })
        assertTrue(Dither.floyd(w, h, IntArray(w * h) { 0 }).all { it })
        val half = Dither.floyd(w, h, IntArray(w * h) { 128 }).count { it }
        assertTrue("qora: $half", half in 700..900)
        assertArrayEquals(booleanArrayOf(true, false), Dither.floyd(2, 1, intArrayOf(10, 250)))
    }

    @Test fun snapshotCarriesStyleAndLogo() {
        val st = ReceiptStyle.ONEC.copy(sub = "Chorsu").encode()
        val s = ClientSnapshot(1L, "c", "C", 12600.0, null, emptyList(), emptyList(), emptyList(), 0L,
            shopName = "Z", receiptStyle = st, receiptLogo = "iVBORw0KGgo=")
        val back = SnapshotCodec.unpack(SnapshotCodec.pack(s))
        assertEquals(st, back.receiptStyle)
        assertEquals("iVBORw0KGgo=", back.receiptLogo)
        // Eski 1-ilova ma'lumoti (maydonlarsiz) — standart
        val old = SnapshotCodec.fromJson("""{"v":1,"at":1,"cid":"c","cn":"C","rate":1,"p":[],"l":[],"cov":[],"pat":0}""")
        assertEquals("", old.receiptStyle)
        assertEquals(ReceiptStyle.STANDARD, ReceiptStyle.decode(old.receiptStyle))
    }
}
