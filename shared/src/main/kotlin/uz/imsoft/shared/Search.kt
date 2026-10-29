package uz.imsoft.shared

/**
 * Matnni qidiruv uchun "buklash": kichik harf, kirill -> lotin, tutuq belgilari olib tashlanadi, qolgan belgilar bo'shliq.
 * [map] — har buklangan belgining asl matndagi o'rni (natijani qalin qilib ko'rsatish uchun).
 */
class Folded(val text: String, val map: IntArray)

object Fold {
    private val cyr: Map<Char, String> = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo", 'ж' to "j", 'з' to "z",
        'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r",
        'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "x", 'ц' to "s", 'ч' to "ch", 'ш' to "sh", 'щ' to "sh",
        'ъ' to "", 'ы' to "i", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya", 'ў' to "o", 'қ' to "q", 'ғ' to "g", 'ҳ' to "h",
        'і' to "i", 'ї' to "i", 'є' to "e", 'ґ' to "g",
    )
    private const val APOS = "'’ʻʼ‘`´ʹ"

    fun fold(input: String): Folded {
        val sb = StringBuilder(input.length + 8)
        val map = IntArray(input.length * 2 + 8)
        var lastSpace = true
        for ((i, raw) in input.withIndex()) {
            val ch = raw.lowercaseChar()
            val m = cyr[ch]
            val piece: String = when {
                m != null -> m
                APOS.indexOf(ch) >= 0 -> ""
                ch == 'w' -> "v"
                ch.isLetterOrDigit() -> ch.toString()
                else -> " "
            }
            for (p in piece) {
                if (p == ' ') {
                    if (lastSpace) continue
                    lastSpace = true
                } else lastSpace = false
                if (sb.length >= map.size) continue
                map[sb.length] = i
                sb.append(p)
            }
        }
        var len = sb.length
        while (len > 0 && sb[len - 1] == ' ') len--
        return Folded(sb.substring(0, len), map.copyOf(maxOf(len, 0)))
    }

    fun key(input: String): String = fold(input).text

    /** Talaffuzga yaqin buklash: h~x, q~k, ikki marta takrorlangan harf bitta. Faqat taqqoslash uchun. */
    fun loose(token: String): String {
        val sb = StringBuilder(token.length)
        for (c0 in token) {
            val c = when (c0) { 'h' -> 'x'; 'q' -> 'k'; else -> c0 }
            if (sb.isNotEmpty() && sb[sb.length - 1] == c && !c.isDigit()) continue
            sb.append(c)
        }
        return sb.toString()
    }

    private const val RU = "йцукенгшщзхъфывапролджэячсмитьбю"
    private const val EN = "qwertyuiop[]asdfghjkl;'zxcvbnm,."

    /** Klaviatura tartibi adashganda (ЙЦУКЕН <-> QWERTY): "ьфнша" -> "mayiz". */
    fun swapLayout(q: String): String {
        val lower = q.lowercase()
        val hasCyr = lower.any { RU.indexOf(it) >= 0 }
        val sb = StringBuilder(lower.length)
        for (c in lower) {
            if (hasCyr) {
                val i = RU.indexOf(c)
                sb.append(if (i >= 0) EN[i] else c)
            } else {
                val i = EN.indexOf(c)
                sb.append(if (i >= 0) RU[i] else c)
            }
        }
        return sb.toString()
    }
}

/** Matndagi so'z (buklangan matnda [start, end) oralig'i). */
class Tok(val text: String, val start: Int, val end: Int) {
    val loose: String = Fold.loose(text)
    val digits: Boolean = text.all { it.isDigit() }
}

object Tokenizer {
    /** Bo'shliq bo'yicha, harf/raqam chegarasida ham bo'ladi: "mayiz134" -> "mayiz", "134". */
    fun tokens(folded: String): List<Tok> {
        val out = ArrayList<Tok>()
        var i = 0
        while (i < folded.length) {
            if (folded[i] == ' ') { i++; continue }
            val start = i
            val digit = folded[i].isDigit()
            while (i < folded.length && folded[i] != ' ' && folded[i].isDigit() == digit) i++
            out += Tok(folded.substring(start, i), start, i)
        }
        return out
    }
}

/** Qidiruv sozlamalari (Sozlamalardan). */
data class SearchOptions(
    /** Imlo xatolariga chidamli ("pista" ≈ "pistta", "bodom" ≈ "badom"). */
    val fuzzy: Boolean = true,
    /** Klaviatura tartibini tuzatish ("ьфнша" -> "mayiz"). */
    val layoutFix: Boolean = true,
)

class SearchHit<T>(
    val item: T,
    val score: Double,
    /** Nomdagi mos qismlar (asl matn bo'yicha) — qalin qilib ko'rsatish uchun. */
    val ranges: List<IntRange>,
    /** true — so'zlarning hammasi topilmadi yoki imlo/klaviatura tuzatildi ("taxminiy"). */
    val approximate: Boolean,
)

