package com.ilxomjon.dur_novvot_dokon.data.repo

import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.data.AppPrefs
import com.ilxomjon.dur_novvot_dokon.data.local.DebtCache
import com.ilxomjon.dur_novvot_dokon.data.local.HistoryDb
import com.ilxomjon.dur_novvot_dokon.data.local.CatalogDao
import com.ilxomjon.dur_novvot_dokon.data.remote.ServerApi
import com.ilxomjon.dur_novvot_dokon.domain.Agent
import com.ilxomjon.dur_novvot_dokon.domain.Group
import com.ilxomjon.dur_novvot_dokon.domain.Product
import com.ilxomjon.dur_novvot_dokon.domain.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class CatalogState(
    val agent: Agent? = null,
    val products: List<Product> = emptyList(),
    val groups: List<Group> = emptyList(),
    val stores: List<Store> = emptyList(),
    val rate: Double = 0.0,
    val rateText: String = "",
    val lastSyncAt: Long = 0L,
    val loaded: Boolean = false,
    val syncing: Boolean = false,
) {
    val productsById: Map<String, Product> by lazy { products.associateBy { it.id } }
    val hasProducts: Boolean get() = products.isNotEmpty()
}

class CatalogRepository(
    private val dao: CatalogDao,
    private val api: ServerApi,
    private val prefs: AppPrefs,
    private val history: HistoryDb,
) {
    private val _state = MutableStateFlow(CatalogState())
    val state: StateFlow<CatalogState> = _state.asStateFlow()
    private val mutex = Mutex()

    /** Katalog yoki qarz yangilanganda (2-ilovaga yuborish uchun). */
    @Volatile var onChanged: (() -> Unit)? = null

    suspend fun load() = mutex.withLock { reload() }

    private suspend fun reload() = withContext(Dispatchers.IO) {
        val products = dao.products()
        val groups = dao.groups()
        val stores = dao.stores()
        val rate = dao.rate()
        val rateText = dao.rateText()
        val agent = dao.agent()
        _state.update {
            it.copy(
                agent = agent, products = products, groups = groups, stores = stores, rate = rate, rateText = rateText,
                lastSyncAt = prefs.lastSyncAt, loaded = true,
            )
        }
    }

    /** Serverdan hamma ma'lumotni oladi va bitta tranzaksiyada almashtiradi. Xato bo'lsa eski ma'lumot qoladi. */
    suspend fun sync() {
        val agentId = _state.value.agent?.id ?: dao.agent()?.id ?: throw IllegalStateException("Agent topilmadi")
        _state.update { it.copy(syncing = true) }
        try {
            val snapshot = api.fetchSnapshot(agentId)
            mutex.withLock {
                withContext(Dispatchers.IO) { dao.replaceAll(snapshot) }
                prefs.lastSyncAt = System.currentTimeMillis()
                reload()
            }
            runCatching { onChanged?.invoke() }
        } finally {
            _state.update { it.copy(syncing = false) }
        }
    }

    /** Do'kon qarzini serverdan yangilaydi va vaqti bilan keshga yozadi. */
    suspend fun refreshDebt(storeId: String): DebtCache {
        val agentId = _state.value.agent?.id ?: throw IllegalStateException("Agent topilmadi")
        val (som, usd) = api.fetchDebt(agentId, storeId)
        val now = System.currentTimeMillis()
        mutex.withLock {
            withContext(Dispatchers.IO) {
                dao.updateDebt(storeId, som, usd)
                history.putDebt(storeId, Money.parse(som), Money.parse(usd), now)
            }
            reload()
        }
        runCatching { onChanged?.invoke() }
        val s = _state.value.stores.firstOrNull { it.id == storeId }
        return DebtCache(storeId, s?.debtSom ?: 0.0, s?.debtUsd ?: 0.0, now)
    }

    /**
     * Qarzning oxirgi ma'lum qiymati va vaqti: serverdan alohida so'ralgan bo'lsa shu, aks holda oxirgi sinxron (katalog) vaqti.
     * Lokal yuborilmagan to'lovlar bu yerga QO'SHILMAYDI.
     */
    suspend fun knownDebt(storeId: String): DebtCache? = withContext(Dispatchers.IO) {
        val cached = history.debt(storeId)
        val sync = _state.value.lastSyncAt
        val store = storeById(storeId)
        when {
            cached != null && cached.fetchedAt >= sync -> cached
            store != null && sync > 0 -> DebtCache(storeId, store.debtSom, store.debtUsd, sync)
            else -> cached
        }
    }

    fun storeById(id: String): Store? = _state.value.stores.firstOrNull { it.id == id }
}
