package com.ilxomjon.dur_novvot_dokon.data.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.imsoft.shared.JObj

/** v5.3.8: rasm orqali kirim — javobni o'qish va o'zini o'zi o'rgatadigan tanlash. */
class ReceiptTest {
    @Test
    fun parseModelReply() {
        val reply = """Mana natija:
```json
{"rotate":90,"supplier":"ECO NON FOOD","date":"14.10.2026","currency":"som","total":2160000,
 "rows":[{"text":"JIYDA YONGOQ","qty":48,"unit":"dona","price":45000,"sum":2160000,"y0":0.41,"y1":0.45,"crossed":false},
         {"text":"SPRAT 0.9","qty":0,"unit":"blok","price":12867,"sum":231000,"y0":0.2,"y1":0.1},
         {"text":"bogyom","qty":1,"price":18500,"sum":0,"y0":0.5,"y1":0.55,"crossed":true}],"note":""}
```"""
        val r = ReceiptScan.parse(reply)
        assertEquals(90, r.rotate)
        assertEquals("ECO NON FOOD", r.supplier)
        assertEquals(3, r.rows.size)
        assertEquals(48.0, r.rows[0].qty, 0.0)
        assertEquals(17.952, r.rows[1].qty, 0.001)                 // qty = sum / price
        assertEquals(0.1, r.rows[1].y0, 0.0)                         // y0 < y1 tartibga keltirildi
        assertEquals(18500.0, r.rows[2].sum, 0.0)                    // sum = qty * price
        assertTrue(r.rows[2].crossed)
        assertTrue(ReceiptScan.sumMismatch(ReceiptScan.Row("x", 2.0, "", 100.0, 250.0, 0.0, 0.0)))
        assertEquals(0, ReceiptScan.parse("""{"rotate":45,"rows":[]}""").rotate)
    }

    @Test
    fun requestAndApiReply() {
        val body = JObj.parse(ReceiptScan.request("QUJD", "m1"))
        assertEquals("m1", body.str("model"))
        val content = body.objs("messages")[0].objs("content")
        assertEquals("image", content[0].str("type"))
        assertEquals("QUJD", content[0].obj("source")?.str("data"))
        assertEquals("{\"rows\":[]}", ReceiptScan.replyText("""{"content":[{"type":"text","text":"{\"rows\":[]}"}]}"""))
        val err = runCatching { ReceiptScan.replyText("""{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}""") }
        assertEquals("invalid x-api-key", err.exceptionOrNull()?.message)
    }

    @Test
    fun geminiFreeProvider() {
        assertTrue(ReceiptScan.isGemini("AIzaSyX"))
        assertTrue(ReceiptScan.isGemini("AQ.Ab8RN6LRmQ"))                  // 2026-yildan AI Studio kalitlari
        assertTrue(!ReceiptScan.isGemini("sk-ant-1"))
        assertEquals(ReceiptScan.GEMINI_MODEL, ReceiptScan.modelFor("AIzaX", ""))
        assertEquals(ReceiptScan.GEMINI_MODEL, ReceiptScan.modelFor("AIzaX", "claude-sonnet-5-5"))
        assertEquals("gemini-3-flash-preview", ReceiptScan.modelFor("AIzaX", "gemini-3-flash-preview"))
        assertEquals(ReceiptScan.DEFAULT_MODEL, ReceiptScan.modelFor("sk-ant-x", "gemini-2.5-flash"))
        val body = JObj.parse(ReceiptScan.geminiRequest("QUJD"))
        val parts = body.objs("contents")[0].objs("parts")
        assertEquals("QUJD", parts[0].obj("inline_data")?.str("data"))
        assertEquals("application/json", body.obj("generationConfig")?.str("responseMimeType"))
        assertEquals("{\"rows\":[]}", ReceiptScan.geminiReplyText("""{"candidates":[{"content":{"parts":[{"text":"{\"rows\":[]}"}]}}]}"""))
        val err = runCatching { ReceiptScan.geminiReplyText("""{"error":{"code":400,"message":"API key not valid","status":"INVALID_ARGUMENT"}}""") }
        assertEquals("API key not valid", err.exceptionOrNull()?.message)
    }

