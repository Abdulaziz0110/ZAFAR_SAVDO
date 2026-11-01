package com.ilxomjon.dur_novvot_dokon.data.relay

import com.ilxomjon.dur_novvot_dokon.data.local.CatalogSnapshot
import com.ilxomjon.dur_novvot_dokon.data.local.RawAgent
import com.ilxomjon.dur_novvot_dokon.data.remote.Backend
import com.ilxomjon.dur_novvot_dokon.data.remote.ServerError
import com.ilxomjon.dur_novvot_dokon.data.remote.ServerException
import com.ilxomjon.dur_novvot_dokon.domain.SentOrder
import com.ilxomjon.dur_novvot_dokon.domain.ServerConfig
import com.ilxomjon.dur_novvot_dokon.domain.outbox.NotSentException
import com.ilxomjon.dur_novvot_dokon.domain.outbox.RejectedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import uz.imsoft.shared.B64
import uz.imsoft.shared.CloudDb
import uz.imsoft.shared.JObj
import uz.imsoft.shared.Json
import java.io.File

/**
 * Agent: "Admin orqali" — SQL server o'rniga Firebase'dagi ko'prik ([RelayCodec]). Faqat internet kerak.
 * Server ishini Admin telefoni bajaradi ([RelayHost]); Admin internetda bo'lmasa savdolar navbatda kutadi.
 *
 * Savdo/to'lov ([insert]) "yuborildi" deb faqat Admin serverga yozib, server ID'sini qaytargandan keyin hisoblanadi.
 */
