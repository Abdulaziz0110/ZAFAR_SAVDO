package uz.imsoft.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v5.0: yozayotganda minglik ajratish. */
class NumTextTest {
    private fun g(s: String) = NumText.group(s)

    @Test fun groups() {
        assertEquals("", g(""))
        assertEquals("7", g("7"))
        assertEquals("123", g("123"))
        assertEquals("1 234", g("1234"))
        assertEquals("12 000", g("12000"))
        assertEquals("1 250 000", g("1250000"))
        assertEquals("12 000", g("12 000"))
        assertEquals("12 000", g("12000"))
        assertEquals("1 250 000,5", g("1250000,5"))
        assertEquals("12 000.75", g("12000.75"))
        assertEquals("0,5", g("0,5"))
        assertEquals("0", g("0"))
        assertEquals("7", g("007"))
        assertEquals("-1 234", g("-1234"))
        assertEquals("-123", g("-123"))
        assertEquals(",5", g(",5"))
        assertEquals("12 345", g("12a3b45"))
        assertEquals("12,34", g("12,3.4"))
    }

    @Test fun cursorKeepsPlace() {
        // "1234|" -> "1 234" : kursor oxirida qoladi
        val old = "1234"
        val n = NumText.significantBefore(old, 4)
        assertEquals(4, n)
        assertEquals(5, NumText.posAfter("1 234", 4))
        // "1 2|34" (3 ta ma'noli belgi oldida) -> yangi matnda ham 3 tadan keyin
        assertEquals(2, NumText.significantBefore("1 234", 3))
        assertEquals(1, NumText.posAfter("1 234", 1))
    }

    @Test fun parseIgnoresGroupingAndNbsp() {
        assertEquals(12000.0, NumText.parse("12 000"), 0.0001)
        assertEquals(12000.0, NumText.parse("12\u00A0000"), 0.0001)
        assertEquals(12000.5, NumText.parse("12 000,5"), 0.0001)
        assertEquals(-3500.0, NumText.parse("-3 500"), 0.0001)
        assertEquals(0.0, NumText.parse(""), 0.0001)
        assertEquals(0.0, NumText.parse("salom"), 0.0001)
        assertNull(NumText.parseOrNull("12a"))
        assertNull(NumText.parseOrNull(null))
    }
}
