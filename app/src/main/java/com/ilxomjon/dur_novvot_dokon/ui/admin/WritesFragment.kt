package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.onec.WriteItem
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.onec.WriteKind
import uz.imsoft.shared.onec.WriteResult

/** v3.2 Admin: 1C'ga yozish navbati va natijalari. "1C'ga kiritildi" faqat kompyuter hujjat raqamini qaytargach. */
class WritesFragment : CodeFragment() {
    private lateinit var lp: Ui.ListPage
    private lateinit var tvInfo: TextView
    private var shown: List<WriteItem> = emptyList()

    override fun build(inflater: LayoutInflater): View {
        lp = listPage(R.string.wr_title) { i -> shown.getOrNull(i)?.let { details(it) } }
        tvInfo = Ui.hint(ctx, getString(R.string.wr_hint), 4).also { lp.header.addView(it) }
        val bNew = Ui.button(ctx, getString(R.string.ad_new_receipt), top = 6, icon = R.drawable.ic_inventory) { navigator().push(WriteFormFragment.of(WriteKind.RECEIPT, "", "")) }
        val bRefresh = Ui.button(ctx, getString(R.string.sp_refresh), outlined = true, top = 6, icon = R.drawable.ic_sync) { launchUi { ServiceLocator.onec.refreshResults() } }
        lp.header.addView(Ui.buttonRow(ctx, bNew, bRefresh, top = 0))
        lp.empty.text = getString(R.string.wr_empty)
        return lp.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        launchUi { ServiceLocator.onec.load() }
        launchOnStarted { ServiceLocator.onec.state.collect { render() } }
        // Ekran ochiq turganda natijalarni har 20 soniyada tekshiradi
        launchOnStarted {
            while (isActive) {
                runCatching { ServiceLocator.onec.refreshResults() }
                delay(20_000)
            }
        }
    }

    private fun title(w: WriteItem): String = getString(
        when (w.req.kind) {
            WriteKind.RECEIPT -> R.string.wf_receipt
            WriteKind.SUPPLIER_PAY -> R.string.wf_pay
            WriteKind.SUPPLIER_RETURN -> R.string.wf_sreturn
            WriteKind.CLIENT_RETURN -> R.string.wf_creturn
            WriteKind.BARCODE -> R.string.wr_barcode
            WriteKind.CLIENT_PAY -> R.string.of_client_pay
            WriteKind.CASH_IN -> R.string.of_cash_in
            WriteKind.CASH_OUT -> R.string.of_cash_out
            WriteKind.GOODS_EDIT -> R.string.of_goods_edit
            WriteKind.NEW_GOODS -> R.string.of_new_goods
            else -> R.string.wr_undo
        },
    )

    private fun icon(w: WriteItem): Int = when (w.req.kind) {
        WriteKind.RECEIPT -> R.drawable.ic_truck
        WriteKind.SUPPLIER_PAY -> R.drawable.ic_payments
        WriteKind.SUPPLIER_RETURN, WriteKind.CLIENT_RETURN -> R.drawable.ic_assignment_return
        WriteKind.BARCODE -> R.drawable.ic_barcode
        WriteKind.CLIENT_PAY -> R.drawable.ic_wallet
        WriteKind.CASH_IN -> R.drawable.ic_cash_in
        WriteKind.CASH_OUT -> R.drawable.ic_cash_out
        WriteKind.GOODS_EDIT -> R.drawable.ic_price
        WriteKind.NEW_GOODS -> R.drawable.ic_add_box
        else -> R.drawable.ic_undo
    }

    /** Holat rangi: 0 — kutilmoqda, 1 — kiritildi, 3 — xato. */
    private fun tone(w: WriteItem): Int = when {
        w.result == null -> 0
        w.result.ok && w.result.state != WriteResult.UNDONE -> 1
        w.result.ok -> 2
        else -> 3
    }

