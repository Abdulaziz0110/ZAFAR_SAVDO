package com.ilxomjon.dur_novvot_dokon.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import uz.imsoft.shared.Json
import uz.imsoft.shared.report.Coverage
import uz.imsoft.shared.report.LedgerEntry
import uz.imsoft.shared.report.SnapshotCodec

/**
 * v5.0: 1C tarixi telefon bazasida (avval JSON fayllarda edi). Ko'rsatishdan oldin yozib qo'yiladi —
 * ekranda ko'ringan narsa baribir bazada bor. Har mijozning kitobi butunligicha almashtiriladi (birlashtirish
 * xotirada, LedgerMerge bilan).
 */
class LedgerDb(context: Context) : SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    class MetaRow(
        val storeId: String, val name: String, val back: Int, val ver: Int, val okAt: Long,
        val tryAt: Long, val err: String, val due: Long, val lastEntry: Long,
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE ledger (store_id TEXT NOT NULL, pos INTEGER NOT NULL, stamp INTEGER NOT NULL, row TEXT NOT NULL, PRIMARY KEY(store_id, pos))")
        db.execSQL("CREATE INDEX ledger_stamp ON ledger(stamp)")
        db.execSQL("CREATE TABLE cov (store_id TEXT NOT NULL, f INTEGER NOT NULL, t INTEGER NOT NULL, at INTEGER NOT NULL, src TEXT NOT NULL)")
        db.execSQL("CREATE INDEX cov_store ON cov(store_id)")
        db.execSQL(
            "CREATE TABLE meta (store_id TEXT PRIMARY KEY, name TEXT NOT NULL, back INTEGER NOT NULL, ver INTEGER NOT NULL, " +
                "ok_at INTEGER NOT NULL, try_at INTEGER NOT NULL, err TEXT NOT NULL, due INTEGER NOT NULL, last_entry INTEGER NOT NULL)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = reset(db)

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = reset(db)

    private fun reset(db: SQLiteDatabase) {
        for (t in listOf("ledger", "cov", "meta")) db.execSQL("DROP TABLE IF EXISTS $t")
        onCreate(db)
    }

    // ---------------- meta ----------------

    fun metas(): List<MetaRow> {
        val out = ArrayList<MetaRow>()
        readableDatabase.rawQuery("SELECT store_id, name, back, ver, ok_at, try_at, err, due, last_entry FROM meta", null).use { c ->
            while (c.moveToNext()) {
                out += MetaRow(c.getString(0), c.getString(1), c.getInt(2), c.getInt(3), c.getLong(4), c.getLong(5), c.getString(6), c.getLong(7), c.getLong(8))
            }
        }
        return out
    }

    fun saveMetas(list: List<MetaRow>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("meta", null, null)
            for (m in list) {
                db.insertWithOnConflict(
                    "meta", null,
                    ContentValues().apply {
                        put("store_id", m.storeId); put("name", m.name); put("back", m.back); put("ver", m.ver)
                        put("ok_at", m.okAt); put("try_at", m.tryAt); put("err", m.err); put("due", m.due); put("last_entry", m.lastEntry)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---------------- kitoblar ----------------

    fun book(storeId: String): Pair<List<LedgerEntry>, List<Coverage>> {
        val entries = ArrayList<LedgerEntry>()
        readableDatabase.rawQuery("SELECT row FROM ledger WHERE store_id = ? ORDER BY pos", arrayOf(storeId)).use { c ->
            while (c.moveToNext()) parse(c.getString(0))?.let { entries += it }
        }
        val cov = ArrayList<Coverage>()
        readableDatabase.rawQuery("SELECT f, t, at, src FROM cov WHERE store_id = ?", arrayOf(storeId)).use { c ->
            while (c.moveToNext()) cov += Coverage(c.getLong(0), c.getLong(1), c.getLong(2), c.getString(3))
        }
        return entries to cov
    }

    /** Shu mijozning butun kitobi almashtiriladi (bitta tranzaksiyada). */
    fun saveBook(storeId: String, entries: List<LedgerEntry>, coverage: List<Coverage>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("ledger", "store_id = ?", arrayOf(storeId))
            db.delete("cov", "store_id = ?", arrayOf(storeId))
            val st = db.compileStatement("INSERT INTO ledger (store_id, pos, stamp, row) VALUES (?, ?, ?, ?)")
            entries.forEachIndexed { i, e ->
                st.clearBindings()
                st.bindString(1, storeId)
                st.bindLong(2, i.toLong())
                st.bindLong(3, e.stamp)
                st.bindString(4, Json.write(SnapshotCodec.entryToList(e)))
                st.executeInsert()
            }
            for (c in coverage) {
                db.insert(
                    "cov", null,
                    ContentValues().apply { put("store_id", storeId); put("f", c.from); put("t", c.to); put("at", c.importedAt); put("src", c.source) },
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Hamma mijozlarning yozuvlari (do'kon id bilan). [fromStamp] — faqat shu vaqtdan keyingilari. */
    fun allEntries(fromStamp: Long = 0L): List<Pair<String, LedgerEntry>> {
        val out = ArrayList<Pair<String, LedgerEntry>>()
        val sql = if (fromStamp > 0) "SELECT store_id, row FROM ledger WHERE stamp >= ? ORDER BY stamp" else "SELECT store_id, row FROM ledger ORDER BY stamp"
        val args = if (fromStamp > 0) arrayOf(fromStamp.toString()) else null
        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) parse(c.getString(1))?.let { out += c.getString(0) to it }
        }
        return out
    }

    fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM ledger", null).use { if (it.moveToNext()) it.getInt(0) else 0 }

    fun clear() {
        val db = writableDatabase
        for (t in listOf("ledger", "cov", "meta")) db.delete(t, null, null)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parse(row: String): LedgerEntry? = runCatching {
        SnapshotCodec.entryFromList(Json.parse(row) as List<Any?>)
    }.getOrNull()

    companion object {
        const val NAME = "ledger.db"
        const val VERSION = 1
    }
}
