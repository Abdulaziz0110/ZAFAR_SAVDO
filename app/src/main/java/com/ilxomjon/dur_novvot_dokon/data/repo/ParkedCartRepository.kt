package com.ilxomjon.dur_novvot_dokon.data.repo

import android.content.ContentValues
import com.ilxomjon.dur_novvot_dokon.data.local.LocalDb
import com.ilxomjon.dur_novvot_dokon.domain.CartLine
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.DealMode
import com.ilxomjon.dur_novvot_dokon.domain.PriceType
import com.ilxomjon.dur_novvot_dokon.domain.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ParkedCart(
    val id: Long,
    val storeId: String,
    val storeName: String,
    val regionId: String,
    val mode: DealMode,
    val currency: Currency,
    val priceType: PriceType,
    val created: Long,
    val lines: List<CartLine>,
    /** Shu savdo oynasining narxi (1..4). 0 — eski yozuv (narx [priceType] dan: 3 yoki 4). */
    val priceLevel: Int = 0,
) {
    val total: Double get() = lines.sumOf { it.total }
}

/**
 * Ko'p savatcha: faol savatcha ("zakaz" jadvali, O'ZGARMAYDI) + kutishga qo'yilganlar (yangi "draft_cart" jadvali).
 * Kutishdagi savatcha hech qachon "yuborilgan" hisoblanmaydi; yuborish faqat faol savatchadan, odatdagi navbat orqali.
 * Har bir kutishdagi savdo O'Z narxi (1..4) bilan saqlanadi va aynan shu narx bilan qaytadi.
 */
class ParkedCartRepository(
    private val db: LocalDb,
    private val draft: DraftRepository,
    private val catalog: CatalogRepository,
) {
    private val _items = MutableStateFlow<List<ParkedCart>>(emptyList())
    val items: StateFlow<List<ParkedCart>> = _items.asStateFlow()

    suspend fun refresh() {
        _items.value = withContext(Dispatchers.IO) { readAll() }
    }

    /** Faol savatchani kutishga qo'yadi (bitta tranzaksiyada: qoralama yoziladi va "zakaz" bo'shatiladi). */
    suspend fun parkActive(): Boolean {
        val d = draft.state.value
        if (d.lines.isEmpty()) return false
        draft.flush()
        withContext(Dispatchers.IO) {
            db.transaction { w ->
                w.insertOrThrow("draft_cart", null, ContentValues().apply {
                    put("store_id", d.store?.id.orEmpty()); put("store_name", d.store?.name.orEmpty()); put("region_id", d.store?.regionId.orEmpty())
                    put("mode", d.mode.code); put("currency", d.currency.code); put("price_type", d.priceType.code)
                    put("price_level", d.level)
                    put("created", System.currentTimeMillis()); put("lines_json", toJson(d.lines))
                })
                w.execSQL("DELETE FROM zakaz")
            }
        }
        draft.finishNow()
        refresh()
        return true
    }

    /** Kutishdagi savatchani faol qiladi. Faol savatcha bo'sh bo'lishi SHART (aks holda false). @return katalogda yo'q tovarlar soni */
    suspend fun resume(id: Long): Int? {
        if (draft.state.value.lines.isNotEmpty()) return null
        val p = withContext(Dispatchers.IO) { readAll().firstOrNull { it.id == id } } ?: return null
        val store = catalog.storeById(p.storeId) ?: Store(p.storeId, p.regionId, p.storeName, "", 0.0, 0.0)
        draft.activate(store, p.mode, p.currency, p.priceType, p.lines, p.priceLevel)
        // Avval faol savatcha bazaga yozilsin, keyin qoralama o'chirilsin: o'rtada o'lsa ham tovar yo'qolmaydi (ikki nusxa qoladi).
        draft.flush()
        withContext(Dispatchers.IO) { db.writableDatabase.delete("draft_cart", "id = ?", arrayOf(id.toString())) }
        refresh()
        val known = catalog.state.value.productsById
        return p.lines.count { it.productId !in known }
    }

    /** O'chiradi va "Bekor qilish" uchun nusxasini qaytaradi. */
    suspend fun delete(id: Long): ParkedCart? {
        val p = withContext(Dispatchers.IO) { readAll().firstOrNull { it.id == id } } ?: return null
        withContext(Dispatchers.IO) { db.writableDatabase.delete("draft_cart", "id = ?", arrayOf(id.toString())) }
        refresh()
        return p
    }

    suspend fun undoDelete(p: ParkedCart) {
        withContext(Dispatchers.IO) {
            db.writableDatabase.insert("draft_cart", null, ContentValues().apply {
                put("store_id", p.storeId); put("store_name", p.storeName); put("region_id", p.regionId)
                put("mode", p.mode.code); put("currency", p.currency.code); put("price_type", p.priceType.code)
                put("price_level", p.priceLevel)
                put("created", p.created); put("lines_json", toJson(p.lines))
            })
        }
        refresh()
    }

    private fun readAll(): List<ParkedCart> {
        val out = ArrayList<ParkedCart>()
        db.readableDatabase.rawQuery(
            "SELECT id, store_id, store_name, region_id, mode, currency, price_type, created, lines_json, price_level FROM draft_cart ORDER BY id", null,
        ).use { c ->
            while (c.moveToNext()) {
                out += ParkedCart(
                    c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3).orEmpty(),
                    DealMode.fromCode(c.getString(4)), Currency.fromCode(c.getString(5)), PriceType.fromCode(c.getString(6)),
                    c.getLong(7), fromJson(c.getString(8)), c.getInt(9),
                )
            }
        }
        return out
    }

    private fun toJson(lines: List<CartLine>): String {
        val a = JSONArray()
        for (l in lines) a.put(JSONObject().put("i", l.productId).put("n", l.name).put("b", l.blockSize).put("q", l.qty).put("p", l.price))
        return a.toString()
    }

    private fun fromJson(s: String?): List<CartLine> = runCatching {
        val a = JSONArray(s.orEmpty())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            CartLine(0, o.optString("i"), o.optString("n"), o.optDouble("b", 1.0), o.optDouble("q"), o.optDouble("p"))
        }
    }.getOrDefault(emptyList())
}
