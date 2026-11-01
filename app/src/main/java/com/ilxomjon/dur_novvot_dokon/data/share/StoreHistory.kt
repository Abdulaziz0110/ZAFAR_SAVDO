package com.ilxomjon.dur_novvot_dokon.data.share

import android.content.Context
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.AppRole
import com.ilxomjon.dur_novvot_dokon.data.remote.Backend
import com.ilxomjon.dur_novvot_dokon.data.remote.Protocol
import com.ilxomjon.dur_novvot_dokon.data.repo.CatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import uz.imsoft.shared.JObj
import uz.imsoft.shared.Json
import uz.imsoft.shared.report.CardParser
import uz.imsoft.shared.report.CardReport
import uz.imsoft.shared.report.Coverage
import uz.imsoft.shared.report.HtmlTable
import uz.imsoft.shared.report.LedgerEntry
import uz.imsoft.shared.report.LedgerMerge
import uz.imsoft.shared.report.LedgerOwner
import uz.imsoft.shared.report.SnapshotCodec
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * v3.6 Admin: BARCHA mijozlarning 1C kartochkalari (savdo, vozvrat, to'lov) — serverdan, oxirgi 1 yil.
 *
 * Qanday yig'iladi (server yuklanib qolmasin — bir vaqtda bitta so'rov, kamida [GAP_MS] oraliq bilan):
 *  1) savdo/to'lov qilingan mijoz — darhol (2.5 va 6 daqiqada qayta, 1C qabul qilgach);
 *  2) hali yuklanmagan mijozlar — oxirgi 31 kun (2-ilovadagi mijozlar birinchi, keyin qarzi kattalar);
 *  3) 1 yilgacha orqaga — 92 kunlik bo'laklar bilan;
 *  4) yangilash: faol mijozlar har soatda, qolganlari kuniga bir (oxirgi 31 kun).
 * Kompyuterdan kelgan "Mijoz kartochkasi" fayllari ham shu yerga qo'shiladi ([commit]).
 * Har yangi hisobot [onReport] orqali 2-ilova mijozining kitobiga ham tushadi (Mijoz ilovasi shu bilan to'ladi).
 */
class StoreHistory(
    private val context: Context,
    private val catalog: CatalogRepository,
    private val api: Backend,
) {
    private class Meta(
        var name: String = "",
        /** Bugundan orqaga necha kun yuklangan (0 — hali hech narsa). */
        var back: Int = 0,
        var ver: Int = 0,
        var okAt: Long = 0L,
        var tryAt: Long = 0L,
        var err: String = "",
        /** Shu vaqtdan keyin darhol yangilansin (savdodan keyin). 0 — yo'q. */
        var due: Long = 0L,
        var lastEntry: Long = 0L,
    )

    private class Book(var entries: List<LedgerEntry>, var coverage: List<Coverage>)

    data class State(
        val entries: List<LedgerEntry> = emptyList(),
        val version: Long = 0L,
        val loaded: Boolean = false,
        val status: String = "",
        /** Do'kon id -> server kartochkasi qamragan davrlar (kompyuter hujjatlari bilan takrorlanmaslik uchun). */
        val cov: Map<String, List<Coverage>> = emptyMap(),
    )

    private val mutex = Mutex()
    private val net = Mutex()
    private val metas = HashMap<String, Meta>()
    private val books = HashMap<String, Book>()
    private var loaded = false
    private var version = 0L
    private var lastReqAt = 0L
    private var pauseUntil = 0L
    private var running = ""
    private var lastErr = ""
    private var lastErrAt = 0L
    private var parseFails = 0
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Har yangi server/kompyuter hisoboti — 2-ilova mijozlari kitobiga ham (ShareRepository). */
    var onReport: (suspend (storeId: String, name: String, report: CardReport, source: String) -> Unit)? = null

    /** 2-ilovadagi mijozlarning do'kon id lari — ular birinchi navbatda yuklanadi. */
    var priorityStores: () -> Set<String> = { emptySet() }

    private fun dir() = File(context.filesDir, "hist").apply { mkdirs() }
    private fun safe(id: String) = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { "x" }
    private fun bookFile(id: String) = File(dir(), "s_" + safe(id) + ".json")
    private fun indexFile() = File(dir(), "index.json")

    /** Server javobi jadval sifatida o'qilmasa — tashxis uchun shu yerga saqlanadi (ulashish mumkin). */
    fun failFile(): File = File(dir(), "server_xato.html")

    /** v3.8: muvaffaqiyatli o'qilgan, yozuvlari eng ko'p javob (tekshirish uchun ulashish mumkin). */
    fun sampleFile(): File = File(dir(), "server_namuna.html")
    @Volatile private var sampleSize = 1

    // ---------------- saqlash ----------------

    suspend fun load() = mutex.withLock {
        if (loaded) return@withLock
        withContext(Dispatchers.IO) {
            runCatching {
                val o = JObj.parse(indexFile().readText())
                for ((id, v) in o.map) {
                    @Suppress("UNCHECKED_CAST")
                    val m = JObj((v as? Map<String, Any?>) ?: continue)
                    metas[id] = Meta(m.str("n"), m.long("b").toInt(), m.long("v").toInt(), m.long("ok"), m.long("try"), m.str("e"), m.long("due"), m.long("le"))
                }
            }
            for (id in metas.keys) {
                val f = bookFile(id)
                if (!f.isFile) continue
                runCatching {
                    val o = JObj.parse(f.readText())
                    @Suppress("UNCHECKED_CAST")
                    val l = o.arr("l").mapNotNull { (it as? List<Any?>)?.let { a -> runCatching { SnapshotCodec.entryFromList(a) }.getOrNull() } }
                    books[id] = Book(l, o.objs("cov").map { Coverage(it.long("f"), it.long("t"), it.long("at"), it.str("src")) })
                }
            }
            loaded = true
            publish()
        }
    }

    private fun saveIndex() {
        val map = LinkedHashMap<String, Any?>()
        for ((id, m) in metas) {
            map[id] = linkedMapOf("n" to m.name, "b" to m.back, "v" to m.ver, "ok" to m.okAt, "try" to m.tryAt, "e" to m.err, "due" to m.due, "le" to m.lastEntry)
        }
        write(indexFile(), Json.write(map))
    }

    private fun saveBook(id: String, b: Book) {
        write(
            bookFile(id),
            Json.write(
                linkedMapOf(
                    "v" to 1,
                    "l" to b.entries.map { SnapshotCodec.entryToList(it) },
                    "cov" to b.coverage.map { linkedMapOf("f" to it.from, "t" to it.to, "at" to it.importedAt, "src" to it.source) },
                ),
            ),
        )
    }

    private fun write(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    // ---------------- UI holati ----------------

    private fun publish() {
        val all = ArrayList<LedgerEntry>()
        for ((id, b) in books) {
            val name = metas[id]?.name?.ifBlank { null } ?: catalog.storeById(id)?.name ?: id
            for (e in b.entries) all += e.copy(kindText = LedgerOwner.tag(e.kindText, id, name))
        }
        all.sortBy { it.stamp }
        version++
        _state.value = State(all, version, loaded, statusText(), books.mapValues { it.value.coverage })
    }

    private fun refreshStatus() { _state.value = _state.value.copy(status = statusText()) }

    private fun hm(ms: Long) = SimpleDateFormat("dd.MM HH:mm", Locale.US).format(Date(ms))

    private fun statusText(): String {
        val total = catalog.state.value.stores.size
        val ready = metas.values.count { it.okAt > 0 }
        val year = metas.values.count { it.back >= DAYS && it.ver == VER }
        val okAt = metas.values.maxOfOrNull { it.okAt } ?: 0L
        val parts = ArrayList<String>()
        parts += if (okAt > 0) context.getString(R.string.sh_status, hm(okAt), ready, total, year) else context.getString(R.string.sh_status_none, total)
        if (running.isNotEmpty()) parts += context.getString(R.string.sh_status_running, running)
        if (lastErr.isNotEmpty() && lastErrAt > okAt) parts += context.getString(R.string.sh_status_err, hm(lastErrAt), lastErr)
        return parts.joinToString("\n")
    }

    /** Batafsil (dialog uchun). */
    fun details(): String {
        val sb = StringBuilder(context.getString(R.string.sh_details_how))
        if (parseFails > 0) sb.append("\n\n").append(context.getString(R.string.sh_details_parse, parseFails))
        val withErr = metas.entries.filter { it.value.err.isNotEmpty() }.sortedByDescending { it.value.tryAt }.take(5)
        if (withErr.isNotEmpty()) {
            sb.append("\n\n").append(context.getString(R.string.sh_details_errors))
            for ((id, m) in withErr) sb.append("\n• ").append(m.name.ifBlank { id }).append(": ").append(m.err)
        }
        return sb.toString()
    }

    // ---------------- yozish ----------------

    /**
     * Hisobotni do'kon tarixiga qo'shadi (server yoki kompyuter). [back] — server so'rovi necha kun orqaga qamradi
     * (0 — o'zgarmaydi).
     */
    suspend fun commit(storeId: String, name: String, report: CardReport, source: String, back: Int = 0, notify: Boolean = true) {
        if (storeId.isEmpty()) return
        load()
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val b = books.getOrPut(storeId) { Book(emptyList(), emptyList()) }
                val m = LedgerMerge.merge(b.entries, b.coverage, report, System.currentTimeMillis(), source)
                b.entries = m.entries
                b.coverage = m.coverage
                val meta = metas.getOrPut(storeId) { Meta() }
                if (name.isNotBlank()) meta.name = name
                if (back > 0) {
                    if (meta.ver != VER) { meta.ver = VER; meta.back = back } else meta.back = maxOf(meta.back, back)
                    meta.okAt = System.currentTimeMillis()
                    meta.err = ""
                }
                meta.lastEntry = b.entries.maxOfOrNull { it.stamp } ?: 0L
                saveBook(storeId, b)
                saveIndex()
                publish()
            }
        }
        if (notify) runCatching { onReport?.invoke(storeId, name, report, source) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
    }

    /** Savdo/to'lovdan keyin: shu mijoz navbatdan tashqari yangilansin. */
    suspend fun bump(storeId: String, atMs: Long = System.currentTimeMillis()) {
        if (storeId.isEmpty()) return
        load()
        mutex.withLock { val m = metas.getOrPut(storeId) { Meta() }; if (m.due == 0L || atMs < m.due) m.due = atMs; withContext(Dispatchers.IO) { saveIndex() } }
    }

    /** Hammasini oxirgi 31 kun bo'yicha tez yangilash ("Hozir yangilash"). */
    suspend fun bumpAll() {
        load()
        mutex.withLock {
            val now = System.currentTimeMillis()
            for (s in catalog.state.value.stores) metas.getOrPut(s.id) { Meta(s.name) }.let { if (it.due == 0L) it.due = now }
            withContext(Dispatchers.IO) { saveIndex() }
        }
        lastReqAt = 0L
        pauseUntil = 0L
    }

    // ---------------- serverdan ----------------

    /**
     * Bitta do'kon kartochkasi: bugundan [toDaysAgo] kun oldingacha, [fromDaysAgo] kun oldindan boshlab.
     * Javob jadval bo'lmasa — HTML [failFile] ga saqlanadi va xato qaytadi.
     */
    private suspend fun fetch(storeId: String, fromDaysAgo: Int, toDaysAgo: Int): CardReport = net.withLock {
        val agentId = catalog.state.value.agent?.id ?: throw IllegalStateException(context.getString(R.string.sh_err_agent))
        val to = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -toDaysAgo) }.time
        val from = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, -fromDaysAgo)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.time
        lastReqAt = System.currentTimeMillis()
        val html = withTimeout(120_000L) { api.fetchReport(agentId, Protocol.report(from, to, storeId)) }
        withContext(Dispatchers.Default) {
            try {
                // v3.8: server "solishtirma dalolatnoma" (akt sverki) beradi; Excel kartochka ham o'qiladi
                uz.imsoft.shared.report.ServerReport.parse(HtmlTable.read(html)).also { r ->
                    // Tashxis uchun namuna: yozuvlari eng ko'p bo'lgan oxirgi javob
                    if (r.entries.size >= sampleSize) withContext(Dispatchers.IO) {
                        runCatching { sampleFile().writeText(html); sampleSize = r.entries.size }
                    }
                }
            } catch (e: CardParser.NotACard) {
                parseFails++
                withContext(Dispatchers.IO) { runCatching { failFile().writeText(html) } }
                throw ParseFail(context.getString(R.string.sh_err_parse, e.message.orEmpty()))
            }
        }
    }

    /** Darhol: oxirgi [days] kun (2-ilova so'raganda, savdodan keyin). @return hisobot (xato bo'lsa — istisno). */
    suspend fun refreshNow(storeId: String, days: Int = 31): CardReport {
        load()
        val name = catalog.storeById(storeId)?.name ?: metas[storeId]?.name.orEmpty()
        running = name
        refreshStatus()
        try {
            val r = fetch(storeId, days - 1, 0)
            commit(storeId, name, r, "server", back = days)
            mutex.withLock { metas[storeId]?.let { it.due = 0L; it.tryAt = System.currentTimeMillis() } }
            return r
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(storeId, name, e)
            throw e
        } finally {
            running = ""
            refreshStatus()
        }
    }

    private suspend fun fail(storeId: String, name: String, e: Exception) {
        val msg = (e.message ?: e.javaClass.simpleName).take(160)
        lastErr = msg
        lastErrAt = System.currentTimeMillis()
        mutex.withLock {
            val m = metas.getOrPut(storeId) { Meta(name) }
            m.err = msg
            m.tryAt = System.currentTimeMillis()
            withContext(Dispatchers.IO) { saveIndex() }
        }
    }

    /**
     * Fon qadami (Admin, har ~25 s). Shoshilinchlari (savdodan keyin, "Hozir yangilash", hali yuklanmaganlar) ketma-ket
     * ~20 soniya davomida; qolgani (1 yilga orqaga, eskirganini yangilash) — bitta so'rov. @return so'rov qilindimi.
     */
    suspend fun step(): Boolean {
        if (!AppRole.isAdmin) return false
        load()
        val t0 = System.currentTimeMillis()
        if (t0 - lastReqAt < GAP_MS || t0 < pauseUntil) return false
        if (catalog.state.value.agent == null || catalog.state.value.stores.isEmpty()) return false
        var any = false
        while (true) {
            val r = stepOnce()
            if (r == 0) break
            any = true
            if (r == 2 || System.currentTimeMillis() - t0 > 20_000L) break
            kotlinx.coroutines.delay(1_500L)
        }
        return any
    }

    private class Job(val id: String, val fromAgo: Int, val toAgo: Int, val urgent: Boolean)

    /** @return 0 — ish yo'q, 1 — shoshilinch so'rov bajarildi, 2 — oddiy so'rov bajarildi. */
    private suspend fun stepOnce(): Int {
        val now = System.currentTimeMillis()
        val stores = catalog.state.value.stores
        val prio = priorityStores()
        val rate = catalog.state.value.rate.takeIf { it > 0 } ?: 12000.0
        val ordered = stores.sortedWith(
            compareByDescending<com.ilxomjon.dur_novvot_dokon.domain.Store> { it.id in prio }
                .thenByDescending { Math.abs(it.debtSom) + Math.abs(it.debtUsd) * rate }
                .thenBy { it.name.lowercase() },
        )
        val job: Job? = mutex.withLock {
            fun meta(id: String, name: String) = metas.getOrPut(id) { Meta(name) }
            fun cooled(m: Meta) = now - m.tryAt >= RETRY_MS
            // 1) savdodan keyin / "Hozir yangilash"
            ordered.firstOrNull { s -> metas[s.id]?.let { it.due in 1..now && (it.tryAt < it.due || cooled(it)) } == true }
                ?.let { return@withLock Job(it.id, 30, 0, true) }
            // 2) hali yuklanmagan (yoki yangi versiya) — oxirgi 31 kun
            ordered.firstOrNull { s -> meta(s.id, s.name).let { (it.okAt == 0L || it.ver != VER) && cooled(it) } }
                ?.let { return@withLock Job(it.id, 30, 0, true) }
            // 3) 1 yilgacha orqaga, 92 kunlik bo'laklar
            ordered.firstOrNull { s -> meta(s.id, s.name).let { it.back in 1 until DAYS && cooled(it) } }?.let { s ->
                val m = meta(s.id, s.name)
                return@withLock Job(s.id, minOf(m.back + CHUNK, DAYS) - 1, m.back, false)
            }
            // 4) eskirganlarini yangilash: faollari (qarzi bor, 60 kun ichida harakat, 2-ilovada) har soatda, qolgani kuniga bir
            val recent = daysAgoStamp(60)
            ordered.filter { s ->
                val m = meta(s.id, s.name)
                val active = s.debtSom != 0.0 || s.debtUsd != 0.0 || m.lastEntry >= recent || s.id in prio
                cooled(m) && now - m.okAt >= (if (active) ACTIVE_MS else IDLE_MS)
            }.minByOrNull { metas[it.id]?.okAt ?: 0L }?.let { return@withLock Job(it.id, 30, 0, false) }
            null
        }
        if (job == null) return 0
        val name = catalog.storeById(job.id)?.name.orEmpty()
        running = name
        refreshStatus()
        try {
            val r = fetch(job.id, job.fromAgo, job.toAgo)
            commit(job.id, name, r, "server", back = job.fromAgo + 1)
            mutex.withLock {
                metas[job.id]?.let { it.tryAt = System.currentTimeMillis(); if (it.due in 1..now) it.due = 0L }
                withContext(Dispatchers.IO) { saveIndex() }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(job.id, name, e)
            // Jadval o'qilmagan bo'lak qayta-qayta so'ralmasin: o'tkazib yuboriladi (tashxis fayli saqlangan)
            if (e is ParseFail) mutex.withLock {
                metas[job.id]?.let { m ->
                    if (m.ver != VER) { m.ver = VER; m.back = job.fromAgo + 1 } else m.back = maxOf(m.back, job.fromAgo + 1)
                    if (m.okAt == 0L) m.okAt = System.currentTimeMillis()
                    if (m.due in 1..now) m.due = 0L
                }
                withContext(Dispatchers.IO) { saveIndex() }
            } else {
                // Server ko'rinmayapti (Tailscale o'chiq, internet yo'q) — har mijozni birma-bir urinib o'tirmaslik uchun tanaffus
                pauseUntil = System.currentTimeMillis() + PAUSE_MS
                return 0
            }
        } finally {
            running = ""
            refreshStatus()
        }
        return if (job.urgent) 1 else 2
    }

    private fun daysAgoStamp(days: Int): Long {
        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -days) }
        return uz.imsoft.shared.report.Stamp.of(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** Server javobi "Mijoz kartochkasi" jadvali sifatida o'qilmadi. */
    class ParseFail(message: String) : IllegalStateException(message)

    companion object {
        /** Yuklash versiyasi: o'qish qoidasi o'zgarsa oshiriladi — hammasi qayta yuklanadi. */
        /** v3.8: 2 — server javobi (akt sverki) endi o'qiladi, avval "o'qilmadi" deb o'tkazilganlar qayta yuklanadi. */
        const val VER = 2
        const val DAYS = 365
        const val CHUNK = 92
        const val GAP_MS = 20_000L
        const val RETRY_MS = 10 * 60_000L
        const val ACTIVE_MS = 60 * 60_000L
        const val IDLE_MS = 24 * 60 * 60_000L
        const val PAUSE_MS = 3 * 60_000L
    }
}
