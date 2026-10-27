package com.ilxomjon.dur_novvot_dokon.core

import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.Locale

/**
 * Money'ning hozirgi xatti-harakati (characterization). P10: Money'ni Long/BigDecimal'ga o'tkazishdan OLDIN
 * shu fixture'lar yashil bo'lishi kerak va o'tkazishdan KEYIN ham o'zgarmasligi kerak.
 * "KNOWN" bilan belgilangan qatorlar — auditda topilgan muammolar (M-1); ularni tuzatish alohida, ataylab qilinadi.
 */
@RunWith(Parameterized::class)
class MoneyFixtureTest(private val locale: Locale) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun locales(): List<Locale> = Fixtures.LOCALES

        private val expected = Fixtures.load("money")

        val cases: Map<String, () -> String> = linkedMapOf(
            "qty.5" to { Money.qty(5.0) },
            "qty.2.5" to { Money.qty(2.5) },
            "qty.third" to { Money.qty(1.0 / 3.0) },
            "qty.big" to { Money.qty(1e7) },
            "qty.tiny" to { Money.qty(0.0001) },
            "qty.halfup" to { Money.qty(2.0005) },
            "qty.zero" to { Money.qty(0.0) },
            "plain.int" to { Money.plain(126750.0) },
            "plain.float.noise" to { Money.plain(0.1 + 0.2) },
            "plain.big" to { Money.plain(1e12) },
            "plain.round" to { Money.plain(12.345) },
            "plain.negative" to { Money.plain(-5.0) },
            "plain.zero" to { Money.plain(0.0) },
            "plain.nan" to { Money.plain(Double.NaN) },
            "display.som" to { Money.display(1250000.5) },
            "display.int" to { Money.display(1250000.0) },
            "format.usd" to { Money.format(1234.5, Currency.USD) },
            "format.som" to { Money.format(1234.5, Currency.SOM) },
            "qtyDisplay" to { Money.qtyDisplay(5.0) },
            "parse.spaces.comma" to { Money.parse("1 250 000,50").toString() },
            "parse.nbsp" to { Money.parse("1 250").toString() },
            "parse.dot" to { Money.parse("12.5").toString() },
            "parse.empty" to { Money.parse("").toString() },
            "parse.null" to { Money.parse(null).toString() },
            "parse.garbage" to { Money.parse("abc").toString() },
            "parse.KNOWN.comma.thousands" to { Money.parse("1,250,000").toString() },
            "parse.KNOWN.dot.thousands" to { Money.parse("1.250.000").toString() },
            "round2.half" to { Money.round2(2.675).toString() },
            "round2.inf" to { Money.round2(Double.POSITIVE_INFINITY).toString() },
        )
    }

    @Test
    fun everyCaseHasFixtureAndViceVersa() {
        assertEquals(expected.keys.sorted(), cases.keys.sorted())
    }

    @Test
    fun outputMatchesFixture() {
        Fixtures.withLocale(locale) {
            for ((key, make) in cases) assertEquals("[$locale] $key", expected.getValue(key), make())
        }
    }
}
