package com.ilxomjon.dur_novvot_dokon.data.labels

import android.content.Context
import com.ilxomjon.dur_novvot_dokon.data.barcode.BarcodeRepository
import com.ilxomjon.dur_novvot_dokon.data.repo.CatalogRepository
import com.ilxomjon.dur_novvot_dokon.data.share.ShareStore
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.PriceType
import com.ilxomjon.dur_novvot_dokon.domain.Pricing
import com.ilxomjon.dur_novvot_dokon.domain.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import uz.imsoft.shared.CloudDb
import uz.imsoft.shared.JObj
import uz.imsoft.shared.Json
import uz.imsoft.shared.android.LabelBitmap
import uz.imsoft.shared.barcode.BarcodeEncode
import uz.imsoft.shared.label.CodesPayload
import uz.imsoft.shared.label.LabelData
import uz.imsoft.shared.label.LabelKind
import uz.imsoft.shared.label.LabelPrice
import uz.imsoft.shared.label.PluImport

/** Ro'yxatdagi tovar: nechta yorliq va (ixtiyoriy) qo'lda yozilgan narx. */
data class LabelItem(val productId: String, val copies: Int, val price: Double? = null)

data class LabelState(
    val items: List<LabelItem> = emptyList(),
    val priceType: PriceType = PriceType.CASH,
    val host: String = "",
    val port: Int = 9110,
    val cloud: Boolean = true,
    val wMm: Double = 58.0,
    val hMm: Double = 40.0,
    val dpi: Int = 203,
    val info: PrinterInfo? = null,
    val infoError: String = "",
    val pluCount: Int = 0,
    /** Kompyuterdan (1C) kelgan tovar kodlari: qachon, nechta PLU / shtrix kod bog'landi. */
    val codesAt: Long = 0L,
    val codesPlu: Int = 0,
    val codesBarcodes: Int = 0,
    val codesNote: String = "",
    /** "Kompyuterdan yangilash" so'ralgan vaqt (javob kutilmoqda). */
    val codesAskedAt: Long = 0L,
) {
    val total: Int get() = items.sumOf { it.copies }
}

/** Chop etishga tayyor ish: chiziladigan yorliqlar va tashlab ketiladiganlar (sababi bilan). */
/** [skipped]: tovar id -> sabab. */
class LabelBatch(val kind: LabelKind, val labels: List<LabelData>, val skipped: List<Pair<String, Skip>>, val wPx: Int, val hPx: Int) {
    enum class Skip { NO_PLU, NO_BARCODE, NO_PRICE, NOT_IN_CATALOG }
    val total: Int get() = labels.sumOf { it.copies }
}

class PluPreview(val rows: List<PluImport.Row>, val map: Map<String, String>, val missing: List<PluImport.Row>)

/**
 * Narx yorliqlari (v2.6): tovarlar ro'yxati, tarozi PLU raqamlari (1C'dan import yoki qo'lda), printer sozlamasi.
 * Yorliq telefonda rasm qilib chiziladi (1C shakli bo'yicha) va kompyuterdagi printerga yuboriladi.
 * Hammasi lokal; serverga (1C/SQL) hech narsa yozilmaydi.
 */
