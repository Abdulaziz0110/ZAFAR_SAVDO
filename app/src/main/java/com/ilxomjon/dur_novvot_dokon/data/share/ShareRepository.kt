package com.ilxomjon.dur_novvot_dokon.data.share

import android.content.Context
import com.ilxomjon.dur_novvot_dokon.data.remote.Protocol
import com.ilxomjon.dur_novvot_dokon.data.remote.ServerApi
import com.ilxomjon.dur_novvot_dokon.data.repo.CatalogRepository
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.PriceType
import com.ilxomjon.dur_novvot_dokon.domain.Pricing
import com.ilxomjon.dur_novvot_dokon.domain.Product
import com.ilxomjon.dur_novvot_dokon.domain.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uz.imsoft.shared.CloudDb
import uz.imsoft.shared.JObj
import uz.imsoft.shared.Json
import uz.imsoft.shared.LinkCode
import uz.imsoft.shared.report.CardParser
import uz.imsoft.shared.report.CardReport
import uz.imsoft.shared.report.ClientSnapshot
import uz.imsoft.shared.report.Coverage
import uz.imsoft.shared.report.DebtInfo
import uz.imsoft.shared.report.EntryKind
import uz.imsoft.shared.report.HtmlTable
import uz.imsoft.shared.report.LedgerEntry
import uz.imsoft.shared.report.LedgerMerge
import uz.imsoft.shared.report.Money2
import uz.imsoft.shared.report.PriceItem
import uz.imsoft.shared.report.SnapshotCodec
import uz.imsoft.shared.report.TableReader
import java.io.File
import java.util.Date

/** Bitta import yozuvi (jurnal). */
data class ImportLog(val at: Long, val name: String, val source: String, val added: Int, val removed: Int, val same: Int, val from: Long, val to: Long)

data class ShareState(
    val configured: Boolean = false,
    val dbUrl: String = "",
    val hasSecret: Boolean = false,
    val clientId: String = "",
    val clientName: String = "",
    val entries: Int = 0,
    val coverage: List<Coverage> = emptyList(),
    val log: List<ImportLog> = emptyList(),
    val lastPublishAt: Long = 0L,
    val lastPublishError: String = "",
    val dirty: Boolean = false,
    val busy: Boolean = false,
    val rulesOk: Boolean = false,
    val autoPublish: Boolean = true,
    val answerRequests: Boolean = true,
    val hasQueue: Boolean = false,
)

/** Import oldidan ko'rsatiladigan natija. Saqlash [ShareRepository.commit] bilan. */
class ImportPreview(
    val report: CardReport,
    val name: String,
    val source: String,
    val merge: LedgerMerge.Result,
    /** Fayldagi mijoz nomi tanlangan mijozdan farq qilsa — o'sha nom. */
    val clientMismatch: String?,
    val matchedProducts: Int,
    val unmatchedProducts: List<String>,
)

/** Fayl jadval sifatida o'qildi, lekin "Mijoz kartochkasi"ga o'xshamadi — tashxis uchun birinchi qatorlar. */
class NotACardException(message: String, val dump: String) : Exception(message)

class PrinterStatus(val at: Long, val text: String, val queue: Int, val printerReady: Boolean)

/**
 * 1-ilova -> 2-ilova ulashish: 1C hisobotlarini import qilish, narxlar va qarzni yig'ib, Firebase'ga yuborish.
 * Ma'lumot telefonda alohida faylda (filesDir/share/) saqlanadi — mavjud bazalarga tegmaydi.
 */
