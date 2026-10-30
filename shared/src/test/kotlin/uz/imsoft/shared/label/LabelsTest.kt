package uz.imsoft.shared.label

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.imsoft.shared.barcode.BarcodeEncode
import uz.imsoft.shared.report.Cell
import uz.imsoft.shared.report.Grid

class LabelsTest {
    private fun m(s: BarcodeEncode.Symbol) = s.modules.joinToString("") { if (it) "1" else "0" }

    /** Naqshlar mustaqil dekoder (zxing-cpp) bilan tekshirilgan: rasm chizib o'qitildi — aynan shu raqamlar chiqdi. */
    @Test fun barcodePatternsVerified() {
        assertEquals(
            "10101101110010001000110100110010110011001101101010111001011100101001000111010011100101010000101",
            m(BarcodeEncode.ean13("4870112008906")),
        )
        assertEquals("1010001011010111101111010110111010101001110111001010001001011100101", m(BarcodeEncode.ean8("96385074")))
        assertEquals(
            "110100111001100110011011011000110111010011001100011011010110001000100010110001100011101011",
            m(BarcodeEncode.code128("0231243236")),
        )
        assertEquals(95, BarcodeEncode.ean13("2500000024375").modules.size)
    }

    @Test fun shopCodeChoice() {
        // 1C'dagi o'z kodimiz (12 xona, tekshiruvsiz) -> do'kon yorlig'idagidek EAN-13 + tekshiruv raqami
        val own = BarcodeEncode.forShopCode("250000002437")!!
        assertEquals(BarcodeEncode.Kind.EAN13, own.kind); assertEquals("2500000024375", own.text)
        assertEquals("4870112008906", BarcodeEncode.forShopCode("4870112008906")!!.text)
        // UPC-A va Excel'da nollari tushib qolgani — bir xil EAN-13
        assertEquals("0012000800214", BarcodeEncode.forShopCode("012000800214")!!.text)
        assertEquals("0012000800214", BarcodeEncode.forShopCode("12000800214")!!.text)
        assertEquals(BarcodeEncode.Kind.EAN8, BarcodeEncode.forShopCode("96385074")!!.kind)
        assertEquals(BarcodeEncode.Kind.CODE128, BarcodeEncode.forShopCode("0231243236")!!.kind)
        // Tarozi tovari (7 xona) — shtrixli yorliq emas, PLU
        assertNull(BarcodeEncode.forShopCode("2900025"))
        assertNull(BarcodeEncode.forShopCode(""))
    }

    @Test fun pluFromMxlLikeText() {
        val txt = "MOXCEL\u0000\u0008\u0000\u0001\u0000\u0009\u0000\uFEFF{8,1,9,\n{\"#\",\"\",1,1,\"#\",\"Язык\",\"Язык\"},\n" +
            "{16,9,\n{1,1,\n{\"#\",\"Olma Qizil (\"\"Fuji\"\")\"}\n}\n},3,0,\n{16,10,\n{1,1,\n{\"#\",\"KOD:  501\"}\n}\n},\n{16,11,\n{1,1,\n{\"#\",\"60 000\"}\n}\n},\n" +
            "{16,9,\n{1,1,\n{\"#\",\"Orik Subxon 116\"}\n}\n},{16,10,\n{1,1,\n{\"#\",\"KOD:  151\"}\n}\n},{16,11,\n{1,1,\n{\"#\",\"80 000\"}\n}\n}}"
        val b = txt.toByteArray(Charsets.UTF_8)
        assertTrue(MxlText.isMxl(b))
        val rows = PluImport.parse(b, "PLU_Narx.mxl")
        assertEquals(2, rows.size)
        assertEquals("Olma Qizil (\"Fuji\")", rows[0].name); assertEquals("501", rows[0].plu)
        assertEquals("Orik Subxon 116", rows[1].name); assertEquals("151", rows[1].plu)
    }

    private fun grid(vararg rows: List<Any?>) = Grid(rows.map { r -> r.map { v -> when (v) { null -> null; is Number -> Cell("", v.toDouble()); else -> Cell(v.toString()) } }.toTypedArray() })

