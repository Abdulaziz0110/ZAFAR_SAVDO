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

    @Test fun paymentsAppearInHistory() {
        val p = AppSale("p5", 1L, 20261101124000L, false, false, emptyList(), 0.0, "T-0005", paid = 50_000_000.0, payment = true, note = "Naqd")
        val e = AppSale.toEntries(p)
        assertEquals(1, e.size)
        val d = LedgerDocs.build(e).single()
        assertEquals(EntryKind.PAYMENT, d.kind)
        assertEquals(50_000_000.0, d.total.som, 0.0)
        assertEquals(Money2(50_000_000.0, 0.0), AppSale.pendingPayments(e))
        assertEquals(p, AppSale.fromJson(JObj.parse(Json.write(AppSale.toJson(p)))))
        // Savdoda to'langan qism — alohida to'lov hujjati
        val paidSale = sale.copy(paid = 100_000.0)
        assertEquals(2, LedgerDocs.build(AppSale.toEntries(paidSale)).size)
        // Bu oy "To'landi" hisobiga kiradi
        val t = ClientStats.totals(e, 20261101000000L, 20261130235959L)
        assertEquals(50_000_000.0, t.paid.som, 0.0)
    }

    @Test fun noDuplicateWhenOneCAlreadyHasIt() {
        val pay = AppSale("p1", 1L, 20261101124000L, false, false, emptyList(), 0.0, "T-1", paid = 50_000_000.0, payment = true)
        // 1C hisobotida 2 daqiqa keyin o'sha to'lov bor
        val oneC = LedgerEntry(20261101124200L, EntryKind.PAYMENT, "To'lov", "Naqd", 0.0, mapOf(Col.PAYMENT to Money2(50_000_000.0, 0.0)))
        assertEquals(0, AppSale.entriesNotIn(listOf(pay, sale.copy(stamp = 20261101125000L)), 0L, listOf(oneC)).count { it.kind == EntryKind.PAYMENT })
        assertEquals(2, AppSale.entriesNotIn(listOf(pay, sale.copy(stamp = 20261101125000L)), 0L, listOf(oneC)).size)
        // Summasi boshqa — o'sha emas
        val other = oneC.copy(values = mapOf(Col.PAYMENT to Money2(10_000_000.0, 0.0)))
        assertEquals(1, AppSale.entriesNotIn(listOf(pay), 0L, listOf(other)).size)
        // 30 daqiqa farq — o'sha emas
        assertEquals(1, AppSale.entriesNotIn(listOf(pay), 0L, listOf(oneC.copy(stamp = 20261101131500L))).size)
    }

    @Test fun pendingDebtAfterServerTime() {
        val s1 = sale.copy(id = "a", stamp = 20261101130000L)   // 364 000 + 31 310 − 1 310 = 394 000
        val p1 = AppSale("p", 1L, 20261101131000L, false, false, emptyList(), 0.0, "T", paid = 100_000.0, payment = true)
        val ledger = AppSale.entriesAfter(listOf(s1, p1), 0L)
        val (eff, n) = AppSale.pendingAfter(ledger, 20261101125900L)
        assertEquals(2, n)
        assertEquals(294_000.0, eff.som, 0.01)
        // Server qarzi savdodan keyin o'qilgan — faqat to'lov qoladi
        assertEquals(-100_000.0, AppSale.pendingAfter(ledger, 20261101130500L).first.som, 0.01)
        assertEquals(0, AppSale.pendingAfter(ledger, 20261101140000L).second)
    }
}