class RelayClient(
    private val config: () -> RelayCodec.Config?,
    private val cloud: (String) -> CloudDb = { CloudDb(it) },
    /** 2-filial cheki ko'zgusi saqlanadigan papka (null — saqlanmaydi). */
    private val saleDir: File? = null,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val now: () -> Long = { System.currentTimeMillis() },
) : Backend {
    /** Admin yuborgan 1C kodlari (PLU/shtrix/narx turlari) — yorliqlar uchun. */
    var onCodes: (suspend (ByteArray) -> Unit)? = null

    @Volatile private var codesAt: Long = 0L

    val isConfigured: Boolean get() = config()?.isValid == true

    class Status(val at: Long, val queue: Int, val error: String, val version: String)

    private fun ctx(): Pair<CloudDb, RelayCodec.Config> {
        val c = config()?.takeIf { it.isValid } ?: throw ServerException(ServerError.NOT_CONFIGURED)
        return cloud(c.dbUrl) to c
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    /** Admin holati (heartbeat). Admin hali yoqilmagan bo'lsa null. */
    suspend fun status(): Status? {
        val (db, c) = ctx()
        val o = io { db.get(c.path("hb")) } as? Map<*, *> ?: return null
        val j = JObj(o.entries.associate { it.key.toString() to it.value })
        return Status(j.long("at"), j.long("q").toInt(), j.str("e"), j.str("v"))
    }

    override suspend fun testConnection(cfg: ServerConfig): String {
        val st = try { status() } catch (e: ServerException) { throw e } catch (e: Exception) { throw ServerException(ServerError.CONNECT, e.message, e) }
            ?: throw ServerException(ServerError.CONNECT, "admin_never")
        return "admin:" + st.at
    }

    private fun signal(db: CloudDb, c: RelayCodec.Config) { runCatching { db.put(c.path("sig"), CloudDb.SERVER_TIME) } }

    /** v3.2: tovarga bog'langan yangi shtrix kod Admin orqali 1C'ga. */
    suspend fun sendBarcode(productId: String, name: String, code: String): Boolean =
        rpc("bc", mapOf("i" to productId, "n" to name.take(120), "c" to code.take(40)), 30_000) == true

    /** Admin'ga so'rov va javobni kutish. Admin javob bermasa [ServerError.TIMEOUT] ("admin_offline"). */
    private suspend fun rpc(op: String, params: Map<String, Any?>, timeoutMs: Long): Any? {
        val (db, c) = ctx()
        val rid = CloudDb.newKey()
        try {
            io { db.put(c.path("rq/$rid"), linkedMapOf("at" to CloudDb.SERVER_TIME, "op" to op, "p" to Json.write(params))) }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            throw ServerException(ServerError.CONNECT, e.message, e)
        }
        io { signal(db, c) }
        val deadline = now() + timeoutMs
        var wait = 600L
        while (now() < deadline) {
            sleep(wait)
            wait = minOf(wait + 200, 1500L)
            val r = runCatching { io { db.get(c.path("rs/$rid")) } }.getOrNull() as? Map<*, *> ?: continue
            runCatching { io { db.delete(c.path("rs/$rid")) } }
            if (r["ok"] == true) return r["r"]
            throw ServerException(if (r["e"] == "rejected") ServerError.REJECTED else ServerError.QUERY, r["e"]?.toString() ?: "admin_error")
        }
        throw ServerException(ServerError.TIMEOUT, "admin_offline")
    }

    override suspend fun login(phone: String, password: String): RawAgent? {
        val salt = CloudDb.newKey()
        val proof = withContext(Dispatchers.Default) { RelayCodec.proof(password, salt) }
        val r = rpc("login", mapOf("ph" to phone, "s" to salt, "pr" to proof), 40_000) as? Map<*, *> ?: return null
        val a = r["a"] as? List<*> ?: return null
        fun at(i: Int) = a.getOrNull(i)?.toString().orEmpty()
        return RawAgent(at(0), at(1), at(2), at(3))
    }

    /**
     * Katalog: avval Admin'dan yangilashni so'raydi (u serverdan oladi), 25 soniyagacha kutadi; kelmasa — oxirgi joylangani
     * (vaqti bilan, [CatalogSnapshot.fetchedAt]). Hech qachon joylanmagan bo'lsa — xato.
     */
    override suspend fun fetchSnapshot(agentId: String): CatalogSnapshot {
        val (db, c) = ctx()
        val asked = now()
        try {
            io { db.put(c.path("creq"), CloudDb.SERVER_TIME) }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            throw ServerException(ServerError.CONNECT, e.message, e)
        }
        io { signal(db, c) }
        var meta: Long = readCatAt(db, c)
        val deadline = now() + 25_000
        while (meta < asked - 120_000 && now() < deadline) {
            // Admin yangisini joylaguncha (soatlar farqi uchun 2 daqiqa zaxira)
            sleep(1500)
            meta = readCatAt(db, c)
        }
        val blob = io { db.get(c.path("cat/b")) } as? String ?: throw ServerException(ServerError.CONNECT, "admin_no_catalog")
        val cat = withContext(Dispatchers.Default) { RelayCodec.catalogFromJson(RelayCodec.unpack(blob)) }
        runCatching { pullCodes(db, c) }
        val me = cat.agents.firstOrNull { it.id == agentId }
        // Ma'lumot o'zgarmagan bo'lsa Admin faqat vaqtni yangilaydi — "qachon tekshirilgan" shu vaqt
        val fresh = maxOf(cat.at, readCatAt(db, c))
        return CatalogSnapshot(cat.snapshot.groups, cat.snapshot.regions, cat.snapshot.stores, cat.snapshot.rates, cat.snapshot.products, me, fresh)
    }

    private suspend fun readCatAt(db: CloudDb, c: RelayCodec.Config): Long =
        (runCatching { io { db.get(c.path("cat/at")) } }.getOrNull() as? Number)?.toLong() ?: 0L

    private suspend fun pullCodes(db: CloudDb, c: RelayCodec.Config) {
        val at = (io { db.get(c.path("codes/at")) } as? Number)?.toLong() ?: return
        if (at <= codesAt) return
        val b = io { db.get(c.path("codes/b")) } as? String ?: return
        val bytes = B64.gunzip(B64.decode(b))
        onCodes?.invoke(bytes)
        codesAt = at
    }

    override suspend fun fetchDebt(agentId: String, storeId: String): Pair<String, String> {
        val r = rpc("debt", mapOf("a" to agentId, "st" to storeId), 45_000) as? Map<*, *> ?: throw ServerException(ServerError.QUERY, "bad_answer")
        return (r["s"]?.toString() ?: "0") to (r["u"]?.toString() ?: "0")
    }

    override suspend fun fetchReport(agentId: String, payload: String): String =
        rpc("report", mapOf("a" to agentId, "p" to payload), 100_000)?.toString() ?: throw ServerException(ServerError.QUERY, "bad_answer")

    override suspend fun serverTimeMillis(): Long =
        (rpc("time", emptyMap(), 30_000) as? Number)?.toLong() ?: throw ServerException(ServerError.QUERY, "bad_answer")

    // ---------------- OutboxSink ----------------

    /** Ko'prikda watermark kerak emas: har savdoning o'z kaliti bor ([RelayCodec.oid]). */
    override suspend fun maxRequestId(userId: String): Long = 0L

    /**
     * Savdo/to'lovni Admin navbatiga qo'yadi (takror qo'yilsa ham bitta yozuv) va server natijasini 15 soniya kutadi.
     * Natija kelmasa [NotSentException]: navbat keyinroq qayta urinadi — yana shu kalit bilan, dublikat bo'lmaydi.
     */
    override suspend fun insert(userId: String, type: String, payload: String): String {
        val (db, c) = try { ctx() } catch (e: ServerException) { throw NotSentException(e.message, e) }
        val oid = RelayCodec.oid(userId, type, payload)
        push(db, c, oid, userId, type, payload)
        io { signal(db, c) }
        val deadline = now() + 15_000
        while (now() < deadline) {
            sleep(1000)
            when (val r = result(db, c, oid)) {
                null -> continue
                else -> return r
            }
        }
        throw NotSentException("admin_wait")
    }

    /** Admin natijasi: server ID; rad etilgan bo'lsa [RejectedException]; hali yo'q — null. */
    private suspend fun result(db: CloudDb, c: RelayCodec.Config, oid: String): String? {
        val r = runCatching { io { db.get(c.path("obr/$oid")) } }.getOrNull() as? Map<*, *> ?: return null
        return when (r["st"]) {
            "ok" -> r["id"]?.toString()?.also { saleFile(oid)?.delete() }
            "rej" -> throw RejectedException(r["m"]?.toString() ?: "rejected")
            else -> null
        }
    }

    private suspend fun push(db: CloudDb, c: RelayCodec.Config, oid: String, userId: String, type: String, payload: String) {
        val item = linkedMapOf<String, Any?>("at" to CloudDb.SERVER_TIME, "u" to userId, "t" to type, "p" to payload)
        saleFile(oid)?.takeIf { it.isFile }?.let { f -> runCatching { f.readText() }.getOrNull()?.let { item["m"] = it } }
        try {
            io { db.put(c.path("ob/$oid"), item) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: CloudDb.CloudException) {
            // Qoida: yozuv bor bo'lsa qayta yozib bo'lmaydi — demak avval yuborilgan (dublikat emas)
            if (!e.denied) throw NotSentException(e.message, e)
        } catch (e: Exception) {
            throw NotSentException(e.message, e)
        }
    }

    /** Navbatdagi hamma savdolarni Admin'ga oldindan joylash (agent internetdan uzilsa ham Admin ularni yozadi). */
    suspend fun pushAll(items: List<Triple<String, String, String>>) {
        if (items.isEmpty()) return
        val (db, c) = ctx()
        for ((u, t, p) in items) runCatching { push(db, c, RelayCodec.oid(u, t, p), u, t, p) }
        io { signal(db, c) }
    }

    override suspend fun find(userId: String, type: String, payload: String, afterId: Long?): String? {
        val (db, c) = try { ctx() } catch (e: ServerException) { throw NotSentException(e.message, e) }
        val oid = RelayCodec.oid(userId, type, payload)
        val r = try { io { db.get(c.path("obr/$oid")) } } catch (e: CancellationException) { throw e } catch (e: Exception) { throw NotSentException(e.message, e) }
        val m = r as? Map<*, *> ?: return null
        return if (m["st"] == "ok") m["id"]?.toString() else null
    }

    /** Ko'prik natijalari 30 kun saqlanadi — qidiruv ishonchli. */
    override suspend fun exists(requestId: String): Boolean = true

    // ---------------- 2-filial cheki ko'zgusi ----------------

    private fun saleFile(oid: String): File? = saleDir?.let { File(it, "$oid.json") }

    /** Savdo navbatga qo'yilganda: chek ma'lumoti savdo bilan birga Admin'ga boradi (Admin 2-ilovaga yuboradi). */
    fun rememberSale(agentId: String, payload: String, sent: SentOrder, code: String) {
        val dir = saleDir ?: return
        dir.mkdirs()
        val oid = RelayCodec.oid(agentId, "1", payload)
        File(dir, "$oid.json").writeText(RelayCodec.saleToJson(sent, code))
        dir.listFiles()?.forEach { if (now() - it.lastModified() > 14L * 86_400_000L) it.delete() }
    }

    // ---------------- yorliq ----------------

    /** Yorliq: TSPL baytlari Admin orqali Xprinter'ga. Natijani 60 soniya kutadi. */
    suspend fun printLabels(data: ByteArray, copies: Int): String {
        val (db, c) = ctx()
        val id = CloudDb.newKey()
        io { db.put(c.path("lb/$id"), linkedMapOf("at" to CloudDb.SERVER_TIME, "n" to copies, "b" to B64.encode(data))) }
        io { signal(db, c) }
        val deadline = now() + 60_000
        while (now() < deadline) {
            sleep(1200)
            val r = runCatching { io { db.get(c.path("lbr/$id")) } }.getOrNull() as? Map<*, *> ?: continue
            if (r["ok"] == true) return r["q"]?.toString().orEmpty()
            throw ServerException(ServerError.QUERY, r["e"]?.toString() ?: "label_error")
        }
        throw ServerException(ServerError.TIMEOUT, "admin_offline")
    }
}