    @Test fun pluFromExcelForm() {
        // 1C "PLU Narx" Excel'ga saqlangan: nom bir qatorda, "KOD" va narx keyingisida
        val g = grid(
            listOf("Orik Bargak Balonchik", null, null),
            listOf("KOD:  501", null, "60 000"),
            listOf("Orik Subxon 116", null, null),
            listOf("KOD:  151", null, "80 000"),
        )
        val rows = PluImport.fromGrid(g)
        assertEquals(listOf("Orik Bargak Balonchik" to "501", "Orik Subxon 116" to "151"), rows.map { it.name to it.plu })
    }

    @Test fun pluFromTable() {
        val g = grid(
            listOf("Код", "Наименование", "Номер PLU"),
            listOf(2555, "Orik Bargak Balonchik", 501),
            listOf("241", "Orik Subxon 116", "0151"),
        )
        val rows = PluImport.fromGrid(g)
        assertEquals(2, rows.size)
        assertEquals("2555", rows[0].code); assertEquals("501", rows[0].plu)
        assertEquals("151", rows[1].plu)
        data class P(val id: String, val name: String)
        val (map, missing) = PluImport.link(rows, listOf(P("2555", "X"), P("9", "ORIK SUBXON 116")), { it.id }, { it.name })
        assertEquals(mapOf("2555" to "501", "9" to "151"), map)
        assertTrue(missing.isEmpty())
    }

    @Test(expected = PluImport.NotPlu::class)
    fun notPlu() { PluImport.parse("Nomi;Narx\nOlma;5000\n".toByteArray(), "x.csv") }

    @Test fun price() {
        assertEquals("60 000", LabelPrice.som(60000.0))
        assertEquals("1 250 500", LabelPrice.som(1250499.6))
        assertEquals("950", LabelPrice.som(950.0))
    }

    @Test
    fun codesPayload() {
        val json = """{"v":1,"src":{"plu":"РегистрСведений.Т (Номенклатура -> Код)","bc":""},"items":[
            {"c":"2555","n":"Orik Bargak Balonchik","p":"0501","b":["2901740","2901740"]},
            {"c":"4307","n":"Mirinda 0.3 L","p":"","b":["012000800214"]},
            {"c":"","n":"","p":"7","b":[]},
            {"c":241,"n":"Orik Subxon 116","p":151,"b":[]}]}"""
        val d = CodesPayload.parse(json.toByteArray())
        assertEquals(3, d.items.size)
        assertEquals("501", d.items[0].plu)
        assertEquals(listOf("2901740"), d.items[0].barcodes)
        assertEquals("241", d.items[2].code)
        assertEquals("151", d.items[2].plu)
        assertEquals(listOf("501", "151"), d.pluRows.map { it.plu })
        assertEquals(2, d.barcodeRows.size)
        assertTrue(d.pluSource.contains("РегистрСведений"))
        data class P(val id: String, val name: String)
        val cat = listOf(P("2555", "Orik Bargak Balonchik"), P("99", "Orik Subxon 116"))
        val (map, missing) = PluImport.link(d.pluRows, cat, { it.id }, { it.name })
        assertEquals(mapOf("2555" to "501", "99" to "151"), map) // ikkinchisi nom bo'yicha
        assertTrue(missing.isEmpty())
    }

    @Test
    fun snapshotCarriesPlu() {
        val snap = uz.imsoft.shared.report.ClientSnapshot(
            1L, "c1", "Mijoz", 12800.0, null,
            listOf(uz.imsoft.shared.report.PriceItem("2555", "Orik Bargak Balonchik", "Orik", 60000.0, 4.7, 58000.0, 4.5, plu = "501")),
            emptyList(), emptyList(), 0L,
        )
        val back = uz.imsoft.shared.report.SnapshotCodec.fromJson(uz.imsoft.shared.report.SnapshotCodec.toJson(snap))
        assertEquals("501", back.products[0].plu)
        // eski 1-ilova yuborgan (PLU'siz) ma'lumot ham o'qiladi
        val old = """{"v":1,"at":1,"cid":"c","cn":"M","rate":1,"pat":0,"p":[["1","A","",1,0,1,0]],"l":[],"cov":[]}"""
        assertEquals("", uz.imsoft.shared.report.SnapshotCodec.fromJson(old).products[0].plu)
    }
}
