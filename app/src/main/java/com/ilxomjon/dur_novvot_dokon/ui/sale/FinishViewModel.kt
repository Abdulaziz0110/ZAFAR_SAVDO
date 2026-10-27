package com.ilxomjon.dur_novvot_dokon.ui.sale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.data.AppPrefs
import com.ilxomjon.dur_novvot_dokon.data.remote.Protocol
import com.ilxomjon.dur_novvot_dokon.data.repo.CheckoutInput
import com.ilxomjon.dur_novvot_dokon.data.repo.DraftRepository
import com.ilxomjon.dur_novvot_dokon.data.repo.OrderRepository
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.Pricing
import com.ilxomjon.dur_novvot_dokon.domain.CartWarning
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FinishState(
    val storeName: String = "",
    val currency: Currency = Currency.SOM,
    val total: Double = 0.0,
    val discount: Double = 0.0,
    val cash: Double = 0.0,
    val card: Double = 0.0,
    val printer: String = "1",
    val sending: Boolean = false,
    val error: Throwable? = null,
    val sent: Boolean = false,
    /** Kvitansiya kodi (masalan "B-0412"). */
    val receipt: String = "",
    /** Tushunarsiz summa kiritilgan (jimgina 0 qilinmaydi). */
    val invalid: Boolean = false,
    val warnings: List<CartWarning> = emptyList(),
) {
    val payable: Double get() = Pricing.totals(total, discount).payable
    val paid: Double get() = if (currency == Currency.SOM) cash + card else cash
    /** Musbat - qarz, manfiy - ortiqcha to'lov. */
    val remaining: Double get() = Money.round2(payable - paid)
    val overpaid: Boolean get() = remaining < -0.004
}

class FinishViewModel(
    private val draft: DraftRepository,
    private val orders: OrderRepository,
    private val prefs: AppPrefs,
) : ViewModel() {
    private val _state = MutableStateFlow(
        draft.state.value.let { FinishState(it.store?.name.orEmpty(), it.currency, it.total, printer = prefs.printer) },
    )
    val state: StateFlow<FinishState> = _state.asStateFlow()
    private var checkCtx: CheckerSupport.Ctx? = null

    init {
        val storeId = draft.state.value.store?.id
        if (storeId != null) viewModelScope.launch {
            checkCtx = CheckerSupport.load(storeId)
            recheck()
        }
    }

    private fun recheck() = _state.update { it.copy(warnings = CheckerSupport.warnings(draft.state.value, checkCtx, includeMissing = true)) }

    fun keepAsIs() { CheckerSupport.dismiss(_state.value.warnings.map { it.key }); recheck() }

    /** Matndan (minglik ajratgichlar bilan ham). null — tushunarsiz. */
    fun setInputs(discount: Double?, cash: Double?, card: Double?) = _state.update {
        it.copy(
            discount = (discount ?: it.discount).coerceIn(0.0, maxOf(it.total, 0.0)),
            cash = cash ?: it.cash, card = card ?: it.card,
            invalid = discount == null || cash == null || card == null,
        )
    }

    fun setDiscount(v: Double) = _state.update { it.copy(discount = v.coerceIn(0.0, maxOf(it.total, 0.0))) }
    fun setCash(v: Double) = _state.update { it.copy(cash = v) }
    fun setCard(v: Double) = _state.update { it.copy(card = v) }
    fun setPrinter(p: String) { prefs.printer = p; _state.update { it.copy(printer = p) } }
    fun clearError() = _state.update { it.copy(error = null) }

    /** Barcha summani naqd to'lashga qo'yadi. */
    fun fillAll() = _state.update { it.copy(cash = it.payable, card = 0.0) }

    fun send() {
        val s = _state.value
        if (s.sending || s.sent) return
        if (s.invalid) return
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            val pay = if (s.currency == Currency.SOM) Protocol.Payments(cashSom = s.cash, card = s.card)
            else Protocol.Payments(cashUsd = s.cash)
            try {
                val receipt = orders.sendOrder(CheckoutInput(s.discount, pay, s.printer))
                _state.update { it.copy(sending = false, sent = true, receipt = receipt) }
            } catch (e: Exception) {
                _state.update { it.copy(sending = false, error = e) }
            }
        }
    }
}
