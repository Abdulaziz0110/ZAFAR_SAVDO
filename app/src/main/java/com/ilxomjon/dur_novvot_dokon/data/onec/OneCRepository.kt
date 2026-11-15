package com.ilxomjon.dur_novvot_dokon.data.onec

import android.content.Context
import com.ilxomjon.dur_novvot_dokon.data.share.ShareStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uz.imsoft.shared.CloudDb
import uz.imsoft.shared.JObj
import uz.imsoft.shared.Json
import uz.imsoft.shared.onec.OneCCodec
import uz.imsoft.shared.onec.OneCSales
import uz.imsoft.shared.onec.Suppliers
import uz.imsoft.shared.onec.WriteCodec
import uz.imsoft.shared.onec.WriteKind
import uz.imsoft.shared.onec.WriteRequest
import uz.imsoft.shared.onec.WriteResult
import java.io.File

/** Admin'dagi 1C yozuvi: so'rov va (kelgan bo'lsa) kompyuter natijasi. "Kiritildi" faqat natija ok bo'lsa. */
data class WriteItem(
    val req: WriteRequest, val sentAt: Long, val result: WriteResult? = null, val sendError: String = "",
    /** v5.3: birinchi marta yuborilgan vaqt (qayta yuborilganda o'zgarmaydi). 0 — [sentAt] bilan bir xil. */
    val firstAt: Long = 0L,
    /** v5.3: necha marta yuborilgan (1 — bir marta). */
    val tries: Int = 1,
) {
    val isDone: Boolean get() = result?.ok == true && result.state != WriteResult.UNDONE
    val isFinal: Boolean get() = result != null
    val isError: Boolean get() = result?.ok == false || (result == null && sendError.isNotEmpty())
    val isWaiting: Boolean get() = result == null && sendError.isEmpty()
    val createdAt: Long get() = if (firstAt > 0) firstAt else sentAt

    /** Bir xil ish (turi va mazmuni) — qayta yuborilgan nusxalarni tanish uchun. */
    val sameKey: String get() = req.kind + "\u0001" + WriteCodec.payload(req)
}

data class OneCState(
    val sales: OneCSales? = null,
    val suppliers: Suppliers? = null,
    val writes: List<WriteItem> = emptyList(),
    val requestedAt: Long = 0L,
    val requestedKind: String = "",
    /** v4.1: 1C hisobotlari (oxirgi so'ralgan davr). */
    val reports: uz.imsoft.shared.onec.OneCReports? = null,
)

/**
 * v3.2 Admin: kompyuter yuborgan 1C savdolar ro'yxati va ta'minotchilar (faqat o'qish) hamda "Tasdiqlab kiritish"
 * navbati (w/{inbox}). Ma'lumot telefonda saqlanadi — internet bo'lmasa ham oxirgisi ko'rinadi.
 */
class OneCRepository(private val context: Context, private val store: ShareStore) {
    private val _state = MutableStateFlow(OneCState())
    val state: StateFlow<OneCState> = _state.asStateFlow()
    private val mutex = Mutex()
    @Volatile private var loaded = false
    /** v3.12: yuborilgandan keyin natijani tez kuzatish uchun (ServiceLocator.appScope). */
    var scope: kotlinx.coroutines.CoroutineScope? = null
    private var watchJob: kotlinx.coroutines.Job? = null

    private fun dir() = File(context.filesDir, "onec").apply { mkdirs() }
    private fun cloud() = CloudDb(store.dbUrl, store.secret)
    private val inbox: String get() = store.inboxKey

    val ready: Boolean get() = store.isCloudReady && inbox.isNotEmpty()

    @Volatile private var costsOf: Suppliers? = null
    @Volatile private var costs: Map<String, Pair<Double, Double>> = emptyMap()

