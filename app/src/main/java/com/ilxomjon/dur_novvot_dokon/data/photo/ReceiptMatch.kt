package com.ilxomjon.dur_novvot_dokon.data.photo

import com.ilxomjon.dur_novvot_dokon.core.Translit
import uz.imsoft.shared.JObj
import uz.imsoft.shared.Json

/**
 * v5.3.8: rasm orqali kirim — o'zini o'zi o'rgatadigan tovar va yuk beruvchi tanlash (Android'siz, testlanadi).
 *
 * Har tasdiqlangan kirimdan keyin eslab qoladi:
 *  - rasmdagi yozuv -> tovar (yuk beruvchi bo'yicha ham): keyingi safar xuddi shu yoki juda o'xshash yozuv — o'sha tovar;
 *  - miqdor ko'paytiruvchisi: rasmda "1 blok" deb o'qilib, kassir 12 dona qilgan bo'lsa — keyingi safar o'zi 12 ga;
 *  - yuk beruvchi sarlavhasi -> 1C'dagi kontragent;
 *  - oxirgi kirim narxi (yuk beruvchi + tovar) — narx keskin o'zgarsa ogohlantirish.
 * O'xshashlik: kirill/lotin bir xil (transliteratsiya), harf uchliklari (trigram) + so'zlar kesishmasi.
 */
object ReceiptMatch {
    const val MEM_EXACT = 1.0
    const val MEM_FUZZY = 0.78
    const val CATALOG_MIN = 0.42
    const val MAX_ALIASES = 6000

    class Alias(val key: String, var pid: String, val sup: String, var n: Int, var factor: Double, var at: Long)

    class Memory(
        val aliases: MutableList<Alias> = ArrayList(),
        /** yuk beruvchi sarlavhasi (normallashgan) -> kontragent kodi va nomi */
        val suppliers: MutableMap<String, Pair<String, String>> = LinkedHashMap(),
        /** "yukBeruvchiKodi|tovarKodi" -> oxirgi kirim narxi */
        val prices: MutableMap<String, Double> = LinkedHashMap(),
    )

    /** Katalogdagi tovar (tanlash uchun). */
    class Cand(val id: String, val name: String) {
        val key: String = norm(name)
        val grams: Set<String> = grams(key)
        val words: Set<String> = words(key)
    }

    /** Natija: [pid] null — topilmadi. how: "mem" (o'rganilgan), "fuzzy" (o'xshash yozuv), "catalog" (nomdan taxmin). */
    data class Match(val pid: String?, val score: Double, val how: String, val factor: Double = 1.0, val alts: List<String> = emptyList())

    fun norm(s: String): String {
        val t = Translit.key(s)
        val sb = StringBuilder(t.length)
        var space = false
        for (ch0 in t) {
            // o'zbekcha yozilishdagi farqlar bir xil: q~k, x~h (qo'lda/kirillda har xil yoziladi)
            val ch = when (ch0) { 'q' -> 'k'; 'x' -> 'h'; else -> ch0 }
            val ok = ch.isLetterOrDigit()
            if (ok) { sb.append(ch); space = false } else if (!space && sb.isNotEmpty()) { sb.append(' '); space = true }
        }
        return sb.toString().trim()
    }

    /** Ikki so'z bir xilmi: aynan, biri ikkinchisining boshi (4+ harf) yoki 1 ta harf farqi (4+ harf). */
    private fun sameWord(x: String, y: String): Boolean {
        if (x == y) return true
        if (x.length < 4 || y.length < 4) return false
        if (x.startsWith(y) || y.startsWith(x)) return true
        if (Math.abs(x.length - y.length) > 1) return false
        var i = 0
        var j = 0
        var diff = 0
        while (i < x.length && j < y.length) {
            if (x[i] == y[j]) { i++; j++; continue }
            if (++diff > 1) return false
            when {
                x.length > y.length -> i++
                y.length > x.length -> j++
                else -> { i++; j++ }
            }
        }
        return diff + (x.length - i) + (y.length - j) <= 1
    }

    fun grams(k: String): Set<String> {
        val s = " " + k + " "
        if (s.length < 3) return setOf(s)
        val out = HashSet<String>(s.length)
        for (i in 0..s.length - 3) out += s.substring(i, i + 3)
        return out
    }

    fun words(k: String): Set<String> = k.split(' ').filter { it.length >= 2 }.toSet()

    /** 0..1: trigramlar (Dice) + so'zlar kesishmasi (raqamli so'zlar — gramm/ml muhim). */
    fun sim(a: String, b: String): Double = sim(grams(a), words(a), grams(b), words(b))

    private fun sim(ga: Set<String>, wa: Set<String>, gb: Set<String>, wb: Set<String>): Double {
        if (ga.isEmpty() || gb.isEmpty()) return 0.0
        var common = 0
        val (small, big) = if (ga.size <= gb.size) ga to gb else gb to ga
        for (g in small) if (g in big) common++
        val dice = 2.0 * common / (ga.size + gb.size)
        val wordScore = if (wa.isEmpty() || wb.isEmpty()) dice else {
            var w = 0
            for (x in wa) if (x in wb || wb.any { y -> sameWord(x, y) }) w++
            w.toDouble() / maxOf(wa.size, wb.size)
        }
        return 0.65 * dice + 0.35 * wordScore
    }

