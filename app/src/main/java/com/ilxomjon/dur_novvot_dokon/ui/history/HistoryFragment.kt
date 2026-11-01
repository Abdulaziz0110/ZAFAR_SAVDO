package com.ilxomjon.dur_novvot_dokon.ui.history

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.databinding.FragmentHistoryBinding
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.DealMode
import com.ilxomjon.dur_novvot_dokon.domain.PriceType
import com.ilxomjon.dur_novvot_dokon.domain.SentOrder
import com.ilxomjon.dur_novvot_dokon.domain.SentPayment
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.money
import com.ilxomjon.dur_novvot_dokon.ui.show
import com.ilxomjon.dur_novvot_dokon.ui.viewModel
import com.ilxomjon.dur_novvot_dokon.ui.outbox.OutboxUi
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxEntry
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxKind

/** Tarix: Buyurtmalar · To'lovlar · 1C tarixi (mijoz ilovasi sozlangan bo'lsa). */
class HistoryFragment : Fragment(), com.ilxomjon.dur_novvot_dokon.ui.ledger.LedgerHost by com.ilxomjon.dur_novvot_dokon.ui.ledgerhost.DokonLedgerHost {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: HistoryViewModel
    private lateinit var adapter: HistoryAdapter
    private var last = HistoryState()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = viewModel { HistoryViewModel(ServiceLocator.history) }
        adapter = HistoryAdapter { row ->
            val order = if (row.key.startsWith("o")) last.orders.firstOrNull { "o${it.id}" == row.key } else null
            val b = MaterialAlertDialogBuilder(requireContext())
                .setTitle(row.detailTitle)
                .setMessage(row.detail)
            if (order != null && order.lines.any { it.productId.isNotEmpty() }) {
                b.setPositiveButton(R.string.repeat_action) { _, _ -> repeatOrder(order) }
                b.setNegativeButton(R.string.close, null)
            } else {
                b.setPositiveButton(R.string.close, null)
            }
            if (row.shareText.isNotEmpty()) {
                b.setNeutralButton(R.string.history_share) { _, _ ->
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, row.shareText)
                    }
                    startActivity(android.content.Intent.createChooser(send, getString(R.string.share_chooser)))
                }
            }
            b.show()
        }
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        // v4.0 "Yagona ro'yxat": birinchi — hammasi (1C + telefonda qilingan, 1C'ga ketayotgani belgisi bilan)
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.history_all))
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.history_orders))
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.history_payments))
        ensureTabs()
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_pdf -> {
                    val lf = childFragmentManager.findFragmentByTag(TAG_LEDGER) as? com.ilxomjon.dur_novvot_dokon.ui.ledger.LedgerFragment
                    if (lf != null) {
                        val html = com.ilxomjon.dur_novvot_dokon.ui.ledger.LedgerPdf.html(requireContext(), ServiceLocator.share.currentClient()?.name.orEmpty(), lf.currentState())
                        com.ilxomjon.dur_novvot_dokon.ui.ledger.ReceiptActions.printHtml(requireContext(), html, "1C_tarixi")
                    }
                    true
                }
                R.id.action_orders -> { com.ilxomjon.dur_novvot_dokon.ui.share.OrdersSheet.show(childFragmentManager); true }
                else -> false
            }
        }
        binding.btnOrdersBanner.setOnClickListener { com.ilxomjon.dur_novvot_dokon.ui.share.OrdersSheet.show(childFragmentManager) }
        binding.btnClientPick.setOnClickListener { pickClient() }
        launchOnStarted {
            ServiceLocator.share.state.collect { st ->
                val bb = _binding ?: return@collect
                bb.btnClientPick.show(st.clients.size > 1)
                bb.btnClientPick.text = getString(R.string.hist_client, st.clientName)
                bb.btnOrdersBanner.text = getString(R.string.ord_banner, st.newOrders)
                bb.btnOrdersBanner.show(st.newOrders > 0 && st.acceptOrders)
            }
        }
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) = render()
            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })
        launchOnStarted { vm.state.collect { last = it; render() } }
        com.ilxomjon.dur_novvot_dokon.ui.share.Tour.show(this, com.ilxomjon.dur_novvot_dokon.ui.share.Tour.HISTORY)
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
        ensureTabs()
    }

    /** "1C tarixi" yorlig'i mijoz tanlangandan keyin paydo bo'ladi; boshqa ekrandan so'ralgan yorliq tanlanadi. */
    private fun ensureTabs() {
        val b = _binding ?: return
        if (pendingTab >= 0 && pendingTab < b.tabs.tabCount) b.tabs.getTabAt(pendingTab)?.select()
        pendingTab = -1
    }

    /** 1C tarixi qaysi mijozniki — ro'yxatdan tanlash (yuk beruvchilar belgilanadi). */
    private fun pickClient() {
        val st = ServiceLocator.share.state.value
        val list = st.clients
        if (list.isEmpty()) return
        val labels = list.map { c -> c.name + (if (c.kind == "s") " · " + getString(R.string.cl_kind_supplier_short) else "") + (if (c.entries > 0) "  (" + c.entries + ")" else "") }
        val cur = list.indexOfFirst { it.id == st.clientId }
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.hist_pick_client)
            .setSingleChoiceItems(labels.toTypedArray(), cur) { d, i ->
                d.dismiss()
                viewLifecycleOwner.lifecycleScope.launch { ServiceLocator.share.select(list[i].id) }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    /** "Takrorlash": savatcha bo'sh bo'lmasa — nima qilishni so'raydi. */
    private fun repeatOrder(o: SentOrder) {
        val draft = ServiceLocator.draft.state.value
        if (draft.lines.isEmpty()) { runRepeat(o, com.ilxomjon.dur_novvot_dokon.data.repo.RepeatSale.Mode.REPLACE); return }
        val modes = ArrayList<com.ilxomjon.dur_novvot_dokon.data.repo.RepeatSale.Mode>()
        val labels = ArrayList<String>()
        modes += com.ilxomjon.dur_novvot_dokon.data.repo.RepeatSale.Mode.REPLACE; labels += getString(R.string.repeat_replace, draft.lines.size)
        modes += com.ilxomjon.dur_novvot_dokon.data.repo.RepeatSale.Mode.ADD; labels += getString(R.string.repeat_add)
        if (ServiceLocator.appPrefs.fMultiCart) { modes += com.ilxomjon.dur_novvot_dokon.data.repo.RepeatSale.Mode.PARK; labels += getString(R.string.repeat_park) }
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.repeat_title)
            .setItems(labels.toTypedArray()) { _, i -> runRepeat(o, modes[i]) }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun runRepeat(o: SentOrder, mode: com.ilxomjon.dur_novvot_dokon.data.repo.RepeatSale.Mode) {
        val act = requireActivity()
        viewLifecycleOwner.lifecycleScope.launch {
            val r = ServiceLocator.repeatSale.repeat(o, mode)
            val root = act.findViewById<View>(android.R.id.content)
            if (r.added == 0) {
                com.google.android.material.snackbar.Snackbar.make(root, R.string.repeat_nothing, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
                return@launch
            }
            (act as? com.ilxomjon.dur_novvot_dokon.ui.Navigator)?.openTab(R.id.nav_sale)
            val parts = ArrayList<String>()
            parts += act.getString(R.string.repeat_done, r.added)
            if (r.missing > 0) parts += act.getString(R.string.sale_repeat_missing, r.missing)
            if (r.priceChanged > 0) parts += act.getString(R.string.repeat_price_changed, r.priceChanged)
            com.google.android.material.snackbar.Snackbar.make(root, parts.joinToString("\n"), com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) { vm.refresh(); ensureTabs(); com.ilxomjon.dur_novvot_dokon.ui.share.Tour.show(this, com.ilxomjon.dur_novvot_dokon.ui.share.Tour.HISTORY) }
    }

    private fun render() {
        if (_binding == null) return
        val ctx = requireContext()
        val ledger = binding.tabs.selectedTabPosition == TAB_ALL
        binding.ledgerPane.show(ledger)
        binding.toolbar.menu.findItem(R.id.action_pdf)?.isVisible = ledger
        binding.toolbar.menu.findItem(R.id.action_orders)?.isVisible = ledger && ServiceLocator.shareStore.acceptOrders && ServiceLocator.shareStore.isConfigured
        binding.recycler.show(!ledger)
        if (ledger) {
            binding.tvEmpty.show(false)
            if (childFragmentManager.findFragmentByTag(TAG_LEDGER) == null) {
                childFragmentManager.beginTransaction().replace(R.id.ledgerContainer, com.ilxomjon.dur_novvot_dokon.ui.ledger.LedgerFragment(), TAG_LEDGER).commitNowAllowingStateLoss()
            }
            viewLifecycleOwner.lifecycleScope.launch { ServiceLocator.share.load(); runCatching { if (ServiceLocator.shareStore.acceptOrders) ServiceLocator.share.refreshOrders() } }
            return
        }
        val rows = if (binding.tabs.selectedTabPosition == TAB_PAYMENTS) last.payments.map { paymentRow(ctx, it) } else last.orders.map { orderRow(ctx, it) }
        adapter.submitList(rows)
        binding.tvEmpty.show(last.loaded && rows.isEmpty())
    }

    private fun typeName(ctx: Context, t: PriceType) = ctx.getString(
        when (t) { PriceType.CASH -> R.string.type_cash; PriceType.WHOLESALE -> R.string.type_wholesale },
    )

    private fun orderRow(ctx: Context, o: SentOrder): HistoryRow {
        val net = o.total - o.discount
        val info = ArrayList<String>()
        if (o.outboxId > 0) info += OutboxEntry.receiptOf(OutboxKind.ORDER, o.outboxId)
        info += stateText(ctx, o.outboxId, o.pending)
        info += ctx.getString(R.string.history_items, o.lines.size)
        info += typeName(ctx, o.priceType)
        if (o.mode == DealMode.RETURN) info += ctx.getString(R.string.history_return)
        if (o.paid > 0.0) info += ctx.getString(R.string.history_paid, ctx.money(o.paid, o.currency))
        val detail = StringBuilder()
        for (l in o.lines) {
            detail.append(l.name).append('\n')
                .append(ctx.getString(R.string.cart_line, Money.qtyDisplay(l.qty), ctx.money(l.price, o.currency)))
                .append(" = ").append(ctx.money(l.qty * l.price, o.currency)).append("\n\n")
        }
        if (o.discount > 0.0) detail.append(ctx.getString(R.string.history_discount, ctx.money(o.discount, o.currency))).append('\n')
        detail.append(ctx.getString(R.string.sale_items_count, o.lines.size)).append(": ").append(ctx.money(net, o.currency))
        val share = StringBuilder(o.storeName).append('\n').append(formatDateTime(o.sentAt)).append("\n\n")
        for (l in o.lines) {
            share.append(l.name).append('\n')
                .append(ctx.getString(R.string.cart_line, Money.qtyDisplay(l.qty), ctx.money(l.price, o.currency)))
                .append(" = ").append(ctx.money(l.qty * l.price, o.currency)).append("\n\n")
        }
        if (o.discount > 0.0) share.append(ctx.getString(R.string.share_discount, ctx.money(o.discount, o.currency))).append('\n')
        share.append(ctx.getString(R.string.share_total, ctx.money(net, o.currency)))
        if (o.paid > 0.0) share.append('\n').append(ctx.getString(R.string.share_paid, ctx.money(o.paid, o.currency)))
        return HistoryRow(
            key = "o${o.id}", title = o.storeName, amount = ctx.money(net, o.currency), whenText = formatDateTime(o.sentAt),
            info = info.joinToString(" · "), detailTitle = o.storeName, detail = detail.toString(), shareText = share.toString(),
        )
    }

    private fun paymentRow(ctx: Context, p: SentPayment): HistoryRow {
        val base = ctx.getString(
            R.string.history_payment_info, Money.display(p.cash), Money.display(p.transfer), Money.display(p.click),
        )
        val head = listOfNotNull(if (p.outboxId > 0) OutboxEntry.receiptOf(OutboxKind.PAYMENT, p.outboxId) else null, stateText(ctx, p.outboxId, p.pending))
        val info = head.joinToString(" · ") + " · " + base
        val detail = if (p.note.isBlank()) info else info + "\n\n" + p.note
        return HistoryRow(
            key = "p${p.id}", title = p.storeName, amount = ctx.money(p.total, Currency.SOM), whenText = formatDateTime(p.sentAt),
            info = info, detailTitle = p.storeName, detail = detail,
        )
    }

    /** Holat matni: "Serverga yozildi" (qayta ishlandi EMAS), "Tekshirish kerak" va h.k. */
    private fun stateText(ctx: Context, outboxId: Long, pending: Boolean): String {
        val st = last.states[outboxId] ?: return ctx.getString(if (pending) R.string.history_pending else R.string.st_sent)
        return OutboxUi.label(ctx, st)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG_LEDGER = "ledger"
        /** v4.0: yorliqlar tartibi — hammasi (yagona ro'yxat), yuborganlarim, to'lovlar. */
        const val TAB_ALL = 0
        const val TAB_ORDERS = 1
        const val TAB_PAYMENTS = 2
        /** Boshqa ekrandan "1C tarixi" ochilishi uchun. */
        var pendingTab = -1
        fun open1C(nav: com.ilxomjon.dur_novvot_dokon.ui.Navigator, existing: HistoryFragment?) {
            pendingTab = TAB_ALL
            existing?.ensureTabs()
            nav.openTab(R.id.nav_history)
        }
    }
}
