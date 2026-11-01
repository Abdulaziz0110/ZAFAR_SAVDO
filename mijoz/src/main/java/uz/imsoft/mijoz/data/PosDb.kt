package uz.imsoft.mijoz.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import uz.imsoft.shared.Json
import uz.imsoft.shared.pos.Pos
import uz.imsoft.shared.pos.PosCustomer
import uz.imsoft.shared.pos.PosLine
import uz.imsoft.shared.pos.PosPayment
import uz.imsoft.shared.pos.PosSale

data class PosData(
    val customers: List<PosCustomer> = emptyList(),
    val sales: List<PosSale> = emptyList(),
    val payments: List<PosPayment> = emptyList(),
    /** O'z sotish narxlari: tovar nomi -> narx (so'm). */
    val prices: Map<String, Double> = emptyMap(),
    val loaded: Boolean = false,
) {
    fun balance(customerId: Long): Double = Pos.balance(customerId, sales, payments)
    fun customer(id: Long): PosCustomer? = customers.firstOrNull { it.id == id }
}

/**
 * v3.2 "Kassa" bazasi (faqat shu telefonda). Kunlik zaxiraga kiradi. Jadvallar qo'shimcha — eski ma'lumotga tegilmaydi.
 */
class PosDb(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {
    private val _state = MutableStateFlow(PosData())
    val state: StateFlow<PosData> = _state.asStateFlow()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE customers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT NOT NULL DEFAULT '', created INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE sales (id INTEGER PRIMARY KEY AUTOINCREMENT, stamp INTEGER NOT NULL, customer INTEGER NOT NULL DEFAULT 0, lines TEXT NOT NULL, discount REAL NOT NULL DEFAULT 0, paid REAL NOT NULL DEFAULT 0, note TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE payments (id INTEGER PRIMARY KEY AUTOINCREMENT, stamp INTEGER NOT NULL, customer INTEGER NOT NULL, amount REAL NOT NULL, note TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE prices (name TEXT PRIMARY KEY, price REAL NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun checkpoint() {
        runCatching { writableDatabase.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() } }
    }

    suspend fun load() = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val customers = ArrayList<PosCustomer>()
        db.rawQuery("SELECT id, name, phone, created FROM customers ORDER BY name COLLATE NOCASE", null).use { c ->
            while (c.moveToNext()) customers += PosCustomer(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3))
        }
        val sales = ArrayList<PosSale>()
        db.rawQuery("SELECT id, stamp, customer, lines, discount, paid, note FROM sales ORDER BY stamp DESC, id DESC", null).use { c ->
            while (c.moveToNext()) sales += PosSale(c.getLong(0), c.getLong(1), c.getLong(2), linesOf(c.getString(3)), c.getDouble(4), c.getDouble(5), c.getString(6))
        }
        val pays = ArrayList<PosPayment>()
        db.rawQuery("SELECT id, stamp, customer, amount, note FROM payments ORDER BY stamp DESC, id DESC", null).use { c ->
            while (c.moveToNext()) pays += PosPayment(c.getLong(0), c.getLong(1), c.getLong(2), c.getDouble(3), c.getString(4))
        }
        val prices = HashMap<String, Double>()
        db.rawQuery("SELECT name, price FROM prices", null).use { c -> while (c.moveToNext()) prices[c.getString(0)] = c.getDouble(1) }
        _state.value = PosData(customers, sales, pays, prices, true)
    }

    suspend fun addCustomer(name: String, phone: String): Long {
        val id = withContext(Dispatchers.IO) {
            writableDatabase.insertOrThrow("customers", null, ContentValues().apply {
                put("name", name.trim().take(120)); put("phone", phone.trim().take(30)); put("created", System.currentTimeMillis())
            })
        }
        load()
        return id
    }

    suspend fun updateCustomer(id: Long, name: String, phone: String) {
        withContext(Dispatchers.IO) {
            writableDatabase.update("customers", ContentValues().apply { put("name", name.trim().take(120)); put("phone", phone.trim().take(30)) }, "id = ?", arrayOf(id.toString()))
        }
        load()
    }

    suspend fun saveSale(stamp: Long, customer: Long, lines: List<PosLine>, discount: Double, paid: Double, note: String): Long {
        require(lines.isNotEmpty())
        val id = withContext(Dispatchers.IO) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                val id = db.insertOrThrow("sales", null, ContentValues().apply {
                    put("stamp", stamp); put("customer", customer); put("lines", linesJson(lines))
                    put("discount", discount); put("paid", paid); put("note", note.take(200))
                })
                // Narx eslab qolinadi (keyingi savdoda tayyor)
                for (l in lines) db.insertWithOnConflict("prices", null, ContentValues().apply { put("name", l.name); put("price", l.price) }, SQLiteDatabase.CONFLICT_REPLACE)
                db.setTransactionSuccessful()
                id
            } finally {
                db.endTransaction()
            }
        }
        load()
        return id
    }

    /** Xato kiritilgan savdoni o'chirish (faqat egasi tasdiqlagandan keyin chaqiriladi). */
    suspend fun deleteSale(id: Long) {
        withContext(Dispatchers.IO) { writableDatabase.delete("sales", "id = ?", arrayOf(id.toString())) }
        load()
    }

    suspend fun addPayment(stamp: Long, customer: Long, amount: Double, note: String): Long {
        require(amount > 0)
        val id = withContext(Dispatchers.IO) {
            writableDatabase.insertOrThrow("payments", null, ContentValues().apply {
                put("stamp", stamp); put("customer", customer); put("amount", amount); put("note", note.take(200))
            })
        }
        load()
        return id
    }

    suspend fun deletePayment(id: Long) {
        withContext(Dispatchers.IO) { writableDatabase.delete("payments", "id = ?", arrayOf(id.toString())) }
        load()
    }

    suspend fun setPrice(name: String, price: Double) {
        withContext(Dispatchers.IO) {
            writableDatabase.insertWithOnConflict("prices", null, ContentValues().apply { put("name", name); put("price", price) }, SQLiteDatabase.CONFLICT_REPLACE)
        }
        load()
    }

    companion object {
        const val NAME = "kassa.db"
        const val VERSION = 1

        fun linesJson(lines: List<PosLine>): String = Json.write(lines.map { listOf(it.name, it.code, it.qty, it.price) })

        @Suppress("UNCHECKED_CAST")
        fun linesOf(s: String): List<PosLine> = runCatching {
            (Json.parse(s) as? List<Any?>).orEmpty().mapNotNull { x ->
                val l = x as? List<Any?> ?: return@mapNotNull null
                PosLine(l.getOrNull(0)?.toString().orEmpty(), l.getOrNull(1)?.toString().orEmpty(), (l.getOrNull(2) as? Number)?.toDouble() ?: 0.0, (l.getOrNull(3) as? Number)?.toDouble() ?: 0.0)
            }
        }.getOrDefault(emptyList())
    }
}
