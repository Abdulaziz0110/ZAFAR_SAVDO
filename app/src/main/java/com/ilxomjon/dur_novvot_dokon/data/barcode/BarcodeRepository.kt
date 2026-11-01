package com.ilxomjon.dur_novvot_dokon.data.barcode

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.ilxomjon.dur_novvot_dokon.data.repo.CatalogRepository
import com.ilxomjon.dur_novvot_dokon.domain.Product
import uz.imsoft.shared.JObj
import uz.imsoft.shared.Json
import uz.imsoft.shared.barcode.BarcodeImport
import uz.imsoft.shared.barcode.BarcodeLink
import uz.imsoft.shared.barcode.BarcodeResolver
import uz.imsoft.shared.barcode.Ean
import uz.imsoft.shared.barcode.ScaleFormat
import uz.imsoft.shared.report.TableReader
import java.io.File

data class BarcodeState(
    val imported: Int = 0,
    val importedAt: Long = 0L,
    val fileName: String = "",
    val unmatched: Int = 0,
    val learned: Int = 0,
    val scaleLearned: Int = 0,
    val loaded: Boolean = false,
)

/** Import oldidan ko'rsatiladi; saqlash — [BarcodeRepository.commit]. */
class BarcodePreview(val fileName: String, val parsed: BarcodeImport.Result, val linked: BarcodeLink.Linked)

/** O'rganilgan bog'lanish (ro'yxat uchun). */
class LearnedItem(val key: String, val productId: String, val scale: Boolean)

/**
 * Shtrix kodlar (v2.6, lokal, additiv): 1C dan import qilingan ro'yxat + skanerlashda "o'rganilgan" bog'lanishlar.
 * Fayl: files/barcodes.json. Serverga hech narsa yozilmaydi; savdo odatdagi savatcha orqali rasmiylashtiriladi.
 */