    /**
     * v4.0: oxirgi kirim narxi (so'm, $) tovar kodi bo'yicha — ta'minotchilar kirim hujjatlaridan (eng yangisi).
     * Agentlarga katalog bilan boradi; "Kelgan narx" va foyda shu bilan hisoblanadi.
     */
    fun lastCosts(): Map<String, Pair<Double, Double>> {
        val s = _state.value.suppliers ?: return emptyMap()
        if (s === costsOf) return costs
        val out = HashMap<String, Pair<Double, Double>>()
        val stampOf = HashMap<String, Long>()
        for (sup in s.list) for (d in sup.docs) {
            if (d.kind != uz.imsoft.shared.onec.OneCDoc.RECEIPT) continue
            for (l in d.lines) {
                val key = l.code
                if (key.isEmpty()) continue
                if ((stampOf[key] ?: 0L) > d.stamp) continue
                if (l.priceSom <= 0 && l.priceUsd <= 0) continue
                stampOf[key] = d.stamp
                out[key] = l.priceSom to l.priceUsd
            }
        }
        costsOf = s
        costs = out
        return out
    }

    fun lastCost(productId: String): Pair<Double, Double>? = lastCosts()[productId]

    suspend fun load() = mutex.withLock {
        if (loaded) return@withLock
        withContext(Dispatchers.IO) {
            val sales = runCatching { File(dir(), "sales.json").takeIf { it.isFile }?.readBytes()?.let(OneCCodec::parseSales) }.getOrNull()
            val supp = runCatching { File(dir(), "supp.json").takeIf { it.isFile }?.readBytes()?.let(OneCCodec::parseSuppliers) }.getOrNull()
            val rep = runCatching { File(dir(), "rep.json").takeIf { it.isFile }?.readBytes()?.let(uz.imsoft.shared.onec.OneCReportsCodec::parse) }.getOrNull()
            _state.value = _state.value.copy(sales = sales, suppliers = supp, writes = readWrites(), reports = rep)
        }
        loaded = true
    }

    /** Kompyuterdan kelgan fayl (ShareRepository.processInbox chaqiradi). */
    suspend fun onData(kind: String, bytes: ByteArray): Boolean {
        load()
        return withContext(Dispatchers.IO) {
            when (kind) {
                "sales" -> {
                    // v3.7: har kelgan davr yig'iladi (oxirgi 1 yil) — Admin "1C tarixi" barcha savdolarni ko'rsatadi
                    val merged = uz.imsoft.shared.onec.OneCSalesArchive.merge(_state.value.sales, OneCCodec.parseSales(bytes), yearAgoStamp())
                    val f = File(dir(), "sales.json")
                    val tmp = File(dir(), "sales.json.tmp")
                    tmp.writeText(OneCCodec.salesJson(merged))
                    if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
                    _state.value = _state.value.copy(sales = merged)
                    true
                }
                "rep" -> {
                    val r = uz.imsoft.shared.onec.OneCReportsCodec.parse(bytes)
                    File(dir(), "rep.json").writeBytes(bytes)
                    _state.value = _state.value.copy(reports = r)
                    true
                }
                "supp" -> {
                    val s = OneCCodec.parseSuppliers(bytes)
                    File(dir(), "supp.json").writeBytes(bytes)
                    _state.value = _state.value.copy(suppliers = s)
                    true
                }
                else -> false
            }
        }
    }

