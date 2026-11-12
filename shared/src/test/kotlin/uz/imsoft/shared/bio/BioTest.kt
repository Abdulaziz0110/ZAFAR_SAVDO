package uz.imsoft.shared.bio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v5.2 (A7): mahsulot ma'lumoti — saqlash, ulash, ko'chirish va 278 belgi chegarasi. */
class BioTest {
    private fun bio(id: String, vararg p: String) = Bio(id, "matn $id", 100L, p.toList())

    @Test
    fun packUnpackRoundTrip() {
        val list = listOf(bio("b1", "1001", "1002"), Bio("b2", "Ichish: kunda 2max ochq", 7L))
        val back = BioBook.unpack(BioBook.pack(list))
        assertEquals(2, back.size)
        assertEquals(listOf("1001", "1002"), back[0].products)
        assertEquals("Ichish: kunda 2max ochq", back[1].text)
        assertEquals(7L, back[1].at)
    }

    @Test
    fun unpackSurvivesGarbage() {
        assertEquals(0, BioBook.unpack("").size)
        assertEquals(0, BioBook.unpack("{bu json emas").size)
        assertEquals(0, BioBook.unpack("[{\"t\":\"id siz\"}]").size)
    }

    @Test
    fun byProductMapsEachProductOnce() {
        val m = BioBook.byProduct(listOf(bio("b1", "1001"), bio("b2", "1002")))
        assertEquals("b1", m["1001"]?.id)
        assertEquals("b2", m["1002"]?.id)
        assertEquals(null, m["9999"])
    }

    @Test
    fun attachMovesProductAwayFromOtherBio() {
        val list = BioBook.attach(listOf(bio("b1", "1001"), bio("b2")), "b2", listOf("1001"))
        assertEquals(emptyList<String>(), list[0].products)
        assertEquals(listOf("1001"), list[1].products)
    }

    @Test
    fun attachManyProductsAtOnce() {
        val list = BioBook.attach(listOf(bio("b1")), "b1", listOf("1001", "1002", "1001"))
        assertEquals(listOf("1001", "1002"), list[0].products)
    }

    @Test
    fun moveKeepsTextWhenCodeChanges() {
        val list = BioBook.move(listOf(bio("b1", "1001")), "1001", "2050")
        assertEquals(listOf("2050"), list[0].products)
        assertEquals("matn b1", list[0].text)
    }

    @Test
    fun detachLeavesOrphan() {
        val list = BioBook.detach(listOf(bio("b1", "1001")), "1001")
        assertEquals(1, BioBook.orphans(list).size)
    }

    @Test
    fun limitIs278AndClipCuts() {
        val ok = Bio("b", "x".repeat(278), 0L)
        assertTrue(ok.fits)
        assertEquals(0, ok.left)
        assertFalse(Bio("b", "x".repeat(279), 0L).fits)
        assertEquals(278, BioBook.clip("x".repeat(400)).length)
    }

    @Test
    fun upsertAndRemove() {
        var list = BioBook.upsert(emptyList(), bio("b1"))
        list = BioBook.upsert(list, Bio("b1", "yangi", 2L))
        assertEquals(1, list.size)
        assertEquals("yangi", list[0].text)
        assertEquals(0, BioBook.remove(list, "b1").size)
    }
}

/** v5.2 (A7): kompyuterda (Excel) yozilgan CSV. */
class BioCsvTest {
    @Test
    fun writesHeaderAndQuotesSemicolons() {
        val csv = BioBook.toCsv(listOf(Triple("1001", "Pista", "Immunitet; yurak")))
        assertEquals("kod;nom;malumot", csv.lineSequence().first())
        assertTrue(csv.contains("\"Immunitet; yurak\""))
    }

    @Test
    fun readsBackCodesAndTexts() {
        val csv = "kod;nom;malumot\n1001;Pista;\"Immunitet; yurak\"\n1002;Bodom;Kunda 2max ochq\n"
        val rows = BioBook.fromCsv(csv)
        assertEquals(2, rows.size)
        assertEquals("1001", rows[0].first)
        assertEquals("Immunitet; yurak", rows[0].second)
        assertEquals("Kunda 2max ochq", rows[1].second)
    }

    @Test
    fun skipsEmptyLinesAndClipsLongText() {
        val rows = BioBook.fromCsv("\n1001;Pista;" + "x".repeat(400) + "\n;;bo'sh kod\n")
        assertEquals(1, rows.size)
        assertEquals(278, rows[0].second.length)
    }
}

/** v5.2 (A10b): avtomatik ichki shtrix kod — kompyuterdagi qoida bilan bir xil. */
class ShopBarcodeTest {
    @Test
    fun startsWithTwoPadsCodeAndEndsWithCheckDigit() {
        val bc = uz.imsoft.shared.barcode.Ean.shopBarcode("1042")
        assertEquals(13, bc.length)
        assertEquals("2000001042", bc.take(10))
        assertEquals("00", bc.substring(10, 12))
        assertTrue(uz.imsoft.shared.barcode.Ean.checkOk(bc))
    }

    @Test
    fun sequenceChangesCodeAndStaysValid() {
        val a = uz.imsoft.shared.barcode.Ean.shopBarcode("1042", 0)
        val b = uz.imsoft.shared.barcode.Ean.shopBarcode("1042", 7)
        assertTrue(a != b)
        assertTrue(uz.imsoft.shared.barcode.Ean.checkOk(b))
    }

    @Test
    fun keepsOnlyLastNineDigitsOfLongCode() {
        val bc = uz.imsoft.shared.barcode.Ean.shopBarcode("AB-1234567890")
        assertEquals("2234567890", bc.take(10))
        assertTrue(uz.imsoft.shared.barcode.Ean.checkOk(bc))
    }
}
