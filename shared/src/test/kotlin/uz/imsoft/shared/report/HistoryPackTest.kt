package uz.imsoft.shared.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** v3.7: Admin -> agent "1C tarixi" bo'laklari. */
class HistoryPackTest {
    private fun e(st: Long, owner: String) = LedgerEntry(
        st, EntryKind.SALE, LedgerOwner.tag("1C №5", "S1", owner), "Pista", 2.5, mapOf(Col.SUM to Money2(1000.0, 0.0), Col.PRICE to Money2(400.0, 0.0)),
    )

    @Test fun bucketsByTenDays() {
        assertEquals("b2026110", HistoryPack.bucket(20261101090000))
        assertEquals("b2026110", HistoryPack.bucket(20261110235959))
        assertEquals("b2026111", HistoryPack.bucket(20261111000000))
        assertEquals("b2026112", HistoryPack.bucket(20261131120000))
    }

    @Test fun roundTripKeepsOwner() {
        val list = listOf(e(20261101090000, "Dadaxon Aka"), e(20261101100000, "Chakana"))
        val back = HistoryPack.unpack(HistoryPack.pack(HistoryPack.json(list)))
        assertEquals(list, back)
        assertEquals("Dadaxon Aka", LedgerOwner.of(back[0].kindText)?.second)
    }

    @Test fun hashChangesWithContent() {
        val a = HistoryPack.json(listOf(e(20261101090000, "A")))
        val b = HistoryPack.json(listOf(e(20261101090000, "B")))
        assertEquals(HistoryPack.hash(a), HistoryPack.hash(HistoryPack.json(listOf(e(20261101090000, "A")))))
        assertNotEquals(HistoryPack.hash(a), HistoryPack.hash(b))
    }
}
