package uz.imsoft.shared.barcode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.imsoft.shared.report.Cell
import uz.imsoft.shared.report.Grid

class BarcodesTest {
    private fun grid(vararg rows: List<Any?>) = Grid(rows.map { r ->
        r.map { v ->
            when (v) {
                null -> null
                is Number -> Cell("", v.toDouble())
                else -> Cell(v.toString())
            }
        }.toTypedArray()
    })

    @Test fun checksumAndCanonical() {
        assertTrue(Ean.checkOk("012000800214"))      // UPC-A (rasmdagi Mirinda)
        assertTrue(Ean.checkOk("054881003995"))
        assertFalse(Ean.checkOk("012000800215"))
        assertTrue(Ean.checkOk("2204307012348"))
        assertEquals(4, Ean.checkDigit("01200080021"))
        // UPC-A, EAN-13 (0 bilan) va Excel soni bir xil kalit
        assertEquals(Ean.canonical("012000800214"), Ean.canonical("0012000800214"))
        assertEquals(Ean.canonical("012000800214"), Ean.canonical("12000800214"))
        assertEquals("ABC-12", Ean.canonical(" abc-12 "))
        assertTrue(Ean.looksLikeBarcode("0231243236"))
        assertFalse(Ean.looksLikeBarcode("Mirinda"))
        assertFalse(Ean.looksLikeBarcode("4307"))
    }

    @Test fun scaleLabel() {
        val f = ScaleFormat()
        val h = f.parse("2204307012348")!!
        assertEquals("4307", h.itemCode)
        assertEquals(1.234, h.qty, 1e-9)
        assertNull(f.parse("2204307012349"))           // tekshiruv raqami xato
        assertNull(f.parse("4780000123456"))           // tarozi prefiksi emas
        assertNull(f.parse("2204307000004".let { it.dropLast(1) + Ean.checkDigit(it.dropLast(1)) })) // og'irlik 0
        assertNull(ScaleFormat(enabled = false).parse("2204307012348"))
        // 10 grammlik: 00123 -> 1.23 kg
        val g = ScaleFormat(divisor = 100.0).parse("2204307001232".let { it.dropLast(1) + Ean.checkDigit(it.dropLast(1)) })!!
        assertEquals(1.23, g.qty, 1e-9)
        // 6 xonali kod + 4 xonali og'irlik
        val k = ScaleFormat(prefixes = listOf("27"), codeLen = 6, valueLen = 4).parse("270043071234".let { "2700430712345".dropLast(1) + Ean.checkDigit("2700430712345".dropLast(1)) })!!
        assertEquals("4307", k.itemCode)
        assertEquals(1.234, k.qty, 1e-9)
        // saqlash/o'qish
        val enc = ScaleFormat(prefixes = listOf("21", "22"), codeLen = 6, valueLen = 4, divisor = 100.0, checkDigit = false, trustCodes = true).encode()
        val d = ScaleFormat.decode(enc)
        assertEquals(listOf("21", "22"), d.prefixes)
        assertEquals(6, d.codeLen); assertEquals(4, d.valueLen); assertEquals(100.0, d.divisor, 0.0)
        assertFalse(d.checkDigit); assertTrue(d.trustCodes)
        assertEquals(ScaleFormat(), ScaleFormat.decode("buzuq"))
    }

    @Test fun importDetectsColumns() {
        // 1C "Вывести список" ko'rinishi: sarlavha, kod (bo'shliq bilan), nom, shtrix kod (matn), sana
        val g = grid(
            listOf("", "Код", "Наименование", "Штрихкод", "Дата"),
            listOf(null, "4 307", "Mirinda 0.3 L", "012000800214", "10.09.2026"),
            listOf(null, "1 931", "Mirinda (250 ml)", "012000804502", "11.11.2025"),
            listOf(null, "1 716", "Behamad 0.9 L", "0160290056710", "05.11.2025"),
            listOf(null, "2 755", "Munis Kalooteh Qizil 500 gr", "0231243236", "03.02.2026"),
            listOf(null, 3305, "Ahmad Mevali 20 dona", 54881003995L, "11.04.2026"), // Excel son qilib yuborgan
            listOf(null, "3 309", "Ahmad Mevali 20 dona", "054881006989", "11.04.2026"),
        )
        val r = BarcodeImport.parse(g)
        assertEquals(3, r.barcodeCol); assertEquals(1, r.codeCol); assertEquals(2, r.nameCol)
        assertEquals(6, r.rows.size)
        assertEquals("4307", r.rows[0].code)
        assertEquals("Mirinda 0.3 L", r.rows[0].name)
        assertEquals("54881003995", r.rows[4].barcode)
        assertEquals(Ean.canonical("054881003995"), Ean.canonical(r.rows[4].barcode))
    }

    @Test(expected = BarcodeImport.NotBarcodes::class)
    fun importRejectsPriceList() {
        // "Tovarlar_Narxi.xlsx": faqat nom va narx — shtrix kod yo'q
        BarcodeImport.parse(grid(listOf("AKTC", 187000), listOf("AXT", 146000), listOf("chaqma bodom", 76000)))
    }

    private data class P(val id: String, val name: String)

