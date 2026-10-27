package com.ilxomjon.dur_novvot_dokon.testutil

import java.util.Locale
import java.util.TimeZone

/**
 * Fixture fayllari: `src/test/resources/fixtures/<nom>.txt`.
 * Format: har qatorda `kalit<TAB>kutilgan qiymat`. `#` bilan boshlangan va bo'sh qatorlar o'tkazib yuboriladi.
 * Kutilgan qiymat bayt-bayt solishtiriladi (fayl UTF-8). Qiymat ichida TAB va yangi qator bo'lmaydi.
 */
object Fixtures {
    fun load(name: String): Map<String, String> {
        val stream = Fixtures::class.java.classLoader!!.getResourceAsStream("fixtures/$name.txt")
            ?: error("Fixture topilmadi: fixtures/$name.txt")
        val out = LinkedHashMap<String, String>()
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (raw in lines) {
                if (raw.isBlank() || raw.startsWith("#")) continue
                val tab = raw.indexOf('\t')
                require(tab > 0) { "Noto'g'ri fixture qatori ($name): $raw" }
                val key = raw.substring(0, tab)
                require(key !in out) { "Takroriy kalit ($name): $key" }
                out[key] = raw.substring(tab + 1)
            }
        }
        return out
    }

    /** Protokol chiqishi qurilma tiliga bog'liq bo'lmasligi kerak: testlar shu locale'larning har birida bajariladi. */
    val LOCALES: List<Locale> = listOf(
        Locale.forLanguageTag("uz-Latn-UZ"),
        Locale.forLanguageTag("uz-Cyrl-UZ"),
        Locale.forLanguageTag("ru-RU"),
        Locale.US,
        Locale.forLanguageTag("ar-EG"), // arabcha raqamlar bilan xavfli locale
    )

    /** Default locale va vaqt zonasini vaqtincha almashtiradi. */
    inline fun <T> withLocale(locale: Locale, block: () -> T): T {
        val oldLocale = Locale.getDefault()
        val oldTz = TimeZone.getDefault()
        try {
            Locale.setDefault(locale)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tashkent"))
            return block()
        } finally {
            Locale.setDefault(oldLocale)
            TimeZone.setDefault(oldTz)
        }
    }
}
