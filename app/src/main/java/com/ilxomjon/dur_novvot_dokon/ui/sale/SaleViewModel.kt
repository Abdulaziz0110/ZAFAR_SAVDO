package com.ilxomjon.dur_novvot_dokon.ui.sale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilxomjon.dur_novvot_dokon.data.AppPrefs
import com.ilxomjon.dur_novvot_dokon.data.repo.KpiTracker
import com.ilxomjon.dur_novvot_dokon.data.repo.ParkedCartRepository
import com.ilxomjon.dur_novvot_dokon.domain.CartLine
import com.ilxomjon.dur_novvot_dokon.domain.SentOrder
import com.ilxomjon.dur_novvot_dokon.data.repo.CatalogRepository
import com.ilxomjon.dur_novvot_dokon.data.repo.DraftRepository
import com.ilxomjon.dur_novvot_dokon.data.repo.HistoryRepository
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.DealMode
import com.ilxomjon.dur_novvot_dokon.domain.DraftState
import com.ilxomjon.dur_novvot_dokon.domain.Group
import com.ilxomjon.dur_novvot_dokon.domain.PriceType
import com.ilxomjon.dur_novvot_dokon.domain.Pricing
import com.ilxomjon.dur_novvot_dokon.domain.Product
import com.ilxomjon.dur_novvot_dokon.domain.SentLine
import com.ilxomjon.dur_novvot_dokon.domain.Store
import com.ilxomjon.dur_novvot_dokon.domain.Suggestion
import com.ilxomjon.dur_novvot_dokon.domain.Suggestions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.imsoft.shared.SearchIndex
import uz.imsoft.shared.SearchOptions

sealed interface ListFilter {
    data object All : ListFilter
    data object Suggested : ListFilter
    data object InCart : ListFilter
    data class OfGroup(val id: String) : ListFilter
}

/** Ro'yxat holati: faqat qidiruv/filtr/katalog o'zgarganda yangilanadi (savatcha o'zgarishida EMAS). */
data class ListState(
    val products: List<Product> = emptyList(),
    val groups: List<Group> = emptyList(),
    val filter: ListFilter = ListFilter.All,
    val hasProducts: Boolean = false,
    val loaded: Boolean = false,
    val searching: Boolean = false,
    /** Nomdagi mos qismlar (qalin ko'rsatish uchun). */
    val highlights: Map<String, List<IntRange>> = emptyMap(),
    /** Natijalar taxminiy (imlo/klaviatura tuzatildi yoki so'zlarning hammasi topilmadi). */
    val approximate: Boolean = false,
)

/** Narx hisoblash sharoiti. */
data class PricingCtx(val type: PriceType, val currency: Currency, val rate: Double)

sealed interface SaleEvent {
    /** [before] — "Bekor qilish" uchun oldingi savatcha. [missing] — katalogda yo'qligi uchun o'tkazib yuborilganlar. */
    class Repeated(val count: Int, val missing: Int, val before: List<CartLine>) : SaleEvent
    data object NothingToRepeat : SaleEvent
    class AddedUsual(val count: Int, val before: List<CartLine>) : SaleEvent
    class Cleared(val before: List<CartLine>) : SaleEvent
    /** Do'kon tanlandi: xulosa kartasini ko'rsatish. */
    class StoreCard(val storeId: String) : SaleEvent
}

