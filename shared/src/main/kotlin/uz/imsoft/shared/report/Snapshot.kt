package uz.imsoft.shared.report

import uz.imsoft.shared.B64
import uz.imsoft.shared.Fold
import uz.imsoft.shared.JObj
import uz.imsoft.shared.Json

/** 2-ilovada ko'rinadigan narx (1-ilovaning narx qoidalari bilan HISOBLANGAN). Kelgan (kirim) narx bu yerda YO'Q. */
data class PriceItem(
    val id: String,
    val name: String,
    val group: String,
    val cashSom: Double,
    val cashUsd: Double,
    val wholesaleSom: Double,
    val wholesaleUsd: Double,
    /** Tarozi PLU raqami (v2.7, bo'sh bo'lishi mumkin). Eski 2-ilova bu maydonni o'qimaydi. */
    val plu: String = "",
)

data class DebtInfo(val som: Double, val usd: Double, val at: Long, val source: String)

/** Qaysi davr hisobotlari yuklangan (stamp kun boshi..oxiri). */
data class Coverage(val from: Long, val to: Long, val importedAt: Long, val source: String)

/** 1-ilovadan 2-ilovaga boradigan to'liq ma'lumot. */
data class ClientSnapshot(
    val createdAt: Long,
    val clientId: String,
    val clientName: String,
    val rate: Double,
    val debt: DebtInfo?,
    val products: List<PriceItem>,
    val ledger: List<LedgerEntry>,
    val coverage: List<Coverage>,
    /** Narxlar oxirgi marta serverdan qachon olingan (1-ilova katalogi). */
    val pricesAt: Long,
    val version: Int = VERSION,
    /** 1C hisobotidagi rasmiy qoldiq ("kun oxiriga qarzdorlik"): stamp (kun oxiri) va summa (Dt − Kt). */
    val reportBalance: Pair<Long, Money2>? = null,
    /** 1-ilovada yoqilgan imkoniyatlar: [FEATURE_ORDERS] — 2-ilovadan buyurtma qabul qilinadi. */
    val features: Set<String> = emptySet(),
    /** Chek sarlavhasi (1-ilova sozlamasidan, bo'sh bo'lishi mumkin). */
    val shopName: String = "",
    /** Chek osti (telefonlar va h.k., har qator alohida) — 1-ilova sozlamasidan. v2.6 */
    val shopFooter: String = "",
) {
    /** Eng oxirgi import vaqti (telefon soati, ms). */
    val lastImportAt: Long get() = coverage.maxOfOrNull { it.importedAt } ?: 0L
    /** Hisobotlar qaysi kungacha (stamp). */
    val coveredTo: Long get() = coverage.maxOfOrNull { it.to } ?: 0L

    companion object {
        const val VERSION = 1
        const val FEATURE_ORDERS = "ord"
    }
}

object SnapshotCodec {
    class TooNew : Exception("Ma'lumot yangiroq ilova versiyasidan — 2-ilovani yangilang")

    fun toJson(s: ClientSnapshot): String = Json.write(
        linkedMapOf(
            "v" to s.version, "at" to s.createdAt, "cid" to s.clientId, "cn" to s.clientName, "rate" to s.rate, "pat" to s.pricesAt,
            "debt" to s.debt?.let { linkedMapOf("s" to it.som, "u" to it.usd, "at" to it.at, "src" to it.source) },
            "p" to s.products.map { listOf(it.id, it.name, it.group, it.cashSom, it.cashUsd, it.wholesaleSom, it.wholesaleUsd, it.plu) },
            "l" to s.ledger.map { entryToList(it) },
            "cov" to s.coverage.map { listOf(it.from, it.to, it.importedAt, it.source) },
            "rb" to s.reportBalance?.let { listOf(it.first, it.second.som, it.second.usd) },
            "f" to s.features.sorted(),
            "shop" to s.shopName,
            "foot" to s.shopFooter,
        ),
    )

    fun entryToList(e: LedgerEntry): List<Any?> = listOf(
        e.stamp, e.kind.code, e.kindText, e.product, e.qty,
        e.values.entries.associate { (k, v) -> k.code to listOf(v.som, v.usd) },
        e.extra.entries.associate { (k, v) -> k to listOf(v.som, v.usd) },
    )

