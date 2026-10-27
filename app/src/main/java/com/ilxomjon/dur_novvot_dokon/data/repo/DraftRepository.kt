package com.ilxomjon.dur_novvot_dokon.data.repo

import android.content.SharedPreferences
import com.ilxomjon.dur_novvot_dokon.data.local.CartDao
import com.ilxomjon.dur_novvot_dokon.domain.CartLine
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.DealMode
import com.ilxomjon.dur_novvot_dokon.domain.DraftState
import com.ilxomjon.dur_novvot_dokon.domain.PriceType
import com.ilxomjon.dur_novvot_dokon.domain.Pricing
import com.ilxomjon.dur_novvot_dokon.domain.Product
import com.ilxomjon.dur_novvot_dokon.domain.Store
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Joriy savdo. Holat XOTIRADA yangilanadi (bosish zumda ko'rinadi), bazaga yozish esa orqada, tartib bilan bajariladi.
 * Saqlash joyi avvalgi ilova bilan bir xil: "user" sozlamalari va "zakaz" jadvali (tugallanmagan buyurtma yo'qolmaydi).
 */
class DraftRepository(
    private val cart: CartDao,
    private val catalog: CatalogRepository,
    private val prefs: SharedPreferences,
    scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(DraftState())
    val state: StateFlow<DraftState> = _state.asStateFlow()

    private val writes = Channel<() -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch(Dispatchers.IO) {
            for (w in writes) runCatching { w() }
        }
    }

    private fun persist(block: () -> Unit) { writes.trySend(block) }

    /** Orqadagi barcha yozuvlar tugaguncha kutadi (zaxira/tiklashdan oldin). */
    suspend fun flush() {
        val done = CompletableDeferred<Unit>()
        writes.send { done.complete(Unit) }
        done.await()
    }

    suspend fun load() {
        flush()
        val loaded = withContext(Dispatchers.IO) {
            DraftState(
                store = readStore(),
                priceType = PriceType.fromCode(prefs.getString(K_PRICE_TYPE, PriceType.WHOLESALE.code)),
                currency = Currency.fromCode(prefs.getString(K_CURRENCY, Currency.SOM.code)),
                mode = DealMode.fromCode(prefs.getString(K_MODE, DealMode.SALE.code)),
                lines = cart.lines(),
            )
        }
        _state.value = loaded
    }

    private fun readStore(): Store? {
        val id = prefs.getString(K_STORE_ID, "").orEmpty()
        if (id.isEmpty()) return null
        return catalog.storeById(id) ?: Store(
            id = id,
            regionId = prefs.getString(K_REGION_ID, "").orEmpty(),
            name = prefs.getString(K_STORE_NAME, "").orEmpty(),
            regionName = "", debtSom = 0.0, debtUsd = 0.0,
        )
    }

    /** Holatni sinxron yangilaydi, natijani bazaga yozishni navbatga qo'yadi. */
    private inline fun mutate(crossinline change: (DraftState) -> DraftState, crossinline write: (DraftState) -> Unit) {
        var result = _state.value
        _state.update { cur -> change(cur).also { result = it } }
        val snapshot = result
        persist { write(snapshot) }
    }

    private fun lineWrite(s: DraftState, productId: String) {
        val l = s.lineFor(productId)
        if (l == null) cart.remove(productId)
        else cart.upsert(s.store, s.currency, l.productId, l.name, l.blockSize, l.qty, l.price)
    }

    private fun allLinesWrite(s: DraftState) {
        for (l in s.lines) cart.upsert(s.store, s.currency, l.productId, l.name, l.blockSize, l.qty, l.price)
    }

    fun setStore(store: Store?) = mutate({ it.copy(store = store) }) { s ->
        prefs.edit().putString(K_STORE_ID, store?.id.orEmpty()).putString(K_REGION_ID, store?.regionId.orEmpty())
            .putString(K_STORE_NAME, store?.name.orEmpty()).apply()
        cart.updateStore(s.store)
    }

    fun setPriceType(type: PriceType) = mutate({ reprice(it.copy(priceType = type)) }) { s ->
        prefs.edit().putString(K_PRICE_TYPE, type.code).apply()
        allLinesWrite(s)
    }

    fun setCurrency(currency: Currency) = mutate({ reprice(it.copy(currency = currency)) }) { s ->
        prefs.edit().putString(K_CURRENCY, currency.code).apply()
        allLinesWrite(s)
    }

    fun setMode(mode: DealMode) = mutate({ it.copy(mode = mode) }) { _ ->
        prefs.edit().putString(K_MODE, mode.code).apply()
    }

    /** Miqdorni zumda o'zgartiradi; 0 ga tushsa qator o'chadi. Qoldiq tekshirilmaydi. */
    fun adjust(product: Product, delta: Double, defaultPrice: Double) = mutate({ s ->
        val existing = s.lineFor(product.id)
        val qty = round3((existing?.qty ?: 0.0) + delta)
        s.copy(
            lines = when {
                qty <= 0.0 -> s.lines.filter { it.productId != product.id }
                existing == null -> s.lines + CartLine(0, product.id, product.name, product.blockSize, qty, defaultPrice)
                else -> s.lines.map { if (it.productId == product.id) it.copy(qty = qty) else it }
            },
        )
    }) { s -> lineWrite(s, product.id) }

    /** Qatorni to'liq belgilaydi (miqdor va narx). Narx hamma savdo turida erkin o'zgartiriladi. */
    fun setLine(product: Product, qty: Double, price: Double) = mutate({ s ->
        val q = round3(qty)
        val existing = s.lineFor(product.id)
        s.copy(
            lines = when {
                q <= 0.0 -> s.lines.filter { it.productId != product.id }
                existing == null -> s.lines + CartLine(0, product.id, product.name, product.blockSize, q, price)
                else -> s.lines.map { if (it.productId == product.id) it.copy(qty = q, price = price) else it }
            },
        )
    }) { s -> lineWrite(s, product.id) }

    /** Bir nechta qatorni birdan qo'shadi (oxirgi buyurtmani takrorlash). */
    fun addLines(items: List<Triple<Product, Double, Double>>) = mutate({ s ->
        var lines = s.lines
        for ((p, qty, price) in items) {
            lines = if (lines.any { it.productId == p.id }) lines.map { if (it.productId == p.id) it.copy(qty = round3(qty), price = price) else it }
            else lines + CartLine(0, p.id, p.name, p.blockSize, round3(qty), price)
        }
        s.copy(lines = lines)
    }) { s -> allLinesWrite(s) }

    fun removeLine(productId: String) = mutate({ s -> s.copy(lines = s.lines.filter { it.productId != productId }) }) { s ->
        lineWrite(s, productId)
    }

    /** O'chirilgan qatorni qaytaradi ("Qaytarish" tugmasi uchun). */
    fun restore(line: CartLine) = mutate({ s ->
        if (s.lineFor(line.productId) != null) s else s.copy(lines = s.lines + line)
    }) { s -> lineWrite(s, line.productId) }

    /** Savatchani tozalaydi (do'kon tanlovi saqlanadi). */
    fun clearLines() = mutate({ it.copy(lines = emptyList()) }) { _ -> cart.clear() }

    /** Savatcha va do'kon tanlovini tozalaydi (buyurtma navbatga qo'yilgandan keyin). */
    fun finish() = mutate({ it.copy(lines = emptyList(), store = null) }) { _ ->
        cart.clear()
        prefs.edit().putString(K_STORE_ID, "").putString(K_REGION_ID, "").putString(K_STORE_NAME, "").apply()
    }

    /** Savatchadagi narxlarni joriy savdo turi/valyutaga qayta hisoblaydi. */
    private fun reprice(s: DraftState): DraftState {
        val c = catalog.state.value
        return s.copy(
            lines = s.lines.map { line ->
                val p = c.productsById[line.productId] ?: return@map line
                line.copy(price = Pricing.unitPrice(p, s.priceType, s.currency, c.rate))
            },
        )
    }

    private fun round3(v: Double) = Math.round(v * 1000.0) / 1000.0

    private companion object {
        const val K_STORE_ID = "tan_dokon_id"
        const val K_REGION_ID = "tan_region_id"
        const val K_STORE_NAME = "tan_dokon_nomi"
        const val K_PRICE_TYPE = "savdo_turi"
        const val K_CURRENCY = "valyuta_turi"
        const val K_MODE = "vazvrat"
    }
}
