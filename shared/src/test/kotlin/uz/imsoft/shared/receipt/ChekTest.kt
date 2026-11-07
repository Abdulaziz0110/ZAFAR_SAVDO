package uz.imsoft.shared.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.imsoft.shared.report.AppSale

/** v5.0.2: chek 1C "Чек1" maketidagidek. */
class ChekTest {
    private val sale = AppSale(
        id = "o1", at = 0L, stamp = 20261014151618, isReturn = false, usd = false,
        lines = listOf(AppSale.Line("Keshu 160", 1.76, 160_000.0), AppSale.Line("Bodom Qoqon Qilich", 4.37, 185_000.0)),
        discount = 10_000.0, code = "30718", debtBefore = 1_500_000.0, paid = 1_000_000.0, cash = 700_000.0, card = 300_000.0,
    )

    @Test fun saleChek() {
        val c = Chek.ofSale(sale, sale.code, "Izzattilo Aka Karvin", "Dokon", "+998936474646")
        assertEquals("15:16:18", c.time)
        assertEquals("14.10.26", c.date)
        assertEquals("30718", c.number)
        assertEquals(2, c.lines.size)
        assertEquals("1 || Keshu 160", c.lines[0].title)
        assertEquals("1,76 х 160 000 = 281 600", c.lines[0].calc)
        assertEquals("1 090 050", c.sumSom)
        assertEquals("10 000", c.discountSom)
        assertEquals("700 000", c.cash)
        assertEquals("300 000", c.card)
        assertEquals("1 500 000", c.debtSom)
        assertEquals("0,00", c.paidUsd)
    }

    @Test fun textHasOnly1cRows() {
        val t = Chek.ofSale(sale, sale.code, "Izzattilo", "Dokon", "+998936474646").text()
        for (x in listOf("Vaqt:", "Sana:", "Mijoz: 30718", "Agent: Dokon", "Qarz qoldig'i", "Summa Sum:", "Chegirma Sum:", "To'lov Naqd:", "Plastik:", "Val ( $ ):", "Tel : +998936474646")) {
            assertTrue(x, t.contains(x))
        }
    }

    @Test fun usdSaleGoesToValColumn() {
        val c = Chek.ofSale(sale.copy(usd = true, paid = 50.0, cash = 0.0, card = 0.0, discount = 0.0), "A-1", "X", "", "")
        assertEquals("0", c.sumSom)
        assertEquals("50,00", c.paidUsd)
        assertTrue(!c.text().contains("Agent:"))
    }

    @Test fun phonePrefixNotDoubled() {
        assertEquals("+998936474646, +998907788510", Chek.phoneOf(listOf("Tel: +998936474646", " Тел. +998907788510", "")))
    }
}