    private val cands = listOf(
        ReceiptMatch.Cand("101", "Jiyda Xitoy 218 gr"),
        ReceiptMatch.Cand("102", "Pista Eron"),
        ReceiptMatch.Cand("103", "Bodom Amerika"),
        ReceiptMatch.Cand("104", "Kit Kat Icon 29g"),
        ReceiptMatch.Cand("105", "Медунок 6 кг"),
    )

    @Test
    fun learnsAndRecognizesAgain() {
        val mem = ReceiptMatch.Memory()
        // birinchi marta: "JIYDA YONGOQ" — katalogda aniq yo'q
        val first = ReceiptMatch.match(mem, "JIYDA YONGOQ", "S1", cands)
        assertTrue(first.alts.contains("101"))
        // egasi "Jiyda Xitoy 218 gr" ni tanladi va 48 o'rniga 48 qoldirdi
        ReceiptMatch.learn(mem, "JIYDA YONGOQ", "S1", "101", 48.0, 48.0, 45000.0, 1L)
        val again = ReceiptMatch.match(mem, "JIYDA YONGOQ", "S1", cands)
        assertEquals("101", again.pid)
        assertEquals("mem", again.how)
        // juda o'xshash yozuv (kirill, xato bilan) — o'zi taniydi
        val similar = ReceiptMatch.match(mem, "Жийда йонгок", "S1", cands)
        assertEquals("101", similar.pid)
        assertTrue(similar.how == "mem" || similar.how == "fuzzy")
        // boshqa yuk beruvchida ham ishlaydi
        assertEquals("101", ReceiptMatch.match(mem, "JIYDA YONGOQ", "S2", cands).pid)
    }

    @Test
    fun catalogGuessAndCyrillic() {
        val mem = ReceiptMatch.Memory()
        val m = ReceiptMatch.match(mem, "Медунок 1*6 кг", "", cands)
        assertEquals("105", m.pid)
        assertEquals("catalog", m.how)
        assertNull(ReceiptMatch.match(mem, "zzzz qqqq", "", cands).pid)
        assertEquals("kit kat icon santa", ReceiptMatch.norm("KIT KAT ICON Santa!"))
        assertEquals(ReceiptMatch.norm("Yongoq"), ReceiptMatch.norm("Ёнгок"))
        assertTrue(ReceiptMatch.sim(ReceiptMatch.norm("jiyda yongoq"), ReceiptMatch.norm("Жийда ёнгок")) > 0.95)
        assertEquals("jiyda", ReceiptMatch.norm("Жийда"))
    }

    @Test
    fun factorLearnedFromBlocks() {
        val mem = ReceiptMatch.Memory()
        assertEquals(12.0, ReceiptMatch.factorOf(1.0, 12.0), 0.0)
        assertEquals(1.0, ReceiptMatch.factorOf(2.0, 3.0), 0.0)
        ReceiptMatch.learn(mem, "Kit Kat blok", "S1", "104", 2.0, 24.0, 7083.0, 1L)
        val m = ReceiptMatch.match(mem, "Kit Kat blok", "S1", cands)
        assertEquals(12.0, m.factor, 0.0)
        assertNotNull(ReceiptMatch.priceJump(mem, "S1", "104", 10000.0))
        assertNull(ReceiptMatch.priceJump(mem, "S1", "104", 7200.0))
    }

    @Test
    fun supplierAndPersistence() {
        val mem = ReceiptMatch.Memory()
        val list = listOf("7" to "TRZ Nestle Fergana", "8" to "Anzur non")
        assertEquals("8" to "Anzur non", ReceiptMatch.supplier(mem, "ANZUR NON", list))
        ReceiptMatch.learnSupplier(mem, "ECO NON FOOD OK", "8", "Anzur non")
        assertEquals("8" to "Anzur non", ReceiptMatch.supplier(mem, "Eco non food ok", list))
        ReceiptMatch.learn(mem, "Pista", "8", "102", 5.0, 5.0, 100.0, 9L)
        val back = ReceiptMatch.read(ReceiptMatch.write(mem))
        assertEquals(1, back.aliases.size)
        assertEquals("102", back.aliases[0].pid)
        assertEquals("8" to "Anzur non", back.suppliers["eco non food ok"])
        assertEquals(100.0, back.prices["8|102"] ?: 0.0, 0.0)
        assertEquals(0, ReceiptMatch.read("buzuq").aliases.size)
    }
}
