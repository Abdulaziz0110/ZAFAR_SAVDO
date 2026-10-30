package com.ilxomjon.dur_novvot_dokon.core

import com.ilxomjon.dur_novvot_dokon.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Audit M-1: "1,250,000" endi jimgina 0 bo'lmaydi. Eski [Money.parse] (fixture) o'zgarmagan. */
class MoneyParseUserTest {
    private fun ok(input: String, want: Double) = assertEquals(input, want, Money.parseUser(input)!!, 1e-9)

    @Test fun thousands() {
        ok("1 250 000", 1250000.0); ok("1,250,000", 1250000.0); ok("1.250.000", 1250000.0); ok("1'250'000", 1250000.0)
        ok("1\u00A0250\u00A0000", 1250000.0); ok("12,500,000", 12500000.0)
    }

    @Test fun decimals() {
        ok("12,5", 12.5); ok("12.5", 12.5); ok("1 250 000,50", 1250000.5); ok("1.250.000,50", 1250000.5); ok("1,250,000.50", 1250000.5)
        ok("0,75", 0.75); ok("1,250", 1.25) // bitta vergul — o'nlik (eski ilova bilan bir xil)
    }

    @Test fun emptyAndPlain() { ok("", 0.0); ok("  ", 0.0); ok("5000", 5000.0); ok("-250", -250.0); assertEquals(0.0, Money.parseUser(null)!!, 0.0) }

    @Test fun rejected() {
        for (bad in listOf("abc", "12a", "1,2,3", "1.2.3", "1,25,000", "1.250,000,5", ".", ",", "1.", "--5", "١٢٣")) assertNull(bad, Money.parseUser(bad))
    }

    @Test fun localeIndependent() {
        for (l in Fixtures.LOCALES) Fixtures.withLocale(l) { ok("1,250,000.50", 1250000.5); ok("12,5", 12.5) }
    }

    @Test fun oldParseUnchanged() {
        // Characterization (fixture) bilan bir xil: eski funksiya tegilmagan.
        assertEquals(0.0, Money.parse("1,250,000"), 0.0)
        assertEquals(1250000.5, Money.parse("1 250 000,50"), 0.0)
    }
}