class LabelRepository(
    private val context: Context,
    private val catalog: CatalogRepository,
    private val barcodes: BarcodeRepository,
    private val share: ShareStore,
) {
    private val p = context.getSharedPreferences("labels", Context.MODE_PRIVATE)
    private val client = LabelPrinterClient()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<LabelState> = _state.asStateFlow()

    private var plu: MutableMap<String, String> = loadMap("plu")
    private var pluManual: MutableMap<String, String> = loadMap("plu_manual")

    private fun loadMap(k: String): MutableMap<String, String> = runCatching {
        @Suppress("UNCHECKED_CAST")
        (Json.parse(p.getString(k, "{}").orEmpty()) as? Map<String, Any?>).orEmpty().mapValues { it.value.toString() }.toMutableMap()
    }.getOrDefault(HashMap())

    private fun load(): LabelState {
        val items = runCatching {
            (Json.parse(p.getString("items", "[]").orEmpty()) as? List<*>).orEmpty().mapNotNull { x ->
                @Suppress("UNCHECKED_CAST")
                val o = JObj(x as? Map<String, Any?> ?: return@mapNotNull null)
                val pr = (o.map["pr"] as? Number)?.toDouble()
                LabelItem(o.str("id"), o.long("n", 1).toInt().coerceIn(1, 999), pr).takeIf { it.productId.isNotEmpty() }
            }
        }.getOrDefault(emptyList())
        val info = p.getString("info", null)?.let { s -> runCatching { infoFromJson(JObj.parse(s)) }.getOrNull() }
        return LabelState(
            items, if (p.getString("ptype", "cash") == "wholesale") PriceType.WHOLESALE else PriceType.CASH,
            p.getString("host", "").orEmpty(), p.getInt("port", 9110), p.getBoolean("cloud", true),
            p.getFloat("wmm", 58f).toDouble(), p.getFloat("hmm", 40f).toDouble(), p.getInt("dpi", 203), info, "",
            loadMap("plu").size + loadMap("plu_manual").size,
            p.getLong("codes_at", 0L), p.getInt("codes_plu", 0), p.getInt("codes_bc", 0), p.getString("codes_note", "").orEmpty(),
            p.getLong("codes_asked", 0L),
        )
    }

    private fun infoToJson(i: PrinterInfo) = Json.write(
        linkedMapOf("host" to i.host, "printer" to i.printer, "mode" to i.mode, "wpx" to i.wPx, "hpx" to i.hPx, "dpi" to i.dpi, "wmm" to i.wMm, "hmm" to i.hMm, "at" to i.at),
    )

    private fun infoFromJson(o: JObj) = PrinterInfo(
        o.str("host"), o.str("printer"), o.str("mode"), o.long("wpx").toInt(), o.long("hpx").toInt(), o.long("dpi").toInt(), o.num("wmm"), o.num("hmm"), o.long("at"),
    )

    private fun saveItems(items: List<LabelItem>) {
        _state.update { it.copy(items = items) }
        p.edit().putString("items", Json.write(items.map { linkedMapOf<String, Any?>("id" to it.productId, "n" to it.copies).apply { it.price?.let { pr -> put("pr", pr) } } })).apply()
    }

    // ---------------- ro'yxat ----------------

    fun copies(productId: String): Int = _state.value.items.firstOrNull { it.productId == productId }?.copies ?: 0

    fun add(productId: String, n: Int = 1) {
        val l = _state.value.items
        val cur = l.firstOrNull { it.productId == productId }
        saveItems(
            if (cur == null) l + LabelItem(productId, n.coerceIn(1, 999))
            else l.map { if (it.productId == productId) it.copy(copies = (it.copies + n).coerceIn(1, 999)) else it },
        )
    }

    fun setCopies(productId: String, n: Int) {
        val l = _state.value.items
        saveItems(if (n <= 0) l.filter { it.productId != productId } else if (l.any { it.productId == productId }) l.map { if (it.productId == productId) it.copy(copies = n.coerceAtMost(999)) else it } else l + LabelItem(productId, n.coerceAtMost(999)))
    }

    fun setPrice(productId: String, price: Double?) {
        saveItems(_state.value.items.map { if (it.productId == productId) it.copy(price = price?.takeIf { v -> v > 0 }) else it })
    }

    fun remove(productId: String) = saveItems(_state.value.items.filter { it.productId != productId })
    fun clear() = saveItems(emptyList())

    fun setPriceType(t: PriceType) {
        _state.update { it.copy(priceType = t) }
        p.edit().putString("ptype", if (t == PriceType.WHOLESALE) "wholesale" else "cash").apply()
    }

    /** Yorliqdagi narx (so'mda): qo'lda yozilgani yoki katalogdan (tanlangan narx turi). */
    fun priceOf(prod: Product, item: LabelItem? = _state.value.items.firstOrNull { it.productId == prod.id }): Double =
        item?.price ?: Pricing.unitPrice(prod, _state.value.priceType, Currency.SOM, catalog.state.value.rate)

    // ---------------- PLU ----------------

    fun pluFor(productId: String): String? = pluManual[productId] ?: plu[productId]

    fun setPlu(productId: String, value: String?) {
        val v = value?.filter { it.isDigit() }?.trimStart('0')?.ifEmpty { null }
        if (v == null) pluManual.remove(productId) else pluManual[productId] = v
        p.edit().putString("plu_manual", Json.write(pluManual)).apply()
        _state.update { it.copy(pluCount = (plu.keys + pluManual.keys).size) }
    }

    suspend fun previewPlu(bytes: ByteArray, name: String): PluPreview = withContext(Dispatchers.Default) {
        val rows = PluImport.parse(bytes, name)
        val (map, missing) = PluImport.link(rows, catalog.state.value.products, { it.id }, { it.name })
        PluPreview(rows, map, missing)
    }

    /** Import qilingan PLU ro'yxatini almashtiradi (qo'lda yozilganlar ustun turadi). */
    fun commitPlu(pv: PluPreview) {
        plu = HashMap(pv.map)
        p.edit().putString("plu", Json.write(plu)).apply()
        _state.update { it.copy(pluCount = (plu.keys + pluManual.keys).size) }
    }

    // ---------------- kompyuterdan tovar kodlari (1C: PLU va shtrix) ----------------

    private fun codesFile() = java.io.File(context.filesDir, "codes_1c.json")

    /**
     * onec_export.py yuborgan tovar kodlari: PLU (qo'lda yozilganlar ustun turadi) va shtrix kodlar.
     * Fayl saqlanadi — katalog yangilansa qayta bog'lanadi ([relinkIfNeeded]).
     */
    suspend fun applyCodes(bytes: ByteArray) {
        val d = withContext(Dispatchers.Default) { CodesPayload.parse(bytes) }
        withContext(Dispatchers.IO) { codesFile().writeBytes(bytes) }
        link(d)
    }

    /** Katalog yangilangandan keyin (yoki birinchi marta) saqlangan kodlarni qayta bog'lash. */
    suspend fun relinkIfNeeded() {
        val sync = catalog.state.value.lastSyncAt
        if (catalog.state.value.products.isEmpty() || p.getLong("codes_sync", -1L) == sync) return
        val f = codesFile()
        if (!f.exists()) return
        val d = runCatching { withContext(Dispatchers.IO) { CodesPayload.parse(f.readBytes()) } }.getOrNull() ?: return
        link(d)
    }

    private suspend fun link(d: CodesPayload.Data) {
        val products = catalog.state.value.products
        if (products.isEmpty()) return // katalog hali yuklanmagan — fayl saqlangan, keyin bog'lanadi
        var pluN = _state.value.codesPlu
        var bcN = _state.value.codesBarcodes
        if (d.pluRows.isNotEmpty()) {
            val (map, _) = withContext(Dispatchers.Default) { PluImport.link(d.pluRows, products, { it.id }, { it.name }) }
            plu = HashMap(map)
            p.edit().putString("plu", Json.write(plu)).apply()
            pluN = map.size
        }
        if (d.barcodeRows.isNotEmpty()) bcN = barcodes.commitRows(d.barcodeRows, "1C (kompyuter)").linked.map.size
        val now = System.currentTimeMillis()
        val note = listOf(d.pluSource.ifEmpty { "-" }, d.barcodeSource.ifEmpty { "-" }).joinToString(" | ")
        p.edit().putLong("codes_at", now).putInt("codes_plu", pluN).putInt("codes_bc", bcN).putString("codes_note", note)
            .putLong("codes_sync", catalog.state.value.lastSyncAt).putLong("codes_asked", 0L).apply()
        _state.update { it.copy(codesAt = now, codesPlu = pluN, codesBarcodes = bcN, codesNote = note, codesAskedAt = 0L, pluCount = (plu.keys + pluManual.keys).size) }
    }

    /** Kompyuterdan hozir yangilashni so'rash (kompyuter ~2 daqiqada bir tekshiradi). */
    suspend fun requestCodes() {
        if (!share.isCloudReady || share.inboxKey.isEmpty()) throw LabelPrinterClient.PrintError("no_cloud")
        withContext(Dispatchers.IO) { CloudDb(share.dbUrl, share.secret).put("p/${share.inboxKey}/creq", CloudDb.SERVER_TIME, timeoutMs = 30_000) }
        val now = System.currentTimeMillis()
        p.edit().putLong("codes_asked", now).apply()
        _state.update { it.copy(codesAskedAt = now) }
    }

    // ---------------- printer sozlamasi ----------------

    fun setHost(host: String, port: Int) {
        _state.update { it.copy(host = host.trim(), port = port) }
        p.edit().putString("host", host.trim()).putInt("port", port).apply()
    }

    fun setCloud(on: Boolean) { _state.update { it.copy(cloud = on) }; p.edit().putBoolean("cloud", on).apply() }

    fun setSize(wMm: Double, hMm: Double, dpi: Int) {
        _state.update { it.copy(wMm = wMm, hMm = hMm, dpi = dpi) }
        p.edit().putFloat("wmm", wMm.toFloat()).putFloat("hmm", hMm.toFloat()).putInt("dpi", dpi).apply()
    }

    private fun cloudDb(): CloudDb? = if (_state.value.cloud && share.isCloudReady) CloudDb(share.dbUrl, share.secret) else null

    /** Kompyuterdan printer ma'lumoti (LAN, bo'lmasa bulutdagi oxirgi holat). */
    suspend fun refreshInfo(): PrinterInfo? {
        val s = _state.value
        val key = share.labelKey
        val info = try {
            if (s.host.isNotBlank()) client.infoLan(s.host, s.port, key) else cloudDb()?.let { client.infoCloud(it, key) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val cloudInfo = runCatching { cloudDb()?.let { client.infoCloud(it, key) } }.getOrNull()
            _state.update { it.copy(infoError = e.message ?: e.javaClass.simpleName, info = cloudInfo ?: it.info) }
            return cloudInfo
        }
        if (info != null) p.edit().putString("info", infoToJson(info)).apply()
        _state.update { it.copy(info = info ?: it.info, infoError = "") }
        return info
    }

    suspend fun discover(): List<FoundPc> = client.discover(_state.value.port)

    /** Yorliq o'lchami nuqtalarda: kompyuter aytgani (Windows drayveridagi qog'oz), bo'lmasa sozlamadagi mm × dpi. */
    fun sizePx(): Pair<Int, Int> {
        val s = _state.value
        val i = s.info
        if (i != null && i.wPx in 80..3000 && i.hPx in 60..3000) return i.wPx to i.hPx
        val k = s.dpi / 25.4
        return (s.wMm * k).toInt() to (s.hMm * k).toInt()
    }

    // ---------------- tayyorlash va chop etish ----------------

    suspend fun build(kind: LabelKind): LabelBatch {
        val byId = catalog.state.value.productsById
        val out = ArrayList<LabelData>()
        val skipped = ArrayList<Pair<String, LabelBatch.Skip>>()
        for (it in _state.value.items) {
            val prod = byId[it.productId]
            if (prod == null) { skipped += it.productId to LabelBatch.Skip.NOT_IN_CATALOG; continue }
            val price = priceOf(prod, it)
            if (price <= 0) { skipped += prod.id to LabelBatch.Skip.NO_PRICE; continue }
            when (kind) {
                LabelKind.PLU -> {
                    val code = pluFor(prod.id)
                    if (code == null) skipped += prod.id to LabelBatch.Skip.NO_PLU
                    else out += LabelData(prod.name, LabelPrice.som(price), plu = code, copies = it.copies)
                }
                LabelKind.BARCODE -> {
                    val bc = barcodes.barcodesFor(prod.id).firstOrNull { c -> BarcodeEncode.forShopCode(c) != null }
                    if (bc == null) skipped += prod.id to LabelBatch.Skip.NO_BARCODE
                    else out += LabelData(prod.name, LabelPrice.som(price), barcode = bc, copies = it.copies)
                }
            }
        }
        val (w, h) = sizePx()
        return LabelBatch(kind, out, skipped, w, h)
    }

    fun render(b: LabelBatch, d: LabelData) = LabelBitmap.render(d, b.kind, b.wPx, b.hPx)

    suspend fun print(b: LabelBatch, only: List<LabelData> = b.labels): LabelPrinterClient.Sent {
        val pngs = withContext(Dispatchers.Default) { only.map { LabelBitmap.png(render(b, it)) to it.copies } }
        val id = "j" + System.currentTimeMillis().toString(36) + (100..999).random()
        val s = _state.value
        return client.send(LabelPrinterClient.Job(id, pngs, only.map { it.name }), s.host, s.port, share.labelKey, cloudDb())
    }

    /** Sinov yorlig'i (PLU shaklida): o'lcham va printerni tekshirish. */
    suspend fun printTest(name: String): LabelPrinterClient.Sent {
        val (w, h) = sizePx()
        val b = LabelBatch(LabelKind.PLU, listOf(LabelData(name, LabelPrice.som(12345.0), plu = "1")), emptyList(), w, h)
        return print(b)
    }

    /** Kompyuter sozlamasi: faqat yorliq printeri uchun (Firebase sozlanmagan bo'lsa ham). */
    fun iniForPc(): String = "[labels]\n; Narx yorlig'i printeri (label_printer.py)\nkey = ${share.labelKey}\nprinter = \nmode = gdi\nport = ${_state.value.port}\n" +
        if (share.isCloudReady) "\n[firebase]\nurl = ${share.dbUrl}\nsecret = ${share.secret}\n" else ""
}
