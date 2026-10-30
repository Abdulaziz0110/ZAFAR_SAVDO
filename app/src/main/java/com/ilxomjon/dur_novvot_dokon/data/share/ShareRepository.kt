package com.ilxomjon.dur_novvot_dokon.data.share

import android.content.Context
import com.ilxomjon.dur_novvot_dokon.R
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
import uz.imsoft.shared.B64
import uz.imsoft.shared.CloudDb
import uz.imsoft.shared.JObj
import uz.imsoft.shared.Json
import uz.imsoft.shared.LinkCode
import uz.imsoft.shared.LoginRecord
import uz.imsoft.shared.OrderCodec
import uz.imsoft.shared.OrderRequest
import uz.imsoft.shared.Phone
import uz.imsoft.shared.ShopCode
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
import java.security.MessageDigest
import java.util.Date

/** Bitta import yozuvi (jurnal). */
data class ImportLog(val at: Long, val name: String, val source: String, val added: Int, val removed: Int, val same: Int, val from: Long, val to: Long)

/** Ro'yxat uchun mijoz qisqacha. */
data class ClientSummary(
    val id: String,
    val name: String,
    val kind: String,
    val entries: Int,
    val members: Int,
    val coveredTo: Long,
    val lastPublishAt: Long,
    val dirty: Boolean,
    val newOrders: Int,
    val hasStore: Boolean,
    val hasPrinter: Boolean,
)

data class ShareState(
    val configured: Boolean = false,
    val cloudReady: Boolean = false,
    val dbUrl: String = "",
    val shopCode: String = "",
    val hasSecret: Boolean = false,
    val clients: List<ClientSummary> = emptyList(),
    // Tanlangan (joriy) mijoz
    val clientId: String = "",
    val clientName: String = "",
    val entries: Int = 0,
    val coverage: List<Coverage> = emptyList(),
    val log: List<ImportLog> = emptyList(),
    val hasQueue: Boolean = false,
    val members: List<ShareMember> = emptyList(),
    // Umumiy
    val lastPublishAt: Long = 0L,
    val lastPublishError: String = "",
    val dirty: Boolean = false,
    val busy: Boolean = false,
    val rulesOk: Boolean = false,
    val autoPublish: Boolean = true,
    val answerRequests: Boolean = true,
    val acceptOrders: Boolean = true,
    val newOrders: Int = 0,
    val receiptHeader: String = "",
    val syncMinutes: Int = 60,
    val pcHours: Int = 2,
    val pcDays: Int = 7,
    val pcStatus: String = "",
    val pcAt: Long = 0L,
    val pcOk: Boolean = false,
)

/** 1C tarixi (UI uchun, tanlangan mijoz). [version] import/tozalash/mijoz almashganda o'zgaradi. */
data class ClientLedger(val entries: List<LedgerEntry>, val clientName: String, val version: Long, val loaded: Boolean, val clientId: String = "")

/** Qaysi mijozga saqlanadi: mavjud ([existingId]) yoki yangi ([newName]). */
class ImportTarget(val existingId: String?, val newName: String, val newStoreId: String)

/** Import oldidan ko'rsatiladigan natija. Saqlash [ShareRepository.commit] bilan. */
class ImportPreview(
    val report: CardReport,
    val name: String,
    val source: String,
    val merge: LedgerMerge.Result,
    val target: ImportTarget,
    val targetName: String,
    val matchedProducts: Int,
    val unmatchedProducts: List<String>,
)

/** Fayl jadval sifatida o'qildi, lekin "Mijoz kartochkasi"ga o'xshamadi — tashxis uchun birinchi qatorlar. */
class NotACardException(message: String, val dump: String) : Exception(message)

class PrinterStatus(val at: Long, val text: String, val queue: Int, val printerReady: Boolean)

/** 2-ilovadan kelgan buyurtma va u qaysi mijozdan. */
data class ClientOrder(val clientId: String, val clientName: String, val order: OrderRequest)

/**
 * 1-ilova -> 2-ilova ulashish (v2.6 — ko'p mijozli): 1C hisobotlarini import qilish (qo'lda yoki kompyuterdan avtomatik),
 * narxlar va qarzni yig'ib har mijozning o'z joyiga (s/{kalit}) yuborish, telefon+parol bilan kirish yozuvlari,
 * buyurtmalar, chek printeri. Ma'lumot telefonda alohida fayllarda (filesDir/share/) — mavjud bazalarga tegmaydi.
 */
