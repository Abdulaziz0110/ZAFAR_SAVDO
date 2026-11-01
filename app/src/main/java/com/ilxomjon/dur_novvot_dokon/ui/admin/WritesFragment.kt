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
        val bNew = Ui.button(ctx, getString(R.string.ad_new_receipt), top = 6) { navigator().push(WriteFormFragment.of(WriteKind.RECEIPT, "", "")) }
        val bRefresh = Ui.button(ctx, getString(R.string.sp_refresh), outlined = true, top = 6) { launchUi { ServiceLocator.onec.refreshResults() } }
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
            else -> R.string.wr_undo
        },
    )

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
                else -> w.req.party
            }
            Ui.Row(title(w) + ": " + what, formatDateTime(w.sentAt) + " · " + status(w),
                if (w.req.kind == WriteKind.BARCODE || w.req.kind == WriteKind.UNDO) "" else money2(w.req.totalSom, w.req.totalUsd))
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
        if (w.req.kind == WriteKind.SUPPLIER_PAY) sb.append(money2(w.req.totalSom, w.req.totalUsd)).append('\n')
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
