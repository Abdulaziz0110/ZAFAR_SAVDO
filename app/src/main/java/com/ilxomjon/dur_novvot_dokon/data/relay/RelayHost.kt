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
        /** v3.2: agent to'lovi serverga yozildi — 2-ilova tarixiga va qarzni yangilash. */
        suspend fun mirrorPayment(storeId: String, cash: Double, transfer: Double, click: Double, note: String, key: String) = Unit
        /** v3.2: agent tovarga yangi shtrix kod bog'ladi — Admin 1C'ga yozish navbatiga qo'yadi. */
        suspend fun barcode(productId: String, name: String, code: String): Boolean = false
        /** v3.12: "Shtrix kod → 1C" ro'yxati birdaniga: (tovar id, kod). @return navbatga qo'yilganlar soni. */
        suspend fun barcodes(items: List<Pair<String, String>>): Int = 0
        /** v4.0: chek Mijoz ilovasi (filial) printerida chiqishi mumkin bo'lgan do'konlar id'si. */
        fun branchStores(): List<String> = emptyList()
        /** v4.0: savdo/to'lov serverga yozildi (agent statistikasi uchun). */
        fun written(agentId: String, type: String, payload: String) = Unit
        /** v4.0: oxirgi kirim narxi (tovar id -> so'm, $). */
        fun lastCosts(): Map<String, Pair<Double, Double>> = emptyMap()
        /** v4.0: Admin bloklagan qurilma (undan kelgan savdo/to'lov qabul qilinmaydi). */
        fun blockedDevice(deviceId: String): Boolean = false
        /** v3.10: 1- va 2-narx (so'm, 1C'dan) — iPhone (veb) agentlar uchun: tovar id -> [1-narx, 2-narx]. */
        fun level12(productIds: List<String>): Map<String, List<Double>> = emptyMap()
        /** v5.3: telefondagi katalogdagi tovar id'lari (1/2-narxni qo'shish uchun). */
        fun productIds(): List<String> = emptyList()
        /** v5.3: bloklangan qurilmalar id'si — kompyuter ko'prigi ham ulardan qabul qilmasin. */
        fun blockedDevices(): List<String> = emptyList()
        /**
         * v5.3: kompyuter ko'prigi sozlamasi: (kirish qutisi kaliti, {key, host, database, user, password}).
         * Kompyuter shu bilan SQL serverga o'zi ulanadi — Admin telefoni o'chiq bo'lsa ham agentlar ishlaydi.
         */
        fun pcLink(): Pair<String, Map<String, String>>? = null
        /** v5.3: yangilanish manzili — kompyuter dasturi ham o'zi yangilanadi. */
        fun updateUrl(): String = ""
    }

    class Stats(val at: Long = 0L, val requests: Int = 0, val written: Int = 0, val labels: Int = 0, val error: String = "", val catalogAt: Long = 0L)

    /** v3.2: katalogni agentlarga qayta joylash oralig'i (ish vaqtida tezroq). Standart — 20 daqiqa. */
    @Volatile var catalogPeriodMs: () -> Long = { 20 * 60_000L }

    @Volatile var stats = Stats()
        private set
    private val mutex = Mutex()
    @Volatile private var catalogAt = 0L
    @Volatile private var catalogHash = 0
    @Volatile private var levelHash = 0
    @Volatile private var codesSent = 0L
    @Volatile private var cleanedAt = 0L
    @Volatile private var auxHash = 0
    @Volatile private var linkHash = 0

    /** v5.3: ko'prikni hozir do'kon kompyuteri yurityaptimi (oxirgi tekshiruv natijasi). */
    @Volatile var pcHost: Boolean = false
        private set

    val isConfigured: Boolean get() = config()?.isValid == true

    /** pc 35: Admin telefoni kompyuterni to'g'ridan-to'g'ri ko'rmasa (Tailscale o'chiq) — yorliq internet orqali kompyuter
     *  ko'prigiga (lb navbati); kompyuter uni Xprinter'ga chiqaradi. @return printer nomi. @throws IllegalStateException */
    suspend fun printViaPc(data: ByteArray, copies: Int, waitMs: Long = 60_000): String {
        val c = config()?.takeIf { it.isValid } ?: throw IllegalStateException("internet ulanishi sozlanmagan")
        val db = cloud(c.dbUrl)
        val hb = runCatching { map(db, c.path("hb")) }.getOrDefault(emptyMap())
        if (!pcAlive(hb)) throw IllegalStateException("kompyuter dasturi ishlamayapti (o'chiq yoki uyquda)")
        if ((hb["lb"] as? Number)?.toInt() != 1) throw IllegalStateException("kompyuter Xprinter'ni topmadi")
        val id = CloudDb.newKey()
        io { db.put(c.path("lb/$id"), linkedMapOf("at" to CloudDb.SERVER_TIME, "n" to copies, "b" to B64.encode(data))) }
        runCatching { io { db.put(c.path("sig"), CloudDb.SERVER_TIME) } }
        val deadline = now() + waitMs
        while (now() < deadline) {
            kotlinx.coroutines.delay(1200)
            val r = runCatching { io { db.get(c.path("lbr/$id")) } }.getOrNull() as? Map<*, *> ?: continue
            runCatching { io { db.delete(c.path("lbr/$id")) } }
            if (r["ok"] == true) return r["q"]?.toString().orEmpty()
            throw IllegalStateException(r["e"]?.toString() ?: "printer xatosi")
        }
        runCatching { io { db.delete(c.path("lb/$id")) } }
        throw IllegalStateException("kompyuter javob bermadi")
    }

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
        // v5.3: kompyuter ko'prigi ishlab turgan bo'lsa — so'rov, savdo va katalogni U bajaradi (ikki joydan yozilmasin).
        // Kompyuter o'chiq/uyquda bo'lsa (holati eskirgan) — Admin o'zi bajaradi, avvalgidek.
        runCatching { publishLink(db, c) }
        val hb = runCatching { map(db, c.path("hb")) }.getOrDefault(emptyMap())
        val pc = pcAlive(hb)
        pcHost = pc
        // pc 28: kompyuter yorliq printerini topgan bo'lsa — agent yorliqlarini U chop etadi
        val pcLabels = pc && (hb["lb"] as? Number)?.toInt() == 1
        // 1) so'rovlar
        if (!pc) for ((id, v) in map(db, c.path("rq"))) {
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
        val queue = if (pc) emptyList() else map(db, c.path("ob")).entries.sortedBy { ((it.value as? Map<*, *>)?.get("at") as? Number)?.toLong() ?: Long.MAX_VALUE }
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
        if (!pcLabels) for ((id, v) in map(db, c.path("lb"))) {
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
        // 3a) v5.3: kompyuter yozgan savdo/to'lovlarning izi — statistika, 2-ilova tarixi va filial cheki
        for ((oid, v) in map(db, c.path("mr"))) {
            val o = (v as? Map<*, *>) ?: continue
            runCatching {
                afterWritten(o["u"]?.toString().orEmpty(), o["t"]?.toString().orEmpty(), o["p"]?.toString().orEmpty(), o["m"]?.toString(), oid)
                io { db.delete(c.path("mr/$oid")) }
            }.onSuccess { done++ }.onFailure { e -> if (e is CancellationException) throw e }
        }
        // 3b) v5.3: agent yuborgan shtrix kodlar (kompyuter saqlab qo'ygan) — 1C'ga yozish navbatiga
        for ((id, v) in map(db, c.path("bq"))) {
            val o = (v as? Map<*, *>) ?: continue
            val ok = try {
                answer(o["op"]?.toString().orEmpty(), o["p"]?.toString().orEmpty()).let { it == true || ((it as? Number)?.toInt() ?: 0) > 0 }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (ok) runCatching { io { db.delete(c.path("bq/$id")) } }.onSuccess { done++ }
        }
        // 3c) v5.3: kompyuter ko'prigi uchun qo'shimcha ma'lumot (filial do'konlari, oxirgi kirim narxi, bloklanganlar)
        runCatching { publishAux(db, c) }
        // 4) katalog: agent so'rasa yoki 20 daqiqada bir
        val asked = (runCatching { io { db.get(c.path("creq")) } }.getOrNull() as? Number)?.toLong() ?: 0L
        if (!pc && (forceCatalog || asked > catalogAt || now() - catalogAt > catalogPeriodMs())) {
            runCatching { publishCatalog(db, c) }.onFailure { e -> if (e is CancellationException) throw e; error = (e.message ?: "catalog").take(200) }
        }
        // 4a) v5.3: kompyuter katalog qo'yganda 1/2-narx (veb-agent uchun) Admin'dan qo'shiladi
        if (pc && now() - levelAt > 10 * 60_000L) runCatching { publishLevels(db, c) }.onSuccess { levelAt = now() }
        // 5) 1C kodlari (kompyuterdan Admin'ga kelgani)
        if (!pc) hooks.codes()?.let { (at, bytes) ->
            if (at > codesSent) runCatching {
                io { db.update(c.path("codes"), linkedMapOf("at" to at, "b" to B64.encode(B64.gzip(bytes)))) }
                codesSent = at
            }
        }
        // 6) eski natijalarni tozalash (kuniga bir marta): 30 kundan eski
        if (now() - cleanedAt > 86_400_000L) runCatching { cleanup(db, c) }.onSuccess { cleanedAt = now() }
        // 7) holat
        val left = runCatching { io { db.keys(c.path("ob")) }.size }.getOrDefault(0)
        if (!pc) runCatching { io { db.put(c.path("hb"), linkedMapOf("at" to CloudDb.SERVER_TIME, "q" to left, "e" to error, "v" to version)) } }
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
        val dev = o["d"]?.toString().orEmpty()
        if (dev.isNotEmpty() && hooks.blockedDevice(dev)) {
            io { db.put(c.path("obr/$oid"), linkedMapOf("at" to CloudDb.SERVER_TIME, "st" to "rej", "m" to "blocked")); db.delete(c.path("ob/$oid")) }
            return false
        }
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
        // Admin'ning o'z savdosi (bulut orqali ketgan) — ko'zgusi o'sha paytda qilingan
        if (dev != RelayCodec.OWN_DEVICE) afterWritten(u, t, p, o["m"]?.toString(), oid)
        return true
    }

    /** Savdo/to'lov serverga yozilgandan keyin: statistika, 2-filial cheki va 2-ilova tarixi. */
    private suspend fun afterWritten(u: String, t: String, p: String, m: String?, oid: String) {
        runCatching { hooks.written(u, t, p) }
        // 2-filial cheki ko'zgusi (savdo bo'lsa)
        m?.let { RelayCodec.saleFromJson(it) }?.let { sale ->
            runCatching { hooks.mirrorSale(sale, "r" + oid.take(16)) }
        }
        // v3.2: to'lov — 2-ilova tarixida va qarz yangilanishi (protokol: agent^do'kon^naqd^0^vaqt^kurs^izoh^naqd^o'tkazma^click^printer~)
        if (t == "4") runCatching {
            val f = p.substringBefore('~').split('^')
            fun d(i: Int) = f.getOrNull(i)?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
            val store = f.getOrNull(1).orEmpty()
            if (store.isNotEmpty()) hooks.mirrorPayment(store, d(7).takeIf { f.size > 7 } ?: d(2), d(8), d(9), f.getOrNull(6).orEmpty(), "r" + oid.take(16))
        }
    }

    @Volatile private var levelAt = 0L

    /**
     * v5.3: kompyuter ko'prigi uchun sozlama va qo'shimcha ma'lumotni yuborish (ko'prik xizmati o'chiq bo'lsa ham).
     * Admin ilovasi ochilganda chaqiriladi — o'zgarmagan bo'lsa hech narsa yubormaydi.
     */
    suspend fun syncPc() {
        val c = config() ?: return
        val db = cloud(c.dbUrl)
        runCatching { publishLink(db, c) }.onFailure { if (it is CancellationException) throw it }
        runCatching { publishAux(db, c) }.onFailure { if (it is CancellationException) throw it }
        pcHost = runCatching { pcAlive(map(db, c.path("hb"))) }.getOrDefault(false)
    }

    /** Kompyuter ko'prigi tirikmi: holatini O'ZI yozgan va 4 daqiqadan eski emas. */
    fun pcAlive(hb: Map<String, Any?>): Boolean {
        if (hb["h"]?.toString() != HOST_PC) return false
        val at = (hb["at"] as? Number)?.toLong() ?: return false
        return kotlin.math.abs(now() - at) < PC_FRESH_MS
    }

    private suspend fun publishAux(db: CloudDb, c: RelayCodec.Config) {
        val lc = hooks.lastCosts()
        val aux = linkedMapOf<String, Any?>(
            "br" to hooks.branchStores(),
            "lc" to lc.entries.sortedBy { it.key }.associate { (k, v) -> k to listOf(v.first, v.second) },
            "bl" to hooks.blockedDevices().sorted(),
        )
        val h = uz.imsoft.shared.Json.write(aux).hashCode()
        if (h == auxHash) return
        io { db.put(c.path("ax"), aux) }
        auxHash = h
    }

    private suspend fun publishLink(db: CloudDb, c: RelayCodec.Config) {
        val (inbox, link) = hooks.pcLink() ?: return
        if (!CloudDb.isKey(inbox)) return
        val url = hooks.updateUrl()
        val all = LinkedHashMap<String, String>(link).also { it["key"] = c.key }
        val h = (all.toString() + url).hashCode()
        if (h == linkHash) return
        io {
            db.put("p/$inbox/rl", all)
            if (url.startsWith("https://")) db.put("p/$inbox/upd", url)
        }
        linkHash = h
    }

    private suspend fun publishLevels(db: CloudDb, c: RelayCodec.Config) {
        val ids = hooks.productIds()
        if (ids.isEmpty()) return
        val l12 = hooks.level12(ids)
        val q = withContext(Dispatchers.Default) { RelayCodec.pack(uz.imsoft.shared.Json.write(l12)) }
        if (q.hashCode() != levelHash) { io { db.update(c.path("cat"), linkedMapOf("q" to q)) }; levelHash = q.hashCode() }
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
            "bcs" -> hooks.barcodes(o.arr("l").mapNotNull { x ->
                val l = x as? List<Any?> ?: return@mapNotNull null
                val id = l.getOrNull(0)?.toString().orEmpty()
                val code = l.getOrNull(1)?.toString().orEmpty()
                if (id.isEmpty() || code.isEmpty()) null else id to code
            }.take(300))
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
            val br = hooks.branchStores()
            val lc = hooks.lastCosts()
            RelayCodec.pack(RelayCodec.catalogToJson(snap, agents, at, br, lc)) to RelayCodec.catalogToJson(snap, agents, 0L, br, lc).hashCode()
        }
        // O'zgarmagan bo'lsa faqat vaqt yangilanadi (trafik tejaladi; agent "yangi" ekanini vaqtdan biladi)
        if (hash == catalogHash) io { db.update(cfg.path("cat"), linkedMapOf("at" to at)) }
        else io { db.update(cfg.path("cat"), linkedMapOf("at" to at, "b" to blob)) }
        catalogHash = hash
        // v3.10: 1/2-narx (Android agent o'zi kompyuter kodlaridan hisoblaydi; veb-agent shu yerdan oladi)
        runCatching {
            val l12 = hooks.level12(snap.products.map { it.id })
            val q = withContext(Dispatchers.Default) { RelayCodec.pack(uz.imsoft.shared.Json.write(l12)) }
            if (q.hashCode() != levelHash) { io { db.update(cfg.path("cat"), linkedMapOf("q" to q)) }; levelHash = q.hashCode() }
        }
        catalogAt = at
    }

    @Suppress("UNCHECKED_CAST")
    companion object {
        const val HOST_PC = "pc"
        const val PC_FRESH_MS = 4 * 60_000L
    }

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
