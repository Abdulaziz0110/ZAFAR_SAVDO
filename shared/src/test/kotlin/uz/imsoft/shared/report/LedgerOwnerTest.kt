package uz.imsoft.shared.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v3.6: Admin "barcha mijozlar" tarixi — bir soniyadagi ikki mijoz hujjati aralashmaydi, nomi qidiruvda bor. */
class LedgerOwnerTest {
    private fun sale(stamp: Long, owner: String?, sum: Double) = LedgerEntry(
        stamp, EntryKind.SALE, owner?.let { LedgerOwner.tag("Savdo So'm", "S$it", it) } ?: "Savdo So'm", "Pista", 1.0,
        mapOf(Col.SUM to Money2(sum, 0.0)),
    )

    @Test fun twoClientsSameSecondStaySeparate() {
        val docs = LedgerDocs.build(listOf(sale(20261101135000, "Dadaxon", 100.0), sale(20261101135000, "Ali", 50.0)))
        assertEquals(2, docs.size)
        assertEquals(setOf("Dadaxon", "Ali"), docs.map { LedgerOwner.of(it.kindText)?.second }.toSet())
        assertEquals("SDadaxon", LedgerOwner.of(docs.first { it.total.som == 100.0 }.kindText)?.first)
    }

    @Test fun plainTextAndUntagged() {
        val t = LedgerOwner.tag("To'lov So'm", "S9", "Dadaxon Aka (TRZ)")
        assertEquals("To'lov So'm", LedgerOwner.plain(t))
        assertEquals("S9" to "Dadaxon Aka (TRZ)", LedgerOwner.of(t))
        assertNull(LedgerOwner.of("To'lov So'm"))
        assertEquals("To'lov So'm", LedgerOwner.plain("To'lov So'm"))
    }

    @Test fun clientNameIsSearchable() {
        val e = listOf(sale(20261101135000, "Dadaxon", 100.0), sale(20261101120000, "Ali", 50.0))
        val r = LedgerIndex(e).search("dadaxon", 20261101235959, LedgerFilter(null, 0L, Long.MAX_VALUE), true)
        assertEquals(1, r.hits.size)
    }
}