    private fun status(w: WriteItem): String {
        val r = w.result
        return when {
            r == null && w.sendError.isNotEmpty() -> getString(R.string.wr_st_unsent)
            r == null -> getString(R.string.wr_st_wait)
            r.state == WriteResult.UNDONE -> getString(R.string.wr_st_undone)
            r.state == WriteResult.SKIPPED -> getString(R.string.wr_st_skip)
            r.ok -> getString(R.string.wr_st_done, r.number)
            else -> getString(R.string.wr_st_err, r.error)
        }
    }

    private fun render() {
        if (!::lp.isInitialized) return
        shown = ServiceLocator.onec.state.value.writes
        lp.empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        lp.adapter.submit(shown.map { w ->
            val what = when (w.req.kind) {
                WriteKind.BARCODE -> w.req.party + " · " + w.req.barcode
                WriteKind.UNDO -> w.req.target
                WriteKind.CASH_IN, WriteKind.CASH_OUT -> w.req.op
                WriteKind.NEW_GOODS -> w.req.fields[uz.imsoft.shared.onec.GoodsField.NAME].orEmpty()
                else -> w.req.party
            }
            val noMoney = w.req.kind == WriteKind.BARCODE || w.req.kind == WriteKind.UNDO || w.req.kind in WriteKind.GOODS
            Ui.Row(title(w) + ": " + what, formatDateTime(w.sentAt) + " · " + status(w), if (noMoney) "" else money2(w.req.totalSom, w.req.totalUsd), icon = icon(w), tone = tone(w))
        })
    }

    private fun details(w: WriteItem) {
        val sb = StringBuilder()
        sb.append(status(w)).append("\n\n")
        if (w.req.party.isNotEmpty()) sb.append(w.req.party).append('\n')
        w.req.lines.forEachIndexed { i, l ->
            val price = if (l.priceSom > 0) som(l.priceSom) else usd(l.priceUsd)
            sb.append("${i + 1}) ${l.name} — ${fmtQty(l.qty)} × $price\n")
        }
        if (w.req.kind in WriteKind.MONEY) sb.append(money2(w.req.totalSom, w.req.totalUsd)).append('\n')
        if (w.req.op.isNotEmpty()) sb.append(w.req.op).append('\n')
        val names = mapOf(
            "p1" to R.string.of_p1, "p2" to R.string.of_p2, "p3" to R.string.of_p3, "p4" to R.string.of_p4, "pin" to R.string.of_cost,
            "plu" to R.string.of_plu, "nm" to R.string.of_name, "bc" to R.string.of_barcode,
        )
        for ((k, v) in w.req.fields) names[k]?.let { sb.append(getString(it)).append(": ").append(v).append('\n') }
        if (w.req.barcode.isNotEmpty()) sb.append(w.req.barcode).append('\n')
        if (w.req.note.isNotEmpty()) sb.append(w.req.note).append('\n')
        val b = MaterialAlertDialogBuilder(ctx).setTitle(title(w)).setMessage(sb.toString().trim()).setNegativeButton(R.string.close, null)
        val r = w.result
        when {
            r != null && r.ok && r.state == WriteResult.DONE && w.req.kind != WriteKind.UNDO -> b.setPositiveButton(R.string.wr_undo_btn) { _, _ ->
                MaterialAlertDialogBuilder(ctx).setTitle(R.string.wr_undo_title).setMessage(R.string.wr_undo_text)
                    .setPositiveButton(R.string.wr_undo_btn) { _, _ -> launchUi { ServiceLocator.onec.undo(w.req.id); snack(getString(R.string.wf_sent)) } }
                    .setNegativeButton(R.string.cancel, null).show()
            }
            r != null && !r.ok -> b.setPositiveButton(R.string.wr_resend) { _, _ -> launchUi { ServiceLocator.onec.resend(w.req.id); snack(getString(R.string.wf_sent)) } }
            r == null && w.sendError.isNotEmpty() -> b.setPositiveButton(R.string.wr_resend) { _, _ -> launchUi { ServiceLocator.onec.retryUnsent() } }
        }
        b.show()
    }
}
