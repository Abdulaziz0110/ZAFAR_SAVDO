package uz.imsoft.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uz.imsoft.shared.sched.RateQuote

/** v5.0: kursni internetdan o'qish — turli sayt formatlari. */
class RateQuoteTest {
    @Test fun cbuFormat() {
        val json = """[{"id":69,"Code":"840","Ccy":"USD","CcyNm_UZ":"AQSH dollari","Nominal":"1","Rate":"12850.25","Diff":"5.10","Date":"2026-11-07"}]"""
        val q = RateQuote.parse("Markaziy bank", json)!!
        assertEquals(12850.25, q.value, 0.001)
        assertEquals("2026-11-07", q.date)
        assertEquals(12850.25, q.mid, 0.001)
    }

    @Test fun bankFormat() {
        val json = """{"data":[{"code":"860","title":"UZS"},{"code":"840","title":"USD","nbu_buy_price":"12 800","nbu_cell_price":"12 950","date":"07.11.2026"}]}"""
        val q = RateQuote.parse("Bank", json)!!
        assertEquals(12800.0, q.buy, 0.001)
        assertEquals(12950.0, q.sell, 0.001)
        assertEquals(12950.0, q.value, 0.001)
        assertEquals(12875.0, q.mid, 0.001)
    }

    @Test fun badInput() {
        assertNull(RateQuote.parse("x", "salom"))
        assertNull(RateQuote.parse("x", """[{"Ccy":"EUR","Rate":"14000"}]"""))
        assertNull(RateQuote.parse("x", """[{"Ccy":"USD","Rate":"0"}]"""))
        assertNull(RateQuote.parse("x", """[{"Ccy":"USD","Rate":"99999999"}]"""))
    }
}
