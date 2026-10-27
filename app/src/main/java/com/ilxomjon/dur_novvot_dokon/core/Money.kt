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
