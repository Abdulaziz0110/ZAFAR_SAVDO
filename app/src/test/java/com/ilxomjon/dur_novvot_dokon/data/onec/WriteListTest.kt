package com.ilxomjon.dur_novvot_dokon.data.onec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.imsoft.shared.onec.WriteKind
import uz.imsoft.shared.onec.WriteRequest
import uz.imsoft.shared.onec.WriteResult

/** v5.3: 1C navbati ro'yxati — qayta yuborilgan xato qatorlar ko'paymaydi. */
class WriteListTest {
    private fun pay(id: String, party: String, sum: Double) = WriteRequest(id, WriteKind.SUPPLIER_PAY, 1L, party = party, partyCode = "7", cashSom = sum)
    private fun err(id: String) = WriteResult(id, 5L, false, WriteResult.ERROR, "", "", "Xato")
    private fun ok(id: String) = WriteResult(id, 5L, true, WriteResult.DONE, "33", "ref", "")

    @Test fun repeatedFailuresCollapseIntoNewest() {
        val list = listOf(
            WriteItem(pay("a1", "Asal", 880_000.0), 100L, err("a1")),
            WriteItem(pay("a2", "Asal", 880_000.0), 200L, err("a2")),
            WriteItem(pay("a3", "Asal", 880_000.0), 300L, err("a3")),
        )
        val out = compact(list)
        assertEquals(1, out.size)
        assertEquals("a3", out[0].req.id)
        assertEquals(3, out[0].tries)
        assertEquals(100L, out[0].createdAt)
    }

    @Test fun failureFollowedBySuccessLeavesOnlySuccess() {
        val out = compact(listOf(WriteItem(pay("a1", "Asal", 5.0), 100L, err("a1")), WriteItem(pay("a2", "Asal", 5.0), 200L, ok("a2"))))
        assertEquals(1, out.size)
        assertTrue(out[0].isDone)
        assertEquals(2, out[0].tries)
    }

    @Test fun successesAreNeverMerged() {
        // bir xil to'lov ikki marta haqiqatan qilingan bo'lishi mumkin
        val out = compact(listOf(WriteItem(pay("a1", "Asal", 5.0), 100L, ok("a1")), WriteItem(pay("a2", "Asal", 5.0), 200L, ok("a2"))))
        assertEquals(2, out.size)
    }

    @Test fun differentWorkStaysSeparate() {
        val out = compact(listOf(WriteItem(pay("a1", "Asal", 5.0), 100L, err("a1")), WriteItem(pay("b1", "Malina", 5.0), 200L, err("b1"))))
        assertEquals(2, out.size)
    }

    @Test fun olderSuccessIsKeptWhenNewerFails() {
        val out = compact(listOf(WriteItem(pay("a1", "Asal", 5.0), 100L, ok("a1")), WriteItem(pay("a2", "Asal", 5.0), 200L, err("a2"))))
        assertEquals(2, out.size)
    }

    @Test fun waitingAndErrorFlags() {
        assertTrue(WriteItem(pay("a", "x", 1.0), 1L).isWaiting)
        assertTrue(WriteItem(pay("a", "x", 1.0), 1L, sendError = "net").isError)
        assertTrue(WriteItem(pay("a", "x", 1.0), 1L, err("a")).isError)
    }
}
