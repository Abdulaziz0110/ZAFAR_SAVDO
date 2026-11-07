package com.ilxomjon.dur_novvot_dokon.ui.sale

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.databinding.FragmentCartBinding
import com.ilxomjon.dur_novvot_dokon.databinding.ItemCartLineBinding
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.DraftState
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
import uz.imsoft.shared.android.NumInput

/**
 * v5.0 Savatcha: tovarlar ro'yxati va to'lov bitta ekranda — yakunlash uchun alohida oyna ochilmaydi.
 * "Yakunlash" tugmasini uzoq (3 soniya) bosib tursangiz — savatchani butunlay tozalash taklif qilinadi.
 * Chek tanlovi: Printer 1 (do'kon), Printer 1+2, Printer 2 (filial), Cheksiz.
 */
class CartFragment : Fragment() {
    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!
    private lateinit var cart: CartViewModel
    private lateinit var vm: FinishViewModel
    private var updating = false
    private var shown: List<com.ilxomjon.dur_novvot_dokon.domain.CartLine> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        cart = viewModel { CartViewModel(ServiceLocator.draft) }
        vm = viewModel { FinishViewModel(ServiceLocator.draft, ServiceLocator.orders, ServiceLocator.appPrefs) }
        binding.toolbar.setNavigationOnClickListener { navigator().pop() }
        for (e in listOf(binding.edtDiscountAmount, binding.edtDiscountPercent, binding.edtCash, binding.edtCard)) NumInput.attach(e)

        val s0 = vm.state.value
        val usd = s0.currency == Currency.USD
        binding.tilCash.hint = getString(if (usd) R.string.finish_cash_usd else R.string.finish_cash_som)
        binding.tilCard.show(!usd)
        setupPrinters(s0.printer)

        binding.edtDiscountPercent.onTextChanged {
            if (updating) return@onTextChanged
            val pct = Money.parseUser(it)
            val amount = Pricing.discountFromPercent(vm.state.value.total, pct ?: 0.0)
            updating = true
            binding.edtDiscountAmount.setText(if (amount > 0.0) Money.plain(amount) else "")
            updating = false
            pushInputs(pctInvalid = pct == null)
        }
        binding.edtDiscountAmount.onTextChanged {
            if (updating) return@onTextChanged
            val amount = Money.parseUser(it)
            val total = vm.state.value.total
            updating = true
            binding.edtDiscountPercent.setText(if (amount != null && amount > 0.0 && total > 0.0) Money.plain(amount / total * 100.0) else "")
            updating = false
            pushInputs()
        }
        binding.edtCash.onTextChanged { if (!updating) pushInputs() }
        binding.edtCard.onTextChanged { if (!updating) pushInputs() }
        binding.btnKeepAsIs.setOnClickListener { vm.keepAsIs() }
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

        binding.btnCheckout.setOnClickListener { ServiceLocator.kpi.tap(); onSend() }
        // v5.0: tozalash tugmasi olib tashlandi — "Yakunlash"ni uzoq bosib turish bilan tozalanadi
        binding.btnCheckout.setOnLongClickListener { confirmClear(); true }