/** Aqlli asosiy tugma amali. Joyi va rangi doim bir xil, faqat yozuvi o'zgaradi. */
sealed interface SmartAction {
    data object PickStore : SmartAction
    class AddUsual(val count: Int) : SmartAction
    data object Finish : SmartAction
    class TakePayment(val debt: Double) : SmartAction
    data object None : SmartAction
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SaleViewModel(
    private val catalog: CatalogRepository,
    private val draftRepo: DraftRepository,
    private val history: HistoryRepository,
    private val prefs: AppPrefs,
    private val parked: ParkedCartRepository,
    private val kpi: KpiTracker,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<ListFilter>(ListFilter.All)
    private val suggestions = MutableStateFlow<List<Suggestion>>(emptyList())
    private val favIds = MutableStateFlow(prefs.favoriteStoreIds)
    private val lastOrder = MutableStateFlow<List<SentLine>>(emptyList())
    private val _showCost = MutableStateFlow(false)
    private val _events = MutableSharedFlow<SaleEvent>(extraBufferCapacity = 4)
    private val _storeOrders = MutableStateFlow<List<SentOrder>>(emptyList())
    /** Tanlangan do'konning tarixi (tekshiruvchi va "oxirgi narx" uchun). */
    val storeOrders: StateFlow<List<SentOrder>> = _storeOrders.asStateFlow()
    val parkedCount: StateFlow<Int> = parked.items.map { it.size }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val flags get() = prefs

    /** "Kelgan narx" almashtirgichi: saqlanmaydi (mijoz ko'rib qolmasligi uchun har safar o'chiq boshlanadi). */
    val showCost: StateFlow<Boolean> = _showCost.asStateFlow()
    val events: SharedFlow<SaleEvent> = _events.asSharedFlow()
    val draft: StateFlow<DraftState> = draftRepo.state

    /** To'liq takliflar (izoh uchun). */
    val suggestionList: StateFlow<List<Suggestion>> = suggestions.asStateFlow()

    /** Mijoz odatda oladigan tovarlar uchun odatiy miqdor (tovar kodi -> miqdor). */
    val suggestedQty: StateFlow<Map<String, Double>> = suggestions.map { list -> list.associate { it.productId to it.qty } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Sozlamalarda belgilangan tez do'konlar. */
    val favorites: StateFlow<List<Store>> = combine(catalog.state, favIds) { c, ids ->
        ids.mapNotNull { id -> c.stores.firstOrNull { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pricing: StateFlow<PricingCtx> = combine(draftRepo.state, catalog.state) { d, c ->
        PricingCtx(d.priceType, d.currency, c.rate)
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, PricingCtx(PriceType.WHOLESALE, Currency.SOM, 0.0))

    /** Faqat "Savatchada" filtri tanlanganda savatcha tarkibi kuzatiladi. */
    private val cartIds = filter.flatMapLatest { f ->
        if (f is ListFilter.InCart) draftRepo.state.map { s -> s.lines.mapTo(HashSet()) { it.productId } }.distinctUntilChanged()
        else flowOf(emptySet<String>())
    }

    val list: StateFlow<ListState> = combine(
        catalog.state.map { Triple(it.products, it.groups, it.loaded) }.distinctUntilChanged(),
        query.debounce { if (it.isEmpty()) 0L else 90L },
        filter, suggestions, cartIds,
    ) { cat, q, f, freq, inCart ->
        val (all, groups, loaded) = cat
        val validFilter = if (f is ListFilter.OfGroup && groups.none { it.id == f.id }) ListFilter.All else f
        search(all, q, validFilter, freq, inCart).copy(groups = groups, filter = validFilter, hasProducts = all.isNotEmpty(), loaded = loaded, searching = q.isNotBlank())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, ListState())

    val canRepeat: StateFlow<Boolean> = combine(lastOrder, draftRepo.state.map { it.lines.isEmpty() }) { last, empty ->
        last.isNotEmpty() && empty
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Aqlli tugma: savatcha bo'sh + do'kon + takliflar -> "Odatdagini qo'sh"; savatcha bor -> "Yakunla"; qarz bor -> "To'lov". */
    val smart: StateFlow<SmartAction> = combine(draftRepo.state, suggestions, catalog.state) { d, sug, c ->
        val store = d.store
        when {
            store == null -> SmartAction.PickStore
            d.lines.isNotEmpty() -> SmartAction.Finish
            sug.isNotEmpty() && d.mode == DealMode.SALE -> SmartAction.AddUsual(sug.count { c.productsById.containsKey(it.productId) })
            (c.stores.firstOrNull { it.id == store.id }?.debtSom ?: 0.0) > 0.0 -> SmartAction.TakePayment(c.stores.first { it.id == store.id }.debtSom)
            else -> SmartAction.None
        }
    }.distinctUntilChanged { a, b -> a.javaClass == b.javaClass && (a as? SmartAction.AddUsual)?.count == (b as? SmartAction.AddUsual)?.count }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SmartAction.None)

    init {
        viewModelScope.launch { parked.refresh() }
        viewModelScope.launch { runCatching { loadPopularity() } }
        viewModelScope.launch {
            draftRepo.state.map { it.store?.id.orEmpty() }.distinctUntilChanged().collect { storeId ->
                if (storeId.isEmpty()) {
                    suggestions.value = emptyList()
                    lastOrder.value = emptyList()
                    _storeOrders.value = emptyList()
                    if (filter.value == ListFilter.Suggested) filter.value = ListFilter.All
                    return@collect
                }
                if (draftRepo.state.value.lines.isEmpty()) kpi.startIfIdle(storeId)
                val own = history.storeOrders(storeId)
                _storeOrders.value = own
                val global = history.recentSaleOrders()
                val fb = history.feedback(storeId)
                val list = withContext(Dispatchers.Default) { Suggestions.build(own, global, System.currentTimeMillis(), feedback = fb) }
                suggestions.value = list
                lastOrder.value = own.firstOrNull { it.mode == DealMode.SALE }?.lines?.filter { it.productId.isNotEmpty() }.orEmpty()
                // Mijoz tanlanganda va savatcha bo'sh bo'lsa, taklif qilinadigan tovarlar o'zi ochiladi.
                if (list.isNotEmpty() && draftRepo.state.value.lines.isEmpty() && filter.value == ListFilter.All) {
                    filter.value = ListFilter.Suggested
                }
            }
        }
    }

    // ---------------- qidiruv (v2.4: professional qidiruv, shared/Search.kt) ----------------
    @Volatile private var index: SearchIndex<Product>? = null
    @Volatile private var indexOf: List<Product>? = null
    /** Tovar mashhurligi (shu telefondagi oxirgi buyurtmalar bo'yicha) — qidiruv va saralash uchun. */
    @Volatile private var popularity: Map<String, Double> = emptyMap()

    private fun indexFor(all: List<Product>): SearchIndex<Product> {
        val cur = index
        if (cur != null && indexOf === all) return cur
        return SearchIndex(all, { it.name }, { it.id }).also { index = it; indexOf = all }
    }

    private fun search(all: List<Product>, q: String, f: ListFilter, sugg: List<Suggestion>, inCart: Set<String>): ListState {
        val base: List<Product> = when (f) {
            ListFilter.All -> all
            is ListFilter.OfGroup -> all.filter { it.groupId == f.id }
            ListFilter.InCart -> all.filter { it.id in inCart }
            ListFilter.Suggested -> {
                val byId = HashMap<String, Product>(all.size * 2)
                for (p in all) byId[p.id] = p
                sugg.mapNotNull { byId[it.productId] }
            }
        }
        if (q.isBlank()) return ListState(products = sorted(base, f))
        val allowed: Set<String>? = if (f == ListFilter.All) null else base.mapTo(HashSet()) { it.id }
        val pop = popularity
        val opts = SearchOptions(fuzzy = prefs.fFuzzySearch, layoutFix = prefs.fLayoutFix)
        val hits = indexFor(all).search(q, opts, popularity = { pop[it.id] ?: 0.0 })
            .filter { allowed == null || it.item.id in allowed }
        val hl = HashMap<String, List<IntRange>>(hits.size * 2)
        for (h in hits) if (h.ranges.isNotEmpty()) hl[h.item.id] = h.ranges
        return ListState(products = hits.map { it.item }, highlights = hl, approximate = hits.isNotEmpty() && hits.all { it.approximate })
    }

    /** Qidiruvsiz ro'yxat tartibi (Sozlamalar ▸ Qulayliklar). Taklif/savatcha filtrlarida o'z tartibi saqlanadi. */
    private fun sorted(list: List<Product>, f: ListFilter): List<Product> {
        if (f == ListFilter.Suggested || f == ListFilter.InCart) return list
        return when (prefs.productSort) {
            "name" -> list.sortedBy { it.searchKey }
            "popular" -> { val pop = popularity; list.sortedByDescending { pop[it.id] ?: 0.0 } }
            else -> list
        }
    }

    private suspend fun loadPopularity() {
        val orders = history.recentSaleOrders()
        val count = HashMap<String, Int>()
        for (o in orders) for (l in o.lines) if (l.productId.isNotEmpty()) count[l.productId] = (count[l.productId] ?: 0) + 1
        popularity = count.mapValues { (_, n) -> minOf(25.0, Math.log(1.0 + n) * 7.0) }
    }

    // ---------------- amallar ----------------
    fun setQuery(q: String) { query.value = q }
    fun setFilter(f: ListFilter) { filter.value = f }
    fun setShowCost(on: Boolean) { _showCost.value = on }
    fun reloadFavorites() { favIds.value = prefs.favoriteStoreIds }

    /** Taklif qilingan tovarlarning savatchada yo'qlarini odatiy miqdorda qo'shadi. */
    fun addAllSuggested() {
        val c = catalog.state.value
        val d = draftRepo.state.value
        val items = suggestions.value.mapNotNull { s ->
            if (d.lineFor(s.productId) != null) return@mapNotNull null
            val p = c.productsById[s.productId] ?: return@mapNotNull null
            Triple(p, s.qty, Pricing.unitPrice(p, d.priceType, d.currency, c.rate))
        }
        if (items.isEmpty()) return
        val before = d.lines
        draftRepo.addLines(items)
        kpi.tap()
        // Hech qachon jimgina qo'shilmaydi: nechta qo'shilgani va "Bekor qilish" ko'rsatiladi.
        _events.tryEmit(SaleEvent.AddedUsual(items.size, before))
    }

    fun plus(p: Product, price: Double, amount: Double = 1.0) { kpi.tap(); draftRepo.adjust(p, amount, price) }
    fun minus(p: Product, price: Double) { kpi.tap(); draftRepo.adjust(p, -1.0, price) }
    fun tap() = kpi.tap()

    /** "Bekor qilish": savatchani oldingi holatiga qaytaradi. */
    fun undo(before: List<CartLine>) = draftRepo.replaceLines(before)

    /** Foydalanuvchi do'kon tanladi (ro'yxatdan yoki tez tugmadan). */
    fun setStore(store: Store?) {
        draftRepo.setStore(store)
        if (store != null && prefs.fStoreCard && draftRepo.state.value.lines.isEmpty()) _events.tryEmit(SaleEvent.StoreCard(store.id))
    }

    fun park() = viewModelScope.launch { parked.parkActive(); kpi.cancel() }
    fun setPriceType(t: PriceType) = draftRepo.setPriceType(t)
    fun setCurrency(c: Currency) = draftRepo.setCurrency(c)
    fun setMode(m: DealMode) = draftRepo.setMode(m)
    fun clearCart() {
        val before = draftRepo.state.value.lines
        draftRepo.clearLines()
        kpi.cancel()
        if (before.isNotEmpty()) _events.tryEmit(SaleEvent.Cleared(before))
    }
    fun storeById(id: String): Store? = catalog.storeById(id)

    /** Do'konga oxirgi buyurtmani joriy narxlarda savatchaga qo'shadi. */
    fun repeatLast() {
        val c = catalog.state.value
        val d = draftRepo.state.value
        val last = lastOrder.value
        val items = last.mapNotNull { l ->
            val p = c.productsById[l.productId] ?: return@mapNotNull null
            Triple(p, l.qty, Pricing.unitPrice(p, d.priceType, d.currency, c.rate))
        }
        if (items.isEmpty()) { _events.tryEmit(SaleEvent.NothingToRepeat); return }
        val before = d.lines
        draftRepo.addLines(items)
        kpi.tap()
        _events.tryEmit(SaleEvent.Repeated(items.size, last.size - items.size, before))
    }
}
