package com.ilxomjon.dur_novvot_dokon.ui.history

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
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

class HistoryFragment : Fragment() {
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
            val b = MaterialAlertDialogBuilder(requireContext())
                .setTitle(row.detailTitle)
                .setMessage(row.detail)
                .setPositiveButton(R.string.close, null)
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
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.history_orders))
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.history_payments))
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) = render()
            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })
        launchOnStarted { vm.state.collect { last = it; render() } }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) vm.refresh()
    }

    private fun render() {
        if (_binding == null) return
        val ctx = requireContext()
        val rows = if (binding.tabs.selectedTabPosition == 1) last.payments.map { paymentRow(ctx, it) } else last.orders.map { orderRow(ctx, it) }
        adapter.submitList(rows)
        binding.tvEmpty.show(last.loaded && rows.isEmpty())
    }

    private fun typeName(ctx: Context, t: PriceType) = ctx.getString(
        when (t) { PriceType.CASH -> R.string.type_cash; PriceType.WHOLESALE -> R.string.type_wholesale },
    )

    private fun orderRow(ctx: Context, o: SentOrder): HistoryRow {
        val net = o.total - o.discount
        val info = ArrayList<String>()
        if (o.pending) info += ctx.getString(R.string.history_pending)
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
        val info = if (p.pending) ctx.getString(R.string.history_pending) + " · " + base else base
        val detail = if (p.note.isBlank()) info else info + "\n\n" + p.note
        return HistoryRow(
            key = "p${p.id}", title = p.storeName, amount = ctx.money(p.total, Currency.SOM), whenText = formatDateTime(p.sentAt),
            info = info, detailTitle = p.storeName, detail = detail,
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
