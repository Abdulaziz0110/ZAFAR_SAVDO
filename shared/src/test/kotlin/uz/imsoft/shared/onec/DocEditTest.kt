package uz.imsoft.shared.onec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v5.2 (A5): tarixdagi 1C hujjatini o'zgartirish so'rovi — tekshiruv va bulutga yoziladigan matn. */
class DocEditTest {
    private fun req(
        kind: String = "r", no: String = "125", stamp: String = "20261108103000",
        lines: List<WriteLine> = listOf(WriteLine("Bodom", "1042", 15.0, 120000.0, 0.0)),
    ) = WriteRequest(
        id = "w1", kind = WriteKind.DOC_EDIT, at = 1L, party = "Dadaxon Aka", partyCode = "77", lines = lines,
        fields = mapOf(DocField.KIND to kind, DocField.NUMBER to no, DocField.STAMP to stamp),
    )

    @Test
    fun validRequestPasses() = assertNull(req().problem())

    @Test
    fun rejectsUneditableDocKind() = assertEquals("kind", req(kind = "y").problem())

    @Test
    fun rejectsMissingNumberStampAndLines() {
        assertEquals("number", req(no = "").problem())
        assertEquals("stamp", req(stamp = "2026").problem())
        assertEquals("lines", req(lines = emptyList()).problem())
    }

    @Test
    fun rejectsBadLine() {
        assertEquals("line", req(lines = listOf(WriteLine("Bodom", "1042", 0.0, 1.0, 0.0))).problem())
        assertEquals("line", req(lines = listOf(WriteLine("", "1042", 1.0, 1.0, 0.0))).problem())
    }

    @Test
    fun payloadKeepsDocFieldsAndSurvivesRoundTrip() {
        val r = req()
        val back = WriteCodec.fromCloud("w1", WriteCodec.toCloud(r, "Admin"))!!
        assertEquals(WriteKind.DOC_EDIT, back.kind)
        assertEquals("r", back.fields[DocField.KIND])
        assertEquals("125", back.fields[DocField.NUMBER])
        assertEquals("20261108103000", back.fields[DocField.STAMP])
        assertEquals(1, back.lines.size)
        assertEquals(15.0, back.lines[0].qty, 1e-9)
        assertEquals(120000.0, back.lines[0].priceSom, 1e-9)
    }
}
