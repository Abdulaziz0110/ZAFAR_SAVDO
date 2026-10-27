package com.ilxomjon.dur_novvot_dokon.core

import com.ilxomjon.dur_novvot_dokon.domain.Currency
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Pul va miqdorlarni o'qish/chiqarish. Server bilan almashinuvda "." o'nlik ajratgich ishlatiladi. */
object Money {
    private val symbols = DecimalFormatSymbols(Locale.US).apply { groupingSeparator = ' ' }

    /** "1 250 000,50" / "12.5" / "" -> Double. Noto'g'ri bo'lsa 0. */
    fun parse(raw: String?): Double {
        if (raw == null) return 0.0
        val s = raw.filter { !it.isWhitespace() && it != '\u00A0' }.replace(',', '.')
        if (s.isEmpty()) return 0.0
        return s.toDoubleOrNull() ?: 0.0
    }

    /**
     * Foydalanuvchi YOZGAN summa/miqdor (v2.3). Eski [parse] dan farqi: minglik ajratgichlarni tushunadi va
     * tushunmagan matnni jimgina 0 qilmaydi — null qaytaradi (ekranda xato ko'rsatiladi).
     *   "1 250 000" "1,250,000" "1.250.000" "1'250'000" -> 1250000
     *   "12,5" "12.5" -> 12.5      "1 250 000,50" "1.250.000,50" "1,250,000.50" -> 1250000.5
     *   "" -> 0.0 (bo'sh maydon = 0)   "abc", "1,2,3.4.5" -> null
     * Bitta vergul yoki bitta nuqta doim o'nlik ajratgich (eski ilova bilan bir xil: "1,250" = 1.25).
     */
    fun parseUser(raw: String?): Double? {
        if (raw == null) return 0.0
        var s = raw.filter { !it.isWhitespace() && it != '\u00A0' && it != '\u202F' && it != '\'' && it != '’' }
        if (s.isEmpty()) return 0.0
        var negative = false
        if (s.startsWith("-")) { negative = true; s = s.substring(1) }
        if (s.isEmpty() || s.any { !(it.isDigit() && it.code < 128) && it != ',' && it != '.' }) return null
        val commas = s.count { it == ',' }
        val dots = s.count { it == '.' }
        val normalized: String = when {
            commas == 0 && dots == 0 -> s
            commas > 0 && dots > 0 -> {
                // Oxirgisi o'nlik ajratgich, ikkinchisi faqat minglik bo'lishi mumkin.
                val dec = if (s.lastIndexOf(',') > s.lastIndexOf('.')) ',' else '.'
                val th = if (dec == ',') '.' else ','
                if (s.count { it == dec } != 1) return null
                val intPart = s.substringBefore(dec)
                if (!groupsOk(intPart, th)) return null
                intPart.replace(th.toString(), "") + "." + s.substringAfter(dec)
            }
            commas > 1 -> if (groupsOk(s, ',')) s.replace(",", "") else return null
            dots > 1 -> if (groupsOk(s, '.')) s.replace(".", "") else return null
            commas == 1 -> s.replace(',', '.')
            else -> s
        }
        if (normalized.startsWith(".") || normalized.endsWith(".")) return null
        val v = normalized.toDoubleOrNull() ?: return null
        if (v.isNaN() || v.isInfinite()) return null
        return if (negative) -v else v
    }

    /** "1,250,000" kabi guruhlar: birinchisi 1–3 raqam, qolganlari aynan 3 tadan. */
    private fun groupsOk(s: String, sep: Char): Boolean {
        val parts = s.split(sep)
        if (parts.size < 2) return parts.firstOrNull()?.isNotEmpty() == true
        if (parts[0].isEmpty() || parts[0].length > 3) return false
        return parts.drop(1).all { it.length == 3 }
    }

    fun round2(v: Double): Double =
        if (v.isNaN() || v.isInfinite()) 0.0 else BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).toDouble()

    /** Ekranda ko'rsatish uchun: guruhlangan, kerak bo'lsa 2 xonali kasr. */
    fun display(v: Double, maxDecimals: Int = 2): String {
        val pattern = if (maxDecimals <= 0) "#,##0" else "#,##0." + "#".repeat(maxDecimals)
        return DecimalFormat(pattern, symbols).format(round2(v))
    }

    fun format(v: Double, currency: Currency): String = when (currency) {
        Currency.SOM -> display(v, 2)
        Currency.USD -> DecimalFormat("#,##0.00", symbols).format(round2(v))
    }

    /** Serverga yuboriladigan narx/summa: guruhsiz, ortiqcha nollarsiz ("126750", "12.5"). */
    fun plain(v: Double): String {
        val bd = BigDecimal.valueOf(round2(v)).stripTrailingZeros()
        return if (bd.scale() < 0) bd.setScale(0).toPlainString() else bd.toPlainString()
    }

    /** Serverga yuboriladigan miqdor: avvalgi ilova formati ("5.0", "2.5"). */
    fun qty(v: Double): String {
        val s = BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        return if (s.contains('.')) s else "$s.0"
    }

    /** Miqdorni ekranda ko'rsatish ("5", "2.5"). */
    fun qtyDisplay(v: Double): String =
        BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}
