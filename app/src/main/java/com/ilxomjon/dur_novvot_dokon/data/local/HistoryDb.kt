package com.ilxomjon.dur_novvot_dokon.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.DealMode
import com.ilxomjon.dur_novvot_dokon.domain.PriceType
import com.ilxomjon.dur_novvot_dokon.domain.SentLine
import com.ilxomjon.dur_novvot_dokon.domain.SentOrder
import com.ilxomjon.dur_novvot_dokon.domain.SentPayment
import org.json.JSONArray
import org.json.JSONObject

class OutboxItem(val id: Long, val kind: String, val agentId: String, val payload: String, val attempts: Int)

/**
 * Yuborilgan buyurtma/to'lovlar jurnali va serverga yuborilishi kutilayotganlar navbati (outbox).
 * Alohida fayl: asosiy bazaning sxemasi o'zgarmaydi.
 */
class HistoryDb(context: Context) : SQLiteOpenHelper(context.applicationContext, NAME, null, 2) {

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
        createOutbox(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            for (t in listOf("sent_order", "sent_payment")) {
                runCatching { db.execSQL("ALTER TABLE $t ADD COLUMN store_id TEXT DEFAULT ''") }
                runCatching { db.execSQL("ALTER TABLE $t ADD COLUMN pending INTEGER DEFAULT 0") }
                runCatching { db.execSQL("ALTER TABLE $t ADD COLUMN outbox_id INTEGER DEFAULT 0") }
            }
            createOutbox(db)
        }
    }

    private fun createOutbox(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS outbox (id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, agent_id TEXT, " +
                "payload TEXT NOT NULL, created_at INTEGER, attempts INTEGER DEFAULT 0, last_error TEXT)"
        )
    }

    // ---------------- navbat ----------------

    /** Buyurtmani navbatga qo'yadi va jurnalga "kutilmoqda" sifatida yozadi (bitta tranzaksiyada). */
    fun enqueueOrder(agentId: String, payload: String, o: SentOrder) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val outboxId = insertOutbox(db, "order", agentId, payload)
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
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun enqueuePayment(agentId: String, payload: String, p: SentPayment) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val outboxId = insertOutbox(db, "payment", agentId, payload)
            val cv = ContentValues().apply {
                put("sent_at", p.sentAt); put("store_id", p.storeId); put("store_name", p.storeName)
                put("cash", p.cash); put("transfer", p.transfer); put("click", p.click); put("note", p.note)
                put("pending", 1); put("outbox_id", outboxId)
            }
            db.insert("sent_payment", null, cv)
            trim(db, "sent_payment")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun insertOutbox(db: SQLiteDatabase, kind: String, agentId: String, payload: String): Long {
        val cv = ContentValues().apply {
            put("kind", kind); put("agent_id", agentId); put("payload", payload); put("created_at", System.currentTimeMillis())
        }
        return db.insert("outbox", null, cv)
    }

    private fun trim(db: SQLiteDatabase, table: String) {
        db.execSQL("DELETE FROM $table WHERE pending = 0 AND id NOT IN (SELECT id FROM $table ORDER BY id DESC LIMIT $KEEP)")
    }

    fun nextPending(): OutboxItem? {
        readableDatabase.rawQuery("SELECT id, kind, agent_id, payload, attempts FROM outbox ORDER BY id LIMIT 1", null).use { c ->
            if (!c.moveToFirst()) return null
            return OutboxItem(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3).orEmpty(), c.getInt(4))
        }
    }

    fun pendingCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM outbox", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun markAttempt(id: Long, error: String?) {
        writableDatabase.execSQL("UPDATE outbox SET attempts = attempts + 1, last_error = ? WHERE id = ?", arrayOf(error ?: "", id))
    }

    /** Muvaffaqiyatli yuborildi: navbatdan olib tashlanadi, jurnalda "kutilmoqda" belgisi o'chadi. */
    fun complete(id: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM outbox WHERE id = ?", arrayOf(id))
            db.execSQL("UPDATE sent_order SET pending = 0 WHERE outbox_id = ?", arrayOf(id))
            db.execSQL("UPDATE sent_payment SET pending = 0 WHERE outbox_id = ?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---------------- jurnal ----------------

    fun orders(limit: Int = KEEP): List<SentOrder> = queryOrders("", null, limit)

    /** Shu do'konning buyurtmalari (eng yangisi birinchi). */
    fun ordersForStore(storeId: String, limit: Int = 80): List<SentOrder> =
        queryOrders("WHERE store_id = ?", arrayOf(storeId), limit)

    /** Umumiy oxirgi sotuv buyurtmalari (taklif algoritmi uchun zaxira). */
    fun recentSaleOrders(limit: Int = 150): List<SentOrder> = queryOrders("WHERE mode = '1'", null, limit)

    private fun queryOrders(where: String, args: Array<String>?, limit: Int): List<SentOrder> {
        val out = ArrayList<SentOrder>()
        readableDatabase.rawQuery(
            "SELECT id, sent_at, store_id, store_name, mode, price_type, currency, total, discount, paid, lines, pending " +
                "FROM sent_order $where ORDER BY id DESC LIMIT $limit", args,
        ).use { c ->
            while (c.moveToNext()) {
                out += SentOrder(
                    c.getLong(0), c.getLong(1), c.getString(2).orEmpty(), c.getString(3).orEmpty(), DealMode.fromCode(c.getString(4)),
                    PriceType.fromCode(c.getString(5)), Currency.fromCode(c.getString(6)),
                    c.getDouble(7), c.getDouble(8), c.getDouble(9), parseLines(c.getString(10)), c.getInt(11) == 1,
                )
            }
        }
        return out
    }

    fun payments(limit: Int = KEEP): List<SentPayment> {
        val out = ArrayList<SentPayment>()
        readableDatabase.rawQuery(
            "SELECT id, sent_at, store_id, store_name, cash, transfer, click, note, pending FROM sent_payment ORDER BY id DESC LIMIT $limit", null,
        ).use { c ->
            while (c.moveToNext()) {
                out += SentPayment(
                    c.getLong(0), c.getLong(1), c.getString(2).orEmpty(), c.getString(3).orEmpty(),
                    c.getDouble(4), c.getDouble(5), c.getDouble(6), c.getString(7).orEmpty(), c.getInt(8) == 1,
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
        private const val KEEP = 500
    }
}
