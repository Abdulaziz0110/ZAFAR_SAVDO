package com.ilxomjon.dur_novvot_dokon.data.share

import android.content.Context
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.DealMode
import com.ilxomjon.dur_novvot_dokon.domain.SentOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uz.imsoft.shared.B64
import uz.imsoft.shared.CloudDb
import uz.imsoft.shared.JObj
import uz.imsoft.shared.Json
import uz.imsoft.shared.android.ReceiptBitmap
import uz.imsoft.shared.receipt.NumFmt
import uz.imsoft.shared.receipt.Receipt
import uz.imsoft.shared.report.AppSale
import uz.imsoft.shared.report.Coverage
import uz.imsoft.shared.report.LedgerEntry
import uz.imsoft.shared.report.Stamp
import java.io.File
import java.util.Calendar

/**
 * 1-ilovadagi savdoni mijozning 2-ilovasiga "ko'zgu" qiladi (v2.6):
 *  1) savdo 2-ilovaning 1C tarixida darhol ko'rinadi ("1-ilovadan" belgisi bilan; 1C hisoboti shu vaqtni qamragach — chiqib ketadi);
 *  2) mijoz 2-ilovada "1-ilova savdolari chekini qabul qilish" ni yoqqan bo'lsa (`p/{qk}/auto`), chek uning printer
 *     navbatiga (`q/{qk}`) qo'yiladi — printer ulanishi bilan navbatdagi hamma cheklar chiqadi.
 * Internet bo'lmasa chek diskda kutadi va keyingi aylanishda yuboriladi. Serverga (1C/SQL) hech narsa yozilmaydi.
 */
class SaleMirror(private val context: Context, private val store: ShareStore) {
    private val mutex = Mutex()
    private val autoCache = HashMap<String, Pair<Long, Boolean>>()

    private fun dir() = File(context.filesDir, "share").apply { mkdirs() }
    private fun salesFile(clientId: String) = File(dir(), "app_sales_" + clientId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { "x" } + ".json")
    private fun pendingFile() = File(dir(), "receipts_pending.json")
    private fun cloud() = CloudDb(store.dbUrl, store.secret)

