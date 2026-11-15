package com.ilxomjon.dur_novvot_dokon.data.relay

import com.ilxomjon.dur_novvot_dokon.data.local.CatalogSnapshot
import com.ilxomjon.dur_novvot_dokon.data.local.RawAgent
import com.ilxomjon.dur_novvot_dokon.data.local.RawProduct
import com.ilxomjon.dur_novvot_dokon.data.local.RawStore
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.DealMode
import com.ilxomjon.dur_novvot_dokon.domain.Group
import com.ilxomjon.dur_novvot_dokon.domain.PriceType
import com.ilxomjon.dur_novvot_dokon.domain.SentLine
import com.ilxomjon.dur_novvot_dokon.domain.SentOrder
import uz.imsoft.shared.B64
import uz.imsoft.shared.CloudDb
import uz.imsoft.shared.JObj
import uz.imsoft.shared.Json
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * v3.0 "Admin orqali" ko'prik: Agent <-> Firebase <-> Admin <-> SQL server. Sof qism (testlanadi).
 *
 * Firebase'da (g/{guruh kaliti}/):
 *   cat      — katalog (Admin yozadi, agentlar o'qiydi): guruhlar, hududlar, do'konlar+qarz, kurs, tovarlar, agentlar (parolsiz)
 *   codes    — 1C tovar kodlari (PLU/shtrix/narx turlari) — kompyuterdan Admin'ga kelgani
 *   hb       — Admin holati (oxirgi ishlagan vaqti, navbat, xato)
 *   creq     — agent "katalogni yangilab ber" so'rovi (vaqt)
 *   sig      — "yangi narsa bor" signali (Admin shuni kuzatadi)
 *   rq/{id}  — so'rov (kirish, qarz, hisobot, server vaqti): faqat yoziladi; rs/{id} — Admin javobi (id ni bilgan o'qiydi)
 *   ob/{oid} — savdo/to'lov (savollar jadvaliga yoziladigan matn aynan o'zi); obr/{oid} — natija (server ID)
 *   lb/{id}  — yorliq (TSPL baytlar), lbr/{id} — natija. 2-filial cheki savdo bilan birga keladi (ob/{oid}/m).
 * oid = sha256(agent, tur, matn) — bitta savdo necha marta yuborilsa ham bitta yozuv (dublikat yo'q).
 */
object RelayCodec {
    const val CODE_PREFIX = "IMSAG1:"

    /** v5.3: Admin'ning o'z savdosi bulut orqali ketganda qurilma o'rnida shu yoziladi (ko'zgusi takrorlanmaydi). */
    const val OWN_DEVICE = "admin"

    data class Config(val dbUrl: String, val key: String) {
        val isValid: Boolean get() = dbUrl.startsWith("https://") && CloudDb.isKey(key)
        fun path(sub: String) = "g/$key/$sub"
    }

    /** Admin beradigan "Agent ulanish kodi": IMSAG1:<base64url(url \n kalit)>. Bo'shliq va qator ko'chishi e'tiborsiz. */
    fun encodeCode(c: Config): String = CODE_PREFIX + B64.encode((c.dbUrl + "\n" + c.key).toByteArray(Charsets.UTF_8), urlSafe = true, pad = false)

    fun decodeCode(text: String): Config? {
        val t = text.filter { !it.isWhitespace() }
        val i = t.indexOf(CODE_PREFIX)
        if (i < 0) return null
        val raw = runCatching { String(B64.decode(t.substring(i + CODE_PREFIX.length)), Charsets.UTF_8) }.getOrNull() ?: return null
        val parts = raw.split('\n')
        if (parts.size != 2) return null
        val c = Config(CloudDb.normalizeUrl(parts[0]), parts[1].trim())
        return c.takeIf { it.isValid }
    }

    /** Savdo/to'lov kaliti: agent + tur + matn (aynan bir xil matn — bitta yozuv). */
    fun oid(userId: String, type: String, payload: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest((userId + "\u0001" + type + "\u0001" + payload).toByteArray(Charsets.UTF_8))
        return d.joinToString("") { "%02x".format(it) }.take(40)
    }

    /** Kirish isboti: parol o'zi Firebase'ga yozilmaydi. Admin serverdagi parol bilan xuddi shuni hisoblab solishtiradi. */
    fun proof(password: String, salt: String, iterations: Int = PROOF_ITER): String {
        val spec = PBEKeySpec(password.toCharArray(), ("imsoft-agent:" + salt).toByteArray(Charsets.UTF_8), iterations, 256)
        val k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return B64.encode(k, urlSafe = true, pad = false)
    }

    const val PROOF_ITER = 20_000

    /** Doimiy vaqtda solishtirish. */
    fun sameProof(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    // ---------------- katalog ----------------

    /** [branches] — v4.0: chek Mijoz ilovasi printerida chiqadigan do'konlar (Dadaxon aka) — agentda "Printer 2" shularga. */
    class Catalog(val snapshot: CatalogSnapshot, val agents: List<RawAgent>, val at: Long, val branches: List<String> = emptyList(), val lastCosts: Map<String, Pair<Double, Double>> = emptyMap())

    /** [lastCosts] — v4.0: oxirgi kirim narxi (tovar id -> so'm, $). */
    fun catalogToJson(s: CatalogSnapshot, agents: List<RawAgent>, at: Long, branches: List<String> = emptyList(), lastCosts: Map<String, Pair<Double, Double>> = emptyMap()): String = Json.write(
        linkedMapOf(
            "v" to 1, "at" to at,
            "g" to s.groups.map { listOf(it.id, it.name) },
            "r" to s.regions.map { listOf(it.first, it.second) },
            "s" to s.stores.map { listOf(it.regionId, it.id, it.name, it.debtSom, it.debtVal, it.time) },
            "k" to s.rates,
            "p" to s.products.map {
                listOf(it.groupId, it.id, it.name, it.currencyFlag, it.wholesaleSom, it.wholesaleVal, it.blockSize, it.stock, it.time,
                    it.cashSom, it.cashVal, it.costSom, it.costVal)
            },
            "a" to agents.map { listOf(it.id, it.fullName, it.boss, it.phone) },
            "br" to branches,
            "lc" to lastCosts.entries.sortedBy { it.key }.associate { (k, v) -> k to listOf(v.first, v.second) },
        ),
    )

    @Suppress("UNCHECKED_CAST")
    fun catalogFromJson(text: String): Catalog {
        val o = JObj.parse(text)
        fun rows(k: String): List<List<String>> = o.arr(k).mapNotNull { r -> (r as? List<Any?>)?.map { v -> v?.let { str(it) }.orEmpty() } }
        fun List<String>.at(i: Int) = getOrNull(i).orEmpty()
        val agents = rows("a").map { RawAgent(it.at(0), it.at(1), it.at(2), it.at(3)) }
        val snap = CatalogSnapshot(
            groups = rows("g").map { Group(it.at(0), it.at(1)) },
            regions = rows("r").map { it.at(0) to it.at(1) },
            stores = rows("s").map { RawStore(it.at(0), it.at(1), it.at(2), it.at(3), it.at(4), it.at(5)) },
            rates = o.arr("k").map { v -> v?.let { str(it) }.orEmpty() },
            products = rows("p").map {
                RawProduct(it.at(0), it.at(1), it.at(2), it.at(3), it.at(4), it.at(5), it.at(6), it.at(7), it.at(8), it.at(9), it.at(10), it.at(11), it.at(12))
            },
            agent = null,
            fetchedAt = o.long("at"),
        )
        val lc = (o.map["lc"] as? Map<String, Any?>).orEmpty().mapNotNull { (k, v) ->
            val l = v as? List<Any?> ?: return@mapNotNull null
            k to (((l.getOrNull(0) as? Number)?.toDouble() ?: 0.0) to ((l.getOrNull(1) as? Number)?.toDouble() ?: 0.0))
        }.toMap()
        return Catalog(snap, agents, o.long("at"), o.arr("br").map { v -> v?.let { str(it) }.orEmpty() }.filter { it.isNotEmpty() }, lc)
    }

    /** Katalog blobi: gzip + base64 (Firebase satr sifatida). */
    fun pack(json: String): String = B64.encode(B64.gzip(json.toByteArray(Charsets.UTF_8)))
    fun unpack(blob: String): String = String(B64.gunzip(B64.decode(blob)), Charsets.UTF_8)

    /** Raqamlar JSON'da Double bo'lib kelishi mumkin — "12.0" emas, "12". */
    private fun str(v: Any): String = when (v) {
        is Double -> if (v == Math.rint(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()
        else -> v.toString()
    }

    // ---------------- 2-ilovaga ko'zgu (savdo cheki) ----------------

    /** [branch] — v4.0: chek filial (Mijoz ilovasi) printerida chiqsinmi (null — Admin sozlamasi bo'yicha). */
    fun saleToJson(s: SentOrder, code: String, branch: Boolean? = null): String = Json.write(
        linkedMapOf(
            "at" to s.sentAt, "st" to s.storeId, "sn" to s.storeName, "m" to s.mode.code, "pt" to s.priceType.code, "c" to s.currency.code,
            "t" to s.total, "d" to s.discount, "pd" to s.paid, "code" to code,
            "l" to s.lines.map { listOf(it.productId, it.name, it.qty, it.price) },
            "bp" to branch,
        ),
    )

    class Sale(val order: SentOrder, val code: String, val branch: Boolean? = null)

    @Suppress("UNCHECKED_CAST")
    fun saleFromJson(text: String): Sale? = runCatching {
        val o = JObj.parse(text)
        val lines = o.arr("l").mapNotNull { r ->
            val l = r as? List<Any?> ?: return@mapNotNull null
            SentLine(l.getOrNull(1)?.toString().orEmpty(), (l.getOrNull(2) as? Number)?.toDouble() ?: 0.0, (l.getOrNull(3) as? Number)?.toDouble() ?: 0.0, l.getOrNull(0)?.let { str(it) }.orEmpty())
        }
        Sale(
            SentOrder(
                id = 0, sentAt = o.long("at"), storeId = o.str("st"), storeName = o.str("sn"), mode = DealMode.fromCode(o.str("m")),
                priceType = PriceType.fromCode(o.str("pt")), currency = Currency.fromCode(o.str("c")), total = o.num("t"),
                discount = o.num("d"), paid = o.num("pd"), lines = lines,
            ),
            o.str("code"),
            o.map["bp"] as? Boolean,
        )
    }.getOrNull()
}
