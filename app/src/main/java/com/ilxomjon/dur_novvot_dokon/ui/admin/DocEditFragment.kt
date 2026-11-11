package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.view.LayoutInflater
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.ui.money
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.onec.DocField
import uz.imsoft.shared.onec.OneCDoc
import uz.imsoft.shared.onec.WriteKind
import uz.imsoft.shared.onec.WriteLine
import uz.imsoft.shared.onec.WriteRequest
import uz.imsoft.shared.onec.WriteCodec
import uz.imsoft.shared.report.Stamp

/**
 * v5.2 (A5): tarixdagi 1C hujjatini (savdo, qaytarish) o'zgartirish. Admin tovarni, miqdorni va narxni
 * almashtiradi; saqlanganda kompyuter 1C'dagi hujjatni qayta yozadi va izohiga "Admin o'zgartirdi" qo'shadi.
 * Eski qatorlar kompyuter jurnalida qoladi — kerak bo'lsa qaytarish mumkin.
 */
class DocEditFragment : CodeFragment() {
    private class Row(var name: String, var code: String, var qty: Double, var price: Double, var usd: Boolean)

    private val rows = ArrayList<Row>()
    private lateinit var doc: OneCDoc
    private lateinit var list: android.widget.LinearLayout
    private lateinit var tvTotal: android.widget.TextView

    override fun build(inflater: LayoutInflater): View {
        val key = arguments?.getString(ARG_KEY).orEmpty()
        val found = ServiceLocator.onec.state.value.sales?.docs?.firstOrNull { it.id == key }
        val p = page(R.string.de_title)
        if (found == null) {
            p.content.addView(Ui.text(ctx, getString(R.string.de_gone)))
            return p.root
        }
        doc = found
        if (rows.isEmpty()) {
            for (l in doc.lines) {
                val usd = l.priceUsd != 0.0 || (l.priceSom == 0.0 && l.sumUsd != 0.0)
                rows += Row(l.name, l.code, l.qty, if (usd) l.priceUsd else l.priceSom, usd)
            }
        }
        val c = p.content
        c.addView(Ui.text(ctx, getString(R.string.de_head, doc.number, Stamp.formatDateTime(doc.stamp), doc.party.ifBlank { "—" })))
        c.addView(Ui.hint(ctx, getString(R.string.de_hint)))
        list = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.VERTICAL }
        c.addView(list)
        tvTotal = Ui.title(ctx, "")
        c.addView(tvTotal)
        c.addView(
            Ui.buttonRow(
                ctx,
                Ui.button(ctx, getString(R.string.de_add), outlined = true, icon = R.drawable.ic_add) { addLine() },
                Ui.button(ctx, getString(R.string.de_save), icon = R.drawable.ic_check) { confirm() },
            ),
        )
        render()
        return p.root
    }

    private fun render() {
        list.removeAllViews()
        for ((i, r) in rows.withIndex()) {
            val cur = if (r.usd) Currency.USD else Currency.SOM
            val sub = getString(R.string.de_line, Money.qtyDisplay(r.qty), ctx.money(r.price, cur))
            list.addView(
                Ui.item(ctx, r.name, sub, ctx.money(r.qty * r.price, cur), R.drawable.ic_shopping_bag) { editLine(i) },
            )
        }
        var som = 0.0
        var usd = 0.0
        for (r in rows) if (r.usd) usd += r.qty * r.price else som += r.qty * r.price
        tvTotal.text = getString(R.string.wf_total, money2(som, usd))
    }

    private fun addLine() {
        Pickers.product(ctx) { pr ->
            rows += Row(pr.name, pr.id, 1.0, if (pr.baseIsSom) pr.wholesaleSom else pr.wholesaleUsd, !pr.baseIsSom)
            render()
        }
    }

    /** Qatorni o'zgartirish: boshqa tovar, miqdor, narx yoki o'chirish. */
    private fun editLine(i: Int) {
        val r = rows.getOrNull(i) ?: return
        val items = arrayOf(
            getString(R.string.de_change_goods), getString(R.string.de_change_qty),
            getString(R.string.de_change_price), getString(R.string.de_remove),
        )
        MaterialAlertDialogBuilder(ctx).setTitle(r.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> Pickers.product(ctx) { pr ->
                        r.name = pr.name
                        r.code = pr.id
                        r.usd = !pr.baseIsSom
                        r.price = if (pr.baseIsSom) pr.wholesaleSom else pr.wholesaleUsd
                        render()
                    }
                    1 -> Pickers.qty(ctx, r.name, r.qty) { q -> r.qty = q; render() }
                    2 -> Pickers.qty(ctx, getString(R.string.de_change_price), r.price) { v -> r.price = v; render() }
                    else -> { rows.removeAt(i); render() }
                }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun confirm() {
        if (rows.isEmpty()) { snack(getString(R.string.de_no_lines)); return }
        val req = WriteRequest(
            id = WriteCodec.newId(), kind = WriteKind.DOC_EDIT, at = System.currentTimeMillis(),
            party = doc.party, partyCode = doc.partyCode,
            lines = rows.map { WriteLine(it.name, it.code, it.qty, if (it.usd) 0.0 else it.price, if (it.usd) it.price else 0.0) },
            fields = mapOf(DocField.KIND to doc.kind, DocField.NUMBER to doc.number, DocField.STAMP to doc.stamp.toString()),
        )
        val why = req.problem()
        if (why != null) { snack(getString(R.string.de_bad, why)); return }
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.de_confirm_title)
            .setMessage(getString(R.string.de_confirm, doc.number, rows.size))
            .setPositiveButton(R.string.wf_confirm_yes) { _, _ ->
                launchUi {
                    val item = ServiceLocator.onec.submit(req, "Admin")
                    snack(getString(if (item.sendError.isEmpty()) R.string.wf_sent else R.string.wf_sent_later))
                    navigator().pop()
                    navigator().push(WritesFragment())
                }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    companion object {
        private const val ARG_KEY = "key"

        fun of(docId: String) = DocEditFragment().apply { arguments = android.os.Bundle().apply { putString(ARG_KEY, docId) } }
    }
}
