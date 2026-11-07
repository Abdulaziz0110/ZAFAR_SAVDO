package uz.imsoft.shared

/**
 * v5.0: foydalanuvchi YOZAYOTGAN son — minglikni bo'shliq bilan ajratib ko'rsatish ("12000" -> "12 000").
 * Sof mantiq (testlanadi), ekran qismi — android/NumInput.kt. O'qish Money.parseUser bilan: u bo'shliqni tushunadi.
 */
object NumText {
    const val GROUP = ' '

    /** Birinchi ',' yoki '.' — o'nlik ajratgich (Money.parseUser bilan bir xil qoida). Qolgan belgilar tashlanadi. */
    fun group(raw: String): String {
        if (raw.isEmpty()) return ""
        val minus = raw.startsWith("-")
        val body = if (minus) raw.substring(1) else raw
        var sep = '\u0000'
        val intPart = StringBuilder()
        val frac = StringBuilder()
        for (ch in body) {
            when {
                ch.isDigit() && ch.code < 128 -> if (sep == '\u0000') intPart.append(ch) else frac.append(ch)
                (ch == ',' || ch == '.') && sep == '\u0000' -> sep = ch
            }
        }
        val out = StringBuilder()
        if (minus) out.append('-')
        // Oldidagi ortiqcha nollar tashlanadi ("007" -> "7"), lekin "0" va "0,5" saqlanadi
        val digits = if (intPart.isEmpty()) "" else intPart.toString().trimStart('0').ifEmpty { "0" }
        val first = digits.length % 3
        if (first > 0) out.append(digits, 0, first)
        var i = first
        while (i < digits.length) {
            if (out.isNotEmpty() && out.last() != '-') out.append(GROUP)
            out.append(digits, i, i + 3)
            i += 3
        }
        if (sep != '\u0000') out.append(sep).append(frac)
        return out.toString()
    }

    /**
     * Foydalanuvchi yozgan sonni o'qiydi: minglik ajratgichlari (oddiy bo'shliq, NBSP, "narrow" bo'shliq va
     * apostrof) tashlanadi, vergul nuqtaga aylanadi. Noto'g'ri bo'lsa — null.
     */
    fun parseOrNull(s: String?): Double? {
        val t = s.orEmpty()
        val b = StringBuilder()
        var sep = false
        for (ch in t) {
            when {
                ch.isDigit() && ch.code < 128 -> b.append(ch)
                ch == '-' && b.isEmpty() -> b.append('-')
                (ch == ',' || ch == '.') && !sep -> { sep = true; b.append('.') }
                ch == ' ' || ch == '\u00A0' || ch == '\u202F' || ch == '\u2009' || ch == '\'' || ch == '\t' -> {}
                else -> return null
            }
        }
        val v = b.toString().toDoubleOrNull() ?: return null
        return if (v.isNaN() || v.isInfinite()) null else v
    }

    /** [parseOrNull], lekin xato bo'lsa 0. */
    fun parse(s: String?): Double = parseOrNull(s) ?: 0.0

    /** [pos] gacha nechta "ma'noli" belgi (raqam yoki o'nlik ajratgich) borligi — kursorni joyida saqlash uchun. */
    fun significantBefore(s: String, pos: Int): Int {
        var n = 0
        for (i in 0 until pos.coerceIn(0, s.length)) {
            val c = s[i]
            if ((c.isDigit() && c.code < 128) || c == ',' || c == '.' || c == '-') n++
        }
        return n
    }

    /** [n] ta ma'noli belgidan keyingi o'rin. */
    fun posAfter(s: String, n: Int): Int {
        if (n <= 0) return 0
        var seen = 0
        for (i in s.indices) {
            val c = s[i]
            if ((c.isDigit() && c.code < 128) || c == ',' || c == '.' || c == '-') {
                seen++
                if (seen == n) return i + 1
            }
        }
        return s.length
    }
}
