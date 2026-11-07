package uz.imsoft.shared.onec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v5.1: yangi tovar kartochkasi — 1C maydonlari va tekshiruvlar. */
class NewGoodsTest {
    private fun req(f: Map<String, String>, party: String = "") =
        WriteRequest("1", WriteKind.NEW_GOODS, 1L, party, "", fields = f)

    private val base = mapOf(
        GoodsField.NAME to "Keshu 160",
        GoodsField.GROUP to "Yong'oqlar",
        GoodsField.UNIT to "kg",
        GoodsField.TYPE to GoodsField.TYPE_GOODS,
    )

    @Test fun cardIsEnough() {
        assertNull(req(base).problem())
    }

    @Test fun groupAndUnitRequiredWithoutSample() {
        assertEquals("group", req(base - GoodsField.GROUP).problem())
        assertEquals("unit", req(base - GoodsField.UNIT).problem())
        // namuna tanlangan bo'lsa — shart emas
        assertNull(req(mapOf(GoodsField.NAME to "Keshu"), party = "Bodom").problem())
    }

    @Test fun weighedNeedsPlu() {
        assertEquals("plu", req(base + (GoodsField.TYPE to GoodsField.TYPE_WEIGHED)).problem())
        assertNull(req(base + (GoodsField.TYPE to GoodsField.TYPE_WEIGHED) + (GoodsField.PLU to "501") + (GoodsField.SCALE to "ОбщиеВесы")).problem())
    }

    @Test fun autoBarcodeCannotHaveManualOne() {
        assertEquals("barcode", req(base + (GoodsField.AUTO_BARCODE to "1") + (GoodsField.BARCODE to "4870112008906")).problem())
        assertNull(req(base + (GoodsField.AUTO_BARCODE to "1")).problem())
    }

    @Test fun numbersChecked() {
        assertEquals(GoodsField.VOLUME, req(base + (GoodsField.VOLUME to "salom")).problem())
        assertEquals(GoodsField.PACK, req(base + (GoodsField.PACK to "-3")).problem())
        assertNull(req(base + (GoodsField.VOLUME to "0.5") + (GoodsField.PACK to "12") + (GoodsField.MARKUP to "25")).problem())
    }

    @Test fun codecKeepsCardFields() {
        val r = req(base + (GoodsField.BRAND to "Zafar") + (GoodsField.VOLUME to "0.5") + (GoodsField.AUTO_BARCODE to "1"))
        val back = WriteCodec.fromCloud(r.id, WriteCodec.toCloud(r, "Admin"))!!
        assertEquals(r.fields, back.fields)
        assertEquals(WriteKind.NEW_GOODS, back.kind)
    }
}
