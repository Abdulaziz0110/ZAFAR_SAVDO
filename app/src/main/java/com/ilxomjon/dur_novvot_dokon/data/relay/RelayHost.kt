package com.ilxomjon.dur_novvot_dokon.data.relay

import com.ilxomjon.dur_novvot_dokon.data.local.CatalogSnapshot
import com.ilxomjon.dur_novvot_dokon.data.local.RawAgent
import com.ilxomjon.dur_novvot_dokon.domain.outbox.RejectedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uz.imsoft.shared.B64
import uz.imsoft.shared.CloudDb
import uz.imsoft.shared.JObj

/**
 * Admin: agentlar ko'prigi ([RelayCodec]). [pump] bir martada hamma kutayotgan ishni bajaradi:
 * so'rovlar (kirish, qarz, hisobot), savdo/to'lovlarni serverga yozish (dublikatsiz) va ularning 2-filial cheki, yorliq,
 * katalogni joylash (agent so'rasa yoki 20 daqiqada bir), 1C kodlari va holat (heartbeat).
 */
class RelayHost(
    private val config: () -> RelayCodec.Config?,
    private val cloud: (String) -> CloudDb,
    private val server: Server,
    private val hooks: Hooks,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    /** Serverdagi ishlar (Admin'da [com.ilxomjon.dur_novvot_dokon.data.remote.ServerApi]). */
    interface Server {
        suspend fun agentByPhone(phone: String): Pair<RawAgent, String>?
        suspend fun agents(): List<RawAgent>
        suspend fun fetchSnapshot(agentId: String): CatalogSnapshot
        suspend fun fetchDebt(agentId: String, storeId: String): Pair<String, String>
        suspend fun fetchReport(agentId: String, payload: String): String
        suspend fun serverTimeMillis(): Long
        suspend fun find(userId: String, type: String, payload: String): String?
        suspend fun insert(userId: String, type: String, payload: String): String
    }

    interface Hooks {
        /** Admin'ning agent ID'si (katalog so'rovi uchun). */
        fun adminAgentId(): String
        /** 1C kodlari fayli: (o'zgargan vaqti, baytlar) yoki null. */
        fun codes(): Pair<Long, ByteArray>?
        suspend fun printLabels(data: ByteArray): String
        suspend fun mirrorSale(sale: RelayCodec.Sale, key: String)
        /** v3.2: agent tovarga yangi shtrix kod bog'ladi — Admin 1C'ga yozish navbatiga qo'yadi. */
        suspend fun barcode(productId: String, name: String, code: String): Boolean = false
    }

    class Stats(val at: Long = 0L, val requests: Int = 0, val written: Int = 0, val labels: Int = 0, val error: String = "", val catalogAt: Long = 0L)

    /** v3.2: katalogni agentlarga qayta joylash oralig'i (ish vaqtida tezroq). Standart — 20 daqiqa. */
    @Volatile var catalogPeriodMs: () -> Long = { 20 * 60_000L }

    @Volatile var stats = Stats()
        private set
    private val mutex = Mutex()
    @Volatile private var catalogAt = 0L
    @Volatile private var catalogHash = 0
    @Volatile private var codesSent = 0L
    @Volatile private var cleanedAt = 0L

    val isConfigured: Boolean get() = config()?.isValid == true

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    @Suppress("UNCHECKED_CAST")
    private suspend fun map(db: CloudDb, path: String): Map<String, Any?> = (io { db.get(path) } as? Map<String, Any?>).orEmpty()

    /** Hamma kutayotgan ishni bajaradi. Bir vaqtda bitta. @return bajarilgan ishlar soni. */
    suspend fun pump(forceCatalog: Boolean = false, version: String = ""): Int = mutex.withLock {
        val c = config()?.takeIf { it.isValid } ?: return 0
        val db = cloud(c.dbUrl)
        var done = 0
        var req = 0; var wrote = 0; var lab = 0
        var error = ""
        // 1) so'rovlar
        for ((id, v) in map(db, c.path("rq"))) {
            val o = (v as? Map<*, *>) ?: continue
            val at = (o["at"] as? Number)?.toLong() ?: 0L
            if (at in 1 until now() - 10 * 60_000L) { runCatching { io { db.delete(c.path("rq/$id")) } }; continue } // eskirgan
            val answer = LinkedHashMap<String, Any?>()
            try {
                answer["r"] = answer(o["op"]?.toString().orEmpty(), o["p"]?.toString().orEmpty())
                answer["ok"] = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                answer["ok"] = false
                answer["e"] = if (e is RejectedException) "rejected" else (e.message ?: e.javaClass.simpleName).take(300)
            }
            answer["at"] = CloudDb.SERVER_TIME
            runCatching { io { db.put(c.path("rs/$id"), answer); db.delete(c.path("rq/$id")) } }.onSuccess { req++; done++ }
        }
        // 2) savdo va to'lovlar -> server (savollar jadvali), dublikatsiz
        // Agentlar yaratgan tartibda (Firebase vaqti bo'yicha) — savdo va to'lov serverga ketma-ket tushsin
        val queue = map(db, c.path("ob")).entries.sortedBy { ((it.value as? Map<*, *>)?.get("at") as? Number)?.toLong() ?: Long.MAX_VALUE }
        for ((oid, v) in queue) {
            val o = (v as? Map<*, *>) ?: continue
            try {
                if (write(db, c, oid, o)) { wrote++; done++ }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = (e.message ?: e.javaClass.simpleName).take(200)
                break // server ko'rinmayapti — qolganlari keyingi safar (tartib saqlanadi)
            }
        }
        // 3) yorliqlar
        for ((id, v) in map(db, c.path("lb"))) {
            val o = (v as? Map<*, *>) ?: continue
            val res = LinkedHashMap<String, Any?>()
            try {
                res["q"] = hooks.printLabels(B64.decode(o["b"]?.toString().orEmpty()))
                res["ok"] = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                res["ok"] = false
                res["e"] = (e.message ?: e.javaClass.simpleName).take(200)
            }
            res["at"] = CloudDb.SERVER_TIME
            runCatching { io { db.put(c.path("lbr/$id"), res); db.delete(c.path("lb/$id")) } }.onSuccess { lab++; done++ }
        }
        // 4) katalog: agent so'rasa yoki 20 daqiqada bir
        val asked = (runCatching { io { db.get(c.path("creq")) } }.getOrNull() as? Number)?.toLong() ?: 0L
        if (forceCatalog || asked > catalogAt || now() - catalogAt > catalogPeriodMs()) {
            runCatching { publishCatalog(db, c) }.onFailure { e -> if (e is CancellationException) throw e; error = (e.message ?: "catalog").take(200) }
        }
        // 5) 1C kodlari (kompyuterdan Admin'ga kelgani)
        hooks.codes()?.let { (at, bytes) ->
            if (at > codesSent) runCatching {
                io { db.update(c.path("codes"), linkedMapOf("at" to at, "b" to B64.encode(B64.gzip(bytes)))) }
                codesSent = at
            }
        }
        // 6) eski natijalarni tozalash (kuniga bir marta): 30 kundan eski
        if (now() - cleanedAt > 86_400_000L) runCatching { cleanup(db, c) }.onSuccess { cleanedAt = now() }
        // 7) holat
        val left = runCatching { io { db.keys(c.path("ob")) }.size }.getOrDefault(0)
        runCatching { io { db.put(c.path("hb"), linkedMapOf("at" to CloudDb.SERVER_TIME, "q" to left, "e" to error, "v" to version)) } }
        val s = stats
        stats = Stats(now(), s.requests + req, s.written + wrote, s.labels + lab, error, catalogAt)
        done
    }

    /** @return true — serverga yozildi (yoki avval yozilgani topildi). Server ko'rinmasa — istisno. */
    private suspend fun write(db: CloudDb, c: RelayCodec.Config, oid: String, o: Map<*, *>): Boolean {
        val u = o["u"]?.toString().orEmpty()
        val t = o["t"]?.toString().orEmpty()
        val p = o["p"]?.toString().orEmpty()
        // Avval natija bormi (qayta yuborilgan) — bo'lsa faqat navbatdan olib tashlanadi
        if (io { db.get(c.path("obr/$oid")) } != null) { io { db.delete(c.path("ob/$oid")) }; return false }
        if (u.isEmpty() || t !in setOf("1", "4") || RelayCodec.oid(u, t, p) != oid) {
            io { db.put(c.path("obr/$oid"), linkedMapOf("at" to CloudDb.SERVER_TIME, "st" to "rej", "m" to "bad_item")); db.delete(c.path("ob/$oid")) }
            return false
        }
        val id = try {
            // Admin yozib, natijani joylashga ulgurmagan bo'lsa — serverdagi o'sha yozuv topiladi (dublikat yo'q)
            server.find(u, t, p) ?: server.insert(u, t, p)
        } catch (e: RejectedException) {
            io { db.put(c.path("obr/$oid"), linkedMapOf("at" to CloudDb.SERVER_TIME, "st" to "rej", "m" to (e.message ?: "rejected").take(300))); db.delete(c.path("ob/$oid")) }
            return false
        }
        io { db.put(c.path("obr/$oid"), linkedMapOf("at" to CloudDb.SERVER_TIME, "st" to "ok", "id" to id)) }
        io { db.delete(c.path("ob/$oid")) }
        // 2-filial cheki ko'zgusi (savdo bo'lsa)
        o["m"]?.toString()?.let { RelayCodec.saleFromJson(it) }?.let { sale ->
            runCatching { hooks.mirrorSale(sale, "r" + oid.take(16)) }
        }
        return true
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun answer(op: String, p: String): Any? {
        val o = runCatching { JObj.parse(p) }.getOrElse { JObj(emptyMap()) }
        return when (op) {
            "login" -> {
                val (agent, pass) = server.agentByPhone(o.str("ph")) ?: return linkedMapOf<String, Any?>("a" to null)
                val expect = withContext(Dispatchers.Default) { RelayCodec.proof(pass, o.str("s")) }
                if (!RelayCodec.sameProof(expect, o.str("pr"))) linkedMapOf<String, Any?>("a" to null)
                else linkedMapOf("a" to listOf(agent.id, agent.fullName, agent.boss, agent.phone))
            }
            "debt" -> server.fetchDebt(o.str("a"), o.str("st")).let { linkedMapOf("s" to it.first, "u" to it.second) }
            "report" -> server.fetchReport(o.str("a"), o.str("p"))
            "time" -> server.serverTimeMillis()
            "bc" -> hooks.barcode(o.str("i"), o.str("n"), o.str("c"))
            else -> throw IllegalArgumentException("unknown_op")
        }
    }

    /** Katalogni serverdan olib, agentlar uchun joylaydi. */
    suspend fun publishCatalog(db: CloudDb = cloud(config()?.dbUrl.orEmpty()), c: RelayCodec.Config? = config()) {
        val cfg = c?.takeIf { it.isValid } ?: return
        val snap = server.fetchSnapshot(hooks.adminAgentId())
        val agents = server.agents()
        val at = now()
        // "at" ham blob ichida — o'zgarish faqat ma'lumotdan aniqlanadi (vaqtsiz)
        val (blob, hash) = withContext(Dispatchers.Default) {
            RelayCodec.pack(RelayCodec.catalogToJson(snap, agents, at)) to RelayCodec.catalogToJson(snap, agents, 0L).hashCode()
        }
        // O'zgarmagan bo'lsa faqat vaqt yangilanadi (trafik tejaladi; agent "yangi" ekanini vaqtdan biladi)
        if (hash == catalogHash) io { db.update(cfg.path("cat"), linkedMapOf("at" to at)) }
        else io { db.update(cfg.path("cat"), linkedMapOf("at" to at, "b" to blob)) }
        catalogHash = hash
        catalogAt = at
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun cleanup(db: CloudDb, c: RelayCodec.Config) {
        val old = now() - 30L * 86_400_000L
        for (path in listOf("obr", "lbr", "rs")) {
            for ((id, v) in map(db, c.path(path))) {
                val at = ((v as? Map<String, Any?>)?.get("at") as? Number)?.toLong() ?: 0L
                val limit = if (path == "obr") old else now() - 86_400_000L
                if (at in 1 until limit) runCatching { io { db.delete(c.path("$path/$id")) } }
            }
        }
    }
}
