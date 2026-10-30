package uz.imsoft.shared.barcode

/**
 * Shtrix kodni CHIZISH uchun (narx yorlig'i): EAN-13, EAN-8 va Code 128 — modullar ro'yxati (true = qora).
 * Android'ga bog'liq emas; chizish [uz.imsoft.shared.android.LabelBitmap] da. Har modul butun nuqta bo'lib chiziladi.
 */
object BarcodeEncode {
    enum class Kind { EAN13, EAN8, CODE128 }

    /** Chiziladigan kod: turi, raqamlari (inson o'qiydigan yozuv) va modullar. */
    class Symbol(val kind: Kind, val text: String, val modules: BooleanArray) {
        /** Yon tomondagi bo'sh joy (modullarda) — skaner o'qishi uchun shart. */
        val quiet: Int get() = if (kind == Kind.CODE128) 10 else 9
    }

    private val L = arrayOf("0001101", "0011001", "0010011", "0111101", "0100011", "0110001", "0101111", "0111011", "0110111", "0001011")
    private val G = arrayOf("0100111", "0110011", "0011011", "0100001", "0011101", "0111001", "0000101", "0010001", "0001001", "0010111")
    private val R = arrayOf("1110010", "1100110", "1101100", "1000010", "1011100", "1001110", "1010000", "1000100", "1001000", "1110100")
    private val PARITY = arrayOf("LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG", "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL")

    private fun bits(s: String, out: StringBuilder) { out.append(s) }

    fun ean13(code: String): Symbol {
        require(code.length == 13 && code.all { it.isDigit() } && Ean.checkOk(code)) { "ean13: $code" }
        val d = code.map { it - '0' }
        val sb = StringBuilder("101")
        val par = PARITY[d[0]]
        for (i in 1..6) bits(if (par[i - 1] == 'L') L[d[i]] else G[d[i]], sb)
        sb.append("01010")
        for (i in 7..12) bits(R[d[i]], sb)
        sb.append("101")
        return Symbol(Kind.EAN13, code, sb.map { it == '1' }.toBooleanArray())
    }

    fun ean8(code: String): Symbol {
        require(code.length == 8 && code.all { it.isDigit() } && Ean.checkOk(code)) { "ean8: $code" }
        val d = code.map { it - '0' }
        val sb = StringBuilder("101")
        for (i in 0..3) bits(L[d[i]], sb)
        sb.append("01010")
        for (i in 4..7) bits(R[d[i]], sb)
        sb.append("101")
        return Symbol(Kind.EAN8, code, sb.map { it == '1' }.toBooleanArray())
    }

    /** Code 128 qiymatlari uchun chiziq/bo'shliq kengliklari (0..106; 106 — STOP). */
    private val C128 = arrayOf(
        "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212", "221213",
        "221312", "231212", "112232", "122132", "122231", "113222", "123122", "123221", "223211", "221132",
        "221231", "213212", "223112", "312131", "311222", "321122", "321221", "312212", "322112", "322211",
        "212123", "212321", "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
        "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121", "313121", "211331",
        "231131", "213113", "213311", "213131", "311123", "311321", "331121", "312113", "312311", "332111",
        "314111", "221411", "431111", "111224", "111422", "121124", "121421", "141122", "141221", "112214",
        "112412", "122114", "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
        "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112", "421211", "212141",
        "214121", "412121", "111143", "111341", "131141", "114113", "114311", "411113", "411311", "113141",
        "114131", "311141", "411131", "211412", "211214", "211232", "2331112",
    )
    private const val START_B = 104
    private const val START_C = 105
    private const val CODE_B = 100
    private const val CODE_C = 99

    /** Code 128: raqamlar juft-juft (C to'plami), qolgan belgilar B to'plamida. Faqat ASCII 32..126. */
    fun code128(text: String): Symbol {
        require(text.isNotEmpty() && text.all { it.code in 32..126 }) { "code128: $text" }
        val vals = ArrayList<Int>()
        var i = 0
        fun digitsRun(from: Int): Int { var k = from; while (k < text.length && text[k].isDigit()) k++; return k - from }
        var set: Int
        if (digitsRun(0) >= 4 || (digitsRun(0) == text.length && text.length % 2 == 0)) { vals += START_C; set = 'C'.code } else { vals += START_B; set = 'B'.code }
        while (i < text.length) {
            val run = digitsRun(i)
            if (set == 'C'.code) {
                if (run >= 2) { vals += (text[i] - '0') * 10 + (text[i + 1] - '0'); i += 2; continue }
                vals += CODE_B; set = 'B'.code; continue
            }
            // B to'plami: oldinda 4+ (yoki oxirigacha juft) raqam bo'lsa — C ga o'tish
            if (run >= 4 && (run % 2 == 0 || run >= 6)) {
                if (run % 2 == 1) { vals += text[i].code - 32; i++ }
                vals += CODE_C; set = 'C'.code; continue
            }
            vals += text[i].code - 32; i++
        }
        var sum = vals[0]
        for (k in 1 until vals.size) sum += vals[k] * k
        vals += sum % 103
        vals += 106
        val sb = StringBuilder()
        for (v in vals) {
            val w = C128[v]
            for ((k, c) in w.withIndex()) repeat(c - '0') { sb.append(if (k % 2 == 0) '1' else '0') }
        }
        return Symbol(Kind.CODE128, text, sb.map { it == '1' }.toBooleanArray())
    }

    /**
     * Do'kondagi shtrix koddan chiziladigan belgini tanlaydi (1C bilan bir xil natija):
     *  - 13 xona, tekshiruv to'g'ri — EAN-13; 12 xona UPC-A — EAN-13 (oldiga 0);
     *  - 12 xona, tekshiruvsiz (o'zimiz yopishtirgan "250000002437") — EAN-13, oxiriga tekshiruv raqami (do'kon yorlig'idagidek);
     *  - 8 xona to'g'ri — EAN-8; boshqasi — Code 128 (raqamlar o'zgarmaydi).
     * Tarozi kodi (7 xona, 2 bilan boshlanadi) — null: bunday tovarga PLU yorlig'i chiqariladi.
     */
    fun forShopCode(raw: String): Symbol? {
        val t = raw.trim().filterNot { it == ' ' || it == '\u00A0' }
        if (t.isEmpty()) return null
        val n = Ean.numeric(t)
        if (n == null) return if (t.all { it.code in 32..126 }) code128(t) else null
        if (n.length == 7 && n.startsWith("2")) return null
        return when {
            n.length == 13 && Ean.checkOk(n) -> ean13(n)
            n.length == 12 && Ean.checkOk(n) -> ean13("0$n")
            n.length == 12 -> ean13(n + Ean.checkDigit(n))
            n.length == 8 && Ean.checkOk(n) -> ean8(n)
            n.length < 12 && n.length > 8 && Ean.checkOk(n.padStart(13, '0')) -> ean13(n.padStart(13, '0'))
            n.length == 14 && Ean.checkOk(n) && n.startsWith("0") -> ean13(n.substring(1))
            else -> code128(n)
        }
    }
}
