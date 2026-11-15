package com.ilxomjon.dur_novvot_dokon.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilxomjon.dur_novvot_dokon.data.repo.CatalogRepository
import com.ilxomjon.dur_novvot_dokon.data.repo.DraftRepository
import com.ilxomjon.dur_novvot_dokon.data.repo.HistoryRepository
import com.ilxomjon.dur_novvot_dokon.data.repo.OutboxRepository
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.DealMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class TodayStats(
    val orders: Int = 0,
    val ordersSom: Double = 0.0,
    val ordersUsd: Double = 0.0,
    val payments: Int = 0,
    val paymentsSum: Double = 0.0,
)

data class HomeState(
    val agentName: String = "",
    val phone: String = "",
    val rate: Double = 0.0,
    val rateText: String = "",
    val lastSyncAt: Long = 0L,
    val syncing: Boolean = false,
    val loaded: Boolean = false,
    val hasProducts: Boolean = false,
    val draftStore: String? = null,
    val draftCount: Int = 0,
    val draftTotal: Double = 0.0,
    val draftCurrency: Currency = Currency.SOM,
    val today: TodayStats = TodayStats(),
    val pending: Int = 0,
    val needsCheck: Int = 0,
)

sealed interface HomeEvent {
    data object SyncOk : HomeEvent
    class SyncFailed(val cause: Throwable) : HomeEvent
}

class HomeViewModel(
    private val catalog: CatalogRepository,
    private val draft: DraftRepository,
    private val history: HistoryRepository,
    private val outbox: OutboxRepository,
) : ViewModel() {

    private val today = MutableStateFlow(TodayStats())
    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    val state: StateFlow<HomeState> = combine(catalog.state, draft.state, today, outbox.counts) { c, d, t, counts ->
        HomeState(
            agentName = c.agent?.fullName.orEmpty(), phone = c.agent?.phone.orEmpty(),
            rate = c.rate, rateText = c.rateText, lastSyncAt = c.lastSyncAt, syncing = c.syncing,
            loaded = c.loaded, hasProducts = c.hasProducts,
            draftStore = d.store?.name, draftCount = d.itemCount, draftTotal = d.total, draftCurrency = d.currency,
            today = t, pending = counts.unsent, needsCheck = counts.needsCheck,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    init {
        viewModelScope.launch {
            val c = catalog.state.first { it.loaded }
            if (c.agent != null && !c.hasProducts) sync()
        }
    }

    fun onResume() {
        refreshToday()
        viewModelScope.launch {
            outbox.refreshCount()
            if (outbox.pending.value > 0) outbox.kick()
            refreshToday()
        }
    }

    fun retryPending() = outbox.retryNow()

    private fun refreshToday() {
        viewModelScope.launch {
            val start = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val orders = history.orders().filter { it.sentAt >= start && it.mode == DealMode.SALE }
            val payments = history.payments().filter { it.sentAt >= start }
            today.value = TodayStats(
                orders = orders.size,
                ordersSom = orders.filter { it.currency == Currency.SOM }.sumOf { it.total - it.discount },
                ordersUsd = orders.filter { it.currency == Currency.USD }.sumOf { it.total - it.discount },
                payments = payments.size,
                paymentsSum = payments.sumOf { it.total },
            )
        }
    }

    /** Savatcha bo'sh bo'lsagina sotuv/qaytarish rejimi almashadi (aks holda ikkalasi aralashib ketadi). */
    fun startDeal(mode: DealMode) {
        if (draft.state.value.lines.isEmpty()) draft.setMode(mode)
    }

    fun sync() {
        if (catalog.state.value.syncing) return
        viewModelScope.launch {
            try {
                catalog.sync()
                draft.load()
                // v4.0.2: Agent — Admin yuborgan savdolar tarixi ham darhol (2 daqiqa kutmasdan)
                if (!com.ilxomjon.dur_novvot_dokon.core.AppRole.isAdmin) {
                    runCatching { com.ilxomjon.dur_novvot_dokon.core.ServiceLocator.historyShare.sync(com.ilxomjon.dur_novvot_dokon.core.ServiceLocator.cloudConfig, true) }
                }
                _events.tryEmit(HomeEvent.SyncOk)
            } catch (e: Exception) {
                _events.tryEmit(HomeEvent.SyncFailed(e))
            }
        }
    }
}
