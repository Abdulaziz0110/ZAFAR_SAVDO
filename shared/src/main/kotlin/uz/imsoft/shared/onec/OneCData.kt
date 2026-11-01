package uz.imsoft.shared.onec

import uz.imsoft.shared.JObj
import uz.imsoft.shared.Json

/**
 * Kompyuter (onec_export.py) 1C'dan o'qib Admin'ga yuboradigan ma'lumotlar (v3.2). Hammasi faqat O'QISH natijasi.
 * Bulutda: in/{inbox}/{k} = {k: "sales" | "supp", b: gzip(json)}.
 */
data class OneCLine(
    val name: String,
    val code: String,
    val qty: Double,
    val priceSom: Double,
    val sumSom: Double,
    val priceUsd: Double = 0.0,
    val sumUsd: Double = 0.0,
)

/** 1C hujjati. [kind]: r — savdo (Реализация), m — mobil savdo, z — chakana, q — mijoz qaytargan, p — kirim, y — ta'minotchiga to'lov, b — ta'minotchiga qaytarish. */
data class OneCDoc(
    val id: String,
    val kind: String,
    val number: String,
    /** yyyyMMddHHmmss */
    val stamp: Long,
    val party: String,
    val partyCode: String,
    val som: Double,
    val usd: Double,
    val note: String,
    val lines: List<OneCLine>,
) {
    /** Qidiruv uchun matn (kichik harflarda). */
    val haystack: String by lazy { (number + " " + party + " " + note + " " + lines.joinToString(" ") { it.name + " " + it.code }).lowercase() }

    fun matches(query: String): Boolean {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        return words.all { haystack.contains(it) }
    }

    companion object {
        const val SALE = "r"
        const val SALE_MOBILE = "m"
        const val RETAIL = "z"
        const val CLIENT_RETURN = "q"
        const val RECEIPT = "p"
        const val SUPPLIER_PAY = "y"
        const val SUPPLIER_RETURN = "b"
    }
}

data class OneCSales(val from: Long, val to: Long, val at: Long, val docs: List<OneCDoc>, val errors: List<String> = emptyList()) {
    fun filter(query: String, kinds: Set<String> = emptySet()): List<OneCDoc> =
        docs.filter { (kinds.isEmpty() || it.kind in kinds) && (query.isBlank() || it.matches(query)) }

    /** Jami (so'm, $) — faqat savdolar, qaytarish ayriladi. */
    fun totals(list: List<OneCDoc>): Pair<Double, Double> {
        var s = 0.0
        var u = 0.0
        for (d in list) {
            val sign = if (d.kind == OneCDoc.CLIENT_RETURN) -1 else 1
            s += sign * d.som
            u += sign * d.usd
        }
        return s to u
    }
}

/** Ta'minotchi: qoldiq [balanceSom]/[balanceUsd] 1C registridan (manfiy — biz qarzdormiz). */
data class Supplier(
    val id: String,
    val code: String,
    val name: String,
    val phone: String,
    val balanceSom: Double,
    val balanceUsd: Double,
    val docs: List<OneCDoc>,
) {
    /** Bizning ta'minotchidan qarzimiz (musbat — qarzdormiz). */
    val oweSom: Double get() = -balanceSom
    val oweUsd: Double get() = -balanceUsd
}

data class Suppliers(val from: Long, val to: Long, val at: Long, val list: List<Supplier>, val errors: List<String> = emptyList())

object OneCCodec {
    private fun d(x: Any?): Double = (x as? Number)?.toDouble() ?: x?.toString()?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
    private fun s(x: Any?): String = when (x) {
        null -> ""
        is Double -> if (x == Math.floor(x) && !x.isInfinite() && kotlin.math.abs(x) < 1e15) x.toLong().toString() else x.toString()
        else -> x.toString()
    }

