package uz.imsoft.shared.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.imsoft.shared.JObj
import uz.imsoft.shared.Json

class AppSalesTest {
    private val sale = AppSale(
        "o17", 1_761_800_000_000L, 20261030101500L, isReturn = false, usd = false,
        lines = listOf(AppSale.Line("Pista Dolana (D)", 2.0, 182_000.0), AppSale.Line("Keshu 210", 0.202, 155_000.0)),
        discount = 1_310.0, code = "B-0412",
    )

    @Test fun becomesOneLedgerDocument() {
        val docs = LedgerDocs.build(AppSale.toEntries(sale))
        assertEquals(1, docs.size)
        val d = docs[0]
        assertEquals(EntryKind.SALE, d.kind)
        assertTrue(AppSale.isApp(d.kindText))
        assertEquals(2, d.goods.size)
        assertEquals(364_000.0 + 31_310.0, d.total.som, 0.001)
        assertEquals(1_310.0, d.discount.som, 0.001)
    }

    @Test fun returnAndDollar() {
        val r = sale.copy(id = "o18", isReturn = true, usd = true, lines = listOf(AppSale.Line("Keshu", 1.0, 12.5)), discount = 0.0)
        val d = LedgerDocs.build(AppSale.toEntries(r)).single()
        assertEquals(EntryKind.RETURN, d.kind)
        assertEquals(12.5, d.total.usd, 0.001)
    }

    @Test fun hiddenOnceOneCCoversTheDay() {
        val older = sale.copy(id = "o1", stamp = 20261029180000L)
        val list = listOf(older, sale)
        assertEquals(4, AppSale.entriesAfter(list, 20261028235959L).size)
        assertEquals(2, AppSale.entriesAfter(list, 20261029235959L).size)   // 29-oktabr 1C'dan import qilingan
        assertEquals(0, AppSale.entriesAfter(list, 20261030235959L).size)
    }

    @Test fun jsonRoundTrip() {
        val back = AppSale.fromJson(JObj.parse(Json.write(AppSale.toJson(sale))))
        assertEquals(sale, back)
    }
}