    private fun yearAgoStamp(): Long {
        val c = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_MONTH, -364) }
        return uz.imsoft.shared.report.Stamp.of(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
    }

    private val bf by lazy { context.getSharedPreferences("onec_backfill", Context.MODE_PRIVATE) }

    /**
     * v3.7 (Admin fon ishchisi): kompyuterdan 1 yillik savdolarni 30 kunlik bo'laklar bilan orqaga so'raydi — bir vaqtda
     * bitta so'rov; javob kelmasa 10 daqiqadan keyin qayta. Hali hech narsa kelmagan bo'lsa — oxirgi 30 kun.
     */
    suspend fun backfillStep() {
        if (!ready) return
        load()
        val now = System.currentTimeMillis()
        val s = _state.value.sales
        val yearAgo = yearAgoStamp()
        val reqFrom = bf.getLong("from", 0L)
        val reqAt = bf.getLong("at", 0L)
        val answered = s != null && reqFrom > 0 && s.from <= reqFrom
        if (!answered && now - reqAt < 10 * 60_000L) return
        val fmt = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
        fun ms(st: Long): Long = java.util.Calendar.getInstance().apply {
            clear(); set(uz.imsoft.shared.report.Stamp.year(st), uz.imsoft.shared.report.Stamp.month(st) - 1, uz.imsoft.shared.report.Stamp.dayOfMonth(st))
        }.timeInMillis
        val day = 86_400_000L
        val (from, to) = if (s == null || s.from <= 0) {
            (now - 29 * day) to now
        } else {
            if (s.from <= yearAgo) return
            val toMs = ms(s.from) - day
            maxOf(ms(yearAgo), toMs - 29 * day) to toMs
        }
        withContext(Dispatchers.IO) {
            cloud().put("p/$inbox/dreq", linkedMapOf("k" to "sales", "f" to fmt.format(java.util.Date(from)), "t" to fmt.format(java.util.Date(to)), "at" to CloudDb.SERVER_TIME))
        }
        val c = java.util.Calendar.getInstance().apply { timeInMillis = from }
        bf.edit().putLong("from", uz.imsoft.shared.report.Stamp.of(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH)))
            .putLong("at", now).apply()
    }

    /** "Shu davrni yuboring" (kompyuter keyingi aylanishda — 30 soniyagacha — bajaradi). [from]/[to] — yyyyMMdd. */
    suspend fun requestData(kind: String, from: String, to: String) {
        check(ready) { "cloud" }
        withContext(Dispatchers.IO) {
            cloud().put("p/$inbox/dreq", linkedMapOf("k" to kind, "f" to from, "t" to to, "at" to CloudDb.SERVER_TIME))
        }
        _state.value = _state.value.copy(requestedAt = System.currentTimeMillis(), requestedKind = kind)
    }

    // ---------------- v3.10: tarozi ----------------

    /** Tarozi natijasi (kompyuter yozadi). [req] — qaysi so'rovga javob (so'rov vaqti). */
    data class ScaleResult(val at: Long, val req: Long, val ok: Boolean, val text: String, val n: Int)

    /**
     * Taroziga narx va PLU: kompyuter 1C'dagi "Taroziga yuklash" bilan aynan bir xil ishni qiladi (RONGTA dasturi).
     * [clear] — tarozini tozalab hammasini yuborish; aks holda faqat qoldig'i borlar. @return so'rov vaqti (server).
     */
    suspend fun requestScale(clear: Boolean): Long {
        check(ready) { "cloud" }
        return withContext(Dispatchers.IO) {
            val db = cloud()
            db.put("p/$inbox/scale", linkedMapOf("m" to if (clear) "clear" else "stock", "at" to CloudDb.SERVER_TIME))
            (db.get("p/$inbox/scale/at") as? Number)?.toLong() ?: System.currentTimeMillis()
        }
    }

    suspend fun scaleResult(): ScaleResult? {
        if (!ready) return null
        @Suppress("UNCHECKED_CAST")
        val m = withContext(Dispatchers.IO) { runCatching { cloud().get("p/$inbox/scale_res") as? Map<String, Any?> }.getOrNull() } ?: return null
        val o = JObj(m)
        return ScaleResult(o.long("at"), o.long("req"), o.bool("ok"), o.str("text"), o.long("n").toInt())
    }

    // ---------------- 1C'ga yozish ----------------

    /** Egasi tasdiqlagan so'rovni navbatga qo'yadi. Internet bo'lmasa — xato (so'rov yo'qolmaydi, "Qayta yuborish" mumkin). */
    suspend fun submit(req: WriteRequest, by: String): WriteItem {
        load()
        require(req.problem() == null) { req.problem().orEmpty() }
        val item = WriteItem(req, System.currentTimeMillis())
        update { it.filter { w -> w.req.id != req.id } + item }
        return push(item)
    }

    private suspend fun push(item: WriteItem): WriteItem {
        val err = runCatching {
            check(ready) { "cloud" }
            withContext(Dispatchers.IO) { cloud().put("w/$inbox/${item.req.id}", WriteCodec.toCloud(item.req, "Admin")) }
        }.exceptionOrNull()
        val sent = item.copy(sendError = err?.message.orEmpty().take(200).ifEmpty { if (err != null) err.javaClass.simpleName else "" })
        update { l -> l.map { if (it.req.id == item.req.id) sent else it } }
        if (err != null && err is kotlinx.coroutines.CancellationException) throw err
        if (err == null) watch()
        return sent
    }

    /**
     * v3.12: kompyuter so'rovni darhol oladi (Firebase oqimi) — natija ham tez ko'rinsin: 3 daqiqagacha har 2.5 soniyada
     * tekshiriladi (javob kelmaganlar qolguncha). Ilova yopilsa — odatdagi 1 daqiqalik tekshiruv qoladi.
     */
    fun watch() {
        val sc = scope ?: return
        if (watchJob?.isActive == true) return
        watchJob = sc.launch(Dispatchers.IO) {
            val until = System.currentTimeMillis() + 180_000L
            while (System.currentTimeMillis() < until) {
                kotlinx.coroutines.delay(2_500L)
                runCatching { refreshResults() }
                if (_state.value.writes.none { it.result == null && it.sendError.isEmpty() }) break
            }
        }
    }

    /** v3.12: bir nechta so'rov birdaniga (shtrix kodlar ro'yxati). @return yuborilmay qolganlar soni. */
    suspend fun submitAll(reqs: List<WriteRequest>, by: String): Int {
        load()
        var failed = 0
        for (r in reqs) {
            if (r.problem() != null) { failed++; continue }
            if (submit(r, by).sendError.isNotEmpty()) failed++
        }
        return failed
    }

    /** Yuborilmay qolganlarni qayta yuboradi. */
    suspend fun retryUnsent() {
        load()
        for (w in _state.value.writes.filter { it.sendError.isNotEmpty() && it.result == null }) push(w)
    }

    /**
     * Xato bilan qaytgan yozuvni qayta yuboradi. v5.3: ro'yxatda YANGI qator paydo bo'lmaydi — o'sha qatorning o'zi
     * yangilanadi (kompyuter uchun id yangi, shuning uchun eski natija emas, yangisi kutiladi). Muvaffaqiyatli bo'lsa
     * qator yashilga aylanadi; "qayta yuborilgan" vaqti va urinishlar soni saqlanadi.
     */
    suspend fun resend(id: String): WriteItem? {
        load()
        val w = _state.value.writes.firstOrNull { it.req.id == id } ?: return null
        val req = w.req.copy(id = WriteCodec.newId(), at = System.currentTimeMillis())
        if (req.problem() != null) return null
        val item = WriteItem(req, System.currentTimeMillis(), firstAt = w.createdAt, tries = w.tries + 1)
        update { l -> l.filter { it.req.id != id && it.req.id != req.id } + item }
        return push(item)
    }

    /** Hamma xato qaytganlarni birdan qayta yuborish. @return nechta yuborildi. */
    suspend fun resendAllFailed(): Int {
        load()
        var n = 0
        for (w in _state.value.writes.filter { it.result?.ok == false }) if (resend(w.req.id) != null) n++
        return n
    }

    /** Ro'yxatni tozalash: [errors] — xatolar, [done] — kiritilganlar. Kutilayotganlar hech qachon o'chirilmaydi. */
    suspend fun clear(errors: Boolean, done: Boolean): Int {
        load()
        var n = 0
        update { l ->
            l.filter { w ->
                val drop = (errors && w.result?.ok == false) || (done && w.result?.ok == true)
                if (drop) n++
                !drop
            }
        }
        return n
    }

    /** Kiritilgan hujjatni bekor qilish (1C'da o'chirish belgisi). */
    suspend fun undo(id: String): WriteItem =
        submit(WriteRequest(WriteCodec.newId(), WriteKind.UNDO, System.currentTimeMillis(), target = id), "Admin")

    /** Kompyuter natijalarini o'qiydi (navbatdagi va oxirgi 2 kunda kiritilganlar — bekor qilinganini ham ko'rish uchun). */
    suspend fun refreshResults(): Int {
        load()
        if (!ready) return 0
        retryUnsent()
        val now = System.currentTimeMillis()
        val want = _state.value.writes.filter { it.sendError.isEmpty() && (it.result == null || (it.isDone && now - it.sentAt < 2 * 86_400_000L)) }
        if (want.isEmpty()) return 0
        val db = cloud()
        var changed = 0
        val results = HashMap<String, WriteResult>()
        withContext(Dispatchers.IO) {
            // v3.12: bitta so'rov bilan (kalit bo'yicha oraliq) — 50 ta shtrix kod bo'lsa ham 50 marta emas
            val ids = want.map { it.req.id }.toSet()
            val range = if (want.size > 2) runCatching {
                @Suppress("UNCHECKED_CAST")
                db.get("wr/$inbox", mapOf("orderBy" to "\"\$key\"", "startAt" to "\"" + ids.min() + "\""), timeoutMs = 30_000) as? Map<String, Any?>
            }.getOrNull() else null
            if (range != null) {
                for ((k, v) in range) if (k in ids) WriteCodec.resultFromCloud(k, v)?.let { results[k] = it }
            } else {
                for (w in want) {
                    val v = runCatching { db.get("wr/$inbox/${w.req.id}") }.getOrNull() ?: continue
                    WriteCodec.resultFromCloud(w.req.id, v)?.let { results[w.req.id] = it }
                }
            }
        }
        update { l ->
            l.map { w ->
                val r = results[w.req.id]
                if (r != null && r != w.result) { changed++; w.copy(result = r) } else w
            }
        }
        return changed
    }

    fun pendingCount(): Int = _state.value.writes.count { it.result == null }

    private suspend fun update(f: (List<WriteItem>) -> List<WriteItem>) = mutex.withLock {
        val l = compact(f(_state.value.writes)).sortedByDescending { it.sentAt }.take(500)
        _state.value = _state.value.copy(writes = l)
        withContext(Dispatchers.IO) { saveWrites(l) }
    }

    private fun writesFile() = File(dir(), "writes.json")

    private fun saveWrites(l: List<WriteItem>) {
        val arr = l.map { w ->
            linkedMapOf<String, Any?>(
                "id" to w.req.id, "k" to w.req.kind, "c" to w.req.at, "p" to WriteCodec.payload(w.req), "s" to w.sentAt, "se" to w.sendError,
                "f" to w.firstAt, "n" to w.tries,
                "r" to w.result?.let { r -> linkedMapOf("at" to r.at, "ok" to r.ok, "st" to r.state, "no" to r.number, "ref" to r.ref, "e" to r.error) },
            )
        }
        val tmp = File(dir(), "writes.json.tmp")
        tmp.writeText(Json.write(arr))
        tmp.renameTo(writesFile())
    }

    @Suppress("UNCHECKED_CAST")
    private fun readWrites(): List<WriteItem> = runCatching {
        val f = writesFile()
        if (!f.isFile) return emptyList()
        (Json.parse(f.readText()) as? List<Any?>).orEmpty().mapNotNull { x ->
            val o = JObj(x as? Map<String, Any?> ?: return@mapNotNull null)
            val req = WriteCodec.fromCloud(o.str("id"), mapOf("k" to o.str("k"), "p" to o.str("p"), "c" to o.long("c"))) ?: return@mapNotNull null
            val r = o.obj("r")?.let { WriteCodec.resultFromCloud(req.id, it.map) }
            WriteItem(req, o.long("s"), r, o.str("se"), o.long("f"), o.long("n").toInt().coerceAtLeast(1))
        }
    }.getOrDefault(emptyList()).let(::compact)
}

