package com.ilxomjon.dur_novvot_dokon.data.remote

import com.ilxomjon.dur_novvot_dokon.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.Locale

/**
 * Serverga ketadigan matnlar (tur 1, 3, 4, 5) fixture bilan BAYT-BAYT solishtiriladi, har locale'da.
 * Fixture = v2.2 ning hozirgi chiqishi (golden master), maydon tartibi PDF va eski APK bilan tekshirilgan.
 * Bu test yiqilsa — protokol o'zgargan: ataylab bo'lmasa, o'zgarishni qaytaring (C1).
 */
@RunWith(Parameterized::class)
class ProtocolFixtureTest(private val locale: Locale) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun locales(): List<Locale> = Fixtures.LOCALES

        private val expected = Fixtures.load("protocol")
    }

    @Test
    fun everyCaseHasFixtureAndViceVersa() {
        assertEquals(expected.keys.sorted(), ProtocolCases.cases.keys.sorted())
    }

    @Test
    fun outputMatchesFixtureByteForByte() {
        Fixtures.withLocale(locale) {
            for ((key, make) in ProtocolCases.cases) {
                val actual = make()
                val want = expected.getValue(key)
                assertEquals("[$locale] $key", want, actual)
                assertTrue("[$locale] $key baytlari", want.toByteArray(Charsets.UTF_8).contentEquals(actual.toByteArray(Charsets.UTF_8)))
            }
        }
    }

    /** Fixture'dan mustaqil tuzilma tekshiruvi (PDF 6-bo'lim). */
    @Test
    fun orderStructure() {
        Fixtures.withLocale(locale) {
            for ((key, make) in ProtocolCases.cases) {
                if (!key.startsWith("order.")) continue
                val s = make()
                val tilde = s.indexOf('~')
                assertTrue("$key: '~' bitta", tilde > 0 && s.indexOf('~', tilde + 1) < 0)
                val head = s.substring(0, tilde).split('^')
                assertEquals("$key: sarlavha 12 maydon", 12, head.size)
                assertTrue("$key: vaqt dd.MM.yyyy HH:mm:ss", head[2].matches(Regex("""\d{2}\.\d{2}\.\d{4} \d{2}:\d{2}:\d{2}""")))
                assertTrue("$key: valyuta 0/1", head[3] in setOf("0", "1"))
                assertTrue("$key: savdoTuri 0/1", head[4] in setOf("0", "1"))
                assertTrue("$key: rejim 1/2", head[10] in setOf("1", "2"))
                assertTrue("$key: printer 1/2/3", head[11] in setOf("1", "2", "3"))
                val body = s.substring(tilde + 1)
                assertTrue("$key: tovarlar '@' bilan tugaydi", body.endsWith("@"))
                for (item in body.removeSuffix("@").split('@')) {
                    val f = item.split('^')
                    assertEquals("$key: tovar^soni^narx", 3, f.size)
                    assertTrue("$key: miqdorda '.' bor (eski '5.0' format)", f[1].contains('.'))
                    assertTrue("$key: raqamlar ASCII", (f[1] + f[2]).all { it in "0123456789.-" })
                }
                for (i in 5..9) assertTrue("$key: maydon $i ASCII raqam", head[i].all { it in "0123456789.-" })
            }
        }
    }

    @Test
    fun paymentStructure() {
        Fixtures.withLocale(locale) {
            for ((key, make) in ProtocolCases.cases) {
                if (!key.startsWith("payment.")) continue
                val s = make()
                assertTrue("$key: '~' bilan tugaydi", s.endsWith("~"))
                val f = s.removeSuffix("~").split('^')
                assertEquals("$key: 11 maydon", 11, f.size)
                assertEquals("$key: 4-maydon doim \"0\" (eski DialogTolov.vid)", "0", f[3])
                assertEquals("$key: 3- va 8-maydon bir xil naqd (eski ilova)", f[2], f[7])
                assertTrue("$key: printer 1/2/3", f[10] in setOf("1", "2", "3"))
            }
        }
    }
}