class BarcodeRepository(private val context: Context, private val catalog: CatalogRepository) {
    private val mutex = Mutex()
    private val prefs = context.getSharedPreferences("scan_prefs", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(BarcodeState())
    val state: StateFlow<BarcodeState> = _state.asStateFlow()

    private var imported: Map<String, String> = emptyMap()
    private var codes: Map<String, String> = emptyMap()
    private var learned: MutableMap<String, String> = HashMap()
    private var scaleLearned: MutableMap<String, String> = HashMap()
    /** O'rganilgan kodning asl ko'rinishi (oldidagi nollar bilan) — eksport uchun. */
    private var learnedRaw: MutableMap<String, String> = HashMap()
    /** Import qilingan kodning asl ko'rinishi (oldidagi nollar bilan) — narx yorlig'ida chizish uchun. kanonik -> asl. */
    private var importedRaw: MutableMap<String, String> = HashMap()
    @Volatile private var byProduct: Map<String, List<String>>? = null
    private var meta = BarcodeState()
    private var loaded = false
    @Volatile private var resolver: BarcodeResolver? = null

    private fun file() = File(context.filesDir, "barcodes.json")

    // ---------------- sozlamalar ----------------
    var scale: ScaleFormat
        get() = ScaleFormat.decode(prefs.getString("scale", null))
        set(v) { prefs.edit().putString("scale", v.encode()).apply(); resolver = null }
    var beep: Boolean get() = prefs.getBoolean("beep", true); set(v) = prefs.edit().putBoolean("beep", v).apply()
    var vibrate: Boolean get() = prefs.getBoolean("vibrate", true); set(v) = prefs.edit().putBoolean("vibrate", v).apply()

    // ---------------- yuklash / saqlash ----------------

    suspend fun load() = mutex.withLock { withContext(Dispatchers.IO) { loadLocked() } }

    private fun loadLocked() {
        if (loaded) return
        val f = file()
        if (f.exists()) runCatching {
            val o = JObj.parse(f.readText())
            fun map(k: String): MutableMap<String, String> = (o.obj(k)?.map ?: emptyMap()).mapValues { it.value.toString() }.toMutableMap()
            imported = map("imp"); codes = map("codes"); learned = map("learned"); scaleLearned = map("scale"); learnedRaw = map("raw"); importedRaw = map("iraw")
            meta = BarcodeState(imported.size, o.long("at"), o.str("file"), o.long("unmatched").toInt())
        }
        loaded = true
        publish()
    }

    /** Zaxiradan tiklangandan keyin fayldan qayta o'qish. */
    suspend fun reloadFromDisk() = mutex.withLock { withContext(Dispatchers.IO) { loaded = false; resolver = null; loadLocked() } }

    private fun saveLocked() {
        val body = linkedMapOf<String, Any?>(
            "v" to 1, "at" to meta.importedAt, "file" to meta.fileName, "unmatched" to meta.unmatched,
            "imp" to imported, "codes" to codes, "learned" to learned, "scale" to scaleLearned, "raw" to learnedRaw, "iraw" to importedRaw,
        )
        val f = file()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(Json.write(body))
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        resolver = null
        byProduct = null
        publish()
    }

    private fun publish() {
        _state.value = meta.copy(imported = imported.size, learned = learned.size, scaleLearned = scaleLearned.size, loaded = true)
    }

    // ---------------- import ----------------

    suspend fun preview(bytes: ByteArray, name: String): BarcodePreview = withContext(Dispatchers.Default) {
        val grid = TableReader.read(bytes, name)
        val parsed = BarcodeImport.parse(grid)
        val products = catalog.state.value.products
        BarcodePreview(name, parsed, BarcodeLink.link(parsed.rows, products, { it.id }, { it.name }))
    }

    /** Import qilingan ro'yxatni ALMASHTIRADI (o'rganilganlar saqlanib qoladi). */
    suspend fun commit(p: BarcodePreview) = mutex.withLock {
        withContext(Dispatchers.IO) {
            loadLocked()
            imported = HashMap(p.linked.map)
            codes = HashMap(p.linked.codes)
            importedRaw = HashMap()
            for (r in p.parsed.rows) { val k = Ean.canonical(r.barcode); if (imported.containsKey(k)) importedRaw.putIfAbsent(k, r.barcode.trim()) }
            meta = BarcodeState(imported.size, System.currentTimeMillis(), p.fileName, p.linked.unmatched.size)
            // 1C'dagi vazn tovarlari prefiksi (masalan 28, 29) — tarozi yorlig'ini tanish uchun
            if (p.parsed.scalePrefixes.isNotEmpty()) scale = scale.copy(enabled = true, prefixes = p.parsed.scalePrefixes)
            saveLocked()
        }
    }

    /** Kompyuterdan (1C) kelgan shtrix kodlar — Excel importi kabi, ro'yxatni almashtiradi. */
    suspend fun commitRows(rows: List<BarcodeImport.Row>, fileName: String): BarcodePreview {
        val parsed = BarcodeImport.Result(rows, -1, -1, -1, 0)
        val p = withContext(Dispatchers.Default) { BarcodePreview(fileName, parsed, BarcodeLink.link(rows, catalog.state.value.products, { it.id }, { it.name })) }
        commit(p)
        return p
    }

    suspend fun clearImported() = mutex.withLock {
        withContext(Dispatchers.IO) { loadLocked(); imported = emptyMap(); codes = emptyMap(); importedRaw = HashMap(); meta = BarcodeState(); saveLocked() }
    }

    suspend fun clearLearned() = mutex.withLock {
        withContext(Dispatchers.IO) { loadLocked(); learned.clear(); scaleLearned.clear(); learnedRaw.clear(); saveLocked() }
    }

    /**
     * Tovarning shtrix kodlari (asl ko'rinishda): import + skanerda o'rganilgan. Narx yorlig'i uchun.
     * Avval chizsa bo'ladiganlari (EAN-13/8), tarozi kodlari (7 xona) oxirida.
     */
    suspend fun barcodesFor(productId: String): List<String> {
        if (!loaded) load()
        val m = byProduct ?: mutex.withLock {
            val acc = HashMap<String, MutableList<String>>()
            for ((k, pid) in imported) acc.getOrPut(pid) { ArrayList() } += importedRaw[k] ?: k
            for ((k, pid) in learned) acc.getOrPut(pid) { ArrayList() } += learnedRaw[k] ?: k
            acc.mapValues { (_, l) -> l.distinct().sortedBy { c -> if (uz.imsoft.shared.barcode.BarcodeEncode.forShopCode(c)?.kind == uz.imsoft.shared.barcode.BarcodeEncode.Kind.EAN13) 0 else if (uz.imsoft.shared.barcode.BarcodeEncode.forShopCode(c) != null) 1 else 2 } }
                .also { byProduct = it }
        }
        return m[productId].orEmpty()
    }

    fun learnedItems(): List<LearnedItem> =
        learned.map { LearnedItem(it.key, it.value, false) } + scaleLearned.map { LearnedItem(it.key, it.value, true) }

    suspend fun forget(item: LearnedItem) = mutex.withLock {
        withContext(Dispatchers.IO) { if (item.scale) scaleLearned.remove(item.key) else learned.remove(item.key); saveLocked() }
    }

    /** v3.2: yangi (tarozi emas) shtrix kod bog'langanda — 1C'ga yozish uchun (Admin: navbat, Agent: Admin orqali). */
    @Volatile var onLearned: (suspend (productId: String, barcode: String) -> Unit)? = null

    /** Skanerda tanlangan tovarni eslab qoladi: oddiy kod -> shtrix kod bo'yicha; tarozi -> yorliqdagi tovar kodi bo'yicha. */
    suspend fun learn(r: BarcodeResolver.Result.Unknown, productId: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                loadLocked()
                val sc = r.scaleCode
                if (sc != null) scaleLearned[sc] = productId
                else { val k = Ean.canonical(r.raw); learned[k] = productId; learnedRaw[k] = r.raw.trim() }
                saveLocked()
            }
        }
        if (r.scaleCode == null) onLearned?.let { f -> runCatching { f(productId, r.raw.trim()) } }
    }

    /**
     * Telefonda yozilgan (o'rganilgan) kodlar — CSV (Excel ochadi): tovar kodi, nomi, shtrix kod. 1C'ga qo'lda kiritish uchun.
     * Tarozi kodlari alohida belgi bilan ("tarozi: 00025").
     */
    suspend fun exportLearnedCsv(dir: File): File = mutex.withLock {
        withContext(Dispatchers.IO) {
            loadLocked()
            val byId = catalog.state.value.productsById
            fun q(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
            dir.mkdirs()
            val f = File(dir, "yangi_shtrix_kodlar.csv")
            f.bufferedWriter(Charsets.UTF_8).use { w ->
                w.write("\uFEFF")
                w.write("Kod;Tovar;Shtrix kod\n")
                for ((bc, pid) in learned.entries.sortedBy { byId[it.value]?.name ?: it.value }) {
                    // Excel uzun raqamni "1.2E+12" qilmasin — matn sifatida
                    w.write(listOf(q(pid), q(byId[pid]?.name ?: "?"), "=\"" + (learnedRaw[bc] ?: bc) + "\"").joinToString(";") + "\n")
                }
                for ((code, pid) in scaleLearned) w.write(listOf(q(pid), q(byId[pid]?.name ?: "?"), q("tarozi: $code")).joinToString(";") + "\n")
            }
            f
        }
    }

    // ---------------- qidirish ----------------

    private fun resolverNow(): BarcodeResolver {
        resolver?.let { return it }
        // Katalog id lari ham "kod" sifatida (tarozi yorlig'idagi kod 1C kodi bo'lsa — taklif qilinadi)
        val allCodes = HashMap<String, String>()
        for (p in catalog.state.value.products) allCodes.putIfAbsent(BarcodeLink.codeKey(p.id), p.id)
        allCodes.putAll(codes)
        val r = BarcodeResolver(imported + learned, scaleLearned, allCodes, scale)
        resolver = r
        return r
    }

    /** Katalog yangilanganda chaqiriladi (id lar o'zgargan bo'lishi mumkin). */
    fun invalidate() { resolver = null }

    sealed class Scan {
        class Found(val product: Product, val qty: Double?, val raw: String) : Scan()
        class Unknown(val result: BarcodeResolver.Result.Unknown, val suggestion: Product?) : Scan()
        /** Shtrix kod ma'lum, lekin tovar katalogda yo'q (katalogdan o'chirilgan). */
        class Missing(val raw: String, val productId: String) : Scan()
    }

    suspend fun resolve(raw: String): Scan {
        if (!loaded) load()
        val byId = catalog.state.value.productsById
        return when (val r = withContext(Dispatchers.Default) { resolverNow().resolve(raw) }) {
            is BarcodeResolver.Result.Found -> byId[r.productId]?.let { Scan.Found(it, r.qty, r.raw) } ?: Scan.Missing(r.raw, r.productId)
            is BarcodeResolver.Result.Unknown -> Scan.Unknown(r, r.suggestion?.let { byId[it] })
        }
    }
}