    private fun writeAtomic(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    fun sales(clientId: String): List<AppSale> = runCatching {
        val f = salesFile(clientId)
        if (!f.exists()) emptyList() else (Json.parse(f.readText()) as? List<*>).orEmpty().mapNotNull { x ->
            @Suppress("UNCHECKED_CAST")
            (x as? Map<String, Any?>)?.let { AppSale.fromJson(JObj(it)) }
        }
    }.getOrDefault(emptyList())

    /** 2-ilovaga qo'shiladigan qatorlar: 1C hisoboti hali qamramagan savdolar. */
    fun entriesFor(clientId: String, ledger: List<LedgerEntry>, coverage: List<Coverage>): List<LedgerEntry> {
        val list = sales(clientId)
        if (list.isEmpty()) return emptyList()
        // Hisobot "bugun"gacha bo'lsa ham import qilingan paytdan keyingi savdolar unda yo'q. v3.4: 1C savdoni bir necha
        // daqiqada qabul qiladi — import vaqtidan 5 daqiqa oldingisigacha "qamralgan" hisoblanadi; qolganini hisobotdagi
        // aynan o'sha hujjat (tur, vaqt, summa) bo'yicha solishtiramiz — ikki marta ko'rinmaydi, yo'qolib ham qolmaydi.
        val covered = coverage.maxOfOrNull { minOf(it.to, stampOf(it.importedAt - 5 * 60_000L)) } ?: 0L
        return AppSale.entriesNotIn(list, covered, ledger)
    }

    /** Savdo yuborilganda chaqiriladi. @return mijoz (bog'langan do'kon bo'lsa), aks holda null. */
    suspend fun record(sent: SentOrder, outboxId: Long, code: String, debtBefore: Double? = null, saleId: String? = null): Pair<ShareClient, AppSale>? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val c = store.clients().firstOrNull { it.storeId.isNotEmpty() && it.storeId == sent.storeId } ?: return@withContext null
            val s = AppSale(
                id = saleId ?: "o$outboxId", at = sent.sentAt, stamp = stampOf(sent.sentAt), isReturn = sent.mode == DealMode.RETURN,
                usd = sent.currency == Currency.USD, lines = sent.lines.map { AppSale.Line(it.name, it.qty, it.price) },
                discount = sent.discount, code = code, debtBefore = debtBefore, paid = sent.paid,
                cash = sent.cash, card = sent.card,
            )
            val list = (sales(c.id).filter { it.id != s.id } + s).takeLast(AppSale.KEEP)
            writeAtomic(salesFile(c.id), Json.write(list.map { AppSale.toJson(it) }))
            c to s
        }
    }

    /** v3.2: 1-ilovada (Admin/Agent) kiritilgan to'lov — 2-ilova tarixida darhol (1C hisoboti qamragunicha). */
    suspend fun recordPayment(p: com.ilxomjon.dur_novvot_dokon.domain.SentPayment, id: String, code: String): ShareClient? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val c = store.clients().firstOrNull { it.storeId.isNotEmpty() && it.storeId == p.storeId } ?: return@withContext null
            if (p.total <= 0.0) return@withContext null
            val s = AppSale(
                id = id, at = p.sentAt, stamp = stampOf(p.sentAt), isReturn = false, usd = false, lines = emptyList(), discount = 0.0,
                code = code, paid = p.total, payment = true, note = listOf(code, p.note).filter { it.isNotBlank() }.joinToString(" · "),
            )
            val list = (sales(c.id).filter { it.id != s.id } + s).takeLast(AppSale.KEEP)
            writeAtomic(salesFile(c.id), Json.write(list.map { AppSale.toJson(it) }))
            c
        }
    }

    // ---------------- chek -> mijoz printeri ----------------

    /** Mijoz 2-ilovada chek qabul qilishni yoqqanmi (5 daqiqa keshlanadi). */
    private fun autoOn(c: ShareClient): Boolean {
        val now = System.currentTimeMillis()
        autoCache[c.queueKey]?.let { (at, v) -> if (now - at < 5 * 60_000L) return v }
        val v = (cloud().get("p/${c.queueKey}/auto") as? Boolean) == true
        autoCache[c.queueKey] = now to v
        return v
    }

    /** Chekni navbatga qo'yadi; internet bo'lmasa — diskda kutadi. */
    /** [force]: true — 2-filial printerida albatta, false — chiqarilmaydi, null — 2-ilovadagi "avtomatik" sozlamasi bo'yicha. */
    suspend fun pushReceipt(c: ShareClient, s: AppSale, force: Boolean? = null) {
        if (c.queueKey.isEmpty() || !store.isCloudReady || force == false) return
        val ok = runCatching { withContext(Dispatchers.IO) { if (force ?: autoOn(c)) send(c, s) } }.isSuccess
        if (!ok) mutex.withLock { withContext(Dispatchers.IO) { addPending(c.id, s.id) } }
    }

    /** Kutayotgan cheklarni qayta yuborish (fon ishchisi). */
    suspend fun retryPending() = mutex.withLock {
        withContext(Dispatchers.IO) {
            val pend = readPending()
            if (pend.isEmpty() || !store.isCloudReady) return@withContext
            val left = ArrayList<Pair<String, String>>()
            for ((cid, sid) in pend) {
                val c = store.client(cid) ?: continue
                val s = sales(cid).firstOrNull { it.id == sid } ?: continue
                if (System.currentTimeMillis() - s.at > 3 * 86_400_000L) continue // 3 kundan eski — endi kerak emas
                val ok = runCatching { if (c.queueKey.isNotEmpty() && autoOn(c)) send(c, s) }.isSuccess
                if (!ok) left += cid to sid
            }
            writeAtomic(pendingFile(), Json.write(left.map { listOf(it.first, it.second) }))
        }
    }

    private fun readPending(): List<Pair<String, String>> = runCatching {
        val f = pendingFile()
        if (!f.exists()) emptyList() else (Json.parse(f.readText()) as? List<*>).orEmpty().mapNotNull { x ->
            val a = x as? List<*> ?: return@mapNotNull null
            (a.getOrNull(0)?.toString() ?: return@mapNotNull null) to (a.getOrNull(1)?.toString() ?: return@mapNotNull null)
        }
    }.getOrDefault(emptyList())

    private fun addPending(cid: String, sid: String) {
        val list = (readPending().filter { it != cid to sid } + (cid to sid)).takeLast(50)
        writeAtomic(pendingFile(), Json.write(list.map { listOf(it.first, it.second) }))
    }

    /** Navbat kaliti chek id siga bog'liq — qayta yuborilsa ham bitta nusxa (dublikat chek chiqmaydi). */
    private fun send(c: ShareClient, s: AppSale) {
        val r = receipt(c, s)
        val style = uz.imsoft.shared.receipt.ReceiptStyle.STANDARD
        val bytes = ReceiptBitmap.toEscPos(ReceiptBitmap.render(r, style, uz.imsoft.shared.android.LogoCache.get(store.receiptLogo)), style.dark)
        val key = "a" + s.at + "_" + s.id.filter { it.isLetterOrDigit() }
        val db = cloud()
        val summary = r.title + " · " + s.lines.size + " · " + r.totals.lastOrNull { it.bold }?.value.orEmpty()
        db.put("q/${c.queueKey}/$key", linkedMapOf("t" to CloudDb.SERVER_TIME, "b" to B64.encode(bytes), "n" to bytes.size, "src" to "sale:" + s.code))
        runCatching { db.put("p/${c.queueKey}/jl/$key", linkedMapOf("t" to CloudDb.SERVER_TIME, "s" to summary.take(160))) }
        runCatching { db.put("p/${c.queueKey}/sig", CloudDb.SERVER_TIME) }
    }

    /**
     * Do'kon chekiga o'xshash: nom, Xodim / Chek № / Vaqt, Mijoz, oldingi qarz, (QAYTIB OLISH), qatorlar, Summa, to'lov,
     * qarz qoldig'i va telefonlar. Qarz qoldig'i 1-ilova hisobi — server tasdig'i emas, shuning uchun "≈" bilan.
     */
    private fun receipt(c: ShareClient, s: AppSale): Receipt = build(c.name, s)

    private fun build(clientName: String, s: AppSale): Receipt {
        val ctx = context
        fun bare(v: Double) = NumFmt.bare(v, s.usd)
        val lines = s.lines.map { l -> Receipt.Line(l.name, NumFmt.qty(l.qty) + " x " + bare(l.price), bare(l.qty * l.price)) }
        val net = s.total - (if (s.isReturn) 0.0 else s.discount)
        val totals = ArrayList<Receipt.Total>()
        if (s.discount > 0 && !s.isReturn) {
            totals += Receipt.Total(ctx.getString(R.string.lg_total), bare(s.total))
            totals += Receipt.Total(ctx.getString(R.string.lg_discount), "−" + bare(s.discount))
        }
        totals += Receipt.Total(ctx.getString(R.string.lg_rc_sum), bare(net), bold = true)
        val after = ArrayList<Receipt.Total>()
        if (s.paid > 0) after += Receipt.Total(ctx.getString(if (s.usd) R.string.rc_paid_usd else R.string.rc_paid_som), bare(s.paid))
        val before = ArrayList<Receipt.Total>()
        val debt = s.debtBefore
        if (debt != null && !s.usd) {
            before += Receipt.Total(ctx.getString(R.string.lg_rc_debt), NumFmt.som(debt))
            val afterDebt = if (s.isReturn) debt - net + s.paid else debt + net - s.paid
            after += Receipt.Total(ctx.getString(R.string.lg_rc_debt), "≈ " + NumFmt.som(afterDebt))
        }
        val (time, date) = uz.imsoft.shared.receipt.ReceiptBuilder.timeAndDate(s.stamp)
        val info = ArrayList<Pair<String, String>>()
        val keys = ArrayList<String>()
        if (store.receiptStaff.isNotBlank()) { info += ctx.getString(R.string.lg_rc_staff) to store.receiptStaff; keys += Receipt.KEY_STAFF }
        if (s.code.isNotBlank()) { info += ctx.getString(R.string.lg_rc_number) to s.code; keys += Receipt.KEY_NUMBER }
        info += ctx.getString(R.string.lg_rc_time) to time
        keys += Receipt.KEY_TIME
        val title = ctx.getString(if (s.isReturn) R.string.mir_receipt_return else R.string.mir_receipt_sale)
        return Receipt(
            store.receiptHeader, title, ctx.getString(R.string.lg_client) + ": " + clientName, Stamp.formatDateTime(s.stamp),
            lines, totals, if (debt != null && !s.usd) listOf(ctx.getString(R.string.rc_note_estimate)) else emptyList(),
            clientLabel = ctx.getString(R.string.lg_client), clientName = clientName, info = info, date = date,
            before = before, banner = if (s.isReturn) ctx.getString(R.string.lg_rc_return) else "", after = after,
            footer = store.receiptFooter.split('\n').map { it.trim() }.filter { it.isNotEmpty() }, infoKeys = keys,
            // v5.0.2: chek 1C "Чек1" maketidagidek chiqadi
            chek = uz.imsoft.shared.receipt.Chek.ofSale(
                s, s.code, clientName, store.receiptStaff,
                uz.imsoft.shared.receipt.Chek.phoneOf(store.receiptFooter.split('\n')),
            ),
        )
    }

    /** Sozlamalar ekranidagi "Namuna chek". */
    fun sampleReceipt(): Receipt = build(
        "Dadaxon Aka (TRZ)",
        AppSale(
            "o0", System.currentTimeMillis(), stampOf(System.currentTimeMillis()), false, false,
            listOf(AppSale.Line("Bodom Kalifor M", 40.0, 117_000.0), AppSale.Line("Pista Akbariy (AKTC)", 20.0, 205_000.0), AppSale.Line("Keshu 210", 0.202, 155_000.0)),
            0.0, "B-0001", debtBefore = 148_542_126.0, paid = 0.0,
        ),
    )

    companion object {
        fun stampOf(ms: Long): Long {
            if (ms <= 0) return 0L
            val k = Calendar.getInstance().apply { timeInMillis = ms }
            return Stamp.of(k.get(Calendar.YEAR), k.get(Calendar.MONTH) + 1, k.get(Calendar.DAY_OF_MONTH), k.get(Calendar.HOUR_OF_DAY), k.get(Calendar.MINUTE), k.get(Calendar.SECOND))
        }
    }
}