    @Test fun linkByCodeThenName() {
        val rows = listOf(
            BarcodeImport.Row("4307", "Mirinda 0.3 L", "012000800214"),
            BarcodeImport.Row("", "Behamad 0.9 L", "0160290056710"),
            BarcodeImport.Row("9999", "Yo'q tovar", "4780000000001"),
            BarcodeImport.Row("", "Ikki xil", "4780000000002"),
        )
        val items = listOf(P("004307", "Mirinda 0,3L"), P("1716", "BEHAMAD 0.9 L"), P("1", "Ikki xil"), P("2", "Ikki xil"))
        val l = BarcodeLink.link(rows, items, { it.id }, { it.name })
        assertEquals("004307", l.map[Ean.canonical("12000800214")])
        assertEquals("1716", l.map[Ean.canonical("0160290056710")])
        assertEquals(1, l.byCode); assertEquals(1, l.byName)
        assertEquals(2, l.unmatched.size)                 // topilmadi + nomi ikki ma'noli
        assertEquals("004307", l.codes["4307"])
    }

    /** Haqiqiy 1C ro'yxatidagi ko'rinishlar: vazn tovari 7 xona (2900027), o'zimiz yopishtirgan 12 xona tekshiruvsiz (250000000031). */
    @Test fun oneCStyleCodes() {
        fun ean(body: String) = body + Ean.checkDigit(body)
        val rows = listOf(
            BarcodeImport.Row("18", "Keshu 320", "2900027"),
            BarcodeImport.Row("459", "Choy Zamin", "2800450"),
            BarcodeImport.Row("612", "Frutomix (500 gr)", "250000000031"),
            BarcodeImport.Row("1", "a", "2900001"), BarcodeImport.Row("2", "b", "2900002"), BarcodeImport.Row("3", "c", "2800003"), BarcodeImport.Row("4", "d", "2800004"),
        )
        val parsed = BarcodeImport.Result(rows, 3, 1, 2, 0)
        assertEquals(listOf("28", "29"), parsed.scalePrefixes)
        val map = rows.associate { Ean.canonical(it.barcode) to it.name }
        val res = BarcodeResolver(map, emptyMap(), emptyMap(), ScaleFormat(prefixes = parsed.scalePrefixes))
        // Tarozi yorlig'i: 29 00027 01234 + tekshiruv -> Keshu 320, 1.234 kg
        val k = res.resolve(ean("290002701234")) as BarcodeResolver.Result.Found
        assertEquals("Keshu 320", k.productId); assertEquals(1.234, k.qty!!, 1e-9); assertEquals(BarcodeResolver.Via.SCALE_BARCODE, k.via)
        val c = res.resolve(ean("280045000500")) as BarcodeResolver.Result.Found
        assertEquals("Choy Zamin", c.productId); assertEquals(0.5, c.qty!!, 1e-9)
        // O'zimiz yopishtirgan kod: printer EAN-13 qilib tekshiruv raqamini qo'shgan
        val f = res.resolve(ean("250000000031")) as BarcodeResolver.Result.Found
        assertEquals("Frutomix (500 gr)", f.productId); assertNull(f.qty)
        // ...yoki Code 128 bilan aynan 12 xona
        assertEquals("Frutomix (500 gr)", (res.resolve("250000000031") as BarcodeResolver.Result.Found).productId)
        // EAN-8 ko'rinishidagi 7 xonali kod
        assertEquals("Keshu 320", (res.resolve(ean("2900027")) as BarcodeResolver.Result.Found).productId)
    }

    /** Do'kondagi haqiqiy yorliqlar (rasmdan): tarozi "Keshu 210" 0,202 kg va o'zimiz yopishtirgan "Saryog Asal 200 gr". */
    @Test fun realLabels() {
        val map = mapOf(Ean.canonical("2900025") to "591 Keshu 210", Ean.canonical("250000002437") to "3811 Saryog Asal 200 gr")
        val res = BarcodeResolver(map, emptyMap(), emptyMap(), ScaleFormat(prefixes = listOf("28", "29")))
        val k = res.resolve("2900025002028") as BarcodeResolver.Result.Found
        assertEquals("591 Keshu 210", k.productId)
        assertEquals(0.202, k.qty!!, 1e-9)                  // yorliqda: Miqdori 0,202 kg, 155 000 x 0,202 = 31 310
        val s = res.resolve("2500000024375") as BarcodeResolver.Result.Found
        assertEquals("3811 Saryog Asal 200 gr", s.productId)
        assertNull(s.qty)
    }

    @Test fun resolver() {
        val res = BarcodeResolver(
            barcodes = mapOf(Ean.canonical("012000800214") to "A"),
            scaleCodes = mapOf("15" to "W"),
            codes = mapOf("4307" to "P"),
            scale = ScaleFormat(),
        )
        val a = res.resolve("0012000800214") as BarcodeResolver.Result.Found
        assertEquals("A", a.productId); assertNull(a.qty)
        val w = res.resolve("2200015005008".let { it.dropLast(1) + Ean.checkDigit(it.dropLast(1)) }) as BarcodeResolver.Result.Found
        assertEquals("W", w.productId); assertEquals(0.5, w.qty!!, 1e-9)
        // tarozi kodi = 1C kodi: avval tasdiq so'raladi (taklif bilan), "ishonish" yoqilsa — darhol
        val u = res.resolve("2204307012348") as BarcodeResolver.Result.Unknown
        assertEquals("4307", u.scaleCode); assertEquals("P", u.suggestion); assertEquals(1.234, u.qty!!, 1e-9)
        val trust = BarcodeResolver(emptyMap(), emptyMap(), mapOf("4307" to "P"), ScaleFormat(trustCodes = true))
        assertEquals("P", (trust.resolve("2204307012348") as BarcodeResolver.Result.Found).productId)
        val x = res.resolve("4780000000001") as BarcodeResolver.Result.Unknown
        assertNull(x.scaleCode); assertNull(x.suggestion)
    }
}