        launchOnStarted { cart.state.collect { renderCart(it) } }
        launchOnStarted { vm.state.collect { render(it) } }
    }

    // ---------------- tovarlar ----------------

    private fun renderCart(d: DraftState) {
        binding.tvStore.text = d.store?.name.orEmpty()
        binding.tvStore.show(d.store != null)
        if (d.lines != shown) {
            shown = d.lines
            binding.lines.removeAllViews()
            val ctx = requireContext()
            for (l in d.lines) {
                val row = ItemCartLineBinding.inflate(layoutInflater, binding.lines, false)
                row.tvName.text = l.name
                row.tvDetail.text = getString(R.string.cart_line, Money.qtyDisplay(l.qty), ctx.money(l.price, d.currency))
                row.tvSum.text = ctx.money(l.total, d.currency)
                row.root.setOnClickListener { ItemEditSheet.show(childFragmentManager, l.productId) }
                row.root.setOnLongClickListener {
                    cart.remove(l)
                    Snackbar.make(binding.root, R.string.cart_deleted, Snackbar.LENGTH_LONG).setAction(R.string.cart_undo) { cart.restore(l) }.show()
                    true
                }
                binding.lines.addView(row.root)
            }
        }
        binding.tvEmpty.show(d.lines.isEmpty())
        binding.payBox.show(d.lines.isNotEmpty())
        binding.tvCount.text = getString(R.string.sale_items_count, d.itemCount)
        binding.tvTotal.text = requireContext().money(d.total, d.currency)
    }

    private fun confirmClear() {
        if (shown.isEmpty()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.clear_cart_title)
            .setMessage(R.string.clear_cart_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                val before = cart.clear()
                Snackbar.make(binding.root, R.string.cart_cleared, Snackbar.LENGTH_LONG).setAction(R.string.cart_undo) { cart.undo(before) }.show()
            }
            .show()
    }

    // ---------------- chek (printerlar) ----------------

    /**
     * v5.0: to'rt tanlov doim ko'rinadi — Printer 1 (do'kon), Printer 1+2, Printer 2 (filial), Cheksiz.
     * Saqlanadi: "1" — do'kon, "12" — ikkalasi, "2" — filial, "0" — chek chiqarilmaydi.
     */
    private fun setupPrinters(saved: String) {
        val b = binding
        val storeId = ServiceLocator.draft.state.value.store?.id.orEmpty()
        val prefs = requireContext().getSharedPreferences("user", android.content.Context.MODE_PRIVATE)
        b.chipPrinter1.text = getString(R.string.pr_shop)
        b.chipPrinterBoth.text = getString(R.string.pr_both)
        b.chipPrinter2.text = getString(R.string.pr_branch)
        b.chipPrinter3.text = getString(R.string.finish_no_printer)
        val choice = prefs.getString("print_choice", null) ?: (if (saved == "3") "0" else "1")
        fun apply(c: String) {
            prefs.edit().putString("print_choice", c).apply()
            // Server (1C) uchun: "1" — do'kon printeri, "3" — chiqarmaslik
            vm.setPrinter(if (c == "1" || c == "12") "1" else "3")
            val branch = if (storeId.isEmpty()) null else (c == "2" || c == "12")
            if (ServiceLocator.isRelay) ServiceLocator.relayClient.nextBranch = branch?.let { storeId to it }
            else ServiceLocator.share.nextBranchPrint = branch?.let { storeId to it }
        }
        b.chipPrinter.check(
            when (choice) {
                "12" -> R.id.chipPrinterBoth
                "2" -> R.id.chipPrinter2
                "0" -> R.id.chipPrinter3
                else -> R.id.chipPrinter1
            },
        )
        apply(choice)
        b.chipPrinter.setOnCheckedStateChangeListener { _, ids ->
            apply(
                when (ids.firstOrNull()) {
                    R.id.chipPrinterBoth -> "12"
                    R.id.chipPrinter2 -> "2"
                    R.id.chipPrinter3 -> "0"
                    else -> "1"
                },
            )
        }
    }

    // ---------------- to'lov ----------------

    /** Hamma maydonlarni bir yo'la o'qiydi: tushunarsiz matn jimgina 0 bo'lmaydi. */
    private fun pushInputs(pctInvalid: Boolean = false) {
        val d = Money.parseUser(binding.edtDiscountAmount.text?.toString())
        val c = Money.parseUser(binding.edtCash.text?.toString())
        val k = Money.parseUser(binding.edtCard.text?.toString())
        vm.setInputs(if (pctInvalid) null else d, c, k)
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
        if (shown.isEmpty() || ServiceLocator.draft.state.value.store == null) return
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
        binding.btnFillAll.isEnabled = !s.sending
        val err = s.error
        binding.tvError.show(err != null || s.invalid)
        if (err != null) binding.tvError.text = getString(R.string.finish_failed) + "\n" + ctx.describeError(err)
        else if (s.invalid) binding.tvError.setText(R.string.amount_invalid)
        binding.btnCheckout.isEnabled = !s.sending && !s.invalid && !s.sent && shown.isNotEmpty()
        binding.warnBox.show(s.warnings.isNotEmpty())
        if (s.warnings.isNotEmpty()) binding.tvWarnings.text = CheckerSupport.text(ctx, ServiceLocator.draft.state.value, s.warnings)
        if (s.sent) {
            Toast.makeText(ctx, getString(R.string.finish_queued_receipt, s.receipt), Toast.LENGTH_LONG).show()
            val nav = navigator()
            nav.pop()
            nav.openTab(R.id.nav_home)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
