package com.ilxomjon.dur_novvot_dokon.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilxomjon.dur_novvot_dokon.data.repo.HistoryRepository
import com.ilxomjon.dur_novvot_dokon.domain.SentOrder
import com.ilxomjon.dur_novvot_dokon.domain.SentPayment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryState(
    val orders: List<SentOrder> = emptyList(),
    val payments: List<SentPayment> = emptyList(),
    val loaded: Boolean = false,
)

class HistoryViewModel(private val history: HistoryRepository) : ViewModel() {
    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = HistoryState(history.orders(), history.payments(), loaded = true)
        }
    }
}
