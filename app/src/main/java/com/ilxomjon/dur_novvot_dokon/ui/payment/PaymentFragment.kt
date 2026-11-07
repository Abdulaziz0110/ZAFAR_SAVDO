package com.ilxomjon.dur_novvot_dokon.ui.payment

import uz.imsoft.shared.android.NumInput
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.databinding.FragmentPaymentBinding
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.ui.UiBus
import com.ilxomjon.dur_novvot_dokon.ui.common.StorePickerSheet
import com.ilxomjon.dur_novvot_dokon.ui.describeError
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
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
    private var dupDialogShown = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPaymentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = viewModel { PaymentViewModel(ServiceLocator.catalog, ServiceLocator.orders, ServiceLocator.appPrefs, ServiceLocator.historyDb) }
        for (e in listOf(binding.edtCash, binding.edtTransfer, binding.edtClick)) NumInput.attach(e)
        lastToken = vm.state.value.sentToken

        childFragmentManager.setFragmentResultListener(REQ_STORE, viewLifecycleOwner) { _, bundle ->
            vm.selectStore(bundle.getString(StorePickerSheet.KEY_ID).orEmpty())
        }
        binding.cardStore.setOnClickListener { StorePickerSheet.show(childFragmentManager, REQ_STORE) }
        binding.btnRefreshDebt.setOnClickListener { vm.loadDebt() }
        binding.btnFullDebt.setOnClickListener {
            val d = vm.state.value.debt ?: return@setOnClickListener
            binding.edtCash.setText(Money.plain(d.som))
            binding.edtTransfer.setText(""); binding.edtClick.setText("")
        }

        val amountWatcher = { _: String ->
            if (!clearing) vm.setAmounts(binding.edtCash.text?.toString().orEmpty(), binding.edtTransfer.text?.toString().orEmpty(), binding.edtClick.text?.toString().orEmpty())
        }
        binding.edtCash.onTextChanged(amountWatcher)
        binding.edtTransfer.onTextChanged(amountWatcher)
        binding.edtClick.onTextChanged(amountWatcher)

        val s0 = vm.state.value
        // v4.0: do'konda bitta printer — "Printer 1 (do'kon)" yoki "Cheksiz"
        binding.chipPrinter1.text = getString(R.string.pr_shop)
        binding.chipPrinter2.visibility = android.view.View.GONE
        binding.chipPrinter3.text = getString(R.string.finish_no_printer)
        binding.chipPrinter.check(when (s0.printer) { "3" -> R.id.chipPrinter3; else -> R.id.chipPrinter1 })
        binding.chipPrinter.setOnCheckedStateChangeListener { _, ids ->
            vm.setPrinter(when (ids.firstOrNull()) { R.id.chipPrinter2 -> "2"; R.id.chipPrinter3 -> "3"; else -> "1" })
        }
        binding.btnSend.setOnClickListener { vm.send(binding.edtNote.text?.toString().orEmpty()) }

        launchOnStarted { vm.state.collect { render(it) } }
        launchOnStarted {
            UiBus.paymentStore.collect { id -> if (id != null) { UiBus.paymentStore.value = null; vm.selectStore(id) } }
        }
    }

    private fun render(s: PaymentState) {
        val ctx = requireContext()
        binding.tvStoreName.text = s.store?.name ?: getString(R.string.sale_select_store)
        binding.btnRefreshDebt.isEnabled = s.store != null && !s.debtLoading
        binding.progressDebt.show(s.debtLoading)
        val d = s.debt
        when {
            d != null -> {
                binding.tvDebtSom.text = getString(R.string.payment_debt_som, ctx.money(d.som, Currency.SOM))
                binding.tvDebtUsd.text = if (d.usd != 0.0) getString(R.string.payment_debt_usd, Money.format(d.usd, Currency.USD)) else ""
            }
            s.debtError != null -> { binding.tvDebtSom.text = getString(R.string.payment_debt_failed); binding.tvDebtUsd.text = "" }
            else -> { binding.tvDebtSom.setText(R.string.payment_debt_unknown); binding.tvDebtUsd.text = "" }
        }
        binding.tvDebtUsd.show(binding.tvDebtUsd.text.isNotEmpty())
        // Qiymat manbasi va yoshi doim ko'rinadi.
        binding.tvDebtAge.text = when {
            d == null -> ""
            s.debtFresh -> getString(R.string.debt_source_server, formatDateTime(d.fetchedAt))
            else -> getString(R.string.debt_source_old, formatDateTime(d.fetchedAt))
        } + if (s.debtError != null && d != null) "\n" + getString(R.string.payment_debt_failed) else ""
        binding.tvDebtAge.show(binding.tvDebtAge.text.isNotEmpty())
        binding.tvUnsentPayments.show(s.unsentCount > 0)
        if (s.unsentCount > 0) binding.tvUnsentPayments.text = getString(R.string.payment_unsent, s.unsentCount, ctx.money(s.unsentSum, Currency.SOM))
        binding.btnFullDebt.show(d != null && d.som > 0.0)

        binding.tvTotal.text = getString(R.string.payment_total, ctx.money(s.total, Currency.SOM))
        binding.progress.showInvisible(s.sending)
        binding.btnSend.isEnabled = !s.sending

        val msg = when (val e = s.error) {
            PaymentError.NoStore -> getString(R.string.payment_error_store)
            PaymentError.NoAmount -> getString(R.string.payment_error_amount)
            PaymentError.BadAmount -> getString(R.string.amount_invalid)
            is PaymentError.Failure -> getString(R.string.payment_failed) + "\n" + ctx.describeError(e.cause)
            null -> if (s.invalid) getString(R.string.amount_invalid) else null
        }
        binding.tvError.text = msg
        binding.tvError.show(msg != null)

        if (s.confirmDuplicate && !dupDialogShown) {
            dupDialogShown = true
            MaterialAlertDialogBuilder(ctx).setTitle(R.string.dup_payment_title).setMessage(R.string.dup_payment_message)
                .setPositiveButton(R.string.dup_payment_send) { _, _ -> dupDialogShown = false; vm.send(binding.edtNote.text?.toString().orEmpty(), duplicateConfirmed = true) }
                .setNegativeButton(R.string.cancel) { _, _ -> dupDialogShown = false; vm.dismissDuplicate() }
                .setOnCancelListener { dupDialogShown = false; vm.dismissDuplicate() }
                .show()
        }

        if (s.sentToken != lastToken) {
            lastToken = s.sentToken
            clearing = true
            binding.edtCash.setText(""); binding.edtTransfer.setText(""); binding.edtClick.setText(""); binding.edtNote.setText("")
            clearing = false
            Snackbar.make(binding.root, getString(R.string.payment_queued_receipt, s.lastReceipt), Snackbar.LENGTH_LONG).show()
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