/**
 * Professional qidiruv:
 *  - bir nechta so'z istalgan tartibda ("mayiz b" = "B Mayiz 134"), har biri so'z boshidan yoki ichidan;
 *  - lotin/kirill farqi yo'q, tutuq belgisisiz ("og'il" = "ogil" = "ўғил");
 *  - tovar kodi bo'yicha (aniq yoki boshi);
 *  - imlo xatolari (1–2 harf), h/x, q/k, qo'sh harflar;
 *  - klaviatura tartibi adashgan bo'lsa ham topadi;
 *  - raqamlar faqat aniq/boshidan (134 hech qachon 120 ga mos kelmaydi);
 *  - natija saralanadi: aniq > so'z boshi > ichida > taxminiy, keyin mashhurlik va qisqalik.
 */
class SearchIndex<T>(
    items: List<T>,
    private val name: (T) -> String,
    private val code: (T) -> String = { "" },
    /** Qo'shimcha qidiriladigan matn (masalan guruh yoki hudud nomi) — past og'irlik bilan. */
    private val extra: (T) -> String = { "" },
) {
    private class Entry<T>(val item: T, val folded: Folded, val tokens: List<Tok>, val code: String, val extra: List<Tok>, val order: Int)

    private val entries: List<Entry<T>> = items.mapIndexed { i, it ->
        val f = Fold.fold(name(it))
        Entry(it, f, Tokenizer.tokens(f.text), Fold.key(code(it)).replace(" ", ""), Tokenizer.tokens(Fold.key(extra(it))), i)
    }

    val size: Int get() = entries.size

    fun search(query: String, options: SearchOptions = SearchOptions(), popularity: ((T) -> Double)? = null, limit: Int = Int.MAX_VALUE): List<SearchHit<T>> {
        val q = Fold.key(query)
        if (q.isBlank()) return entries.take(limit).map { SearchHit(it.item, 0.0, emptyList(), false) }
        var hits = run(q, options, popularity, approximate = false)
        if (options.layoutFix && hits.count { !it.approximate } < 3) {
            val alt = Fold.key(Fold.swapLayout(query))
            if (alt != q && alt.isNotBlank()) {
                val altHits = run(alt, options, popularity, approximate = true)
                if (altHits.isNotEmpty()) {
                    val have = HashSet<Int>()
                    hits.forEach { have += System.identityHashCode(it.item) }
                    val strongest = hits.maxOfOrNull { it.score } ?: 0.0
                    // Asl so'rov yaxshi natija bergan bo'lsa, almashtirilgan variant faqat oxiriga qo'shiladi.
                    val penalty = if (strongest >= 80) 0.5 else 0.9
                    hits = hits + altHits.filter { System.identityHashCode(it.item) !in have }
                        .map { SearchHit(it.item, it.score * penalty, it.ranges, true) }
                }
            }
        }
        return hits.sortedWith(compareByDescending<SearchHit<T>> { it.score }).take(limit)
    }

    private fun run(q: String, options: SearchOptions, popularity: ((T) -> Double)?, approximate: Boolean): List<SearchHit<T>> {
        val qTokens = Tokenizer.tokens(q)
        if (qTokens.isEmpty()) return emptyList()
        val qCompact = q.replace(" ", "")
        val out = ArrayList<SearchHit<T>>()
        val partial = ArrayList<SearchHit<T>>()
        for (e in entries) {
            var total = 0.0
            var matched = 0
            var fuzzyUsed = false
            val ranges = ArrayList<IntRange>()
            var lastPos = -1
            var inOrder = true
            var firstHitsFirst = false
            for ((qi, qt) in qTokens.withIndex()) {
                val m = best(e, qt, options.fuzzy)
                if (m == null) continue
                matched++
                total += m.score
                if (m.fuzzy) fuzzyUsed = true
                if (m.tokenIndex >= 0) {
                    if (m.tokenIndex < lastPos) inOrder = false
                    lastPos = m.tokenIndex
                    if (qi == 0 && m.tokenIndex == 0) firstHitsFirst = true
                    m.range?.let { ranges += it }
                }
            }
            if (matched == 0) continue
            // Bonuslar
            if (firstHitsFirst) total += 25
            if (inOrder && qTokens.size > 1) total += 15
            if (e.folded.text.startsWith(q)) total += 20
            if (e.folded.text == q) total += 40
            if (e.code.isNotEmpty() && e.code == qCompact) total += 60
            total -= minOf(20.0, e.folded.text.length * 0.08)
            popularity?.let { total += minOf(25.0, it(e.item)) }
            val hit = SearchHit(e.item, total, mergeRanges(e, ranges), approximate || fuzzyUsed || matched < qTokens.size)
            if (matched == qTokens.size) out += hit else if (qTokens.size >= 2 && matched >= qTokens.size - 1) partial += hit
        }
        // Hamma so'z topilgan natija bo'lmasa — bittasi tushib qolganlarini "taxminiy" qilib ko'rsatamiz.
        if (out.isEmpty()) return partial.map { SearchHit(it.item, it.score * 0.6, it.ranges, true) }
        return out
    }

    private class Match(val score: Double, val tokenIndex: Int, val range: IntRange?, val fuzzy: Boolean)

    private fun best(e: Entry<T>, qt: Tok, fuzzy: Boolean): Match? {
        var best: Match? = null
        fun offer(m: Match) { if (best == null || m.score > best!!.score) best = m }
        // Kod
        if (e.code.isNotEmpty()) {
            if (e.code == qt.text) offer(Match(120.0, -1, null, false))
            else if (qt.text.length >= 2 && e.code.startsWith(qt.text)) offer(Match(90.0, -1, null, false))
        }
        for ((ti, nt) in e.tokens.withIndex()) {
            val s = tokenScore(qt, nt, fuzzy) ?: continue
            val len = if (s.second) minOf(nt.text.length, qt.text.length) else qt.text.length
            val start = if (s.third >= 0) nt.start + s.third else nt.start
            offer(Match(s.first, ti, rangeOf(e, start, start + len), s.second))
        }
        for (xt in e.extra) {
            val s = tokenScore(qt, xt, false) ?: continue
            offer(Match(s.first * 0.5, -1, null, false))
        }
        // Butun nom ichida (bo'shliqsiz) — "bmayiz" kabi qo'shib yozilganlar
        if (best == null && qt.text.length >= 4 && !qt.digits) {
            val compact = e.folded.text.replace(" ", "")
            if (compact.contains(qt.text)) offer(Match(40.0, -1, null, true))
        }
        return best
    }

    /** (ball, imlo tuzatilganmi, nt ichidagi boshlanish o'rni yoki -1). */
    private fun tokenScore(qt: Tok, nt: Tok, fuzzy: Boolean): Triple<Double, Boolean, Int>? {
        val q = qt.text
        val n = nt.text
        if (n == q) return Triple(100.0, false, 0)
        if (n.startsWith(q)) return Triple(85.0 - minOf(15, n.length - q.length), false, 0)
        if (qt.digits || nt.digits) {
            // Raqamlar: faqat aniq yoki boshidan (yuqorida). Ichida — faqat 3+ raqam.
            if (qt.digits && q.length >= 3) { val i = n.indexOf(q); if (i > 0) return Triple(50.0, false, i) }
            return null
        }
        if (nt.loose == qt.loose) return Triple(80.0, true, -1)
        if (qt.loose.length >= 2 && nt.loose.startsWith(qt.loose)) return Triple(70.0, true, -1)
        if (q.length >= 2) { val i = n.indexOf(q); if (i > 0) return Triple(55.0, false, i) }
        if (!fuzzy || q.length < 4) return null
        val maxD = if (q.length >= 7) 2 else 1
        // So'zning boshi (bir xil uzunlik ±1) bilan solishtiriladi: "pistt" ~ "pista..."
        var bestD = Int.MAX_VALUE
        for (len in (q.length - 1)..(q.length + 1)) {
            if (len < 3 || len > n.length) continue
            val d = Distance.osa(q, n.substring(0, len), maxD)
            if (d < bestD) bestD = d
        }
        if (bestD <= maxD) return Triple(45.0 - 10.0 * bestD, true, -1)
        return null
    }

    private fun rangeOf(e: Entry<T>, foldStart: Int, foldEnd: Int): IntRange? {
        val map = e.folded.map
        if (foldStart < 0 || foldEnd <= foldStart || foldEnd > map.size) return null
        return map[foldStart]..map[foldEnd - 1]
    }

    private fun mergeRanges(e: Entry<T>, list: List<IntRange>): List<IntRange> {
        if (list.size <= 1) return list
        val sorted = list.sortedBy { it.first }
        val out = ArrayList<IntRange>()
        var cur = sorted[0]
        for (r in sorted.drop(1)) {
            cur = if (r.first <= cur.last + 1) cur.first..maxOf(cur.last, r.last) else { out += cur; r }
        }
        out += cur
        return out
    }
}

object Distance {
    /** Optimal string alignment (Damerau–Levenshtein, qo'shni harflar almashinuvi = 1). [max] dan oshsa erta to'xtaydi. */
    fun osa(a: String, b: String, max: Int = Int.MAX_VALUE): Int {
        if (a == b) return 0
        if (Math.abs(a.length - b.length) > max) return max + 1
        val n = a.length
        val m = b.length
        var prev2 = IntArray(m + 1)
        var prev = IntArray(m + 1) { it }
        var cur = IntArray(m + 1)
        for (i in 1..n) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prev2[j - 2] + 1)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > max) return max + 1
            val t = prev2; prev2 = prev; prev = cur; cur = t
        }
        return prev[m]
    }
}
