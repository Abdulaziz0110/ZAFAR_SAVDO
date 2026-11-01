package uz.imsoft.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uz.imsoft.shared.onec.GoodsField
import uz.imsoft.shared.onec.OneCCodec
import uz.imsoft.shared.onec.WriteCodec
import uz.imsoft.shared.onec.WriteKind
import uz.imsoft.shared.onec.WriteRequest

/** v3.12: yangi 1C'ga yozish turlari — telefon va kompyuter (onec_write.py) bir xil tekshiradi. */
class V312Test {
    private fun r(kind: String, party: String = "", code: String = "", f: Map<String, String> = emptyMap(), cash: Double = 0.0, click: Double = 0.0, op: String = "") =
        WriteRequest("w1", kind, 1L, party, code, cashSom = cash, clickSom = click, op = op, fields = f)

    @Test fun validation() {
        assertEquals("party", r(WriteKind.CLIENT_PAY, cash = 5.0).problem())
        assertEquals("amount", r(WriteKind.CLIENT_PAY, "Ali").problem())
        assertNull(r(WriteKind.CLIENT_PAY, "Ali", click = 5.0).problem())
        assertEquals("op", r(WriteKind.CASH_OUT, cash = 5.0).problem())
        assertNull(r(WriteKind.CASH_IN, op = "Ijara", cash = 1.0).problem())
        assertEquals("empty", r(WriteKind.GOODS_EDIT, code = "1001").problem())
        assertEquals("plu", r(WriteKind.GOODS_EDIT, code = "1001", f = mapOf(GoodsField.PLU to "1.5")).problem())
        assertEquals("price", r(WriteKind.GOODS_EDIT, code = "1001", f = mapOf(GoodsField.P1 to "-1")).problem())
        assertNull(r(WriteKind.GOODS_EDIT, code = "1001", f = mapOf(GoodsField.P1 to "1300", GoodsField.CUR to "s")).problem())
        assertEquals("name", r(WriteKind.NEW_GOODS, code = "1001", f = mapOf(GoodsField.P1 to "5")).problem())
        assertEquals("barcode", r(WriteKind.NEW_GOODS, code = "1001", f = mapOf(GoodsField.NAME to "Yangi", GoodsField.BARCODE to "12 3")).problem())
        assertNull(r(WriteKind.NEW_GOODS, code = "1001", f = mapOf(GoodsField.NAME to "Yangi", GoodsField.BARCODE to "4780000000099")).problem())
    }

    @Test fun codecRoundTrip() {
        val a = WriteRequest(
            "w9", WriteKind.CLIENT_PAY, 5L, "Ali", "3", "izoh", cashSom = 100.0, clickSom = 20.0, clickUsd = 1.5, discountSom = 3.0,
            op = "Ijara", opCode = "12", fields = mapOf(GoodsField.P2 to "900", "hack" to "x"),
        )
        val back = WriteCodec.fromCloud("w9", mapOf("k" to a.kind, "p" to WriteCodec.payload(a), "c" to 5L))
        assertEquals(a.copy(fields = mapOf(GoodsField.P2 to "900")), back)
        assertEquals(120.0, a.totalSom, 0.0)
        assertEquals(1.5, a.totalUsd, 0.0)
    }

    @Test fun cashOps() {
        val s = OneCCodec.parseSuppliers("""{"f":"20261001","t":"20261101","at":1,"list":[],"ops":[["12","Ijara"],["3",""],[7,"Svet"]]}""".toByteArray())
        assertEquals(listOf("12" to "Ijara", "7" to "Svet"), s.ops.map { it.code to it.name })
        assertEquals(s.ops, OneCCodec.parseSuppliers(OneCCodec.suppliersJson(s).toByteArray()).ops)
    }
}
