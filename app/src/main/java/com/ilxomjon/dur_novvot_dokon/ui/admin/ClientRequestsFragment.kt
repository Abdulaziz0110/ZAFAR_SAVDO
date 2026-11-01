package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.share.ClientEdit
import com.ilxomjon.dur_novvot_dokon.data.share.ClientPay
import com.ilxomjon.dur_novvot_dokon.data.share.ClientRequests
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import uz.imsoft.shared.ReqStatus
import uz.imsoft.shared.android.Ui

/** v3.2 Admin: 2-ilovadan kelgan to'lov va chek tuzatish so'rovlari. */
class ClientRequestsFragment : CodeFragment() {
    private lateinit var box: LinearLayout

    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.ad_requests)
        p.content.addView(Ui.hint(ctx, getString(R.string.cr_hint), 4))
        p.content.addView(Ui.button(ctx, getString(R.string.sp_refresh), outlined = true, icon = R.drawable.ic_sync) { launchUi { ServiceLocator.clientRequests.refresh() } })
        box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        p.content.addView(box)
        return p.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        launchOnStarted { ServiceLocator.clientRequests.state.collect { render(it) } }
        launchUi { ServiceLocator.clientRequests.refresh() }
    }

    private fun st(s: ReqStatus): String = when (s.state) {
        ReqStatus.OK -> getString(R.string.cr_st_ok, s.serverId)
        ReqStatus.REJECTED -> getString(R.string.cr_st_no)
        ReqStatus.ACCEPTED -> getString(R.string.cr_st_acc)
        else -> getString(R.string.cr_st_new)
    }

    private fun render(r: ClientRequests) {
        box.removeAllViews()
        box.addView(Ui.section(ctx, getString(R.string.cr_pays)))
        if (r.pays.isEmpty()) box.addView(Ui.hint(ctx, getString(R.string.cr_none)))
        for (p in r.pays) {
            box.addView(Ui.item(ctx, p.clientName + " — " + som(p.req.som),
                listOf(formatDateTime(p.req.at), methodName(p.req.method), p.req.by, p.req.note, st(p.req.status)).filter { it.isNotBlank() }.joinToString(" · "), icon = R.drawable.ic_payments) { pay(p) })
            box.addView(Ui.divider(ctx))
        }
        box.addView(Ui.section(ctx, getString(R.string.cr_edits)))
        if (r.edits.isEmpty()) box.addView(Ui.hint(ctx, getString(R.string.cr_none)))
        for (e in r.edits) {
            box.addView(Ui.item(ctx, e.clientName + " — " + getString(R.string.cr_edit_lines, e.req.lines.size),
                listOf(formatDateTime(e.req.at), e.req.by, e.req.note, st(e.req.status)).filter { it.isNotBlank() }.joinToString(" · "), icon = R.drawable.ic_edit_note) { edit(e) })
            box.addView(Ui.divider(ctx))
        }
    }

    private fun methodName(m: String) = getString(when (m) { "p" -> R.string.cr_m_card; "c" -> R.string.cr_m_click; else -> R.string.cr_m_cash })

    private fun pay(p: ClientPay) {
        val open = p.req.status.state == ReqStatus.SENT || p.req.status.state == ReqStatus.SEEN
        val msg = getString(R.string.cr_pay_text, p.clientName, som(p.req.som), methodName(p.req.method), p.req.note.ifBlank { "—" }, st(p.req.status))
        val b = MaterialAlertDialogBuilder(ctx).setTitle(R.string.cr_pay_title).setMessage(msg).setNeutralButton(R.string.close, null)
        if (open) {
            b.setPositiveButton(R.string.cr_accept) { _, _ -> launchUi { ServiceLocator.clientRequests.acceptPay(p); snack(getString(R.string.cr_accepted)) } }
            b.setNegativeButton(R.string.cr_reject) { _, _ -> reject(p.clientId, "pay", p.req.id) }
        }
        b.show()
    }

    private fun edit(e: ClientEdit) {
        val open = !e.req.status.isFinal
        val sb = StringBuilder()
        sb.append(getString(R.string.cr_edit_doc, e.req.doc.substringBefore('|').let { uz.imsoft.shared.report.Stamp.formatDateTime(it.toLongOrNull() ?: 0L) })).append("\n\n")
        sb.append(getString(R.string.cr_edit_new)).append('\n')
        e.req.lines.forEachIndexed { i, l ->
            val price = if (l.priceSom != 0.0) som(l.priceSom) else usd(l.priceUsd)
            sb.append("${i + 1}) ${l.name} — ${fmtQty(l.qty)} × $price\n")
        }
        if (e.req.note.isNotBlank()) sb.append('\n').append(e.req.note)
        sb.append("\n\n").append(st(e.req.status))
        val b = MaterialAlertDialogBuilder(ctx).setTitle(R.string.cr_edit_title).setMessage(sb.toString()).setNeutralButton(R.string.close, null)
        if (open) {
            b.setPositiveButton(R.string.cr_accept) { _, _ -> launchUi { ServiceLocator.clientRequests.acceptEdit(e); snack(getString(R.string.cr_edit_done)) } }
            b.setNegativeButton(R.string.cr_reject) { _, _ -> reject(e.clientId, "ed", e.req.id) }
        }
        b.show()
    }

    private fun reject(clientId: String, kind: String, id: String) {
        val (til, et) = Ui.field(ctx, getString(R.string.cr_reason))
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(ctx, 20), 0, Ui.dp(ctx, 20), 0); addView(til) }
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.cr_reject).setView(col)
            .setPositiveButton(R.string.cr_reject) { _, _ -> launchUi { ServiceLocator.clientRequests.reject(clientId, kind, id, et.text?.toString().orEmpty()) } }
            .setNegativeButton(R.string.cancel, null).show()
    }
}
