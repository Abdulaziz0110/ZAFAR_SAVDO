package com.ilxomjon.dur_novvot_dokon.data.repo

import com.ilxomjon.dur_novvot_dokon.data.AppPrefs
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
) {
    private val _state = MutableStateFlow(CatalogState())
    val state: StateFlow<CatalogState> = _state.asStateFlow()
    private val mutex = Mutex()

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
        } finally {
            _state.update { it.copy(syncing = false) }
        }
    }

    /** Do'kon qarzini serverdan yangilaydi. */
    suspend fun refreshDebt(storeId: String): Pair<Double, Double> {
        val agentId = _state.value.agent?.id ?: throw IllegalStateException("Agent topilmadi")
        val (som, usd) = api.fetchDebt(agentId, storeId)
        mutex.withLock {
            withContext(Dispatchers.IO) { dao.updateDebt(storeId, som, usd) }
            reload()
        }
        val s = _state.value.stores.firstOrNull { it.id == storeId }
        return (s?.debtSom ?: 0.0) to (s?.debtUsd ?: 0.0)
    }

    fun storeById(id: String): Store? = _state.value.stores.firstOrNull { it.id == id }
}