    /** Rasmdagi yozuv -> tovar. Avval o'rganilganlar (shu yuk beruvchiniki ustun), keyin katalog nomlari. */
    fun match(mem: Memory, text: String, sup: String, cands: List<Cand>): Match {
        val k = norm(text)
        if (k.isEmpty()) return Match(null, 0.0, "")
        val ids = cands.associateBy { it.id }
        // 1) aynan shu yozuv
        val exact = mem.aliases.filter { it.key == k && it.pid in ids }
            .maxWithOrNull(compareBy<Alias>({ if (it.sup == sup) 1 else 0 }, { it.n }, { it.at }))
        val gk = grams(k)
        val wk = words(k)
        // 2) o'xshash yozuv
        var best: Alias? = exact
        var bestScore = if (exact != null) MEM_EXACT else 0.0
        if (exact == null) {
            for (a in mem.aliases) {
                if (a.pid !in ids) continue
                val s = sim(gk, wk, grams(a.key), words(a.key)) + (if (sup.isNotEmpty() && a.sup == sup) 0.05 else 0.0)
                if (s > bestScore) { bestScore = s; best = a }
            }
        }
        // 3) katalog (tovar nomlari) — muqobillar ro'yxati uchun ham
        val scored = cands.map { it to sim(gk, wk, it.grams, it.words) }.sortedByDescending { it.second }
        val alts = scored.take(6).filter { it.second >= 0.25 }.map { it.first.id }
        val b = best
        if (b != null && bestScore >= MEM_FUZZY) {
            return Match(b.pid, minOf(1.0, bestScore), if (bestScore >= MEM_EXACT) "mem" else "fuzzy", b.factor, (listOf(b.pid) + alts).distinct())
        }
        val top = scored.firstOrNull()
        if (top != null && top.second >= CATALOG_MIN) return Match(top.first.id, top.second, "catalog", 1.0, alts)
        return Match(null, top?.second ?: 0.0, "", 1.0, alts)
    }

    /** Yuk beruvchi: avval eslab qolingan sarlavha (aynan yoki o'xshash), keyin 1C ro'yxatidagi nomlar. @return (kod, nom) */
    fun supplier(mem: Memory, text: String, list: List<Pair<String, String>>): Pair<String, String>? {
        val k = norm(text)
        if (k.isEmpty()) return null
        mem.suppliers[k]?.let { return it }
        var best: Pair<String, String>? = null
        var bs = 0.0
        for ((key, v) in mem.suppliers) {
            val s = sim(k, key)
            if (s > bs) { bs = s; best = v }
        }
        if (best != null && bs >= 0.72) return best
        var cand: Pair<String, String>? = null
        var cs = 0.0
        for (p in list) {
            val s = sim(k, norm(p.second))
            if (s > cs) { cs = s; cand = p }
        }
        return cand?.takeIf { cs >= 0.55 }
    }

    /** Miqdor ko'paytiruvchisi: kassir rasmdagini butun songa ko'paytirgan bo'lsa (blok -> dona), aks holda 1. */
    fun factorOf(scanned: Double, final: Double): Double {
        if (scanned <= 0 || final <= 0) return 1.0
        val f = final / scanned
        val r = Math.rint(f)
        return if (r >= 2 && Math.abs(f - r) < 0.001) r else 1.0
    }

    /** Tasdiqlangan qator: yozuv -> tovar, ko'paytiruvchi, narx. */
    fun learn(mem: Memory, text: String, sup: String, pid: String, scannedQty: Double, finalQty: Double, price: Double, now: Long) {
        val k = norm(text)
        if (k.isEmpty() || pid.isEmpty()) return
        val f = factorOf(scannedQty, finalQty)
        val a = mem.aliases.firstOrNull { it.key == k && it.sup == sup }
        if (a == null) mem.aliases += Alias(k, pid, sup, 1, f, now)
        else {
            if (a.pid != pid) { a.pid = pid; a.n = 1 } else a.n++
            a.factor = f
            a.at = now
        }
        if (price > 0) mem.prices["$sup|$pid"] = price
        if (mem.aliases.size > MAX_ALIASES) {
            mem.aliases.sortBy { it.at }
            while (mem.aliases.size > MAX_ALIASES) mem.aliases.removeAt(0)
        }
    }

    fun learnSupplier(mem: Memory, text: String, code: String, name: String) {
        val k = norm(text)
        if (k.isNotEmpty() && name.isNotEmpty()) mem.suppliers[k] = code to name
    }

    /** Oxirgi narxdan 25% dan ko'p farq — ogohlantirish uchun oxirgi narx, aks holda null. */
    fun priceJump(mem: Memory, sup: String, pid: String, price: Double): Double? {
        val last = mem.prices["$sup|$pid"] ?: return null
        if (last <= 0 || price <= 0) return null
        return last.takeIf { Math.abs(price - it) / it > 0.25 }
    }

    fun write(mem: Memory): String = Json.write(
        mapOf(
            "v" to 1,
            "a" to mem.aliases.map { listOf(it.key, it.pid, it.sup, it.n, it.factor, it.at) },
            "s" to mem.suppliers.map { (k, v) -> listOf(k, v.first, v.second) },
            "p" to mem.prices,
        ),
    )

    fun read(text: String): Memory {
        val m = Memory()
        val o = runCatching { JObj.parse(text) }.getOrNull() ?: return m
        for (x in o.arr("a")) {
            val l = x as? List<*> ?: continue
            if (l.size < 6) continue
            m.aliases += Alias(l[0].toString(), l[1].toString(), l[2].toString(), (l[3] as? Number)?.toInt() ?: 1,
                (l[4] as? Number)?.toDouble() ?: 1.0, (l[5] as? Number)?.toLong() ?: 0L)
        }
        for (x in o.arr("s")) {
            val l = x as? List<*> ?: continue
            if (l.size >= 3) m.suppliers[l[0].toString()] = l[1].toString() to l[2].toString()
        }
        o.obj("p")?.map?.forEach { (k, v) -> (v as? Number)?.toDouble()?.let { m.prices[k] = it } }
        return m
    }
}
