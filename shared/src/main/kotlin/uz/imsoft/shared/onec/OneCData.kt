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

/** v3.12: kassa moddasi (Справочник.Операции). */
data class CashOp(val code: String, val name: String)

/**
 * [refs] — v5.1: 1C ma'lumotnomalari yangi tovar shakli uchun: "grp" guruhlar, "brd" brendlar,
 * "unit" o'lchov birliklari, "scale" tarozilar, "reg" regionlar.
 */
data class Suppliers(
    val from: Long, val to: Long, val at: Long, val list: List<Supplier>,
    val errors: List<String> = emptyList(), val ops: List<CashOp> = emptyList(),
    val refs: Map<String, List<String>> = emptyMap(),
    /** v5.3: kontragent telefonlari: kalit — 1C kodi va (zaxira) kichik harfli nomi. */
    val phones: Map<String, String> = emptyMap(),
    /** v5.3: kassadagi savdo oynasining "Paneli" (kelmagan bo'lsa null). */
    val panel: Panel? = null,
) {
    /** Mijoz/ta'minotchi telefoni: avval kodi, bo'lmasa nomi bo'yicha. */
    fun phoneOf(code: String, name: String): String = phones[code.trim()] ?: phones[nameKey(name)].orEmpty()

    companion object {
        fun nameKey(name: String): String = "n:" + name.trim().lowercase()
        const val GROUPS = "grp"
        const val BRANDS = "brd"
        const val UNITS = "unit"
        const val SCALES = "scale"
        const val REGIONS = "reg"
    }

    fun ref(key: String): List<String> = refs[key].orEmpty()
}

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
        val ops = o.arr("ops").mapNotNull { x ->
            val l = x as? List<Any?> ?: return@mapNotNull null
            val name = s(l.getOrNull(1)).trim()
            if (name.isEmpty()) null else CashOp(s(l.getOrNull(0)).trim(), name)
        }
        val refs = LinkedHashMap<String, List<String>>()
        (o.map["refs"] as? Map<*, *>)?.forEach { (k, v) ->
            val key = s(k)
            val names = (v as? List<*>)?.mapNotNull { n -> s(n).trim().takeIf { it.isNotEmpty() } }.orEmpty()
            if (key.isNotEmpty() && names.isNotEmpty()) refs[key] = names
        }
        val phones = HashMap<String, String>()
        for (x in o.arr("tel")) {
            val l = x as? List<Any?> ?: continue
            val tel = s(l.getOrNull(2)).trim()
            if (tel.isEmpty()) continue
            s(l.getOrNull(0)).trim().takeIf { it.isNotEmpty() }?.let { phones[it] = tel }
            s(l.getOrNull(1)).trim().takeIf { it.isNotEmpty() }?.let { phones[Suppliers.nameKey(it)] = tel }
        }
        return Suppliers(stampOf(o.map["f"]), stampOf(o.map["t"]), o.long("at"), list, o.arr("err").map { s(it) }, ops, refs, phones, Panel.parse(o.arr("pnl")))
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
            "err" to s.errors, "ops" to s.ops.map { listOf(it.code, it.name) }, "refs" to s.refs,
        ),
    )
}

/**
 * v3.7 Admin: kompyuter yuborgan savdolar (har safar bir davr) telefonda YIG'ILADI — oxirgi 1 yil. Yangi davr ichidagi eski
 * hujjatlar yangisi bilan almashadi (1C'da o'chirilgan/o'zgargan hujjat ham to'g'ri bo'ladi), davrdan tashqaridagilar qoladi.
 */
object OneCSalesArchive {
    private fun dayEnd(st: Long) = if (st % 1_000_000 == 0L) st + 235959 else st

    fun merge(old: OneCSales?, new: OneCSales, keepFrom: Long): OneCSales {
        val nf = new.from.takeIf { it > 0 } ?: new.docs.minOfOrNull { it.stamp }
        val nt = new.to.takeIf { it > 0 }?.let { dayEnd(it) } ?: new.docs.maxOfOrNull { it.stamp }
        if (nf == null || nt == null) return old?.copy(at = maxOf(old.at, new.at), errors = new.errors) ?: new
        val kept = old?.docs.orEmpty().filter { it.stamp < nf || it.stamp > nt }
        val docs = (kept + new.docs).filter { it.stamp >= keepFrom }.distinctBy { it.id.ifEmpty { it.kind + it.number + it.stamp } }
            .sortedByDescending { it.stamp }
        val from = listOfNotNull(old?.from?.takeIf { it > 0 }, nf).min().coerceAtLeast(keepFrom)
        val to = listOfNotNull(old?.to?.takeIf { it > 0 }?.let { dayEnd(it) }, nt).max()
        return OneCSales(from, to, maxOf(old?.at ?: 0L, new.at), docs, new.errors)
    }
}

