package uz.imsoft.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedCoreTest {

    @Test fun json_roundTrip() {
        val v = linkedMapOf<String, Any?>(
            "a" to 1L, "b" to 2.5, "s" to "q\"uote\\ \n ўзбек ✓", "n" to null, "t" to true,
            "l" to listOf(1L, "x", listOf<Any?>()), "o" to linkedMapOf("k" to "v"),
        )
        val text = Json.write(v)
        assertEquals(v, Json.parse(text))
        assertEquals("1500000", Json.write(1500000.0))
        assertEquals("""{"a":"\u0001"}""", Json.write(mapOf("a" to "\u0001")))
        assertEquals("é", Json.parse("\"\\u00e9\""))
    }

    @Test fun json_rejectsGarbage() {
        for (bad in listOf("{", "[1,", "{\"a\" 1}", "tru", "\"abc", "1 2")) {
            try { Json.parse(bad); throw AssertionError("parsed: $bad") } catch (e: Json.ParseException) { }
        }
    }

    @Test fun b64_matchesJdk() {
        val r = java.util.Random(7)
        for (len in 0..70) {
            val d = ByteArray(len).also { r.nextBytes(it) }
            assertEquals(java.util.Base64.getEncoder().encodeToString(d), B64.encode(d))
            assertEquals(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(d), B64.encode(d, urlSafe = true, pad = false))
            assertTrue(d.contentEquals(B64.decode(B64.encode(d))))
            assertTrue(d.contentEquals(B64.decode(B64.encode(d, true, false))))
        }
        val big = ByteArray(200_000) { (it % 251).toByte() }
        assertTrue(big.contentEquals(B64.gunzip(B64.gzip(big))))
    }

    @Test fun linkCode_roundTripAndRejects() {
        val c = LinkCode("https://abc-default-rtdb.europe-west1.firebasedatabase.app", CloudDb.newKey(), CloudDb.newKey(), "Dadaxon Aka (TRZ)")
        val text = "Salom! Kod: " + c.encode() + " — shuni joylang"
        assertEquals(c, LinkCode.decode(text))
        assertNull(LinkCode.decode("IMSOFT-1:abc"))
        assertNull(LinkCode.decode("hech narsa"))
        val noQueue = c.copy(queueKey = "")
        assertEquals(noQueue, LinkCode.decode(noQueue.encode()))
        val wrongHost = c.copy(dbUrl = "https://evil.example.com")
        assertNull(LinkCode.decode(wrongHost.encode()))
    }

    @Test fun cloud_urlNormalize() {
        assertEquals("https://x-default-rtdb.firebaseio.com", CloudDb.normalizeUrl(" x-default-rtdb.firebaseio.com/ "))
        assertEquals("https://x.europe-west1.firebasedatabase.app", CloudDb.normalizeUrl("http://x.europe-west1.firebasedatabase.app/s/.json"))
        assertTrue(CloudDb.looksLikeFirebase("x-default-rtdb.firebaseio.com"))
        assertFalse(CloudDb.looksLikeFirebase("firebaseio.com.evil.org"))
        val db = CloudDb("x.firebaseio.com", "SEC")
        assertEquals("https://x.firebaseio.com/s/abc/meta.json?print=silent&auth=SEC", db.url("s/abc/meta", mapOf("print" to "silent")))
        assertEquals("https://x.firebaseio.com/.settings/rules.json?auth=SEC", db.url(".settings/rules"))
        try { db.url("s/a.b"); throw AssertionError() } catch (e: IllegalArgumentException) { }
        // Qoidalar haqiqiy JSON
        assertNotNull(Json.parse(CloudDb.RULES))
        assertTrue(CloudDb.isKey(CloudDb.newKey()))
    }

    @Test fun cloud_callsAndErrors() {
        val calls = ArrayList<String>()
        val fake = object : CloudDb.Http {
            override fun request(method: String, url: String, body: String?, timeoutMs: Int): CloudDb.Response {
                calls += "$method $url ${body ?: ""}"
                return when {
                    url.contains("/deny") -> CloudDb.Response(401, """{"error":"Permission denied"}""")
                    method == "GET" && url.contains("shallow=true") -> CloudDb.Response(200, """{"b":true,"a":true}""")
                    method == "GET" -> CloudDb.Response(200, """{"x":1}""")
                    else -> CloudDb.Response(204, "")
                }
            }
        }
        val db = CloudDb("x.firebaseio.com", "", fake)
        assertEquals(listOf("a", "b"), db.keys("q/k"))
        db.update("s/k", mapOf("meta" to mapOf("at" to 1L)))
        assertTrue(calls.last().startsWith("PATCH https://x.firebaseio.com/s/k.json?print=silent"))
        try { db.get("deny"); throw AssertionError() } catch (e: CloudDb.CloudException) { assertTrue(e.denied); assertTrue(e.message!!.contains("Permission denied")) }
    }

    // ---------------- qidiruv ----------------
    private data class P(val id: String, val name: String)

    private val products = listOf(
        P("101", "Qulupnay (Sublimit)"), P("102", "Pista Ahmadiy (AXT)"), P("103", "B Mayiz 134"), P("104", "B Mayiz 206"),
        P("105", "B Mayiz 120"), P("106", "B Osh Mayiz 57"), P("107", "Pista Akbariy (AK)"), P("108", "Pista Mumtoz (M)"),
        P("109", "Bodom Yongoq M"), P("110", "Bodom Yongoq S"), P("111", "Ёнғоқ мағзи"), P("112", "O'rik qoqi"), P("113", "Shokolad Alpen Gold"),
    )
    private val index = SearchIndex(products, { it.name }, { it.id })
    private fun names(q: String, o: SearchOptions = SearchOptions()) = index.search(q, o).map { it.item.name }

    @Test fun search_multiWordAnyOrder() {
        assertEquals("B Mayiz 134", names("mayiz 134").first())
        assertEquals("B Mayiz 134", names("134 mayiz").first())
        assertEquals(listOf("B Mayiz 120", "B Mayiz 134", "B Mayiz 206", "B Osh Mayiz 57").sorted(), names("b mayiz").sorted())
    }

    @Test fun search_numbersNeverFuzzy() {
        assertEquals(listOf("B Mayiz 134"), names("mayiz 13"))
        assertTrue(names("mayiz 999").isEmpty() || index.search("mayiz 999").all { it.approximate })
    }

    @Test fun search_cyrillicLatinAndApostrophes() {
        assertEquals("Ёнғоқ мағзи", names("yongoq magz").first())
        assertTrue(names("ёнғоқ").containsAll(listOf("Bodom Yongoq M", "Bodom Yongoq S", "Ёнғоқ мағзи")))
        assertEquals("O'rik qoqi", names("orik").first())
        assertEquals("O'rik qoqi", names("ўрик").first())
    }

    @Test fun search_codeExactWins() {
        assertEquals("Pista Mumtoz (M)", names("108").first())
    }

    @Test fun search_typosAndPhonetics() {
        assertEquals("Shokolad Alpen Gold", names("shakolad").first())
        assertTrue(names("pistta axmadiy").first().startsWith("Pista Ahmadiy"))
        assertTrue(index.search("pistta").first().approximate)
        assertTrue(names("kulupnay").contains("Qulupnay (Sublimit)"))
        assertFalse(names("shakolad", SearchOptions(fuzzy = false)).contains("Shokolad Alpen Gold"))
    }

    @Test fun search_keyboardLayout() {
        // "mayiz" ЙЦУКЕН tartibida yozilgan: m=ь a=ф y=н i=ш z=я
        assertTrue(names("ьфншя").contains("B Mayiz 134"))
        assertFalse(names("ьфншя", SearchOptions(layoutFix = false)).contains("B Mayiz 134"))
    }

    @Test fun search_highlightRanges() {
        val hit = index.search("mayiz 134").first()
        val name = hit.item.name
        val bold = hit.ranges.joinToString("|") { name.substring(it.first, it.last + 1) }
        assertEquals("Mayiz|134", bold)
        val cyr = index.search("магз").first()
        assertEquals("мағз", cyr.item.name.substring(cyr.ranges[0].first, cyr.ranges[0].last + 1))
    }

    @Test fun search_partialFallbackIsApproximate() {
        val r = index.search("pista nimadir")
        assertTrue(r.isNotEmpty())
        assertTrue(r.all { it.approximate })
    }

    @Test fun search_popularityBreaksTies() {
        val pop = mapOf("110" to 20.0)
        val r = index.search("bodom yongoq", popularity = { pop[it.id] ?: 0.0 })
        assertEquals("Bodom Yongoq S", r.first().item.name)
    }

    @Test fun distance_osa() {
        assertEquals(1, Distance.osa("pista", "pitsa"))
        assertEquals(2, Distance.osa("kitten", "sitten", 5) + 1)
        assertEquals(3, Distance.osa("abc", "xyzw", 2))
    }

    @Test fun search_largeIndexIsFast() {
        val big = (1..5000).map { P("$it", "Tovar ${it % 97} Mahsulot ${it % 13} nomi $it") }
        val ix = SearchIndex(big, { it.name }, { it.id })
        val t0 = System.nanoTime()
        repeat(20) { ix.search("mahsulot 7 nom") }
        val ms = (System.nanoTime() - t0) / 1_000_000 / 20
        assertTrue("har qidiruv $ms ms", ms < 150)
    }
}
