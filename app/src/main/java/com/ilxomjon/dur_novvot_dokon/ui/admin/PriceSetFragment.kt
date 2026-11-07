package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.domain.Product
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.onec.GoodsField
import uz.imsoft.shared.onec.OneCDoc
import uz.imsoft.shared.onec.PriceRule
import uz.imsoft.shared.onec.WriteCodec
import uz.imsoft.shared.onec.WriteKind
import uz.imsoft.shared.onec.WriteRequest

/**
 * v4.1 Admin: ommaviy narx belgilash — bir nechta tovarga birdan 1–4-narx. Tovarlar: guruh, oxirgi kirimlar yoki
 * qo'lda tanlanadi; usul: hozirgi narx ± %, kelgan narx + summa, kelgan narx + %, aniq narx; yaxlitlash.
 * "Hisoblash" — eski → yangi ro'yxat (keraksizini olib tashlash mumkin); "1C'ga yozish" — har tovar uchun tovar
 * kartochkasi yozuvi (ge), kompyuter kiritadi va telefonlarga narxlar qayta yuboriladi.
 */
class PriceSetFragment : CodeFragment() {
    private val selected = LinkedHashMap<String, Product>()
    private val levels = linkedSetOf(4)
    private var mode = PriceRule.Mode.COST_PLUS
    private var step = 100.0
    private lateinit var tvGoods: TextView
    private lateinit var eValue: TextInputEditText
    private lateinit var tvValueHint: TextView
    private lateinit var preview: LinearLayout
    private var plan: List<Change> = emptyList()
    private var sending = false

    /** Bitta o'zgarish: tovar, narx darajasi (1..4), eski va yangi qiymat (tovar valyutasida). */
    data class Change(val product: Product, val level: Int, val old: Double, val new: Double)