/** v3.7: 1C hujjati (kompyuterdan) -> "1C tarixi" yozuvlari: savdo (r, m, z) va mijoz qaytargan (q). Boshqalari — yo'q. */
object OneCLedger {
    fun isClientDoc(kind: String) = kind == OneCDoc.SALE || kind == OneCDoc.SALE_MOBILE || kind == OneCDoc.RETAIL || kind == OneCDoc.CLIENT_RETURN

    /**
     * v4.0.1: server kartochkasi (akt sverki) savdo/qaytarishni TOVARSIZ beradi. Kompyuter (1C) qamragan vaqtda savdo va
     * qaytarish 1C hujjatlaridan (tovarlari bilan) olinadi, to'lovlar va qamralmagan vaqt — kartochkadan.
     * [mine] — hujjat shu mijozniki-mi. Admin "1C tarixi" va 2-ilova (Mijoz) tarixi bir xil qoidada.
     */
    fun withGoods(entries: List<uz.imsoft.shared.report.LedgerEntry>, sales: OneCSales?, nowMs: Long, mine: (OneCDoc) -> Boolean): List<uz.imsoft.shared.report.LedgerEntry> {
        if (sales == null || sales.docs.isEmpty()) return entries
        val (from, to) = covered(sales, nowMs) ?: return entries
        val out = ArrayList<uz.imsoft.shared.report.LedgerEntry>(entries.size + 16)
        for (e in entries) {
            val goods = e.kind == uz.imsoft.shared.report.EntryKind.SALE || e.kind == uz.imsoft.shared.report.EntryKind.RETURN
            if (goods && e.stamp in from..to) continue
            out += e
        }
        for (d in sales.docs) if (isClientDoc(d.kind) && d.stamp in from..to && mine(d)) out += entries(d)
        out.sortBy { it.stamp }
        return out
    }

    /** Kompyuter qamragan vaqt (yyyyMMddHHmmss): davr boshi .. min(davr oxiri, yuborilgan payt − 5 daqiqa). */
    fun covered(sales: OneCSales, nowMs: Long): Pair<Long, Long>? {
        val from = sales.from.takeIf { it > 0 } ?: return null
        val end = if (sales.to % 1_000_000 == 0L) sales.to + 235959 else sales.to
        val at = if (sales.at > 0) sales.at else nowMs
        val to = minOf(end, uz.imsoft.shared.report.PendingMerge.stampOf(at - 5 * 60_000L))
        return if (to >= from) from to to else null
    }

    fun entries(d: OneCDoc): List<uz.imsoft.shared.report.LedgerEntry> {
        if (!isClientDoc(d.kind)) return emptyList()
        val ret = d.kind == OneCDoc.CLIENT_RETURN
        val kind = if (ret) uz.imsoft.shared.report.EntryKind.RETURN else uz.imsoft.shared.report.EntryKind.SALE
        val sumCol = if (ret) uz.imsoft.shared.report.Col.RETURN else uz.imsoft.shared.report.Col.SUM
        val text = "1C №" + d.number
        if (d.lines.isEmpty()) {
            return listOf(uz.imsoft.shared.report.LedgerEntry(d.stamp, kind, text, "", 0.0, mapOf(sumCol to uz.imsoft.shared.report.Money2(d.som, d.usd))))
        }
        return d.lines.map { l ->
            val v = LinkedHashMap<uz.imsoft.shared.report.Col, uz.imsoft.shared.report.Money2>()
            if (l.priceSom != 0.0 || l.priceUsd != 0.0) v[uz.imsoft.shared.report.Col.PRICE] = uz.imsoft.shared.report.Money2(l.priceSom, l.priceUsd)
            v[sumCol] = uz.imsoft.shared.report.Money2(l.sumSom, l.sumUsd)
            uz.imsoft.shared.report.LedgerEntry(d.stamp, kind, text, l.name, l.qty, v)
        }
    }
}
