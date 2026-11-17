package uz.imsoft.shared.report

import uz.imsoft.shared.SearchIndex
import uz.imsoft.shared.SearchOptions

/**
 * 1C hisobotidagi tovar nomini katalogdagi tovarga bog'lash (takrorlash va buyurtma uchun).
 * Avval aniq (buklangan nom bir xil), bo'lmasa eng yaqini — lekin u faqat "taxminiy" deb belgilanadi va
 * foydalanuvchi o'zi tasdiqlamaguncha savatchaga qo'shilmaydi.
 */
class CatalogMatch<T>(items: List<T>, private val name: (T) -> String, private val id: (T) -> String) {
    enum class Level { EXACT, APPROX, NONE }

    class Match<T>(val item: T?, val level: Level)

    private val exact = HashMap<String, T>()
    private val byId = HashMap<String, T>()
    private val index = SearchIndex(items, name)

    init {
        for (it in items) {
            exact.getOrPut(LedgerMerge.nameKey(name(it))) { it }
            byId.getOrPut(id(it)) { it }
        }
    }

    fun match(productName: String, productId: String = ""): Match<T> {
        if (productId.isNotEmpty()) byId[productId]?.let { return Match(it, Level.EXACT) }
        exact[LedgerMerge.nameKey(productName)]?.let { return Match(it, Level.EXACT) }
        if (productName.isBlank()) return Match(null, Level.NONE)
        val hits = index.search(productName, SearchOptions(fuzzy = true, layoutFix = false), limit = 2)
        val top = hits.firstOrNull() ?: return Match(null, Level.NONE)
        // Juda past ball — bog'lanmaydi
        if (top.score < 120) return Match(null, Level.NONE)
        return Match(top.item, Level.APPROX)
    }
}
