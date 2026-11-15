package uz.imsoft.shared

/**
 * v5.3: qidiruv matnini tushunish — bitta maydonda KOD, NOM va NARX.
 *
 *  * faqat raqam ("1042")            — avval KODI shu bo'lgan tovar, keyin narxi shu raqamdan boshlanadiganlar;
 *  * harflar ("pista")               — nomi bo'yicha;
 *  * harf + raqam ("pista 95000" yoki "95000 pista") — nomi mos VA narxi shu raqamdan boshlanadigan
 *    (yoki nomida shu raqam bor: "cola 1" -> "Coca Cola 1 L").
 *
 * Narx — savdoda HOZIR TANLANGAN narx turi bo'yicha (1/2/3/4-narx) solishtiriladi; buni chaqiruvchi beradi.
 * "95 000" kabi bo'shliq bilan yozilgan summa bitta son deb olinadi.
 */
object SmartQuery {
    class Parsed(
        /** Nom bo'yicha qidiriladigan qism (raqam bo'laklarisiz). */
        val text: String,
        /** Sof raqam bo'laklari (faqat raqamlar). */
        val numbers: List<String>,
    ) {
        val onlyNumbers: Boolean get() = text.isEmpty() && numbers.isNotEmpty()
        val mixed: Boolean get() = text.isNotEmpty() && numbers.isNotEmpty()
    }

    private fun isNumber(t: String): Boolean = t.isNotEmpty() && t.all { it in '0'..'9' }

    fun parse(query: String): Parsed {
        val tokens = query.trim().split(' ', '\t', '\u00A0').filter { it.isNotEmpty() }
        val words = ArrayList<String>()
        val nums = ArrayList<String>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (isNumber(t)) {
                // "95 000" / "1 250 000": keyingi bo'laklar aynan 3 xonali bo'lsa — bitta son
                val sb = StringBuilder(t)
                var j = i + 1
                while (t.length <= 3 && j < tokens.size && tokens[j].length == 3 && isNumber(tokens[j])) { sb.append(tokens[j]); j++ }
                nums += sb.toString()
                i = j
            } else {
                words += t
                i++
            }
        }
        return Parsed(words.joinToString(" "), nums)
    }

    /** Narx shu raqamlardan boshlanadimi (95 -> 95 000, 9 500; 95000 -> 95 000). Nol va manfiy narx mos kelmaydi. */
    fun priceMatches(price: Double, digits: String): Boolean {
        if (digits.isEmpty() || price <= 0.0) return false
        val whole = Math.round(price)
        if (whole <= 0L) return false
        return whole.toString().startsWith(digits.trimStart('0').ifEmpty { "0" })
    }

    /** Narx aynan shu son (tartiblashda birinchi turishi uchun). */
    fun priceEquals(price: Double, digits: String): Boolean = digits.toLongOrNull()?.let { it > 0 && it == Math.round(price) } == true

    /** Kod mosligi: 2 — aynan shu kod, 1 — shu raqamlardan boshlanadi, 0 — mos emas. */
    fun codeRank(code: String, digits: String): Int {
        val c = code.trim().trimStart('0')
        val d = digits.trimStart('0')
        return when {
            d.isEmpty() -> 0
            c == d -> 2
            c.startsWith(d) -> 1
            else -> 0
        }
    }

    /**
     * Tayyor ro'yxatni (nom bo'yicha topilganlar) raqam bo'laklari bilan toraytirish va tartiblash.
     * [byName] — nom qidiruvi natijasi (matn bo'sh bo'lsa — hamma tovar). @return tartiblangan natija.
     */
    fun <T> refine(
        parsed: Parsed,
        byName: List<T>,
        all: List<T>,
        code: (T) -> String,
        name: (T) -> String,
        price: (T) -> Double,
    ): List<T> {
        if (parsed.numbers.isEmpty()) return byName
        if (parsed.onlyNumbers) {
            val n = parsed.numbers.joinToString("")
            val exactCode = ArrayList<T>()
            val prefixCode = ArrayList<T>()
            val exactPrice = ArrayList<T>()
            val prefixPrice = ArrayList<T>()
            for (x in all) {
                when (codeRank(code(x), n)) {
                    2 -> { exactCode += x; continue }
                    1 -> { prefixCode += x; continue }
                }
                val p = price(x)
                if (priceEquals(p, n)) exactPrice += x else if (priceMatches(p, n)) prefixPrice += x
            }
            val seen = HashSet<T>()
            val out = ArrayList<T>()
            for (list in listOf(exactCode, exactPrice, prefixCode, prefixPrice, byName)) for (x in list) if (seen.add(x)) out += x
            return out
        }
        // nom + raqam: har bir raqam narxga YOKI nomga mos kelishi kerak
        val first = ArrayList<T>()
        val second = ArrayList<T>()
        for (x in byName) {
            val p = price(x)
            val nm = name(x)
            var byPrice = true
            var ok = true
            for (n in parsed.numbers) {
                val pm = priceMatches(p, n)
                if (!pm) byPrice = false
                if (!pm && !nm.contains(n)) { ok = false; break }
            }
            if (!ok) continue
            if (byPrice) first += x else second += x
        }
        return first + second
    }
}