class ShareRepository(
    private val context: Context,
    private val store: ShareStore,
    private val catalog: CatalogRepository,
    private val api: ServerApi,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(ShareState())
    val state: StateFlow<ShareState> = _state.asStateFlow()

    private var entries: List<LedgerEntry> = emptyList()
    private var coverage: List<Coverage> = emptyList()
    private var log: List<ImportLog> = emptyList()
    /** Eng oxirgi import qilingan hisobotdagi rasmiy qoldiq. */
    private var balance: Pair<Long, Money2>? = null
    private var loadedFor: String? = null

    private fun dir() = File(context.filesDir, "share").apply { mkdirs() }
    private fun file(clientId: String) = File(dir(), "ledger_" + clientId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { "x" } + ".json")

    suspend fun load() = mutex.withLock { withContext(Dispatchers.IO) { loadLocked() } }

    private fun loadLocked() {
        val cid = store.clientId
        if (loadedFor != cid) {
            entries = emptyList(); coverage = emptyList(); log = emptyList(); balance = null
            val f = file(cid)
            if (cid.isNotEmpty() && f.exists()) {
                runCatching {
                    val o = JObj.parse(f.readText())
                    @Suppress("UNCHECKED_CAST")
                    entries = o.arr("l").mapNotNull { (it as? List<Any?>)?.let { a -> runCatching { SnapshotCodec.entryFromList(a) }.getOrNull() } }
                    coverage = o.objs("cov").map { Coverage(it.long("f"), it.long("t"), it.long("at"), it.str("src")) }
                    balance = (o.map["bal"] as? List<*>)?.let { l -> (l.getOrNull(0) as? Number)?.toLong()?.let { st -> st to Money2((l.getOrNull(1) as? Number)?.toDouble() ?: 0.0, (l.getOrNull(2) as? Number)?.toDouble() ?: 0.0) } }
                    log = o.objs("log").map { ImportLog(it.long("at"), it.str("n"), it.str("src"), it.long("a").toInt(), it.long("r").toInt(), it.long("s").toInt(), it.long("f"), it.long("t")) }
                }
            }
            loadedFor = cid
        }
        publishState()
    }

    private fun saveLocked() {
        val cid = store.clientId
        if (cid.isEmpty()) return
        val json = Json.write(
            linkedMapOf(
                "v" to 1,
                "l" to entries.map { SnapshotCodec.entryToList(it) },
                "cov" to coverage.map { linkedMapOf("f" to it.from, "t" to it.to, "at" to it.importedAt, "src" to it.source) },
                "bal" to balance?.let { listOf(it.first, it.second.som, it.second.usd) },
                "log" to log.take(30).map { linkedMapOf("at" to it.at, "n" to it.name, "src" to it.source, "a" to it.added, "r" to it.removed, "s" to it.same, "f" to it.from, "t" to it.to) },
            ),
        )
        val f = file(cid)
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    private fun publishState(busy: Boolean = _state.value.busy) {
        _state.value = ShareState(
            configured = store.isConfigured, dbUrl = store.dbUrl, hasSecret = store.secret.isNotEmpty(),
            clientId = store.clientId, clientName = store.clientName, entries = entries.size, coverage = coverage, log = log,
            lastPublishAt = store.lastPublishAt, lastPublishError = store.lastPublishError, dirty = store.dirty, busy = busy,
            rulesOk = store.rulesOk, autoPublish = store.autoPublish, answerRequests = store.answerRequests, hasQueue = store.queueKey.isNotEmpty(),
        )
    }

    private inline fun <T> busy(block: () -> T): T {
        _state.update { it.copy(busy = true) }
        try { return block() } finally { _state.update { it.copy(busy = false) } }
    }

    // ---------------- sozlash ----------------

    /** Firebase manzili va secret: qoidalarni o'rnatadi va ruxsatlarni tekshiradi. */
    suspend fun saveFirebase(url: String, secret: String) = busy {
        val u = CloudDb.normalizeUrl(url)
        require(CloudDb.looksLikeFirebase(u)) { "bad_url" }
        withContext(Dispatchers.IO) {
            val db = CloudDb(u, secret.trim())
            db.setRules(CloudDb.RULES)
            // Kalitsiz (2-ilova kabi) ildizni o'qib bo'lmasligi kerak — qoidalar ishlayotganini tekshiramiz.
            val anon = CloudDb(u, "")
            val rootOpen = runCatching { anon.get("", mapOf("shallow" to "true")); true }.getOrDefault(false)
            if (rootOpen) throw IllegalStateException("rules_not_applied")
            store.dbUrl = u
            store.secret = secret.trim()
            store.rulesOk = true
            if (store.readKey.isEmpty()) store.readKey = CloudDb.newKey()
            if (store.queueKey.isEmpty()) store.queueKey = CloudDb.newKey()
            store.dirty = true
        }
        mutex.withLock { publishState() }
    }

    /** Mijozni tanlash. Boshqa mijozga o'tilsa o'qish kaliti yangilanadi (oldingi mijoz telefoni yangisini ko'rmasin). */
    suspend fun setClient(s: Store) = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (store.clientId != s.id) {
                if (store.clientId.isNotEmpty()) store.readKey = CloudDb.newKey()
                store.clientId = s.id
                store.lastRequestServed = 0L
            }
            store.clientName = s.name
            if (store.readKey.isEmpty()) store.readKey = CloudDb.newKey()
            if (store.queueKey.isEmpty()) store.queueKey = CloudDb.newKey()
            store.dirty = true
            loadLocked()
        }
    }

    fun setAuto(v: Boolean) { store.autoPublish = v; publishState() }
    fun setAnswer(v: Boolean) { store.answerRequests = v; publishState() }

    /** Kalitlarni almashtirish (masalan telefon yo'qolsa): eski ulanish kodi ishlamay qoladi. */
    suspend fun regenerateKeys() = mutex.withLock {
        withContext(Dispatchers.IO) {
            val old = store.readKey
            store.readKey = CloudDb.newKey()
            store.queueKey = CloudDb.newKey()
            store.dirty = true
            if (store.isConfigured && old.isNotEmpty()) runCatching { cloud().delete("s/$old") }
            publishState()
        }
    }

    fun linkCode(): String = LinkCode(store.dbUrl, store.readKey, store.queueKey, store.clientName).encode()

    /** Kompyuterdagi forwarder.py uchun sozlama fayli (secret ichida — faqat egasining kompyuteriga!). */
    fun forwarderIni(): String = "[firebase]\nurl = ${store.dbUrl}\nsecret = ${store.secret}\nqueue = ${store.queueKey}\n"

    private fun cloud() = CloudDb(store.dbUrl, store.secret)

    // ---------------- import ----------------

    suspend fun previewFile(bytes: ByteArray, name: String): ImportPreview = withContext(Dispatchers.Default) {
        val grid = TableReader.read(bytes, name)
        val report = try { CardParser.parse(grid) } catch (e: CardParser.NotACard) { throw NotACardException(e.message.orEmpty(), grid.dump(25)) }
        buildPreview(report, name, "file")
    }

    /** Server hisoboti (tur 5) — 1-ilovaning odatdagi "Hisobot ▸ Serverdan" so'rovi bilan bir xil. */
    suspend fun previewServer(from: Date, to: Date): ImportPreview {
        val agentId = catalog.state.value.agent?.id ?: throw IllegalStateException("Agent topilmadi")
        val html = api.fetchReport(agentId, Protocol.report(from, to, store.clientId))
        return withContext(Dispatchers.Default) {
            val grid = HtmlTable.read(html)
            val report = try { CardParser.parse(grid) } catch (e: CardParser.NotACard) { throw NotACardException(e.message.orEmpty(), grid.dump(25)) }
            buildPreview(report, "server", "server")
        }
    }

    private suspend fun buildPreview(report: CardReport, name: String, source: String): ImportPreview {
        mutex.withLock { withContext(Dispatchers.IO) { loadLocked() } }
        val merge = LedgerMerge.merge(entries, coverage, report, System.currentTimeMillis(), source)
        val byKey = productIndex()
        val names = report.entries.filter { it.product.isNotBlank() && (it.kind == EntryKind.SALE || it.kind == EntryKind.RETURN) }
            .map { it.product }.distinct()
        val unmatched = names.filter { byKey[LedgerMerge.nameKey(it)] == null }
        val fileClient = report.clientName
        val mismatch = if (fileClient != null && store.clientName.isNotEmpty() &&
            LedgerMerge.nameKey(fileClient) != LedgerMerge.nameKey(store.clientName)
        ) fileClient else null
        return ImportPreview(report, name, source, merge, mismatch, names.size - unmatched.size, unmatched)
    }

    suspend fun commit(p: ImportPreview) = mutex.withLock {
        withContext(Dispatchers.IO) {
            loadLocked()
            // Ko'rib chiqish paytidan beri boshqa import bo'lgan bo'lsa ham to'g'ri bo'lishi uchun qaytadan birlashtiriladi.
            val m = LedgerMerge.merge(entries, coverage, p.report, System.currentTimeMillis(), p.source)
            entries = m.entries
            coverage = m.coverage
            p.report.closing?.let { c -> if (balance == null || c.first >= balance!!.first) balance = c }
            log = listOf(ImportLog(System.currentTimeMillis(), p.name, p.source, m.added, m.removed, m.same, p.report.periodFrom ?: 0, p.report.periodTo ?: 0)) + log
            saveLocked()
            store.dirty = true
            publishState()
        }
    }

    /** Barcha import qilingan yozuvlarni o'chirish (shu mijoz). */
    suspend fun clearLedger() = mutex.withLock {
        withContext(Dispatchers.IO) {
            loadLocked()
            entries = emptyList(); coverage = emptyList(); log = emptyList(); balance = null
            saveLocked()
            store.dirty = true
            publishState()
        }
    }

    // ---------------- yuborish ----------------

    private fun productIndex(): Map<String, Product> {
        val map = HashMap<String, Product>()
        for (p in catalog.state.value.products) map.putIfAbsent(LedgerMerge.nameKey(p.name), p)
        return map
    }

    /** 2-ilovaga boradigan ma'lumot. Kelgan narx QO'SHILMAYDI. */
    suspend fun buildSnapshot(): ClientSnapshot {
        mutex.withLock { withContext(Dispatchers.IO) { loadLocked() } }
        val c = catalog.state.value
        val byKey = productIndex()
        val groupName = c.groups.associate { it.id to it.name }
        val seen = HashSet<String>()
        val items = ArrayList<PriceItem>()
        for (e in entries) {
            if (e.product.isBlank() || (e.kind != EntryKind.SALE && e.kind != EntryKind.RETURN)) continue
            val k = LedgerMerge.nameKey(e.product)
            if (!seen.add(k)) continue
            val p = byKey[k]
            items += if (p == null) PriceItem("", e.product, "", 0.0, 0.0, 0.0, 0.0)
            else PriceItem(
                p.id, p.name, groupName[p.groupId].orEmpty(),
                Pricing.unitPrice(p, PriceType.CASH, Currency.SOM, c.rate), Pricing.unitPrice(p, PriceType.CASH, Currency.USD, c.rate),
                Pricing.unitPrice(p, PriceType.WHOLESALE, Currency.SOM, c.rate), Pricing.unitPrice(p, PriceType.WHOLESALE, Currency.USD, c.rate),
            )
        }
        val debt = runCatching { catalog.knownDebt(store.clientId) }.getOrNull()?.let { DebtInfo(it.som, it.usd, it.fetchedAt, "server") }
        return ClientSnapshot(
            System.currentTimeMillis(), store.clientId, store.clientName, c.rate, debt,
            items.sortedBy { it.name.lowercase() }, entries, coverage, c.lastSyncAt, reportBalance = balance,
        )
    }

    /** Firebase'ga yozadi (meta + blob bitta so'rovda). */
    suspend fun publish(): Boolean {
        if (!store.isConfigured) return false
        return busy {
            try {
                val snap = buildSnapshot()
                val blob = withContext(Dispatchers.Default) { SnapshotCodec.pack(snap) }
                withContext(Dispatchers.IO) {
                    // Avval ma'lumot, keyin sarlavha: 2-ilova yangi sarlavhani ko'rsa, ma'lumot ham yangi bo'ladi.
                    // (PATCH ishlatilmaydi — oddiy PUT eng ishonchli; "req" ga tegilmaydi.)
                    val db = cloud()
                    db.put("s/${store.readKey}/blob", blob, timeoutMs = 120_000)
                    db.put(
                        "s/${store.readKey}/meta",
                        linkedMapOf(
                            "v" to ClientSnapshot.VERSION, "at" to CloudDb.SERVER_TIME, "created" to snap.createdAt,
                            "n" to snap.ledger.size, "p" to snap.products.size, "size" to blob.length, "name" to snap.clientName,
                        ),
                    )
                }
                store.lastPublishAt = System.currentTimeMillis()
                store.lastPublishError = ""
                store.dirty = false
                true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                store.lastPublishError = (e.message ?: e.javaClass.simpleName).take(200)
                false
            } finally {
                mutex.withLock { publishState(busy = false) }
            }
        }
    }

    fun markDirty() {
        if (!store.isConfigured) return
        store.dirty = true
        publishState()
    }

    /**
     * Fon ishchisi uchun: 2-ilova "Yangilash" so'rovi bo'lsa — narx va qarzni serverdan yangilab (imkon bo'lsa) yuboradi;
     * aks holda o'zgarish bo'lsa yuboradi. @return true — biror narsa yuborildi.
     */
    suspend fun tick(): Boolean {
        if (!store.isConfigured) return false
        mutex.withLock { withContext(Dispatchers.IO) { loadLocked() } }
        var serve = false
        var req = 0L
        if (store.answerRequests) {
            req = runCatching { withContext(Dispatchers.IO) { (cloud().get("s/${store.readKey}/req") as? Number)?.toLong() ?: 0L } }.getOrDefault(0L)
            serve = req > store.lastRequestServed
        }
        if (serve) {
            // Server ko'rinmasa (do'kondan tashqarida, VPN yo'q) — bor ma'lumot baribir yuboriladi.
            if (System.currentTimeMillis() - catalog.state.value.lastSyncAt > 10 * 60_000L) runCatching { catalog.sync() }
            runCatching { catalog.refreshDebt(store.clientId) }
        }
        val sent = if (serve || (store.autoPublish && store.dirty)) publish() else false
        if (serve && sent) store.lastRequestServed = req
        return sent
    }

    /** 2-filial telefoni (2-ilova) qoldirgan printer holati. */
    suspend fun printerStatus(): PrinterStatus? {
        if (!store.isConfigured || store.queueKey.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            val db = cloud()
            val st = db.get("p/${store.queueKey}/status") as? Map<*, *>
            val queue = runCatching { db.keys("q/${store.queueKey}").size }.getOrDefault(-1)
            if (st == null) PrinterStatus(0L, "", queue, false)
            else {
                @Suppress("UNCHECKED_CAST")
                val o = JObj(st as Map<String, Any?>)
                PrinterStatus(o.long("at"), o.str("text"), queue, o.bool("ready"))
            }
        }
    }
}