    @Suppress("UNCHECKED_CAST")
    fun entryFromList(a: List<Any?>): LedgerEntry {
        fun d(x: Any?) = (x as? Number)?.toDouble() ?: 0.0
        fun pairs(x: Any?): Map<String, Money2> = (x as? Map<String, Any?>).orEmpty().mapValues { (_, v) ->
            val l = v as? List<Any?>
            Money2(d(l?.getOrNull(0)), d(l?.getOrNull(1)))
        }
        val values = HashMap<Col, Money2>()
        for ((k, v) in pairs(a.getOrNull(5))) Col.fromCode(k)?.let { values[it] = v }
        return LedgerEntry(
            (a[0] as Number).toLong(), EntryKind.fromCode(a.getOrNull(1)?.toString().orEmpty()), a.getOrNull(2)?.toString().orEmpty(),
            a.getOrNull(3)?.toString().orEmpty(), d(a.getOrNull(4)), values, LinkedHashMap(pairs(a.getOrNull(6))),
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun fromJson(text: String): ClientSnapshot {
        val o = JObj.parse(text)
        val v = o.long("v", 1).toInt()
        if (v > ClientSnapshot.VERSION) throw TooNew()
        fun d(x: Any?) = (x as? Number)?.toDouble() ?: 0.0
        val products = o.arr("p").mapNotNull { x ->
            val l = x as? List<Any?> ?: return@mapNotNull null
            PriceItem(
                l[0].toString(), l[1].toString(), l.getOrNull(2)?.toString().orEmpty(), d(l.getOrNull(3)), d(l.getOrNull(4)), d(l.getOrNull(5)), d(l.getOrNull(6)),
                l.getOrNull(7)?.toString().orEmpty(),
            )
        }
        val ledger = o.arr("l").mapNotNull { x -> (x as? List<Any?>)?.let { runCatching { entryFromList(it) }.getOrNull() } }
        val cov = o.arr("cov").mapNotNull { x ->
            val l = x as? List<Any?> ?: return@mapNotNull null
            Coverage((l[0] as Number).toLong(), (l[1] as Number).toLong(), (l.getOrNull(2) as? Number)?.toLong() ?: 0L, l.getOrNull(3)?.toString().orEmpty())
        }
        val debt = o.obj("debt")?.let { DebtInfo(it.num("s"), it.num("u"), it.long("at"), it.str("src")) }
        val rb = (o.map["rb"] as? List<Any?>)?.let { l -> (l.getOrNull(0) as? Number)?.toLong()?.let { st -> st to Money2(d(l.getOrNull(1)), d(l.getOrNull(2))) } }
        val features = o.arr("f").mapNotNull { it?.toString() }.toSet()
        return ClientSnapshot(o.long("at"), o.str("cid"), o.str("cn"), o.num("rate"), debt, products, ledger, cov, o.long("pat"), v, rb, features, o.str("shop"), o.str("foot"))
    }

    /** Bulutga yoziladigan ko'rinish: gzip + base64 (Firebase satr sifatida saqlaydi). */
    fun pack(s: ClientSnapshot): String = B64.encode(B64.gzip(toJson(s).toByteArray(Charsets.UTF_8)))

    fun unpack(blob: String): ClientSnapshot = fromJson(String(B64.gunzip(B64.decode(blob)), Charsets.UTF_8))
}

/** Yangi hisobotni avvalgi yozuvlarga qo'shish: hisobot davri ichidagi eskilari olib tashlanib, yangilari qo'yiladi. */
object LedgerMerge {
    class Result(val entries: List<LedgerEntry>, val coverage: List<Coverage>, val added: Int, val removed: Int, val same: Int)

    fun merge(old: List<LedgerEntry>, oldCov: List<Coverage>, report: CardReport, importedAt: Long, source: String): Result {
        val from = report.periodFrom ?: return Result(old, oldCov, 0, 0, 0)
        val to = report.periodTo ?: return Result(old, oldCov, 0, 0, 0)
        val (inside, outside) = old.partition { it.stamp in from..to }
        val oldIds = inside.map { it.identity() }.groupingBy { it }.eachCount().toMutableMap()
        var same = 0
        for (e in report.entries) {
            val id = e.identity()
            val n = oldIds[id] ?: 0
            if (n > 0) { same++; oldIds[id] = n - 1 }
        }
        val merged = (outside + report.entries).sortedWith(compareBy({ it.stamp }))
        return Result(merged, mergeCoverage(oldCov + Coverage(from, to, importedAt, source)), report.entries.size - same, inside.size - same, same)
    }

    /** Ustma-ust tushgan davrlarni birlashtiradi. */
    fun mergeCoverage(list: List<Coverage>): List<Coverage> {
        if (list.size <= 1) return list
        val sorted = list.sortedBy { it.from }
        val out = ArrayList<Coverage>()
        var cur = sorted[0]
        for (c in sorted.drop(1)) {
            cur = if (c.from <= Stamp.addDays(cur.to, 1) + 235959) {
                Coverage(cur.from, maxOf(cur.to, c.to), maxOf(cur.importedAt, c.importedAt), if (c.importedAt >= cur.importedAt) c.source else cur.source)
            } else { out += cur; c }
        }
        out += cur
        return out
    }

    /** Hisobotdagi tovar nomini katalog nomi bilan solishtirish kaliti. */
    fun nameKey(name: String): String = Fold.key(name).replace(" ", "")
}