    private val modes = listOf(PriceRule.Mode.COST_PLUS, PriceRule.Mode.COST_PERCENT, PriceRule.Mode.PERCENT, PriceRule.Mode.FIXED)
    private val steps = listOf(0.0, 100.0, 500.0, 1000.0)

    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.ps_title)
        val c = p.content
        c.addView(Ui.hint(ctx, getString(R.string.ps_hint), 4))

        c.addView(Ui.section(ctx, getString(R.string.ps_goods)))
        tvGoods = Ui.title(ctx, "", 4).also { c.addView(it) }
        c.addView(Ui.buttonRow(ctx,
            Ui.button(ctx, getString(R.string.ps_by_group), outlined = true, top = 0, icon = R.drawable.ic_list) { pickGroup() },
            Ui.button(ctx, getString(R.string.ps_by_receipt), outlined = true, top = 0, icon = R.drawable.ic_truck) { pickReceipts() },
        ))
        c.addView(Ui.buttonRow(ctx,
            Ui.button(ctx, getString(R.string.ps_add_one), outlined = true, top = 0, icon = R.drawable.ic_add) { Pickers.product(ctx) { pr -> selected[pr.id] = pr; plan = emptyList(); render() } },
            Ui.button(ctx, getString(R.string.ps_clear), outlined = true, top = 0, icon = R.drawable.ic_close) { selected.clear(); plan = emptyList(); render() },
        ))

        c.addView(Ui.section(ctx, getString(R.string.ps_levels)))
        for (l in 1..4) {
            c.addView(Ui.switch(ctx, getString(R.string.ps_level, l), l in levels, 0) { on -> if (on) levels += l else levels -= l; plan = emptyList(); render() })
        }

        c.addView(Ui.section(ctx, getString(R.string.ps_method)))
        c.addView(Ui.toggle(ctx, listOf(getString(R.string.ps_m_cost_plus), getString(R.string.ps_m_cost_pct), getString(R.string.ps_m_pct), getString(R.string.ps_m_fixed)), 0, 4) { i ->
            mode = modes[i]; plan = emptyList(); render()
        })
        val (til, e) = Ui.field(ctx, getString(R.string.ps_value), android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED, "2000")
        eValue = e
        c.addView(til)
        tvValueHint = Ui.hint(ctx, "", 2).also { c.addView(it) }
        c.addView(Ui.text(ctx, getString(R.string.ps_round), com.google.android.material.R.style.TextAppearance_Material3_LabelLarge, 10))
        c.addView(Ui.toggle(ctx, listOf(getString(R.string.ps_round_none), "100", "500", "1 000"), 1, 4) { i -> step = steps[i]; plan = emptyList(); render() })

        c.addView(Ui.button(ctx, getString(R.string.ps_calc), top = 14, icon = R.drawable.ic_calculate) { calc() })
        preview = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        c.addView(preview)
        c.addView(Ui.hint(ctx, getString(R.string.wf_safety), 8))
        render()
        return p.root
    }

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        launchUi { ServiceLocator.onec.load() }
    }

    private fun render() {
        if (!::tvGoods.isInitialized) return
        tvGoods.text = if (selected.isEmpty()) getString(R.string.ps_none) else getString(R.string.ps_count, selected.size)
        tvValueHint.text = getString(
            when (mode) {
                PriceRule.Mode.COST_PLUS -> R.string.ps_v_cost_plus
                PriceRule.Mode.COST_PERCENT -> R.string.ps_v_cost_pct
                PriceRule.Mode.PERCENT -> R.string.ps_v_pct
                PriceRule.Mode.FIXED -> R.string.ps_v_fixed
            },
        )
        renderPreview()
    }

    private fun pickGroup() {
        val st = ServiceLocator.catalog.state.value
        val groups = st.groups.filter { g -> st.products.any { it.groupId == g.id } }
        if (groups.isEmpty()) { snack(getString(R.string.ps_no_groups)); return }
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.ps_by_group).setItems(groups.map { it.name }.toTypedArray()) { _, i ->
            st.products.filter { it.groupId == groups[i].id }.forEach { selected[it.id] = it }
            plan = emptyList()
            render()
        }.setNegativeButton(R.string.cancel, null).show()
    }

    /** Oxirgi kirimlar (kompyuterdan kelgan ta'minotchilar hujjatlari) — tanlangan hujjat tovarlari. */
    private fun pickReceipts() {
        val docs = ServiceLocator.onec.state.value.suppliers?.list.orEmpty().flatMap { s -> s.docs.filter { it.kind == OneCDoc.RECEIPT }.map { s.name to it } }
            .sortedByDescending { it.second.stamp }.take(40)
        if (docs.isEmpty()) { snack(getString(R.string.ps_no_receipts)); return }
        val byId = ServiceLocator.catalog.state.value.productsById
        val names = docs.map { (sup, d) -> getString(R.string.ps_receipt_row, uz.imsoft.shared.report.Stamp.formatDate(d.stamp), sup, d.lines.size) }.toTypedArray()
        val checked = BooleanArray(docs.size)
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.ps_by_receipt).setMultiChoiceItems(names, checked) { _, i, on -> checked[i] = on }
            .setPositiveButton(R.string.ok) { _, _ ->
                var miss = 0
                docs.forEachIndexed { i, (_, d) ->
                    if (!checked[i]) return@forEachIndexed
                    for (l in d.lines) {
                        val pr = byId[l.code] ?: ServiceLocator.catalog.state.value.products.firstOrNull { it.name.equals(l.name, true) }
                        if (pr == null) miss++ else selected[pr.id] = pr
                    }
                }
                if (miss > 0) snack(getString(R.string.ps_missing, miss))
                plan = emptyList()
                render()
            }.setNegativeButton(R.string.cancel, null).show()
    }

    /** Hozirgi narx (tovar valyutasida): 1C'dan kelgan narx turlari, bo'lmasa serverdagi chakana/ulgurji. */
    private fun oldPrice(p: Product, level: Int): Double {
        if (p.baseIsSom) {
            ServiceLocator.labels.priceForLevel(p.id, level)?.let { return it }
            return when (level) { 3 -> p.cashSom; 4 -> p.wholesaleSom; else -> 0.0 }
        }
        return when (level) { 3 -> p.cashUsd; 4 -> p.wholesaleUsd; else -> 0.0 }
    }

    /** Kelgan narx: oxirgi kirim (1C), bo'lmasa serverdagi. */
    private fun cost(p: Product): Double {
        val last = ServiceLocator.onec.lastCost(p.id)
        return if (p.baseIsSom) last?.first?.takeIf { it > 0 } ?: p.costSom else last?.second?.takeIf { it > 0 } ?: p.costUsd
    }

    private fun calc() {
        if (selected.isEmpty()) { snack(getString(R.string.ps_none)); return }
        if (levels.isEmpty()) { snack(getString(R.string.ps_no_levels)); return }
        val v = Money.parseUser(eValue.text?.toString()?.replace("−", "-"))
        if (v == null) { snack(getString(R.string.ps_bad_value)); return }
        val rate = ServiceLocator.catalog.state.value.rate
        val out = ArrayList<Change>()
        var skipped = 0
        for (p in selected.values) {
            // Dollarli tovar: summa (so'm) kurs bilan dollarga, yaxlitlash — sentgacha
            val value = if (!p.baseIsSom && (mode == PriceRule.Mode.COST_PLUS || mode == PriceRule.Mode.FIXED)) { if (rate > 0) v / rate else { skipped++; continue } } else v
            val st = if (p.baseIsSom) step else 0.01
            for (l in levels.sorted()) {
                val old = oldPrice(p, l)
                val n = PriceRule.apply(old, cost(p), mode, value, st)
                if (n == null) { skipped++; continue }
                if (kotlin.math.abs(n - old) < 0.005) continue
                out += Change(p, l, old, n)
            }
        }
        plan = out
        renderPreview()
        if (skipped > 0) snack(getString(R.string.ps_skipped, skipped))
    }

    private fun fmt(p: Product, v: Double) = if (p.baseIsSom) som(v) else usd(v)

    private fun renderPreview() {
        if (!::preview.isInitialized) return
        preview.removeAllViews()
        if (plan.isEmpty()) return
        preview.addView(Ui.section(ctx, getString(R.string.ps_preview, plan.map { it.product.id }.distinct().size, plan.size)))
        val (card, col) = Ui.card(ctx)
        plan.take(400).forEachIndexed { i, ch ->
            if (i > 0) col.addView(Ui.divider(ctx))
            val up = ch.new >= ch.old
            val row = Ui.item(ctx, ch.product.name, getString(R.string.ps_level, ch.level) + " · " + (if (ch.old > 0) fmt(ch.product, ch.old) else "—"),
                (if (up) "▲ " else "▼ ") + fmt(ch.product, ch.new)) { remove(ch) }
            (row.getChildAt(row.childCount - 1) as? TextView)?.setTextColor(if (up) uz.imsoft.shared.android.ChartColors.UP else uz.imsoft.shared.android.ChartColors.DOWN)
            col.addView(row)
        }
        if (plan.size > 400) col.addView(Ui.hint(ctx, getString(R.string.ps_more, plan.size - 400), 4))
        preview.addView(card)
        preview.addView(Ui.hint(ctx, getString(R.string.ps_tap_remove), 4))
        preview.addView(Ui.button(ctx, getString(R.string.ps_send), top = 10, icon = R.drawable.ic_check) { confirm() })
    }

    private fun remove(ch: Change) {
        plan = plan - ch
        renderPreview()
    }

    private fun requests(): List<WriteRequest> = plan.groupBy { it.product.id }.map { (_, list) ->
        val p = list.first().product
        val f = LinkedHashMap<String, String>()
        for (ch in list) f["p${ch.level}"] = Money.plain(ch.new)
        f[GoodsField.CUR] = if (p.baseIsSom) "s" else "u"
        WriteRequest(WriteCodec.newId(), WriteKind.GOODS_EDIT, System.currentTimeMillis(), p.name, p.id, getString(R.string.ps_note), fields = f)
    }

    private fun confirm() {
        if (sending || plan.isEmpty()) return
        if (!ServiceLocator.onec.ready) { snack(getString(R.string.oc_no_cloud)); return }
        val reqs = requests()
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.wf_confirm_title)
            .setMessage(getString(R.string.ps_confirm, reqs.size, plan.size) + "\n\n" + getString(R.string.wf_confirm_text))
            .setPositiveButton(R.string.wf_confirm_yes) { _, _ ->
                sending = true
                launchUi {
                    try {
                        val failed = ServiceLocator.onec.submitAll(reqs, "Admin")
                        snack(if (failed == 0) getString(R.string.wf_sent) else getString(R.string.ps_sent_partly, reqs.size - failed, failed))
                        navigator().pop()
                        navigator().push(WritesFragment())
                    } finally {
                        sending = false
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }
}
