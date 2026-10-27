package com.ilxomjon.dur_novvot_dokon.data.local

import android.content.ContentValues
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.domain.CartLine
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.Store
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Savatcha: avvalgi ilovadagi "zakaz" jadvali (saqlandi = 0 qatorlar). */
class CartDao(private val db: LocalDb) {

    fun lines(): List<CartLine> {
        val sql = "SELECT Id, tovar_id, nomi, blokdagi_soni, soni, narxi FROM zakaz WHERE saqlandi = 0 ORDER BY Id"
        val out = ArrayList<CartLine>()
        db.readableDatabase.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                out += CartLine(
                    rowId = c.getLong(0),
                    productId = c.getString(1).orEmpty(),
                    name = c.getString(2).orEmpty(),
                    blockSize = Money.parse(c.getString(3)).let { if (it <= 0.0) 1.0 else it },
                    qty = Money.parse(c.getString(4)),
                    price = Money.parse(c.getString(5)),
                )
            }
        }
        return out
    }

    fun upsert(store: Store?, currency: Currency, productId: String, name: String, blockSize: Double, qty: Double, price: Double) {
        val total = qty * price
        val now = SimpleDateFormat("HH:mm:ss dd.MM.yyyy", Locale.US).format(Date())
        val cv = ContentValues().apply {
            put("region_id", store?.regionId.orEmpty()); put("dokon_id", store?.id.orEmpty()); put("dok_nomi", store?.name.orEmpty())
            put("tovar_id", productId); put("nomi", name)
            put("soni", Money.qty(qty)); put("blokdagi_soni", Money.plain(blockSize)); put("narxi", Money.plain(price))
            put("vaqti", now); put("zakaz_raqam", "0")
            put("summa_sum", Money.plain(total)); put("summa_val", "0"); put("turi", currency.code)
            put("saqlandi", "0")
        }
        db.transaction { d ->
            val updated = d.update("zakaz", cv, "tovar_id = ? AND saqlandi = 0", arrayOf(productId))
            if (updated == 0) d.insert("zakaz", null, cv)
        }
    }

    fun remove(productId: String) {
        db.writableDatabase.delete("zakaz", "tovar_id = ? AND saqlandi = 0", arrayOf(productId))
    }

    fun clear() {
        db.transaction { d ->
            d.execSQL("DELETE FROM zakaz")
            d.execSQL("DELETE FROM sqlite_sequence WHERE name='zakaz'")
        }
    }

    fun updateStore(store: Store?) {
        val cv = ContentValues().apply {
            put("region_id", store?.regionId.orEmpty()); put("dokon_id", store?.id.orEmpty()); put("dok_nomi", store?.name.orEmpty())
        }
        db.writableDatabase.update("zakaz", cv, "saqlandi = 0", null)
    }

    fun updateCurrency(currency: Currency) {
        val cv = ContentValues().apply { put("turi", currency.code) }
        db.writableDatabase.update("zakaz", cv, "saqlandi = 0", null)
    }
}
