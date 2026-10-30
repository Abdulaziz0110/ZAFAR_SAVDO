package uz.imsoft.mijoz.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.imsoft.shared.JObj
import uz.imsoft.shared.Json
import uz.imsoft.shared.OrderLine

/**
 * Buyurtma savatchasi (2-ilova, v2.6): "Narxlar" dan tovar tanlanadi va 1-ilovaga BUYURTMA bo'lib boradi.
 * Bu savdo emas — 1-ilova egasi tasdiqlagandagina savatchasiga tushadi. Telefonda saqlanadi (ilova yopilsa ham qoladi).
 */
class OrderCart(context: Context) {
    data class Item(val key: String, val name: String, val productId: String, val qty: Double, val price: Double = 0.0, val usd: Boolean = false)

    private val p = context.getSharedPreferences("order_cart", Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Item>> = _items.asStateFlow()

    private fun load(): List<Item> = runCatching {
        (Json.parse(p.getString("items", "[]").orEmpty()) as? List<*>).orEmpty().mapNotNull { x ->
            @Suppress("UNCHECKED_CAST")
            val o = JObj(x as? Map<String, Any?> ?: return@mapNotNull null)
            Item(o.str("k"), o.str("n"), o.str("id"), o.num("q"), o.num("p"), o.bool("u", false)).takeIf { it.qty > 0 && it.name.isNotEmpty() }
        }
    }.getOrDefault(emptyList())

    private fun save(l: List<Item>) {
        _items.value = l
        p.edit().putString("items", Json.write(l.map { linkedMapOf("k" to it.key, "n" to it.name, "id" to it.productId, "q" to it.qty, "p" to it.price, "u" to it.usd) })).apply()
    }

    fun qty(key: String): Double = _items.value.firstOrNull { it.key == key }?.qty ?: 0.0

    /** Miqdorni o'rnatadi (0 — olib tashlaydi). */
    fun set(item: Item) {
        val l = _items.value
        save(
            when {
                item.qty <= 0.0 -> l.filter { it.key != item.key }
                l.any { it.key == item.key } -> l.map { if (it.key == item.key) item else it }
                else -> l + item
            },
        )
    }

    fun remove(key: String) = save(_items.value.filter { it.key != key })
    fun clear() = save(emptyList())

    fun lines(): List<OrderLine> = _items.value.map { OrderLine(it.name, it.qty, it.productId) }

    /** Taxminiy summa (narx ma'lum bo'lganlari) — so'm va dollar alohida. */
    fun approxTotal(): Pair<Double, Double> = _items.value.fold(0.0 to 0.0) { (s, u), it ->
        if (it.usd) s to u + it.qty * it.price else s + it.qty * it.price to u
    }
}
