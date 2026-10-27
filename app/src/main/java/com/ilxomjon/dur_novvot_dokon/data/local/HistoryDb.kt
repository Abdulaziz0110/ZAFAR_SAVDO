package com.ilxomjon.dur_novvot_dokon.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.DealMode
import com.ilxomjon.dur_novvot_dokon.domain.PriceType
import com.ilxomjon.dur_novvot_dokon.domain.SentLine
import com.ilxomjon.dur_novvot_dokon.domain.SentOrder
import com.ilxomjon.dur_novvot_dokon.domain.SentPayment
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxEntry
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxKind
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxState
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Do'kon qarzi keshi: serverdan olingan qiymat va olingan vaqt. */
data class DebtCache(val storeId: String, val som: Double, val usd: Double, val fetchedAt: Long)

/** Navbat holatlari bo'yicha sonlar (UI uchun). */
data class OutboxCounts(val ready: Int = 0, val sending: Int = 0, val retry: Int = 0, val uncertain: Int = 0, val needsCheck: Int = 0) {
    /** Hali serverga yozilmagan (yoki yozilgani noma'lum) jami. */
    val unsent: Int get() = ready + sending + retry + uncertain + needsCheck
}

class KpiRow(val start: Long, val end: Long, val taps: Int, val items: Int)

/**
 * Yuborilgan buyurtma/to'lovlar jurnali va serverga yuborilishi kutilayotganlar navbati (outbox).
 * Alohida fayl: asosiy bazaning (assafocopy.sqlite) sxemasi o'zgarmaydi.
 *
 * Versiyalar (faqat qo'shimcha — additive):
 *  v2: sent_order, sent_payment, outbox (eski 2.2)
 *  v3: outbox ga holat mashinasi ustunlari; debt_cache, suggestion_feedback, kpi_session, migration_log
 */
