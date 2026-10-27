package com.ilxomjon.dur_novvot_dokon.ui.payment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.databinding.FragmentPaymentBinding
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.ui.common.StorePickerSheet
import com.ilxomjon.dur_novvot_dokon.ui.describeError
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.money
import com.ilxomjon.dur_novvot_dokon.ui.onTextChanged
import com.ilxomjon.dur_novvot_dokon.ui.show
import com.ilxomjon.dur_novvot_dokon.ui.showInvisible
import com.ilxomjon.dur_novvot_dokon.ui.viewModel

class PaymentFragment : Fragment() {
    private var _binding: FragmentPaymentBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: PaymentViewModel
    private var lastToken = 0
    private var clearing = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPaymentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = viewModel { PaymentViewModel(ServiceLocator.catalog, ServiceLocator.orders, ServiceLocator.appPrefs) }
        lastToken = vm.state.value.sentToken

        childFragmentManager.setFragmentResultListener(REQ_STORE, viewLifecycleOwner) { _, bundle ->
            vm.selectStore(bundle.getString(StorePickerSheet.KEY_ID).orEmpty())
        }
        binding.cardStore.setOnClickListener { StorePickerSheet.show(childFragmentManager, REQ_STORE) }
        binding.btnRefreshDebt.setOnClickListener { vm.loadDebt() }

        val amountWatcher = { _: String ->
            if (!clearing) {
                vm.setAmounts(
                    Money.parse(binding.edtCash.text?.toString()),
                    Money.parse(binding.edtTransfer.text?.toString()),
                    Money.parse(binding.edtClick.text?.toString()),
                )
            }
        }
        binding.edtCash.onTextChanged(amountWatcher)
        binding.edtTransfer.onTextChanged(amountWatcher)
        binding.edtClick.onTextChanged(amountWatcher)

        val s0 = vm.state.value
        binding.chipPrinter1.text = getString(R.string.finish_printer_n, 1)
        binding.chipPrinter2.text = getString(R.string.finish_printer_n, 2)
        binding.chipPrinter3.text = getString(R.string.finish_printer_n, 3)
        binding.chipPrinter.check(when (s0.printer) { "2" -> R.id.chipPrinter2; "3" -> R.id.chipPrinter3; else -> R.id.chipPrinter1 })
        binding.chipPrinter.setOnCheckedStateChangeListener { _, ids ->
            vm.setPrinter(when (ids.firstOrNull()) { R.id.chipPrinter2 -> "2"; R.id.chipPrinter3 -> "3"; else -> "1" })
        }
        binding.btnSend.setOnClickListener { vm.send(binding.edtNote.text?.toString().orEmpty()) }

        launchOnStarted { vm.state.collect { render(it) } }
    }

    private fun render(s: PaymentState) {
        val ctx = requireContext()
        binding.tvStoreName.text = s.store?.name ?: getString(R.string.sale_select_store)
        binding.btnRefreshDebt.isEnabled = s.store != null && !s.debtLoading
        binding.progressDebt.show(s.debtLoading)
        when {
            s.debtError != null -> {
                binding.tvDebtSom.text = getString(R.string.payment_debt_failed)
                binding.tvDebtUsd.text = ""
            }
            s.debtSom != null -> {
                binding.tvDebtSom.text = getString(R.string.payment_debt_som, ctx.money(s.debtSom, Currency.SOM))
                binding.tvDebtUsd.text = getString(R.string.payment_debt_usd, Money.format(s.debtUsd ?: 0.0, Currency.USD))
            }
            else -> {
                binding.tvDebtSom.setText(R.string.payment_debt_unknown)
                binding.tvDebtUsd.text = ""
            }
        }
        binding.tvDebtUsd.show(binding.tvDebtUsd.text.isNotEmpty())
        binding.tvTotal.text = getString(R.string.payment_total, ctx.money(s.total, Currency.SOM))
        binding.progress.showInvisible(s.sending)
        binding.btnSend.isEnabled = !s.sending

        val msg = when (val e = s.error) {
            PaymentError.NoStore -> getString(R.string.payment_error_store)
            PaymentError.NoAmount -> getString(R.string.payment_error_amount)
            is PaymentError.Failure -> getString(R.string.payment_failed) + "\n" + ctx.describeError(e.cause)
            null -> null
        }
        binding.tvError.text = msg
        binding.tvError.show(msg != null)

        if (s.sentToken != lastToken) {
            lastToken = s.sentToken
            clearing = true
            binding.edtCash.setText(""); binding.edtTransfer.setText(""); binding.edtClick.setText(""); binding.edtNote.setText("")
            clearing = false
            Snackbar.make(binding.root, R.string.payment_queued, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val REQ_STORE = "pay_store"
    }
}