    fun stampOf(v: Any?): Long {
        val t = s(v).filter { it.isDigit() }
        if (t.length < 8) return 0L
        return (t + "000000").take(14).toLongOrNull() ?: 0L
    }

    fun line(x: Any?): OneCLine? {
        val l = x as? List<*> ?: return null
        val name = s(l.getOrNull(0)).trim()
        if (name.isEmpty()) return null
        return OneCLine(name, s(l.getOrNull(1)), d(l.getOrNull(2)), d(l.getOrNull(3)), d(l.getOrNull(4)), d(l.getOrNull(5)), d(l.getOrNull(6)))
    }

    @Suppress("UNCHECKED_CAST")
    fun doc(x: Any?): OneCDoc? {
        val o = JObj(x as? Map<String, Any?> ?: return null)
        val st = stampOf(o.map["d"])
        if (st == 0L) return null
        return OneCDoc(
            o.str("id"), o.str("k").ifEmpty { OneCDoc.SALE }, s(o.map["no"]).trim(), st, o.str("c").trim(), s(o.map["cc"]),
            d(o.map["s"]), d(o.map["u"]), o.str("n"), o.arr("l").mapNotNull { line(it) },
        )
    }

    fun parseSales(bytes: ByteArray): OneCSales {
        val o = JObj.parse(String(bytes, Charsets.UTF_8))
        val docs = o.arr("docs").mapNotNull { doc(it) }.sortedByDescending { it.stamp }
        return OneCSales(stampOf(o.map["f"]), stampOf(o.map["t"]), o.long("at"), docs, o.arr("err").map { s(it) })
    }

    @Suppress("UNCHECKED_CAST")
    fun parseSuppliers(bytes: ByteArray): Suppliers {
        val o = JObj.parse(String(bytes, Charsets.UTF_8))
        val list = o.arr("list").mapNotNull { x ->
            val m = JObj(x as? Map<String, Any?> ?: return@mapNotNull null)
            val name = m.str("name").trim()
            if (name.isEmpty()) return@mapNotNull null
            Supplier(
                m.str("id"), s(m.map["code"]), name, m.str("tel"), d(m.map["bs"]), d(m.map["bu"]),
                m.arr("docs").mapNotNull { doc(it) }.sortedByDescending { it.stamp },
            )
        }.sortedWith(compareByDescending<Supplier> { kotlin.math.abs(it.balanceSom) + kotlin.math.abs(it.balanceUsd) * 12_000 }.thenBy { it.name.lowercase() })
        return Suppliers(stampOf(o.map["f"]), stampOf(o.map["t"]), o.long("at"), list, o.arr("err").map { s(it) })
    }

    fun lineToList(l: OneCLine): List<Any?> = listOf(l.name, l.code, l.qty, l.priceSom, l.sumSom, l.priceUsd, l.sumUsd)

    fun docToMap(d: OneCDoc): Map<String, Any?> = linkedMapOf(
        "id" to d.id, "k" to d.kind, "no" to d.number, "d" to d.stamp.toString(), "c" to d.party, "cc" to d.partyCode,
        "s" to d.som, "u" to d.usd, "n" to d.note, "l" to d.lines.map { lineToList(it) },
    )

    fun salesJson(s: OneCSales): String = Json.write(
        linkedMapOf("v" to 1, "f" to s.from.toString(), "t" to s.to.toString(), "at" to s.at, "docs" to s.docs.map { docToMap(it) }, "err" to s.errors),
    )

    fun suppliersJson(s: Suppliers): String = Json.write(
        linkedMapOf(
            "v" to 1, "f" to s.from.toString(), "t" to s.to.toString(), "at" to s.at,
            "list" to s.list.map { linkedMapOf("id" to it.id, "code" to it.code, "name" to it.name, "tel" to it.phone, "bs" to it.balanceSom, "bu" to it.balanceUsd, "docs" to it.docs.map { d -> docToMap(d) }) },
            "err" to s.errors,
        ),
    )
}
