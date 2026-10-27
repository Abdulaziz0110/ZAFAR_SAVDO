package com.ilxomjon.dur_novvot_dokon.ui.sale

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.databinding.SheetFinishBinding
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.Pricing
import com.ilxomjon.dur_novvot_dokon.ui.common.StorePickerSheet
import com.ilxomjon.dur_novvot_dokon.ui.describeError
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.money
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import com.ilxomjon.dur_novvot_dokon.ui.onTextChanged
import com.ilxomjon.dur_novvot_dokon.ui.show
import com.ilxomjon.dur_novvot_dokon.ui.showInvisible
import com.ilxomjon.dur_novvot_dokon.ui.viewModel

/** Buyurtmani yakunlash: chegirma, to'lov, printer va yuborish. */
class FinishSheet : BottomSheetDialogFragment() {
    private var _binding: SheetFinishBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: FinishViewModel
    private var updating = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val d = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        d.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        d.behavior.skipCollapsed = true
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return d
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetFinishBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = viewModel { FinishViewModel(ServiceLocator.draft, ServiceLocator.orders, ServiceLocator.appPrefs) }
        val s0 = vm.state.value
        val usd = s0.currency == Currency.USD

        binding.tvStore.text = s0.storeName
        binding.tvTotal.text = requireContext().money(s0.total, s0.currency)
        binding.tilCash.hint = getString(if (usd) R.string.finish_cash_usd else R.string.finish_cash_som)
        binding.tilCard.show(!usd)
        binding.chipPrinter1.text = getString(R.string.finish_printer_n, 1)
        binding.chipPrinter2.text = getString(R.string.finish_printer_n, 2)
        binding.chipPrinter3.text = getString(R.string.finish_printer_n, 3)
        binding.chipPrinter.check(
            when (s0.printer) { "2" -> R.id.chipPrinter2; "3" -> R.id.chipPrinter3; else -> R.id.chipPrinter1 },
        )
        binding.chipPrinter.setOnCheckedStateChangeListener { _, ids ->
            vm.setPrinter(when (ids.firstOrNull()) { R.id.chipPrinter2 -> "2"; R.id.chipPrinter3 -> "3"; else -> "1" })
        }

        binding.edtDiscountPercent.onTextChanged {
            if (updating) return@onTextChanged
            val amount = Pricing.discountFromPercent(vm.state.value.total, Money.parse(it))
            vm.setDiscount(amount)
            updating = true
            binding.edtDiscountAmount.setText(if (amount > 0.0) Money.plain(amount) else "")
            updating = false
        }
        binding.edtDiscountAmount.onTextChanged {
            if (updating) return@onTextChanged
            val amount = Money.parse(it)
            vm.setDiscount(amount)
            val total = vm.state.value.total
            updating = true
            binding.edtDiscountPercent.setText(if (amount > 0.0 && total > 0.0) Money.plain(amount / total * 100.0) else "")
            updating = false
        }
        binding.edtCash.onTextChanged { if (!updating) vm.setCash(Money.parse(it)) }
        binding.edtCard.onTextChanged { if (!updating) vm.setCard(Money.parse(it)) }
        binding.btnFillAll.setOnClickListener {
            vm.fillAll()
            val s = vm.state.value
            updating = true
            binding.edtCash.setText(Money.plain(s.cash))
            binding.edtCard.setText("")
            updating = false
        }
        binding.chipD5.setOnClickListener { binding.edtDiscountPercent.setText("5") }
        binding.chipD10.setOnClickListener { binding.edtDiscountPercent.setText("10") }
        binding.chipD15.setOnClickListener { binding.edtDiscountPercent.setText("15") }
        binding.chipRound.setOnClickListener { roundDown() }
        val store = ServiceLocator.draft.state.value.store
        val debt = store?.let { StorePickerSheet.debtAmounts(requireContext(), it) }.orEmpty()
        binding.tvStoreDebt.text = if (debt.isEmpty()) "" else getString(R.string.finish_store_debt, debt)
        binding.tvStoreDebt.show(debt.isNotEmpty())
        binding.btnSend.setOnClickListener { onSend() }

        launchOnStarted { vm.state.collect { render(it) } }
    }

    /** To'lanadigan summani butun mingga (so'm) yoki butun dollarga tushiradi, farqni chegirma qiladi. */
    private fun roundDown() {
        val s = vm.state.value
        val step = if (s.currency == Currency.SOM) 1000.0 else 1.0
        val payable = s.payable
        val rounded = Math.floor(payable / step) * step
        val extra = Money.round2(payable - rounded)
        if (extra <= 0.0) return
        binding.edtDiscountAmount.setText(Money.plain(s.discount + extra))
    }

    private fun onSend() {
        if (vm.state.value.overpaid) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.finish_overpay_title)
                .setMessage(R.string.finish_overpay_message)
                .setPositiveButton(R.string.finish_send_anyway) { _, _ -> vm.send() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            vm.send()
        }
    }

    private fun render(s: FinishState) {
        val ctx = requireContext()
        binding.tvPayable.text = ctx.money(s.payable, s.currency)
        val rem = s.remaining
        binding.tvDebtLabel.setText(if (s.overpaid) R.string.finish_overpaid else R.string.finish_debt)
        binding.tvDebt.text = ctx.money(kotlin.math.abs(rem), s.currency)
        binding.progress.showInvisible(s.sending)
        binding.btnSend.isEnabled = !s.sending
        binding.btnFillAll.isEnabled = !s.sending
        val err = s.error
        binding.tvError.show(err != null)
        if (err != null) binding.tvError.text = getString(R.string.finish_failed) + "\n" + ctx.describeError(err)
        if (s.sent) {
            Toast.makeText(ctx, R.string.finish_queued, Toast.LENGTH_SHORT).show()
            val nav = navigator()
            dismissAllowingStateLoss()
            nav.openTab(R.id.nav_home)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun show(fm: FragmentManager) = FinishSheet().show(fm, "finish")
    }
}
