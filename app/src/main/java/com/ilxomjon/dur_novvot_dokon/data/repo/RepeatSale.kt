package com.ilxomjon.dur_novvot_dokon.data.repo

import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.DealMode
import com.ilxomjon.dur_novvot_dokon.domain.PriceType
import com.ilxomjon.dur_novvot_dokon.domain.Pricing
import com.ilxomjon.dur_novvot_dokon.domain.Product
import com.ilxomjon.dur_novvot_dokon.domain.SentOrder
import com.ilxomjon.dur_novvot_dokon.domain.Store

/**
 * Qilingan savdoni takrorlash: o'sha do'kon, o'sha savdo turi/valyuta/rejim, o'sha miqdorlar — lekin narxlar JORIY katalogdan
 * (biznes qoidalari o'zgarmaydi: narx har doim 1-ilovaning odatdagi hisobi bilan). Yuborilgan buyurtma O'ZGARTIRILMAYDI —
 * bu yangi savatcha.
 */
class RepeatSale(
    private val catalog: CatalogRepository,
    private val draft: DraftRepository,
    private val parked: ParkedCartRepository,
) {
    enum class Mode {
        /** Joriy savatcha o'rniga. */
        REPLACE,
        /** Joriy savatchaga qo'shish (miqdorlar qo'shiladi, joriy savdo turi/valyutasida). */
        ADD,
        /** Joriy savatchani kutishga qo'yib, yangisini ochish. */
        PARK,
    }

    class Result(val added: Int, val missing: Int, val priceChanged: Int)

    suspend fun repeat(o: SentOrder, mode: Mode): Result {
        val c = catalog.state.value
        if (mode == Mode.PARK && draft.state.value.lines.isNotEmpty()) parked.parkActive()
        val d = draft.state.value
        val keepCurrent = mode == Mode.ADD && d.lines.isNotEmpty()
        val type = if (keepCurrent) d.priceType else o.priceType
        val currency = if (keepCurrent) d.currency else o.currency
        // Bir xil tovar bir necha qatorda bo'lsa — birlashtiriladi.
        val qtyById = LinkedHashMap<String, Double>()
        val oldPrice = HashMap<String, Double>()
        var missing = 0
        for (l in o.lines) {
            if (l.productId.isEmpty() || c.productsById[l.productId] == null) { missing++; continue }
            qtyById[l.productId] = (qtyById[l.productId] ?: 0.0) + l.qty
            oldPrice[l.productId] = l.price
        }
        val items = ArrayList<Triple<Product, Double, Double>>()
        var changed = 0
        for ((id, q) in qtyById) {
            val p = c.productsById.getValue(id)
            val price = if (keepCurrent) Pricing.unitPrice(p, d.level, currency, c.rate) else Pricing.unitPrice(p, type, currency, c.rate)
            if (type == o.priceType && currency == o.currency && Math.abs(price - (oldPrice[id] ?: price)) > 0.004) changed++
            val existing = if (mode == Mode.ADD) draft.state.value.lineFor(id) else null
            items += Triple(p, (existing?.qty ?: 0.0) + q, existing?.price ?: price)
        }
        if (items.isEmpty()) return Result(0, missing, 0)
        if (keepCurrent) {
            draft.addLines(items)
        } else {
            val store = catalog.storeById(o.storeId) ?: Store(o.storeId, "", o.storeName, "", 0.0, 0.0)
            draft.activate(store, o.mode, currency, type, emptyList())
            draft.addLines(items)
        }
        return Result(items.size, missing, changed)
    }

    class Item(val productId: String, val qty: Double)

    /**
     * 1C tarixidan yoki 2-ilova buyurtmasidan takrorlash (v2.5): tovarlar katalog id si bilan, narx JORIY katalogdan
     * (odatdagi [Pricing] qoidasi). [mode] — savatcha bo'sh bo'lmasa nima qilish.
     */
    suspend fun repeatItems(storeId: String, storeName: String, deal: DealMode, type: PriceType, currency: Currency, list: List<Item>, mode: Mode): Result {
        val c = catalog.state.value
        if (mode == Mode.PARK && draft.state.value.lines.isNotEmpty()) parked.parkActive()
        val d = draft.state.value
        val keepCurrent = mode == Mode.ADD && d.lines.isNotEmpty()
        val t = if (keepCurrent) d.priceType else type
        val cur = if (keepCurrent) d.currency else currency
        val qtyById = LinkedHashMap<String, Double>()
        var missing = 0
        for (it in list) {
            if (it.qty <= 0.0 || c.productsById[it.productId] == null) { missing++; continue }
            qtyById[it.productId] = (qtyById[it.productId] ?: 0.0) + it.qty
        }
        val items = ArrayList<Triple<Product, Double, Double>>()
        for ((id, q) in qtyById) {
            val p = c.productsById.getValue(id)
            val existing = if (keepCurrent) draft.state.value.lineFor(id) else null
            items += Triple(p, (existing?.qty ?: 0.0) + q, existing?.price ?: (if (keepCurrent) Pricing.unitPrice(p, d.level, cur, c.rate) else Pricing.unitPrice(p, t, cur, c.rate)))
        }
        if (items.isEmpty()) return Result(0, missing, 0)
        if (!keepCurrent) {
            val store = catalog.storeById(storeId) ?: Store(storeId, "", storeName, "", 0.0, 0.0)
            draft.activate(store, deal, cur, t, emptyList())
        }
        draft.addLines(items)
        return Result(items.size, missing, 0)
    }
}
