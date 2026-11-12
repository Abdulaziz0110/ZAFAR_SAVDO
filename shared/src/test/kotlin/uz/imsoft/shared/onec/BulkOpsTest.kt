package uz.imsoft.shared.onec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v5.2 (A11): ko'p tovarga birdan amal — nom o'zgarishi, qamrov va ko'rib chiqish. */
class BulkOpsTest {
    private val items = listOf(
        Triple("1001", "Pista (Eron) 500gr", "Quruq meva"),
        Triple("1002", "Bodom - oq", "Quruq meva"),
        Triple("1003", "Shakar", "Oziq-ovqat"),
    )

    @Test
    fun replaceRemovesSeveralNeedlesAndTidiesSpaces() {
        assertEquals("Pista Eron 500", BulkText.replace("Pista (Eron) 500gr", "(|)|gr", ""))
        assertEquals("Bodom oq", BulkText.replace("Bodom - oq", "-", ""))
    }

    @Test
    fun replaceCanSubstitute() {
        assertEquals("Pista [Eron] 500gr", BulkText.replace("Pista (Eron) 500gr", "(", "[").let { BulkText.replace(it, ")", "]") })
    }

    @Test
    fun addAtStartEndAndIndex() {
        assertEquals("NEW Shakar", BulkText.add("Shakar", "NEW ", "start"))
        assertEquals("Shakar *", BulkText.add("Shakar", " *", "end"))
        assertEquals("Sha-kar", BulkText.add("Shakar", "-", "3"))
        assertEquals("Shakar!", BulkText.add("Shakar", "!", "999"))
    }

    @Test
    fun scopeFiltersByGroupMatchAndCodes() {
        val byGroup = BulkScope(group = "Quruq meva")
        assertTrue(byGroup.covers("1001", "Pista", "Quruq meva"))
        assertEquals(false, byGroup.covers("1003", "Shakar", "Oziq-ovqat"))
        val byMatch = BulkScope(match = "pista")
        assertTrue(byMatch.covers("1001", "Pista (Eron)", "x"))
        val byCodes = BulkScope(codes = listOf("1002"))
        assertEquals(false, byCodes.covers("1001", "Pista", "x"))
        assertTrue(byCodes.covers("1002", "Bodom", "x"))
        assertTrue(BulkScope().isAll)
    }

    @Test
    fun previewCountsOnlyRealChanges() {
        val f = mapOf(BulkField.OP to BulkOp.REPLACE, BulkField.FIND to "(|)|gr", BulkField.TO to "")
        val p = BulkPlan.preview(f, items)
        assertEquals(1, p.changed)
        assertEquals(3, p.total)
        assertEquals("Pista Eron 500", p.samples[0].third)
    }

    @Test
    fun previewForFieldCountsWholeScope() {
        val f = mapOf(
            BulkField.OP to BulkOp.FIELD, BulkField.KEY to GoodsField.MARKUP, BulkField.VALUE to "15",
            BulkField.GROUP to "Quruq meva",
        )
        val p = BulkPlan.preview(f, items)
        assertEquals(2, p.changed)
        assertEquals("15", p.samples[0].third)
    }

    @Test
    fun validation() {
        assertNull(BulkPlan.problem(mapOf(BulkField.OP to BulkOp.REPLACE, BulkField.FIND to "-")))
        assertEquals("find", BulkPlan.problem(mapOf(BulkField.OP to BulkOp.REPLACE, BulkField.FIND to "")))
        assertEquals("where", BulkPlan.problem(mapOf(BulkField.OP to BulkOp.ADD, BulkField.TEXT to "x", BulkField.WHERE to "qayerdir")))
        assertEquals("key", BulkPlan.problem(mapOf(BulkField.OP to BulkOp.FIELD, BulkField.KEY to "nm", BulkField.VALUE to "x")))
        assertEquals("op", BulkPlan.problem(mapOf(BulkField.OP to "yoq")))
    }

    @Test
    fun requestRoundTripKeepsBulkFields() {
        val r = WriteRequest(
            "w1", WriteKind.BULK, 1L,
            fields = mapOf(BulkField.OP to BulkOp.REPLACE, BulkField.FIND to "()|gr", BulkField.TO to "", BulkField.GROUP to "Quruq meva"),
        )
        assertNull(r.problem())
        val back = WriteCodec.fromCloud("w1", WriteCodec.toCloud(r, "Admin"))
        assertEquals(BulkOp.REPLACE, back?.fields?.get(BulkField.OP))
        assertEquals("()|gr", back?.fields?.get(BulkField.FIND))
        assertEquals("Quruq meva", back?.fields?.get(BulkField.GROUP))
    }
}

/** v5.2 (A11): qoldiqni belgilangan songa tushirish. */
class BulkStockTest {
    private val items = listOf(
        Triple("1001", "Pista", "Quruq meva"),
        Triple("1002", "Bodom", "Quruq meva"),
        Triple("1003", "Shakar", "Oziq-ovqat"),
    )

    @Test
    fun validation() {
        assertNull(BulkPlan.problem(mapOf(BulkField.OP to BulkOp.STOCK, BulkField.QTY to "0")))
        assertNull(BulkPlan.problem(mapOf(BulkField.OP to BulkOp.STOCK, BulkField.QTY to "12,5")))
        assertEquals("qty", BulkPlan.problem(mapOf(BulkField.OP to BulkOp.STOCK, BulkField.QTY to "-1")))
        assertEquals("qty", BulkPlan.problem(mapOf(BulkField.OP to BulkOp.STOCK, BulkField.QTY to "")))
    }

    @Test
    fun previewCountsScopeAndShowsTarget() {
        val f = mapOf(BulkField.OP to BulkOp.STOCK, BulkField.QTY to "0", BulkField.GROUP to "Quruq meva")
        val p = BulkPlan.preview(f, items)
        assertEquals(2, p.changed)
        assertEquals("0", p.samples[0].third)
    }
}
