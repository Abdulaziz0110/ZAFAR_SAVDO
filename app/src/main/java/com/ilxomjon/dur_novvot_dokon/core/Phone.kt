package com.ilxomjon.dur_novvot_dokon.core

/**
 * Serverdagi telefon formati: "+99890 000-00-00" (avvalgi ilovadagi niqob bilan bir xil).
 * Foydalanuvchi faqat raqam yozadi, format avtomatik qo'yiladi.
 */
object Phone {
    private const val PREFIX = "+998"

    /** 998 dan keyingi 9 raqamgacha (operator kodi + 7 raqam). */
    fun digitsOf(input: String): String {
        var d = input.filter { it.isDigit() }
        if (d.startsWith("998")) d = d.substring(3)
        return d.take(9)
    }

    fun format(input: String): String {
        val d = digitsOf(input)
        if (d.isEmpty()) return ""
        val sb = StringBuilder(PREFIX)
        sb.append(d.take(2))
        if (d.length > 2) sb.append(' ').append(d.substring(2, minOf(5, d.length)))
        if (d.length > 5) sb.append('-').append(d.substring(5, minOf(7, d.length)))
        if (d.length > 7) sb.append('-').append(d.substring(7, d.length))
        return sb.toString()
    }

    fun isComplete(input: String): Boolean = digitsOf(input).length == 9
}
