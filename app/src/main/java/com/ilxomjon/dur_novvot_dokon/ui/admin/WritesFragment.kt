package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
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
    private var shown: List<WriteItem> = emptyList()
    private var filter = 0
    private var tabs: com.google.android.material.button.MaterialButtonToggleGroup? = null
    private var bResend: View? = null

    override fun build(inflater: LayoutInflater): View {
        lp = listPage(R.string.wr_title) { i -> shown.getOrNull(i)?.let { details(it) } }
        // v5.3: saralash — hammasi / kutmoqda / xato / kiritildi (har birida soni)
        tabs = Ui.toggle(ctx, listOf(getString(R.string.wr_f_all), getString(R.string.wr_f_wait), getString(R.string.wr_f_err), getString(R.string.wr_f_done)), filter, 4) { i ->
            filter = i
            render()
        }.also { lp.header.addView(it) }
        val bAll = Ui.button(ctx, getString(R.string.wr_resend_all), top = 6, icon = R.drawable.ic_sync) {
            launchUi { snack(getString(R.string.wr_resent, ServiceLocator.onec.resendAllFailed())) }
        }
        bResend = bAll
        val bClear = Ui.button(ctx, getString(R.string.wr_clear), outlined = true, top = 6, icon = R.drawable.ic_close) { clearDialog() }
        lp.header.addView(Ui.buttonRow(ctx, bAll, bClear, top = 0))
        lp.empty.text = getString(R.string.wr_empty)
        return lp.root
    }

    private fun clearDialog() {
        val rows = listOf(
            Ui.MenuRow(getString(R.string.wr_clear_err), icon = R.drawable.ic_close, tone = 3) { clear(errors = true, done = false) },
            Ui.MenuRow(getString(R.string.wr_clear_done), icon = R.drawable.ic_check, tone = 1) { clear(errors = false, done = true) },
            Ui.MenuRow(getString(R.string.wr_clear_all), getString(R.string.wr_clear_note), icon = R.drawable.ic_list) { clear(errors = true, done = true) },
        )
        Ui.menuSheet(ctx, getString(R.string.wr_clear_title), rows)
    }

    private fun clear(errors: Boolean, done: Boolean) {
        launchUi { snack(getString(R.string.wr_cleared, ServiceLocator.onec.clear(errors, done))) }
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
            WriteKind.RATE -> R.string.rt_title
            WriteKind.PARTY_NEW -> R.string.kf_new_title
            WriteKind.PARTY_EDIT -> R.string.kf_edit_title
            WriteKind.WRITE_OFF -> R.string.wo_title
            WriteKind.DOC_EDIT -> R.string.de_title
            WriteKind.BULK -> R.string.bk_title
            WriteKind.PANEL -> R.string.pn_title
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
        WriteKind.RATE -> R.drawable.ic_currency
        WriteKind.PANEL -> R.drawable.ic_widgets
        WriteKind.PARTY_NEW -> R.drawable.ic_person_add
        WriteKind.PARTY_EDIT -> R.drawable.ic_person_search
        WriteKind.WRITE_OFF -> R.drawable.ic_assignment_return
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
        val all = ServiceLocator.onec.state.value.writes
        val counts = listOf(all.size, all.count { it.isWaiting }, all.count { it.isError }, all.count { it.result?.ok == true })
        val names = listOf(R.string.wr_f_all, R.string.wr_f_wait, R.string.wr_f_err, R.string.wr_f_done)
        tabs?.let { g ->
            for (i in 0 until minOf(g.childCount, 4)) {
                (g.getChildAt(i) as? com.google.android.material.button.MaterialButton)?.text = getString(R.string.wr_f_count, getString(names[i]), counts[i])
            }
        }
        bResend?.visibility = if (counts[2] > 0) View.VISIBLE else View.GONE
        shown = when (filter) {
            1 -> all.filter { it.isWaiting }
            2 -> all.filter { it.isError }
            3 -> all.filter { it.result?.ok == true }
            else -> all
        }
        lp.empty.text = getString(if (all.isEmpty()) R.string.wr_empty else R.string.wr_filter_empty)
        lp.empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        lp.adapter.submit(shown.map { w ->
            val what = when (w.req.kind) {
                WriteKind.BARCODE -> w.req.party + " · " + w.req.barcode
                WriteKind.UNDO -> w.req.target
                WriteKind.CASH_IN, WriteKind.CASH_OUT -> w.req.op
                WriteKind.NEW_GOODS, WriteKind.PARTY_NEW -> w.req.fields[uz.imsoft.shared.onec.GoodsField.NAME].orEmpty()
                WriteKind.WRITE_OFF -> w.req.lines.joinToString(", ") { it.name }.take(60)
                WriteKind.RATE -> w.req.fields[uz.imsoft.shared.onec.GoodsField.RATE].orEmpty()
                WriteKind.DOC_EDIT -> "\u2116" + w.req.fields[uz.imsoft.shared.onec.DocField.NUMBER].orEmpty()
                WriteKind.BULK -> w.req.fields[uz.imsoft.shared.onec.BulkField.OP].orEmpty()
                WriteKind.PANEL -> w.req.lines.joinToString(", ") { it.name.ifEmpty { "—" } }.take(60)
                else -> w.req.party
            }
            val noMoney = w.req.kind in setOf(WriteKind.BARCODE, WriteKind.UNDO, WriteKind.RATE, WriteKind.PARTY_NEW, WriteKind.PARTY_EDIT, WriteKind.WRITE_OFF, WriteKind.BULK, WriteKind.PANEL) ||
                w.req.kind in WriteKind.GOODS
            // Ro'yxatda xato matni qisqa (to'lig'i — bosganda); qayta yuborilgan bo'lsa urinish soni ko'rinadi
            val st = status(w).let { if (w.isError && it.length > 70) it.take(70).trimEnd() + "…" else it }
            val retry = if (w.tries > 1) "\n" + getString(R.string.wr_retry_info, w.tries, formatDateTime(w.createdAt)) else ""
            Ui.Row(title(w) + ": " + what, formatDateTime(w.sentAt) + " · " + st + retry, if (noMoney) "" else money2(w.req.totalSom, w.req.totalUsd), icon = icon(w), tone = tone(w))
        })
    }

    private fun details(w: WriteItem) {
        val sb = StringBuilder()
        sb.append(status(w)).append("\n")
        if (w.tries > 1) sb.append(getString(R.string.wr_retry_info, w.tries, formatDateTime(w.createdAt))).append('\n')
        // v5.3: yuborilmaganda aniq sabab ko'rinsin (avval faqat "internet" deyilar edi)
        if (w.result == null && w.sendError.isNotEmpty()) {
            sb.append(getString(R.string.wr_send_err, w.sendError)).append("\n\n").append(getString(R.string.wr_net_hint)).append('\n')
        }
        sb.append('\n')
        if (w.req.party.isNotEmpty()) sb.append(w.req.party).append('\n')
        w.req.lines.forEachIndexed { i, l ->
            if (w.req.kind == WriteKind.PANEL) {
                val slot = l.qty.toInt()
                sb.append(if (l.name.isEmpty() && l.code.isEmpty()) getString(R.string.pn_line_clear, slot) else getString(R.string.pn_line, slot, l.name)).append('\n')
                return@forEachIndexed
            }
            val price = if (l.priceSom > 0) som(l.priceSom) else usd(l.priceUsd)
            sb.append("${i + 1}) ${l.name} — ${fmtQty(l.qty)} × $price\n")
        }
        if (w.req.kind in WriteKind.MONEY) sb.append(money2(w.req.totalSom, w.req.totalUsd)).append('\n')
        if (w.req.op.isNotEmpty()) sb.append(w.req.op).append('\n')
        val names = mapOf(
            "p1" to R.string.of_p1, "p2" to R.string.of_p2, "p3" to R.string.of_p3, "p4" to R.string.of_p4, "pin" to R.string.of_cost,
            "plu" to R.string.of_plu, "nm" to R.string.of_name, "bc" to R.string.of_barcode,
            uz.imsoft.shared.onec.PartyField.PHONE to R.string.kf_phone, uz.imsoft.shared.onec.PartyField.PHONE2 to R.string.kf_phone2,
            uz.imsoft.shared.onec.PartyField.ADDRESS to R.string.kf_address, uz.imsoft.shared.onec.PartyField.REGION to R.string.kf_region,
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
