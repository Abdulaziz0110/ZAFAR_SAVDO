package uz.imsoft.shared.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.imsoft.shared.onec.OneCDoc
import uz.imsoft.shared.onec.OneCLine
import uz.imsoft.shared.report.Money2

/** v5.3.13: kirim cheki qarzsiz, to'lov chekida oldingi va qolgan qarzimiz. */
class SupplierChekTest {
    private fun doc(id: String, kind: String, stamp: Long, som: Double, lines: List<OneCLine> = emptyList()) =
        OneCDoc(id, kind, id, stamp, "Ali aka", "7", som, 0.0, "", lines)

    private val r1 = doc("1", OneCDoc.RECEIPT, 20261101100000, 5_000_000.0, listOf(OneCLine("Bodom", "12", 10.0, 500_000.0, 5_000_000.0)))
    private val p1 = doc("2", OneCDoc.SUPPLIER_PAY, 20261105120000, 2_000_000.0)
    private val r2 = doc("3", OneCDoc.RECEIPT, 20261110090000, 1_000_000.0)
    private val p2 = doc("4", OneCDoc.SUPPLIER_PAY, 20261115150000, 500_000.0)
    private val docs = listOf(p2, r1, r2, p1)

    @Test fun oweBeforePayment() {
        // hozir qarzimiz 3 500 000 = 5 000 000 - 2 000 000 + 1 000 000 - 500 000
        val now = Money2(3_500_000.0, 0.0)
        assertEquals(5_000_000.0, SupplierChek.oweBefore(docs, now, p1).som, 0.001)
        assertEquals(4_000_000.0, SupplierChek.oweBefore(docs, now, p2).som, 0.001)
        assertEquals(0.0, SupplierChek.oweBefore(docs, now, r1).som, 0.001)
    }

    @Test fun paymentChekShowsDebts() {
        val c = SupplierChek.of(p1, "TO'LOV", Chek.Labels.LATIN, "Dokon", "+998", Money2(5_000_000.0, 0.0))
        assertTrue(c.showDebt)
        assertFalse(c.showPay)
        assertEquals("5 000 000", c.debtSom)
        assertEquals("3 000 000", c.endSom)
        assertEquals("2 000 000", c.sumSom)
        assertTrue(c.text().contains("TO'LOV"))
        assertTrue(c.html().contains(Chek.Labels.LATIN.endSom))
    }

    @Test fun receiptChekHasNoDebt() {
        val c = SupplierChek.of(r1, "KIRIM", Chek.Labels.LATIN, "Dokon", "+998", null)
        assertFalse(c.showDebt)
        assertEquals("1 || Bodom", c.lines[0].title)
        assertEquals("10 х 500 000 = 5 000 000", c.lines[0].calc)
        assertEquals("5 000 000", c.sumSom)
        assertFalse(c.text().contains(Chek.Labels.LATIN.debtSom))
        assertFalse(c.text().contains(Chek.Labels.LATIN.cash))
        assertFalse(c.html().contains(Chek.Labels.LATIN.endSom))
    }

    @Test fun supports() {
        assertTrue(SupplierChek.supports(OneCDoc.RECEIPT))
        assertTrue(SupplierChek.supports(OneCDoc.SUPPLIER_PAY))
        assertFalse(SupplierChek.supports(OneCDoc.SALE))
    }
}
