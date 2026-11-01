package uz.imsoft.shared.onec

import org.junit.Assert.assertEquals
import org.junit.Test
import uz.imsoft.shared.report.EntryKind
import uz.imsoft.shared.report.LedgerDocs

/** v3.7: kompyuter savdolari yig'iladi (1 yil), 1C tarixiga hujjat bo'lib tushadi. */
class OneCArchiveTest {
    private fun doc(id: String, st: Long, kind: String = OneCDoc.SALE, party: String = "Dadaxon") =
        OneCDoc(id, kind, id, st, party, "", 1000.0, 0.0, "", listOf(OneCLine("Pista", "1", 2.0, 500.0, 1000.0)))

    @Test fun newPeriodReplacesOnlyInside() {
        val old = OneCSales(20261001000000, 20261031000000, 1, listOf(doc("a", 20261005100000), doc("b", 20261030100000)))
        val new = OneCSales(20261030000000, 20261101000000, 2, listOf(doc("c", 20261101090000)))
        val m = OneCSalesArchive.merge(old, new, 20251101000000)
        // "b" yangi davr ichida, yangisida yo'q -> 1C'da o'chirilgan; "a" davrdan tashqarida -> qoladi
        assertEquals(listOf("c", "a"), m.docs.map { it.id })
        assertEquals(20261001000000, m.from)
        assertEquals(20261101235959, m.to)
    }

    @Test fun olderThanYearDropped() {
        val m = OneCSalesArchive.merge(null, OneCSales(20250101000000, 20250201000000, 1, listOf(doc("x", 20250105000000))), 20251101000000)
        assertEquals(0, m.docs.size)
    }

    @Test fun docBecomesLedgerDocument() {
        val e = OneCLedger.entries(doc("7", 20261101120000))
        val d = LedgerDocs.build(e).single()
        assertEquals(EntryKind.SALE, d.kind)
        assertEquals(1000.0, d.total.som, 0.0)
        val r = LedgerDocs.build(OneCLedger.entries(doc("8", 20261101120000, OneCDoc.CLIENT_RETURN))).single()
        assertEquals(EntryKind.RETURN, r.kind)
        assertEquals(1000.0, r.total.som, 0.0)
        assertEquals(0, OneCLedger.entries(doc("9", 20261101120000, OneCDoc.RECEIPT)).size)
    }
}
