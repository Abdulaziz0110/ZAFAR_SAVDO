package com.ilxomjon.dur_novvot_dokon.ui.share

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.share.ClientOrder
import com.ilxomjon.dur_novvot_dokon.data.share.OrderConfirm
import com.ilxomjon.dur_novvot_dokon.databinding.ItemOrderBinding
import com.ilxomjon.dur_novvot_dokon.databinding.SheetOrdersBinding
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import com.ilxomjon.dur_novvot_dokon.ui.ledger.RepeatSheet
import com.ilxomjon.dur_novvot_dokon.ui.ledgerhost.DokonLedgerHost
import com.ilxomjon.dur_novvot_dokon.ui.show
import kotlinx.coroutines.launch
import uz.imsoft.shared.OrderRequest
import uz.imsoft.shared.receipt.NumFmt

/** 2-ilovadan (hamma mijozlardan) kelgan buyurtmalar. Ochilganda bulutdan yangilanadi. Tasdiqlansa — savatchaga. */
class OrdersSheet : BottomSheetDialogFragment() {
    private var _b: SheetOrdersBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = SheetOrdersBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        render(ServiceLocator.share.orders.value)
        viewLifecycleOwner.lifecycleScope.launch {
            val list = runCatching { ServiceLocator.share.refreshOrders() }.getOrNull()
            _b ?: return@launch
            b.progress.show(false)
            if (list != null) render(list)
        }
    }

    private fun render(list: List<ClientOrder>) {
        val bb = _b ?: return
        bb.list.removeAllViews()
        bb.tvEmpty.show(list.isEmpty())
        val inf = LayoutInflater.from(requireContext())
        for (co in list) {
            val o = co.order
            val ib = ItemOrderBinding.inflate(inf, bb.list, false)
            ib.tvTitle.text = co.clientName.ifBlank { getString(R.string.ord_item_title, formatDateTime(o.at)) }
            val names = o.lines.take(4).joinToString(", ") { it.name + " " + NumFmt.qty(it.qty) }
            ib.tvSub.text = listOfNotNull(
                formatDateTime(o.at) + (if (o.by.isNotBlank()) " · " + o.by else ""),
                getString(R.string.ord_item_lines, o.lines.size) + " · " + names + if (o.lines.size > 4) " …" else "",
                o.note.takeIf { it.isNotBlank() }?.let { getString(R.string.ord_note, it) },
            ).joinToString("\n")
            ib.tvStatus.text = statusText(o.status)
            ib.tvStatus.setTextColor(
                when (o.status) {
                    OrderRequest.Status.TAKEN -> ContextCompat.getColor(requireContext(), R.color.brand_success)
                    OrderRequest.Status.REJECTED -> MaterialColors.getColor(ib.root, com.google.android.material.R.attr.colorError)
                    else -> MaterialColors.getColor(ib.root, com.google.android.material.R.attr.colorTertiary)
                },
            )
            ib.root.setOnClickListener { open(co) }
            bb.list.addView(ib.root)
        }
    }

    private fun statusText(s: OrderRequest.Status) = getString(
        when (s) {
            OrderRequest.Status.SENT, OrderRequest.Status.SEEN -> R.string.ord_st_new
            OrderRequest.Status.TAKEN -> R.string.ord_st_taken
            OrderRequest.Status.REJECTED -> R.string.ord_st_rejected
        },
    )

    private fun open(co: ClientOrder) {
        val o = co.order
        val ctx = requireContext()
        val head = co.clientName + (if (o.by.isNotBlank()) " · " + getString(R.string.ord_by, o.by) else "") + "\n\n"
        val text = head + o.lines.joinToString("\n") { "• " + it.name + " — " + NumFmt.qty(it.qty) } + if (o.note.isNotBlank()) "\n\n" + getString(R.string.ord_note, o.note) else ""
        val fm = parentFragmentManager
        MaterialAlertDialogBuilder(ctx).setTitle(getString(R.string.ord_item_title, formatDateTime(o.at))).setMessage(text)
            // v5.1.1: "Tasdiqlash" — darhol savdo bo'lib rasmiylashadi, chek printerini Admin tanlaydi.
            .setPositiveButton(R.string.ord_ok) { _, _ -> askPrinter(co) }
            .setNeutralButton(R.string.ord_to_cart) { _, _ ->
                dismiss()
                RepeatSheet.showPlan(fm, DokonLedgerHost.planForOrder(ctx, co))
            }
            .setNegativeButton(R.string.ord_reject) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching { ServiceLocator.share.setOrderStatus(co.clientId, o.id, OrderRequest.Status.REJECTED) }
                    render(ServiceLocator.share.orders.value)
                }
            }
            .show()
    }

    /** Chek qayerdan chiqsin: Printer 1 / Printer 2 / cheksiz. Keyin buyurtma darhol savdo bo'ladi. */
    private fun askPrinter(co: ClientOrder) {
        val ctx = requireContext()
        val choices = OrderConfirm.printerChoices(ctx)
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.ord_where)
            .setMessage(getString(R.string.ord_where_sub, co.clientName))
            .setItems(choices.map { it.second }.toTypedArray()) { _, i -> accept(co, choices[i].first) }
            .setNegativeButton(R.string.lg_cancel, null)
            .show()
    }

    private fun accept(co: ClientOrder, printer: String) {
        val ctx = requireContext().applicationContext
        val root = requireActivity().findViewById<View>(android.R.id.content)
        b.progress.show(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val msg = try {
                val r = OrderConfirm.accept(ctx, co.clientId, co.order.id, printer)
                listOfNotNull(
                    getString(R.string.ord_done, r.storeName, r.added, OrderConfirm.printerName(ctx, printer)),
                    r.missing.takeIf { it > 0 }?.let { getString(R.string.ord_missing_n, it) },
                ).joinToString(" · ")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                getString(R.string.ord_fail_title) + ": " + (e.message ?: e.javaClass.simpleName)
            }
            if (_b != null) {
                b.progress.show(false)
                render(ServiceLocator.share.orders.value)
            }
            com.google.android.material.snackbar.Snackbar.make(root, msg, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        fun show(fm: FragmentManager) {
            if (fm.findFragmentByTag("orders") == null) OrdersSheet().show(fm, "orders")
        }
    }
}
