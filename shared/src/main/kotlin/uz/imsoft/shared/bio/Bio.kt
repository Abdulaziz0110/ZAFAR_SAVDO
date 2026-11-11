package uz.imsoft.shared.bio

import uz.imsoft.shared.JObj
import uz.imsoft.shared.Json

/**
 * v5.2 (A7): tovar haqida qisqa ma'lumot — foydasi, iste'mol tartibi, kimga mumkin emasligi.
 *
 * Matn ALOHIDA yozuv, tovarlar unga ULANADI. Shuning uchun:
 *  - bitta matnni bir nechta tovarga birdan ulash mumkin (nusxa ko'paymaydi),
 *  - tovar kodi almashtirilsa matn yo'qolmaydi — boshqa tovarga ko'chiriladi,
 *  - bitta joyni tahrirlasa, ulangan hamma tovarda yangilanadi.
 *
 * [MAX] — Xprinter 58x40 etiketkasiga Arial 10 bilan sig'adigan belgilar soni (hisoblab topilgan).
 */
data class Bio(
    val id: String,
    val text: String,
    /** Oxirgi o'zgartirilgan vaqt (ms). */
    val at: Long,
    /** Ulangan tovar kodlari (1C kodi). */
    val products: List<String> = emptyList(),
) {
    val fits: Boolean get() = text.length <= MAX
    val left: Int get() = MAX - text.length

    companion object {
        /** 58x40 etiketka, Arial 10 — "0123456789" bilan o'lchangan sig'im. */
        const val MAX = 278
    }
}

object BioBook {
    /** Yangi yozuv uchun kalit (vaqt + tasodifiy). */
    fun newId(now: Long = System.currentTimeMillis(), rnd: java.util.Random = java.security.SecureRandom()): String {
        val a = "abcdefghijkmnpqrstuvwxyz23456789"
        return "b" + now + String(CharArray(4) { a[rnd.nextInt(a.length)] })
    }

    /** Matnni chegaraga moslash (ortig'i kesiladi). */
    fun clip(text: String): String = text.trim().take(Bio.MAX)

    fun pack(list: List<Bio>): String = Json.write(
        list.map { linkedMapOf<String, Any?>("i" to it.id, "t" to it.text, "a" to it.at, "p" to it.products) },
    )

    @Suppress("UNCHECKED_CAST")
    fun unpack(text: String?): List<Bio> {
        if (text.isNullOrBlank()) return emptyList()
        val arr = runCatching { Json.parse(text) as? List<Any?> }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { x ->
            val o = JObj(x as? Map<String, Any?> ?: return@mapNotNull null)
            val id = o.str("i")
            if (id.isEmpty()) return@mapNotNull null
            val prods = (o.map["p"] as? List<Any?>).orEmpty().mapNotNull { p -> p?.toString()?.trim()?.takeIf { it.isNotEmpty() } }
            Bio(id, o.str("t"), o.long("a"), prods.distinct())
        }
    }

    /** Tovar kodi -> matn. Bitta tovar faqat bitta matnga ulanadi (oxirgisi ustun). */
    fun byProduct(list: List<Bio>): Map<String, Bio> {
        val out = LinkedHashMap<String, Bio>()
        for (b in list.sortedBy { it.at }) for (p in b.products) out[p] = b
        return out
    }

    /** Matnni [productIds] tovarlariga ulaydi (boshqa matnlardan uziladi). */
    fun attach(list: List<Bio>, bioId: String, productIds: List<String>): List<Bio> {
        val add = productIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (add.isEmpty()) return list
        return list.map { b ->
            when {
                b.id == bioId -> b.copy(products = (b.products + add).distinct())
                b.products.any { it in add } -> b.copy(products = b.products.filter { it !in add })
                else -> b
            }
        }
    }

    /** Tovarni matndan uzadi. */
    fun detach(list: List<Bio>, productId: String): List<Bio> =
        list.map { b -> if (productId in b.products) b.copy(products = b.products.filter { it != productId }) else b }

    /** Matnni [from] tovaridan [to] tovariga ko'chiradi (kod almashtirilganda). */
    fun move(list: List<Bio>, from: String, to: String): List<Bio> {
        val b = list.firstOrNull { from in it.products } ?: return list
        return attach(detach(list, from), b.id, listOf(to))
    }

    /** Hech qaysi tovarga ulanmagan matnlar (kod o'chib ketgan bo'lsa ham yo'qolmaydi). */
    fun orphans(list: List<Bio>): List<Bio> = list.filter { it.products.isEmpty() }

    fun upsert(list: List<Bio>, b: Bio): List<Bio> {
        val i = list.indexOfFirst { it.id == b.id }
        return if (i < 0) list + b else list.toMutableList().also { it[i] = b }
    }

    fun remove(list: List<Bio>, id: String): List<Bio> = list.filter { it.id != id }

    // ---------------- kompyuterdan kiritish (CSV) ----------------

    /**
     * v5.2 (A7): Excel/kompyuterda yozish uchun CSV. Ustunlar: kod;nom;ma'lumot (nuqtali vergul — Excel uzbekcha).
     * Nom faqat ko'rish uchun — ulanish KOD bo'yicha.
     */
    fun toCsv(rows: List<Triple<String, String, String>>): String = buildString {
        append("kod;nom;malumot\n")
        for ((code, name, text) in rows) {
            append(csv(code)).append(';').append(csv(name)).append(';').append(csv(text)).append('\n')
        }
    }

    private fun csv(v: String): String {
        val t = v.replace("\r", "").replace("\n", " ").trim()
        return if (t.contains(';') || t.contains('"')) "\"" + t.replace("\"", "\"\"") + "\"" else t
    }

    /** CSV -> kod va matn juftliklari (sarlavha qatori o'tkazib yuboriladi, bo'sh matn — o'chirish). */
    fun fromCsv(text: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        for ((n, raw) in text.split('\n').withIndex()) {
            val line = raw.trim('\r', ' ', '\t')
            if (line.isEmpty()) continue
            val cells = splitCsv(line)
            val code = cells.getOrNull(0).orEmpty().trim()
            if (code.isEmpty()) continue
            if (n == 0 && code.equals("kod", ignoreCase = true)) continue
            out += code to clip(cells.getOrNull(2).orEmpty())
        }
        return out
    }

    private fun splitCsv(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var q = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && q && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> q = !q
                (c == ';' || c == '\t') && !q -> { out += sb.toString(); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }
}
