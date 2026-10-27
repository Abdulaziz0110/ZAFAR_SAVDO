package com.ilxomjon.dur_novvot_dokon.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilxomjon.dur_novvot_dokon.data.repo.HistoryRepository
import com.ilxomjon.dur_novvot_dokon.domain.SentOrder
import com.ilxomjon.dur_novvot_dokon.domain.SentPayment
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryState(
    val orders: List<SentOrder> = emptyList(),
    val payments: List<SentPayment> = emptyList(),
    val loaded: Boolean = false,
    /** outbox_id -> holat. */
    val states: Map<Long, OutboxState> = emptyMap(),
)

class HistoryViewModel(private val history: HistoryRepository) : ViewModel() {
    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val o = history.orders(); val p = history.payments()
            val st = history.states((o.map { it.outboxId } + p.map { it.outboxId }).filter { it > 0 })
            _state.value = HistoryState(o, p, loaded = true, states = st)
        }
    }
}
