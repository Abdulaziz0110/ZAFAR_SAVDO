package com.ilxomjon.dur_novvot_dokon.ui.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.data.AppPrefs
import com.ilxomjon.dur_novvot_dokon.data.local.DebtCache
import com.ilxomjon.dur_novvot_dokon.data.local.HistoryDb
import com.ilxomjon.dur_novvot_dokon.data.repo.CatalogRepository
import com.ilxomjon.dur_novvot_dokon.data.repo.OrderRepository
import com.ilxomjon.dur_novvot_dokon.domain.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PaymentError {
    data object NoStore : PaymentError
    data object NoAmount : PaymentError
    data object BadAmount : PaymentError
    class Failure(val cause: Throwable) : PaymentError
}

data class PaymentState(
    val store: Store? = null,
    /** Serverdagi qarz (vaqti bilan). null — noma'lum. */
    val debt: DebtCache? = null,
    /** true — qarz shu ekranda serverdan hozirgina olindi; false — keshdan/oxirgi sinxrondan. */
    val debtFresh: Boolean = false,
    val debtLoading: Boolean = false,
    val debtError: Throwable? = null,
    /** Shu do'konga hali serverga yozilmagan to'lovlar (telefonda). Qarzga QO'SHILMAYDI. */
    val unsentCount: Int = 0,
    val unsentSum: Double = 0.0,
    val cash: Double = 0.0,
    val transfer: Double = 0.0,
    val click: Double = 0.0,
    val invalid: Boolean = false,
    val printer: String = "1",
    val sending: Boolean = false,
    val error: PaymentError? = null,
    /** Takroriy to'lov ehtimoli: agent tasdiqlashi kerak. */
    val confirmDuplicate: Boolean = false,
    /** Har muvaffaqiyatli yuborishda oshadi: ekran maydonlarni tozalaydi. */
    val sentToken: Int = 0,
    val lastReceipt: String = "",
) {
    val total: Double get() = cash + transfer + click
}

class PaymentViewModel(
    private val catalog: CatalogRepository,
    private val orders: OrderRepository,
    private val prefs: AppPrefs,
    private val history: HistoryDb,
) : ViewModel() {
    private val _state = MutableStateFlow(PaymentState(printer = prefs.printer))
    val state: StateFlow<PaymentState> = _state.asStateFlow()
    private var debtJob: Job? = null

    fun selectStore(id: String) {
        val store = catalog.storeById(id) ?: return
        _state.update { it.copy(store = store, debt = null, debtFresh = false, debtError = null, error = null, confirmDuplicate = false) }
        viewModelScope.launch {
            val known = catalog.knownDebt(store.id)
            if (_state.value.store?.id == store.id && !_state.value.debtFresh) _state.update { it.copy(debt = known) }
            refreshUnsent()
        }
        loadDebt()
    }

    private suspend fun refreshUnsent() {
        val id = _state.value.store?.id ?: return
        val (n, sum) = withContext(Dispatchers.IO) { history.unsentPayments(id) }
        _state.update { it.copy(unsentCount = n, unsentSum = sum) }
    }

    /** Qarzni serverdan so'raydi (20 s gacha). Ekran bloklanmaydi; boshqa do'kon tanlansa bekor qilinadi. */
    fun loadDebt() {
        val store = _state.value.store ?: return
        debtJob?.cancel()
        _state.update { it.copy(debtLoading = true, debtError = null) }
        debtJob = viewModelScope.launch {
            try {
                val d = catalog.refreshDebt(store.id)
                _state.update { if (it.store?.id == store.id) it.copy(debtLoading = false, debt = d, debtFresh = true) else it }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(debtLoading = false, debtError = e) }
            }
        }
    }

    /** Matn maydonlaridan. Tushunarsiz summa jimgina 0 bo'lmaydi — xato ko'rsatiladi. */
    fun setAmounts(cash: String, transfer: String, click: String) {
        val c = Money.parseUser(cash); val t = Money.parseUser(transfer); val k = Money.parseUser(click)
        _state.update { it.copy(cash = c ?: 0.0, transfer = t ?: 0.0, click = k ?: 0.0, invalid = c == null || t == null || k == null, error = null, confirmDuplicate = false) }
    }

    fun setPrinter(p: String) { prefs.printer = p; _state.update { it.copy(printer = p) } }

    fun dismissDuplicate() = _state.update { it.copy(confirmDuplicate = false) }

    fun send(note: String, duplicateConfirmed: Boolean = false) {
        val s = _state.value
        if (s.sending) return
        val store = s.store ?: return _state.update { it.copy(error = PaymentError.NoStore) }
        if (s.invalid) return _state.update { it.copy(error = PaymentError.BadAmount) }
        if (s.total <= 0.0) return _state.update { it.copy(error = PaymentError.NoAmount) }
        _state.update { it.copy(sending = true, error = null, confirmDuplicate = false) }
        viewModelScope.launch {
            try {
                if (!duplicateConfirmed && prefs.fDuplicatePaymentGuard && looksDuplicate(store.id, s)) {
                    _state.update { it.copy(sending = false, confirmDuplicate = true) }
                    return@launch
                }
                val receipt = orders.sendPayment(store.id, store.name, s.cash, s.transfer, s.click, note, s.printer)
                debtJob?.cancel()
                _state.update { PaymentState(printer = it.printer, sentToken = it.sentToken + 1, lastReceipt = receipt) }
            } catch (e: Exception) {
                _state.update { it.copy(sending = false, error = PaymentError.Failure(e)) }
            }
        }
    }

    /** Shu do'konga oxirgi 15 daqiqada aynan shu summalar bilan to'lov bo'lganmi. */
    private suspend fun looksDuplicate(storeId: String, s: PaymentState): Boolean = withContext(Dispatchers.IO) {
        val since = System.currentTimeMillis() - DUP_WINDOW_MS
        history.paymentsBetween(since, Long.MAX_VALUE, storeId).any {
            Money.round2(it.cash) == Money.round2(s.cash) && Money.round2(it.transfer) == Money.round2(s.transfer) && Money.round2(it.click) == Money.round2(s.click)
        }
    }

    private companion object { const val DUP_WINDOW_MS = 15 * 60_000L }
}