class HistoryDb(context: Context) : SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION), OutboxStore {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE sent_order (id INTEGER PRIMARY KEY AUTOINCREMENT, sent_at INTEGER NOT NULL, store_name TEXT, " +
                "mode TEXT, price_type TEXT, currency TEXT, total REAL, discount REAL, paid REAL, lines TEXT, " +
                "store_id TEXT DEFAULT '', pending INTEGER DEFAULT 0, outbox_id INTEGER DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE sent_payment (id INTEGER PRIMARY KEY AUTOINCREMENT, sent_at INTEGER NOT NULL, store_name TEXT, " +
                "cash REAL, transfer REAL, click REAL, note TEXT, " +
                "store_id TEXT DEFAULT '', pending INTEGER DEFAULT 0, outbox_id INTEGER DEFAULT 0)"
        )
        createOutboxV2(db)
        upgradeToV3(db)
        log(db, 0, VERSION, true, "create")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // SQLiteOpenHelper buni bitta tranzaksiyada bajaradi: xato bo'lsa hammasi qaytariladi.
        if (oldVersion < 2) {
            for (t in listOf("sent_order", "sent_payment")) {
                addColumn(db, t, "store_id", "TEXT DEFAULT ''")
                addColumn(db, t, "pending", "INTEGER DEFAULT 0")
                addColumn(db, t, "outbox_id", "INTEGER DEFAULT 0")
            }
            createOutboxV2(db)
        }
        if (oldVersion < 3) upgradeToV3(db)
        log(db, oldVersion, newVersion, true, "upgrade")
    }

    /** Eski APK'ga qaytilganda emas, yangi bazali zaxira eski kodga tushganda: sxema faqat qo'shimcha, ma'lumot saqlanadi. */
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        log(db, oldVersion, newVersion, true, "downgrade-noop")
    }

    private fun createOutboxV2(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS outbox (id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, agent_id TEXT, " +
                "payload TEXT NOT NULL, created_at INTEGER, attempts INTEGER DEFAULT 0, last_error TEXT)"
        )
    }

    private fun upgradeToV3(db: SQLiteDatabase) {
        // Eski (v2) qatorlar uchun DEFAULT 'UNCERTAIN': ular natijasi noma'lum holda qolgan bo'lishi mumkin -> avval tekshiriladi.
        addColumn(db, "outbox", "state", "TEXT DEFAULT 'UNCERTAIN'")
        addColumn(db, "outbox", "local_uuid", "TEXT")
        addColumn(db, "outbox", "watermark", "INTEGER")
        addColumn(db, "outbox", "server_row_id", "TEXT")
        addColumn(db, "outbox", "lease_until", "INTEGER DEFAULT 0")
        addColumn(db, "outbox", "next_at", "INTEGER DEFAULT 0")
        addColumn(db, "outbox", "phase", "INTEGER DEFAULT 0")
        addColumn(db, "outbox", "prev_state", "TEXT")
        addColumn(db, "outbox", "check_reason", "TEXT")
        addColumn(db, "outbox", "force_send", "INTEGER DEFAULT 0")
        addColumn(db, "outbox", "updated_at", "INTEGER DEFAULT 0")
        addColumn(db, "outbox", "store_id", "TEXT DEFAULT ''")
        addColumn(db, "outbox", "store_name", "TEXT DEFAULT ''")
        addColumn(db, "outbox", "amount", "REAL DEFAULT 0")
        addColumn(db, "outbox", "sent_at", "INTEGER DEFAULT 0")
        db.execSQL("UPDATE outbox SET local_uuid = lower(hex(randomblob(16))) WHERE local_uuid IS NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS outbox_state ON outbox(state, next_at)")
        db.execSQL("CREATE TABLE IF NOT EXISTS debt_cache (store_id TEXT PRIMARY KEY, som REAL, usd REAL, fetched_at INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS suggestion_feedback (id INTEGER PRIMARY KEY AUTOINCREMENT, store_id TEXT, product_id TEXT, reason TEXT, ts INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS kpi_session (id INTEGER PRIMARY KEY AUTOINCREMENT, start_ts INTEGER, end_ts INTEGER, taps INTEGER, items INTEGER, store_hash TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS migration_log (id INTEGER PRIMARY KEY AUTOINCREMENT, from_v INTEGER, to_v INTEGER, at INTEGER, ok INTEGER, note TEXT)")
    }

    private fun addColumn(db: SQLiteDatabase, table: String, column: String, def: String) {
        db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            val i = c.getColumnIndex("name")
            while (c.moveToNext()) if (c.getString(i) == column) return
        }
        db.execSQL("ALTER TABLE $table ADD COLUMN $column $def")
    }

    private fun log(db: SQLiteDatabase, from: Int, to: Int, ok: Boolean, note: String) {
        runCatching {
            db.execSQL("CREATE TABLE IF NOT EXISTS migration_log (id INTEGER PRIMARY KEY AUTOINCREMENT, from_v INTEGER, to_v INTEGER, at INTEGER, ok INTEGER, note TEXT)")
            db.execSQL("INSERT INTO migration_log (from_v, to_v, at, ok, note) VALUES (?, ?, ?, ?, ?)", arrayOf(from, to, System.currentTimeMillis(), if (ok) 1 else 0, note))
        }
    }

    private inline fun <T> tx(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val r = block(db)
            db.setTransactionSuccessful()
            return r
        } finally {
            db.endTransaction()
        }
    }

    // ---------------- navbatga qo'yish ----------------

    /** Buyurtmani navbatga qo'yadi va jurnalga yozadi (bitta tranzaksiyada). @return outbox id */
    fun enqueueOrder(agentId: String, payload: String, o: SentOrder): Long = tx { db ->
        val outboxId = insertOutbox(db, OutboxKind.ORDER, agentId, payload, o.storeId, o.storeName, o.total - o.discount)
        val arr = JSONArray()
        for (l in o.lines) arr.put(JSONObject().put("n", l.name).put("q", l.qty).put("p", l.price).put("i", l.productId))
        val cv = ContentValues().apply {
            put("sent_at", o.sentAt); put("store_id", o.storeId); put("store_name", o.storeName); put("mode", o.mode.code)
            put("price_type", o.priceType.code); put("currency", o.currency.code)
            put("total", o.total); put("discount", o.discount); put("paid", o.paid); put("lines", arr.toString())
            put("pending", 1); put("outbox_id", outboxId)
        }
        db.insert("sent_order", null, cv)
        trim(db, "sent_order")
        outboxId
    }

    fun enqueuePayment(agentId: String, payload: String, p: SentPayment): Long = tx { db ->
        val outboxId = insertOutbox(db, OutboxKind.PAYMENT, agentId, payload, p.storeId, p.storeName, p.total)
        val cv = ContentValues().apply {
            put("sent_at", p.sentAt); put("store_id", p.storeId); put("store_name", p.storeName)
            put("cash", p.cash); put("transfer", p.transfer); put("click", p.click); put("note", p.note)
            put("pending", 1); put("outbox_id", outboxId)
        }
        db.insert("sent_payment", null, cv)
        trim(db, "sent_payment")
        outboxId
    }

    private fun insertOutbox(db: SQLiteDatabase, kind: OutboxKind, agentId: String, payload: String, storeId: String, storeName: String, amount: Double): Long {
        val now = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put("kind", kind.code); put("agent_id", agentId); put("payload", payload); put("created_at", now)
            put("state", OutboxState.READY.code); put("local_uuid", UUID.randomUUID().toString()); put("updated_at", now)
            put("store_id", storeId); put("store_name", storeName); put("amount", amount)
        }
        return db.insertOrThrow("outbox", null, cv)
    }

    private fun trim(db: SQLiteDatabase, table: String) {
        db.execSQL("DELETE FROM $table WHERE pending = 0 AND id NOT IN (SELECT id FROM $table ORDER BY id DESC LIMIT $KEEP)")
    }

    /** Eski, serverga yozilgan navbat qatorlarini tozalaydi (jadval cheksiz o'smasin). */
    fun purgeOld(now: Long) {
        writableDatabase.execSQL(
            "DELETE FROM outbox WHERE state IN ('SENT','SENT_MANUAL') AND sent_at > 0 AND sent_at < ?",
            arrayOf(now - KEEP_SENT_MS),
        )
    }

    // ---------------- OutboxStore ----------------

    override fun claimNext(now: Long, leaseMs: Long): OutboxEntry? = tx { db ->
        val sql = "SELECT $COLS FROM outbox WHERE state = 'READY' OR (state IN ('RETRY_WAIT','UNCERTAIN') AND next_at <= ?) " +
            "OR (state = 'SENDING' AND lease_until < ?) ORDER BY id LIMIT 1"
        val e = db.rawQuery(sql, arrayOf(now.toString(), now.toString())).use { c -> if (c.moveToFirst()) readEntry(c) else null } ?: return@tx null
        db.execSQL(
            "UPDATE outbox SET state = 'SENDING', prev_state = ?, lease_until = ?, attempts = attempts + 1, updated_at = ? WHERE id = ? AND state = ?",
            arrayOf(e.state.code, now + leaseMs, now, e.id, e.state.code),
        )
        val changed = db.rawQuery("SELECT changes()", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        if (changed != 1) null
        else e.copy(state = OutboxState.SENDING, prevState = e.state, leaseUntil = now + leaseMs, attempts = e.attempts + 1)
    }

    override fun setWatermark(id: Long, watermark: Long, now: Long) {
        writableDatabase.execSQL("UPDATE outbox SET watermark = ?, updated_at = ? WHERE id = ?", arrayOf(watermark, now, id))
    }

    override fun markInserting(id: Long, now: Long) {
        writableDatabase.execSQL("UPDATE outbox SET phase = 1, force_send = 0, updated_at = ? WHERE id = ?", arrayOf(now, id))
    }

    override fun markSent(id: Long, serverRowId: String, now: Long) {
        tx { db ->
            db.execSQL(
                "UPDATE outbox SET state = 'SENT', server_row_id = ?, phase = 0, sent_at = ?, last_error = NULL, check_reason = NULL, updated_at = ? WHERE id = ?",
                arrayOf(serverRowId, now, now, id),
            )
            db.execSQL("UPDATE sent_order SET pending = 0 WHERE outbox_id = ?", arrayOf(id))
            db.execSQL("UPDATE sent_payment SET pending = 0 WHERE outbox_id = ?", arrayOf(id))
        }
    }

    override fun markRetry(id: Long, error: String?, nextAt: Long, now: Long) {
        writableDatabase.execSQL(
            "UPDATE outbox SET state = 'RETRY_WAIT', phase = 0, next_at = ?, last_error = ?, updated_at = ? WHERE id = ?",
            arrayOf(nextAt, error.orEmpty().take(300), now, id),
        )
    }

    override fun markUncertain(id: Long, error: String?, nextAt: Long, now: Long) {
        writableDatabase.execSQL(
            "UPDATE outbox SET state = 'UNCERTAIN', force_send = 0, next_at = ?, last_error = ?, updated_at = ? WHERE id = ?",
            arrayOf(nextAt, error.orEmpty().take(300), now, id),
        )
    }

    override fun markNeedsCheck(id: Long, reason: String, error: String?, now: Long) {
        writableDatabase.execSQL(
            "UPDATE outbox SET state = 'NEEDS_CHECK', force_send = 0, check_reason = ?, last_error = COALESCE(?, last_error), updated_at = ? WHERE id = ?",
            arrayOf(reason, error?.take(300), now, id),
        )
    }

    override fun lastServerRowId(agentId: String): String? =
        readableDatabase.rawQuery(
            "SELECT server_row_id FROM outbox WHERE agent_id = ? AND state = 'SENT' AND server_row_id IS NOT NULL ORDER BY sent_at DESC LIMIT 1",
            arrayOf(agentId),
        ).use { if (it.moveToFirst()) it.getString(0) else null }

    // ---------------- qo'lda amallar (agent tasdig'i bilan) ----------------

    /** "Baribir qayta yuborish": probe'siz bir marta yuboriladi. Faqat NEEDS_CHECK/UNCERTAIN/RETRY_WAIT uchun. */
    fun markForceSend(id: Long) {
        writableDatabase.execSQL(
            "UPDATE outbox SET state = 'READY', force_send = 1, phase = 0, next_at = 0, check_reason = NULL, updated_at = ? " +
                "WHERE id = ? AND state IN ('NEEDS_CHECK','UNCERTAIN','RETRY_WAIT')",
            arrayOf(System.currentTimeMillis(), id),
        )
    }

    /** Agent ofis bilan tekshirib "serverda bor" dedi. */
    fun markManualSent(id: Long) {
        tx { db ->
            val now = System.currentTimeMillis()
            db.execSQL(
                "UPDATE outbox SET state = 'SENT_MANUAL', phase = 0, sent_at = ?, updated_at = ? WHERE id = ? AND state IN ('NEEDS_CHECK','UNCERTAIN','RETRY_WAIT')",
                arrayOf(now, now, id),
            )
            db.execSQL("UPDATE sent_order SET pending = 0 WHERE outbox_id = ?", arrayOf(id))
            db.execSQL("UPDATE sent_payment SET pending = 0 WHERE outbox_id = ?", arrayOf(id))
        }
    }

    /** Zaxiradan tiklangandan keyin: yakunlanmagan hamma qator avtomatik yuborilmaydi. */
    fun markAllUnfinishedNeedsCheck(reason: String): Int = tx { db ->
        db.execSQL(
            "UPDATE outbox SET state = 'NEEDS_CHECK', check_reason = ?, force_send = 0, updated_at = ? WHERE state NOT IN ('SENT','SENT_MANUAL')",
            arrayOf(reason, System.currentTimeMillis()),
        )
        db.rawQuery("SELECT changes()", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun entry(id: Long): OutboxEntry? =
        readableDatabase.rawQuery("SELECT $COLS FROM outbox WHERE id = ?", arrayOf(id.toString())).use { if (it.moveToFirst()) readEntry(it) else null }

    /** Navbat ro'yxati: avval tekshirish kerak bo'lganlar, keyin yuborilmaganlar, keyin oxirgi yuborilganlar. */
    fun outboxList(limit: Int = 200): List<OutboxEntry> {
        val out = ArrayList<OutboxEntry>()
        readableDatabase.rawQuery(
            "SELECT $COLS FROM outbox ORDER BY CASE state WHEN 'NEEDS_CHECK' THEN 0 WHEN 'UNCERTAIN' THEN 1 WHEN 'SENDING' THEN 2 " +
                "WHEN 'RETRY_WAIT' THEN 3 WHEN 'READY' THEN 4 ELSE 5 END, id DESC LIMIT $limit",
            null,
        ).use { c -> while (c.moveToNext()) out += readEntry(c) }
        return out
    }

    fun counts(): OutboxCounts {
        var r = OutboxCounts()
        readableDatabase.rawQuery("SELECT state, COUNT(*) FROM outbox GROUP BY state", null).use { c ->
            while (c.moveToNext()) {
                val n = c.getInt(1)
                r = when (OutboxState.fromCode(c.getString(0))) {
                    OutboxState.READY -> r.copy(ready = r.ready + n)
                    OutboxState.SENDING -> r.copy(sending = r.sending + n)
                    OutboxState.RETRY_WAIT -> r.copy(retry = r.retry + n)
                    OutboxState.UNCERTAIN -> r.copy(uncertain = r.uncertain + n)
                    OutboxState.NEEDS_CHECK -> r.copy(needsCheck = r.needsCheck + n)
                    else -> r
                }
            }
        }
        return r
    }

    fun pendingCount(): Int = counts().unsent

    /** Shu do'konga hali serverga yozilmagan to'lovlar: (soni, summasi). */
    fun unsentPayments(storeId: String): Pair<Int, Double> =
        readableDatabase.rawQuery(
            "SELECT COUNT(*), COALESCE(SUM(amount), 0) FROM outbox WHERE kind = 'payment' AND store_id = ? AND state NOT IN ('SENT','SENT_MANUAL')",
            arrayOf(storeId),
        ).use { if (it.moveToFirst()) it.getInt(0) to it.getDouble(1) else 0 to 0.0 }

    /** Holat va kvitansiya: outbox_id -> holat. */
    fun statesFor(ids: Collection<Long>): Map<Long, OutboxState> {
        if (ids.isEmpty()) return emptyMap()
        val out = HashMap<Long, OutboxState>()
        for (chunk in ids.chunked(500)) {
            readableDatabase.rawQuery("SELECT id, state FROM outbox WHERE id IN (${chunk.joinToString(",")})", null).use { c ->
                while (c.moveToNext()) out[c.getLong(0)] = OutboxState.fromCode(c.getString(1))
            }
        }
        return out
    }

    private fun readEntry(c: Cursor): OutboxEntry = OutboxEntry(
        id = c.getLong(0), kind = OutboxKind.fromCode(c.getString(1)), agentId = c.getString(2).orEmpty(), payload = c.getString(3).orEmpty(),
        localUuid = c.getString(4).orEmpty(), createdAt = c.getLong(5), state = OutboxState.fromCode(c.getString(6)), attempts = c.getInt(7),
        watermark = if (c.isNull(8)) null else c.getLong(8), serverRowId = c.getString(9), leaseUntil = c.getLong(10), nextAt = c.getLong(11),
        phase = c.getInt(12), prevState = c.getString(13)?.let { OutboxState.fromCode(it) }, lastError = c.getString(14),
        checkReason = c.getString(15), forceSend = c.getInt(16) == 1, storeId = c.getString(17).orEmpty(), storeName = c.getString(18).orEmpty(),
        amount = c.getDouble(19), sentAt = c.getLong(20),
    )

    // ---------------- qarz keshi ----------------

    fun putDebt(storeId: String, som: Double, usd: Double, at: Long) {
        writableDatabase.execSQL("INSERT OR REPLACE INTO debt_cache (store_id, som, usd, fetched_at) VALUES (?, ?, ?, ?)", arrayOf(storeId, som, usd, at))
    }

    fun debt(storeId: String): DebtCache? =
        readableDatabase.rawQuery("SELECT store_id, som, usd, fetched_at FROM debt_cache WHERE store_id = ?", arrayOf(storeId))
            .use { if (it.moveToFirst()) DebtCache(it.getString(0), it.getDouble(1), it.getDouble(2), it.getLong(3)) else null }

    // ---------------- taklif fikri ("Yo'q ekan") ----------------

    fun addFeedback(storeId: String, productId: String, reason: String) {
        writableDatabase.execSQL("INSERT INTO suggestion_feedback (store_id, product_id, reason, ts) VALUES (?, ?, ?, ?)", arrayOf(storeId, productId, reason, System.currentTimeMillis()))
        writableDatabase.execSQL("DELETE FROM suggestion_feedback WHERE ts < ?", arrayOf(System.currentTimeMillis() - 90L * DAY_MS))
    }

    /** product_id -> (sabab, vaqt), shu do'kon uchun eng oxirgisi. */
    fun feedback(storeId: String): Map<String, Pair<String, Long>> {
        val out = HashMap<String, Pair<String, Long>>()
        readableDatabase.rawQuery("SELECT product_id, reason, ts FROM suggestion_feedback WHERE store_id = ? ORDER BY ts", arrayOf(storeId)).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = c.getString(1) to c.getLong(2)
        }
        return out
    }

    // ---------------- KPI (faqat qurilmada) ----------------

    fun addKpi(start: Long, end: Long, taps: Int, items: Int, storeHash: String) {
        writableDatabase.execSQL("INSERT INTO kpi_session (start_ts, end_ts, taps, items, store_hash) VALUES (?, ?, ?, ?, ?)", arrayOf(start, end, taps, items, storeHash))
        writableDatabase.execSQL("DELETE FROM kpi_session WHERE id NOT IN (SELECT id FROM kpi_session ORDER BY id DESC LIMIT 200)")
    }

    fun kpi(limit: Int = 50): List<KpiRow> {
        val out = ArrayList<KpiRow>()
        readableDatabase.rawQuery("SELECT start_ts, end_ts, taps, items FROM kpi_session ORDER BY id DESC LIMIT $limit", null).use { c ->
            while (c.moveToNext()) out += KpiRow(c.getLong(0), c.getLong(1), c.getInt(2), c.getInt(3))
        }
        return out
    }

    // ---------------- jurnal ----------------

    fun orders(limit: Int = KEEP): List<SentOrder> = queryOrders("", null, limit)

    fun ordersBetween(from: Long, to: Long, storeId: String?): List<SentOrder> =
        if (storeId.isNullOrEmpty()) queryOrders("WHERE sent_at >= ? AND sent_at < ?", arrayOf(from.toString(), to.toString()), KEEP)
        else queryOrders("WHERE sent_at >= ? AND sent_at < ? AND store_id = ?", arrayOf(from.toString(), to.toString(), storeId), KEEP)

    /** Shu do'konning buyurtmalari (eng yangisi birinchi). */
    fun ordersForStore(storeId: String, limit: Int = 80): List<SentOrder> =
        queryOrders("WHERE store_id = ?", arrayOf(storeId), limit)

    /** Umumiy oxirgi sotuv buyurtmalari (taklif algoritmi uchun zaxira). */
    fun recentSaleOrders(limit: Int = 150): List<SentOrder> = queryOrders("WHERE mode = '1'", null, limit)

    private fun queryOrders(where: String, args: Array<String>?, limit: Int): List<SentOrder> {
        val out = ArrayList<SentOrder>()
        readableDatabase.rawQuery(
            "SELECT id, sent_at, store_id, store_name, mode, price_type, currency, total, discount, paid, lines, pending, outbox_id " +
                "FROM sent_order $where ORDER BY id DESC LIMIT $limit", args,
        ).use { c ->
            while (c.moveToNext()) {
                out += SentOrder(
                    c.getLong(0), c.getLong(1), c.getString(2).orEmpty(), c.getString(3).orEmpty(), DealMode.fromCode(c.getString(4)),
                    PriceType.fromCode(c.getString(5)), Currency.fromCode(c.getString(6)),
                    c.getDouble(7), c.getDouble(8), c.getDouble(9), parseLines(c.getString(10)), c.getInt(11) == 1, c.getLong(12),
                )
            }
        }
        return out
    }

    fun payments(limit: Int = KEEP): List<SentPayment> = queryPayments("", null, limit)

    fun paymentsBetween(from: Long, to: Long, storeId: String?): List<SentPayment> =
        if (storeId.isNullOrEmpty()) queryPayments("WHERE sent_at >= ? AND sent_at < ?", arrayOf(from.toString(), to.toString()), KEEP)
        else queryPayments("WHERE sent_at >= ? AND sent_at < ? AND store_id = ?", arrayOf(from.toString(), to.toString(), storeId), KEEP)

    private fun queryPayments(where: String, args: Array<String>?, limit: Int): List<SentPayment> {
        val out = ArrayList<SentPayment>()
        readableDatabase.rawQuery(
            "SELECT id, sent_at, store_id, store_name, cash, transfer, click, note, pending, outbox_id FROM sent_payment $where ORDER BY id DESC LIMIT $limit", args,
        ).use { c ->
            while (c.moveToNext()) {
                out += SentPayment(
                    c.getLong(0), c.getLong(1), c.getString(2).orEmpty(), c.getString(3).orEmpty(),
                    c.getDouble(4), c.getDouble(5), c.getDouble(6), c.getString(7).orEmpty(), c.getInt(8) == 1, c.getLong(9),
                )
            }
        }
        return out
    }

    /** Oxirgi ishlangan do'konlar (eng yangisi birinchi). */
    fun recentStoreIds(limit: Int = 6): List<String> {
        val out = LinkedHashSet<String>()
        val sql = "SELECT store_id, sent_at FROM (SELECT store_id, sent_at FROM sent_order UNION ALL SELECT store_id, sent_at FROM sent_payment) " +
            "WHERE store_id <> '' ORDER BY sent_at DESC LIMIT 60"
        readableDatabase.rawQuery(sql, null).use { c ->
            while (c.moveToNext() && out.size < limit) out += c.getString(0).orEmpty()
        }
        return out.toList()
    }

    /** Ishga tushganda yengil tekshiruv. */
    fun quickCheck(): Boolean = runCatching {
        readableDatabase.rawQuery("PRAGMA quick_check", null).use { it.moveToFirst() && it.getString(0).equals("ok", ignoreCase = true) }
    }.getOrDefault(false)

    fun checkpoint() {
        runCatching { writableDatabase.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() } }
    }

    private fun parseLines(json: String?): List<SentLine> {
        val lines = ArrayList<SentLine>()
        runCatching {
            val arr = JSONArray(json.orEmpty())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                lines += SentLine(o.optString("n"), o.optDouble("q"), o.optDouble("p"), o.optString("i"))
            }
        }
        return lines
    }

    companion object {
        const val NAME = "dokon_tarix.sqlite"
        const val VERSION = 3
        private const val KEEP = 500
        private const val DAY_MS = 86_400_000L
        private const val KEEP_SENT_MS = 60L * DAY_MS
        private const val COLS = "id, kind, agent_id, payload, local_uuid, created_at, state, attempts, watermark, server_row_id, lease_until, " +
            "next_at, phase, prev_state, last_error, check_reason, force_send, store_id, store_name, amount, sent_at"
    }
}
