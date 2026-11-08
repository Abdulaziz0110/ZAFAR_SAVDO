package com.ilxomjon.dur_novvot_dokon.data.share

import com.ilxomjon.dur_novvot_dokon.domain.Product
import org.junit.Assert.assertEquals
import org.junit.Test
import uz.imsoft.shared.OrderLine

/**
 * v5.1.1: 2-ilova buyurtmasini tasdiqlaganda savdo qatorlari qanday tuziladi.
 * Qoida: narx JORIY katalogdan, bir tovar bir necha qatorda bo'lsa miqdor qo'shiladi,
 * katalogda yo'q (yoki id si bo'sh, yoki miqdori 0) qatorlar sanaladi va tashlanadi.
 */
class OrderConfirmTest {
    private fun prod(id: String, name: String, block: Double = 1.0) =
        Product("g", id, name, true, 120.0, 0.0, 100.0, 0.0, 80.0, 0.0, block)

    private val catalog = mapOf("P1" to prod("P1", "Pista", 12.0), "P2" to prod("P2", "Shakar"))

    private fun run(lines: List<OrderLine>) =
        OrderConfirm.cartLines(lines, { catalog[it] }, { it.wholesaleSom })

    @Test
    fun mergesSameProductKeepsOrderAndUsesCatalogPrice() {
        val (out, missing) = run(
            listOf(
                OrderLine("Pista", 2.0, "P1"),
                OrderLine("Shakar", 5.0, "P2"),
                OrderLine("Pista (yana)", 3.0, "P1"),
            ),
        )
        assertEquals(0, missing)
        assertEquals(listOf("P1", "P2"), out.map { it.productId })
        assertEquals(5.0, out[0].qty, 1e-9)
        assertEquals(5.0, out[1].qty, 1e-9)
        assertEquals(100.0, out[0].price, 1e-9)
        assertEquals("Pista", out[0].name)
        assertEquals(12.0, out[0].blockSize, 1e-9)
        assertEquals(listOf(1L, 2L), out.map { it.rowId })
    }

    @Test
    fun countsUnknownEmptyAndZeroLines() {
        val (out, missing) = run(
            listOf(
                OrderLine("Yo'q tovar", 1.0, "P9"),
                OrderLine("Id siz", 1.0, ""),
                OrderLine("Nol", 0.0, "P2"),
                OrderLine("Pista", 1.5, "P1"),
            ),
        )
        assertEquals(3, missing)
        assertEquals(1, out.size)
        assertEquals(1.5, out[0].qty, 1e-9)
    }

    @Test
    fun emptyWhenNothingMatches() {
        val (out, missing) = run(listOf(OrderLine("X", 1.0, "P9")))
        assertEquals(0, out.size)
        assertEquals(1, missing)
    }

    @Test
    fun totalIsQtyTimesCatalogPrice() {
        val (out, _) = run(listOf(OrderLine("Pista", 4.0, "P1")))
        assertEquals(400.0, out[0].total, 1e-9)
    }

    /** Bildirishnomadan tasdiqlanganda chek Printer 1 dan chiqadi, to'lov yo'q (qarzga), chegirma yo'q. */
    @Test
    fun directSalePayloadIsDebtSaleOnChosenPrinter() {
        val (lines, _) = run(listOf(OrderLine("Pista", 2.0, "P1")))
        val payload = com.ilxomjon.dur_novvot_dokon.data.remote.Protocol.order(
            agentId = "A1", storeId = "S1", time = java.util.Date(0L),
            currency = com.ilxomjon.dur_novvot_dokon.domain.Currency.SOM,
            priceType = com.ilxomjon.dur_novvot_dokon.domain.PriceType.WHOLESALE,
            pay = com.ilxomjon.dur_novvot_dokon.data.remote.Protocol.Payments(0.0, 0.0, 0.0),
            discount = 0.0, rate = "12650",
            mode = com.ilxomjon.dur_novvot_dokon.domain.DealMode.SALE,
            printer = OrderConfirm.PRINTER_DEFAULT, lines = lines,
        )
        val head = payload.substringBefore('~').split("^")
        assertEquals("A1", head[0])
        assertEquals("S1", head[1])
        // to'lovlar: naqd so'm, naqd dollar, o'tkazma, chegirma — hammasi 0
        assertEquals(listOf("0", "0", "0", "0"), head.subList(5, 9).map { it })
        assertEquals("1", head[11])
        assertEquals("P1^2.0^100@", payload.substringAfter('~'))
    }
}
