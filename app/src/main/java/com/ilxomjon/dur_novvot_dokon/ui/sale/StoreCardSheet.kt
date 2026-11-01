package com.ilxomjon.dur_novvot_dokon.ui.sale

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.databinding.SheetStoreCardBinding
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.DealMode
import com.ilxomjon.dur_novvot_dokon.domain.Suggestions
import com.ilxomjon.dur_novvot_dokon.ui.UiBus
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import com.ilxomjon.dur_novvot_dokon.ui.money
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import com.ilxomjon.dur_novvot_dokon.ui.show
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Do'kon tanlanganda bitta ko'rinish: qarz (yoshi bilan), oxirgi buyurtma, "vaqti kelgan" tovarlar va katta "Boshlash".
 * Bitta bosish bilan yopiladi va savdoga o'tkazadi. Sozlamada o'chiriladi.
 */
class StoreCardSheet : BottomSheetDialogFragment() {
    private var _binding: SheetStoreCardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetStoreCardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val id = requireArguments().getString(ARG_ID).orEmpty()
        val store = ServiceLocator.catalog.storeById(id)
        if (store == null) { dismissAllowingStateLoss(); return }
        val ctx = requireContext()
        binding.tvName.text = store.name
        // v3.1: "Boshlash" kerak emas — savdo do'kon tanlanishi bilan boshlanadi
        binding.btnStart.show(false)
        viewLifecycleOwner.lifecycleScope.launch {
            val debt = ServiceLocator.catalog.knownDebt(id)
            val orders = ServiceLocator.history.storeOrders(id)
            val global = ServiceLocator.history.recentSaleOrders()
            val fb = ServiceLocator.history.feedback(id)
            val (unsentN, _) = withContext(Dispatchers.IO) { ServiceLocator.historyDb.unsentPayments(id) }
            val sug = withContext(Dispatchers.Default) { Suggestions.build(orders, global, System.currentTimeMillis(), feedback = fb) }
            if (_binding == null) return@launch
            val products = ServiceLocator.catalog.state.value.productsById
            // Qarz: manba va vaqt bilan; yuborilmagan to'lovlar alohida (qarzga qo'shilmaydi).
            val debtLine = if (debt == null) getString(R.string.payment_debt_unknown) else {
                val parts = ArrayList<String>()
                parts += ctx.money(debt.som, Currency.SOM)
                if (debt.usd != 0.0) parts += Money.format(debt.usd, Currency.USD) + " $"
                getString(R.string.card_debt, parts.joinToString(" · ")) + "\n" + getString(R.string.debt_source_old, formatDateTime(debt.fetchedAt))
            }
            binding.tvDebt.text = debtLine + if (unsentN > 0) "\n" + getString(R.string.card_unsent_payments, unsentN) else ""
            val last = orders.firstOrNull { it.mode == DealMode.SALE }
            binding.tvLast.text = if (last == null) getString(R.string.card_no_history)
            else getString(R.string.card_last_order, formatDateTime(last.sentAt), ctx.money(last.total - last.discount, last.currency))
            val due = sug.filter { s -> s.intervalDays != null && s.daysSince != null && s.daysSince >= s.intervalDays * 0.7 }
                .mapNotNull { products[it.productId]?.name }.take(3)
            binding.tvDue.show(due.isNotEmpty())
            binding.tvDue.text = getString(R.string.card_due, due.joinToString(", "))
            val som = debt?.som ?: 0.0
            binding.btnPay.show(som > 0.0)
            binding.btnPay.text = getString(R.string.smart_payment, ctx.money(som, Currency.SOM))
            binding.btnPay.setOnClickListener {
                UiBus.paymentStore.value = id
                val nav = navigator()
                dismissAllowingStateLoss()
                nav.openTab(R.id.nav_payment)
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    companion object {
        private const val ARG_ID = "store_id"
        fun show(fm: FragmentManager, storeId: String) {
            if (fm.findFragmentByTag("store_card") != null) return
            StoreCardSheet().apply { arguments = Bundle().apply { putString(ARG_ID, storeId) } }.show(fm, "store_card")
        }
    }
}
