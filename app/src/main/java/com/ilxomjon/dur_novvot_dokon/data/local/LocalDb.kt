package com.ilxomjon.dur_novvot_dokon.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Asosiy lokal baza. Fayl nomi, versiya (1), jadval va ustun nomlari avvalgi ilova bilan BIR XIL,
 * shuning uchun eski ma'lumotlar ustiga o'rnatilganda yo'qolmaydi.
 */
class LocalDb(context: Context) : SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) = ensureSchema(db)

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        ensureSchema(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = ensureSchema(db)

    private fun ensureSchema(db: SQLiteDatabase) {
        for (sql in CREATE) db.execSQL(sql)
        addColumnIfMissing(db, "zakaz", "summa_sum", "VARCHAR DEFAULT 0")
        addColumnIfMissing(db, "zakaz", "turi", "VARCHAR DEFAULT 0")
        addColumnIfMissing(db, "zakaz", "summa_val", "VARCHAR DEFAULT 0")
        addColumnIfMissing(db, "tolov", "tur", "VARCHAR DEFAULT 0")
        addColumnIfMissing(db, "tovar", "kirim_narxi_som", "VARCHAR DEFAULT 0")
        addColumnIfMissing(db, "tovar", "kirim_narxi_val", "VARCHAR DEFAULT 0")
        // v2.3 (faqat qo'shimcha jadval): kutishga qo'yilgan savatchalar. Eski jadvallarga tegilmaydi.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS draft_cart (id INTEGER PRIMARY KEY AUTOINCREMENT, store_id TEXT, store_name TEXT, region_id TEXT, " +
                "mode TEXT, currency TEXT, price_type TEXT, created INTEGER, lines_json TEXT)"
        )
    }

    private fun addColumnIfMissing(db: SQLiteDatabase, table: String, column: String, definition: String) {
        db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            val nameIdx = c.getColumnIndex("name")
            while (c.moveToNext()) if (c.getString(nameIdx) == column) return
        }
        db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
    }

    inline fun <T> transaction(block: (SQLiteDatabase) -> T): T {
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

    companion object {
        const val NAME = "assafocopy.sqlite"
        const val VERSION = 1

        private val CREATE = listOf(
            "CREATE TABLE IF NOT EXISTS agent (Id INTEGER PRIMARY KEY AUTOINCREMENT, agent_id VARCHAR, fio VARCHAR, boshagan VARCHAR, telefon VARCHAR, parol VARCHAR, regionlar VARCHAR, kontrlar VARCHAR, gruppalar VARCHAR,  zakaz_id VARCHAR DEFAULT 0, zakaz_letter VARCHAR DEFAULT 0, tolov VARCHAR DEFAULT 0, otchot VARCHAR DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS kurs (Id INTEGER PRIMARY KEY AUTOINCREMENT, kurs VARCHAR)",
            "CREATE TABLE IF NOT EXISTS region (Id INTEGER PRIMARY KEY AUTOINCREMENT, region_id VARCHAR, nomi VARCHAR)",
            "CREATE TABLE IF NOT EXISTS sozlama (Id INTEGER PRIMARY KEY AUTOINCREMENT, ip VARCHAR, nomi VARCHAR, login VARCHAR, parol VARCHAR)",
            "CREATE TABLE IF NOT EXISTS dokon (Id INTEGER PRIMARY KEY AUTOINCREMENT, region_id VARCHAR, dokon_id VARCHAR, nomi VARCHAR, qarzi_som VARCHAR, qarzi_val VARCHAR, vaqti VARCHAR)",
            "CREATE TABLE IF NOT EXISTS tolov (Id INTEGER PRIMARY KEY AUTOINCREMENT, region_id VARCHAR, dokon_id VARCHAR, summa_sum VARCHAR, summa_val VARCHAR, summa_plastik VARCHAR, vaqti VARCHAR, izox VARCHAR, yuklandi VARCHAR DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS tovar (Id INTEGER PRIMARY KEY AUTOINCREMENT, gruppa_id VARCHAR, tovar_id VARCHAR, nomi VARCHAR, valyuta_turi VARCHAR, sotish_narxi_chakana_som VARCHAR, sotish_narxi_chakana_val VARCHAR, sotish_narxi_optom_som VARCHAR, sotish_narxi_optom_val VARCHAR, blokdagi_soni VARCHAR, qoldiq VARCHAR DEFAULT 0, time VARCHAR)",
            "CREATE TABLE IF NOT EXISTS gruppa (Id INTEGER PRIMARY KEY AUTOINCREMENT, gruppa_id VARCHAR, gruppa_nomi VARCHAR, time VARCHAR, soni VARCHAR)",
            "CREATE TABLE IF NOT EXISTS zakaz (Id INTEGER PRIMARY KEY AUTOINCREMENT, region_id VARCHAR, dokon_id VARCHAR, dok_nomi VARCHAR, tovar_id VARCHAR, nomi VARCHAR, blokdagi_soni VARCHAR, soni VARCHAR, blok_soni VARCHAR, tolov_turi VARCHAR, val_turi VARCHAR,  narxi VARCHAR,  saqlandi VARCHAR DEFAULT 0, vaqti VARCHAR, zakaz_raqam VARCHAR DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS zakaz_jonatishga (Id INTEGER PRIMARY KEY AUTOINCREMENT, zakaz_id VARCHAR, zakaz_raqam VARCHAR, zakaz_txt VARCHAR)",
        )
    }
}
