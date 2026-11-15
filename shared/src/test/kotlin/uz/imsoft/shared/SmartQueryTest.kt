package uz.imsoft.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v5.3: bitta qidiruv maydonida kod, nom va narx. */
class SmartQueryTest {
    private class P(val code: String, val name: String, val price: Double)

    private val all = listOf(
        P("1042", "Fuse Tea 0.5 L", 9000.0),
        P("95", "Pista Eron", 95000.0),
        P("950", "Bodom", 104200.0),
        P("7", "Coca Cola 1 L", 12000.0),
        P("8", "Coca Cola 1.5 L", 15000.0),
        P("9", "Pista Amerika", 110000.0),
        P("10", "Tekin namuna", 0.0),
    )

    private fun run(q: String): List<String> {
        val p = SmartQuery.parse(q)
        val byName = if (p.text.isEmpty()) emptyList() else all.filter { x -> p.text.lowercase().split(' ').all { x.name.lowercase().contains(it) } }
        return SmartQuery.refine(p, byName, all, { it.code }, { it.name }, { it.price }).map { it.code }
    }

    @Test fun parseSplitsWordsAndNumbers() {
        val p = SmartQuery.parse("  pista   95000 ")
        assertEquals("pista", p.text)
        assertEquals(listOf("95000"), p.numbers)
        assertTrue(p.mixed)
        assertTrue(SmartQuery.parse("1042").onlyNumbers)
        assertFalse(SmartQuery.parse("pista").mixed)
    }

    @Test fun spacedSumIsOneNumber() {
        assertEquals(listOf("95000"), SmartQuery.parse("95 000").numbers)
        assertEquals(listOf("1250000"), SmartQuery.parse("1 250 000").numbers)
        // "cola 1 500" — 1500 so'm; lekin "1042 950" — ikkita alohida son
        assertEquals(listOf("1500"), SmartQuery.parse("cola 1 500").numbers)
        assertEquals(listOf("1042", "950"), SmartQuery.parse("1042 950").numbers)
    }

    @Test fun mixedTokensStayInName() {
        val p = SmartQuery.parse("7up 0.5l")
        assertEquals("7up 0.5l", p.text)
        assertTrue(p.numbers.isEmpty())
    }

    @Test fun digitsFindCodeFirstThenPrice() {
        // "95": kodi 95 (aynan), keyin narxi 95 000 emas — u shu tovarning o'zi; kodi 950 (boshlanadi)
        assertEquals(listOf("95", "950"), run("95"))
        // "1042": kodi 1042, keyin narxi 104 200 (Bodom)
        assertEquals(listOf("1042", "950"), run("1042"))
        // "9000": kod yo'q — narxi aynan 9 000
        assertEquals(listOf("1042"), run("9000"))
    }

    @Test fun exactPriceBeforePrefixPrice() {
        // "12": kod yo'q; narxi 12 000 (boshlanadi) — bitta
        assertEquals(listOf("7"), run("12"))
        // "15000": aynan
        assertEquals(listOf("8"), run("15000"))
    }

    @Test fun nameWithPrice() {
        assertEquals(listOf("95"), run("pista 95000"))
        assertEquals(listOf("95"), run("95 pista"))
        assertEquals(listOf("9"), run("pista 11"))
        assertTrue(run("pista 777").isEmpty())
    }

    @Test fun numberMayBePartOfName() {
        // "cola 1" — narxi 1 dan boshlanadiganlar (12 000, 15 000) ham, nomida "1" borlar ham
        assertEquals(listOf("7", "8"), run("cola 1"))
        // "cola 15" — narxi 15 000 (birinchi); "1 L" nomida 15 yo'q
        assertEquals(listOf("8"), run("cola 15"))
    }

    @Test fun zeroPriceNeverMatches() {
        assertFalse(SmartQuery.priceMatches(0.0, "0"))
    }

    @Test fun leadingZerosInCode() {
        assertEquals(2, SmartQuery.codeRank("0007", "7"))
        assertEquals(1, SmartQuery.codeRank("1042", "10"))
        assertEquals(0, SmartQuery.codeRank("1042", "42"))
    }
}
