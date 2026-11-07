package uz.imsoft.mijoz.ui.extra

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.shared.EditLine
import uz.imsoft.shared.PayRequest
import uz.imsoft.shared.ReqStatus
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.report.LedgerDoc
import uz.imsoft.shared.report.Stamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * v3.2 Mijoz: to'lov kiritish (Admin tasdiqlaydi) va yuborilgan so'rovlar holati. Chek — faqat Admin serverga yozib
 * tasdiqlagandan keyin ("Tasdiqlandi").
 */
class RequestsFragment : MFragment() {
    private lateinit var box: LinearLayout
    private var method = "n"

    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.rq_title)
        val c = p.content
        c.addView(Ui.hint(ctx, getString(R.string.rq_hint), 4))
        c.addView(Ui.section(ctx, getString(R.string.rq_new_pay)))
        val (tSum, eSum) = Ui.field(ctx, getString(R.string.rq_sum), Ui.NUMBER)
        c.addView(tSum)
        c.addView(Ui.toggle(ctx, listOf(getString(R.string.rq_m_cash), getString(R.string.rq_m_card), getString(R.string.rq_m_click)), 0) { i ->
            method = when (i) { 1 -> "p"; 2 -> "c"; else -> "n" }
        })
        val (tNote, eNote) = Ui.field(ctx, getString(R.string.rq_note))
        c.addView(tNote)
        c.addView(Ui.button(ctx, getString(R.string.rq_send), icon = R.drawable.ic_check) {
            val v = uz.imsoft.shared.NumText.parse(eSum.text?.toString())
            if (v <= 0.0) { snack(getString(R.string.rq_err_sum)); return@button }
            MaterialAlertDialogBuilder(ctx).setTitle(R.string.rq_confirm_title).setMessage(getString(R.string.rq_confirm, som(v)))
                .setPositiveButton(R.string.rq_send) { _, _ ->
                    launchUi {
                        Locator.requests.sendPay(v, method, eNote.text?.toString().orEmpty())
                        eSum.setText("")
                        eNote.setText("")
                        snack(getString(R.string.rq_sent))
                        reload()
                    }
                }
                .setNegativeButton(R.string.cancel, null).show()
        })
        c.addView(Ui.section(ctx, getString(R.string.rq_mine)))
        c.addView(Ui.button(ctx, getString(R.string.rq_refresh), outlined = true, icon = R.drawable.ic_sync) { reload() })
        box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        c.addView(box)
        return p.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) { reload() }

    private fun st(s: ReqStatus): String = when (s.state) {
        ReqStatus.OK -> getString(R.string.rq_st_ok)
        ReqStatus.REJECTED -> getString(R.string.rq_st_no, s.error.ifBlank { "—" })
        ReqStatus.ACCEPTED -> getString(R.string.rq_st_acc)
        ReqStatus.SEEN -> getString(R.string.rq_st_seen)
        else -> getString(R.string.rq_st_sent)
    }

    private fun fmt(ms: Long) = if (ms > 0) SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(Date(ms)) else ""

    private fun reload() {
        launchUi {
            val pays = Locator.requests.myPays()
            val edits = Locator.requests.myEdits()
            box.removeAllViews()
            if (pays.isEmpty() && edits.isEmpty()) box.addView(Ui.hint(ctx, getString(R.string.rq_none)))
            for (p in pays) {
                box.addView(Ui.item(ctx, getString(R.string.rq_pay_row, som(p.som)), listOf(fmt(p.at), p.note, st(p.status)).filter { it.isNotBlank() }.joinToString(" · "), icon = R.drawable.ic_payments) { payDetails(p) })
                box.addView(Ui.divider(ctx))
            }
            for (e in edits) {
                val docAt = e.doc.substringBefore('|').toLongOrNull()?.let { Stamp.formatDateTime(it) }.orEmpty()
                box.addView(Ui.item(ctx, getString(R.string.rq_edit_row, docAt), listOf(fmt(e.at), e.note, st(e.status)).filter { it.isNotBlank() }.joinToString(" · "), icon = R.drawable.ic_edit_note))
                box.addView(Ui.divider(ctx))
            }
        }
    }

    private fun payDetails(p: PayRequest) {
        val b = MaterialAlertDialogBuilder(ctx).setTitle(getString(R.string.rq_pay_row, som(p.som)))
            .setMessage(listOf(fmt(p.at), p.note, st(p.status)).filter { it.isNotBlank() }.joinToString("\n"))
            .setNegativeButton(R.string.close, null)
        if (p.status.state == ReqStatus.OK) b.setPositiveButton(R.string.rq_receipt) { _, _ ->
            launchUi {
                val snap = Locator.data.state.value.snapshot
                val shop = snap?.shopName.orEmpty().ifBlank { getString(R.string.app_name) }
                val stamp = uz.imsoft.shared.pos.Pos.stampOf(if (p.status.at > 0) p.status.at else p.at)
                printReceipt(uz.imsoft.shared.pos.Pos.paymentReceipt(shop, getString(R.string.rq_pay_title), snap?.clientName.orEmpty(), stamp, p.som, p.note,
                    p.status.serverId, posLabels()))
            }
        }
        b.show()
    }

    companion object {
        /** Chekni tuzatish so'rovi oynasi (1C tarixidagi savdo chekidan). */
        fun askEdit(f: androidx.fragment.app.Fragment, doc: LedgerDoc) {
            val ctx = f.requireContext()
            val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 4), Ui.dp(ctx, 20), 0) }
            col.addView(Ui.hint(ctx, ctx.getString(R.string.ed_hint), 0))
            val fields = ArrayList<Triple<String, com.google.android.material.textfield.TextInputEditText, com.google.android.material.textfield.TextInputEditText>>()
            val usd = ArrayList<Boolean>()
            for (e in doc.goods) {
                val price = e.v(uz.imsoft.shared.report.Col.PRICE)
                val isUsd = price.som == 0.0 && price.usd != 0.0
                col.addView(Ui.title(ctx, e.product, 10))
                val (tq, eq) = Ui.field(ctx, ctx.getString(R.string.ed_qty), Ui.NUMBER, uz.imsoft.shared.receipt.NumFmt.qty(e.qty).replace(" ", ""), 2)
                val (tp, ep) = Ui.field(ctx, ctx.getString(if (isUsd) R.string.ed_price_usd else R.string.ed_price), Ui.NUMBER,
                    (if (isUsd) price.usd else price.som).let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() }, 2)
                col.addView(tq)
                col.addView(tp)
                fields += Triple(e.product, eq, ep)
                usd += isUsd
            }
            val (tn, en) = Ui.field(ctx, ctx.getString(R.string.ed_note), top = 12)
            col.addView(tn)
            val scroll = androidx.core.widget.NestedScrollView(ctx).apply { addView(col) }
            MaterialAlertDialogBuilder(ctx).setTitle(R.string.ed_title).setView(scroll)
                .setPositiveButton(R.string.rq_send) { _, _ ->
                    val lines = fields.mapIndexed { i, (name, q, p) ->
                        val qty = uz.imsoft.shared.NumText.parse(q.text?.toString())
                        val pr = uz.imsoft.shared.NumText.parse(p.text?.toString())
                        if (usd[i]) EditLine(name, qty, 0.0, pr) else EditLine(name, qty, pr, 0.0)
                    }
                    val note = en.text?.toString().orEmpty()
                    Locator.scope.launch {
                        val ok = runCatching { Locator.requests.sendEdit(doc.key, lines, note) }.isSuccess
                        if (f.isAdded) f.view?.let { com.google.android.material.snackbar.Snackbar.make(it, if (ok) R.string.ed_sent else R.string.ed_failed, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show() }
                    }
                }
                .setNegativeButton(R.string.cancel, null).show()
        }
    }
}
