package uz.imsoft.shared.label

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextLabelsTest {
    @Test
    fun cleanUnifiesLineEndingsAndTrims() {
        assertEquals("Salom\nDunyo", TextLabels.clean("  \r\nSalom  \r\nDunyo \r\n\n"))
        assertEquals("a b", TextLabels.clean("a\u00A0b"))
        assertEquals("", TextLabels.clean("   \n  "))
    }

    @Test
    fun cleanLimitsLength() {
        val long = "x".repeat(TextLabels.MAX_CHARS + 50)
        assertEquals(TextLabels.MAX_CHARS, TextLabels.clean(long).length)
    }

    @Test
    fun copiesAreClamped() {
        assertEquals(1, TextLabels.copies(null))
        assertEquals(1, TextLabels.copies(0))
        assertEquals(1, TextLabels.copies(-5))
        assertEquals(7, TextLabels.copies(7))
        assertEquals(TextLabels.MAX_COPIES, TextLabels.copies(1000))
    }

    @Test
    fun summaryJoinsLinesAndShortens() {
        assertEquals("Olma \u00B7 5 000 so'm", TextLabels.summary("Olma\n\n  5 000 so'm  "))
        val s = TextLabels.summary("a".repeat(100), 20)
        assertEquals(20, s.length)
        assertTrue(s.endsWith("\u2026"))
    }
}