/**
 * v5.3: ro'yxatni ixchamlash. Avval har "Qayta yuborish" yangi qator qo'shar edi — bitta xato 5-6 marta takrorlanib
 * ro'yxat to'lib ketardi. Qoida: XATO (yoki yuborilmagan) qatordan KEYIN aynan shu ish yana yuborilgan bo'lsa —
 * eski xato qator olib tashlanadi, yangisi uning birinchi vaqtini va urinishlar sonini oladi.
 * Muvaffaqiyatli kiritilgan qatorlar hech qachon birlashtirilmaydi (bir xil to'lov ikki marta bo'lishi mumkin).
 */
fun compact(items: List<WriteItem>): List<WriteItem> {
    if (items.size < 2) return items
    val out = ArrayList<WriteItem>(items.size)
    val newest = HashMap<String, Int>()      // sameKey -> out ichidagi o'rni (eng yangi qator)
    for (w in items.sortedByDescending { it.sentAt }) {
        val key = w.sameKey
        val at = newest[key]
        if (at != null && w.isError) {
            val keep = out[at]
            out[at] = keep.copy(firstAt = minOf(keep.createdAt, w.createdAt), tries = keep.tries + w.tries)
            continue
        }
        if (at == null) newest[key] = out.size
        out += w
    }
    return out
}
