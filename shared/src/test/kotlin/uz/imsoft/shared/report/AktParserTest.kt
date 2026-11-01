package uz.imsoft.shared.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v3.8: server javobi — "solishtirma dalolatnoma" (haqiqiy server fayli asosida). */
class AktParserTest {
    private fun grid(name: String): Grid =
        HtmlTable.read(String(requireNotNull(javaClass.classLoader?.getResourceAsStream("cards/$name")).readBytes(), Charsets.UTF_8))

    @Test fun realServerAnswerWithoutOperations() {
        val r = ServerReport.parse(grid("akt_empty.htm"))
        assertEquals(0, r.entries.size)
        assertEquals("Dostavka Dadaxon Aka", r.clientName)
        assertEquals(20261002000000, r.periodFrom)
        assertEquals(20261101235959, r.periodTo)
        assertEquals(20317500.0, r.closing?.second?.som ?: 0.0, 0.0)
        assertEquals(20317500.0, r.opening?.second?.som ?: 0.0, 0.0)
    }

    @Test fun operationsBecomeDocuments() {
        val r = ServerReport.parse(grid("akt_ops.htm"))
        assertEquals(3, r.entries.size)
        val (sale, pay, ret) = r.entries
        assertEquals(EntryKind.SALE, sale.kind)
        assertEquals(20261027104117, sale.stamp)
        assertEquals(12_500_000.0, sale.v(Col.SUM).som, 0.0)
        assertTrue(sale.kindText.contains("Pista"))
        assertEquals(EntryKind.PAYMENT, pay.kind)
        assertEquals(50_000_000.0, pay.v(Col.PAYMENT).som, 0.0)
        assertEquals("naqd", pay.product)
        assertEquals(EntryKind.RETURN, ret.kind)
        assertEquals(1_000_000.0, LedgerDocs.build(listOf(ret)).single().total.som, 0.0)
        assertNotNull(r.closing)
        assertEquals(-17_182_500.0, r.closing?.second?.som ?: 0.0, 0.0)
        // Birlashtirishda shu davr to'liq almashtiriladi
        val m = LedgerMerge.merge(emptyList(), emptyList(), r, 1L, "server")
        assertEquals(3, m.added)
    }
}