class ShareRepository(
    private val context: Context,
    private val store: ShareStore,
    private val catalog: CatalogRepository,
    private val api: ServerApi,
) {
    private val mutex = Mutex()
    /** v2.7: kompyuterdan kelgan tovar kodlari (PLU, shtrix) — narx yorliqlari bo'limi qabul qiladi. */
    var codesHook: (suspend (ByteArray) -> Unit)? = null
    /** v2.7: tovar id -> tarozi PLU (2-ilovada ko'rsatiladi). */
    var pluOf: (String) -> String? = { null }
    /** v2.6: 1-ilova savdolari -> 2-ilova (tarix va chek). */
    val mirror = SaleMirror(context, store)
    private val _state = MutableStateFlow(ShareState())
    val state: StateFlow<ShareState> = _state.asStateFlow()

    private class Book(var entries: List<LedgerEntry>, var coverage: List<Coverage>, var log: List<ImportLog>, var balance: Pair<Long, Money2>?)

    private val books = HashMap<String, Book>()
    private var ledgerVersion = 0L
    private val _ledger = MutableStateFlow(ClientLedger(emptyList(), "", 0L, false))
    val ledger: StateFlow<ClientLedger> = _ledger.asStateFlow()
    private val _orders = MutableStateFlow<List<ClientOrder>>(emptyList())
    /** 2-ilovalardan kelgan buyurtmalar (oxirgi tekshiruv), barcha mijozlar. */
    val orders: StateFlow<List<ClientOrder>> = _orders.asStateFlow()
    private var pcSt: Triple<Long, String, Boolean> = Triple(0L, "", false)

    private fun dir() = File(context.filesDir, "share").apply { mkdirs() }
    private fun file(clientId: String) = File(dir(), "ledger_" + clientId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { "x" } + ".json")

    // ---------------- lokal saqlash ----------------

    suspend fun load() = mutex.withLock { withContext(Dispatchers.IO) { for (c in store.clients()) book(c.id); publishState() } }

    private fun book(id: String): Book {
        books[id]?.let { return it }
        val b = Book(emptyList(), emptyList(), emptyList(), null)
        val f = file(id)
        if (f.exists()) runCatching {
            val o = JObj.parse(f.readText())
            @Suppress("UNCHECKED_CAST")
            b.entries = o.arr("l").mapNotNull { (it as? List<Any?>)?.let { a -> runCatching { SnapshotCodec.entryFromList(a) }.getOrNull() } }
            b.coverage = o.objs("cov").map { Coverage(it.long("f"), it.long("t"), it.long("at"), it.str("src")) }
            b.balance = (o.map["bal"] as? List<*>)?.let { l -> (l.getOrNull(0) as? Number)?.toLong()?.let { st -> st to Money2((l.getOrNull(1) as? Number)?.toDouble() ?: 0.0, (l.getOrNull(2) as? Number)?.toDouble() ?: 0.0) } }
            b.log = o.objs("log").map { ImportLog(it.long("at"), it.str("n"), it.str("src"), it.long("a").toInt(), it.long("r").toInt(), it.long("s").toInt(), it.long("f"), it.long("t")) }
        }
        books[id] = b
        return b
    }

    private fun saveBook(id: String, b: Book) {
        val json = Json.write(
            linkedMapOf(
                "v" to 1,
                "l" to b.entries.map { SnapshotCodec.entryToList(it) },
                "cov" to b.coverage.map { linkedMapOf("f" to it.from, "t" to it.to, "at" to it.importedAt, "src" to it.source) },
                "bal" to b.balance?.let { listOf(it.first, it.second.som, it.second.usd) },
                "log" to b.log.take(30).map { linkedMapOf("at" to it.at, "n" to it.name, "src" to it.source, "a" to it.added, "r" to it.removed, "s" to it.same, "f" to it.from, "t" to it.to) },
            ),
        )
        val f = file(id)
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    private fun publishState(busy: Boolean = _state.value.busy) {
        val list = store.clients()
        val cur = store.current()
        val orders = _orders.value
        fun newFor(id: String) = orders.count { it.clientId == id && (it.order.status == OrderRequest.Status.SENT || it.order.status == OrderRequest.Status.SEEN) }
        val sums = list.map { c ->
            val b = books[c.id]
            ClientSummary(
                c.id, c.name, c.kind, b?.entries?.size ?: 0, c.members.size, b?.coverage?.maxOfOrNull { it.to } ?: 0L,
                c.lastPublishAt, c.dirty, newFor(c.id), c.storeId.isNotEmpty(), c.queueKey.isNotEmpty(),
            )
        }.sortedWith(compareByDescending<ClientSummary> { it.newOrders }.thenBy { it.name.lowercase() })
        val cb = cur?.let { books[it.id] }
        _state.value = ShareState(
            configured = store.isConfigured, cloudReady = store.isCloudReady, dbUrl = store.dbUrl,
            shopCode = if (store.dbUrl.isNotEmpty()) ShopCode.fromUrl(store.dbUrl) else "", hasSecret = store.secret.isNotEmpty(),
            clients = sums, clientId = cur?.id.orEmpty(), clientName = cur?.name.orEmpty(), entries = cb?.entries?.size ?: 0,
            coverage = cb?.coverage.orEmpty(), log = cb?.log.orEmpty(), hasQueue = cur?.queueKey?.isNotEmpty() == true,
            members = cur?.members.orEmpty(),
            lastPublishAt = store.lastPublishAt, lastPublishError = store.lastPublishError, dirty = list.any { it.dirty }, busy = busy,
            rulesOk = store.rulesOk, autoPublish = store.autoPublish, answerRequests = store.answerRequests,
            acceptOrders = store.acceptOrders, newOrders = orders.count { it.order.status == OrderRequest.Status.SENT || it.order.status == OrderRequest.Status.SEEN },
            receiptHeader = store.receiptHeader, syncMinutes = store.syncMinutes, pcHours = store.pcHours, pcDays = store.pcDays,
            pcAt = pcSt.first, pcStatus = pcSt.second, pcOk = pcSt.third,
        )
        _ledger.value = ClientLedger(cb?.entries.orEmpty(), cur?.name.orEmpty(), ledgerVersion, true, cur?.id.orEmpty())
    }

    private inline fun <T> busy(block: () -> T): T {
        _state.update { it.copy(busy = true) }
        try { return block() } finally { _state.update { it.copy(busy = false) } }
    }

    private fun cloud() = CloudDb(store.dbUrl, store.secret)

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
            val changed = store.dbUrl.isNotEmpty() && CloudDb.normalizeUrl(store.dbUrl) != u
            store.dbUrl = u
            store.secret = secret.trim()
            store.rulesOk = true
            store.rulesVersion = CloudDb.RULES_VERSION
            if (store.inboxKey.isEmpty()) store.inboxKey = CloudDb.newKey()
            store.markAllDirty()
            // Yangi bazada kirish yozuvlari ham qaytadan yoziladi
            if (changed) for (c in store.clients()) runCatching { writeMembers(c) }
        }
        mutex.withLock { publishState() }
    }

    fun setAuto(v: Boolean) { store.autoPublish = v; publishState() }
    fun setAnswer(v: Boolean) { store.answerRequests = v; publishState() }
    fun setAcceptOrders(v: Boolean) { store.acceptOrders = v; store.markAllDirty(); publishState() }
    fun setReceiptHeader(v: String) { store.receiptHeader = v.trim().take(60); store.markAllDirty(); publishState() }
    fun setReceiptFooter(v: String) {
        val t = v.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.take(6).joinToString("\n").take(300)
        if (t != store.receiptFooter) { store.receiptFooter = t; store.markAllDirty(); publishState() }
    }
    fun setReceiptStaff(v: String) { store.receiptStaff = v.trim().take(40) }
    fun setSyncMinutes(v: Int) { store.syncMinutes = v; publishState() }

    // ---------------- mijozlar ----------------

    private fun idFor(name: String, storeId: String): String {
        if (storeId.isNotEmpty()) return storeId
        val h = MessageDigest.getInstance("SHA-1").digest(LedgerMerge.nameKey(name).toByteArray(Charsets.UTF_8))
        return "n" + h.joinToString("") { "%02x".format(it) }.take(12)
    }

    private fun storeFor(name: String): Store? {
        val k = LedgerMerge.nameKey(name)
        return catalog.state.value.stores.firstOrNull { LedgerMerge.nameKey(it.name) == k }
    }

    /** Nom bo'yicha mavjud mijozni topadi. */
    fun findClientByName(name: String): ShareClient? {
        val k = LedgerMerge.nameKey(name)
        return store.clients().firstOrNull { LedgerMerge.nameKey(it.name) == k }
    }

    private fun createClient(name: String, storeId: String, kind: String = "c"): ShareClient {
        val id = idFor(name, storeId)
        store.client(id)?.let { return it }
        val c = ShareClient(id, name, storeId, CloudDb.newKey(), kind = kind)
        store.upsert(c)
        if (store.currentId.isEmpty()) store.currentId = id
        return c
    }

    /** Katalogdagi do'kondan mijoz qo'shish (bor bo'lsa — tanlanadi). */
    suspend fun addClient(s: Store, kind: String = "c") = mutex.withLock {
        withContext(Dispatchers.IO) {
            val c = createClient(s.name, s.id, kind)
            store.currentId = c.id
            book(c.id)
            ledgerVersion++
            publishState()
        }
    }

    /** Nom bilan (katalogda bo'lmasa ham, masalan yuk beruvchi). */
    suspend fun addClientByName(name: String, kind: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val st = storeFor(name)
            val c = createClient(name.trim(), st?.id.orEmpty(), kind)
            store.currentId = c.id
            book(c.id)
            ledgerVersion++
            publishState()
        }
    }

    /** v2.5 bilan moslik: bitta mijozni tanlash. */
    suspend fun setClient(s: Store) = addClient(s)

    suspend fun select(id: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (store.client(id) == null) return@withContext
            store.currentId = id
            book(id)
            ledgerVersion++
            publishState()
        }
    }

    /** Mijozni butunlay o'chirish: bulutdagi joyi, kirish yozuvlari va telefondagi tarixi. */
    suspend fun removeClient(id: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val c = store.client(id) ?: return@withContext
            if (store.isCloudReady) {
                val db = cloud()
                runCatching { db.delete("s/${c.readKey}") }
                for (m in c.members) runCatching { db.delete("a/" + LoginRecord.key(store.dbUrl, m.phone)) }
            }
            store.remove(id)
            books.remove(id)
            file(id).delete()
            if (store.currentId == id) store.currentId = store.clients().firstOrNull()?.id.orEmpty()
            ledgerVersion++
            publishState()
        }
    }

    suspend fun setKind(id: String, kind: String) = mutex.withLock { store.update(id) { it.copy(kind = kind, dirty = true) }; publishState() }

    suspend fun linkStore(id: String, s: Store) = mutex.withLock { store.update(id) { it.copy(storeId = s.id, dirty = true) }; publishState() }

    /** Chek printeri (2-filial) navbatini yoqish — kalit yaratiladi. */
    suspend fun enablePrinter(id: String) = mutex.withLock {
        store.update(id) { if (it.queueKey.isEmpty()) it.copy(queueKey = CloudDb.newKey()) else it }
        publishState()
    }

    // ---------------- telefonlar (2-ilovaga kirish) ----------------

    private suspend fun ensureRules() {
        if (store.rulesVersion < CloudDb.RULES_VERSION && store.isCloudReady) {
            withContext(Dispatchers.IO) { cloud().setRules(CloudDb.RULES) }
            store.rulesVersion = CloudDb.RULES_VERSION
        }
    }

    private fun writeMember(c: ShareClient, m: ShareMember) {
        val rec = LoginRecord.encrypt(LoginRecord.Grant(c.readKey, c.queueKey, c.name, m.name, c.id), m.password)
        cloud().put("a/" + LoginRecord.key(store.dbUrl, m.phone), rec)
    }

    private fun writeMembers(c: ShareClient) { for (m in c.members) writeMember(c, m) }

    /** Telefon qo'shish (yoki parolini almashtirish). Boshqa mijozda shu raqam bo'lsa — o'sha yerdan olinadi. */
    suspend fun addMember(clientId: String, phone: String, name: String, password: String) {
        require(Phone.isValid(phone)) { "bad_phone" }
        require(password.length >= LoginRecord.MIN_PASSWORD) { "short_password" }
        check(store.isCloudReady) { "not_configured" }
        val p = Phone.normalize(phone)
        ensureRules()
        mutex.withLock {
            withContext(Dispatchers.IO) {
                for (other in store.clients().filter { it.id != clientId && it.members.any { m -> m.phone == p } }) {
                    store.update(other.id) { it.copy(members = it.members.filter { m -> m.phone != p }) }
                }
                store.update(clientId) { c ->
                    c.copy(members = c.members.filter { it.phone != p } + ShareMember(p, name.trim(), password, System.currentTimeMillis()), dirty = true)
                }
                val c = store.client(clientId) ?: return@withContext
                writeMember(c, c.members.first { it.phone == p })
                publishState()
            }
        }
    }

    /**
     * Telefonni o'chirish: kirish yozuvi o'chiriladi va mijoz kaliti ALMASHTIRILADI (o'chirilgan telefon eski kalitni
     * eslab qolgan bo'lsa ham o'qiy olmasin). Qolgan telefonlar keyingi ochilishda o'zi qayta kiradi.
     */
    suspend fun removeMember(clientId: String, phone: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val p = Phone.normalize(phone)
                store.update(clientId) { it.copy(members = it.members.filter { m -> m.phone != p }) }
                if (store.isCloudReady) runCatching { cloud().delete("a/" + LoginRecord.key(store.dbUrl, p)) }
            }
        }
        rotateKeys(clientId)
    }

    /** Mijoz kalitini almashtirish (eski ulanish kodi va eski kalitni bilgan telefonlar ishlamay qoladi). */
    suspend fun rotateKeys(clientId: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val old = store.client(clientId) ?: return@withContext
                store.update(clientId) { it.copy(readKey = CloudDb.newKey(), dirty = true, lastRequestServed = 0L) }
                val c = store.client(clientId)!!
                if (store.isCloudReady) {
                    runCatching { writeMembers(c) }
                    // Hali ko'rilmagan buyurtmalar yo'qolmasin — yangi kalit ostiga ko'chiriladi.
                    runCatching { cloud().get("s/${old.readKey}/ord")?.let { cloud().put("s/${c.readKey}/ord", it) } }
                    runCatching { cloud().delete("s/${old.readKey}") }
                }
                publishState()
            }
        }
        publishOne(clientId)
    }

    /** Ishchiga yuboriladigan matn: do'kon kodi, telefon, parol. */
    fun loginText(clientId: String, phone: String): String {
        val c = store.client(clientId) ?: return ""
        val m = c.members.firstOrNull { it.phone == Phone.normalize(phone) } ?: return ""
        return context.getString(R.string.mb_share_text, c.name, ShopCode.fromUrl(store.dbUrl), Phone.pretty(m.phone), m.password)
    }

    fun linkCode(clientId: String = store.current()?.id.orEmpty()): String {
        val c = store.client(clientId) ?: return ""
        return LinkCode(store.dbUrl, c.readKey, c.queueKey, c.name).encode()
    }

    /** Kompyuterdagi forwarder.py va onec_export.py uchun sozlama (secret ichida — faqat egasining kompyuteriga!). */
    fun forwarderIni(): String {
        if (store.inboxKey.isEmpty()) store.inboxKey = CloudDb.newKey()
        val printer = store.current()?.queueKey?.ifEmpty { null } ?: store.clients().firstOrNull { it.queueKey.isNotEmpty() }?.queueKey.orEmpty()
        return "[firebase]\nurl = ${store.dbUrl}\nsecret = ${store.secret}\nqueue = $printer\ninbox = ${store.inboxKey}\n\n" +
            "[labels]\n; Narx yorlig'i printeri (label_printer.py). printer — Windows'dagi nomi (bo'sh: Xprinter o'zi qidiriladi)\n" +
            "key = ${store.labelKey}\nprinter = \nmode = gdi\nport = 9110\n\n" +
            "[onec]\n; 1C 8.3 ga ulanish (COM, oddiy foydalanuvchi). Masalan: File=\"D:\\1C\\Baza\";  yoki  Srvr=\"server\";Ref=\"baza\";\n" +
            "connection = \nuser = \npassword = \n" +
            "; Quyidagilar: python onec_export.py --discover natijasiga qarab to'ldiriladi\n" +
            "report = \nbuild = skd\nperiod_from = НачалоПериода\nperiod_to = КонецПериода\nclient_field = Контрагент\n" +
            "catalog = Контрагенты\nsupplier_flag = \nactive_register = \n; skip = Nom1; Nom2\n"
    }

    // ---------------- import ----------------

    suspend fun previewFile(bytes: ByteArray, name: String, targetId: String? = null): ImportPreview = withContext(Dispatchers.Default) {
        val grid = TableReader.read(bytes, name)
        val report = try { CardParser.parse(grid) } catch (e: CardParser.NotACard) { throw NotACardException(e.message.orEmpty(), grid.dump(25)) }
        buildPreview(report, name, "file", targetId)
    }

    /** Server hisoboti (tur 5) — 1-ilovaning odatdagi "Hisobot ▸ Serverdan" so'rovi bilan bir xil (joriy mijoz). */
    suspend fun previewServer(from: Date, to: Date): ImportPreview {
        val agentId = catalog.state.value.agent?.id ?: throw IllegalStateException("Agent topilmadi")
        val cur = store.current() ?: throw IllegalStateException("Mijoz tanlanmagan")
        require(cur.storeId.isNotEmpty()) { "no_store" }
        val html = api.fetchReport(agentId, Protocol.report(from, to, cur.storeId))
        return withContext(Dispatchers.Default) {
            val grid = HtmlTable.read(html)
            val report = try { CardParser.parse(grid) } catch (e: CardParser.NotACard) { throw NotACardException(e.message.orEmpty(), grid.dump(25)) }
            buildPreview(report, "server", "server", cur.id)
        }
    }

    /** Fayl qaysi mijozniki: aniq tanlangan, fayldagi nom bo'yicha mavjud, yoki yangi. */
    private fun resolveTarget(report: CardReport, targetId: String?): ImportTarget {
        if (targetId != null && store.client(targetId) != null) return ImportTarget(targetId, "", "")
        val fileName = report.clientName?.trim().orEmpty()
        if (fileName.isNotEmpty()) {
            findClientByName(fileName)?.let { return ImportTarget(it.id, "", "") }
            val st = storeFor(fileName)
            if (st != null) store.client(st.id)?.let { return ImportTarget(it.id, "", "") }
            return ImportTarget(null, fileName, st?.id.orEmpty())
        }
        val cur = store.current()
        return if (cur != null) ImportTarget(cur.id, "", "") else ImportTarget(null, "?", "")
    }

    private suspend fun buildPreview(report: CardReport, name: String, source: String, targetId: String?): ImportPreview {
        val target = resolveTarget(report, targetId)
        val b = mutex.withLock { withContext(Dispatchers.IO) { target.existingId?.let { book(it) } } }
        val merge = LedgerMerge.merge(b?.entries.orEmpty(), b?.coverage.orEmpty(), report, System.currentTimeMillis(), source)
        val byKey = productIndex()
        val names = report.entries.filter { it.product.isNotBlank() && (it.kind == EntryKind.SALE || it.kind == EntryKind.RETURN) }
            .map { it.product }.distinct()
        val unmatched = names.filter { byKey[LedgerMerge.nameKey(it)] == null }
        val tName = target.existingId?.let { store.client(it)?.name } ?: target.newName
        return ImportPreview(report, name, source, merge, target, tName, names.size - unmatched.size, unmatched)
    }

    /** Saqlash. [toCurrent] — fayldagi nomdan qat'i nazar joriy mijozga. @return mijoz id si. */
    suspend fun commit(p: ImportPreview, toCurrent: Boolean = false): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            val id = when {
                toCurrent && store.current() != null -> store.current()!!.id
                p.target.existingId != null -> p.target.existingId
                else -> createClient(p.target.newName, p.target.newStoreId).id
            }
            commitLocked(id, p.report, p.name, p.source)
            if (store.currentId.isEmpty() || p.target.existingId == null) store.currentId = id
            publishState()
            id
        }
    }

    private fun commitLocked(id: String, report: CardReport, name: String, source: String) {
        val b = book(id)
        // Ko'rib chiqish paytidan beri boshqa import bo'lgan bo'lsa ham to'g'ri bo'lishi uchun qaytadan birlashtiriladi.
        val m = LedgerMerge.merge(b.entries, b.coverage, report, System.currentTimeMillis(), source)
        b.entries = m.entries
        b.coverage = m.coverage
        report.closing?.let { c -> if (b.balance == null || c.first >= b.balance!!.first) b.balance = c }
        b.log = listOf(ImportLog(System.currentTimeMillis(), name, source, m.added, m.removed, m.same, report.periodFrom ?: 0, report.periodTo ?: 0)) + b.log
        saveBook(id, b)
        store.update(id) { it.copy(dirty = true) }
        ledgerVersion++
    }

    /** Barcha import qilingan yozuvlarni o'chirish (joriy mijoz). */
    suspend fun clearLedger(id: String = store.current()?.id.orEmpty()) = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (id.isEmpty()) return@withContext
            val b = book(id)
            b.entries = emptyList(); b.coverage = emptyList(); b.log = emptyList(); b.balance = null
            saveBook(id, b)
            store.update(id) { it.copy(dirty = true) }
            ledgerVersion++
            publishState()
        }
    }

    // ---------------- kompyuterdan avtomatik (1C COM -> in/{inbox}) ----------------

    /** Kompyuter qancha vaqtda eksport qilsin — bulutga yoziladi, kompyuter har aylanishda o'qiydi. */
    suspend fun setPcSchedule(hours: Int, days: Int) {
        store.pcHours = hours
        store.pcDays = days
        publishState()
        if (!store.isCloudReady) return
        if (store.inboxKey.isEmpty()) store.inboxKey = CloudDb.newKey()
        withContext(Dispatchers.IO) { cloud().put("p/${store.inboxKey}/exp", linkedMapOf("h" to hours, "d" to days, "at" to CloudDb.SERVER_TIME)) }
    }

    /** Kompyuter qoldirgan holat (oxirgi eksport vaqti, xato). */
    suspend fun refreshPcStatus() {
        if (!store.isCloudReady || store.inboxKey.isEmpty()) return
        @Suppress("UNCHECKED_CAST")
        val st = withContext(Dispatchers.IO) { runCatching { cloud().get("p/${store.inboxKey}/st") as? Map<String, Any?> }.getOrNull() }
        if (st != null) {
            val o = JObj(st)
            pcSt = Triple(o.long("at"), o.str("text"), o.bool("ok"))
            mutex.withLock { publishState() }
        }
    }

    /** Kompyuter yuborgan fayllarni import qiladi (mijoz fayldagi nom bo'yicha; yangi bo'lsa qo'shiladi). @return import soni. */
    suspend fun processInbox(): Int {
        if (!store.isCloudReady || store.inboxKey.isEmpty()) return 0
        val db = cloud()
        val keys = withContext(Dispatchers.IO) { runCatching { db.keys("in/${store.inboxKey}") }.getOrDefault(emptyList()) }.sorted()
        var n = 0
        var codesChanged = false
        for (k in keys) {
            @Suppress("UNCHECKED_CAST")
            val item = withContext(Dispatchers.IO) { runCatching { db.get("in/${store.inboxKey}/$k", timeoutMs = 60_000) as? Map<String, Any?> }.getOrNull() } ?: continue
            val o = JObj(item)
            val ok = runCatching {
                val raw = B64.decode(o.str("b"))
                val bytes = if (o.bool("gz")) B64.gunzip(raw) else raw
                if (o.str("k") == "codes") {
                    // Tovar kodlari (1C: PLU va shtrix) — mijoz hisoboti emas
                    (codesHook ?: throw IllegalStateException("codes")).invoke(bytes)
                    codesChanged = true
                    return@runCatching
                }
                val name = o.str("n").ifEmpty { "pc.xlsx" }
                val report = withContext(Dispatchers.Default) { CardParser.parse(TableReader.read(bytes, name)) }
                val hint = o.str("c")
                mutex.withLock {
                    withContext(Dispatchers.IO) {
                        val fileName = report.clientName?.ifBlank { null } ?: hint
                        val c = findClientByName(fileName) ?: (if (hint.isNotEmpty()) findClientByName(hint) else null)
                            ?: storeFor(fileName)?.let { st -> store.client(st.id) }
                            ?: createClient(fileName, storeFor(fileName)?.id.orEmpty(), o.str("k").ifEmpty { "c" })
                        commitLocked(c.id, report, name, "pc")
                    }
                }
            }.isSuccess
            // Buzilgan fayl qayta-qayta urinilmasin: baribir o'chiriladi (kompyuter jurnalida qoladi).
            withContext(Dispatchers.IO) { runCatching { db.delete("in/${store.inboxKey}/$k") } }
            if (ok) n++
        }
        if (n > 0) mutex.withLock { publishState() }
        // PLU 2-ilovada ham ko'rinsin: mijozlar "yangilanishi kerak" deb belgilanadi (navbatdagi yuborishda ketadi)
        if (codesChanged) mutex.withLock { withContext(Dispatchers.IO) { for (c in store.clients()) store.update(c.id) { it.copy(dirty = true) } }; publishState() }
        return n
    }

    // ---------------- yuborish ----------------

    private fun productIndex(): Map<String, Product> {
        val map = HashMap<String, Product>()
        for (p in catalog.state.value.products) map.putIfAbsent(LedgerMerge.nameKey(p.name), p)
        return map
    }

    /** 2-ilovaga boradigan ma'lumot. Kelgan narx QO'SHILMAYDI. */
    private suspend fun buildSnapshot(c: ShareClient): ClientSnapshot {
        val b = mutex.withLock { withContext(Dispatchers.IO) { book(c.id) } }
        val cat = catalog.state.value
        val byKey = productIndex()
        val groupName = cat.groups.associate { it.id to it.name }
        val seen = HashSet<String>()
        val items = ArrayList<PriceItem>()
        for (e in b.entries) {
            if (e.product.isBlank() || (e.kind != EntryKind.SALE && e.kind != EntryKind.RETURN)) continue
            val k = LedgerMerge.nameKey(e.product)
            if (!seen.add(k)) continue
            val p = byKey[k]
            items += if (p == null) PriceItem("", e.product, "", 0.0, 0.0, 0.0, 0.0)
            else PriceItem(
                p.id, p.name, groupName[p.groupId].orEmpty(),
                Pricing.unitPrice(p, PriceType.CASH, Currency.SOM, cat.rate), Pricing.unitPrice(p, PriceType.CASH, Currency.USD, cat.rate),
                Pricing.unitPrice(p, PriceType.WHOLESALE, Currency.SOM, cat.rate), Pricing.unitPrice(p, PriceType.WHOLESALE, Currency.USD, cat.rate),
                plu = pluOf(p.id).orEmpty(),
            )
        }
        val debt = if (c.storeId.isEmpty()) null else runCatching { catalog.knownDebt(c.storeId) }.getOrNull()?.let { DebtInfo(it.som, it.usd, it.fetchedAt, "server") }
        val features = if (store.acceptOrders && store.rulesVersion >= 2 && c.kind == "c") setOf(ClientSnapshot.FEATURE_ORDERS) else emptySet()
        return ClientSnapshot(
            System.currentTimeMillis(), c.id, c.name, cat.rate, debt,
            items.sortedBy { it.name.lowercase() }, b.entries + mirror.entriesFor(c.id, b.entries, b.coverage), b.coverage, cat.lastSyncAt, reportBalance = b.balance,
            features = features, shopName = store.receiptHeader, shopFooter = store.receiptFooter,
        )
    }

    private suspend fun publishOne(id: String): Boolean {
        val c = store.client(id) ?: return false
        if (!store.isCloudReady) return false
        val snap = buildSnapshot(c)
        val blob = withContext(Dispatchers.Default) { SnapshotCodec.pack(snap) }
        withContext(Dispatchers.IO) {
            // Avval ma'lumot, keyin sarlavha: 2-ilova yangi sarlavhani ko'rsa, ma'lumot ham yangi bo'ladi.
            val db = cloud()
            db.put("s/${c.readKey}/blob", blob, timeoutMs = 120_000)
            db.put(
                "s/${c.readKey}/meta",
                linkedMapOf(
                    "v" to ClientSnapshot.VERSION, "at" to CloudDb.SERVER_TIME, "created" to snap.createdAt,
                    "n" to snap.ledger.size, "p" to snap.products.size, "size" to blob.length, "name" to snap.clientName,
                ),
            )
        }
        store.update(id) { it.copy(dirty = false, lastPublishAt = System.currentTimeMillis()) }
        return true
    }

    /** O'zgargan mijozlarni yuboradi ([all] — hammasini). @return true — xatosiz. */
    suspend fun publish(all: Boolean = false): Boolean {
        if (!store.isConfigured) return false
        return busy {
            var ok = true
            try {
                for (c in store.clients()) {
                    if (!all && !c.dirty) continue
                    try { publishOne(c.id) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                        ok = false
                        store.lastPublishError = (c.name + ": " + (e.message ?: e.javaClass.simpleName)).take(200)
                    }
                }
                if (ok) { store.lastPublishAt = System.currentTimeMillis(); store.lastPublishError = "" }
                ok
            } finally {
                mutex.withLock { publishState(busy = false) }
            }
        }
    }

    fun markDirty() {
        if (!store.isConfigured) return
        store.markAllDirty()
        publishState()
    }

    /**
     * Fon ishchisi uchun (va ilova ochilganda): qoidalar, kompyuterdan kelgan fayllar, 2-ilova "Yangilash" so'rovlari,
     * o'zgargan ma'lumotni yuborish va buyurtmalar. @return true — biror narsa yuborildi.
     */
    suspend fun tick(): Boolean {
        if (!store.isConfigured && !store.isCloudReady) return false
        load()
        runCatching { ensureRules() }
        val imported = runCatching { processInbox() }.getOrDefault(0)
        runCatching { refreshPcStatus() }
        if (!store.isConfigured) return false
        val db = cloud()
        val served = ArrayList<Pair<String, Long>>()
        if (store.answerRequests) {
            var synced = false
            for (c in store.clients()) {
                val req = runCatching { withContext(Dispatchers.IO) { (db.get("s/${c.readKey}/req") as? Number)?.toLong() ?: 0L } }.getOrDefault(0L)
                if (req <= c.lastRequestServed) continue
                // Narx/qarz faqat server ko'rinsa yangilanadi (do'kon tarmog'i yoki VPN). Ko'rinmasa — import qilingan oxirgi ma'lumot qoladi.
                if (!synced && System.currentTimeMillis() - catalog.state.value.lastSyncAt > 10 * 60_000L) { runCatching { catalog.sync() }; synced = true }
                if (c.storeId.isNotEmpty()) runCatching { catalog.refreshDebt(c.storeId) }
                store.update(c.id) { it.copy(dirty = true) }
                served += c.id to req
            }
        }
        val sent = if (served.isNotEmpty() || imported > 0 || (store.autoPublish && store.clients().any { it.dirty })) publish() else false
        if (sent) for ((id, req) in served) store.update(id) { it.copy(lastRequestServed = req) }
        if (store.acceptOrders && store.rulesVersion >= 2) runCatching { refreshOrders(notify = true) }
        runCatching { mirror.retryPending() }
        return sent
    }

    /**
     * 1-ilovada savdo navbatga qo'yilganda: mijoz ro'yxatda bo'lsa — uning 2-ilovasiga darhol yuboriladi (tarixda ko'rinadi)
     * va 2-ilovada yoqilgan bo'lsa chek printer navbatiga qo'yiladi. Internet bo'lmasa — keyin (fon ishchisi).
     */
    suspend fun onSale(sent: com.ilxomjon.dur_novvot_dokon.domain.SentOrder, outboxId: Long, code: String) {
        if (!store.isConfigured) return
        // Chek uchun "oldingi qarz" — serverdan oxirgi yuklangan qiymat (so'm savdosida)
        val debt = if (sent.currency == com.ilxomjon.dur_novvot_dokon.domain.Currency.SOM) catalog.storeById(sent.storeId)?.debtSom else null
        val (c, s) = mirror.record(sent, outboxId, code, debt) ?: return
        store.update(c.id) { it.copy(dirty = true) }
        mutex.withLock { publishState() }
        val fresh = store.client(c.id) ?: c
        runCatching { mirror.pushReceipt(fresh, s) }
        if (store.autoPublish) runCatching { publishOne(c.id) }.onFailure { ShareWorker.kick(context) }
        else ShareWorker.kick(context)
    }

    // ---------------- 2-ilovadan buyurtmalar ----------------

    /**
     * Buyurtmalarni o'qiydi (barcha mijozlar). Yangi (SENT) buyurtmaga "1-ilovaga yetib bordi" (SEEN) belgisi qo'yiladi —
     * bu odam ko'rdi degani EMAS. 30 kundan eskilari o'chiriladi.
     */
    suspend fun refreshOrders(notify: Boolean = false): List<ClientOrder> = withContext(Dispatchers.IO) {
        if (!store.isConfigured) return@withContext emptyList()
        val db = cloud()
        val now = System.currentTimeMillis()
        val list = ArrayList<ClientOrder>()
        for (c in store.clients()) {
            @Suppress("UNCHECKED_CAST")
            val raw = runCatching { db.get("s/${c.readKey}/ord") as? Map<String, Any?> }.getOrNull() ?: continue
            for ((id, v) in raw) {
                var o = OrderCodec.fromCloud(id, v) ?: continue
                if (o.at in 1 until now - 30L * 86_400_000L) { runCatching { db.delete("s/${c.readKey}/ord/$id") }; continue }
                if (o.lines.isEmpty()) continue
                if (o.status == OrderRequest.Status.SENT) {
                    runCatching { db.put("s/${c.readKey}/ord/$id/st", OrderCodec.statusValue(OrderRequest.Status.SEEN)) }
                        .onSuccess { o = o.copy(status = OrderRequest.Status.SEEN, statusAt = now) }
                }
                list += ClientOrder(c.id, c.name, o)
            }
        }
        list.sortByDescending { it.order.at }
        _orders.value = list
        if (notify) {
            val seen = store.seenOrders.split('|').filter { it.isNotEmpty() }.toMutableSet()
            val fresh = list.filter { it.order.id !in seen && (it.order.status == OrderRequest.Status.SEEN || it.order.status == OrderRequest.Status.SENT) }
            if (fresh.isNotEmpty()) {
                val f = fresh.first()
                com.ilxomjon.dur_novvot_dokon.data.extras.Extras.notify(
                    context, 4103,
                    context.getString(R.string.ord_notif_title, f.clientName),
                    context.getString(R.string.ord_notif_text, fresh.size, f.order.lines.size, f.order.note.ifBlank { f.order.by.ifBlank { "—" } }),
                )
                seen += fresh.map { it.order.id }
                store.seenOrders = seen.toList().takeLast(300).joinToString("|")
            }
        }
        mutex.withLock { publishState() }
        list
    }

    suspend fun setOrderStatus(clientId: String, id: String, status: OrderRequest.Status) = withContext(Dispatchers.IO) {
        val c = store.client(clientId) ?: return@withContext
        cloud().put("s/${c.readKey}/ord/$id/st", OrderCodec.statusValue(status))
        _orders.value = _orders.value.map { if (it.order.id == id) it.copy(order = it.order.copy(status = status, statusAt = System.currentTimeMillis())) else it }
        mutex.withLock { publishState() }
    }

    // ---------------- chek printeri ----------------

    /** Tayyor ESC/POS chekni joriy mijozning printer navbatiga qo'yadi (2-ilova chop etadi). */
    suspend fun sendToBranchPrinter(bytes: ByteArray, clientId: String = store.current()?.id.orEmpty()) = withContext(Dispatchers.IO) {
        val c = store.client(clientId)
        check(store.isCloudReady && c != null && c.queueKey.isNotEmpty()) { context.getString(R.string.cl_no_printer) }
        val db = cloud()
        val key = "c" + (System.currentTimeMillis() * 1_000_000L + (System.nanoTime() % 1_000_000L).let { if (it < 0) -it else it })
        db.put(
            "q/${c!!.queueKey}/$key",
            linkedMapOf("t" to CloudDb.SERVER_TIME, "b" to B64.encode(bytes), "n" to bytes.size, "src" to "1-ilova"),
            timeoutMs = 60_000,
        )
        runCatching { db.put("p/${c.queueKey}/sig", CloudDb.SERVER_TIME) }
    }

    /** Joriy mijozning chek printeri holati (2-ilova yozadi). */
    suspend fun printerStatus(): PrinterStatus? {
        val c = store.current() ?: return null
        if (!store.isCloudReady || c.queueKey.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            val db = cloud()
            val st = runCatching { db.get("p/${c.queueKey}/status") as? Map<*, *> }.getOrNull()
            val queue = runCatching { db.keys("q/${c.queueKey}").size }.getOrDefault(-1)
            if (st == null) PrinterStatus(0L, "", queue, false)
            else {
                @Suppress("UNCHECKED_CAST")
                val o = JObj(st as Map<String, Any?>)
                PrinterStatus(o.long("at"), o.str("text"), queue, o.bool("ready"))
            }
        }
    }

    fun currentClient(): ShareClient? = store.current()
}
