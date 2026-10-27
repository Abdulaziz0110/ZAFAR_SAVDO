package com.ilxomjon.dur_novvot_dokon.ui.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilxomjon.dur_novvot_dokon.data.AppPrefs
import com.ilxomjon.dur_novvot_dokon.data.repo.CatalogRepository
import com.ilxomjon.dur_novvot_dokon.data.repo.OrderRepository
import com.ilxomjon.dur_novvot_dokon.domain.Store
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PaymentError {
    data object NoStore : PaymentError
    data object NoAmount : PaymentError
    class Failure(val cause: Throwable) : PaymentError
}

data class PaymentState(
    val store: Store? = null,
    val debtSom: Double? = null,
    val debtUsd: Double? = null,
    val debtLoading: Boolean = false,
    val debtError: Throwable? = null,
    val cash: Double = 0.0,
    val transfer: Double = 0.0,
    val click: Double = 0.0,
    val printer: String = "1",
    val sending: Boolean = false,
    val error: PaymentError? = null,
    /** Har muvaffaqiyatli yuborishda oshadi: ekran maydonlarni tozalaydi. */
    val sentToken: Int = 0,
) {
    val total: Double get() = cash + transfer + click
}

class PaymentViewModel(
    private val catalog: CatalogRepository,
    private val orders: OrderRepository,
    private val prefs: AppPrefs,
) : ViewModel() {
    private val _state = MutableStateFlow(PaymentState(printer = prefs.printer))
    val state: StateFlow<PaymentState> = _state.asStateFlow()
    private var debtJob: Job? = null

    fun selectStore(id: String) {
        val store = catalog.storeById(id) ?: return
        _state.update { it.copy(store = store, debtSom = null, debtUsd = null, debtError = null, error = null) }
        loadDebt()
    }

    fun loadDebt() {
        val store = _state.value.store ?: return
        debtJob?.cancel()
        _state.update { it.copy(debtLoading = true, debtError = null) }
        debtJob = viewModelScope.launch {
            try {
                val (som, usd) = catalog.refreshDebt(store.id)
                _state.update { it.copy(debtLoading = false, debtSom = som, debtUsd = usd) }
            } catch (e: Exception) {
                _state.update { it.copy(debtLoading = false, debtError = e) }
            }
        }
    }

    fun setAmounts(cash: Double, transfer: Double, click: Double) =
        _state.update { it.copy(cash = cash, transfer = transfer, click = click, error = null) }

    fun setPrinter(p: String) { prefs.printer = p; _state.update { it.copy(printer = p) } }

    fun send(note: String) {
        val s = _state.value
        if (s.sending) return
        val store = s.store ?: return _state.update { it.copy(error = PaymentError.NoStore) }
        if (s.total <= 0.0) return _state.update { it.copy(error = PaymentError.NoAmount) }
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            try {
                orders.sendPayment(store.id, store.name, s.cash, s.transfer, s.click, note, s.printer)
                debtJob?.cancel()
                _state.update {
                    PaymentState(printer = it.printer, sentToken = it.sentToken + 1)
                }
            } catch (e: Exception) {
                _state.update { it.copy(sending = false, error = PaymentError.Failure(e)) }
            }
        }
    }
}
