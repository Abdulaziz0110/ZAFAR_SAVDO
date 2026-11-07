package com.ilxomjon.dur_novvot_dokon.core

import org.junit.Assert.assertEquals
import org.junit.Test
import uz.imsoft.shared.NumText

/** v5.0: ajratib ko'rsatilgan matn baribir to'g'ri o'qilishi kerak (Money.parseUser bo'shliqni tushunadi). */
class NumTextRoundTripTest {
    @Test fun groupedTextParsesBack() {
        val cases = mapOf(
            "12000" to 12000.0, "1250000" to 1250000.0, "1250000,5" to 1250000.5,
            "12000.75" to 12000.75, "0,5" to 0.5, "-1234" to -1234.0, "7" to 7.0,
        )
        for ((raw, want) in cases) {
            val shown = NumText.group(raw)
            assertEquals(shown, want, Money.parseUser(shown) ?: Double.NaN, 0.0001)
        }
    }

    @Test fun emptyStaysEmpty() {
        assertEquals(0.0, Money.parseUser(NumText.group("")) ?: Double.NaN, 0.0)
    }
}
